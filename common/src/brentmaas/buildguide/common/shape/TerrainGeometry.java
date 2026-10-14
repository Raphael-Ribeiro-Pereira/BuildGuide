package brentmaas.buildguide.common.shape;

/**
 * Terrain geometry, in the shape's local frame: a relief skin over a plan. The plan is star shaped (a
 * distance from the centre to the edge per angle: Circle, Square, Polygon, Organic, with the same meaning
 * as the Island's, but the Terrain's own copy). Each (x, z) inside it is one column whose top is the
 * relief height h(x, z) in [0, Height], rounded. Never solid: only the skin, Skin blocks deep, with no
 * floor and no wall at the edge. A column goes down to the lowest top among itself and its 4 neighbours
 * inside the plan, then Skin - 1 more, so neighbouring columns always touch (no gaps on steep slopes); a
 * neighbour outside the plan is ignored (the edge is open).
 *
 * Relief: Hills is smooth fractal noise; Mountains ridged noise (sharp crests, wide valleys); Dunes
 * directional waves across the Rotation's direction (gentle windward side, steep lee) with light noise.
 * Roughness sets the octaves and how much detail they keep. Edge fade lowers h to 0 at the edge of the
 * plan, over that percentage of the way to the centre.
 *
 * Pure and deterministic: the result depends only on the parameters (seed included).
 * Only primitives and java.*: no net.minecraft.
 */
public final class TerrainGeometry {
	public enum Outline{
		CIRCLE,
		SQUARE,
		POLYGON,
		ORGANIC
	}

	public enum Type{
		HILLS,
		MOUNTAINS,
		DUNES
	}

	public static final int maxWidth = 121, minWidth = 3, minSides = 3, maxSides = 12;
	public static final int minHeight = 1, maxHeight = 60, minScale = 4, maxScale = 64, minSkin = 1, maxSkin = 3;
	// The height cap (cost): Height <= HEIGHT_PER_SCALE x Scale, so a slope stays bounded. The skin fills a
	// column down to its lowest neighbour, so the block count grows with the steepness (2026-10-14: without
	// the cap the worst case was 381,733 blocks)
	public static final int HEIGHT_PER_SCALE = 3;
	// Seed salts: the edge, the relief and the dunes' warp are independent fields
	private static final long edgeSalt = 0x6A09E667F3BCC909L, reliefSalt = 0x3243F6A8885A308DL, warpSalt = 0x13198A2E03707344L, detailSalt = 0x0A4093822299F31DL;
	// Dunes: the lee (the steep side) takes DUNE_LEE_FRACTION of a wave, the windward side the rest. DUNE_NOISE
	// is the light noise's share of the height (the waves take the rest), DUNE_WARP how far the warp bends the
	// crests (in waves). The count of the skin hardly depends on the lee (a column drops about 2 x Height /
	// Scale on either side); the noise's share lowers it: 0.35 and 0.20 keep the worst Dunes (126,210 blocks)
	// under the worst Mountains (129,882) at Height <= 3 x Scale (2026-10-14)
	public static final double DUNE_LEE_FRACTION = 0.35;
	public static final double DUNE_NOISE = 0.20, DUNE_WARP = 0.35;
	// Chamfer distance to the outside (Edge fade): 3 per side step, 4 per diagonal step
	private static final int chamferSide = 3, chamferDiagonal = 4;

	public static final class Params {
		public Outline outline = Outline.CIRCLE;
		public int widthX = 41, widthZ = 41, sides = 6;
		public double roundness = 0.7, rotationDeg = 0.0, edgeAmplitude = 0.0, edgeScale = 6.0;
		public Type type = Type.HILLS;
		public int height = 12, scale = 20;
		public double roughness = 0.0;
		public int edgeFade = 0, skin = 2;
		public long seed = 0;

		// Parameters brought into their documented ranges (typed values can be anything)
		Params clamped() {
			Params p = new Params();
			p.outline = outline == null ? Outline.CIRCLE : outline;
			p.widthX = clamp(widthX, minWidth, maxWidth);
			p.widthZ = clamp(widthZ, minWidth, maxWidth);
			p.sides = clamp(sides, minSides, maxSides);
			p.roundness = clamp(roundness, 0.0, 1.0);
			p.rotationDeg = Double.isFinite(rotationDeg) ? rotationDeg : 0.0;
			p.edgeAmplitude = clamp(edgeAmplitude, 0.0, 1.0);
			p.edgeScale = clamp(edgeScale, 0.5, 64.0);
			p.type = type == null ? Type.HILLS : type;
			p.scale = clamp(scale, minScale, maxScale);
			p.height = clamp(height, minHeight, maxHeightFor(p.scale));
			p.roughness = clamp(roughness, 0.0, 1.0);
			p.edgeFade = clamp(edgeFade, 0, 100);
			p.skin = clamp(skin, minSkin, maxSkin);
			p.seed = seed;
			return p;
		}
	}

	private TerrainGeometry() {}

	// The largest Height a Scale allows: min(maxHeight, HEIGHT_PER_SCALE x Scale), Scale held in its range first
	public static int maxHeightFor(int scale) {
		return Math.min(maxHeight, HEIGHT_PER_SCALE * clamp(scale, minScale, maxScale));
	}

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}

	/**
	 * Edge distance at angle theta in the normalised plan (1 = the half widths): Circle 1; Square a
	 * superellipse whose exponent goes from infinite (Corner round 0, a square) to 2 (Corner round 1, a
	 * circle); Polygon of N sides blended towards its circumcircle by Corner round; Organic the Square
	 * superellipse times (1 + Wobble x noise around the edge).
	 */
	public static double edge(Params p, double theta) {
		switch(p.outline) {
		case CIRCLE:
			return 1.0;
		case SQUARE:
			return superellipse(p.roundness, theta);
		case POLYGON: {
			double sector = 2 * Math.PI / p.sides;
			double a = theta % sector;
			if(a < 0) a += sector;
			double poly = Math.cos(Math.PI / p.sides) / Math.cos(a - Math.PI / p.sides);
			return poly + (1.0 - poly) * p.roundness;
		}
		default: {
			// Noise sampled on a circle, so the edge closes on itself; Wobble size ~ bumps around the edge
			double k = p.edgeScale / (2 * Math.PI);
			double n = TerrainNoise.fbm(p.seed ^ edgeSalt, Math.cos(theta) * k, Math.sin(theta) * k, 3, 0.5);
			return Math.max(0.1, superellipse(p.roundness, theta) * (1.0 + p.edgeAmplitude * n));
		}
		}
	}

	private static double superellipse(double roundness, double theta) {
		double c = Math.abs(Math.cos(theta)), s = Math.abs(Math.sin(theta));
		if(roundness < 1e-3) return 1.0 / Math.max(c, s);
		double e = Math.min(200.0, 2.0 / roundness);
		return Math.pow(Math.pow(c, e) + Math.pow(s, e), -1.0 / e);
	}

	// Largest edge distance any angle can reach (normalised), for the bounding box
	static double maxEdge(Params p) {
		switch(p.outline) {
		case CIRCLE:
		case POLYGON:
			return 1.0;
		case SQUARE:
			return Math.sqrt(2.0);
		default:
			return Math.sqrt(2.0) * (1.0 + p.edgeAmplitude);
		}
	}

	// Half side of the square that contains every column (in blocks)
	public static int halfBox(Params params) {
		Params p = params.clamped();
		return (int) Math.ceil(Math.max(p.widthX, p.widthZ) / 2.0 * maxEdge(p));
	}

	// Whether the column (x, z) is inside the plan
	public static boolean inside(Params params, int x, int z) {
		Params p = params.clamped();
		double rot = Math.toRadians(p.rotationDeg);
		return inside(p, Math.cos(rot), Math.sin(rot), x, z);
	}

	private static boolean inside(Params p, double cr, double sr, int x, int z) {
		// Into the outline's own frame: undo the rotation, then normalise by the half widths
		double u = (x * cr + z * sr) / (p.widthX / 2.0), v = (-x * sr + z * cr) / (p.widthZ / 2.0);
		return Math.sqrt(u * u + v * v) <= edge(p, Math.atan2(v, u));
	}

	// Octaves and gain from Roughness: 1 octave (smooth) at 0, 5 at 1, keeping more of each finer one
	static int octaves(Params p) {
		return 1 + (int) Math.round(4 * p.roughness);
	}

	static double gain(Params p) {
		return 0.3 + 0.3 * p.roughness;
	}

	// The relief in [0, 1] before Edge fade, at a column
	static double relief(Params p, int x, int z) {
		double fx = x / (double) p.scale, fz = z / (double) p.scale;
		int oct = octaves(p);
		double g = gain(p);
		switch(p.type) {
		case MOUNTAINS: {
			double r = TerrainNoise.ridged(p.seed ^ reliefSalt, fx, fz, oct, g);
			return clamp(r * r, 0.0, 1.0);
		}
		case DUNES: {
			// Along the wind (the Rotation's direction): a wave per Scale blocks, its crests bent by a warp
			double rot = Math.toRadians(p.rotationDeg);
			double along = (x * Math.cos(rot) + z * Math.sin(rot)) / p.scale;
			double s = along + DUNE_WARP * TerrainNoise.fbm(p.seed ^ warpSalt, fx * 0.5, fz * 0.5, oct, g);
			s -= Math.floor(s);
			double windward = 1.0 - DUNE_LEE_FRACTION;
			double w = s < windward ? s / windward : (1.0 - s) / DUNE_LEE_FRACTION;
			w = w * w * (3 - 2 * w);
			double detail = 0.5 + 0.5 * TerrainNoise.fbm(p.seed ^ detailSalt, fx * 2, fz * 2, oct, g);
			return clamp((1.0 - DUNE_NOISE) * w + DUNE_NOISE * detail, 0.0, 1.0);
		}
		default:
			return clamp(0.5 + 0.8 * TerrainNoise.fbm(p.seed ^ reliefSalt, fx, fz, oct, g), 0.0, 1.0);
		}
	}

	/**
	 * Column tops: for each (x, z) in [-h, h]^2 with h = halfBox + 1, the top y (0..Height) or -1 outside
	 * the plan. Index (x + h) * (2h + 1) + (z + h).
	 */
	public static int[] tops(Params params) {
		Params p = params.clamped();
		int h = halfBox(p) + 1, n = 2 * h + 1;
		double rot = Math.toRadians(p.rotationDeg), cr = Math.cos(rot), sr = Math.sin(rot);
		int[] top = new int[n * n];
		boolean[] in = new boolean[n * n];
		for(int x = -h;x <= h;++x) for(int z = -h;z <= h;++z) in[(x + h) * n + (z + h)] = inside(p, cr, sr, x, z);
		double[] fade = p.edgeFade > 0 ? fade(in, n, p.edgeFade) : null;
		for(int x = -h;x <= h;++x) {
			for(int z = -h;z <= h;++z) {
				int i = (x + h) * n + (z + h);
				if(!in[i]) {
					top[i] = -1;
					continue;
				}
				double value = p.height * relief(p, x, z) * (fade == null ? 1.0 : fade[i]);
				top[i] = clamp((int) Math.round(value), 0, p.height);
			}
		}
		return top;
	}

	/**
	 * Edge fade factor per column, 0..1: 0 on the outermost ring (a column next to the outside, sideways or
	 * diagonally), rising smoothly to 1 over `percent` % of the way from there to the deepest column.
	 * Chamfer distance to the outside (3 per side step, 4 per diagonal), two passes.
	 */
	private static double[] fade(boolean[] in, int n, int percent) {
		final int far = Integer.MAX_VALUE / 4;
		int[] d = new int[n * n];
		for(int i = 0;i < d.length;++i) d[i] = in[i] ? far : 0;
		for(int x = 0;x < n;++x) for(int z = 0;z < n;++z) {
			int i = x * n + z;
			if(d[i] == 0) continue;
			if(x > 0) d[i] = Math.min(d[i], d[i - n] + chamferSide);
			if(z > 0) d[i] = Math.min(d[i], d[i - 1] + chamferSide);
			if(x > 0 && z > 0) d[i] = Math.min(d[i], d[i - n - 1] + chamferDiagonal);
			if(x > 0 && z < n - 1) d[i] = Math.min(d[i], d[i - n + 1] + chamferDiagonal);
		}
		int deepest = 0;
		for(int x = n - 1;x >= 0;--x) for(int z = n - 1;z >= 0;--z) {
			int i = x * n + z;
			if(d[i] == 0) continue;
			if(x < n - 1) d[i] = Math.min(d[i], d[i + n] + chamferSide);
			if(z < n - 1) d[i] = Math.min(d[i], d[i + 1] + chamferSide);
			if(x < n - 1 && z < n - 1) d[i] = Math.min(d[i], d[i + n + 1] + chamferDiagonal);
			if(x < n - 1 && z > 0) d[i] = Math.min(d[i], d[i + n - 1] + chamferDiagonal);
			deepest = Math.max(deepest, d[i]);
		}
		double width = percent / 100.0 * (deepest - chamferDiagonal);
		double[] f = new double[n * n];
		for(int i = 0;i < f.length;++i) {
			if(d[i] == 0) continue;
			double t = width <= 0 ? (d[i] <= chamferDiagonal ? 0.0 : 1.0) : clamp((d[i] - chamferDiagonal) / width, 0.0, 1.0);
			f[i] = t * t * (3 - 2 * t);
		}
		return f;
	}

	/**
	 * Column bottoms for the tops: the lowest top among the column and its 4 neighbours inside the plan,
	 * minus Skin - 1. -1 stays outside (any value; only inside columns are read).
	 */
	public static int[] bottoms(Params params, int[] top) {
		Params p = params.clamped();
		int h = halfBox(p) + 1, n = 2 * h + 1;
		int[] bottom = new int[n * n];
		for(int x = -h;x <= h;++x) {
			for(int z = -h;z <= h;++z) {
				int i = (x + h) * n + (z + h);
				if(top[i] < 0) continue;
				int low = top[i];
				if(x > -h && top[i - n] >= 0) low = Math.min(low, top[i - n]);
				if(x < h && top[i + n] >= 0) low = Math.min(low, top[i + n]);
				if(z > -h && top[i - 1] >= 0) low = Math.min(low, top[i - 1]);
				if(z < h && top[i + 1] >= 0) low = Math.min(low, top[i + 1]);
				bottom[i] = low - (p.skin - 1);
			}
		}
		return bottom;
	}

	// The skin, in order x, z, then y from the top down; each cell once
	public static void enumerate(Params params, IBlockConsumer out) throws InterruptedException {
		Params p = params.clamped();
		int h = halfBox(p) + 1, n = 2 * h + 1;
		int[] top = tops(p), bottom = bottoms(p, top);
		for(int x = -h;x <= h;++x) {
			for(int z = -h;z <= h;++z) {
				int i = (x + h) * n + (z + h);
				if(top[i] < 0) continue;
				for(int y = top[i];y >= bottom[i];--y) out.accept(x, y, z);
			}
		}
	}
}
