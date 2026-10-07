import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandControls.Values;
import brentmaas.buildguide.common.shape.IslandGeometry.BreakMode;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.IslandGeometry.SpikeMode;
// Island spikes 2, phase 3: Segmented spikes (pieces with gaps, the top one attached, the others loose).
// The body keeps one depth per column; loose pieces are a sparse list on the side. The shell is checked
// against a brute-force reference over the whole union (3D grid, BFS distance from the non-solid cells).
public class IslandSegmentedTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params seg(Outline o, Profile pr, int wall, int count, SpikeMode m, int pieces, int gap, long seed){
		Params p = new Params(); p.outline = o; p.profile = pr; p.wall = wall; p.seed = seed; p.widthX = 41; p.widthZ = 33; p.depth = 12; p.sides = 5;
		p.spikes = count; p.spikeMode = m; p.spikeLength = 24; p.spikeBase = 4; p.lengthVar = 20; p.spread = 70; p.breakMode = BreakMode.SEGMENTED; p.pieces = pieces; p.gap = gap; return p;
	}
	// The union as a padded 3D grid: columns (body and attached pieces) plus the loose cells
	static int H, N, D, NY;
	static boolean[] solid(Params p){
		int[] col = IslandGeometry.columns(p, 2); List<int[]> loose = IslandGeometry.looseCells(p);
		int deepest = 0; for(int b: col) deepest = Math.max(deepest, b); for(int[] c: loose) deepest = Math.max(deepest, -c[1]);
		H = IslandGeometry.halfBox(p) + 2; N = 2 * H + 1; D = deepest + 4; NY = D + 3;
		boolean[] s = new boolean[N * NY * N];
		for(int x = 0; x < N; x++) for(int z = 0; z < N; z++){ int b = col[x * N + z]; if(b < 0) continue; for(int y = -b; y <= 0; y++) s[(x * NY + (y + D + 1)) * N + z] = true; }
		for(int[] c: loose) s[((c[0] + H) * NY + (c[1] + D + 1)) * N + (c[2] + H)] = true;
		return s;
	}
	static int idx(int x, int y, int z){ return ((x + H) * NY + (y + D + 1)) * N + (z + H); }
	static final int[][] SIX = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
	// {mismatches against the reference shell, interior cells the outside fill reaches}
	static int[] shellCheck(Params p, Set<Long> emitted){
		boolean[] s = solid(p); int[] dist = new int[s.length]; Arrays.fill(dist, -1); ArrayDeque<Integer> q = new ArrayDeque<>();
		for(int i = 0; i < s.length; i++) if(!s[i]){ dist[i] = 0; q.add(i); }
		while(!q.isEmpty()){ int i = q.poll(); int x = i / (NY * N), y = (i / N) % NY, z = i % N; for(int[] o: SIX){ int a = x + o[0], b = y + o[1], c = z + o[2]; if(a < 0 || b < 0 || c < 0 || a >= N || b >= NY || c >= N) continue; int j = (a * NY + b) * N + c; if(dist[j] < 0){ dist[j] = dist[i] + 1; q.add(j); } } }
		int mismatch = 0; boolean[] shell = new boolean[s.length];
		for(int i = 0; i < s.length; i++){ int x = i / (NY * N) - H, y = (i / N) % NY - D - 1, z = i % N - H; boolean ref = s[i] && dist[i] <= p.wall; shell[i] = emitted.contains(LocalPos.pack(x, y, z)); if(ref != shell[i]) mismatch++; }
		boolean[] reach = new boolean[s.length]; q.add(0); reach[0] = true; int reached = 0;
		while(!q.isEmpty()){ int i = q.poll(); int x = i / (NY * N), y = (i / N) % NY, z = i % N; if(s[i] && !shell[i]) reached++; for(int[] o: SIX){ int a = x + o[0], b = y + o[1], c = z + o[2]; if(a < 0 || b < 0 || c < 0 || a >= N || b >= NY || c >= N) continue; int j = (a * NY + b) * N + c; if(!reach[j] && !shell[j]){ reach[j] = true; q.add(j); } } }
		return new int[]{mismatch, reached};
	}
	// 6-connected components of the solid union
	static int components(boolean[] s){
		boolean[] seen = new boolean[s.length]; int count = 0; ArrayDeque<Integer> q = new ArrayDeque<>();
		for(int st = 0; st < s.length; st++){ if(!s[st] || seen[st]) continue; count++; seen[st] = true; q.add(st);
			while(!q.isEmpty()){ int i = q.poll(); int x = i / (NY * N), y = (i / N) % NY, z = i % N; for(int[] o: SIX){ int a = x + o[0], b = y + o[1], c = z + o[2]; if(a < 0 || b < 0 || c < 0 || a >= N || b >= NY || c >= N) continue; int j = (a * NY + b) * N + c; if(s[j] && !seen[j]){ seen[j] = true; q.add(j); } } } }
		return count;
	}

	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }
	static Object call(Object o, String m) throws Exception { Method x = ShapeIsland.class.getDeclaredMethod(m); x.setAccessible(true); return x.invoke(o); }
	static Object get(Object o, String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) get(s, f)).value = v; }
	static void setRandom(ShapeIsland s, long seed) throws Exception { Field x = ShapeIsland.class.getDeclaredField("random"); x.setAccessible(true); x.set(s, new Random(seed)); }
	static Values values(ShapeIsland s) throws Exception { return (Values) call(s, "values"); }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new FloatStepTest.Widgets();

		System.out.println("-- Attached and Count 0 are the GOLDEN islands");
		Params att = seg(Outline.ORGANIC, Profile.BOWL, 2, 8, SpikeMode.FILL, 3, 2, 5); att.breakMode = BreakMode.ATTACHED; Params att2 = seg(Outline.ORGANIC, Profile.BOWL, 2, 8, SpikeMode.FILL, 4, 6, 5); att2.breakMode = BreakMode.ATTACHED;
		check(IslandTest.set(IslandTest.gen(att)).equals(IslandTest.set(IslandTest.gen(att2))) && IslandGeometry.looseCells(att).isEmpty(), "Attached: Pieces and Gap change nothing, no loose cells (IslandGoldenTest and IslandGoldenSpikesTest guard the blocks)");
		Params none = seg(Outline.CIRCLE, Profile.BOWL, 2, 0, SpikeMode.RANDOM, 3, 2, 5); Params noneAtt = seg(Outline.CIRCLE, Profile.BOWL, 2, 0, SpikeMode.RANDOM, 3, 2, 5); noneAtt.breakMode = BreakMode.ATTACHED;
		check(IslandTest.set(IslandTest.gen(none)).equals(IslandTest.set(IslandTest.gen(noneAtt))), "Segmented with Count 0: the body as it is");

		System.out.println("-- one central spike, 3 pieces, gap 2");
		Params one = seg(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 3, 2, 1); one.spikeLength = 30; one.lengthVar = 0; one.roughness = 0; one.sharpness = 0;
		int[] layout = IslandGeometry.pieceLayout(30, 3, 2);
		check(layout[0] == 3 && layout[1] == 8, "length 30, 3 pieces, gap 2: pieces of (30 - 2 x 2) / 3 = 8 blocks");
		boolean[] s = solid(one);
		check(components(s) == 3, "3 components: the island with its attached top piece, and 2 loose pieces (" + components(s) + ")");
		int surface = IslandGeometry.columns(seg(Outline.CIRCLE, Profile.BOWL, 2, 0, SpikeMode.RING, 3, 2, 1), 0)[IslandGeometry.halfBox(one) * (2 * IslandGeometry.halfBox(one) + 1) + IslandGeometry.halfBox(one)];
		List<int[]> runs = new ArrayList<>(); boolean in = false; int start = 0;
		for(int y = 0; y >= -(surface + 40); y--){ boolean v = s[idx(0, y, 0)]; if(v && !in){ in = true; start = y; } if(!v && in){ in = false; runs.add(new int[]{start, y + 1}); } }
		boolean attached = runs.size() == 3 && runs.get(0)[0] == 0 && runs.get(0)[1] == -(surface + 8);
		boolean gaps = runs.size() == 3 && runs.get(0)[1] - runs.get(1)[0] - 1 == 2 && runs.get(1)[1] - runs.get(2)[0] - 1 == 2;
		boolean pieces = runs.size() == 3 && runs.get(1)[0] - runs.get(1)[1] + 1 == 8 && runs.get(2)[0] - runs.get(2)[1] + 1 == 8;
		StringBuilder rs = new StringBuilder(); for(int[] r: runs) rs.append(" [").append(r[0]).append("..").append(r[1]).append("]");
		check(attached, "the top piece hangs from the body: the axis column is solid from the top to 8 below the bottom surface (" + surface + ")");
		check(gaps && pieces, "along the axis: gaps of exactly 2 blocks, loose pieces of 8 (solid runs:" + rs + ")");

		System.out.println("-- too short");
		check(IslandGeometry.pieceLayout(5, 4, 2) == null && IslandGeometry.pieceLayout(8, 4, 2)[0] == 2 && IslandGeometry.pieceLayout(14, 4, 2)[0] == 4 && IslandGeometry.pieceLayout(12, 4, 2)[0] == 3, "length 5 stays whole; 8 drops to 2 pieces; 12 to 3; 14 keeps 4 (pieces of at least 2 blocks)");
		Params shortSpike = seg(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 4, 6, 1); shortSpike.spikeLength = 9; shortSpike.lengthVar = 0;
		Params shortAtt = seg(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 4, 6, 1); shortAtt.spikeLength = 9; shortAtt.lengthVar = 0; shortAtt.breakMode = BreakMode.ATTACHED;
		check(IslandGeometry.looseCells(shortSpike).isEmpty() && IslandTest.set(IslandTest.gen(shortSpike)).equals(IslandTest.set(IslandTest.gen(shortAtt))), "length 9 with gap 6 cannot break into 2 pieces of 2: it stays whole, as Attached");

		System.out.println("-- shell of the union with loose pieces");
		int cases = 0, bad = 0, withLoose = 0; String firstBad = null;
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()) for(int w = 1; w <= 3; w++) for(boolean drip: new boolean[]{false, true}){
			Params p = seg(o, pr, w, 6 + cases % 10, SpikeMode.values()[cases % 3], 2 + cases % 3, 1 + cases % 6, 50 + cases); p.dripstone = drip; p.taper = 0.5 + (cases % 6) * 0.5; p.rotationDeg = 11 * cases; cases++;
			if(!IslandGeometry.looseCells(p).isEmpty()) withLoose++;
			List<int[]> l = IslandTest.gen(p); Set<Long> st = IslandTest.set(l); int[] r = shellCheck(p, st);
			if(r[0] != 0 || r[1] != 0 || st.size() != l.size()){ bad++; if(firstBad == null) firstBad = o + "/" + pr + " wall " + w + (drip ? " dripstone" : "") + ": mismatch " + r[0] + ", reached " + r[1]; }
		}
		check(bad == 0 && withLoose == cases, cases + " Segmented islands (" + withLoose + " with loose pieces), Outline x Profile x Wall x Dripstone: shell = brute-force reference on the union, closed, no duplicates" + (firstBad == null ? "" : " (first bad: " + firstBad + ")"));
		Params dense = seg(Outline.SQUARE, Profile.TERRACED, 3, 64, SpikeMode.FILL, 4, 1, 9); dense.widthX = 61; dense.widthZ = 61; dense.depth = 20; dense.spikeLength = 40; dense.spikeBase = 8; dense.taper = 0.5; dense.spread = 100;
		int[] rd = shellCheck(dense, IslandTest.set(IslandTest.gen(dense)));
		check(rd[0] == 0 && rd[1] == 0, "64 overlapping spikes (base 8, Taper 0.5), 4 pieces, gap 1: shell = reference, closed");

		System.out.println("-- cost: 64 Segmented spikes at their widest");
		int worst = 0; long worstMs = 0; String at = "";
		for(SpikeMode m: SpikeMode.values()) for(int gap: new int[]{1, 3}) for(boolean d: new boolean[]{false, true}){
			Params w = seg(Outline.ORGANIC, Profile.BOWL, 3, 64, m, 4, gap, 1); w.widthX = 121; w.widthZ = 121; w.depth = 80; w.spikeLength = 40; w.spikeBase = 8; w.spread = 100; w.taper = 0.5; w.dripstone = d; w.lengthVar = 0; w.roundness = 0; w.edgeAmplitude = 0.25; w.roughness = 0.3; w.sharpness = 0.4;
			int[] c = {0}; long t0 = System.nanoTime(); IslandGeometry.enumerate(w, (x, y, z) -> c[0]++); long ms = (System.nanoTime() - t0) / 1000000; worstMs = Math.max(worstMs, ms);
			if(c[0] > worst){ worst = c[0]; at = m + " gap " + gap + (d ? " dripstone" : ""); }
		}
		System.out.printf(Locale.ROOT, "   worst: %d blocks (%s), about %.1f MB of vertex buffer, slowest geometry %d ms%n", worst, at, worst * 672 / 1048576.0, worstMs);
		check(worst < 400000 && worst * 672L < 260L * 1048576 && worstMs < 1500, "64 Segmented spikes stay under ~400k blocks and ~260 MB: " + worst + " blocks (cap 64 kept)");

		System.out.println("-- controls");
		TestIsland r = new TestIsland(); setRandom(r, 3); setProp(r, "propertySpikes", 10); setProp(r, "propertyBreak", BreakMode.SEGMENTED); setProp(r, "propertyPieces", 3); setProp(r, "propertyGap", 5); setProp(r, "propertySpikesPercent", 100); setProp(r, "propertyBodyPercent", 100);
		boolean fixed = true; for(int i = 0; i < 40; i++){ call(r, "randomize"); Values x = values(r); fixed &= x.breakMode == BreakMode.SEGMENTED && x.pieces == 3 && x.gap == 5; }
		call(r, "naturalizeSpikes"); Values x = values(r); fixed &= x.breakMode == BreakMode.SEGMENTED && x.pieces == 3 && x.gap == 5;
		check(fixed, "Randomize and Naturalize spikes never change Break, Pieces or Gap");
		TestIsland u = new TestIsland(); setRandom(u, 4); setProp(u, "propertySpikes", 6); Values before = values(u); setProp(u, "propertySpikesPercent", 100); before = values(u);
		call(u, "randomize"); setProp(u, "propertyBreak", BreakMode.SEGMENTED); call(u, "undo");
		check(values(u).breakMode == BreakMode.ATTACHED && values(u).equals(before), "Undo restores the values before the last Randomize, Break included");
		ShapeIsland panel = new TestIsland(); setProp(panel, "propertySpikes", 4);
		check(panel.countRows(3) == 4, "Spike shape, Attached: Dripstone, Taper, Edge falloff %, Break (4 rows)");
		setProp(panel, "propertyBreak", BreakMode.SEGMENTED); check(panel.countRows(3) == 6, "Segmented: + Pieces and Gap (6 rows)");
		String old = "ORGANIC,51,37,6,0.7,15.0,0.25,6.0,3,30,TERRACED,0.4,0.3,777,Runnable,20,35,Runnable,Runnable,9,FILL,22,5,15,70,35,true,2.3,45,60,Runnable";
		ShapeIsland l = new TestIsland(); l.restorePersistence(old);
		check(!l.error && l.toPersistence().startsWith(old + ",ATTACHED,2,2,"), "a 31-value Island loads unchanged, Break Attached, Pieces 2, Gap 2 (later values follow): " + l.toPersistence());
		ShapeIsland g = new TestIsland(); g.restorePersistence(old + ",SEGMENTED,4,6");
		check(!g.error && g.toPersistence().startsWith(old + ",SEGMENTED,4,6,") && new TestIsland().properties.size() >= 34, "Segmented, 4 pieces, gap 6 saved and loaded; the 34 persisted properties of spikes 2 in their places");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
