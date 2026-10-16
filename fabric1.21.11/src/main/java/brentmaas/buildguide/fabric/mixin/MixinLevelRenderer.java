package brentmaas.buildguide.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.fabric.RenderHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
	@Shadow
	private LevelTargetBundle targets;
	
	@Inject(method = "addWeatherPass", at = @At(value = "RETURN", shift = At.Shift.BEFORE), remap = false)
	private void addWeatherPassEnd(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo callbackInfo) {
		FramePass framePass = frameGraphBuilder.addPass(BuildGuide.modid);
		this.targets.main = framePass.readsAndWrites(this.targets.main);

		framePass.executes(() -> {
			BuildGuide.renderHandler.render();
		});
	}

	// The frustum renderLevel culls the terrain with in this frame, for the world buffer's sections. Sodium
	// replaces the terrain culling but still calls this; require = 0: should another mod remove it, the
	// sections are culled by distance only
	@Inject(method = "prepareCullFrustum", at = @At("RETURN"), require = 0)
	private void buildguide$cullFrustum(Matrix4f modelView, Matrix4f projection, Vec3 camera, CallbackInfoReturnable<Frustum> callbackInfo) {
		RenderHandler.noteCullFrustum(callbackInfo.getReturnValue());
	}
}
