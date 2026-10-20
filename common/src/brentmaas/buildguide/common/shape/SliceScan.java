package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
 * layer; the state keeps its previous totals meanwhile and gets the new ones in one go (publish), the same
 * state the calls of an instantaneous scan leave, so the outcome is identical.
 *
 * Why slicing does not change the result: a structure error at a cell depends only on that cell's block
 * and on the expected set, which is fixed for the whole scan (a new generation, an invalidation or a new
 * scan request makes the scan stale, and the loader starts over). Blocks that change during the scan are
 * reported with markDirty and read again when publishing, so the result is the one of an instantaneous
 * scan of the final world.
 *
 * Cost (performance step 2): everything the constructor does reads no world, so the loader builds the scan on
 * a worker thread (prepare). The layers allocate nothing per block: positions are numbered by a LongIndex
 * (also the expected set), statuses and names live in arrays by that number, the exclusion boxes are a copy.
 * The status map the state will hold is filled here as the layers go, in the shape's order, and publish
 * swaps it in (ValidationState.publishScan) instead of writing 50 000 positions one by one in one frame.
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
	// The state's palette when the scan started (4D); a new palette comes with a new generation, so a new scan
	private final LayerPalette palette;
	private final long epoch, generation;
	private final int ox, oy, oz;
	// The tracked positions (not excluded) in the loader's order (the order of the state's index lists), numbered
	// by `index`: statuses[i], names[i] and present[i] belong to order.get(i)
	private final List<Long> order;
	private final LongIndex index;
	private final byte[] statuses;
	private final String[] names;
	// Tracked positions the shape's order holds too: the ones the state tracks (beginScan's rule)
	private final boolean[] present;
	// The state's next status map: the shape's order, UNKNOWN until classified
	private final HashMap<Long, Byte> statusMap;
	private final int[][] boxes;
	private final int minX, minY, minZ, maxX, maxY, maxZ;
	// Bounds of the positions the state tracks, for publishScan (beginScan's bounding box)
	private final int[] trackedBounds;
	// Per layer (y - minY) the numbers of its positions
	private final int[][] byLayer;
	// The tracked positions as a bitmap of the bounding box expanded by nearRadius, z fastest: the near pass asks
	// whether 32 neighbours of a cell are expected, and a bit next to its neighbours answers that from the cache.
	// Null when the box would take more than maxBitmapBits (then the index answers)
	public static final long maxBitmapBits = 1L << 28;
	private final long[] bitmap;
	private final int bx0, by0, bz0, bw, bh, bd;
	private int nextY;
	// Within layer nextY (performance step 4): how far it got, so a frame can stop inside a layer. Classifying its
	// expected positions comes first, classifyChunk at a time (nextClassify counts them), then the near sweep one
	// x row at a time (nextRow); a layer is one unit of work no more
	public static final int classifyChunk = 512;
	private int nextClassify = 0, nextRow = Integer.MIN_VALUE;
	private final Map<Long, NearBlock> near = new HashMap<Long, NearBlock>();
	private final Set<Long> dirty = new LinkedHashSet<Long>();
	private int frames = 0;
	private long totalNanos = 0, maxSliceNanos = 0;
	// What the loader spent preparing this scan in the frame it started, and on its worker thread (-1: not measured)
	private long startNanos = -1, prepNanos = -1;

	/**
	 * @param shapeExpected the shape's expected positions (local), in its iteration order
	 * @param tracked the expected positions that are not excluded (local), in the loader's order
	 */
	public SliceScan(ValidationState state, Collection<Long> shapeExpected, List<Long> tracked, int ox, int oy, int oz, long generation) {
		this(state, shapeExpected, tracked, ox, oy, oz, generation, state.getScanEpoch(), state.getPalette(), state.getExclusionBoxes());
	}

	/**
	 * The same with what the loader read from the state on the render thread when it asked for the scan (its scan
	 * epoch, palette and exclusion boxes): the constructor may then run on another thread, reading nothing else of
	 * the state.
	 */
	public SliceScan(ValidationState state, Collection<Long> shapeExpected, List<Long> tracked, int ox, int oy, int oz, long generation, long epoch, LayerPalette palette, List<int[]> exclusionBoxes) {
		this.state = state;
		this.epoch = epoch;
		this.palette = palette;
		this.generation = generation;
		this.ox = ox;
		this.oy = oy;
		this.oz = oz;
		this.boxes = exclusionBoxes.toArray(new int[0][]);
		this.order = new ArrayList<Long>(tracked);
		int n = order.size();
		index = new LongIndex(n);
		statuses = new byte[n];
		names = new String[n];
		present = new boolean[n];
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for(int i = 0;i < n;++i) {
			long pos = order.get(i);
			if(index.add(pos) != i) throw new IllegalArgumentException("a tracked position twice");
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			b[0] = Math.min(b[0], x);
			b[1] = Math.min(b[1], y);
			b[2] = Math.min(b[2], z);
			b[3] = Math.max(b[3], x);
			b[4] = Math.max(b[4], y);
			b[5] = Math.max(b[5], z);
		}
		minX = b[0];
		minY = b[1];
		minZ = b[2];
		maxX = b[3];
		maxY = b[4];
		maxZ = b[5];
		nextY = minY - ValidationState.nearRadius;
		int r = ValidationState.nearRadius;
		bx0 = minX - r;
		by0 = minY - r;
		bz0 = minZ - r;
		bw = n == 0 ? 0 : maxX - minX + 1 + 2 * r;
		bh = n == 0 ? 0 : maxY - minY + 1 + 2 * r;
		bd = n == 0 ? 0 : maxZ - minZ + 1 + 2 * r;
		long bits = (long) bw * bh * bd;
		if(n > 0 && bits <= maxBitmapBits) {
			bitmap = new long[(int) ((bits + 63) >>> 6)];
			for(int i = 0;i < n;++i) {
				long pos = order.get(i);
				long bit = bitOf(LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos));
				bitmap[(int) (bit >>> 6)] |= 1L << (bit & 63);
			}
		}else {
			bitmap = null;
		}
		// The positions of each layer, in the loader's order within it (what the layers classify)
		int layers = n == 0 ? 0 : maxY - minY + 1;
		int[] count = new int[layers];
		for(int i = 0;i < n;++i) ++count[LocalPos.unpackY(order.get(i)) - minY];
		byLayer = new int[layers][];
		for(int l = 0;l < layers;++l) byLayer[l] = new int[count[l]];
		Arrays.fill(count, 0);
		for(int i = 0;i < n;++i) {
			int l = LocalPos.unpackY(order.get(i)) - minY;
			byLayer[l][count[l]++] = i;
		}
		// The state's next map, in the shape's order and grown from empty as beginScan's was, so its lists iterate
		// alike (a map sized up front treeifies crowded buckets instead of growing, and orders them differently)
		statusMap = new HashMap<Long, Byte>();
		int[] t = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for(Long pos: shapeExpected) {
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(excluded(boxes, x, y, z)) continue;
			int i = index.indexOf(pos);
			if(i >= 0) {
				if(present[i]) continue;
				present[i] = true;
				statusMap.put(order.get(i), ValidationState.UNKNOWN);
			}else {
				// Expected, not excluded, yet not tracked: beginScan tracked it too, and nothing ever classified it
				if(statusMap.containsKey(pos)) continue;
				statusMap.put(pos, ValidationState.UNKNOWN);
			}
			t[0] = Math.min(t[0], x);
			t[1] = Math.min(t[1], y);
			t[2] = Math.min(t[2], z);
			t[3] = Math.max(t[3], x);
			t[4] = Math.max(t[4], y);
			t[5] = Math.max(t[5], z);
		}
		trackedBounds = t;
	}

	/**
	 * The loader's scan, built from a copy of the shape's expected set (in its order): the positions outside the
	 * exclusion boxes are tracked, in that same order. Null when none is. Reads nothing of the world or of the
	 * state besides what is passed, so it runs on a worker thread.
	 */
	public static SliceScan prepare(ValidationState state, List<Long> shapeExpected, int ox, int oy, int oz, long generation, long epoch, LayerPalette palette, List<int[]> exclusionBoxes) {
		long started = System.nanoTime();
		int[][] boxes = exclusionBoxes.toArray(new int[0][]);
		List<Long> tracked = new ArrayList<Long>(shapeExpected.size());
		for(Long pos: shapeExpected) {
			if(!excluded(boxes, LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos))) tracked.add(pos);
		}
		if(tracked.isEmpty()) return null;
		SliceScan scan = new SliceScan(state, shapeExpected, tracked, ox, oy, oz, generation, epoch, palette, exclusionBoxes);
		scan.prepNanos = System.nanoTime() - started;
		return scan;
	}

	private long bitOf(int x, int y, int z) {
		return ((long) (x - bx0) * bh + (y - by0)) * bd + (z - bz0);
	}

	// Whether a local position is tracked (expected and not excluded)
	private boolean isTracked(int x, int y, int z) {
		if(bitmap == null) return index.contains(LocalPos.pack(x, y, z));
		if(x < bx0 || y < by0 || z < bz0 || x >= bx0 + bw || y >= by0 + bh || z >= bz0 + bd) return false;
		long bit = bitOf(x, y, z);
		return (bitmap[(int) (bit >>> 6)] & (1L << (bit & 63))) != 0;
	}

	private static boolean excluded(int[][] boxes, int x, int y, int z) {
		for(int[] b: boxes) {
			if(x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
		}
		return false;
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
	 * One frame of work until budgetNanos is spent (measured with nanoClock), at least one unit. A budget of 0 or
	 * less means no limit (Instant). Returns whether the scan is complete. A unit is classifyChunk expected
	 * positions or one x row of the near sweep (performance step 4: a whole layer was one unit, so a wide shape
	 * went over the budget by up to a layer, 9 ms at NORMAL's 6). Every cell is still read once, in the same
	 * order as layer by layer, so the result is the same.
	 */
	public boolean step(IBlockProbe probe, long budgetNanos, LongSupplier nanoClock) {
		long start = nanoClock.getAsLong();
		do {
			scanUnit(probe);
		}while(!isDone() && (budgetNanos <= 0 || nanoClock.getAsLong() - start < budgetNanos));
		long spent = nanoClock.getAsLong() - start;
		++frames;
		totalNanos += spent;
		maxSliceNanos = Math.max(maxSliceNanos, spent);
		return isDone();
	}

	// The next unit of layer nextY: a chunk of its expected positions, else a row of its near sweep; after the last
	// row the next layer
	private void scanUnit(IBlockProbe probe) {
		int r = ValidationState.nearRadius;
		if(nextRow == Integer.MIN_VALUE) {
			int l = nextY - minY;
			int[] layer = l >= 0 && l < byLayer.length ? byLayer[l] : null;
			if(layer != null && nextClassify < layer.length) {
				int end = Math.min(layer.length, nextClassify + classifyChunk);
				for(int k = nextClassify;k < end;++k) classify(probe, layer[k]);
				nextClassify = end;
				if(nextClassify == layer.length) nextRow = minX - r;
				return;
			}
			nextRow = minX - r; // nothing to classify: the first row is this unit
		}
		int x = nextRow;
		for(int z = minZ - r;z <= maxZ + r;++z) nearCell(probe, x, nextY, z, false);
		if(++nextRow > maxX + r) {
			++nextY;
			nextRow = Integer.MIN_VALUE;
			nextClassify = 0;
		}
	}

	// An expected position (its number), by LayerPalette.classify (the rule every path shares; without a palette:
	// ignored type -> IGNORED, solid -> OK, anything else -> MISSING). The block's id is read only where the layer
	// has a chosen block, its name only for the statuses that list it
	private void classify(IBlockProbe probe, int i) {
		Long pos = order.get(i);
		int lx = LocalPos.unpackX(pos), ly = LocalPos.unpackY(pos), lz = LocalPos.unpackZ(pos);
		int wx = lx + ox, wy = ly + oy, wz = lz + oz;
		int f = probe.flags(wx, wy, wz);
		int id = palette != null && palette.needsId(lx, ly, lz) ? probe.blockId(wx, wy, wz) : LayerPalette.NONE;
		byte s = LayerPalette.classify(palette, lx, ly, lz, f, id);
		statuses[i] = s;
		names[i] = ValidationState.namesBlock(s) ? probe.name(wx, wy, wz) : null;
		if(present[i]) statusMap.put(pos, s);
	}

	// A cell that is not expected: a solid, non-ignored block within nearRadius of an expected one is a
	// structure error; anything else is not. A re-read (a block changed meanwhile) also removes one found before
	private void nearCell(IBlockProbe probe, int x, int y, int z, boolean reread) {
		if(isTracked(x, y, z)) return;
		if(excluded(boxes, x, y, z)) return; // excluded cells are not even read
		long pos = LocalPos.pack(x, y, z);
		int f = probe.nearFlags(x + ox, y + oy, z + oz);
		if((f & IBlockProbe.FLAG_AIR) != 0 || (f & IBlockProbe.FLAG_SOLID) == 0 || (f & IBlockProbe.FLAG_IGNORED) != 0) {
			if(reread) near.remove(pos);
			return;
		}
		double best = Double.MAX_VALUE;
		int r = ValidationState.nearRadius;
		for(int dx = -r;dx <= r;++dx) {
			for(int dy = -r;dy <= r;++dy) {
				for(int dz = -r;dz <= r;++dz) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if(d2 == 0 || d2 > r * r) continue;
					if(isTracked(x + dx, y + dy, z + dz)) {
						double d = Math.sqrt(d2);
						if(d < best) best = d;
					}
				}
			}
		}
		if(best <= r) near.put(pos, new NearBlock(pos, probe.name(x + ox, y + oy, z + oz), (float) best));
		else if(reread) near.remove(pos);
	}

	// A block changed during the scan (world coordinates): read it again when publishing
	public void markDirty(int wx, int wy, int wz) {
		int x = wx - ox, y = wy - oy, z = wz - oz;
		int r = ValidationState.nearRadius;
		if(x < minX - r || x > maxX + r || y < minY - r || y > maxY + r || z < minZ - r || z > maxZ + r) return;
		dirty.add(LocalPos.pack(x, y, z));
	}

	/**
	 * The result into the state, in one go: positions changed during the scan are read again, then the state
	 * the calls of an instantaneous scan leave (beginScan with the shape's order, setStatus in the loader's
	 * order, the structure errors in x, y, z order, endScan), the filled status map swapped in.
	 */
	public void publish(IBlockProbe probe) {
		for(long pos: dirty) {
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(y >= nextY) continue; // not scanned yet: impossible once done, kept for safety
			int i = index.indexOf(pos);
			if(i >= 0) classify(probe, i);
			else nearCell(probe, x, y, z, true);
		}
		dirty.clear();
		// The order an instantaneous scan finds them in: x, then y, then z (a primitive sort of shifted keys)
		List<NearBlock> found = new ArrayList<NearBlock>(near.values());
		long[] keys = new long[found.size()];
		for(int k = 0;k < keys.length;++k) {
			long p = found.get(k).localPos;
			keys[k] = (((long) LocalPos.unpackX(p) + 0x100000L) << 42) | (((long) LocalPos.unpackY(p) + 0x100000L) << 21) | ((long) LocalPos.unpackZ(p) + 0x100000L);
		}
		Arrays.sort(keys);
		List<NearBlock> list = new ArrayList<NearBlock>(keys.length);
		for(long key: keys) {
			long p = LocalPos.pack((int) ((key >>> 42) & 0x1FFFFF) - 0x100000, (int) ((key >>> 21) & 0x1FFFFF) - 0x100000, (int) (key & 0x1FFFFF) - 0x100000);
			list.add(near.get(p));
		}
		state.publishScan(statusMap, order, statuses, names, present, list, ox, oy, oz, trackedBounds);
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

	// The preparation on the worker thread (prepare), -1 when the scan was built on the caller's thread
	public long getPrepMillis() {
		return prepNanos < 0 ? -1 : prepNanos / 1000000;
	}

	public int getExpectedCount() {
		return order.size();
	}

	// World bounds of the tracked positions {minX, minY, minZ, maxX, maxY, maxZ}: the chunks that must be loaded
	public int[] worldBounds() {
		return new int[] {minX + ox, minY + oy, minZ + oz, maxX + ox, maxY + oy, maxZ + oz};
	}
}
