package brentmaas.buildguide.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import brentmaas.buildguide.fabric.validation.IncrementalValidator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block-change hooks of the incremental validation. What they cover (1.21.11, read from the
 * decompiled ClientLevel):
 *  - ClientLevel.setBlock: the placement and destruction the client predicts, and syncBlockState,
 *    which the server's acknowledgement runs to correct a wrong prediction (it calls setBlock).
 *  - ClientLevel.setServerVerifiedBlockState: every BlockUpdate and section update from the server.
 *    With a prediction pending for that position it only stores the server's state for the ack and
 *    changes nothing; without one it calls Level.setBlock through invokespecial, which skips the
 *    setBlock override above. That is why this second hook exists: before it, a server change
 *    to a position the player had no prediction for (a falling block, another player, fire, an
 *    update arriving after its prediction was settled) never reached the validation.
 * Not covered: chunk loads (the full Validate scan reads those) and anything that writes chunks
 * directly. StateReconciler is the safety net for whatever still slips through.
 */
@Mixin(ClientLevel.class)
public class MixinClientLevel {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void buildguide$onSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		if(cir.getReturnValue()) IncrementalValidator.onBlockChanged(pos, state);
	}

	// Reads the block that is in the world afterwards, not the requested one. Idempotent with the hook
	// above (ValidationState.updateBlock changes nothing when told the same thing twice), and harmless
	// when the update was only stored for a pending prediction: the world is then unchanged
	@Inject(method = "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("RETURN"))
	private void buildguide$onServerVerified(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		IncrementalValidator.onBlockChanged(pos, ((ClientLevel) (Object) this).getBlockState(pos));
	}
}
