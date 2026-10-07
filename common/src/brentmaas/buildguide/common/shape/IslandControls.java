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
			return v;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Values)) return false;
			Values v = (Values) o;
			return outline == v.outline && profile == v.profile && widthX == v.widthX && widthZ == v.widthZ && sides == v.sides && depth == v.depth && seed == v.seed
					&& cornerRound == v.cornerRound && rotation == v.rotation && wobble == v.wobble && wobbleSize == v.wobbleSize && sharpness == v.sharpness && roughness == v.roughness
					&& spikes == v.spikes && spikeLength == v.spikeLength && spikeBase == v.spikeBase && lengthVar == v.lengthVar && spread == v.spread && jitter == v.jitter && spikeMode == v.spikeMode;
		}

		@Override
		public int hashCode() {
			return seed;
		}
	}

	/**
	 * A new seed, and each control of a group moved by up to its percentage of its range:
	 * new = clamp(current + U(-1, 1) x percent x range). Base moves the plan controls that apply to
	 * the current Outline; Body moves Depth, Sharpness and Roughness. A group at 0 % is left alone.
	 * Outline and Profile never change.
	 */
	public static Values randomize(Values current, int basePercent, int bodyPercent, Random random) {
		Values v = current.copy();
		v.seed = random.nextInt(seedBound);
		double base = Math.max(0, Math.min(100, basePercent)) / 100.0, body = Math.max(0, Math.min(100, bodyPercent)) / 100.0;
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
			// Spikes belong to Body, but Randomize never turns them on or off: with none, nothing is drawn for
			// them (so islands without spikes randomize exactly as before); with some, the count stays >= 1
			if(v.spikes > 0) {
				v.spikes = moveInt(v.spikes, 1, IslandGeometry.maxSpikes, body, random);
				v.spikeLength = moveInt(v.spikeLength, IslandGeometry.minSpikeLength, IslandGeometry.maxSpikeLength, body, random);
				v.spikeBase = moveInt(v.spikeBase, IslandGeometry.minSpikeBase, IslandGeometry.maxSpikeBase, body, random);
				v.lengthVar = movePercent(v.lengthVar, body, random);
				v.spread = movePercent(v.spread, body, random);
			}
		}
		return v;
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
