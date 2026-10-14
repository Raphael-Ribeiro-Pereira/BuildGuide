package brentmaas.buildguide.common.shape;

/**
 * Layers by height for the Terrain: the layer of a column comes from the height of its surface, t = h /
 * Height, cut as ShapeLayers cuts (a surface exactly on a cut goes to the next layer), so layer 1 is the
 * lowest ground and layer 4 the peaks. The whole skin of a column takes its column's layer. Built as
 * ShapeLayers(top 0, depth Height, cuts) and asked with y = -h, so the same integer comparison decides;
 * depthRange then gives the heights above the base (y 0) each layer covers, for the preview's legend.
 *
 * Immutable: the tops are the generation's own array (TerrainGeometry.tops), never written after.
 * Only primitives: no net.minecraft.
 */
public final class TerrainLayers extends ShapeLayers {
	private final int[] tops;
	private final int h, n;

	// tops and h as TerrainGeometry.tops gives them (index (x + h) * (2h + 1) + (z + h), -1 outside the plan)
	public TerrainLayers(int height, int cut1, int cut2, int cut3, int[] tops, int h) {
		super(0, Math.max(1, height), cut1, cut2, cut3);
		this.tops = tops;
		this.h = h;
		this.n = 2 * h + 1;
	}

	// 1 to 4 for a column whose surface is h blocks above the base
	public int layerOfHeight(int height) {
		return layerOf(-Math.max(0, height));
	}

	// The column's layer; a position off the plan (never an expected one) by its own height above the base
	@Override
	public int layerAt(int x, int y, int z) {
		int ix = x + h, iz = z + h;
		int top = ix < 0 || ix >= n || iz < 0 || iz >= n ? -1 : tops[ix * n + iz];
		return layerOfHeight(top < 0 ? y : top);
	}

	// Same cuts and Height, and the same generation's columns
	@Override
	public boolean equals(Object o) {
		return super.equals(o) && ((TerrainLayers) o).tops == tops;
	}

	@Override
	public int hashCode() {
		return super.hashCode() * 31 + System.identityHashCode(tops);
	}
}
