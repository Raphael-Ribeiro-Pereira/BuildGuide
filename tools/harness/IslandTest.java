import brentmaas.buildguide.common.shape.IslandGeometry;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.LocalPos;
import java.util.*;
// Island block A: seeded plan, body and hollow shell (IslandGeometry). The shell is checked against
// an independent reference (BFS distance from the non-solid cells over a 3D grid), its closure by a
// 6-connected flood fill from outside, plus determinism, symmetry, roundness, the cap and the costs.
public class IslandTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static List<int[]> gen(Params p) throws InterruptedException {
		List<int[]> out = new ArrayList<>();
		IslandGeometry.enumerate(p, (x, y, z) -> out.add(new int[]{x, y, z}));
		return out;
	}
	static Set<Long> set(List<int[]> l){ Set<Long> s = new HashSet<>(); for(int[] b: l) s.add(LocalPos.pack(b[0], b[1], b[2])); return s; }
	static Params params(Outline o, Profile pr, int wx, int wz, int depth, int wall, long seed){
		Params p = new Params(); p.outline = o; p.profile = pr; p.widthX = wx; p.widthZ = wz; p.depth = depth; p.wall = wall; p.seed = seed; return p;
	}
	static String name(Params p){ return p.outline + "/" + p.profile + " " + p.widthX + "x" + p.widthZ + " depth " + p.depth + " wall " + p.wall + " seed " + p.seed; }

	// Plan columns (y = 0 cells) of a parameter set
	static Set<Long> plan(Params p){
		int h = IslandGeometry.halfBox(p), n = 2 * h + 1; int[] b = IslandGeometry.columns(p, 0); Set<Long> s = new HashSet<>();
		for(int x = -h; x <= h; x++) for(int z = -h; z <= h; z++) if(b[(x + h) * n + (z + h)] >= 0) s.add(LocalPos.pack(x, 0, z));
		return s;
	}

	// Reference shell, closure and diagonal leaks over a padded 3D grid. Returns {mismatches, reached interior (6), leaks (26), interior cells}
	static int[] shellCheck(Params p, Set<Long> emitted){
		int h = IslandGeometry.halfBox(p) + 2, n = 2 * h + 1, d = p.depth + 4, ny = d + 3; // y from -d-1 .. 1
		int[] col = IslandGeometry.columns(p, 2);
		boolean[] solid = new boolean[n * ny * n];
		for(int x = 0; x < n; x++) for(int z = 0; z < n; z++){ int b = col[x * n + z]; if(b < 0) continue; for(int y = -b; y <= 0; y++) solid[(x * ny + (y + d + 1)) * n + z] = true; }
		// Multi-source BFS distance from every non-solid cell (6-connected)
		int[] dist = new int[solid.length]; Arrays.fill(dist, -1); ArrayDeque<Integer> q = new ArrayDeque<>();
		for(int i = 0; i < solid.length; i++) if(!solid[i]){ dist[i] = 0; q.add(i); }
		int[][] six = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
		while(!q.isEmpty()){ int i = q.poll(); int x = i / (ny * n), y = (i / n) % ny, z = i % n;
			for(int[] o: six){ int a = x + o[0], b = y + o[1], c = z + o[2]; if(a < 0 || b < 0 || c < 0 || a >= n || b >= ny || c >= n) continue; int j = (a * ny + b) * n + c; if(dist[j] < 0){ dist[j] = dist[i] + 1; q.add(j); } } }
		int mismatch = 0, interior = 0;
		boolean[] shell = new boolean[solid.length];
		for(int i = 0; i < solid.length; i++){ int x = i / (ny * n) - h, y = (i / n) % ny - d - 1, z = i % n - h;
			boolean ref = solid[i] && dist[i] <= p.wall; shell[i] = emitted.contains(LocalPos.pack(x, y, z));
			if(ref != shell[i]) mismatch++; if(solid[i] && !ref) interior++; }
		// 6-connected flood fill from the grid corner through cells that are not shell
		boolean[] reach = new boolean[solid.length]; q.add(0); reach[0] = true; int reached = 0;
		while(!q.isEmpty()){ int i = q.poll(); int x = i / (ny * n), y = (i / n) % ny, z = i % n; if(solid[i] && !shell[i]) reached++;
			for(int[] o: six){ int a = x + o[0], b = y + o[1], c = z + o[2]; if(a < 0 || b < 0 || c < 0 || a >= n || b >= ny || c >= n) continue; int j = (a * ny + b) * n + c; if(!reach[j] && !shell[j]){ reach[j] = true; q.add(j); } } }
		// Diagonal leaks: interior cells with one of their 26 neighbours in the outside region
		int leaks = 0;
		for(int i = 0; i < solid.length; i++){ if(!solid[i] || shell[i]) continue; int x = i / (ny * n), y = (i / n) % ny, z = i % n; boolean leak = false;
			for(int a = -1; a <= 1 && !leak; a++) for(int b = -1; b <= 1 && !leak; b++) for(int c = -1; c <= 1 && !leak; c++){ int X = x + a, Y = y + b, Z = z + c; if(X < 0 || Y < 0 || Z < 0 || X >= n || Y >= ny || Z >= n) continue; if(reach[(X * ny + Y) * n + Z] && !solid[(X * ny + Y) * n + Z]) leak = true; }
			if(leak) leaks++; }
		return new int[]{mismatch, reached, leaks, interior};
	}

	public static void main(String[] a) throws Exception {
		System.out.println("-- determinism");
		Params org = params(Outline.ORGANIC, Profile.BOWL, 41, 33, 20, 2, 12345);
		List<int[]> g1 = gen(org), g2 = gen(org);
		boolean same = g1.size() == g2.size(); for(int i = 0; same && i < g1.size(); i++) same = Arrays.equals(g1.get(i), g2.get(i));
		check(same, "same seed, generated twice: identical blocks in the same order (" + g1.size() + ")");
		Params other = params(Outline.ORGANIC, Profile.BOWL, 41, 33, 20, 2, 54321);
		check(!set(g1).equals(set(gen(other))), "seed 12345 vs 54321: different shapes");
		check(!plan(org).equals(plan(other)), "seed 12345 vs 54321: different outlines");
		// Order and thread independence: generate on another thread after unrelated noise work
		final List<int[]>[] g3 = new List[1];
		Thread t = new Thread(() -> { try { gen(params(Outline.ORGANIC, Profile.CONE, 25, 25, 9, 1, 7)); g3[0] = gen(org); } catch(InterruptedException e){} }); t.start(); t.join();
		check(set(g3[0]).equals(set(g1)), "same seed on another thread after other generations: same blocks");

		System.out.println("-- shell: reference (BFS distance <= Wall), closure in 6-connectivity, leaks in 26-connectivity");
		int[] leaksByWall = new int[4], interiorByWall = new int[4];
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()) for(int w = 1; w <= 3; w++){
			Params p = params(o, pr, 27, 21, 14, w, 99); p.rotationDeg = o == Outline.SQUARE ? 30 : 0; p.roundness = o == Outline.SQUARE ? 0.0 : 0.7;
			List<int[]> l = gen(p); Set<Long> s = set(l); int[] r = shellCheck(p, s);
			leaksByWall[w] += r[2]; interiorByWall[w] += r[3];
			check(r[0] == 0 && r[1] == 0 && s.size() == l.size(), name(p) + ": shell = reference, outside fill reaches 0 of " + r[3] + " interior cells, no duplicates (" + l.size() + " blocks; 26-leaks " + r[2] + ")");
		}
		for(int w = 1; w <= 3; w++) System.out.println("   Wall " + w + ": 26-connected diagonal leaks " + leaksByWall[w] + " of " + interiorByWall[w] + " interior cells (12 cases)");
		// Rough organic edge and terraces with strong roughness, deep and thin
		for(int w = 1; w <= 3; w++){ Params p = params(Outline.ORGANIC, Profile.TERRACED, 45, 45, 30, w, 4242); p.edgeAmplitude = 0.6; p.roughness = 1.0; p.sharpness = 1.0; int[] r = shellCheck(p, set(gen(p))); check(r[0] == 0 && r[1] == 0, name(p) + " amplitude 0.6 roughness 1: shell = reference, closed (26-leaks " + r[2] + ")"); }

		System.out.println("-- bounds, depth, cap");
		for(Outline o: Outline.values()){
			Params p = params(o, Profile.CONE, 31, 19, 17, 2, 3); p.edgeAmplitude = 1.0; p.rotationDeg = 45; p.roughness = 1.0;
			List<int[]> l = gen(p); int hb = IslandGeometry.halfBox(p); boolean in = true; int minY = 0;
			for(int[] b: l){ in &= Math.abs(b[0]) <= hb && Math.abs(b[2]) <= hb && b[1] <= 0 && b[1] >= -p.depth; minY = Math.min(minY, b[1]); }
			check(in, o + " (amplitude 1, rotated 45, roughness 1): every block inside |x|,|z| <= " + hb + ", -" + p.depth + " <= y <= 0 (lowest " + minY + ")");
			Set<Long> cap = new HashSet<>(); for(int[] b: l) if(b[1] == 0) cap.add(LocalPos.pack(b[0], 0, b[2]));
			check(cap.equals(plan(p)), o + ": the y = 0 blocks are the whole plan disc (" + cap.size() + " cells, no holes)");
		}
		Params deep = params(Outline.CIRCLE, Profile.CONE, 41, 41, 30, 1, 0); deep.sharpness = 0.0; deep.roughness = 0.0;
		int lowest = 0; for(int[] b: gen(deep)) lowest = Math.min(lowest, b[1]);
		check(lowest == -30, "Cone depth 30, no roughness: reaches exactly y = -30 at the centre");
		Params over = params(Outline.CIRCLE, Profile.BOWL, 500, 500, 500, 9, 0);
		int ho = IslandGeometry.halfBox(over); boolean inside = true; for(int[] b: gen(over)) inside &= b[1] >= -IslandGeometry.maxDepth && Math.abs(b[0]) <= (IslandGeometry.maxWidth + 1) / 2;
		check(ho == (IslandGeometry.maxWidth + 1) / 2 && inside, "out-of-range values are clamped: width " + IslandGeometry.maxWidth + ", depth " + IslandGeometry.maxDepth + ", wall " + IslandGeometry.maxWall);

		System.out.println("-- symmetry");
		for(Outline o: new Outline[]{Outline.CIRCLE, Outline.SQUARE}) for(Profile pr: Profile.values()){
			Params p = params(o, pr, 23, 15, 12, 2, 77); p.roughness = 0.0; p.roundness = 0.4;
			Set<Long> s = set(gen(p)), mx = new HashSet<>(), mz = new HashSet<>();
			for(long k: s){ int x = LocalPos.unpackX(k), y = LocalPos.unpackY(k), z = LocalPos.unpackZ(k); mx.add(LocalPos.pack(-x, y, z)); mz.add(LocalPos.pack(x, y, -z)); }
			check(s.equals(mx) && s.equals(mz), o + "/" + pr + " 23x15, no noise: mirror in X and in Z");
		}
		for(int sides = 3; sides <= 12; sides++){
			Params p = params(Outline.POLYGON, Profile.BOWL, 41, 41, 10, 2, 0); p.sides = sides; p.roundness = 0.0;
			boolean periodic = true; int maxima = 0; int N = 3600; double[] e = new double[N];
			for(int i = 0; i < N; i++){ double th = 2 * Math.PI * i / N; e[i] = IslandGeometry.edge(p, th); periodic &= Math.abs(e[i] - IslandGeometry.edge(p, th + 2 * Math.PI / sides)) < 1e-9; }
			for(int i = 0; i < N; i++) if(e[i] > e[(i + N - 1) % N] + 1e-12 && e[i] >= e[(i + 1) % N]) maxima++;
			check(periodic && maxima == sides, "Polygon " + sides + " sides: edge repeats every 360/" + sides + " degrees, " + maxima + " vertices");
		}
		Params sq = params(Outline.POLYGON, Profile.BOWL, 31, 31, 8, 2, 0); sq.sides = 4; sq.roughness = 0;
		Set<Long> s4 = plan(sq), r4 = new HashSet<>(); for(long k: s4) r4.add(LocalPos.pack(-LocalPos.unpackZ(k), 0, LocalPos.unpackX(k)));
		check(s4.equals(r4), "Polygon 4 sides, square widths: the plan is unchanged by a 90 degree turn");

		System.out.println("-- roundness: 0 = square, 1 = circle");
		Params r0 = params(Outline.SQUARE, Profile.BOWL, 21, 21, 5, 1, 0); r0.roundness = 0.0;
		Set<Long> sqr = new HashSet<>(); for(int x = -10; x <= 10; x++) for(int z = -10; z <= 10; z++) sqr.add(LocalPos.pack(x, 0, z));
		check(plan(r0).equals(sqr), "Square roundness 0, width 21: exactly the 21 x 21 square (" + plan(r0).size() + " cells)");
		Params r1 = params(Outline.SQUARE, Profile.BOWL, 21, 21, 5, 1, 0); r1.roundness = 1.0;
		Params ci = params(Outline.CIRCLE, Profile.BOWL, 21, 21, 5, 1, 0);
		check(plan(r1).equals(plan(ci)), "Square roundness 1: the same cells as Circle (" + plan(ci).size() + ")");
		Params rh = params(Outline.SQUARE, Profile.BOWL, 21, 21, 5, 1, 0); rh.roundness = 0.5;
		int c0 = plan(r0).size(), ch = plan(rh).size(), c1 = plan(r1).size();
		check(c0 > ch && ch > c1, "coverage shrinks with roundness: " + c0 + " > " + ch + " > " + c1);

		System.out.println("-- cost (wall 2, roughness 0.3, sharpness 0.4)");
		Runtime rt = Runtime.getRuntime(); long worst = 0;
		for(Outline o: new Outline[]{Outline.CIRCLE, Outline.SQUARE}) for(int r: new int[]{30, 45, 60}) for(int d: new int[]{40, 80}){
			Params p = params(o, Profile.BOWL, 2 * r + 1, 2 * r + 1, d, 2, 1); p.roundness = 0.0;
			gen(p); long t0 = System.nanoTime(); int[] count = {0}; IslandGeometry.enumerate(p, (x, y, z) -> count[0]++); long ms = (System.nanoTime() - t0) / 1000000; worst = Math.max(worst, ms);
			System.out.printf(Locale.ROOT, "   %-6s radius %d depth %d: %7d blocks, %4d ms, vertex buffer %6.1f MB, expected set ~%5.1f MB%n", o, r, d, count[0], ms, count[0] * 672 / 1048576.0, count[0] * 60 / 1048576.0);
		}
		Params big = params(Outline.SQUARE, Profile.BOWL, 121, 121, 80, 2, 1); big.roundness = 0.0;
		System.gc(); long before = rt.totalMemory() - rt.freeMemory(); Set<Long> bigSet = set(gen(big)); System.gc(); long after = rt.totalMemory() - rt.freeMemory();
		System.out.printf(Locale.ROOT, "   measured expected-set memory, Square radius 60 depth 80: %.1f MB for %d blocks (%d B/entry)%n", (after - before) / 1048576.0, bigSet.size(), (after - before) / Math.max(1, bigSet.size()));
		check(worst < 1500, "worst generation " + worst + " ms (limit 1500 ms, the slowest of the 12 cases)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
