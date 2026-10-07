package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * The validation scan in Y layers, bottom to top, spread over frames: each frame processes layers until
 * its time budget is spent, at least one. A layer is the expected positions at that y (classified) and
 * the cells of the expanded bounding box at that y (structure errors). Results stay here until the last
 * layer; the state keeps its previous totals meanwhile and gets the new ones in one go (publish), with
 * exactly the calls an instantaneous scan makes, so the outcome is identical.
 *
 * Why slicing does not change the result: a structure error at a cell depends only on that cell's block
 * and on the expected set, which is fixed for the whole scan (a new generation, an invalidation or a new
 * scan request makes the scan stale, and the loader starts over). Blocks that change during the scan are
 * reported with markDirty and read again when publishing, so the result is the one of an instantaneous
 * scan of the final world.
 *
 * Only primitives and java.*: no net.minecraft. The world is read through IBlockProbe, in world
 * coordinates (local + origin).
 */
public class SliceScan {
	// Configuration "Scan speed": the time budget per frame. Instant scans everything in one frame (as before)
	public enum Speed{
		INSTANT(0),
		FAST(12),
		NORMAL(6),
		SLOW(3);

		public final int budgetMillis;

		Speed(int budgetMillis) {
			this.budgetMillis = budgetMillis;
		}
	}

	private final ValidationState state;
	private final long epoch, generation;
	private final int ox, oy, oz;
	// The shape's expected positions in its own order (beginScan), and the tracked ones (not excluded) in the
	// loader's order (setStatus): publishing in these orders keeps the state's lists exactly as before
	private final List<Long> beginOrder;
	private final List<Long> statusOrder;
	private final Set<Long> expected;
	private final int minX, minY, minZ, maxX, maxY, maxZ;
	private final Map<Integer, List<Long>> byLayer = new HashMap<Integer, List<Long>>();
	private int nextY;
	private final Map<Long, Byte> statuses = new HashMap<Long, Byte>();
	private final Map<Long, String> names = new HashMap<Long, String>();
	private final Map<Long, NearBlock> near = new HashMap<Long, NearBlock>();
	private final Set<Long> dirty = new LinkedHashSet<Long>();
	private int frames = 0;
	private long totalNanos = 0, maxSliceNanos = 0;
	// What the loader spent preparing this scan (outside the layers, in the frame the scan started), for the timing line
	private long startNanos = -1;

	/**
	 * @param shapeExpected the shape's expected positions (local), in its iteration order
	 * @param tracked the expected positions that are not excluded (local), in the loader's order
	 */
	public SliceScan(ValidationState state, Collection<Long> shapeExpected, List<Long> tracked, int ox, int oy, int oz, long generation) {
		this.state = state;
		this.epoch = state.getScanEpoch();
		this.generation = generation;
		this.ox = ox;
		this.oy = oy;
		this.oz = oz;
		this.beginOrder = new ArrayList<Long>(shapeExpected);
		this.statusOrder = new ArrayList<Long>(tracked);
		this.expected = new HashSet<Long>(tracked);
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for(long pos: statusOrder) {
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			b[0] = Math.min(b[0], x);
			b[1] = Math.min(b[1], y);
			b[2] = Math.min(b[2], z);
			b[3] = Math.max(b[3], x);
			b[4] = Math.max(b[4], y);
			b[5] = Math.max(b[5], z);
			byLayer.computeIfAbsent(y, k -> new ArrayList<Long>()).add(pos);
		}
		minX = b[0];
		minY = b[1];
		minZ = b[2];
		maxX = b[3];
		maxY = b[4];
		maxZ = b[5];
		nextY = minY - ValidationState.nearRadius;
	}

	// Stale: the expected set or the request changed (new generation, invalidation, new scan request)
	public boolean isStale(ValidationState current, long currentGeneration) {
		return current != state || current.getScanEpoch() != epoch || currentGeneration != generation;
	}

	public boolean isDone() {
		return nextY > maxY + ValidationState.nearRadius;
	}

	// Layers done, 0..100
	public int percent() {
		int total = maxY - minY + 1 + 2 * ValidationState.nearRadius;
		int done = nextY - (minY - ValidationState.nearRadius);
		return (int) Math.min(100, Math.max(0, 100L * done / Math.max(1, total)));
	}

	/**
	 * One frame of work: layers until budgetNanos is spent (measured with nanoClock), at least one. A budget
	 * of 0 or less means no limit (Instant). Returns whether the scan is complete.
	 */
	public boolean step(IBlockProbe probe, long budgetNanos, LongSupplier nanoClock) {
		long start = nanoClock.getAsLong();
		do {
			scanLayer(probe, nextY);
			++nextY;
		}while(!isDone() && (budgetNanos <= 0 || nanoClock.getAsLong() - start < budgetNanos));
		long spent = nanoClock.getAsLong() - start;
		++frames;
		totalNanos += spent;
		maxSliceNanos = Math.max(maxSliceNanos, spent);
		return isDone();
	}

	private void scanLayer(IBlockProbe probe, int y) {
		List<Long> layer = byLayer.get(y);
		if(layer != null) for(long pos: layer) classify(probe, pos);
		for(int x = minX - ValidationState.nearRadius;x <= maxX + ValidationState.nearRadius;++x) {
			for(int z = minZ - ValidationState.nearRadius;z <= maxZ + ValidationState.nearRadius;++z) {
				nearCell(probe, x, y, z);
			}
		}
	}

	// An expected position: ignored type -> IGNORED (with its name), solid -> OK, anything else -> MISSING
	private void classify(IBlockProbe probe, long pos) {
		int wx = LocalPos.unpackX(pos) + ox, wy = LocalPos.unpackY(pos) + oy, wz = LocalPos.unpackZ(pos) + oz;
		int f = probe.flags(wx, wy, wz);
		boolean air = (f & IBlockProbe.FLAG_AIR) != 0;
		if(!air && (f & IBlockProbe.FLAG_IGNORED) != 0) {
			statuses.put(pos, ValidationState.IGNORED);
			names.put(pos, probe.name(wx, wy, wz));
		}else {
			statuses.put(pos, !air && (f & IBlockProbe.FLAG_SOLID) != 0 ? ValidationState.OK : ValidationState.MISSING);
			names.remove(pos);
		}
	}

	// A cell that is not expected: a solid, non-ignored block within nearRadius of an expected one is a
	// structure error; anything else is not (and removes one recorded before, for a re-read)
	private void nearCell(IBlockProbe probe, int x, int y, int z) {
		long pos = LocalPos.pack(x, y, z);
		if(expected.contains(pos)) return;
		if(state.isExcluded(x, y, z)) return; // excluded cells are not even read
		int f = probe.nearFlags(x + ox, y + oy, z + oz);
		if((f & IBlockProbe.FLAG_AIR) != 0 || (f & IBlockProbe.FLAG_SOLID) == 0 || (f & IBlockProbe.FLAG_IGNORED) != 0) {
			near.remove(pos);
			return;
		}
		double best = Double.MAX_VALUE;
		int r = ValidationState.nearRadius;
		for(int dx = -r;dx <= r;++dx) {
			for(int dy = -r;dy <= r;++dy) {
				for(int dz = -r;dz <= r;++dz) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if(d2 == 0 || d2 > r * r) continue;
					if(expected.contains(LocalPos.pack(x + dx, y + dy, z + dz))) {
						double d = Math.sqrt(d2);
						if(d < best) best = d;
					}
				}
			}
		}
		if(best <= r) near.put(pos, new NearBlock(pos, probe.name(x + ox, y + oy, z + oz), (float) best));
		else near.remove(pos);
	}

	// A block changed during the scan (world coordinates): read it again when publishing
	public void markDirty(int wx, int wy, int wz) {
		int x = wx - ox, y = wy - oy, z = wz - oz;
		int r = ValidationState.nearRadius;
		if(x < minX - r || x > maxX + r || y < minY - r || y > maxY + r || z < minZ - r || z > maxZ + r) return;
		dirty.add(LocalPos.pack(x, y, z));
	}

	/**
	 * The result into the state, in one go: positions changed during the scan are read again, then the
	 * same calls as an instantaneous scan (beginScan with the shape's order, setStatus in the loader's
	 * order, the structure errors in x, y, z order, endScan).
	 */
	public void publish(IBlockProbe probe) {
		for(long pos: dirty) {
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(y >= nextY) continue; // not scanned yet: impossible once done, kept for safety
			if(expected.contains(pos)) classify(probe, pos);
			else nearCell(probe, x, y, z);
		}
		dirty.clear();
		state.beginScan(beginOrder, ox, oy, oz);
		for(long pos: statusOrder) {
			Byte s = statuses.get(pos);
			state.setStatus(pos, s == null ? ValidationState.MISSING : s, names.get(pos));
		}
		List<NearBlock> list = new ArrayList<NearBlock>(near.values());
		// The order an instantaneous scan finds them in: x, then y, then z
		list.sort((a, b) -> {
			int c = Integer.compare(LocalPos.unpackX(a.localPos), LocalPos.unpackX(b.localPos));
			if(c == 0) c = Integer.compare(LocalPos.unpackY(a.localPos), LocalPos.unpackY(b.localPos));
			if(c == 0) c = Integer.compare(LocalPos.unpackZ(a.localPos), LocalPos.unpackZ(b.localPos));
			return c;
		});
		state.setNearBlocks(list);
		state.endScan();
	}

	public int getFrames() {
		return frames;
	}

	public long getTotalMillis() {
		return totalNanos / 1000000;
	}

	public long getMaxSliceMillis() {
		return maxSliceNanos / 1000000;
	}

	public void setStartNanos(long nanos) {
		startNanos = nanos;
	}

	// -1 when the loader did not measure it
	public long getStartMillis() {
		return startNanos < 0 ? -1 : startNanos / 1000000;
	}

	public int getExpectedCount() {
		return statusOrder.size();
	}
}
