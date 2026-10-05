package brentmaas.buildguide.fabric.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import brentmaas.buildguide.fabric.place.PlacementClick;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/**
 * Area 3: a right click fills the guideline cell aimed at. Every read of the hitResult field inside
 * startUseItem goes through PlacementClick.substitute, which may hand back a click on the target cell.
 * A value substitution, not a cancel: no field is written and nothing has to be restored, so the
 * other mods hooking startUseItem in Raphael's pack (Effortless Building and Tweakeroo cancel at the
 * head, Litematica after getCount, Click Through Plus assigns hitResult before getItemInHand) keep
 * their behaviour; when one of them cancels, the click is simply theirs.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {
	@ModifyExpressionValue(method = "startUseItem", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", opcode = Opcodes.GETFIELD))
	private HitResult buildguide$guidelineTarget(HitResult original) {
		return PlacementClick.substitute((Minecraft) (Object) this, original);
	}
}
