package brentmaas.buildguide.common.shape;

/**
 * Island geometry, in the shape's local frame: y = 0 is the flat top, the body goes down to
 * y = -depth. The plan is star shaped (a distance from the centre to the edge per angle), so the
 * solid is one column per (x, z) from its bottom up to 0: no caves, no overhangs. Only the hollow
 * shell is emitted: a solid cell whose 6-connected (Manhattan) distance to a non-solid cell is at
 * most `wall`. That shell is closed in 6-connectivity by construction (an inside cell next to an
 * outside cell would be at distance 1). The cap is the whole plan disc at y = 0.
 *
 * Pure and deterministic: the result depends only on the parameters (seed included).
 * Only primitives and java.*: no net.minecraft.
 */
public final class IslandGeometry {
	public enum Outline{
		CIRCLE,
		SQUARE,
		POLYGON,
		ORGANIC
	}

	public enum Profile{
		BOWL,
		CONE,
		TERRACED
	}

	public static final int maxWidth = 121, minWidth = 3, maxDepth = 80, minSides = 3, maxSides = 12, minWall = 1, maxWall = 3;
	// Seed salts so the edge and the depth noise are independent fields
	private static final long edgeSalt = 0x5DEECE66DL, depthSalt = 0x2545F4914F6CDD1DL;

	public static final class Params {
		public Outline outline = Outline.ORGANIC;
		public int widthX = 41, widthZ = 41, sides = 6;
		public double roundness = 0.7, rotationDeg = 0.0, edgeAmplitude = 0.25, edgeScale = 6.0;
		public int wall = 2, depth = 24;
		public Profile profile = Profile.BOWL;
		public double sharpness = 0.4, roughness = 0.3;
		public long seed = 0;

		// Parameters brought into their documented ranges (typed values can be anything)
		Params clamped() {
			Params p = new Params();
			p.outline = outline;
			p.widthX = clamp(widthX, minWidth, maxWidth);
			p.widthZ = clamp(widthZ, minWidth, maxWidth);
			p.sides = clamp(sides, minSides, maxSides);
			p.roundness = clamp(roundness, 0.0, 1.0);
			p.rotationDeg = Double.isFinite(rotationDeg) ? rotationDeg : 0.0;
			p.edgeAmplitude = clamp(edgeAmplitude, 0.0, 1.0);
			p.edgeScale = clamp(edgeScale, 0.5, 64.0);
			p.wall = clamp(wall, minWall, maxWall);
			p.depth = clamp(depth, 0, maxDepth);
			p.profile = profile;
			p.sharpness = clamp(sharpness, 0.0, 1.0);
			p.roughness = clamp(roughness, 0.0, 1.0);
			p.seed = seed;
			return p;
		}
	}

	private IslandGeometry() {}

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}

	/**
	 * Edge distance at angle theta in the normalised plan (1 = the half widths): Circle 1;
	 * Square a superellipse whose exponent goes from infinite (Roundness 0, a square) to 2
	 * (Roundness 1, a circle); Polygon of N sides blended towards its circumcircle by Roundness;
	 * Organic the Square superellipse times (1 + amplitude x noise around the edge).
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
			// Noise sampled on a circle, so the edge closes on itself; edgeScale ~ bumps around the edge
			double k = p.edgeScale / (2 * Math.PI);
			double n = IslandNoise.fbm(p.seed ^ edgeSalt, Math.cos(theta) * k, Math.sin(theta) * k);
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

	// Body depth fraction (0..1) at plan fraction f (0 centre, 1 edge) before roughness
	static double profile(Params p, double f) {
		f = clamp(f, 0.0, 1.0);
		switch(p.profile) {
		case BOWL:
			return Math.pow(1.0 - f * f, 0.35 + 1.65 * p.sharpness);
		default:
			return Math.pow(1.0 - f, 0.6 + 1.9 * p.sharpness);
		}
	}

	/**
	 * Column bottoms: for each (x, z) in [-h, h]^2 with h = halfBox + margin, the number of blocks
	 * the column goes down below the top (0 = only the cap), or -1 outside the plan. Index
	 * (x + h) * (2h + 1) + (z + h).
	 */
	public static int[] columns(Params params, int margin) {
		Params p = params.clamped();
		int h = halfBox(p) + margin, n = 2 * h + 1;
		int[] bottom = new int[n * n];
		double rx = p.widthX / 2.0, rz = p.widthZ / 2.0;
		double rot = Math.toRadians(p.rotationDeg), cr = Math.cos(rot), sr = Math.sin(rot);
		int terraces = clamp((int) Math.round(p.depth / 6.0), 2, 10);
		for(int x = -h;x <= h;++x) {
			for(int z = -h;z <= h;++z) {
				// Into the outline's own frame: undo the rotation, then normalise by the half widths
				double u = (x * cr + z * sr) / rx, v = (-x * sr + z * cr) / rz;
				double rho = Math.sqrt(u * u + v * v);
				double e = edge(p, Math.atan2(v, u));
				int b = -1;
				if(rho <= e) {
					double g = profile(p, rho / e);
					if(p.roughness > 0) g *= 1.0 + p.roughness * 0.6 * IslandNoise.fbm(p.seed ^ depthSalt, x / 9.0, z / 9.0);
					if(p.profile == Profile.TERRACED) g = Math.ceil(g * terraces - 1e-9) / terraces;
					b = (int) Math.round(clamp(g, 0.0, 1.0) * p.depth);
				}
				bottom[(x + h) * n + (z + h)] = b;
			}
		}
		return bottom;
	}

	/**
	 * The shell, in order x, z, then y from the top down; each cell once. A cell (x, y, z) of a
	 * column is shell when some non-solid cell lies within Manhattan distance `wall`: for every
	 * column offset (dx, dz) with |dx| + |dz| <= wall, the vertical budget left is
	 * k = wall - |dx| - |dz|, and the neighbour column has a non-solid cell within k of y when it is
	 * outside the plan, or y + k reaches above the top, or y - k reaches below its bottom.
	 */
	public static void enumerate(Params params, IBlockConsumer out) throws InterruptedException {
		Params p = params.clamped();
		int w = p.wall, h = halfBox(p) + w, n = 2 * h + 1, inner = h - w;
		int[] bottom = columns(p, w);
		for(int x = -inner;x <= inner;++x) {
			for(int z = -inner;z <= inner;++z) {
				int b = bottom[(x + h) * n + (z + h)];
				if(b < 0) continue;
				for(int y = 0;y >= -b;--y) {
					if(isShell(bottom, h, n, w, x, y, z)) out.accept(x, y, z);
				}
			}
		}
	}

	private static boolean isShell(int[] bottom, int h, int n, int w, int x, int y, int z) {
		for(int dx = -w;dx <= w;++dx) {
			int rest = w - Math.abs(dx);
			for(int dz = -rest;dz <= rest;++dz) {
				int k = rest - Math.abs(dz);
				int nb = bottom[(x + dx + h) * n + (z + dz + h)];
				if(nb < 0 || y + k >= 1 || y - k <= -nb - 1) return true;
			}
		}
		return false;
	}
}
