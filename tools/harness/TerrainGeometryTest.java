import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.TerrainGeometry.*;
// Terrain geometry (pure): determinism by seed, Type changes the relief, Dunes follow the Rotation, h within
// [0, Height] and the cap Height <= 3 x Scale, Edge fade 100 % gives 0 at the edge, Height 1; the skin has no
// gap (every two neighbouring columns inside the plan touch or overlap, every column has at least Skin blocks,
// each column one interval from its top), checked from the emitted blocks alone over 4 Outlines x 3 Types x
// Skin 1 to 3 x seeds, steep slopes included (Mountains, Scale 4, Height 12); no block outside the plan, the
// Rotation turns the plan; the cost at the worst case stays under 150k blocks.
public class TerrainGeometryTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params params(Outline o, Type t, int skin, long seed){
		Params p = new Params(); p.outline = o; p.type = t; p.skin = skin; p.seed = seed; p.widthX = 45; p.widthZ = 33; p.rotationDeg = 20; p.edgeAmplitude = 0.3; p.roundness = 0.4; p.sides = 5; p.height = 18; p.scale = 8; p.roughness = 0.6;
		return p;
	}
	static Map<Long, List<Integer>> columns(Params p) throws Exception {
		Map<Long, List<Integer>> cols = new HashMap<>(); Set<Long> seen = new HashSet<>(); int[] dupes = {0};
		TerrainGeometry.enumerate(p, (x, y, z) -> { if(!seen.add(LocalPos.pack(x, y, z))) dupes[0]++; cols.computeIfAbsent(LocalPos.pack(x, 0, z), k -> new ArrayList<>()).add(y); });
		if(dupes[0] > 0) cols.put(Long.MIN_VALUE, null);
		return cols;
	}
	// Plan membership computed here from the edge function alone (undo the rotation, normalise by the half widths)
	static boolean inPlan(Params p, int x, int z){
		double rot = Math.toRadians(p.rotationDeg);
		double u = (x * Math.cos(rot) + z * Math.sin(rot)) / (p.widthX / 2.0), v = (-x * Math.sin(rot) + z * Math.cos(rot)) / (p.widthZ / 2.0);
		return Math.sqrt(u * u + v * v) <= TerrainGeometry.edge(p, Math.atan2(v, u));
	}
	static int[] tops(Params p){ return TerrainGeometry.tops(p); }

	public static void main(String[] a) throws Exception {
		System.out.println("-- determinism and the relief");
		Params base = params(Outline.ORGANIC, Type.HILLS, 2, 11);
		check(Arrays.equals(tops(base), tops(params(Outline.ORGANIC, Type.HILLS, 2, 11))), "the same seed gives the same terrain");
		check(!Arrays.equals(tops(base), tops(params(Outline.ORGANIC, Type.HILLS, 2, 12))), "another seed another terrain");
		check(!Arrays.equals(tops(base), tops(params(Outline.ORGANIC, Type.MOUNTAINS, 2, 11))) && !Arrays.equals(tops(base), tops(params(Outline.ORGANIC, Type.DUNES, 2, 11))) && !Arrays.equals(tops(params(Outline.ORGANIC, Type.MOUNTAINS, 2, 11)), tops(params(Outline.ORGANIC, Type.DUNES, 2, 11))), "Hills, Mountains and Dunes are three different reliefs");
		// Dunes: the height changes along the wind (the Rotation) much more than across it
		for(double rot: new double[]{0, 90, 45}){
			Params d = new Params(); d.type = Type.DUNES; d.outline = Outline.SQUARE; d.roundness = 0; d.widthX = 81; d.widthZ = 81; d.scale = 16; d.height = 30; d.seed = 5; d.rotationDeg = rot;
			int[] t = tops(d); int h = TerrainGeometry.halfBox(d) + 1, n = 2 * h + 1; double along = 0, across = 0; int k = 0;
			double r = Math.toRadians(rot); int ax = (int) Math.round(Math.cos(r) * 2), az = (int) Math.round(Math.sin(r) * 2);
			for(int x = -25; x <= 25; x++) for(int z = -25; z <= 25; z++){ int i = (x + h) * n + (z + h), j = (x + ax + h) * n + (z + az + h), c = (x - az + h) * n + (z + ax + h); along += Math.abs(t[i] - t[j]); across += Math.abs(t[i] - t[c]); k++; }
			check(along > 2.5 * across, "Dunes, Rotation " + rot + ": the height changes " + String.format(Locale.ROOT, "%.2f", along / k) + " per 2 blocks along the wind, " + String.format(Locale.ROOT, "%.2f", across / k) + " across it");
		}

		System.out.println("-- heights, the cap, Edge fade, Height 1");
		boolean inRange = true, capped = true;
		for(int scale = TerrainGeometry.minScale; scale <= TerrainGeometry.maxScale; scale++){
			int max = Math.min(60, 3 * scale);
			capped &= TerrainGeometry.maxHeightFor(scale) == max;
			for(Type t: Type.values()){ Params p = new Params(); p.type = t; p.scale = scale; p.height = 60; p.roughness = 1; p.seed = scale; p.widthX = 31; p.widthZ = 31; for(int v: tops(p)) inRange &= v <= max; }
		}
		check(capped && inRange, "every Scale from 4 to 64: Height <= min(60, 3 x Scale), and no top above it (asked Height 60)");
		Params cap = new Params(); cap.scale = 4; cap.height = 60;
		int capTop = 0; cap.roughness = 1; cap.type = Type.MOUNTAINS; for(int v: tops(cap)) capTop = Math.max(capTop, v);
		check(capTop <= 12 && capTop >= 9 && TerrainGeometry.maxHeightFor(4) == 12 && TerrainGeometry.maxHeightFor(1) == 12 && TerrainGeometry.maxHeightFor(100) == 60, "Scale 4 holds Height 60 at 12 (tallest top " + capTop + "); Scale out of range counts as its limit");
		boolean all = true; for(Outline o: Outline.values()) for(Type t: Type.values()){ Params p = params(o, t, 2, 3); for(int v: tops(p)) all &= v == -1 || (v >= 0 && v <= p.height); }
		check(all, "h in [0, Height] for every Outline and Type");
		Params faded = params(Outline.CIRCLE, Type.MOUNTAINS, 2, 4); faded.edgeFade = 100;
		int[] ft = tops(faded); int fh = TerrainGeometry.halfBox(faded) + 1, fn = 2 * fh + 1; boolean edgeZero = true, someHigh = false;
		for(int x = -fh + 1; x < fh; x++) for(int z = -fh + 1; z < fh; z++){ int i = (x + fh) * fn + (z + fh); if(ft[i] < 0) continue;
			boolean edge = false; for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) edge |= ft[i + dx * fn + dz] < 0;
			if(edge) edgeZero &= ft[i] == 0; else someHigh |= ft[i] > 0; }
		check(edgeZero && someHigh, "Edge fade 100 %: every column on the edge is at 0, the inside still rises");
		Params half = params(Outline.CIRCLE, Type.MOUNTAINS, 2, 4); half.edgeFade = 50; Params none = params(Outline.CIRCLE, Type.MOUNTAINS, 2, 4);
		check(!Arrays.equals(tops(half), tops(faded)) && !Arrays.equals(tops(half), tops(none)), "Edge fade 50 % differs from 0 and 100 %");
		Params flat = params(Outline.SQUARE, Type.MOUNTAINS, 1, 9); flat.height = 1; boolean low = true; for(int v: tops(flat)) low &= v <= 1;
		check(low, "Height 1: every top at 0 or 1");

		System.out.println("-- the skin: no gaps, at least Skin blocks, nothing outside the plan");
		int cases = 0, gaps = 0, thin = 0, broken = 0, outside = 0, dupes = 0, wrongTop = 0;
		List<Params> all3 = new ArrayList<>();
		for(Outline o: Outline.values()) for(Type t: Type.values()) for(int skin = 1; skin <= 3; skin++) for(long seed = 1; seed <= 3; seed++) all3.add(params(o, t, skin, seed));
		for(Type t: Type.values()) for(int skin = 1; skin <= 3; skin++){ Params s = params(Outline.SQUARE, t, skin, 7); s.scale = 4; s.height = 12; s.roughness = 1; s.roundness = 0; s.widthX = 61; s.widthZ = 61; all3.add(s); }
		for(Params p: all3){
			Map<Long, List<Integer>> cols = columns(p); if(cols.containsKey(Long.MIN_VALUE)){ dupes++; cols.remove(Long.MIN_VALUE); }
			int[] t = tops(p); int h = TerrainGeometry.halfBox(p) + 1, n = 2 * h + 1;
			Map<Long, int[]> range = new HashMap<>();
			for(Map.Entry<Long, List<Integer>> e: cols.entrySet()){
				int x = LocalPos.unpackX(e.getKey()), z = LocalPos.unpackZ(e.getKey()); List<Integer> ys = e.getValue();
				int hi = Collections.max(ys), lo = Collections.min(ys);
				if(hi - lo + 1 != ys.size()) broken++;
				if(ys.size() < p.skin) thin++;
				if(!inPlan(p, x, z)) outside++;
				if(hi != t[(x + h) * n + (z + h)]) wrongTop++;
				range.put(e.getKey(), new int[]{lo, hi});
			}
			for(Map.Entry<Long, int[]> e: range.entrySet()){
				int x = LocalPos.unpackX(e.getKey()), z = LocalPos.unpackZ(e.getKey());
				for(int[] d: new int[][]{{1, 0}, {0, 1}}){ int[] b = range.get(LocalPos.pack(x + d[0], 0, z + d[1])); if(b == null) continue; int[] r = e.getValue();
					if(Math.max(r[0], b[0]) > Math.min(r[1], b[1]) + 1) gaps++; }
			}
			// Every column inside the plan is there (none missing)
			for(int x = -h; x <= h; x++) for(int z = -h; z <= h; z++) if(inPlan(p, x, z) && !range.containsKey(LocalPos.pack(x, 0, z))) outside++;
			cases++;
		}
		check(cases == 117 && gaps == 0, cases + " terrains (4 Outlines x 3 Types x Skin 1 to 3 x 3 seeds, and Scale 4 / Height 12 at Roughness 1, the steepest the cap allows): every two neighbouring columns touch or overlap");
		check(thin == 0 && broken == 0 && dupes == 0 && wrongTop == 0, "every column is one interval from its top (the relief), at least Skin blocks, each cell once");
		check(outside == 0, "every column inside the plan has its skin, none outside it");
		Params r0 = new Params(); r0.outline = Outline.SQUARE; r0.roundness = 0; r0.widthX = 41; r0.widthZ = 21; r0.height = 1;
		Params r90 = new Params(); r90.outline = Outline.SQUARE; r90.roundness = 0; r90.widthX = 41; r90.widthZ = 21; r90.height = 1; r90.rotationDeg = 90;
		Set<Long> p0 = new HashSet<>(), p90 = new HashSet<>(); columns(r0).keySet().forEach(k -> p0.add(LocalPos.pack(-LocalPos.unpackZ(k), 0, LocalPos.unpackX(k)))); p90.addAll(columns(r90).keySet());
		check(p0.equals(p90) && !columns(r0).keySet().equals(p90), "Rotation 90 turns the plan (41 x 21 becomes 21 x 41)");

		System.out.println("-- the cost under the cap");
		long worst = 0; String at = "";
		for(Type t: Type.values()) for(int scale: new int[]{4, 12, 20, 32, 64}) for(Outline o: new Outline[]{Outline.SQUARE, Outline.ORGANIC}){
			Params p = new Params(); p.type = t; p.outline = o; p.roundness = 0; p.edgeAmplitude = o == Outline.ORGANIC ? 1 : 0; p.widthX = 121; p.widthZ = 121; p.scale = scale; p.height = 60; p.roughness = 1; p.skin = 3; p.seed = 3;
			long[] c = {0}; TerrainGeometry.enumerate(p, (x, y, z) -> c[0]++); if(c[0] > worst){ worst = c[0]; at = t + " " + o + " Scale " + scale; }
		}
		check(worst < 150000, "121 x 121, Roughness 1, Skin 3, Height at its cap: the largest is " + worst + " blocks (" + at + "), under 150k");
		Params d = new Params(); d.outline = Outline.SQUARE; d.roundness = 0; d.widthX = 121; d.widthZ = 121; long[] dc = {0}; TerrainGeometry.enumerate(d, (x, y, z) -> dc[0]++);
		check(dc[0] > 30000 && dc[0] < 45000, "the defaults on 121 x 121 (Hills, Height 12, Scale 20, Skin 2): " + dc[0] + " blocks");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
