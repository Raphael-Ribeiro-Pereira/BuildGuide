package brentmaas.buildguide.common.shape;

/**
 * Catmull-Rom spline through N control points (N >= 2). The first and last control points
 * are duplicated as phantom points so the curve passes through them. Segment s (0-based,
 * N-1 of them) runs from point s to point s+1; parameter t in [0, 1].
 *
 * Extracted from ShapeSpline. The arc-length table is for callers that need evenly
 * spaced samples (e.g. bridge pillars); ShapeSpline itself samples by t.
 */
public class CatmullRomCurve {
	private final int[][] points;
	
	// Arc-length table, built on first use
	private static final int lengthSamplesPerSegment = 32;
	private double[] cumulativeLength = null;
	
	public CatmullRomCurve(int[][] controlPoints) {
		if(controlPoints.length < 2) throw new IllegalArgumentException("A curve needs at least two control points");
		points = controlPoints;
	}
	
	public int getSegmentCount() {
		return points.length - 1;
	}
	
	public static double catmullRom(int p0, int p1, int p2, int p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
	
	private static double catmullRomDerivative(int p0, int p1, int p2, int p3, double t) {
		double t2 = t * t;
		return 0.5 * ((-p0 + p2) + 2 * (2 * p0 - 5 * p1 + 4 * p2 - p3) * t + 3 * (-p0 + 3 * p1 - 3 * p2 + p3) * t2);
	}
	
	// The four control points of segment s, with the ends clamped (phantom points)
	private int[][] segmentPoints(int s) {
		int last = points.length - 1;
		return new int[][] {points[Math.max(s - 1, 0)], points[s], points[s + 1], points[Math.min(s + 2, last)]};
	}
	
	// Position {x, y, z} on segment s at parameter t
	public double[] sample(int s, double t) {
		int[][] p = segmentPoints(s);
		return new double[] {
			catmullRom(p[0][0], p[1][0], p[2][0], p[3][0], t),
			catmullRom(p[0][1], p[1][1], p[2][1], p[3][1], t),
			catmullRom(p[0][2], p[1][2], p[2][2], p[3][2], t)
		};
	}
	
	// Unnormalised tangent {x, y, z} on segment s at parameter t
	public double[] tangent(int s, double t) {
		int[][] p = segmentPoints(s);
		return new double[] {
			catmullRomDerivative(p[0][0], p[1][0], p[2][0], p[3][0], t),
			catmullRomDerivative(p[0][1], p[1][1], p[2][1], p[3][1], t),
			catmullRomDerivative(p[0][2], p[1][2], p[2][2], p[3][2], t)
		};
	}
	
	private void buildLengthTable() {
		int n = getSegmentCount() * lengthSamplesPerSegment;
		cumulativeLength = new double[n + 1];
		cumulativeLength[0] = 0;
		double[] prev = sample(0, 0);
		for(int i = 1;i <= n;++i) {
			// Global parameter g in [0, segmentCount]; the last sample lands on (lastSegment, t = 1)
			double g = (double) i / lengthSamplesPerSegment;
			int seg = Math.min((int) Math.floor(g), getSegmentCount() - 1);
			double[] cur = sample(seg, g - seg);
			double dx = cur[0] - prev[0], dy = cur[1] - prev[1], dz = cur[2] - prev[2];
			cumulativeLength[i] = cumulativeLength[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
			prev = cur;
		}
	}
	
	// Approximate total length of the curve
	public double getLength() {
		if(cumulativeLength == null) buildLengthTable();
		return cumulativeLength[cumulativeLength.length - 1];
	}
	
	/**
	 * Maps an arc length s in [0, getLength()] to {segment, t}. Callers use it to place
	 * things at even spacing along the curve: sample(seg, t) with s = k * length / (n - 1).
	 */
	public double[] parameterAtLength(double s) {
		if(cumulativeLength == null) buildLengthTable();
		int n = cumulativeLength.length - 1;
		if(s <= 0) return new double[] {0, 0};
		if(s >= cumulativeLength[n]) return new double[] {getSegmentCount() - 1, 1.0};
		// Binary search for the table interval containing s
		int lo = 0, hi = n;
		while(hi - lo > 1) {
			int mid = (lo + hi) / 2;
			if(cumulativeLength[mid] <= s) lo = mid;
			else hi = mid;
		}
		double span = cumulativeLength[hi] - cumulativeLength[lo];
		double frac = span > 0 ? (s - cumulativeLength[lo]) / span : 0;
		double globalT = (lo + frac) / lengthSamplesPerSegment; // in [0, segmentCount]
		int seg = Math.min((int) Math.floor(globalT), getSegmentCount() - 1);
		return new double[] {seg, globalT - seg};
	}
	
	// Convenience: position at arc length s
	public double[] sampleAtLength(double s) {
		double[] p = parameterAtLength(s);
		return sample((int) p[0], p[1]);
	}
}
