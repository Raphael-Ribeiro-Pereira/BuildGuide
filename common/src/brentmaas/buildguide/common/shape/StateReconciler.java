package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
import java.util.function.Consumer;

/**
 * Safety net for the incremental validation. Block events (MixinClientLevel) are an optimisation
 * and some server-side changes never reach them; the world is the truth. A pass re-reads
 *  - every tracked structure error (few) and drops those that are no longer a solid, non-ignored block;
 *  - up to `budget` tracked positions, round robin from where the last pass stopped, and corrects
 *    OK / MISSING / IGNORED when the world says otherwise.
 * Only positions whose chunk is loaded are judged (an unloaded chunk reads as air and must not erase
 * errors or turn blocks into missing ones). Nothing is done on an unvalidated state (regenerating or
 * being scanned) or with a scan pending: the scan reads the world anyway. Every correction is a line
 * for `log` (at most ValidationState.maxLoggedCorrections per state), which is also the diagnosis of
 * whatever bypassed the events. Positions are local to the scan origin, like the whole state.
 *
 * Only primitives and java.*: no net.minecraft.
 */
public final class StateReconciler {
	// Positions re-read per pass; passes run every intervalMillis per validated shape (render thread)
	public static final int checksPerPass = 4000;
	public static final long intervalMillis = 250;

	private StateReconciler() {}

	// Returns the number of corrections made
	public static int run(ValidationState vs, IBlockProbe probe, int budget, Consumer<String> log) {
		if(!vs.isValidated() || vs.isScanRequested()) return 0;
		int ox = vs.getScanOriginX(), oy = vs.getScanOriginY(), oz = vs.getScanOriginZ();
		int fixed = 0;

		for(NearBlock nb: vs.getNearBlocks()) {
			int x = ox + LocalPos.unpackX(nb.localPos), y = oy + LocalPos.unpackY(nb.localPos), z = oz + LocalPos.unpackZ(nb.localPos);
			if(!probe.isLoaded(x, y, z)) continue;
			int f = probe.flags(x, y, z);
			if((f & IBlockProbe.FLAG_AIR) != 0 || (f & IBlockProbe.FLAG_SOLID) == 0 || (f & IBlockProbe.FLAG_IGNORED) != 0) {
				if(vs.removeNearBlock(nb.localPos)) {
					++fixed;
					report(vs, log, x, y, z, "structure error (" + nb.blockName + ")", "no longer a solid block");
				}
			}
		}

		long[] tracked = vs.getTrackedPositions();
		int n = tracked.length;
		if(n > 0) {
			int cursor = vs.getReconcileCursor() % n;
			int steps = Math.min(budget, n);
			for(int i = 0;i < steps;++i) {
				long pos = tracked[(cursor + i) % n];
				if(!vs.isTracked(pos)) continue; // excluded since the scan
				int x = ox + LocalPos.unpackX(pos), y = oy + LocalPos.unpackY(pos), z = oz + LocalPos.unpackZ(pos);
				if(!probe.isLoaded(x, y, z)) continue;
				int f = probe.flags(x, y, z);
				boolean air = (f & IBlockProbe.FLAG_AIR) != 0;
				// The scan's rule: ignored type -> IGNORED, solid -> OK, anything else -> MISSING
				byte real = !air && (f & IBlockProbe.FLAG_IGNORED) != 0 ? ValidationState.IGNORED : (f & IBlockProbe.FLAG_SOLID) != 0 ? ValidationState.OK : ValidationState.MISSING;
				byte old = vs.getStatus(pos);
				if(real != old) {
					vs.setStatus(pos, real, real == ValidationState.IGNORED ? probe.name(x, y, z) : null);
					++fixed;
					report(vs, log, x, y, z, statusName(old), statusName(real));
				}
			}
			vs.setReconcileCursor((cursor + steps) % n);
		}

		vs.addReconcileCorrections(fixed);
		return fixed;
	}

	private static void report(ValidationState vs, Consumer<String> log, int x, int y, int z, String was, String now) {
		int slot = vs.takeLogSlot();
		if(slot == 0 || log == null) return;
		log.accept("[Build Guide] safety net corrected [" + x + ", " + y + ", " + z + "]: was " + was + ", world says " + now);
		if(slot == 2) log.accept("[Build Guide] safety net: " + ValidationState.maxLoggedCorrections + " corrections logged for this shape, the next ones are not");
	}

	private static String statusName(byte status) {
		switch(status) {
		case ValidationState.OK:
			return "OK";
		case ValidationState.MISSING:
			return "MISSING";
		case ValidationState.IGNORED:
			return "IGNORED";
		default:
			return "UNKNOWN";
		}
	}
}
