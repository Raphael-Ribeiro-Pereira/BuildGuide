package brentmaas.buildguide.common.shape;

/**
 * What the 3D preview shows (GUI redesign E6). Structure errors are not a status: they are the
 * model's separate `errors` list (solid blocks outside the shape), so ERRORS is the only filter
 * besides ALL that shows them, and it shows no shape position. IGNORED positions only show in ALL.
 */
public enum PreviewFilter {
	ALL("screen.buildguide.filter.all"),
	ERRORS("screen.buildguide.filter.errors"),
	MISSING("screen.buildguide.filter.missing"),
	BUILT("screen.buildguide.filter.built"),
	UNVALIDATED("screen.buildguide.filter.unvalidated"),
	// Palette per layer (4D): the blocks of another layer; offered only while the shape has a palette
	WRONG_LAYER("screen.buildguide.filter.wronglayer");

	public final String translationKey;

	private PreviewFilter(String translationKey) {
		this.translationKey = translationKey;
	}

	// Whether a shape position with this validation status is drawn
	public boolean showsStatus(byte status) {
		switch(this) {
		case ALL: return true;
		case MISSING: return status == ValidationState.MISSING;
		case BUILT: return status == ValidationState.OK;
		case UNVALIDATED: return status == ValidationState.UNKNOWN;
		case ERRORS: return status == ValidationState.WRONG_BLOCK; // a wrong block on the shape is an error too (4D)
		case WRONG_LAYER: return status == ValidationState.WRONG_LAYER;
		default: return false;
		}
	}

	public boolean showsErrors() {
		return this == ALL || this == ERRORS;
	}

	public PreviewFilter next() {
		return values()[(ordinal() + 1) % values().length];
	}
	
	// The next filter of the button; Wrong layer only with a palette (without one it would always show nothing)
	public PreviewFilter next(boolean withWrongLayer) {
		PreviewFilter f = next();
		return f == WRONG_LAYER && !withWrongLayer ? f.next() : f;
	}
}
