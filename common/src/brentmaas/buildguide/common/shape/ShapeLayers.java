package brentmaas.buildguide.common.shape;

import java.util.Arrays;

/**
 * Layers (2026-10-27, in place of the three cuts by height): up to five layers of blocks, one engine for the
 * Island's strata (a piece of ground lifted out of the earth) and the Terrain's zones by height. Numbered 1 (the
 * top) to count (the bottom), with generic names; each has its own block (LayerPalette) and colour.
 *
 * Each layer is a band, or no band (thickness 0), and may also spread as patches into the bands next to it. One
 * band is the rest: it takes whatever the others leave.
 *
 * Depth (the Island), per column, from its top down to the body's bottom: the bands above the rest take their
 * thickness in blocks from the top, those below it a percentage of the column from its bottom. A column too thin
 * for them all gives the surfaces' bands first (the top one, the bottom one, then inwards from both sides) and the
 * rest shrinks to nothing first. Everything under the body (the spikes, their loose pieces) is the spike layer.
 *
 * Elevation (the Terrain): the same bands over Height, all in percent (from the top, from the base); a column
 * takes the layer of the band its surface height falls in, its whole skin with it.
 *
 * Mix: `mix` blocks on each side of every boundary where the two layers interleave, decided by a smooth noise
 * and a per-block one, so the boundary is ragged rather than a line. Patches: a layer with patches % above 0
 * appears as blobs of about `size` blocks in the bands next to it (above and below in layer order), covering
 * about that share of them. Seeded and pure: a position always has the same layer, so the world, the preview,
 * the scan and Fill agree. Immutable. Only primitives: no net.minecraft.
 */
public final class ShapeLayers {
	public static final int COUNT = 5, minCount = 2;
	public static final int maxThickness = 100, maxMix = 6, maxPatches = 100, patchStep = 5, minSize = 1, maxSize = 16;

	public enum Axis {
		DEPTH,
		ELEVATION
	}

	// Layer colours (0xRRGGBB), away from the status colours (green built, blue-grey missing, yellow ignored,
	// red errors, orange wrong layer) and from each other
	public static final int LAYER_1 = 0x00C8DC; // cyan
	public static final int LAYER_2 = 0xDC32C8; // magenta
	public static final int LAYER_3 = 0x8C5A2D; // brown
	public static final int LAYER_4 = 0x2846DC; // strong blue
	public static final int LAYER_5 = 0x96962A; // olive
	private static final int[] colours = {LAYER_1, LAYER_2, LAYER_3, LAYER_4, LAYER_5};

	// The legend of a layer (legend): what kind of band it is, with legendValue the number shown
	public static final int LEGEND_NONE = 0, LEGEND_BLOCKS = 1, LEGEND_BOTTOM = 2, LEGEND_TOP = 3, LEGEND_BASE = 4, LEGEND_REST = 5, LEGEND_PATCHES = 6, LEGEND_EMPTY = 7;

	/** What the layers are made of, the same for every column. Arrays are per layer, index 0 = layer 1. */
	public static final class Spec {
		public final Axis axis;
		public final int count, rest, mix, spikeLayer;
		private final int[] thickness, patches, size;
		public final long seed;

		public Spec(Axis axis, int count, int rest, int mix, int spikeLayer, int[] thickness, int[] patches, int[] size, long seed) {
			this.axis = axis;
			this.count = clamp(count, minCount, COUNT);
			this.rest = clamp(rest, 1, this.count);
			this.mix = clamp(mix, 0, maxMix);
			this.spikeLayer = clamp(spikeLayer, 1, this.count);
			this.thickness = new int[COUNT];
			this.patches = new int[COUNT];
			this.size = new int[COUNT];
			for(int i = 0;i < COUNT;++i) {
				this.thickness[i] = clamp(at(thickness, i, 0), 0, maxThickness);
				this.patches[i] = clamp(at(patches, i, 0), 0, maxPatches);
				this.size[i] = clamp(at(size, i, 3), minSize, maxSize);
			}
			this.seed = seed;
		}

		public int thickness(int layer) {
			return thickness[layer - 1];
		}

		public int patches(int layer) {
			return patches[layer - 1];
		}

		public int size(int layer) {
			return size[layer - 1];
		}

		// A band: a layer with a thickness, or the rest
		public boolean isBand(int layer) {
			return layer == rest || thickness[layer - 1] > 0;
		}

		private static int at(int[] a, int i, int fallback) {
			return a != null && i < a.length ? a[i] : fallback;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Spec)) return false;
			Spec s = (Spec) o;
			return axis == s.axis && count == s.count && rest == s.rest && mix == s.mix && spikeLayer == s.spikeLayer && seed == s.seed && Arrays.equals(thickness, s.thickness) && Arrays.equals(patches, s.patches) && Arrays.equals(size, s.size);
		}

		@Override
		public int hashCode() {
			return ((((axis.hashCode() * 31 + count) * 31 + rest) * 31 + mix) * 31 + spikeLayer) * 31 + Long.hashCode(seed) + 7 * Arrays.hashCode(thickness) + 11 * Arrays.hashCode(patches) + 13 * Arrays.hashCode(size);
		}
	}

	public final Spec spec;
	// The bands in order from the top, their layers, and which of them is the rest
	private final int[] bandLayer;
	private final int bandCount, restBand;
	// For each layer, the layers its patches appear in (bit L for layer L)
	private final int[] hostMask = new int[COUNT + 1];
	// Depth: the top's y, and per column the body's depth below it (-1 off the plan) or one depth for all
	private final int topY;
	private final int[] body;
	private final int uniformBody;
	// Elevation: per column the surface's height above the base (-1 off the plan), and Height
	private final int[] tops;
	private final int height;
	// The grid of body / tops: index (x + h) * n + (z + h)
	private final int h, n;
	// The band ends: per column for the Depth (null off the plan), once for a uniform body or the Elevation
	private final int[][] columnEnds;
	private final int[] sharedEnds;

	private ShapeLayers(Spec spec, int topY, int[] body, int uniformBody, int[] tops, int height, int h) {
		this.spec = spec;
		this.topY = topY;
		this.body = body;
		this.uniformBody = uniformBody;
		this.tops = tops;
		this.height = Math.max(1, height);
		this.h = h;
		this.n = 2 * h + 1;
		int[] bands = new int[COUNT];
		int b = 0, restAt = 0;
		for(int layer = 1;layer <= spec.count;++layer) {
			if(!spec.isBand(layer)) continue;
			if(layer == spec.rest) restAt = b;
			bands[b++] = layer;
		}
		bandLayer = Arrays.copyOf(bands, b);
		bandCount = b;
		restBand = restAt;
		for(int layer = 1;layer <= spec.count;++layer) {
			if(spec.patches(layer) <= 0) continue;
			int above = 0, below = 0;
			for(int i = 0;i < bandCount;++i) {
				if(bandLayer[i] < layer) above = bandLayer[i];
				if(bandLayer[i] > layer && below == 0) below = bandLayer[i];
			}
			if(above > 0) hostMask[layer] |= 1 << above;
			if(below > 0) hostMask[layer] |= 1 << below;
			// The spikes are the bottom of the island: a layer next to the bottom band reaches them too
			if(spec.axis == Axis.DEPTH && below == 0 && spec.spikeLayer != layer) hostMask[layer] |= 1 << spec.spikeLayer;
		}
		if(spec.axis == Axis.ELEVATION) {
			columnEnds = null;
			sharedEnds = ends(this.height + 1, true);
		}else if(body == null) {
			columnEnds = null;
			sharedEnds = ends(this.uniformBody + 1, false);
		}else {
			columnEnds = new int[body.length][];
			for(int c = 0;c < body.length;++c) if(body[c] >= 0) columnEnds[c] = ends(body[c] + 1, false);
			sharedEnds = null;
		}
	}

	/** Strata of the Island: top at y topY, body depth per column (index (x + h) * (2h + 1) + (z + h), -1 off the plan). */
	public static ShapeLayers depth(Spec spec, int topY, int[] body, int h) {
		return new ShapeLayers(spec, topY, body, -1, null, 0, h);
	}

	/** The same with one body depth for every column (any plan). */
	public static ShapeLayers uniformDepth(Spec spec, int topY, int bodyDepth) {
		return new ShapeLayers(spec, topY, null, Math.max(0, bodyDepth), null, 0, 0);
	}

	/** Zones of the Terrain: surface height per column (the same index, -1 off the plan), over Height. */
	public static ShapeLayers elevation(Spec spec, int height, int[] tops, int h) {
		return new ShapeLayers(spec, 0, null, -1, tops, height, h);
	}

	public int count() {
		return spec.count;
	}

	public static int colour(int layer) {
		return colours[clamp(layer, 1, COUNT) - 1];
	}

	/**
	 * Where each band ends, as a depth below the top (exclusive), for a column of `total` blocks. Thicknesses in
	 * blocks above the rest (percent when percentAbove), percent of the column below it; the surfaces' bands are
	 * served first, the rest takes what is left (possibly nothing).
	 */
	private int[] ends(int total, boolean percentAbove) {
		int[] want = new int[bandCount], got = new int[bandCount];
		for(int i = 0;i < bandCount;++i) {
			if(i == restBand) continue;
			int t = spec.thickness(bandLayer[i]);
			want[i] = i < restBand && !percentAbove ? t : (int) Math.max(1, Math.round(t * total / 100.0));
		}
		int left = total, lo = 0, hi = bandCount - 1;
		boolean fromTop = true;
		while(lo <= hi) {
			int i = fromTop ? lo++ : hi--;
			fromTop = !fromTop;
			if(i == restBand) continue;
			got[i] = Math.min(want[i], left);
			left -= got[i];
		}
		got[restBand] = left;
		int[] out = new int[bandCount];
		int d = 0;
		for(int i = 0;i < bandCount;++i) {
			d += got[i];
			out[i] = d;
		}
		return out;
	}

	// The body depth of the column (-1 off the plan)
	private int bodyAt(int x, int z) {
		if(body == null) return uniformBody;
		int ix = x + h, iz = z + h;
		return ix < 0 || ix >= n || iz < 0 || iz >= n ? -1 : body[ix * n + iz];
	}

	// The surface height of the column, a position off the plan by its own y
	private int topAt(int x, int y, int z) {
		int ix = x + h, iz = z + h;
		int t = tops == null || ix < 0 || ix >= n || iz < 0 || iz >= n ? -1 : tops[ix * n + iz];
		return clamp(t < 0 ? y : t, 0, height);
	}

	/** 1 to count for the block at this local position. */
	public int layerAt(int x, int y, int z) {
		boolean flat = spec.axis == Axis.ELEVATION;
		int depth, total, base;
		int[] ends;
		int nz = flat ? 0 : y;
		if(flat) {
			total = height + 1;
			depth = height - topAt(x, y, z);
			ends = sharedEnds;
		}else {
			int b = bodyAt(x, z);
			if(b < 0) return patched(spec.spikeLayer, x, y, z); // off the plan: a loose spike piece
			total = b + 1;
			depth = topY - y;
			ends = body == null ? sharedEnds : columnEnds[(x + h) * n + (z + h)];
		}
		// The interval the depth falls in: the bands, then (Depth) the spikes under the body
		int intervals = flat ? bandCount : bandCount + 1;
		int i = interval(ends, intervals, total, depth);
		if(spec.mix > 0) i = mixed(ends, intervals, total, depth, i, x, nz, z);
		base = i < bandCount ? bandLayer[i] : spec.spikeLayer;
		return patched(base, x, nz, z);
	}

	// Start and end of interval i (the spikes' interval, after the last band, ends nowhere)
	private static int start(int[] ends, int i) {
		return i == 0 ? 0 : ends[i - 1];
	}

	private int end(int[] ends, int i) {
		return i < bandCount ? ends[i] : Integer.MAX_VALUE;
	}

	private int interval(int[] ends, int intervals, int total, int depth) {
		for(int i = 0;i < intervals;++i) if(depth < end(ends, i) && end(ends, i) > start(ends, i)) return i;
		return intervals - 1;
	}

	// The non-empty interval before / after i, -1 when none
	private int previous(int[] ends, int i) {
		for(int k = i - 1;k >= 0;--k) if(end(ends, k) > start(ends, k)) return k;
		return -1;
	}

	private int next(int[] ends, int intervals, int i) {
		for(int k = i + 1;k < intervals;++k) if(end(ends, k) > start(ends, k)) return k;
		return -1;
	}

	/**
	 * Mix: within `mix` blocks of the boundary with a neighbouring interval, the position may take that
	 * interval's layer, the more likely the closer it is (an even split right on the boundary).
	 */
	private int mixed(int[] ends, int intervals, int total, int depth, int i, int x, int y, int z) {
		double half = spec.mix, width = 2.0 * spec.mix, centre = depth + 0.5;
		double s = start(ends, i), e = end(ends, i);
		int before = previous(ends, i), after = next(ends, intervals, i);
		double noise = uniform(mixNoise(x, y, z), spec.axis == Axis.ELEVATION ? mixSamples2 : mixSamples3);
		if(before >= 0 && centre - s < half) {
			double u = (centre - (s - half)) / width; // 0.5 on the boundary, 1 a mix inside
			if(noise >= u) return before;
		}else if(after >= 0 && e - centre < half) {
			double u = (centre - (e - half)) / width; // 0 a mix inside, 0.5 on the boundary
			if(noise < u) return after;
		}
		return i;
	}

	// Patches: a layer whose patches reach this one may take the position
	private int patched(int layer, int x, int y, int z) {
		for(int k = 1;k <= spec.count;++k) {
			if(k == layer || (hostMask[k] & (1 << layer)) == 0) continue;
			if(blob(k, x, y, z)) return k;
		}
		return layer;
	}

	private boolean blob(int layer, int x, int y, int z) {
		double s = spec.size(layer);
		boolean flat = spec.axis == Axis.ELEVATION;
		double v = value(IslandNoise.mix(spec.seed ^ (0x51ED2701L * layer)), x / s, flat ? 0 : y / s, z / s);
		return v < threshold(spec.patches(layer) / 100.0, spec.size(layer), flat);
	}

	private double mixNoise(int x, int y, int z) {
		long seed = IslandNoise.mix(spec.seed ^ 0x6A09E667F3BCC909L);
		return 0.5 * value(seed, x / 3.0, y / 3.0, z / 3.0) + 0.5 * hash01(seed + 1, x, y, z);
	}

	// Seeded noise: a hash per lattice point in [0, 1), and its smooth (smoothstep) trilinear interpolation
	static double hash01(long seed, int x, int y, int z) {
		long v = IslandNoise.mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L));
		return (v >>> 11) * 0x1.0p-53;
	}

	static double value(long seed, double x, double y, double z) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
		double fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
		double c000 = hash01(seed, ix, iy, iz), c100 = hash01(seed, ix + 1, iy, iz), c010 = hash01(seed, ix, iy + 1, iz), c110 = hash01(seed, ix + 1, iy + 1, iz);
		double c001 = hash01(seed, ix, iy, iz + 1), c101 = hash01(seed, ix + 1, iy, iz + 1), c011 = hash01(seed, ix, iy + 1, iz + 1), c111 = hash01(seed, ix + 1, iy + 1, iz + 1);
		double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx, x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
		double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
		return y0 + (y1 - y0) * fz;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	// The noise level below which `share` of the positions lie: smooth noise is not uniform, and how it spreads
	// depends on where whole blocks fall in its lattice (at size 1 exactly on its points), so the shares come from
	// sorted samples taken once per size, at whole positions as the patches read it (in a plane through the
	// lattice for the Terrain's 2D patches)
	private static final double[][] quantiles3 = new double[maxSize + 1][], quantiles2 = new double[maxSize + 1][];

	static {
		for(int size = minSize;size <= maxSize;++size) {
			quantiles3[size] = samples(size, false);
			quantiles2[size] = samples(size, true);
		}
	}

	private static double[] samples(int size, boolean flat) {
		int count = 2048;
		double[] v = new double[count];
		for(int i = 0;i < count;++i) {
			int x = (int) (hash01(91, i, size, 0) * 100000), y = flat ? 0 : (int) (hash01(92, i, size, 0) * 100000), z = (int) (hash01(93, i, size, 0) * 100000);
			v[i] = value(0x2545F4914F6CDD1DL, x / (double) size, flat ? 0 : y / (double) size, z / (double) size);
		}
		Arrays.sort(v);
		return v;
	}

	// The mix's noise (half smooth, half per block) is not uniform either: its rank among sorted samples is
	private static final double[] mixSamples3 = mixSamples(false), mixSamples2 = mixSamples(true);

	private static double[] mixSamples(boolean flat) {
		int count = 4096;
		double[] v = new double[count];
		for(int i = 0;i < count;++i) {
			int x = (int) (hash01(94, i, 0, 0) * 100000), y = flat ? 0 : (int) (hash01(95, i, 0, 0) * 100000), z = (int) (hash01(96, i, 0, 0) * 100000);
			v[i] = 0.5 * value(77, x / 3.0, y / 3.0, z / 3.0) + 0.5 * hash01(78, x, y, z);
		}
		Arrays.sort(v);
		return v;
	}

	// The share of the samples below v, in [0, 1]
	private static double uniform(double v, double[] sorted) {
		int i = Arrays.binarySearch(sorted, v);
		if(i < 0) i = -i - 1;
		return i / (double) sorted.length;
	}

	static double threshold(double share, int size, boolean flat) {
		if(share <= 0) return -1;
		if(share >= 1) return 2;
		double[] q = (flat ? quantiles2 : quantiles3)[clamp(size, minSize, maxSize)];
		return q[(int) Math.min(q.length - 1, Math.round(share * (q.length - 1)))];
	}

	/**
	 * What the preview's legend says of a layer: {kind, value}, kind one of LEGEND_*: no such layer, a band of
	 * value blocks from the top, value % from the bottom, value % from the top, value % from the base, the rest,
	 * patches only (value %), or nothing at all.
	 */
	public int[] legend(int layer) {
		if(layer < 1 || layer > spec.count) return new int[] {LEGEND_NONE, 0};
		if(layer == spec.rest) return new int[] {LEGEND_REST, 0};
		int t = spec.thickness(layer);
		if(t <= 0) return spec.patches(layer) > 0 ? new int[] {LEGEND_PATCHES, spec.patches(layer)} : new int[] {LEGEND_EMPTY, 0};
		boolean above = layer < spec.rest;
		if(spec.axis == Axis.ELEVATION) return new int[] {above ? LEGEND_TOP : LEGEND_BASE, t};
		return new int[] {above ? LEGEND_BLOCKS : LEGEND_BOTTOM, t};
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// The same spec over the same generation's columns (identity: the arrays are never written after)
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof ShapeLayers)) return false;
		ShapeLayers l = (ShapeLayers) o;
		return spec.equals(l.spec) && topY == l.topY && body == l.body && uniformBody == l.uniformBody && tops == l.tops && height == l.height && h == l.h;
	}

	@Override
	public int hashCode() {
		return ((spec.hashCode() * 31 + topY) * 31 + System.identityHashCode(body) + System.identityHashCode(tops)) * 31 + uniformBody * 7 + height;
	}
}
