package brentmaas.buildguide.common.shape;

/**
 * Cross-section profiles for composed shapes, emitted in "section space": u (lateral) in
 * [0, width), v (vertical) in [0, height), w = 0 — the same frame as
 * ShapeCuboid.enumerate(width, height, 1, ...), so one consumer can place any profile.
 * Filled ellipse is the only geometry here that no existing shape provides (ShapeCircle and
 * ShapeEllipse are hollow rings).
 */
public class Profiles {
	// Filled ellipse inscribed in a width x height rectangle, tested at block centres
	public static void filledEllipse(int width, int height, IBlockConsumer out) throws InterruptedException {
		double a = width / 2.0, b = height / 2.0;
		double uc0 = (width - 1) / 2.0, vc0 = (height - 1) / 2.0;
		for(int u = 0;u < width;++u) {
			for(int v = 0;v < height;++v) {
				double uc = (u - uc0) / a, vc = (v - vc0) / b;
				if(uc * uc + vc * vc <= 1.0) out.accept(u, v, 0);
			}
		}
	}	
	// Hollow ellipse: the filled ellipse minus the filled ellipse inscribed one block in. For
	// width or height <= 2 the inner one is empty and the result equals the filled ellipse
	public static void hollowEllipse(int width, int height, IBlockConsumer out) throws InterruptedException {
		double a = width / 2.0, b = height / 2.0;
		double ai = a - 1.0, bi = b - 1.0;
		boolean hasInner = ai > 0.0 && bi > 0.0;
		double uc0 = (width - 1) / 2.0, vc0 = (height - 1) / 2.0;
		for(int u = 0;u < width;++u) {
			for(int v = 0;v < height;++v) {
				double du = u - uc0, dv = v - vc0;
				if((du / a) * (du / a) + (dv / b) * (dv / b) > 1.0) continue;
				if(hasInner && (du / ai) * (du / ai) + (dv / bi) * (dv / bi) <= 1.0) continue;
				out.accept(u, v, 0);
			}
		}
	}
}
