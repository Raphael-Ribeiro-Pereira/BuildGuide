import java.security.MessageDigest;
import java.util.*;
import brentmaas.buildguide.common.shape.IslandGeometry;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.LocalPos;
// Island GOLDEN-SPIKES (before spikes 2, 2026-10-07): SHA-256 of the sorted block set of 16 islands
// with spikes, recorded from the code of 1fab13a (block B). Spikes 2 must leave every one of them
// unchanged at its defaults (Jitter, Falloff 0, Taper 1, no Dripstone, Attached). "print" prints them.
public class IslandGoldenSpikesTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params p(Outline o, Profile pr, int wall, long seed, int wx, int wz, int depth, double rot, double round, double amp, double rough, double sharp, int sides){
		Params p = new Params(); p.outline = o; p.profile = pr; p.wall = wall; p.seed = seed; p.widthX = wx; p.widthZ = wz; p.depth = depth;
		p.rotationDeg = rot; p.roundness = round; p.edgeAmplitude = amp; p.roughness = rough; p.sharpness = sharp; p.sides = sides; return p;
	}
	static Params s(Params p, int count, IslandGeometry.SpikeMode mode, int length, int base, int var, int spread){ p.spikes = count; p.spikeMode = mode; p.spikeLength = length; p.spikeBase = base; p.lengthVar = var; p.spread = spread; return p; }
	static final IslandGeometry.SpikeMode R = IslandGeometry.SpikeMode.RANDOM, G = IslandGeometry.SpikeMode.RING;
	static final Params[] CASES = {
		s(p(Outline.CIRCLE, Profile.BOWL, 1, 1, 41, 41, 24, 0, 0.7, 0, 0, 0, 6), 1, R, 12, 3, 0, 50),
		s(p(Outline.CIRCLE, Profile.CONE, 2, 7, 33, 21, 18, 30, 0.7, 0, 0.3, 0.4, 6), 5, G, 20, 2, 40, 60),
		s(p(Outline.CIRCLE, Profile.TERRACED, 3, 99, 45, 45, 30, 0, 0.7, 0, 0.6, 0.8, 6), 12, R, 40, 8, 100, 100),
		s(p(Outline.SQUARE, Profile.BOWL, 2, 12345, 27, 39, 20, 15, 0.0, 0, 0.2, 0.3, 6), 3, G, 8, 4, 0, 90),
		s(p(Outline.SQUARE, Profile.CONE, 3, 4242, 41, 41, 24, 45, 0.5, 0, 0.5, 1.0, 6), 7, R, 15, 5, 25, 70),
		s(p(Outline.SQUARE, Profile.TERRACED, 1, 2, 31, 31, 16, 0, 1.0, 0, 1.0, 0.2, 6), 1, G, 30, 6, 50, 0),
		s(p(Outline.POLYGON, Profile.BOWL, 3, 3, 41, 41, 24, 0, 0.0, 0, 0.3, 0.4, 3), 9, R, 10, 3, 10, 100),
		s(p(Outline.POLYGON, Profile.CONE, 1, 54321, 37, 29, 22, 10, 0.3, 0, 0, 0.6, 7), 4, G, 25, 1, 60, 45),
		s(p(Outline.POLYGON, Profile.TERRACED, 2, 77, 51, 51, 40, 0, 0.0, 0, 0.4, 0.5, 12), 12, G, 40, 8, 0, 75),
		s(p(Outline.ORGANIC, Profile.BOWL, 2, 1, 41, 41, 24, 0, 0.7, 0.25, 0.3, 0.4, 6), 6, R, 12, 3, 30, 50),
		s(p(Outline.ORGANIC, Profile.CONE, 1, 999, 61, 45, 35, 60, 0.75, 0.6, 0.35, 0.45, 6), 2, R, 35, 7, 80, 85),
		s(p(Outline.ORGANIC, Profile.TERRACED, 3, 31337, 45, 45, 30, 0, 0.5, 1.0, 1.0, 1.0, 6), 11, G, 5, 2, 100, 20),
		s(p(Outline.ORGANIC, Profile.BOWL, 3, 8, 121, 121, 80, 0, 0.7, 0.25, 0.3, 0.4, 6), 12, G, 40, 8, 0, 70),
		s(p(Outline.SQUARE, Profile.BOWL, 1, 5, 3, 3, 0, 0, 0.0, 0, 0, 0, 6), 1, R, 1, 1, 0, 0),
		s(p(Outline.POLYGON, Profile.CONE, 2, 6, 15, 9, 80, 90, 0.2, 0, 0.9, 0.9, 5), 8, R, 40, 8, 100, 100),
		s(p(Outline.CIRCLE, Profile.BOWL, 2, 42, 81, 61, 50, 33, 0.7, 0, 0.3, 0.4, 6), 10, R, 22, 4, 45, 65),
	};
	static final String[] GOLDEN = {
		"3980:ca6587dab5595e49", // CIRCLE/BOWL wall 1 seed 1 41x41 depth 24, 1 RANDOM
		"2570:b61d6a4f3d001729", // CIRCLE/CONE wall 2 seed 7 33x21 depth 18, 5 RING
		"19204:05e495fcb31076ac", // CIRCLE/TERRACED wall 3 seed 99 45x45 depth 30, 12 RANDOM
		"5427:d3ff0014ef0385d4", // SQUARE/BOWL wall 2 seed 12345 27x39 depth 20, 3 RING
		"6276:14abed1b83083927", // SQUARE/CONE wall 3 seed 4242 41x41 depth 24, 7 RANDOM
		"2479:2f86e3ad4fa4db57", // SQUARE/TERRACED wall 1 seed 2 31x31 depth 16, 1 RING
		"5201:1359f6c861f982cf", // POLYGON/BOWL wall 3 seed 3 41x41 depth 24, 9 RANDOM
		"1701:49e63076873494e4", // POLYGON/CONE wall 1 seed 54321 37x29 depth 22, 4 RING
		"19435:62d4dfd6b43302ae", // POLYGON/TERRACED wall 2 seed 77 51x51 depth 40, 12 RING
		"7361:f14b54e4dd78ccbf", // ORGANIC/BOWL wall 2 seed 1 41x41 depth 24, 6 RANDOM
		"5998:a99f4676783757bd", // ORGANIC/CONE wall 1 seed 999 61x45 depth 35, 2 RANDOM
		"14146:68c74dde44af4464", // ORGANIC/TERRACED wall 3 seed 31337 45x45 depth 30, 11 RING
		"124169:883a184f6aeb9094", // ORGANIC/BOWL wall 3 seed 8 121x121 depth 80, 12 RING
		"10:7f33fd4d1848392d", // SQUARE/BOWL wall 1 seed 5 3x3 depth 0, 1 RANDOM
		"2197:f11a30e9a1432526", // POLYGON/CONE wall 2 seed 6 15x9 depth 80, 8 RANDOM
		"24171:21ff2ac47a290e10", // CIRCLE/BOWL wall 2 seed 42 81x61 depth 50, 10 RANDOM
	};
	static String hash(Params p) throws Exception {
		List<Long> l = new ArrayList<>(); IslandGeometry.enumerate(p, (x, y, z) -> l.add(LocalPos.pack(x, y, z)));
		Collections.sort(l); MessageDigest md = MessageDigest.getInstance("SHA-256");
		for(long v: l) for(int i = 0; i < 8; i++) md.update((byte) (v >>> (8 * i)));
		StringBuilder sb = new StringBuilder(); for(byte b: md.digest()) sb.append(String.format("%02x", b)); return l.size() + ":" + sb.substring(0, 16);
	}
	static String name(Params p){ return p.outline + "/" + p.profile + " wall " + p.wall + " seed " + p.seed + " " + p.widthX + "x" + p.widthZ + " depth " + p.depth + ", " + p.spikes + " " + p.spikeMode; }

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
