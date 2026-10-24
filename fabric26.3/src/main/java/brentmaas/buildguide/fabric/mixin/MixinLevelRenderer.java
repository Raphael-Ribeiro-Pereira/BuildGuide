package brentmaas.buildguide.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import brentmaas.buildguide.common.BuildGuide;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;

/**
 * Minecraft 26.3 has no frame graph pass to add to any more (the weather pass went with it): the world is drawn
 * right before the entity outline, which is the hook the 26.3 build of the mod uses too. The frustum the terrain
 * is culled with is not captured here (prepareCullFrustum went with the rewrite): the world buffer's sections are
 * culled by distance only, see RenderHandler.noteCullFrustum.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
	@Inject(method = "executeOutline", at = @At("HEAD"), remap = false)
	private void executeOutlineBegin(PreparedFrame featureFrame, CallbackInfo callbackInfo) {
		BuildGuide.renderHandler.render();
	}
}
