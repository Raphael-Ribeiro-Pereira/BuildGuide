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

	// Spikes (block B): cones under the body, pointing down
	public enum SpikeMode{
		RANDOM,
		RING,
		// Spikes 2: a sunflower spiral over the whole outline, with a seeded jitter
		FILL
	}

	public static final int maxWidth = 121, minWidth = 3, maxDepth = 80, minSides = 3, maxSides = 12, minWall = 1, maxWall = 3;
	public static final int maxSpikes = 64, minSpikeLength = 1, maxSpikeLength = 40, minSpikeBase = 1, maxSpikeBase = 8;
	// Seed salts so the edge and the depth noise are independent fields
	private static final long edgeSalt = 0x5DEECE66DL, depthSalt = 0x2545F4914F6CDD1DL;
	// The spikes' own random stream: with no spikes nothing reads it, so the body is exactly as before
	private static final long spikeSalt = 0x7F4A7C159E3779B9L;
	// Fill: the golden angle, pi x (3 - sqrt 5), between consecutive spikes of the spiral
	public static final double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
	public static final double minTaper = 0.5, maxTaper = 3.0;
	// Dripstone: the curve's exponent is DRIP_EXP_FACTOR x Taper, the radius never drops below DRIP_TAIL_R
	// (a 1-block column) until the last DRIP_TIP_BLOCKS blocks, which narrow to the tip
	public static final double DRIP_EXP_FACTOR = 2.0, DRIP_TAIL_R = 0.5;
	public static final int DRIP_TIP_BLOCKS = 2;
	// Spikes 2, phase 3: a spike may break into pieces separated by gaps; each piece at least minPieceBlocks
	public enum BreakMode{
		ATTACHED,
		SEGMENTED
	}
	public static final int minPieces = 2, maxPieces = 4, minGap = 1, maxGap = 6, minPieceBlocks = 2;

	public static final class Params {
		public Outline outline = Outline.ORGANIC;
		public int widthX = 41, widthZ = 41, sides = 6;
		public double roundness = 0.7, rotationDeg = 0.0, edgeAmplitude = 0.25, edgeScale = 6.0;
		public int wall = 2, depth = 24;
		public Profile profile = Profile.BOWL;
		public double sharpness = 0.4, roughness = 0.3;
		public long seed = 0;
		// Spikes: count (0 = none), placement, length below the bottom surface, base radius, length variation
		// and spread in percent (spread: of the outline's half widths)
		public int spikes = 0;
		public SpikeMode spikeMode = SpikeMode.RANDOM;
		public int spikeLength = 12, spikeBase = 3, lengthVar = 0, spread = 50;
		// Spikes 2: Fill jitter in percent of half the mean spacing; edge falloff in percent (0 = every
		// spike full length, 100 = a spike on the edge is 1 long)
		public int jitter = 20, falloff = 0;
		// Spikes 2, phase 2: radius profile Base x (1 - t)^taper; Dripstone: a needle on a thick base
		public double taper = 1.0;
		public boolean dripstone = false;
		// Spikes 2, phase 3: Segmented splits each spike into pieces (the top one attached), gap blocks apart
		public BreakMode breakMode = BreakMode.ATTACHED;
		public int pieces = 2, gap = 2;

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
			p.spikes = clamp(spikes, 0, maxSpikes);
			p.spikeMode = spikeMode;
			p.spikeLength = clamp(spikeLength, minSpikeLength, maxSpikeLength);
			p.spikeBase = clamp(spikeBase, minSpikeBase, maxSpikeBase);
			p.lengthVar = clamp(lengthVar, 0, 100);
			p.spread = clamp(spread, 0, 100);
			p.jitter = clamp(jitter, 0, 100);
			p.falloff = clamp(falloff, 0, 100);
			p.taper = clamp(taper, minTaper, maxTaper);
			p.dripstone = dripstone;
			p.breakMode = breakMode;
			p.pieces = clamp(pieces, minPieces, maxPieces);
			p.gap = clamp(gap, minGap, maxGap);
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
		return columns(params, margin, null);
	}

	// The same, collecting the loose spike pieces (Segmented) in `loose` when it is given
	private static int[] columns(Params params, int margin, Loose loose) {
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
		if(p.spikes > 0) addSpikes(p, bottom, h, n, loose);
		if(loose != null) loose.finish(bottom);
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
		Loose loose = p.breakMode == BreakMode.SEGMENTED && p.spikes > 0 ? new Loose(n) : null;
		int[] bottom = columns(p, w, loose);
		if(loose != null && !loose.isEmpty()) {
			enumerateUnion(bottom, loose, h, n, w, inner, out);
			return;
		}
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

	/**
	 * Segmented spikes: the shell of the union of the columns (body and attached pieces) with the loose
	 * pieces, by the same definition (a solid cell within Manhattan distance wall of a non-solid one; the
	 * gaps under the cuts are non-solid, so the cut faces are outside). Columns first, as enumerate does,
	 * then the loose cells below them; each cell once.
	 */
	private static void enumerateUnion(int[] bottom, Loose loose, int h, int n, int w, int inner, IBlockConsumer out) throws InterruptedException {
		for(int x = -inner;x <= inner;++x) {
			for(int z = -inner;z <= inner;++z) {
				int b = bottom[(x + h) * n + (z + h)];
				if(b < 0) continue;
				for(int y = 0;y >= -b;--y) {
					if(isShellUnion(bottom, loose, h, n, w, x, y, z)) out.accept(x, y, z);
				}
			}
		}
		for(int x = -inner;x <= inner;++x) {
			for(int z = -inner;z <= inner;++z) {
				int[] iv = loose.at((x + h) * n + (z + h));
				if(iv == null) continue;
				for(int k = 0;k < iv.length;k += 2) {
					for(int y = iv[k + 1];y >= iv[k];--y) {
						if(isShellUnion(bottom, loose, h, n, w, x, y, z)) out.accept(x, y, z);
					}
				}
			}
		}
	}

	private static boolean isShellUnion(int[] bottom, Loose loose, int h, int n, int w, int x, int y, int z) {
		for(int dx = -w;dx <= w;++dx) {
			int rest = w - Math.abs(dx);
			for(int dz = -rest;dz <= rest;++dz) {
				int k = rest - Math.abs(dz);
				int i = (x + dx + h) * n + (z + dz + h);
				int nb = bottom[i];
				int[] iv = loose.at(i);
				if(iv == null) {
					if(nb < 0 || y + k >= 1 || y - k <= -nb - 1) return true;
				}else {
					for(int yy = y - k;yy <= y + k;++yy) {
						if(!(yy <= 0 && yy >= -nb) && !Loose.contains(iv, yy)) return true;
					}
				}
			}
		}
		return false;
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

	// Whether a point of the normalised plan (u, v, before the rotation) is inside the outline
	public static boolean insideOutline(Params params, double u, double v) {
		Params p = params.clamped();
		return Math.sqrt(u * u + v * v) <= edge(p, Math.atan2(v, u));
	}

	/**
	 * Where the spikes grow, from the body alone: one row per spike, {u, v, x, z, length}. (u, v) is the
	 * root in the normalised plan, (x, z) its column after the rotation. Random: radius Spread x sqrt(r)
	 * and a uniform angle, from the spikes' own stream of the seed; Ring: equal angles on a ring of
	 * radius Spread (one spike: the centre), independent of the seed; Fill: a sunflower spiral (golden
	 * angle, radial fraction sqrt((i + 0.5) / N) x Spread of the edge at its angle; one spike: the centre)
	 * moved by the seeded jitter. A root outside the outline, or on a column outside the plan, is pulled
	 * towards the centre (same angle) until it is inside. Length: Spike length x (1 - falloff x rho^2) x
	 * (1 + var x U(-1, 1)), clamped to [1, 40]. Empty when there are no spikes.
	 */
	public static double[][] spikePlan(Params params) {
		Params p = params.clamped();
		if(p.spikes == 0) return new double[0][];
		Params body = p.clamped();
		body.spikes = 0;
		int h = halfBox(p), n = 2 * h + 1;
		return spikePlan(p, columns(body, 0), h, n);
	}

	private static double[][] spikePlan(Params p, int[] bodyBottom, int h, int n) {
		java.util.Random random = new java.util.Random(IslandNoise.mix(p.seed ^ spikeSalt));
		double rx = p.widthX / 2.0, rz = p.widthZ / 2.0;
		double rot = Math.toRadians(p.rotationDeg), cr = Math.cos(rot), sr = Math.sin(rot);
		double spread = p.spread / 100.0;
		double[][] plan = new double[p.spikes][];
		double jitterReach = p.spikeMode == SpikeMode.FILL ? p.jitter / 100.0 * 0.5 * fillSpacing(p, spread) : 0;
		for(int i = 0;i < p.spikes;++i) {
			double u, v;
			if(p.spikeMode == SpikeMode.RANDOM) {
				double angle = random.nextDouble() * 2 * Math.PI, radius = spread * Math.sqrt(random.nextDouble());
				u = radius * Math.cos(angle);
				v = radius * Math.sin(angle);
			}else if(p.spikes == 1) {
				u = 0;
				v = 0;
			}else if(p.spikeMode == SpikeMode.FILL) {
				// Spiral: radial fraction sqrt((i + 0.5) / N) x Spread of the edge at that angle, so the
				// spikes reach every part of the outline, corners included; then the seeded jitter
				double angle = i * goldenAngle, fraction = Math.sqrt((i + 0.5) / p.spikes) * spread * edge(p, angle);
				u = fraction * Math.cos(angle);
				v = fraction * Math.sin(angle);
				double jitterAngle = random.nextDouble() * 2 * Math.PI, jitterDistance = jitterReach * random.nextDouble();
				u += jitterDistance * Math.cos(jitterAngle);
				v += jitterDistance * Math.sin(jitterAngle);
			}else {
				double angle = 2 * Math.PI * i / p.spikes;
				u = spread * Math.cos(angle);
				v = spread * Math.sin(angle);
			}
			double vary = 1.0 + p.lengthVar / 100.0 * (random.nextDouble() * 2.0 - 1.0);
			int x = 0, z = 0;
			for(int k = 0;;++k) {
				// Into the grid: undo the normalisation, then apply the rotation (the inverse of columns())
				double a = u * rx, b = v * rz;
				x = (int) Math.round(a * cr - b * sr);
				z = (int) Math.round(a * sr + b * cr);
				boolean inPlan = Math.abs(x) <= h && Math.abs(z) <= h && bodyBottom[(x + h) * n + (z + h)] >= 0;
				if(inPlan && Math.sqrt(u * u + v * v) <= edge(p, Math.atan2(v, u))) break;
				if(k >= 200) {
					u = 0;
					v = 0;
					x = 0;
					z = 0;
					break;
				}
				u *= 0.9;
				v *= 0.9;
			}
			// Edge falloff from the final root: m = 1 - falloff x rho^2, rho = 0 at the centre, 1 on the edge
			double rho = Math.sqrt(u * u + v * v) / edge(p, Math.atan2(v, u));
			double length = p.spikeLength * (1.0 - p.falloff / 100.0 * rho * rho) * vary;
			plan[i] = new double[] {u, v, x, z, clamp(Math.round(length), minSpikeLength, maxSpikeLength)};
		}
		return plan;
	}

	// Fill: mean distance between spikes in the normalised plan, sqrt(area / N), the area being the
	// outline's (half the integral of edge^2) scaled by Spread^2
	public static double fillSpacing(Params p, double spread) {
		double area = 0;
		int samples = 720;
		for(int k = 0;k < samples;++k) {
			double e = edge(p, 2 * Math.PI * k / samples);
			area += 0.5 * e * e * (2 * Math.PI / samples);
		}
		return Math.sqrt(area * spread * spread / p.spikes);
	}

	/**
	 * The union of the body with the spikes, column by column (the body is one column per position, so
	 * the union stays one): a column under a spike goes down to the cone. Each spike is a vertical cone,
	 * tip down, whose radius is Spike base (+ 0.5, the cell) at the body's bottom surface under its root
	 * and 0 at length below it. Upwards it runs into the body as far as needed, so the joint has no gap.
	 * Columns outside the plan stay empty: the top is the flat cap at y = 0, so a spike cannot grow
	 * beyond the outline. The shell is then computed on these columns exactly as for the body alone.
	 */
	private static void addSpikes(Params p, int[] bottom, int h, int n, Loose loose) {
		int[] body = bottom.clone();
		double[][] plan = spikePlan(p, body, h, n);
		double radius = p.spikeBase + 0.5;
		for(double[] s: plan) {
			int cx = (int) s[2], cz = (int) s[3], length = (int) s[4];
			int surface = body[(cx + h) * n + (cz + h)];
			// Segmented: {pieces, blocks per piece}; null keeps the spike whole (Attached, or too short)
			int[] layout = p.breakMode == BreakMode.SEGMENTED ? pieceLayout(length, p.pieces, p.gap) : null;
			for(int x = cx - p.spikeBase;x <= cx + p.spikeBase;++x) {
				for(int z = cz - p.spikeBase;z <= cz + p.spikeBase;++z) {
					if(Math.abs(x) > h || Math.abs(z) > h) continue;
					int i = (x + h) * n + (z + h);
					if(body[i] < 0) continue;
					double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
					int reach = spikeReach(p, d, length, radius);
					if(reach < 0) continue;
					if(layout != null) {
						// Piece k spans the offsets k x (blocks + gap) + 1 .. k x (blocks + gap) + blocks below the
						// surface, each a cut of the same profile; the top one stays attached to the body
						int step = layout[1] + p.gap;
						for(int k = 1;k < layout[0] && loose != null;++k) {
							int from = k * step + 1, to = Math.min(k * step + layout[1], reach);
							if(from <= to) loose.add(i, -(surface + to), -(surface + from));
						}
						reach = Math.min(reach, layout[1]);
					}
					int depth = surface + reach;
					if(depth > bottom[i]) bottom[i] = depth;
				}
			}
		}
	}

	/**
	 * How many blocks below the bottom surface a spike of this length reaches in a column at horizontal
	 * distance d from its axis, or -1 when that column is not part of it. radius = Spike base + 0.5 (the
	 * cell). Taper 1 without Dripstone is the block B cone, computed exactly as before; another Taper
	 * gives radius x (1 - t)^taper; Dripstone follows dripRadius layer by layer.
	 */
	static int spikeReach(Params p, double d, int length, double radius) {
		if(!p.dripstone) {
			if(d >= radius) return -1;
			if(p.taper == 1.0) return (int) Math.round(length * (1.0 - d / radius));
			return (int) Math.round(length * (1.0 - Math.pow(d / radius, 1.0 / p.taper)));
		}
		int reach = -1;
		for(int j = 1;j <= length;++j) {
			if(d < dripRadius(p, (j - 0.5) / length, length, radius)) reach = j;
			else break;
		}
		return reach;
	}

	// Dripstone: the radius within which a cell belongs to the spike at t (0 root, 1 tip); the floor is
	// DRIP_TAIL_R + 0.5 (only the axis cell) and the last DRIP_TIP_BLOCKS blocks narrow from it to 0
	static double dripRadius(Params p, double t, int length, double radius) {
		double floor = DRIP_TAIL_R + 0.5, aboveTip = (1.0 - t) * length;
		if(aboveTip < DRIP_TIP_BLOCKS) return floor * aboveTip / DRIP_TIP_BLOCKS;
		return Math.max(radius * Math.pow(1.0 - t, DRIP_EXP_FACTOR * p.taper), floor);
	}

	/**
	 * The spike's radius profile in blocks at t (0 root, 1 tip), as specified: Base x (1 - t)^Taper, or
	 * with Dripstone Base x (1 - t)^(2 x Taper) held at DRIP_TAIL_R until the last DRIP_TIP_BLOCKS of
	 * `length`, which narrow to 0. (Cells are included up to that radius + 0.5, see spikeReach.)
	 */
	public static double spikeRadius(Params params, double t, int length) {
		Params p = params.clamped();
		t = clamp(t, 0.0, 1.0);
		if(!p.dripstone) return p.spikeBase * Math.pow(1.0 - t, p.taper);
		double aboveTip = (1.0 - t) * length;
		if(aboveTip < DRIP_TIP_BLOCKS) return DRIP_TAIL_R * aboveTip / DRIP_TIP_BLOCKS;
		return Math.max(p.spikeBase * Math.pow(1.0 - t, DRIP_EXP_FACTOR * p.taper), DRIP_TAIL_R);
	}

	/**
	 * Segmented: how a spike of this length breaks, {pieces, blocks per piece}: each piece is
	 * (length - (pieces - 1) x gap) / pieces blocks (rounded down); with fewer than minPieceBlocks a piece
	 * less is tried, down to 2. Null when even two do not fit: the spike stays whole.
	 */
	public static int[] pieceLayout(int length, int pieces, int gap) {
		for(int count = clamp(pieces, minPieces, maxPieces);count >= minPieces;--count) {
			int each = (length - (count - 1) * gap) / count;
			if(each >= minPieceBlocks) return new int[] {count, each};
		}
		return null;
	}

	// Every cell of the loose pieces (Segmented), {x, y, z}, below the columns; empty otherwise
	public static java.util.List<int[]> looseCells(Params params) {
		Params p = params.clamped();
		java.util.List<int[]> cells = new java.util.ArrayList<int[]>();
		if(p.breakMode != BreakMode.SEGMENTED || p.spikes == 0) return cells;
		int h = halfBox(p), n = 2 * h + 1;
		Loose loose = new Loose(n);
		columns(p, 0, loose);
		for(int i = 0;i < n * n;++i) {
			int[] iv = loose.at(i);
			if(iv == null) continue;
			for(int k = 0;k < iv.length;k += 2) for(int y = iv[k];y <= iv[k + 1];++y) cells.add(new int[] {i / n - h, y, i % n - h});
		}
		return cells;
	}

	/**
	 * The loose spike pieces, column by column: per column a sorted list of disjoint y intervals
	 * {low, high, low, high, ...}, all below the column's own bottom (what overlaps the column is part of
	 * it anyway). The body keeps its one depth per column; only this sparse list is added.
	 */
	static final class Loose {
		private final java.util.Map<Integer, java.util.List<int[]>> raw = new java.util.HashMap<Integer, java.util.List<int[]>>();
		private final int[][] merged;

		Loose(int n) {
			merged = new int[n * n][];
		}

		void add(int column, int low, int high) {
			raw.computeIfAbsent(column, c -> new java.util.ArrayList<int[]>()).add(new int[] {low, high});
		}

		// Clip below each column's bottom, then merge overlapping or touching intervals
		void finish(int[] bottom) {
			for(java.util.Map.Entry<Integer, java.util.List<int[]>> e: raw.entrySet()) {
				int top = -bottom[e.getKey()] - 1;
				java.util.List<int[]> list = new java.util.ArrayList<int[]>();
				for(int[] r: e.getValue()) if(r[0] <= Math.min(r[1], top)) list.add(new int[] {r[0], Math.min(r[1], top)});
				if(list.isEmpty()) continue;
				list.sort((a, b) -> Integer.compare(a[0], b[0]));
				java.util.List<int[]> out = new java.util.ArrayList<int[]>();
				for(int[] r: list) {
					if(!out.isEmpty() && r[0] <= out.get(out.size() - 1)[1] + 1) out.get(out.size() - 1)[1] = Math.max(out.get(out.size() - 1)[1], r[1]);
					else out.add(r);
				}
				int[] flat = new int[out.size() * 2];
				for(int k = 0;k < out.size();++k) {
					flat[2 * k] = out.get(k)[0];
					flat[2 * k + 1] = out.get(k)[1];
				}
				merged[e.getKey()] = flat;
			}
		}

		boolean isEmpty() {
			for(int[] iv: merged) if(iv != null) return false;
			return true;
		}

		int[] at(int column) {
			return merged[column];
		}

		static boolean contains(int[] iv, int y) {
			for(int k = 0;k < iv.length;k += 2) if(y >= iv[k] && y <= iv[k + 1]) return true;
			return false;
		}
	}
}
