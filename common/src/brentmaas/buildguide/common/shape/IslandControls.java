package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;

/**
 * Island controls without the GUI: which plan controls apply to each Outline, and the Randomize and
 * Naturalize recipes over a snapshot of the values (Values), which is also what Undo restores.
 * Pure: the random source is passed in, so a seeded Random gives the same result every time.
 *
 * Only primitives and java.*: no net.minecraft.
 */
public final class IslandControls {
	// Plan (Base) controls whose visibility depends on the Outline; Outline itself is always shown
	public enum Control{
		WIDTH_X,
		WIDTH_Z,
		SIDES,
		CORNER_ROUND,
		ROTATION,
		WOBBLE,
		WOBBLE_SIZE
	}

	// Ranges Randomize moves within (and clamps to); the widths, sides and depth are IslandGeometry's limits
	public static final float rotationMin = -180.0f, rotationMax = 180.0f;
	public static final float wobbleSizeMin = 1.0f, wobbleSizeMax = 16.0f;
	// Grids the randomized values are rounded to, so the fields read clean numbers
	public static final float unitGrid = 0.05f, rotationGrid = 1.0f, wobbleSizeGrid = 0.5f;
	public static final int seedBound = 1000000;
	public static final int percentGrid = 5;
	public static final float taperGrid = 0.1f;

	// Naturalize spikes recipe (spikes 2): centre value and spread, drawn from the current seed. With no
	// spikes it turns naturalSpikeCount on
	public static final int naturalSpikeCount = 8;
	public static final int naturalFalloff = 60, naturalFalloffSpread = 15;
	public static final int naturalLengthVar = 30, naturalLengthVarSpread = 10;
	public static final float naturalTaper = 1.4f, naturalTaperSpread = 0.3f;
	public static final int naturalJitter = 25, naturalJitterSpread = 10;
	private static final long naturalSpikesSalt = 0x3C6EF372FE94F82BL;

	// Naturalize recipe: centre value and how far a fresh seed moves it either way
	public static final float naturalCornerRound = 0.75f, naturalCornerRoundSpread = 0.15f;
	public static final float naturalWobble = 0.25f, naturalWobbleSpread = 0.08f;
	public static final float naturalWobbleSize = 6.0f, naturalWobbleSizeSpread = 2.0f;
	public static final float naturalSharpness = 0.45f, naturalSharpnessSpread = 0.15f;
	public static final float naturalRoughness = 0.35f, naturalRoughnessSpread = 0.15f;
	public static final Outline naturalOutline = Outline.ORGANIC;
	public static final Profile naturalProfile = Profile.BOWL;

	private IslandControls() {}

	/**
	 * Whether a plan control changes the geometry for this Outline (IslandGeometry.edge): Circle
	 * ignores Corner round, Sides only shapes the Polygon, Wobble and its size only the Organic edge.
	 * Rotation always applies (with unequal widths even a Circle is an ellipse).
	 */
	public static boolean applies(Outline outline, Control control) {
		switch(control) {
		case SIDES:
			return outline == Outline.POLYGON;
		case CORNER_ROUND:
			return outline != Outline.CIRCLE;
		case WOBBLE:
		case WOBBLE_SIZE:
			return outline == Outline.ORGANIC;
		default:
			return true;
		}
	}

	// Every value Randomize, Naturalize and Undo touch
	public static final class Values {
		public Outline outline;
		public Profile profile;
		public int widthX, widthZ, sides, depth, seed;
		public float cornerRound, rotation, wobble, wobbleSize, sharpness, roughness;
		// Spikes (block B); the mode is only carried, Randomize never changes it
		public int spikes, spikeLength, spikeBase, lengthVar, spread, jitter;
		public IslandGeometry.SpikeMode spikeMode = IslandGeometry.SpikeMode.RANDOM;
		// Spikes 2, phase 2: shape of the spikes; Dripstone is only carried, Randomize never changes it
		public int falloff;
		public float taper = 1.0f;
		public boolean dripstone;
		// Spikes 2, phase 3: only carried (Randomize and Naturalize spikes never change them), Undo restores them
		public IslandGeometry.BreakMode breakMode = IslandGeometry.BreakMode.ATTACHED;
		public int pieces = 2, gap = 2;

		public Values copy() {
			Values v = new Values();
			v.outline = outline;
			v.profile = profile;
			v.widthX = widthX;
			v.widthZ = widthZ;
			v.sides = sides;
			v.depth = depth;
			v.seed = seed;
			v.cornerRound = cornerRound;
			v.rotation = rotation;
			v.wobble = wobble;
			v.wobbleSize = wobbleSize;
			v.sharpness = sharpness;
			v.roughness = roughness;
			v.spikes = spikes;
			v.spikeLength = spikeLength;
			v.spikeBase = spikeBase;
			v.lengthVar = lengthVar;
			v.spread = spread;
			v.jitter = jitter;
			v.spikeMode = spikeMode;
			v.falloff = falloff;
			v.taper = taper;
			v.dripstone = dripstone;
			v.breakMode = breakMode;
			v.pieces = pieces;
			v.gap = gap;
			return v;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Values)) return false;
			Values v = (Values) o;
			return outline == v.outline && profile == v.profile && widthX == v.widthX && widthZ == v.widthZ && sides == v.sides && depth == v.depth && seed == v.seed
					&& cornerRound == v.cornerRound && rotation == v.rotation && wobble == v.wobble && wobbleSize == v.wobbleSize && sharpness == v.sharpness && roughness == v.roughness
					&& spikes == v.spikes && spikeLength == v.spikeLength && spikeBase == v.spikeBase && lengthVar == v.lengthVar && spread == v.spread && jitter == v.jitter && spikeMode == v.spikeMode
					&& falloff == v.falloff && taper == v.taper && dripstone == v.dripstone
					&& breakMode == v.breakMode && pieces == v.pieces && gap == v.gap;
		}

		@Override
		public int hashCode() {
			return seed;
		}
	}

	/**
	 * A new seed, and each control of a group moved by up to its percentage of its range:
	 * new = clamp(current + U(-1, 1) x percent x range). Base moves the plan controls that apply to
	 * the current Outline; Body moves Depth, Sharpness and Roughness; Spikes (only when there are spikes)
	 * moves Count (kept >= 1), Length, Base, Length var, Spread, Taper, Edge falloff and, in Fill, Jitter. A
	 * group at 0 % is left alone. Outline, Profile, Spike mode and Dripstone never change.
	 */
	public static Values randomize(Values current, int basePercent, int bodyPercent, int spikesPercent, Random random) {
		Values v = current.copy();
		v.seed = random.nextInt(seedBound);
		double base = Math.max(0, Math.min(100, basePercent)) / 100.0, body = Math.max(0, Math.min(100, bodyPercent)) / 100.0, spike = Math.max(0, Math.min(100, spikesPercent)) / 100.0;
		if(base > 0) {
			Outline o = v.outline;
			if(applies(o, Control.WIDTH_X)) v.widthX = moveInt(v.widthX, IslandGeometry.minWidth, IslandGeometry.maxWidth, base, random);
			if(applies(o, Control.WIDTH_Z)) v.widthZ = moveInt(v.widthZ, IslandGeometry.minWidth, IslandGeometry.maxWidth, base, random);
			if(applies(o, Control.SIDES)) v.sides = moveInt(v.sides, IslandGeometry.minSides, IslandGeometry.maxSides, base, random);
			if(applies(o, Control.CORNER_ROUND)) v.cornerRound = move(v.cornerRound, 0.0f, 1.0f, unitGrid, base, random);
			if(applies(o, Control.ROTATION)) v.rotation = move(v.rotation, rotationMin, rotationMax, rotationGrid, base, random);
			if(applies(o, Control.WOBBLE)) v.wobble = move(v.wobble, 0.0f, 1.0f, unitGrid, base, random);
			if(applies(o, Control.WOBBLE_SIZE)) v.wobbleSize = move(v.wobbleSize, wobbleSizeMin, wobbleSizeMax, wobbleSizeGrid, base, random);
		}
		if(body > 0) {
			v.depth = moveInt(v.depth, 0, IslandGeometry.maxDepth, body, random);
			v.sharpness = move(v.sharpness, 0.0f, 1.0f, unitGrid, body, random);
			v.roughness = move(v.roughness, 0.0f, 1.0f, unitGrid, body, random);
		}
		// Spikes % (spikes 2: no longer part of Body). Randomize never turns spikes on or off: with none,
		// nothing is drawn for them; with some, the count stays >= 1
		if(spike > 0 && v.spikes > 0) {
			v.spikes = moveInt(v.spikes, 1, IslandGeometry.maxSpikes, spike, random);
			v.spikeLength = moveInt(v.spikeLength, IslandGeometry.minSpikeLength, IslandGeometry.maxSpikeLength, spike, random);
			v.spikeBase = moveInt(v.spikeBase, IslandGeometry.minSpikeBase, IslandGeometry.maxSpikeBase, spike, random);
			v.lengthVar = movePercent(v.lengthVar, spike, random);
			v.spread = movePercent(v.spread, spike, random);
			v.taper = move(v.taper, (float) IslandGeometry.minTaper, (float) IslandGeometry.maxTaper, taperGrid, spike, random);
			v.falloff = movePercent(v.falloff, spike, random);
			if(v.spikeMode == IslandGeometry.SpikeMode.FILL) v.jitter = movePercent(v.jitter, spike, random);
		}
		return v;
	}

	/**
	 * The natural spikes recipe: Edge falloff, Length var, Taper and Jitter at the recipe's centre values,
	 * moved by up to their spread with a stream drawn from the current seed (the same seed gives the same
	 * spikes). With no spikes it turns naturalSpikeCount on; otherwise Count stays. Seed, Spike mode,
	 * Dripstone, Break, Length, Base and Spread are kept.
	 */
	public static Values naturalizeSpikes(Values current) {
		Values v = current.copy();
		Random random = new Random(IslandNoise.mix(v.seed ^ naturalSpikesSalt));
		if(v.spikes == 0) v.spikes = naturalSpikeCount;
		v.falloff = aroundPercent(naturalFalloff, naturalFalloffSpread, random);
		v.lengthVar = aroundPercent(naturalLengthVar, naturalLengthVarSpread, random);
		v.taper = around(naturalTaper, naturalTaperSpread, (float) IslandGeometry.minTaper, (float) IslandGeometry.maxTaper, taperGrid, random);
		v.jitter = aroundPercent(naturalJitter, naturalJitterSpread, random);
		return v;
	}

	private static int aroundPercent(int centre, int spread, Random random) {
		long value = Math.round((centre + unit(random) * spread) / percentGrid) * percentGrid;
		return (int) Math.max(0, Math.min(100, value));
	}

	/**
	 * The natural island recipe with a fresh seed: Organic outline, Bowl body, and Corner round,
	 * Wobble, Wobble size, Sharpness and Roughness at the recipe's centre values moved by up to their
	 * spread. Widths, Depth, Rotation, Sides and every spike control are kept (Wall and the origin are
	 * not in Values).
	 */
	public static Values naturalize(Values current, Random random) {
		Values v = current.copy();
		v.seed = random.nextInt(seedBound);
		v.outline = naturalOutline;
		v.profile = naturalProfile;
		v.cornerRound = around(naturalCornerRound, naturalCornerRoundSpread, 0.0f, 1.0f, unitGrid, random);
		v.wobble = around(naturalWobble, naturalWobbleSpread, 0.0f, 1.0f, unitGrid, random);
		v.wobbleSize = around(naturalWobbleSize, naturalWobbleSizeSpread, wobbleSizeMin, wobbleSizeMax, wobbleSizeGrid, random);
		v.sharpness = around(naturalSharpness, naturalSharpnessSpread, 0.0f, 1.0f, unitGrid, random);
		v.roughness = around(naturalRoughness, naturalRoughnessSpread, 0.0f, 1.0f, unitGrid, random);
		return v;
	}

	private static double unit(Random random) {
		return random.nextDouble() * 2.0 - 1.0;
	}

	private static int moveInt(int value, int min, int max, double fraction, Random random) {
		long moved = Math.round(value + unit(random) * fraction * (max - min));
		return (int) Math.max(min, Math.min(max, moved));
	}

	// A 0..100 percentage on its 5 grid (the fields step by 5)
	private static int movePercent(int value, double fraction, Random random) {
		long moved = Math.round((value + unit(random) * fraction * 100) / percentGrid) * percentGrid;
		return (int) Math.max(0, Math.min(100, moved));
	}

	private static float move(float value, float min, float max, float grid, double fraction, Random random) {
		return snap(value + unit(random) * fraction * (max - min), min, max, grid);
	}

	private static float around(float centre, float spread, float min, float max, float grid, Random random) {
		return snap(centre + unit(random) * spread, min, max, grid);
	}

	// Clamp, then round to the grid through its decimal string so 0.05 steps stay clean (0.15, not 0.15000001)
	static float snap(double value, float min, float max, float grid) {
		double clamped = Math.max(min, Math.min(max, value));
		long steps = Math.round(clamped / grid);
		float snapped = new java.math.BigDecimal(Float.toString(grid)).multiply(java.math.BigDecimal.valueOf(steps)).floatValue();
		return Math.max(min, Math.min(max, snapped));
	}
}
