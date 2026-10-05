package brentmaas.buildguide.fabric;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;

import brentmaas.buildguide.common.AbstractRenderHandler;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.shape.GuidelinePicker;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.StateReconciler;
import brentmaas.buildguide.common.shape.TargetOutline;
import brentmaas.buildguide.common.shape.ValidationOverlay;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
import brentmaas.buildguide.fabric.place.PlacementClick;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import brentmaas.buildguide.fabric.validation.WorldProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
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

	public void renderShapeBuffer(Shape shape) {
		((ShapeBuffer) shape.buffer).render();
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
			ValidationOverlay.build(buffer, state, new ShapeSet.Origin(player.x - state.getScanOriginX(), player.y - state.getScanOriginY(), player.z - state.getScanOriginZ()));
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
	
	protected void validateShape(ShapeSet shapeSet) {
		Shape validatable = shapeSet.getShape(); // every Shape is IValidatable
		ValidationState state = validatable.getValidationState();
		// Manual (button) scans run now. Automatic ones, requested by the shape after it regenerated,
		// wait until the shape has been idle for a moment (holding +/- regenerates many times per
		// second) and until the chunks under the shape are loaded (a scan of unloaded chunks would
		// read everything as air). Origin changes request a scan too (P4): the same idle wait makes
		// holding + on the origin give one scan
		boolean manual = validatable.consumeValidateRequest();
		if(!manual) {
			if(!state.isScanDue(System.currentTimeMillis(), autoScanIdleMillis)) return;
			if(shapeSet.getShape().getHowLongAgoCompletedMillis() < autoScanIdleMillis) return;
		}

		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;

		int ox = shapeSet.getOriginX();
		int oy = shapeSet.getOriginY();
		int oz = shapeSet.getOriginZ();

		// Expected blocks in world coordinates (mapped back to local for the state), plus their bounding box
		Set<Long> expectedWorld = new HashSet<Long>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		// Exclusion boxes of the shape set (local coords): excluded positions are neither expected nor scanned
		state.setExclusionBoxes(shapeSet.getActiveExclusionBoxes());
		for(long local: validatable.getExpectedBlocks()) {
			int lx = LocalPos.unpackX(local), ly = LocalPos.unpackY(local), lz = LocalPos.unpackZ(local);
			if(state.isExcluded(lx, ly, lz)) continue;
			int wx = ox + lx;
			int wy = oy + ly;
			int wz = oz + lz;
			expectedWorld.add(BlockPos.asLong(wx, wy, wz));
			if(wx < minX) minX = wx;
			if(wx > maxX) maxX = wx;
			if(wy < minY) minY = wy;
			if(wy > maxY) maxY = wy;
			if(wz < minZ) minZ = wz;
			if(wz > maxZ) maxZ = wz;
		}
		if(expectedWorld.isEmpty()) {
			state.consumeScanRequest();
			return;
		}
		if(!manual && !world.hasChunksAt(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ))) return; // stays pending
		state.consumeScanRequest();
		state.beginScan(validatable.getExpectedBlocks(), ox, oy, oz);

		// Classify expected positions into the state: ignored type -> ignored (counts as missing), solid ->
		// ok, anything else (air, torch, flower, water) -> missing
		for(long wl: expectedWorld) {
			BlockPos wp = BlockPos.of(wl);
			long local = LocalPos.pack(wp.getX() - ox, wp.getY() - oy, wp.getZ() - oz);
			BlockState st = world.getBlockState(wp);
			if(!st.isAir() && isIgnored(st)) state.setStatus(local, ValidationState.IGNORED, st.getBlock().getName().getString());
			else if(!st.isAir() && st.blocksMotion()) state.setStatus(local, ValidationState.OK, null);
			else state.setStatus(local, ValidationState.MISSING, null);
		}

		// Structure errors: solid blocks within 2 of the shape but not part of it, outside it or inside a
		// hollow one; they deform the geometric form
		List<NearBlock> near = new ArrayList<NearBlock>();
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
		for(int x = minX - 2;x <= maxX + 2;++x) {
			for(int y = minY - 2;y <= maxY + 2;++y) {
				for(int z = minZ - 2;z <= maxZ + 2;++z) {
					if(expectedWorld.contains(BlockPos.asLong(x, y, z))) continue;
					if(state.isExcluded(x - ox, y - oy, z - oz)) continue; // excluded cells are not even read: this is where the ground under a bridge stops costing
					BlockState st = world.getBlockState(mpos.set(x, y, z));
					if(st.isAir() || !st.blocksMotion() || isIgnored(st)) continue;

					double best = Double.MAX_VALUE;
					for(int dx = -2;dx <= 2;++dx) {
						for(int dy = -2;dy <= 2;++dy) {
							for(int dz = -2;dz <= 2;++dz) {
								int d2 = dx * dx + dy * dy + dz * dz;
								if(d2 == 0 || d2 > 4) continue;
								if(expectedWorld.contains(BlockPos.asLong(x + dx, y + dy, z + dz))) {
									double d = Math.sqrt(d2);
									if(d < best) best = d;
								}
							}
						}
					}
					if(best <= 2.0) near.add(new NearBlock(LocalPos.pack(x - ox, y - oy, z - oz), st.getBlock().getName().getString(), (float) best));
				}
			}
		}

		state.setNearBlocks(near);
		state.endScan();
		logValidation(state);
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
		GuidelinePicker.Cells cells = GuidelinePicker.shapeCells(shape.getExpectedBlocks(), shapeSet.getOriginX(), shapeSet.getOriginY(), shapeSet.getOriginZ(), shapeSet.getActiveExclusionBoxes());
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

	// Block ids the user chose to ignore (Configuration screen), resolved through the registry here so common stays Minecraft-free
	public static boolean isIgnored(BlockState state) {
		return BuildGuide.config.ignoredBlocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}
	
	// One-line chat summary read from the state; positions will be shown in the GUI (2.4), not logged
	private void logValidation(ValidationState state) {
		BuildGuide.logHandler.sendChatMessage("[Build Guide] Validate - ok " + state.getOk() + ", missing " + state.getMissing() + ", errors " + state.getNearCount());
	}

	public static RenderPipeline getRenderPipeline() {
		return BuildGuide.stateManager.getState().isDepthTest() ? BUILD_GUIDE_DEPTH_TEST : BUILD_GUIDE;
	}
}
