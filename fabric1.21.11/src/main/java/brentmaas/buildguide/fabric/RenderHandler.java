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
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ValidationOverlay;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.block.state.BlockState;
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
			ValidationOverlay.build(buffer, state, new ShapeSet.Origin(player.x - shapeSet.getOriginX(), player.y - shapeSet.getOriginY(), player.z - shapeSet.getOriginZ()));
			buffer.end();
			shape.overlayBuffer = buffer;
			shape.overlayVersion = version;
			shape.overlayBuiltAt = now;
		}
		((ShapeBuffer) shape.overlayBuffer).render();
	}
	
	protected void validateShape(ShapeSet shapeSet) {
		Shape validatable = shapeSet.getShape(); // every Shape is IValidatable
		ValidationState state = validatable.getValidationState();
		// Manual (button) scans run now. Automatic ones, requested by the shape after it regenerated,
		// wait until the shape has been idle for a moment (holding +/- regenerates many times per
		// second) and until the chunks under the shape are loaded (a scan of unloaded chunks would
		// read everything as air)
		boolean manual = validatable.consumeValidateRequest();
		if(!manual) {
			if(!state.isScanRequested()) return;
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
		state.beginScan(validatable.getExpectedBlocks());

		// Classify expected positions into the state: air -> missing, ignored type -> ignored (counts as
		// missing), solid -> ok, otherwise wrong
		for(long wl: expectedWorld) {
			BlockPos wp = BlockPos.of(wl);
			long local = LocalPos.pack(wp.getX() - ox, wp.getY() - oy, wp.getZ() - oz);
			BlockState st = world.getBlockState(wp);
			if(st.isAir()) state.setStatus(local, ValidationState.MISSING, null);
			else if(isIgnored(st)) state.setStatus(local, ValidationState.IGNORED, st.getBlock().getName().getString());
			else if(st.blocksMotion()) state.setStatus(local, ValidationState.OK, null);
			else state.setStatus(local, ValidationState.WRONG, st.getBlock().getName().getString());
		}

		// Solid blocks near (within 2) the shape but not part of it, likely misplaced
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

	// Block ids the user chose to ignore (Configuration screen), resolved through the registry here so common stays Minecraft-free
	public static boolean isIgnored(BlockState state) {
		return BuildGuide.config.ignoredBlocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}
	
	// One-line chat summary read from the state; positions will be shown in the GUI (2.4), not logged
	private void logValidation(ValidationState state) {
		BuildGuide.logHandler.sendChatMessage("[Build Guide] Validate - ok " + state.getOk() + ", missing " + state.getMissing() + ", wrong " + state.getWrong() + ", near " + state.getNearCount());
	}

	public static RenderPipeline getRenderPipeline() {
		return BuildGuide.stateManager.getState().isDepthTest() ? BUILD_GUIDE_DEPTH_TEST : BUILD_GUIDE;
	}
}
