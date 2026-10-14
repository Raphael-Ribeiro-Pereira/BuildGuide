package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.shape.TerrainGeometry.Outline;
import brentmaas.buildguide.common.shape.TerrainGeometry.Type;

/**
 * Terrain controls without the GUI: which plan controls apply to each Outline, the height cap, and the
 * Randomize and Natural recipes over a snapshot of the values (Values), which is also what Undo restores.
 * Pure: the random source is passed in (Natural draws from the current seed), so the same input gives the
 * same result. The Terrain's own copy: nothing is shared with the Island's classes.
 *
 * Only primitives and java.*: no net.minecraft.
 */
public final class TerrainControls {
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

	// Ranges Randomize moves within (and clamps to); widths, sides, height and scale are TerrainGeometry's limits
	public static final float rotationMin = -180.0f, rotationMax = 180.0f;
	public static final float wobbleSizeMin = 1.0f, wobbleSizeMax = 16.0f;
	// Grids the randomized values are rounded to, so the fields read clean numbers
	public static final float unitGrid = 0.05f, rotationGrid = 1.0f, wobbleSizeGrid = 0.5f;
	public static final int seedBound = 1000000;
	public static final int percentGrid = 5;

	// Natural recipe: centre value and how far the current seed moves it either way
	public static final Outline NATURAL_OUTLINE = Outline.ORGANIC;
	public static final float NATURAL_CORNER_ROUND = 0.75f, NATURAL_CORNER_ROUND_SPREAD = 0.15f;
	public static final float NATURAL_WOBBLE = 0.25f, NATURAL_WOBBLE_SPREAD = 0.08f;
	public static final float NATURAL_WOBBLE_SIZE = 6.0f, NATURAL_WOBBLE_SIZE_SPREAD = 2.0f;
	public static final float NATURAL_ROUGHNESS = 0.40f, NATURAL_ROUGHNESS_SPREAD = 0.15f;
	public static final int NATURAL_EDGE_FADE = 35, NATURAL_EDGE_FADE_SPREAD = 10;
	private static final long naturalSalt = 0x5BE0CD19137E2179L;

	private TerrainControls() {}

	/**
	 * Whether a plan control changes the geometry for this Outline (TerrainGeometry.edge): Circle ignores
	 * Corner round, Sides only shapes the Polygon, Wobble and its size only the Organic edge. Rotation always
	 * applies (with unequal widths even a Circle is an ellipse, and it sets the Dunes' direction).
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

	// Every value Randomize, Natural and Undo touch (Skin, Type and Outline are carried; the focus is not here)
	public static final class Values {
		public Outline outline = Outline.CIRCLE;
		public Type type = Type.HILLS;
		public int widthX, widthZ, sides, height, scale, edgeFade, skin, seed;
		public float cornerRound, rotation, wobble, wobbleSize, roughness;

		public Values copy() {
			Values v = new Values();
			v.outline = outline;
			v.type = type;
			v.widthX = widthX;
			v.widthZ = widthZ;
			v.sides = sides;
			v.height = height;
			v.scale = scale;
			v.edgeFade = edgeFade;
			v.skin = skin;
			v.seed = seed;
			v.cornerRound = cornerRound;
			v.rotation = rotation;
			v.wobble = wobble;
			v.wobbleSize = wobbleSize;
			v.roughness = roughness;
			return v;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Values)) return false;
			Values v = (Values) o;
			return outline == v.outline && type == v.type && widthX == v.widthX && widthZ == v.widthZ && sides == v.sides && height == v.height && scale == v.scale
					&& edgeFade == v.edgeFade && skin == v.skin && seed == v.seed && cornerRound == v.cornerRound && rotation == v.rotation && wobble == v.wobble
					&& wobbleSize == v.wobbleSize && roughness == v.roughness;
		}

		@Override
		public int hashCode() {
			return seed;
		}
	}

	// The cap: Height brought to [minHeight, min(maxHeight, 3 x Scale)] (TerrainGeometry.maxHeightFor)
	public static int cappedHeight(int height, int scale) {
		return Math.max(TerrainGeometry.minHeight, Math.min(TerrainGeometry.maxHeightFor(scale), height));
	}

	/**
	 * A new seed, and each control of a group moved by up to its percentage of its range:
	 * new = clamp(current + U(-1, 1) x percent x range), on its grid. Base moves Width X / Z, Corner round,
	 * Rotation, Wobble, Wobble size and, only for a Polygon, Sides; Relief moves Height, Scale, Roughness and
	 * Edge fade, then the cap holds Height under 3 x Scale. A group at 0 % is left alone. Outline, Type and
	 * Skin never change.
	 */
	public static Values randomize(Values current, int basePercent, int reliefPercent, Random random) {
		Values v = current.copy();
		v.seed = random.nextInt(seedBound);
		double base = Math.max(0, Math.min(100, basePercent)) / 100.0, relief = Math.max(0, Math.min(100, reliefPercent)) / 100.0;
		if(base > 0) {
			v.widthX = moveInt(v.widthX, TerrainGeometry.minWidth, TerrainGeometry.maxWidth, base, random);
			v.widthZ = moveInt(v.widthZ, TerrainGeometry.minWidth, TerrainGeometry.maxWidth, base, random);
			if(v.outline == Outline.POLYGON) v.sides = moveInt(v.sides, TerrainGeometry.minSides, TerrainGeometry.maxSides, base, random);
			v.cornerRound = move(v.cornerRound, 0.0f, 1.0f, unitGrid, base, random);
			v.rotation = move(v.rotation, rotationMin, rotationMax, rotationGrid, base, random);
			v.wobble = move(v.wobble, 0.0f, 1.0f, unitGrid, base, random);
			v.wobbleSize = move(v.wobbleSize, wobbleSizeMin, wobbleSizeMax, wobbleSizeGrid, base, random);
		}
		if(relief > 0) {
			v.height = moveInt(v.height, TerrainGeometry.minHeight, TerrainGeometry.maxHeight, relief, random);
			v.scale = moveInt(v.scale, TerrainGeometry.minScale, TerrainGeometry.maxScale, relief, random);
			v.roughness = move(v.roughness, 0.0f, 1.0f, unitGrid, relief, random);
			v.edgeFade = movePercent(v.edgeFade, relief, random);
		}
		v.height = cappedHeight(v.height, v.scale);
		return v;
	}

	/**
	 * The natural terrain recipe, drawn from the current seed (the same seed gives the same result, the seed
	 * stays): Organic outline, and Corner round, Wobble, Wobble size, Roughness and Edge fade at the recipe's
	 * centre values moved by up to their spread. Widths, Rotation, Sides, Height, Scale, Type and Skin are
	 * kept (the focus and the origin are not in Values).
	 */
	public static Values natural(Values current) {
		Values v = current.copy();
		Random random = new Random(TerrainNoise.mix(v.seed ^ naturalSalt));
		v.outline = NATURAL_OUTLINE;
		v.cornerRound = around(NATURAL_CORNER_ROUND, NATURAL_CORNER_ROUND_SPREAD, 0.0f, 1.0f, unitGrid, random);
		v.wobble = around(NATURAL_WOBBLE, NATURAL_WOBBLE_SPREAD, 0.0f, 1.0f, unitGrid, random);
		v.wobbleSize = around(NATURAL_WOBBLE_SIZE, NATURAL_WOBBLE_SIZE_SPREAD, wobbleSizeMin, wobbleSizeMax, wobbleSizeGrid, random);
		v.roughness = around(NATURAL_ROUGHNESS, NATURAL_ROUGHNESS_SPREAD, 0.0f, 1.0f, unitGrid, random);
		v.edgeFade = (int) Math.max(0, Math.min(100, Math.round((NATURAL_EDGE_FADE + unit(random) * NATURAL_EDGE_FADE_SPREAD) / percentGrid) * percentGrid));
		v.height = cappedHeight(v.height, v.scale);
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
