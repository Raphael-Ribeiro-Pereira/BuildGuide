package brentmaas.buildguide.fabric;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import brentmaas.buildguide.common.shape.ShapeCone;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.fabric.shape.ShapeBuffer;
import brentmaas.buildguide.fabric.validation.NearBlock;
import brentmaas.buildguide.fabric.validation.ValidationResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class RenderHandler extends AbstractRenderHandler {
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

	protected void validateShape(ShapeSet shapeSet) {
		if(!(shapeSet.getShape() instanceof ShapeCone cone)) return;
		if(!cone.consumeValidateRequest()) return;

		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;

		int ox = shapeSet.getOriginX();
		int oy = shapeSet.getOriginY();
		int oz = shapeSet.getOriginZ();

		// Expected blocks in world coordinates, plus their bounding box
		Set<Long> expectedWorld = new HashSet<Long>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for(long local: cone.getExpectedBlocks()) {
			int wx = ox + ShapeCone.unpackLocalX(local);
			int wy = oy + ShapeCone.unpackLocalY(local);
			int wz = oz + ShapeCone.unpackLocalZ(local);
			expectedWorld.add(BlockPos.asLong(wx, wy, wz));
			if(wx < minX) minX = wx;
			if(wx > maxX) maxX = wx;
			if(wy < minY) minY = wy;
			if(wy > maxY) maxY = wy;
			if(wz < minZ) minZ = wz;
			if(wz > maxZ) maxZ = wz;
		}
		if(expectedWorld.isEmpty()) return;

		// Count expected positions: air -> missing, non-solid -> wrong, otherwise ok
		int ok = 0, missing = 0, wrong = 0;
		for(long wl: expectedWorld) {
			BlockState st = world.getBlockState(BlockPos.of(wl));
			if(st.isAir()) ++missing;
			else if(st.blocksMotion()) ++ok;
			else ++wrong;
		}

		// Solid blocks near (within 2) the shape but not part of it, likely misplaced
		List<NearBlock> near = new ArrayList<NearBlock>();
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
		for(int x = minX - 2;x <= maxX + 2;++x) {
			for(int y = minY - 2;y <= maxY + 2;++y) {
				for(int z = minZ - 2;z <= maxZ + 2;++z) {
					if(expectedWorld.contains(BlockPos.asLong(x, y, z))) continue;
					BlockState st = world.getBlockState(mpos.set(x, y, z));
					if(st.isAir() || !st.blocksMotion()) continue;

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
					if(best <= 2.0) near.add(new NearBlock(x, y, z, st.getBlock().getName().getString(), (float) best));
				}
			}
		}

		logValidation(new ValidationResult(ok, missing, wrong, near));
	}

	private void logValidation(ValidationResult result) {
		BuildGuide.logHandler.sendChatMessage("[Build Guide] Validate - ok: " + result.blocksOk + ", missing: " + result.blocksMissing + ", wrong: " + result.blocksWrong + ", near: " + result.nearBlocks.size());
		int shown = 0;
		for(NearBlock nb: result.nearBlocks) {
			if(shown >= 10) {
				BuildGuide.logHandler.sendChatMessage("  ... and " + (result.nearBlocks.size() - 10) + " more near blocks");
				break;
			}
			++shown;
			BuildGuide.logHandler.sendChatMessage("  near [" + nb.x + ", " + nb.y + ", " + nb.z + "] " + nb.blockName + " (d=" + String.format(Locale.ROOT, "%.1f", nb.distance) + ")");
		}
	}

	public static RenderPipeline getRenderPipeline() {
		return BuildGuide.stateManager.getState().isDepthTest() ? BUILD_GUIDE_DEPTH_TEST : BUILD_GUIDE;
	}
}
