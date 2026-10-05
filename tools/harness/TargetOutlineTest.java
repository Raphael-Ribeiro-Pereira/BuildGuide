import brentmaas.buildguide.common.shape.IShapeBuffer;
import brentmaas.buildguide.common.shape.TargetOutline;
import brentmaas.buildguide.common.shape.ValidationOverlay;
import java.util.*;
// Area 3: the outline of the targeted cell. White faces (the error list's highlight colour) and 12
// thin opaque black edge boxes, one buffer, in the cell's own frame (the cell spans 0..1).
public class TargetOutlineTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final double EPS = 1e-9;

	static class Rec implements IShapeBuffer {
		final List<double[]> v = new ArrayList<>(); final List<int[]> c = new ArrayList<>(); int[] cur = {0, 0, 0, 0};
		public void setColour(int r, int g, int b, int a){ cur = new int[]{r, g, b, a}; }
		public void pushVertex(double x, double y, double z){ v.add(new double[]{x, y, z}); c.add(cur); }
		public void end(){} public void close(){}
	}
	// {minX, minY, minZ, maxX, maxY, maxZ} of the vertices [from, to)
	static double[] bounds(Rec r, int from, int to){
		double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
		for(int i = from; i < to; i++){ double[] p = r.v.get(i); for(int k = 0; k < 3; k++){ b[k] = Math.min(b[k], p[k]); b[k + 3] = Math.max(b[k + 3], p[k]); } }
		return b;
	}
	static boolean near(double a, double b){ return Math.abs(a - b) < EPS; }

	public static void main(String[] a){
		System.out.println("-- size: shape cube size + " + TargetOutline.TARGET_GROW + ", at most a full block");
		check(near(TargetOutline.side(0.6), 0.75), "0.6 -> 0.75");
		check(near(TargetOutline.side(1.0), 1.0), "1.0 -> 1.0 (limit)");
		check(near(TargetOutline.side(0.95), 1.0), "0.95 -> 1.0 (limit)");

		for(double size: new double[]{0.6, 0.95, 1.0, 0.2}){
			System.out.println("-- shape cube size " + size);
			Rec r = new Rec(); TargetOutline.build(r, size);
			double s = TargetOutline.side(size), t = TargetOutline.EDGE_THICKNESS, lo = 0.5 - s / 2, hi = 0.5 + s / 2;
			check(r.v.size() == 24 * (1 + 12) && TargetOutline.VERTICES == 312, "vertices = 24 x (1 + 12) = " + r.v.size());
			boolean faceColour = true; for(int i = 0; i < 24; i++){ int[] c = r.c.get(i); faceColour &= c[0] == ValidationOverlay.highlightR && c[1] == ValidationOverlay.highlightG && c[2] == ValidationOverlay.highlightB && c[3] == ValidationOverlay.highlightA; }
			check(faceColour, "faces: the highlight colour (" + ValidationOverlay.highlightR + ", " + ValidationOverlay.highlightG + ", " + ValidationOverlay.highlightB + ", " + ValidationOverlay.highlightA + ")");
			boolean black = true; for(int i = 24; i < r.c.size(); i++){ int[] c = r.c.get(i); black &= c[0] == 0 && c[1] == 0 && c[2] == 0 && c[3] == 255; }
			check(black, "the 12 edges: opaque black");
			double[] f = bounds(r, 0, 24);
			check(near(f[3] - f[0], s) && near(f[4] - f[1], s) && near(f[5] - f[2], s), "face cube side " + s);
			check(near((f[0] + f[3]) / 2, 0.5) && near((f[1] + f[4]) / 2, 0.5) && near((f[2] + f[5]) / 2, 0.5), "centred on (0.5, 0.5, 0.5) of the cell: (x + 0.5, y + 0.5, z + 0.5) once translated to the cell");
			boolean inside = true, shapes = true; Set<String> seen = new HashSet<>();
			for(int e = 1; e <= 12; e++){
				double[] b = bounds(r, 24 * e, 24 * (e + 1));
				for(int k = 0; k < 3; k++) inside &= b[k] >= lo - t - EPS && b[k + 3] <= hi + t + EPS;
				double[] d = {b[3] - b[0], b[4] - b[1], b[5] - b[2]};
				int longAxis = -1, thin = 0;
				for(int k = 0; k < 3; k++){ if(near(d[k], s + t)) longAxis = k; else if(near(d[k], t)) thin++; }
				shapes &= longAxis >= 0 && thin == 2;
				seen.add(String.format(Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f %.4f", b[0], b[1], b[2], b[3], b[4], b[5]));
			}
			check(inside, "all 12 edges inside the cube's box grown by the thickness " + t);
			check(shapes, "each edge: length side + thickness (" + (s + t) + "), section thickness x thickness");
			check(seen.size() == 12, "12 distinct edges");
		}
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
