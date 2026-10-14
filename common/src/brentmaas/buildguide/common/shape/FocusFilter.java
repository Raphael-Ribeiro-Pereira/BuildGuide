package brentmaas.buildguide.common.shape;

/**
 * Build focus: the world shows only the part of a shape one is working on, the preview still shows all
 * of it. Two selectors that intersect: a part (All, Cap, Body, Spikes, or Hidden: the world draws
 * nothing of the shape; shapes without parts have only All and Hidden) and a layer (All or 1 to 4, only
 * with the shape's Layers on). The focus only applies to a shape of at least `threshold` blocks (0 =
 * always); below it the world shows everything. Only the world's drawing follows it (the shape's buffer,
 * its error overlay, the Area 3 target): the expected set, the scan, the totals and Validate never do.
 *
 * Immutable, only primitives: no net.minecraft.
 */
public final class FocusFilter {
	public enum Part {
		ALL("all"),
		CAP("cap"),
		BODY("body"),
		SPIKES("spikes"),
		HIDDEN("hidden");

		public final String translationKey;

		Part(String name) {
			translationKey = "screen.buildguide.focus.part." + name;
		}
	}

	// Layer selector: 0 = all, 1 to ShapeLayers.COUNT one layer
	public static final int LAYER_ALL = 0;
	// The part of every block of a shape without parts (IFocusParts default): only All and Hidden select it
	public static final Part WHOLE = Part.ALL;
	// Configuration "Focus from": the default threshold in blocks. Low on purpose: with Part and Layer on All the
	// focus does nothing, so it only decides from what size a chosen focus applies
	public static final int defaultThreshold = 400;

	public final Part part;
	public final int layer;

	public FocusFilter(Part part, int layer) {
		this.part = part == null ? Part.ALL : part;
		this.layer = layer < 0 || layer > ShapeLayers.COUNT ? LAYER_ALL : layer;
	}

	// Whether a focus with these selectors applies to a shape of `blocks` blocks: something is selected and the
	// shape is large enough (threshold 0 or less = always)
	public static boolean isActive(Part part, int layer, int threshold, int blocks) {
		if((part == null || part == Part.ALL) && layer == LAYER_ALL) return false;
		return threshold <= 0 || blocks >= threshold;
	}

	// A block of this part (WHOLE for shapes without parts) and layer (LAYER_ALL without layers) is drawn
	public boolean accepts(Part blockPart, int blockLayer) {
		if(part == Part.HIDDEN) return false;
		if(part != Part.ALL && blockPart != part) return false;
		return layer == LAYER_ALL || blockLayer == layer;
	}

	// The part button's next value: All, Cap, Body, Spikes, Hidden with parts; All, Hidden without
	public static Part nextPart(Part part, boolean hasParts) {
		if(!hasParts) return part == Part.ALL ? Part.HIDDEN : Part.ALL;
		Part[] all = Part.values();
		return all[((part == null ? 0 : part.ordinal()) + 1) % all.length];
	}

	// The layer button's next value: All, 1, 2, 3, 4, All
	public static int nextLayer(int layer) {
		return layer < 0 || layer >= ShapeLayers.COUNT ? LAYER_ALL : layer + 1;
	}
}
