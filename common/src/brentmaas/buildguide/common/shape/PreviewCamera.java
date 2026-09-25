package brentmaas.buildguide.common.shape;

/**
 * Orbit camera of the 3D preview. Angles in degrees. yaw 0 looks from the north (-Z) towards
 * the south; yaw 45 puts the viewer at the north-east, so the north face shows on the left and
 * the east face on the right. pitch > 0 looks down on the model (its top faces are visible).
 * zoom 1 fits the whole model in the preview area.
 */
public class PreviewCamera {
	public static final float defaultYaw = 45, defaultPitch = 30, defaultZoom = 1;
	// Share of the area's half size the model's bounding sphere takes at zoom 1
	private static final double fitMargin = 0.9;

	// Mouse: degrees per GUI pixel dragged, zoom factor per scroll notch
	public static final float degreesPerPixel = 0.5f, zoomStep = 1.1f;
	// Pitch stays short of +-90 (there yaw and pitch turn about the same axis and the view flips);
	// zoom bounds keep the model visible (min) and useful (max; the texture is clipped to the area anyway)
	public static final float maxPitch = 89, minZoom = 0.25f, maxZoom = 8;

	public float yaw = defaultYaw;
	public float pitch = defaultPitch;
	public float zoom = defaultZoom;

	// Drag by (dx, dy) GUI pixels: right turns the model right, down shows more of its top
	public void rotate(double dx, double dy) {
		yaw = (float) (((yaw + dx * degreesPerPixel) % 360 + 360) % 360);
		pitch = (float) Math.max(-maxPitch, Math.min(maxPitch, pitch + dy * degreesPerPixel));
	}

	// Scroll by `notches` (positive = closer), multiplicative so every notch feels the same
	public void zoomBy(double notches) {
		zoom = (float) Math.max(minZoom, Math.min(maxZoom, zoom * Math.pow(zoomStep, notches)));
	}

	public void reset() {
		yaw = defaultYaw;
		pitch = defaultPitch;
		zoom = defaultZoom;
	}

	// GUI pixels per block for a model shown in a width x height area (GUI pixels)
	public static double fitScale(PreviewModel model, int width, int height, float zoom) {
		if(model.isEmpty() || width <= 0 || height <= 0) return 1;
		return fitMargin * Math.min(width, height) / 2 / model.radius() * zoom;
	}
}
