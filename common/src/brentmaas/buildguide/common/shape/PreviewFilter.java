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
	UNVALIDATED("screen.buildguide.filter.unvalidated");

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
		default: return false;
		}
	}

	public boolean showsErrors() {
		return this == ALL || this == ERRORS;
	}

	public PreviewFilter next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
