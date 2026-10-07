import java.security.MessageDigest;
import java.util.*;
import brentmaas.buildguide.common.shape.IslandGeometry;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.LocalPos;
// Island GOLDEN (before the spikes, 2026-10-07): SHA-256 of the sorted block set of 15 islands,
// recorded from the code of 0c14d4a. Spikes must leave every one of them unchanged while their count
// is 0 (the default). "java IslandGoldenTest print" prints the current hashes instead of checking.
public class IslandGoldenTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params p(Outline o, Profile pr, int wall, long seed, int wx, int wz, int depth, double rot, double round, double amp, double rough, double sharp, int sides){
		Params p = new Params(); p.outline = o; p.profile = pr; p.wall = wall; p.seed = seed; p.widthX = wx; p.widthZ = wz; p.depth = depth;
		p.rotationDeg = rot; p.roundness = round; p.edgeAmplitude = amp; p.roughness = rough; p.sharpness = sharp; p.sides = sides; return p;
	}
	static final Params[] CASES = {
		p(Outline.CIRCLE, Profile.BOWL, 1, 1, 41, 41, 24, 0, 0.7, 0, 0, 0, 6),
		p(Outline.CIRCLE, Profile.CONE, 2, 7, 33, 21, 18, 30, 0.7, 0, 0.3, 0.4, 6),
		p(Outline.CIRCLE, Profile.TERRACED, 3, 99, 45, 45, 30, 0, 0.7, 0, 0.6, 0.8, 6),
		p(Outline.SQUARE, Profile.BOWL, 2, 12345, 27, 39, 20, 15, 0.0, 0, 0.2, 0.3, 6),
		p(Outline.SQUARE, Profile.CONE, 3, 4242, 41, 41, 24, 45, 0.5, 0, 0.5, 1.0, 6),
		p(Outline.SQUARE, Profile.TERRACED, 1, 2, 31, 31, 16, 0, 1.0, 0, 1.0, 0.2, 6),
		p(Outline.POLYGON, Profile.BOWL, 3, 3, 41, 41, 24, 0, 0.0, 0, 0.3, 0.4, 3),
		p(Outline.POLYGON, Profile.CONE, 1, 54321, 37, 29, 22, 10, 0.3, 0, 0, 0.6, 7),
		p(Outline.POLYGON, Profile.TERRACED, 2, 77, 51, 51, 40, 0, 0.0, 0, 0.4, 0.5, 12),
		p(Outline.ORGANIC, Profile.BOWL, 2, 1, 41, 41, 24, 0, 0.7, 0.25, 0.3, 0.4, 6),
		p(Outline.ORGANIC, Profile.CONE, 1, 999, 61, 45, 35, 60, 0.75, 0.6, 0.35, 0.45, 6),
		p(Outline.ORGANIC, Profile.TERRACED, 3, 31337, 45, 45, 30, 0, 0.5, 1.0, 1.0, 1.0, 6),
		p(Outline.ORGANIC, Profile.BOWL, 3, 8, 121, 121, 80, 0, 0.7, 0.25, 0.3, 0.4, 6),
		p(Outline.SQUARE, Profile.BOWL, 1, 5, 3, 3, 0, 0, 0.0, 0, 0, 0, 6),
		p(Outline.POLYGON, Profile.CONE, 2, 6, 15, 9, 80, 90, 0.2, 0, 0.9, 0.9, 5),
	};
	static final String[] GOLDEN = {
		"3918:73dbf55496514ded", // CIRCLE/BOWL wall 1 seed 1 41x41 depth 24
		"1913:bbeacce29f5d235e", // CIRCLE/CONE wall 2 seed 7 33x21 depth 18
		"11500:3814c7b5a018796e", // CIRCLE/TERRACED wall 3 seed 99 45x45 depth 30
		"5224:f4f923795b18716f", // SQUARE/BOWL wall 2 seed 12345 27x39 depth 20
		"4857:fafe04ab3a2e7814", // SQUARE/CONE wall 3 seed 4242 41x41 depth 24
		"2270:e11c27d31724e493", // SQUARE/TERRACED wall 1 seed 2 31x31 depth 16
		"4788:fe13a875c2d703e0", // POLYGON/BOWL wall 3 seed 3 41x41 depth 24
		"1510:63b3dff60ac3dae5", // POLYGON/CONE wall 1 seed 54321 37x29 depth 22
		"11019:aee43f52a9baae22", // POLYGON/TERRACED wall 2 seed 77 51x51 depth 40
		"6868:2a9e8eff3fe646c3", // ORGANIC/BOWL wall 2 seed 1 41x41 depth 24
		"5057:e5c0004a3f35f305", // ORGANIC/CONE wall 1 seed 999 61x45 depth 35
		"13820:cfd6272e08753f67", // ORGANIC/TERRACED wall 3 seed 31337 45x45 depth 30
		"105422:cc6c5bae6c683b14", // ORGANIC/BOWL wall 3 seed 8 121x121 depth 80
		"9:c3dd1e4f4eaa8a58", // SQUARE/BOWL wall 1 seed 5 3x3 depth 0
		"971:dcc963cf03033a4e", // POLYGON/CONE wall 2 seed 6 15x9 depth 80
	};

	static String hash(Params p) throws Exception {
		List<Long> l = new ArrayList<>(); IslandGeometry.enumerate(p, (x, y, z) -> l.add(LocalPos.pack(x, y, z)));
		Collections.sort(l); MessageDigest md = MessageDigest.getInstance("SHA-256");
		for(long v: l) for(int i = 0; i < 8; i++) md.update((byte) (v >>> (8 * i)));
		StringBuilder sb = new StringBuilder(); for(byte b: md.digest()) sb.append(String.format("%02x", b)); return l.size() + ":" + sb.substring(0, 16);
	}
	static String name(Params p){ return p.outline + "/" + p.profile + " wall " + p.wall + " seed " + p.seed + " " + p.widthX + "x" + p.widthZ + " depth " + p.depth; }

	public static void main(String[] a) throws Exception {
		boolean print = a.length > 0 && a[0].equals("print");
		for(int i = 0; i < CASES.length; i++){
			String h = hash(CASES[i]);
			if(print) System.out.println("\t\t\"" + h + "\", // " + name(CASES[i]));
			else check(h.equals(GOLDEN[i]), name(CASES[i]) + ": " + h);
		}
		if(!print) System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
