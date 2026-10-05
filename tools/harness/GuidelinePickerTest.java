import brentmaas.buildguide.common.shape.GuidelinePicker;
import brentmaas.buildguide.common.shape.GuidelinePicker.Cells;
import brentmaas.buildguide.common.shape.GuidelinePicker.Target;
import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.LocalPos;
import java.util.*;
// Area 3: the voxel walk that picks the guideline cell a right click fills. A fake world (air
// unless set; chunks can be unloaded) and fake guidelines in world coordinates.
public class GuidelinePickerTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final double INF = Double.POSITIVE_INFINITY;

	static class World implements IBlockProbe {
		final Map<Long,Integer> blocks = new HashMap<>(); final Set<Long> unloaded = new HashSet<>(); int reads = 0;
		static long k(int x, int y, int z){ return LocalPos.pack(x, y, z); }
		void set(int x, int y, int z, int f){ blocks.put(k(x, y, z), f); }
		public boolean isLoaded(int x, int y, int z){ return !unloaded.contains(LocalPos.pack(x >> 4, 0, z >> 4)); }
		public int flags(int x, int y, int z){ reads++; return blocks.getOrDefault(k(x, y, z), FLAG_AIR); }
		public String name(int x, int y, int z){ return "?"; }
	}
	static Cells cells(int[]... cs){ Set<Long> s = new HashSet<>(); for(int[] c: cs) s.add(LocalPos.pack(c[0], c[1], c[2])); return (x, y, z) -> s.contains(LocalPos.pack(x, y, z)); }
	static String at(Target t){ return t == null ? "none" : "[" + t.x + ", " + t.y + ", " + t.z + "] d=" + String.format(Locale.ROOT, "%.3f", t.distance); }
	static boolean is(Target t, int x, int y, int z){ return t != null && t.x == x && t.y == y && t.z == z; }
	// player standing at (0.5, 0, 0.5), eye at 1.62
	static final double[] BOX = {0.2, 0, 0.2, 0.8, 1.8, 0.8};
	static Target pick(double dx, double dy, double dz, double reach, double realHit, Cells c, World w){ return GuidelinePicker.pick(0.5, 1.62, 0.5, dx, dy, dz, reach, realHit, BOX, c, w); }

	public static void main(String[] a){
		System.out.println("-- the first empty guideline cell along the ray");
		World w = new World();
		Cells line = cells(new int[]{2,1,0}, new int[]{3,1,0}, new int[]{4,1,0});
		Target t = pick(1, 0, 0, 4.5, INF, line, w);
		check(is(t, 2, 1, 0) && Math.abs(t.distance - 1.5) < 1e-9, "looking +x along y 1: cell (2,1,0) entered at 1.5: " + at(t));
		w.set(2, 1, 0, IBlockProbe.FLAG_SOLID);
		t = pick(1, 0, 0, 4.5, INF, line, w);
		check(is(t, 3, 1, 0), "(2,1,0) already built (solid): skipped, next one (3,1,0): " + at(t));
		w.set(3, 1, 0, 0); // non-air, not solid, not replaceable: a torch
		t = pick(1, 0, 0, 4.5, INF, line, w);
		check(is(t, 4, 1, 0), "a torch in (3,1,0) is not empty: skipped: " + at(t));
		w.set(3, 1, 0, IBlockProbe.FLAG_REPLACEABLE); // water, tall grass
		t = pick(1, 0, 0, 4.5, INF, line, w);
		check(is(t, 3, 1, 0), "water or tall grass in (3,1,0): replaceable, so a target: " + at(t));
		w.set(3, 1, 0, IBlockProbe.FLAG_SOLID | IBlockProbe.FLAG_REPLACEABLE);
		check(is(pick(1, 0, 0, 4.5, INF, line, w), 3, 1, 0), "a solid but replaceable block (snow layer) is a target too: Block.canBeReplaced decides");

		System.out.println("-- reach");
		w = new World();
		check(pick(1, 0, 0, 1.4, INF, line, w) == null, "reach 1.4: (2,1,0) is entered at 1.5, out of reach");
		check(is(pick(1, 0, 0, 1.5001, INF, line, w), 2, 1, 0), "reach 1.5001: in reach");
		check(pick(1, 0, 0, 1.5, INF, line, w) == null, "reach exactly 1.5: the entry must be strictly inside");

		System.out.println("-- the real hit (a block or an entity in front)");
		check(is(pick(1, 0, 0, 4.5, 2.7, line, w), 2, 1, 0), "real block hit at 2.7: the cell entered at 1.5 is in front of it");
		check(pick(1, 0, 0, 4.5, 1.5, line, w) == null, "real hit at exactly 1.5 (the face in front of that cell): tie, left to vanilla");
		check(pick(1, 0, 0, 4.5, 1.2, line, w) == null, "real hit (an entity) at 1.2: the guideline cell is behind it, no target");
		check(is(pick(1, 0, 0, 4.5, 1.5 + 1e-3, line, w), 2, 1, 0), "real hit just behind the entry (1.501): target");

		System.out.println("-- exclusion boxes (local to the origin) are not targets");
		Set<Long> exp = new HashSet<>(); for(int x = 2; x < 5; x++) exp.add(LocalPos.pack(x, 0, 0)); // local line x 2..4, origin (0, 1, 0) -> world (2..4, 1, 0)
		Cells ex = GuidelinePicker.shapeCells(exp, 0, 1, 0, Arrays.asList(new int[]{2, 0, 0, 2, 0, 0}));
		check(is(pick(1, 0, 0, 4.5, INF, ex, w), 3, 1, 0), "local (2,0,0) excluded: world (2,1,0) skipped, (3,1,0) picked");
		Cells noEx = GuidelinePicker.shapeCells(exp, 0, 1, 0, Collections.emptyList());
		check(is(pick(1, 0, 0, 4.5, INF, noEx, w), 2, 1, 0), "same guideline without the box: (2,1,0)");
		Cells moved = GuidelinePicker.shapeCells(exp, 10, 1, 0, Collections.emptyList());
		check(pick(1, 0, 0, 4.5, INF, moved, w) == null, "origin moved to x 10: the guideline is where the current origin puts it (out of reach)");

		System.out.println("-- the player's own cells");
		Cells feet = cells(new int[]{0,0,0}, new int[]{0,1,0}, new int[]{1,0,0});
		Target down = GuidelinePicker.pick(0.5, 1.62, 0.5, 0.6, -1, 0, 4.5, INF, BOX, feet, w);
		check(is(down, 1, 0, 0), "looking down and forward: own cells (0,1,0), (0,0,0) skipped, (1,0,0) picked: " + at(down));
		check(GuidelinePicker.pick(0.5, 1.62, 0.5, 0, -1, 0, 4.5, INF, BOX, feet, w) == null, "straight down: only the player's own cells on the ray, no target");

		System.out.println("-- several shape sets: the nearest wins");
		Cells far = cells(new int[]{4,1,0}), near = cells(new int[]{3,1,0});
		Target tf = pick(1, 0, 0, 4.5, INF, far, w), tn = pick(1, 0, 0, 4.5, INF, near, w);
		Target best = tf == null ? tn : tn == null ? tf : tn.distance < tf.distance ? tn : tf;
		check(is(best, 3, 1, 0) && tf.distance > tn.distance, "two sets: entry distances " + String.format(Locale.ROOT, "%.1f / %.1f", tn.distance, tf.distance) + ", the nearer set wins");

		System.out.println("-- unloaded chunk");
		World u = new World(); u.unloaded.add(LocalPos.pack(0, 0, 0)); // chunk (0, 0) holds x 0..15
		check(pick(1, 0, 0, 4.5, INF, line, u) == null, "cells in an unloaded chunk are never targets (they read as air)");

		System.out.println("-- directions");
		Cells diag = cells(new int[]{2,3,2});
		Target td = GuidelinePicker.pick(0.5, 1.62, 0.5, 1.5, 1.38, 1.5, 4.5, INF, BOX, diag, w);
		check(is(td, 2, 3, 2), "diagonal ray up and to +x +z reaches (2,3,2): " + at(td));
		Cells back = cells(new int[]{-2,1,-1});
		Target tb = GuidelinePicker.pick(0.5, 1.62, 0.5, -1, 0, -0.4, 4.5, INF, BOX, back, w);
		check(is(tb, -2, 1, -1), "negative directions (-x, -z): " + at(tb));
		check(GuidelinePicker.pick(0.5, 1.62, 0.5, 0, 0, 0, 4.5, INF, BOX, line, w) == null, "zero direction: no target, no exception");
		Cells edge = cells(new int[]{3,1,0});
		check(is(GuidelinePicker.pick(2.0, 1.5, 0.5, 1, 0, 0, 4.5, INF, null, edge, w), 3, 1, 0), "eye exactly on a cell boundary (x 2.0): the walk is consistent");

		System.out.println("-- the size of the structure does not matter");
		Set<Long> big = new HashSet<>(); for(int x = 0; x < 100; x++) for(int y = 0; y < 50; y++) for(int z = 0; z < 100; z++) big.add(LocalPos.pack(x, y, z)); // 500,000
		World bw = new World(); for(long p: big) bw.set(LocalPos.unpackX(p) + 1, LocalPos.unpackY(p), LocalPos.unpackZ(p) - 50, IBlockProbe.FLAG_SOLID); // all built
		Cells bigCells = GuidelinePicker.shapeCells(big, 1, 0, -50, Collections.emptyList());
		bw.reads = 0; long t0 = System.nanoTime(); Target tbig = null; for(int i = 0; i < 1000; i++) tbig = pick(1, 0.1, 0.05, 4.5, INF, bigCells, bw);
		double us = (System.nanoTime() - t0) / 1e3 / 1000;
		check(tbig == null && bw.reads / 1000 <= 12, "500,000-cell structure, all built: " + (bw.reads / 1000) + " cells read per pick, " + String.format(Locale.ROOT, "%.2f", us) + " us per pick");
		check(us < 200, "a pick stays far below a frame");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
