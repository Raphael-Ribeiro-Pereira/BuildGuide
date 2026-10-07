package brentmaas.buildguide.common.shape;

/**
 * Layers by depth (palette stage 4C, visual only): four bands below a shape's top, cut at three
 * percentages of its depth. t = (top - y) / depth; t below cut 1 is layer 1, from cut 1 to below cut 2
 * layer 2, from cut 2 to below cut 3 layer 3, the rest layer 4. A block exactly on a cut goes to the
 * deeper layer; everything below the depth (an island's spikes) is layer 4; depth 0 makes everything
 * layer 1. Compared in integers (100 x depth below the top against cut x depth), so a cut never
 * depends on rounding.
 *
 * Immutable: a shape reports the layers of its last generation (Shape.getLayers), so the preview colours
 * the same blocks the world shows. Only primitives: no net.minecraft.
 */
public final class ShapeLayers {
	public static final int COUNT = 4;
	// Cut limits in percent of the depth, the -/+ step, and the least distance between two cuts
	public static final int minCut = 5, maxCut = 95, cutStep = 5, minCutGap = 5;
	public static final int defaultCut1 = 25, defaultCut2 = 50, defaultCut3 = 75;
	// Layer colours (0xRRGGBB), away from the status colours (green built, blue-grey missing, yellow
	// ignored, red errors) and from each other
	public static final int LAYER_1 = 0x00C8DC; // cyan
	public static final int LAYER_2 = 0xDC32C8; // magenta
	public static final int LAYER_3 = 0x8C5A2D; // brown
	public static final int LAYER_4 = 0x2846DC; // strong blue
	private static final int[] colours = {LAYER_1, LAYER_2, LAYER_3, LAYER_4};

	public final int top, depth, cut1, cut2, cut3;

	/**
	 * @param top the y of the shape's top (local)
	 * @param depth how far the body goes below the top (negative counts as 0)
	 * @param cut1 cut2 cut3 percentages of the depth; put in order first (normalize, as if cut 1 had changed)
	 */
	public ShapeLayers(int top, int depth, int cut1, int cut2, int cut3) {
		int[] c = normalize(cut1, cut2, cut3, 0);
		this.top = top;
		this.depth = Math.max(0, depth);
		this.cut1 = c[0];
		this.cut2 = c[1];
		this.cut3 = c[2];
	}

	// 1 to 4 for a block at this y
	public int layerOf(int y) {
		if(depth == 0) return 1;
		long below = 100L * (top - y);
		if(below < (long) cut1 * depth) return 1;
		if(below < (long) cut2 * depth) return 2;
		if(below < (long) cut3 * depth) return 3;
		return 4;
	}

	public static int colour(int layer) {
		return colours[Math.max(1, Math.min(COUNT, layer)) - 1];
	}

	/**
	 * Depths below the top (in blocks) that a layer covers: {first, last}, last = -1 for "and everything
	 * below" (layer 4), or null when no block depth falls in it. For the preview's legend.
	 */
	public int[] depthRange(int layer) {
		if(depth == 0) return layer == 1 ? new int[] {0, -1} : null;
		int first = layer == 1 ? 0 : firstDepthAt(layer == 2 ? cut1 : layer == 3 ? cut2 : cut3);
		if(layer == 4) return new int[] {first, -1};
		int last = firstDepthAt(layer == 1 ? cut1 : layer == 2 ? cut2 : cut3) - 1;
		return last < first ? null : new int[] {first, last};
	}

	// The smallest depth below the top whose block is at or past the cut: ceil(cut x depth / 100)
	private int firstDepthAt(int cut) {
		return (int) ((cut * (long) depth + 99) / 100);
	}

	/**
	 * Cuts in order, each at least minCutGap above the one before, within minCut..maxCut. The cut that just
	 * changed (index 0..2) keeps its value when it can (held where the others still fit) and pushes the
	 * others: the ones after it up, the ones before it down.
	 */
	public static int[] normalize(int cut1, int cut2, int cut3, int changed) {
		int[] c = {cut1, cut2, cut3};
		int k = Math.max(0, Math.min(2, changed));
		c[k] = Math.max(minCut + k * minCutGap, Math.min(maxCut - (2 - k) * minCutGap, c[k]));
		for(int i = k + 1;i < 3;++i) c[i] = Math.min(maxCut - (2 - i) * minCutGap, Math.max(c[i], c[i - 1] + minCutGap));
		for(int i = k - 1;i >= 0;--i) c[i] = Math.max(minCut + i * minCutGap, Math.min(c[i], c[i + 1] - minCutGap));
		return c;
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof ShapeLayers)) return false;
		ShapeLayers l = (ShapeLayers) o;
		return top == l.top && depth == l.depth && cut1 == l.cut1 && cut2 == l.cut2 && cut3 == l.cut3;
	}

	@Override
	public int hashCode() {
		return (((top * 31 + depth) * 31 + cut1) * 31 + cut2) * 31 + cut3;
	}
}
