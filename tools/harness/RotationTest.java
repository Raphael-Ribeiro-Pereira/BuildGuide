import brentmaas.buildguide.common.shape.Shape;

// Rotate 90 degrees (2026-10-28): Shape.turnX / turnZ, the quarter turns addShapeCube applies to every block
public class RotationTest {
	static int fails = 0;

	static void check(boolean ok, String label) {
		System.out.println((ok ? "OK   " : "FAIL ") + label);
		if(!ok) ++fails;
	}

	public static void main(String[] a) {
		int x = 5, z = 2;
		check(Shape.turnX(x, z, 0) == 5 && Shape.turnZ(x, z, 0) == 2, "0 turns: unchanged");
		check(Shape.turnX(x, z, 1) == -2 && Shape.turnZ(x, z, 1) == 5, "1 turn: (5, 2) -> (-2, 5), a quarter turn about the origin");
		check(Shape.turnX(x, z, 2) == -5 && Shape.turnZ(x, z, 2) == -2, "2 turns: (5, 2) -> (-5, -2)");
		boolean back = true, four = true;
		for(int t = 0;t < 4;++t) {
			for(int px = -3;px <= 3;++px) for(int pz = -3;pz <= 3;++pz) {
				int tx = Shape.turnX(px, pz, t), tz = Shape.turnZ(px, pz, t), b = (4 - t) & 3;
				back &= Shape.turnX(tx, tz, b) == px && Shape.turnZ(tx, tz, b) == pz;
				int fx = px, fz = pz;
				for(int i = 0;i < 4;++i) {
					int nx = Shape.turnX(fx, fz, 1);
					fz = Shape.turnZ(fx, fz, 1);
					fx = nx;
				}
				four &= fx == px && fz == pz;
			}
		}
		check(back, "turning back by 4 - t undoes t turns (the lookups by a stored position)");
		check(four, "four quarter turns are the identity");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		if(fails > 0) System.exit(1);
	}
}
