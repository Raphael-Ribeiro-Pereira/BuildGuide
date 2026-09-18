package brentmaas.buildguide.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import brentmaas.buildguide.fabric.validation.IncrementalValidator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single choke point for block changes on the client: server block/section updates, local
 * placement prediction and block destruction all end in ClientLevel.setBlock. Chunk loads do
 * not, which is what the full Validate scan is for.
 */
@Mixin(ClientLevel.class)
public class MixinClientLevel {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void buildguide$onSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		if(cir.getReturnValue()) IncrementalValidator.onBlockChanged(pos, state);
	}
}
