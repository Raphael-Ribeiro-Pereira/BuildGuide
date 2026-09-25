package brentmaas.buildguide.common.shape;

/**
 * Base colours (0xRRGGBB) of the 3D preview, before FaceShadedBuffer darkens each face. MISSING is
 * slightly blue on purpose: plain grey (180) would equal a shaded side of a white cube (225 x 0.8).
 */
public class PreviewColours {
	public static final int WHITE = 0xE1E1E1; // not validated, or UNKNOWN (excluded positions)
	public static final int OK = 0x3CC850;
	public static final int MISSING = 0xAAB4C8; // 170, 180, 200
	public static final int IGNORED = 0xE6C832;
	public static final int ERROR = 0xFF3C3C; // structure errors (NearBlock)

	public static int forStatus(byte status) {
		switch(status) {
		case ValidationState.OK:
			return OK;
		case ValidationState.MISSING:
			return MISSING;
		case ValidationState.IGNORED:
			return IGNORED;
		default:
			return WHITE;
		}
	}
}
