package brentmaas.buildguide.fabric;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;

import brentmaas.buildguide.common.AbstractRenderHandler;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.RenderStats;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.shape.GuidelinePicker;
import brentmaas.buildguide.common.shape.LayerPalette;
import brentmaas.buildguide.common.shape.LongHashSet;
import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeLayers;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.SliceScan;
import brentmaas.buildguide.common.shape.StateReconciler;
import brentmaas.buildguide.common.shape.TargetOutline;
import brentmaas.buildguide.common.shape.ValidationOverlay;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.fabric.place.PlacementClick;
import brentmaas.buildguide.fabric.screen.ScreenWrapper;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import brentmaas.buildguide.fabric.validation.WorldProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RenderHandler extends AbstractRenderHandler {
	// An automatic scan waits this long after the shape's last regeneration (debounce for rapid property changes)
	private static final long autoScanIdleMillis = 300;
	// The error overlay is rebuilt at most this often while the state keeps changing
	private static final long overlayRebuildMillis = 100;
	private static final RenderPipeline.Snippet BUILD_GUIDE_SNIPPET = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA))
			.withCull(true)
			.withDepthWrite(false)
			.buildSnippet();
	private static final RenderPipeline BUILD_GUIDE = RenderPipeline.builder(BUILD_GUIDE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(BuildGuide.modid, "pipeline/build_guide"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build();
	private static final RenderPipeline BUILD_GUIDE_DEPTH_TEST = RenderPipeline.builder(BUILD_GUIDE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(BuildGuide.modid, "pipeline/build_guide_depth_test"))
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.build();
	// 3D preview (its own picture-in-picture texture): opaque cubes need depth writes, which the world
	// pipelines disable. No culling: the preview's flipped projection would make face winding a guess
	public static final RenderPipeline BUILD_GUIDE_PREVIEW = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(BuildGuide.modid, "pipeline/build_guide_preview"))
			.withCull(false)
			.withDepthWrite(true)
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.build();

	public void register() {
		try {
			Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> irisProgramClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");

			Method irisGetInstanceMethod = irisApiClass.getMethod("getInstance");
			Method irisAssignPipelineMethod = irisApiClass.getMethod("assignPipeline", RenderPipeline.class, irisProgramClass);
			Method irisProgramEnumValueOfMethod = irisProgramClass.getMethod("valueOf", String.class);

			Object irisApiInstance = irisGetInstanceMethod.invoke(irisApiClass);
			Object irisProgramEnumBasic = irisProgramEnumValueOfMethod.invoke(irisProgramClass, "BASIC");
			irisAssignPipelineMethod.invoke(irisApiInstance, BUILD_GUIDE, irisProgramEnumBasic);
			irisAssignPipelineMethod.invoke(irisApiInstance, BUILD_GUIDE_DEPTH_TEST, irisProgramEnumBasic);
			System.out.println("Iris compatibility applied");
		} catch (ClassNotFoundException e) {
			BuildGuide.logHandler.debugOrHigher("Iris not found");
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			BuildGuide.logHandler.error("Could not apply Iris compatibility");
			e.printStackTrace();
		}
	}

	// The frustum the world culls its terrain with in this frame (MixinLevelRenderer); null when it was not
	// captured (another mod replaced prepareCullFrustum): then only the distance culls
	private static Frustum frustum = null;
	// Sections in view this frame (reused, render thread)
	private final List<ShapeBuffer> visibleSections = new ArrayList<ShapeBuffer>();

	public static void noteCullFrustum(Frustum cullFrustum) {
		frustum = cullFrustum;
	}

	@Override
	public void render() {
		try {
			super.render();
		}finally {
			frustum = null; // never used in a later frame
		}
	}

	// Live apply (deferredWorldUpdates): the world draws the last applied buffer, not the one being generated
	public void renderShapeBuffer(Shape shape) {
		if(shape.shownBuffer instanceof SectionedBuffer sectioned) {
			visibleSections.clear();
			for(SectionedBuffer.Section section: sectioned.getSections()) visibleSections.add((ShapeBuffer) section.getBuffer());
			ShapeBuffer.renderAll(visibleSections);
			visibleSections.clear();
		}else {
			((ShapeBuffer) shape.shownBuffer).render();
		}
	}

	/**
	 * The world buffer's sections that can be seen, in one render pass: those within the render distance (plus
	 * a section) horizontally and inside the frustum the terrain is culled with, like the world's own sections.
	 * Inside the set's translation (setupRenderingShapeSet).
	 */
	@Override
	protected void renderWorldBuffer(ShapeSet shapeSet) {
		Shape shape = shapeSet.getShape();
		if(!(shape.shownBuffer instanceof SectionedBuffer sectioned)) {
			renderShapeBuffer(shape);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		double ox = shapeSet.getOriginX(), oy = shapeSet.getOriginY(), oz = shapeSet.getOriginZ();
		double reach = (mc.options.getEffectiveRenderDistance() + 1) * 16.0;
		List<SectionedBuffer.Section> sections = sectioned.getSections();
		visibleSections.clear();
		for(SectionedBuffer.Section section: sections) {
			if(section.getVertexCount() == 0) continue;
			if(section.horizontalDistanceSq(camera.x, camera.z, ox, oz) > reach * reach) continue;
			if(frustum != null && !frustum.isVisible(new AABB(ox + section.minX(), oy + section.minY(), oz + section.minZ(), ox + section.maxX(), oy + section.maxY(), oz + section.maxZ()))) continue;
			visibleSections.add((ShapeBuffer) section.getBuffer());
		}
		ShapeBuffer.renderAll(visibleSections);
		RenderStats.sections(visibleSections.size(), sections.size());
		visibleSections.clear();
	}

	@Override
	protected boolean deferredWorldUpdates() {
		return true;
	}

	// Any of the mod's screens (tabs, dropdowns, the expanded preview) counts as the menu being open
	@Override
	protected boolean isMenuOpen() {
		return Minecraft.getInstance().screen instanceof ScreenWrapper;
	}

	protected void setupRenderingShapeSet(ShapeSet shapeSet) {
		RenderSystem.getModelViewStack().pushMatrix();
		Vec3 projectedView = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		RenderSystem.getModelViewStack().translate((float) (-projectedView.x + shapeSet.getOriginX()), (float) (-projectedView.y + shapeSet.getOriginY()), (float) (-projectedView.z + shapeSet.getOriginZ()));
	}

	protected void endRenderingShapeSet() {
		RenderSystem.getModelViewStack().popMatrix();
	}

	protected void pushProfiler(String key) {
		Profiler.get().push(key);
	}

	protected void popProfiler() {
		Profiler.get().pop();
	}

	// Validation overlay: one extra buffer per shape with red/yellow/orange/white cubes, rebuilt when
	// the state version changed (at most every 100 ms), one draw call per frame regardless of count
	protected void renderValidationOverlay(ShapeSet shapeSet) {
		Shape shape = shapeSet.getShape();
		ValidationState state = shape.getValidationState();
		if(!ValidationOverlay.hasContent(state)) {
			if(shape.overlayBuffer != null) {
				shape.overlayBuffer.close();
				shape.overlayBuffer = null;
				shape.overlayVersion = -1;
			}
			return;
		}
		long version = state.getVersion();
		long now = System.currentTimeMillis();
		if(shape.overlayBuffer == null || (version != shape.overlayVersion && now - shape.overlayBuiltAt >= overlayRebuildMillis)) {
			if(shape.overlayBuffer != null) shape.overlayBuffer.close();
			ShapeBuffer buffer = new ShapeBuffer();
			ShapeSet.Origin player = BuildGuide.shapeHandler.getPlayerPosition();
			// Build focus: the shape's own states only on the blocks the world shows (Shape.setFocus forces this rebuild)
			ValidationOverlay.build(buffer, state, new ShapeSet.Origin(player.x - state.getScanOriginX(), player.y - state.getScanOriginY(), player.z - state.getScanOriginZ()), shape.worldFocusTest());
			buffer.end();
			shape.overlayBuffer = buffer;
			shape.overlayVersion = version;
			shape.overlayBuiltAt = now;
		}
		// The state is local to the origin of its scan (P4): after the origin moved, the cubes stay where
		// the scan saw them until the rescan. No extra transform at all when the origins match
		int[] offset = state.getScanOffset(shapeSet.getOriginX(), shapeSet.getOriginY(), shapeSet.getOriginZ());
		boolean shifted = offset[0] != 0 || offset[1] != 0 || offset[2] != 0;
		if(shifted) {
			RenderSystem.getModelViewStack().pushMatrix();
			RenderSystem.getModelViewStack().translate(offset[0], offset[1], offset[2]);
		}
		((ShapeBuffer) shape.overlayBuffer).render();
		if(shifted) RenderSystem.getModelViewStack().popMatrix();
	}
	
	/**
	 * The validation scan, in Y layers (SliceScan): each frame scans layers until the Scan speed budget is
	 * spent (Instant: all of them, as before), and the result reaches the state in one go at the end, with
	 * the previous totals kept meanwhile. A scan whose inputs changed (new generation, invalidation, a new
	 * request) or that a manual Validate replaces starts over. Blocks changed meanwhile are marked by
	 * IncrementalValidator and read again before publishing.
	 */
	protected void validateShape(ShapeSet shapeSet) {
		Shape validatable = shapeSet.getShape(); // every Shape is IValidatable
		ValidationState state = validatable.getValidationState();
		// Check errors (Visualisation): a change makes a scan asked for or running stale
		state.setCheckErrors(BuildGuide.stateManager.getState().isCheckErrors());
		boolean manual = validatable.consumeValidateRequest();
		SliceScan scan = state.getActiveScan();
		if(scan != null && (manual || scan.isStale(state, validatable.getGeneration()))) {
			scan = null;
			state.setActiveScan(null);
			state.setScanPercent(-1);
		}
		PendingScan pending = preparing.get(state);
		if(pending != null && (manual || pending.isStale(state, validatable.getGeneration()))) {
			pending.future.cancel(true);
			preparing.remove(state);
			pending = null;
		}
		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;
		if(scan == null) {
			if(pending == null) {
				prepareScan(shapeSet, validatable, state, manual);
				return; // it starts on the frame its preparation is ready
			}
			scan = startScan(state, pending, world);
			if(scan == null) return;
		}
		WorldProbe probe = new WorldProbe(world);
		if(!scan.isDone()) {
			// Performance step 4: the result is published on the next frame, never in the frame that also spent the
			// budget on the last layers (that frame was the budget plus the publish, up to 20 ms)
			scan.step(probe, BuildGuide.config.scanSpeed.value.budgetMillis * 1000000L, System::nanoTime);
			state.setScanPercent(scan.percent());
			return;
		}
		long publishing = System.nanoTime();
		scan.publish(probe);
		long publishNanos = System.nanoTime() - publishing;
		state.setActiveScan(null);
		state.setScanPercent(-1);
		TimingLog.recordScan(scan.getTotalMillis(), scan.getFrames(), scan.getMaxSliceMillis(), scan.getExpectedCount(), scan.getStartMillis(), publishNanos / 1000000, scan.getPrepMillis());
	}

	// Scans being prepared, per validation state: the copy of the expected set and SliceScan.prepare (which reads
	// no world) run on scanPreparation; the scan starts on the first frame it is ready
	private static final ExecutorService scanPreparation = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Build Guide scan preparation");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<ValidationState, PendingScan> preparing = new WeakHashMap<ValidationState, PendingScan>();

	private static final class PendingScan {
		final Future<SliceScan> future;
		final long epoch, generation, startNanos;
		final boolean manual;

		PendingScan(Future<SliceScan> future, long epoch, long generation, boolean manual, long startNanos) {
			this.future = future;
			this.epoch = epoch;
			this.generation = generation;
			this.manual = manual;
			this.startNanos = startNanos;
		}

		// Its inputs changed meanwhile (new generation, invalidation, new scan request)
		boolean isStale(ValidationState state, long currentGeneration) {
			return state.getScanEpoch() != epoch || currentGeneration != generation;
		}
	}

	/**
	 * Asks for a new scan when one is due. Manual (button) scans are asked for now. Automatic ones, requested by
	 * the shape after it regenerated, wait until the shape has been idle for a moment (holding +/- regenerates
	 * many times per second). Origin changes request a scan too (P4): the same idle wait makes holding + on the
	 * origin give one scan. Only the state's inputs (exclusion boxes, palette, scan epoch) are read here; the
	 * preparation itself runs on scanPreparation.
	 */
	private void prepareScan(ShapeSet shapeSet, Shape validatable, ValidationState state, boolean manual) {
		if(!manual) {
			if(!state.isScanDue(System.currentTimeMillis(), autoScanIdleMillis)) return;
			if(validatable.getHowLongAgoCompletedMillis() < autoScanIdleMillis) return;
		}
		long started = System.nanoTime();
		// Exclusion boxes of the shape set (local coords): excluded positions are neither expected nor scanned
		state.setExclusionBoxes(shapeSet.getActiveExclusionBoxes());
		state.setPalette(paletteOf(validatable)); // before the SliceScan, which takes it
		state.setWater(validatable.waterTest()); // the same: the positions that want water (the farm's holes)
		long epoch = state.getScanEpoch(), generation = validatable.getGeneration();
		LayerPalette palette = state.getPalette();
		List<int[]> boxes = state.getExclusionBoxes();
		boolean nearErrors = state.isCheckErrors();
		int ox = shapeSet.getOriginX(), oy = shapeSet.getOriginY(), oz = shapeSet.getOriginZ();
		// The copy of the expected set too runs on the worker, under the shape's lock (a frame that finds the lock
		// taken draws the shape as during a generation). Should a newer generation be there by then, the scan is
		// stale (its epoch and generation are this frame's) and is dropped
		Future<SliceScan> future = scanPreparation.submit(() -> {
			List<Long> expected = null;
			long[] raw = null;
			validatable.lock.lock();
			try {
				// The shape's own set (LongHashSet): one array copy while the lock is held (performance step 4)
				if(validatable.getExpectedBlocks() instanceof LongHashSet set) raw = set.toLongArray();
				else expected = Arrays.asList(validatable.getExpectedBlocks().toArray(new Long[0]));
			}finally {
				validatable.lock.unlock();
			}
			if(raw != null) {
				expected = new ArrayList<Long>(raw.length);
				for(long pos: raw) expected.add(pos);
			}
			return SliceScan.prepare(state, expected, ox, oy, oz, generation, epoch, palette, boxes, nearErrors);
		});
		preparing.put(state, new PendingScan(future, epoch, generation, manual, System.nanoTime() - started));
	}

	/**
	 * The prepared scan, once it is ready and (automatic scans) the chunks under the shape are loaded: a scan of
	 * unloaded chunks would read everything as air, so it waits, prepared, and the request stays pending. Null
	 * while it must wait, and when nothing is tracked (every position excluded: no scan).
	 */
	private SliceScan startScan(ValidationState state, PendingScan pending, ClientLevel world) {
		if(!pending.future.isDone()) return null;
		SliceScan scan;
		try {
			scan = pending.future.get();
		}catch(Exception e) {
			preparing.remove(state);
			BuildGuide.logHandler.debugThrowable("The validation scan could not be prepared.", e);
			return null;
		}
		if(scan == null) {
			preparing.remove(state);
			state.consumeScanRequest();
			return null;
		}
		int[] b = scan.worldBounds();
		if(!pending.manual && !world.hasChunksAt(new BlockPos(b[0], b[1], b[2]), new BlockPos(b[3], b[4], b[5]))) return null; // stays pending
		preparing.remove(state);
		state.consumeScanRequest();
		scan.setStartNanos(pending.startNanos);
		state.setActiveScan(scan);
		return scan;
	}

	// Palette per layer (4D): the generation's layers and block ids, each id resolved once to the block's raw
	// registry id (an id that is not a block, or air, counts as no block: any solid). Null without a palette
	private static LayerPalette paletteOf(Shape shape) {
		ShapeLayers layers = shape.getLayers();
		String[] ids = shape.getPalette();
		if(layers == null || ids == null) return null;
		int[] raw = new int[ShapeLayers.COUNT];
		for(int i = 0;i < raw.length;++i) raw[i] = i < ids.length ? rawBlockId(ids[i]) : LayerPalette.NONE;
		LayerPalette palette = new LayerPalette(layers, raw);
		return palette.isEmpty() ? null : palette;
	}

	private static int rawBlockId(String id) {
		if(id == null || id.isEmpty()) return LayerPalette.NONE;
		Identifier key = Identifier.tryParse(id);
		if(key == null) return LayerPalette.NONE;
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(key);
		return block.isPresent() && !block.get().defaultBlockState().isAir() ? BuiltInRegistries.BLOCK.getId(block.get()) : LayerPalette.NONE;
	}
	// Safety net (StateReconciler): block events miss some server-side changes, so a few tracked
	// positions are re-read from the world every 250 ms. Runs under the shape's lock, right after validateShape
	@Override
	protected void reconcileShape(ShapeSet shapeSet) {
		ValidationState state = shapeSet.getShape().getValidationState();
		long now = System.currentTimeMillis();
		if(!state.isReconcileDue(now, StateReconciler.intervalMillis)) return;
		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;
		state.markReconciled(now);
		StateReconciler.run(state, new WorldProbe(world), StateReconciler.checksPerPass, line -> BuildGuide.logHandler.debugOrHigher(line));
	}

	// Area 3 (placing into the guideline): the view ray of this frame, filled by beginPlacementFrame when
	// the mode is on; every rendered set contributes its nearest cell, the nearest of all is published
	private boolean placeFrame = false;
	private double eyeX, eyeY, eyeZ, lookX, lookY, lookZ, placeReach, realHit;
	private double[] playerBox;
	private WorldProbe placeProbe;
	private GuidelinePicker.Target placeBest;
	// TargetOutline (white faces, black edges) for the target's cube size; rebuilt only when the size
	// changes, moved per frame
	private ShapeBuffer placeOutline;
	private double placeOutlineSize = Double.NaN;

	@Override
	protected void beginPlacementFrame() {
		placeFrame = false;
		placeBest = null;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if(player == null || mc.level == null || !BuildGuide.stateManager.getState().placeMode) return;
		// No target for a spectator or when the camera is not the player (free camera, spectating a mob)
		if(player.isSpectator() || mc.getCameraEntity() != player) return;
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partialTick), look = player.getViewVector(partialTick);
		eyeX = eye.x;
		eyeY = eye.y;
		eyeZ = eye.z;
		lookX = look.x;
		lookY = look.y;
		lookZ = look.z;
		// The real hit of this frame (GameRenderer.pick ran before the world render): a block or an entity
		HitResult hit = mc.hitResult;
		realHit = hit == null || hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : hit.getLocation().distanceTo(eye);
		AABB box = player.getBoundingBox();
		playerBox = new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
		placeReach = player.blockInteractionRange();
		placeProbe = new WorldProbe(mc.level);
		placeFrame = true;
	}

	// Under the shape's lock (AbstractRenderHandler.renderShapeSet): the expected set is not being regenerated
	@Override
	protected void pickPlacementTarget(ShapeSet shapeSet) {
		if(!placeFrame) return;
		Shape shape = shapeSet.getShape();
		// Build focus: only the cells the world shows can be targeted
		GuidelinePicker.Cells cells = GuidelinePicker.shapeCells(shape.getExpectedBlocks(), shapeSet.getOriginX(), shapeSet.getOriginY(), shapeSet.getOriginZ(), shapeSet.getActiveExclusionBoxes(), shape.worldFocusTest());
		GuidelinePicker.Target target = GuidelinePicker.pick(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, placeReach, realHit, playerBox, cells, placeProbe);
		// The set's cube size travels with the target, for the outline only: the choice is unchanged
		if(target != null && (placeBest == null || target.distance < placeBest.distance)) placeBest = new GuidelinePicker.Target(target.x, target.y, target.z, target.distance, shapeSet.getShapeCubeSize());
	}

	@Override
	protected void endPlacementFrame() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null) return;
		BuildGuide.stateManager.getState().placeTarget = placeBest;
		PlacementClick.noteTarget(placeBest);
		if(placeBest == null) return;
		if(placeOutline == null || placeOutlineSize != placeBest.cubeSize) {
			if(placeOutline != null) placeOutline.close();
			placeOutline = new ShapeBuffer();
			TargetOutline.build(placeOutline, placeBest.cubeSize);
			placeOutline.end();
			placeOutlineSize = placeBest.cubeSize;
		}
		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		RenderSystem.getModelViewStack().pushMatrix();
		RenderSystem.getModelViewStack().translate((float) (placeBest.x - camera.x), (float) (placeBest.y - camera.y), (float) (placeBest.z - camera.z));
		placeOutline.render();
		RenderSystem.getModelViewStack().popMatrix();
	}

	// Block ids the user chose to ignore (Configuration screen), resolved through the registry here so common stays
	// Minecraft-free. Performance step 4: resolved to the blocks once per change of the setting and compared by
	// identity; the scan asks this for every solid block it reads, and building "namespace:path" each time made a
	// String per block (hundreds of thousands per scan over terrain)
	private record IgnoredBlocks(String source, Block[] blocks) {}
	private static volatile IgnoredBlocks ignored = new IgnoredBlocks(null, new Block[0]);

	public static boolean isIgnored(BlockState state) {
		IgnoredBlocks current = ignored;
		String source = BuildGuide.config.ignoredBlocks.value;
		if(current.source() != source) current = resolveIgnored(source);
		Block block = state.getBlock();
		for(Block b: current.blocks()) if(b == block) return true;
		return false;
	}

	private static IgnoredBlocks resolveIgnored(String source) {
		List<Block> found = new ArrayList<Block>();
		for(Block block: BuiltInRegistries.BLOCK) if(BuildGuide.config.ignoredBlocks.contains(BuiltInRegistries.BLOCK.getKey(block).toString())) found.add(block);
		IgnoredBlocks resolved = new IgnoredBlocks(source, found.toArray(new Block[0]));
		ignored = resolved;
		return resolved;
	}

	public static RenderPipeline getRenderPipeline() {
		return BuildGuide.stateManager.getState().isDepthTest() ? BUILD_GUIDE_DEPTH_TEST : BUILD_GUIDE;
	}
}
