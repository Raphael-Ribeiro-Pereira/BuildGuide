package brentmaas.buildguide.common.shape;

/**
 * Palette per layer (stage 4D): one chosen block per layer of ShapeLayers, as the loader's stable integer
 * id (the raw registry id, resolved once from the typed ids), or NONE when the layer accepts any solid
 * block. classify is the one rule every validation path uses (the scan, SliceScan, the block events, the
 * safety net), so they can never disagree:
 *
 *  - air: MISSING;
 *  - the layer has a block and this is it: OK (a block chosen for two layers is right on both);
 *  - the layer has a block and this is the block of another layer: WRONG_LAYER;
 *  - an ignored type: IGNORED; any other non-solid block: MISSING (as without a palette);
 *  - the layer has a block and this solid block is in no layer: WRONG_BLOCK;
 *  - otherwise (no block chosen for the layer): OK.
 *
 * Without a palette (null) the rule is the one of before. Only the block's id is compared, never its state.
 * Only primitives: no net.minecraft.
 */
public final class LayerPalette {
	public static final int NONE = -1;

	public final ShapeLayers layers;
	private final int[] ids;

	// ids: one per layer (1 to 4, index 0 to 3), NONE = any solid block
	public LayerPalette(ShapeLayers layers, int[] ids) {
		this.layers = layers;
		this.ids = new int[ShapeLayers.COUNT];
		for(int i = 0;i < ShapeLayers.COUNT;++i) this.ids[i] = ids != null && i < ids.length ? ids[i] : NONE;
	}

	// The chosen block of a layer (1 to 4), NONE for any
	public int idOf(int layer) {
		return ids[Math.max(1, Math.min(ShapeLayers.COUNT, layer)) - 1];
	}

	public boolean isEmpty() {
		for(int id: ids) if(id != NONE) return false;
		return true;
	}

	// The block is chosen for some layer
	public boolean contains(int blockId) {
		if(blockId == NONE) return false;
		for(int id: ids) if(id == blockId) return true;
		return false;
	}

	/**
	 * Status of an expected position at local y, from the probe's flags (IBlockProbe) and the block's id.
	 * palette null = no palette (the rule of before); blockId is only read when the layer has a block.
	 */
	public static byte classify(LayerPalette palette, int y, int flags, int blockId) {
		return byWanted(palette, palette == null ? NONE : palette.idOf(palette.layers.layerOf(y)), flags, blockId);
	}

	// The same at a local position: the layer is the layers' layerAt (by y for the Island, by column for the
	// Terrain). Every validation path calls this one
	public static byte classify(LayerPalette palette, int x, int y, int z, int flags, int blockId) {
		return byWanted(palette, palette == null ? NONE : palette.idOf(palette.layers.layerAt(x, y, z)), flags, blockId);
	}

	// wanted: the chosen block of the position's layer, NONE for any
	private static byte byWanted(LayerPalette palette, int wanted, int flags, int blockId) {
		boolean air = (flags & IBlockProbe.FLAG_AIR) != 0;
		if(air) return ValidationState.MISSING;
		if(wanted != NONE) {
			if(blockId == wanted) return ValidationState.OK;
			if(palette.contains(blockId)) return ValidationState.WRONG_LAYER;
		}
		if((flags & IBlockProbe.FLAG_IGNORED) != 0) return ValidationState.IGNORED;
		if((flags & IBlockProbe.FLAG_SOLID) == 0) return ValidationState.MISSING;
		return wanted != NONE ? ValidationState.WRONG_BLOCK : ValidationState.OK;
	}

	// Whether classify needs the block's id at this y (the layer has a chosen block): the loader reads it only then
	public boolean needsId(int y) {
		return idOf(layers.layerOf(y)) != NONE;
	}

	public boolean needsId(int x, int y, int z) {
		return idOf(layers.layerAt(x, y, z)) != NONE;
	}
}
