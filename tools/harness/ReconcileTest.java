import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.StateReconciler;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
import java.util.*;
// Ghost near-block fix: the safety net (StateReconciler) and the second injection's idempotence.
// A fake world stands in for the client level (world coordinates, chunks 16 x 16 that can be
// "unloaded": they read as air). The real ValidationState is used throughout. GhostTest holds the
// model of the 1.21.11 client block-change code; here it is run with and without the second hook.
public class ReconcileTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static class World implements IBlockProbe {
		final Map<Long,Integer> blocks = new HashMap<>(); final Set<Long> unloaded = new HashSet<>();
		int flagsCalls = 0, loadedCalls = 0; Set<Long> seen = null;
		static long k(int x, int y, int z){ return LocalPos.pack(x, y, z); }
		static long chunk(int x, int z){ return LocalPos.pack(x >> 4, 0, z >> 4); }
		void solid(int x, int y, int z){ blocks.put(k(x, y, z), FLAG_SOLID); }
		void ignored(int x, int y, int z){ blocks.put(k(x, y, z), FLAG_SOLID | FLAG_IGNORED); }
		void air(int x, int y, int z){ blocks.remove(k(x, y, z)); }
		public boolean isLoaded(int x, int y, int z){ loadedCalls++; return !unloaded.contains(chunk(x, z)); }
		public int flags(int x, int y, int z){
			flagsCalls++; if(seen != null) seen.add(k(x, y, z));
			if(unloaded.contains(chunk(x, z))) return FLAG_AIR; // what an unloaded chunk reads as
			return blocks.getOrDefault(k(x, y, z), FLAG_AIR);
		}
		public String name(int x, int y, int z){ return "Scaffolding"; }
	}

	// 6 expected cells (0..5, 0, 0) all OK and solid, one structure error at local (2, 1, 0), solid
	static ValidationState small(World w, int ox, int oy, int oz){
		List<Long> exp = new ArrayList<>(); for(int x = 0; x < 6; x++) exp.add(LocalPos.pack(x, 0, 0));
		ValidationState s = new ValidationState(); s.beginScan(exp, ox, oy, oz);
		for(int x = 0; x < 6; x++){ w.solid(ox + x, oy, oz); s.setStatus(LocalPos.pack(x, 0, 0), ValidationState.OK, null); }
		w.solid(ox + 2, oy + 1, oz);
		s.setNearBlocks(Arrays.asList(new NearBlock(LocalPos.pack(2, 1, 0), "Stone", 1f))); s.endScan();
		return s;
	}
	// nx * ny * nz expected cells, all OK and solid, no structure errors
	static ValidationState box(World w, int nx, int ny, int nz, int ox, int oy, int oz){
		List<Long> exp = new ArrayList<>(); for(int x = 0; x < nx; x++) for(int y = 0; y < ny; y++) for(int z = 0; z < nz; z++) exp.add(LocalPos.pack(x, y, z));
		ValidationState s = new ValidationState(); s.beginScan(exp, ox, oy, oz);
		for(long p: exp){ w.solid(ox + LocalPos.unpackX(p), oy + LocalPos.unpackY(p), oz + LocalPos.unpackZ(p)); s.setStatus(p, ValidationState.OK, null); }
		s.endScan(); return s;
	}
	static int run(ValidationState s, World w, List<String> log){ return StateReconciler.run(s, w, StateReconciler.checksPerPass, log::add); }

	public static void main(String[] a){
		System.out.println("-- the ghost: world changed without an event reaching the state");
		World w = new World(); ValidationState s = small(w, 100, 64, -200); List<String> log = new ArrayList<>();
		check(run(s, w, log) == 0 && log.isEmpty(), "healthy state: nothing to correct, nothing logged");
		w.air(102, 65, -200);
		check(s.getNearCount() == 1, "BEFORE the net: the error is stuck (world is air, state keeps 1 error)");
		int n = run(s, w, log);
		check(n == 1 && s.getNearCount() == 0, "AFTER one pass: error removed");
		check(log.size() == 1 && log.get(0).contains("[102, 65, -200]") && log.get(0).contains("structure error") && log.get(0).contains("Stone"), "one log line with the world position (scan origin + local) and the old status: "+log);
		check(run(s, w, log) == 0 && log.size() == 1, "next pass: quiet");
		ValidationState s2 = small(w = new World(), 100, 64, -200); w.ignored(102, 65, -200); log.clear();
		check(run(s2, w, log) == 1 && s2.getNearCount() == 0, "an error whose block became an ignored type is dropped too");
		ValidationState s2b = small(w = new World(), 100, 64, -200); w.blocks.put(World.k(102, 65, -200), 0); // non-air, not solid (torch, water)
		check(run(s2b, w, log) == 1 && s2b.getNearCount() == 0, "a non-solid block (torch, water) is not a structure error either");
		ValidationState s3 = small(w = new World(), 100, 64, -200); log.clear();
		check(run(s3, w, log) == 0 && s3.getNearCount() == 1, "a real error that is still there is kept");

		System.out.println("-- tracked positions");
		w = new World(); s = small(w, 100, 64, -200); log.clear();
		w.air(103, 64, -200);
		check(run(s, w, log) == 1 && s.getStatus(LocalPos.pack(3, 0, 0)) == ValidationState.MISSING && s.getOk() == 5 && s.getMissing() == 1, "block broken without an event: OK -> MISSING, counters follow (ok 5, missing 1)");
		check(log.get(0).contains("[103, 64, -200]") && log.get(0).contains("was OK") && log.get(0).contains("MISSING"), "logged: "+log.get(0));
		w.solid(103, 64, -200);
		check(run(s, w, log) == 1 && s.getStatus(LocalPos.pack(3, 0, 0)) == ValidationState.OK && s.getOk() == 6 && s.getMissing() == 0, "block placed without an event: MISSING -> OK");
		w.ignored(101, 64, -200);
		check(run(s, w, log) == 1 && s.getStatus(LocalPos.pack(1, 0, 0)) == ValidationState.IGNORED && "Scaffolding".equals(s.getIgnoredBlockName(LocalPos.pack(1, 0, 0))) && s.getIgnored() == 1, "ignored type placed: IGNORED with its name");
		check(s.getOk() + s.getMissing() == s.getTotal() && s.getIgnored() <= s.getMissing(), "ok + missing (ignored counts as missing) still add up to the total");

		System.out.println("-- chunk gate: an unloaded chunk reads as air and proves nothing");
		w = new World(); s = small(w, 100, 64, -200); log.clear();
		w.unloaded.add(World.chunk(100, -200));
		int callsBefore = w.flagsCalls;
		check(run(s, w, log) == 0 && w.flagsCalls == callsBefore, "chunk unloaded: nothing read, nothing changed");
		check(s.getNearCount() == 1 && s.getOk() == 6 && s.getMissing() == 0 && log.isEmpty(), "errors kept, no position turned into missing, no log");
		w.unloaded.clear(); w.air(102, 65, -200); w.air(105, 64, -200);
		check(run(s, w, log) == 2 && s.getNearCount() == 0 && s.getMissing() == 1, "chunk loaded again: the real differences are corrected (error gone, one missing)");
		// half the cells in an unloaded chunk (x crosses 112): only the loaded half is judged
		w = new World(); s = small(w, 110, 64, -200); w.unloaded.add(World.chunk(112, -200)); // cells 110, 111 loaded; 112..115 not
		for(int x = 110; x <= 115; x++) w.air(x, 64, -200);
		check(run(s, w, log) == 2 && s.getMissing() == 2 && s.getOk() == 4, "mixed chunks: cells in loaded chunks corrected (2), the others untouched");

		System.out.println("-- no pass while regenerating or with a scan pending");
		w = new World(); s = small(w, 100, 64, -200); w.air(102, 65, -200); w.air(103, 64, -200);
		s.invalidate(); callsBefore = w.flagsCalls;
		check(run(s, w, log) == 0 && w.flagsCalls == callsBefore && w.loadedCalls == 0, "unvalidated state (regenerating, being scanned): no read at all");
		w = new World(); s = small(w, 100, 64, -200); w.air(102, 65, -200); s.requestScan();
		check(run(s, w, log) == 0 && w.flagsCalls == 0 && s.getNearCount() == 1, "scan pending: no read, the scan will read the world anyway");
		s.consumeScanRequest();
		check(run(s, w, log) == 1, "request consumed: the pass runs again");

		System.out.println("-- excluded since the scan: never brought back");
		w = new World(); s = small(w, 100, 64, -200); log.clear(); run(s, w, log);
		s.exclude(LocalPos.pack(4, 0, 0)); w.air(104, 64, -200);
		check(run(s, w, log) == 0 && s.getStatus(LocalPos.pack(4, 0, 0)) == ValidationState.UNKNOWN && s.getTotal() == 5 && log.isEmpty(), "excluded position stays out of the state");

		System.out.println("-- round robin with a budget");
		w = new World(); s = box(w, 50, 50, 20, 1000, 70, 1000); // 50,000 positions
		long[] tracked = s.getTrackedPositions();
		check(tracked.length == 50000 && StateReconciler.checksPerPass == 4000, "50,000 tracked, budget " + StateReconciler.checksPerPass + " per pass (every " + StateReconciler.intervalMillis + " ms)");
		long last = tracked[tracked.length - 1]; // the one the cursor reaches last
		w.air(1000 + LocalPos.unpackX(last), 70 + LocalPos.unpackY(last), 1000 + LocalPos.unpackZ(last));
		w.seen = new HashSet<>(); int passes = 0, found = 0; List<String> big = new ArrayList<>(); boolean exact = true;
		while(found == 0 && passes < 40){
			int before = w.flagsCalls; found += StateReconciler.run(s, w, StateReconciler.checksPerPass, big::add); passes++;
			if(found == 0 && w.flagsCalls - before != 4000) exact = false;
		}
		check(exact && passes == 13, "a divergence at the end of 50,000 positions is found on pass " + passes + " (13 = ceil(50000 / 4000)), 4000 reads on each earlier pass");
		check(w.seen.size() == 50000, "13 passes read each of the 50,000 positions");
		w = new World(); ValidationState s2k = box(w, 20, 10, 10, 1000, 70, 1000); // 2,000
		w.air(1019, 79, 1009); w.seen = new HashSet<>();
		check(run(s2k, w, big) == 1 && w.seen.size() == 2000, "2,000 positions: one pass reads them all and finds it");
		check(run(s2k, w, big) == 0, "and the next pass has nothing left to correct");

		System.out.println("-- log limit");
		w = new World(); s = box(w, 20, 15, 1, 0, 0, 0); log.clear(); // 300 cells
		for(int x = 0; x < 20; x++) for(int y = 0; y < 15; y++) w.air(x, y, 0);
		int fixed = StateReconciler.run(s, w, 100000, log::add);
		check(fixed == 300 && log.size() == ValidationState.maxLoggedCorrections + 1 && log.get(log.size() - 1).contains("not"), "300 corrections: " + (log.size() - 1) + " lines + 1 notice that logging stops (" + log.size() + " lines)");
		for(int x = 0; x < 20; x++) for(int y = 0; y < 15; y++) w.solid(x, y, 0);
		StateReconciler.run(s, w, 100000, log::add);
		check(log.size() == ValidationState.maxLoggedCorrections + 1 && s.getReconcileCorrections() == 600, "later corrections are counted (" + s.getReconcileCorrections() + ") but not logged");

		System.out.println("-- idempotence of the update (setBlock hook + server-update hook + net)");
		w = new World(); s = small(w, 100, 64, -200);
		long v0 = s.getVersion(); s.updateBlock(LocalPos.pack(3, 1, 0), false, true, false, "Stone");
		long v1 = s.getVersion(); int c1 = s.getNearCount();
		s.updateBlock(LocalPos.pack(3, 1, 0), false, true, false, "Stone");
		check(c1 == 2 && s.getNearCount() == 2 && v1 > v0 && s.getVersion() == v1, "same solid block reported twice: 2 errors, version bumped once (list and overlay rebuilt once)");
		s.updateBlock(LocalPos.pack(3, 1, 0), true, false, false, null); long v2 = s.getVersion(); s.updateBlock(LocalPos.pack(3, 1, 0), true, false, false, null);
		check(s.getNearCount() == 1 && v2 > v1 && s.getVersion() == v2, "same removal twice: back to 1 error, bumped once");
		long v3 = s.getVersion(); s.updateBlock(LocalPos.pack(1, 0, 0), false, true, false, null); s.updateBlock(LocalPos.pack(1, 0, 0), false, true, false, null);
		check(s.getOk() == 6 && s.getVersion() == v3, "an expected cell told OK twice: counts and version unchanged");

		System.out.println("-- the client model: without and with the second injection (MixinClientLevel.setServerVerifiedBlockState)");
		GhostTest.second = false;
		boolean ghostBefore = GhostTest.runServerRemoval();
		GhostTest.run("D before  A1 U(STONE) A2 U(AIR)  (ack overtakes)", c -> new GhostTest.Msg[]{ GhostTest.A(c,"A1",0,1), GhostTest.U(c,"U(STONE)#1",0,GhostTest.STONE), GhostTest.A(c,"A2",1,2), GhostTest.U(c,"U(AIR)#2",1,GhostTest.AIR) });
		int ghostsBefore = GhostTest.ghosts;
		GhostTest.second = true;
		boolean ghostAfter = GhostTest.runServerRemoval();
		int bad = 0;
		GhostTest.run("D after   same orderings", c -> new GhostTest.Msg[]{ GhostTest.A(c,"A1",0,1), GhostTest.U(c,"U(STONE)#1",0,GhostTest.STONE), GhostTest.A(c,"A2",1,2), GhostTest.U(c,"U(AIR)#2",1,GhostTest.AIR) });
		bad += GhostTest.ghosts + GhostTest.missings;
		GhostTest.run("A after   vanilla order", c -> new GhostTest.Msg[]{ GhostTest.U(c,"U(STONE)#1",0,GhostTest.STONE), GhostTest.A(c,"A1",0,1), GhostTest.U(c,"U(AIR)#2",1,GhostTest.AIR), GhostTest.A(c,"A2",1,2) });
		bad += GhostTest.ghosts + GhostTest.missings;
		GhostTest.run("B after   one tick", c -> new GhostTest.Msg[]{ GhostTest.U(c,"U(AIR)#12",1,GhostTest.AIR), GhostTest.A(c,"A2",1,2) });
		bad += GhostTest.ghosts + GhostTest.missings;
		check(ghostBefore && ghostsBefore == 2, "without the second hook: server removal leaves the ghost, and 2 of 3 stress orderings do");
		check(!ghostAfter && bad == 0, "with the second hook: no ghost and no missing marker in any ordering");
		GhostTest.second = false;

		System.out.println("-- cost of one pass (fake world: a HashMap read per position, the real world read is added on top)");
		for(int size: new int[]{2000, 50000}){
			World cw = new World(); ValidationState cs = size == 2000 ? box(cw, 20, 10, 10, 1000, 70, 1000) : box(cw, 50, 50, 20, 1000, 70, 1000);
			long t0 = System.nanoTime(); cs.getTrackedPositions(); double build = (System.nanoTime() - t0) / 1e6;
			for(int i = 0; i < 5; i++) StateReconciler.run(cs, cw, StateReconciler.checksPerPass, null);
			int reps = 50; t0 = System.nanoTime(); for(int i = 0; i < reps; i++) StateReconciler.run(cs, cw, StateReconciler.checksPerPass, null);
			double perPass = (System.nanoTime() - t0) / 1e6 / reps;
			int checked = Math.min(size, StateReconciler.checksPerPass);
			System.out.println(String.format("     %,6d positions: %.3f ms per pass (%d reads, %.0f ns each), tracked list built once per scan in %.2f ms; whole shape re-read every %.1f s", size, perPass, checked, perPass * 1e6 / checked, build, Math.ceil(size / (double) StateReconciler.checksPerPass) * StateReconciler.intervalMillis / 1000.0));
			check(perPass < 50, size + " positions: a pass stays far below a frame (" + String.format("%.3f", perPass) + " ms)");
		}
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
