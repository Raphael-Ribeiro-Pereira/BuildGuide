package brentmaas.buildguide.fabric.place;

import java.util.Locale;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State;
import brentmaas.buildguide.common.shape.GuidelinePicker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Area 3: a right click fills the guideline cell aimed at. This class holds the short diagnostic
 * log (at most maxLogLines lines in latest.log, then a notice): when a target appears, when the
 * click's hitResult is replaced and when a click is discarded.
 */
public final class PlacementClick {
	public static final int maxLogLines = 50;
	private static int logged = 0;
	private static boolean hadTarget = false;

	// The use key's state at the end of the previous client tick: a click counts only on the press itself
	// (one click, one block), not on vanilla's repeat while the key is held
	private static boolean useDownLastTick = false;
	// The last logged click event, so the several hitResult reads of one click log once
	private static String lastEvent = null;

	private PlacementClick() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> useDownLastTick = mc.options.keyUse.isDown());
	}

	/**
	 * MixinMinecraft hands every read of Minecraft.hitResult inside startUseItem to this method. It
	 * returns a click on the target cell (top face, as vanilla would for a click on that face) when
	 * placing into the guideline is on, a target exists, the main hand holds a BlockItem and the player
	 * is not a spectator; a miss (nothing happens with a block in hand) when that click must be
	 * discarded; otherwise the original value untouched. Pure value substitution: nothing to restore,
	 * other mods' hooks on startUseItem keep working (and see this value).
	 */
	public static HitResult substitute(Minecraft mc, HitResult original) {
		LocalPlayer player = mc.player;
		if(player == null || mc.level == null) return original;
		State state = BuildGuide.stateManager.getState();
		GuidelinePicker.Target target = state.placeTarget;
		if(!state.placeMode || target == null || player.isSpectator() || !(player.getMainHandItem().getItem() instanceof BlockItem)) return original;
		BlockPos pos = new BlockPos(target.x, target.y, target.z);
		long tick = mc.level.getGameTime();
		if(useDownLastTick) {
			event(tick, "click discarded at " + at(pos) + ": the use key was already down last tick (one click, one block)");
			return BlockHitResult.miss(Vec3.atCenterOf(pos), Direction.UP, pos);
		}
		// The target is from the last frame; a second click in the same tick finds the block just placed
		if(!mc.level.getBlockState(pos).canBeReplaced()) {
			event(tick, "click discarded at " + at(pos) + ": the cell is no longer empty");
			return BlockHitResult.miss(Vec3.atCenterOf(pos), Direction.UP, pos);
		}
		event(tick, "hitResult replaced: " + at(pos) + " instead of " + (original == null ? "null" : original.getType().toString()));
		return new BlockHitResult(new Vec3(target.x + 0.5, target.y + 1.0, target.z + 0.5), Direction.UP, pos, false);
	}

	private static String at(BlockPos pos) {
		return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
	}

	private static void event(long tick, String message) {
		String key = tick + message;
		if(key.equals(lastEvent)) return;
		lastEvent = key;
		log(message);
	}

	// Called once per frame with the published target: logs only when a target appears after none
	public static void noteTarget(GuidelinePicker.Target target) {
		boolean has = target != null;
		if(has && !hadTarget) log(String.format(Locale.ROOT, "target [%d, %d, %d] at %.2f blocks", target.x, target.y, target.z, target.distance));
		hadTarget = has;
	}

	static void log(String message) {
		if(logged >= maxLogLines) return;
		++logged;
		String line = "[Build Guide] place: " + message;
		if(logged == maxLogLines) line += " (diagnostic limit of " + maxLogLines + " lines reached, no more place lines)";
		BuildGuide.logHandler.debugOrHigher(line);
	}
}
