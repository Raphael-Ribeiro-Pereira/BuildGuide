import java.util.*;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// The validation scan in Y layers (SliceScan) against the scan as it was (LEGACY below: the algorithm of
// RenderHandler.validateShape before slicing, over the same fake world): identical state in random worlds
// with errors, structure errors and exclusions; blocks changed mid-scan; cancellation; the time budget.
public class SliceScanTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// A fake world in world coordinates; every read advances the fake nano clock
	static long nanos = 0;
	static class World implements IBlockProbe {
		final Map<Long, Integer> flags = new HashMap<>(); final Map<Long, String> names = new HashMap<>(); long reads = 0; long cost = 1000;
		public boolean isLoaded(int x, int y, int z){ return true; }
		public int flags(int x, int y, int z){ reads++; nanos += cost; Integer f = flags.get(LocalPos.pack(x, y, z)); return f == null ? FLAG_AIR : f; }
		public String name(int x, int y, int z){ String n = names.get(LocalPos.pack(x, y, z)); return n == null ? "Air" : n; }
		void set(int x, int y, int z, int f, String name){ long k = LocalPos.pack(x, y, z); if(f == FLAG_AIR){ flags.remove(k); names.remove(k); } else { flags.put(k, f); names.put(k, name); } }
		World copy(){ World w = new World(); w.flags.putAll(flags); w.names.putAll(names); return w; }
	}
	static final int SOLID = IBlockProbe.FLAG_SOLID, IGN_SOLID = IBlockProbe.FLAG_SOLID | IBlockProbe.FLAG_IGNORED, IGN_SOFT = IBlockProbe.FLAG_IGNORED, SOFT = 0;

	// The scan before slicing (RenderHandler.validateShape at 89F6B80A), on a fake world
	static void legacy(ValidationState state, Collection<Long> shapeExpected, List<Long> tracked, int ox, int oy, int oz, IBlockProbe w){
		Set<Long> exp = new HashSet<>(tracked); int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for(long p: tracked){ int x = LocalPos.unpackX(p), y = LocalPos.unpackY(p), z = LocalPos.unpackZ(p); minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); }
		state.consumeScanRequest(); state.beginScan(shapeExpected, ox, oy, oz);
		for(long p: tracked){ int f = w.flags(LocalPos.unpackX(p) + ox, LocalPos.unpackY(p) + oy, LocalPos.unpackZ(p) + oz); boolean air = (f & IBlockProbe.FLAG_AIR) != 0;
			if(!air && (f & IBlockProbe.FLAG_IGNORED) != 0) state.setStatus(p, ValidationState.IGNORED, w.name(LocalPos.unpackX(p) + ox, LocalPos.unpackY(p) + oy, LocalPos.unpackZ(p) + oz));
			else if(!air && (f & IBlockProbe.FLAG_SOLID) != 0) state.setStatus(p, ValidationState.OK, null); else state.setStatus(p, ValidationState.MISSING, null); }
		List<NearBlock> near = new ArrayList<>();
		for(int x = minX - 2; x <= maxX + 2; x++) for(int y = minY - 2; y <= maxY + 2; y++) for(int z = minZ - 2; z <= maxZ + 2; z++){
			if(exp.contains(LocalPos.pack(x, y, z))) continue; if(state.isExcluded(x, y, z)) continue;
			int f = w.flags(x + ox, y + oy, z + oz); if((f & IBlockProbe.FLAG_AIR) != 0 || (f & IBlockProbe.FLAG_SOLID) == 0 || (f & IBlockProbe.FLAG_IGNORED) != 0) continue;
			double best = Double.MAX_VALUE; for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++) for(int dz = -2; dz <= 2; dz++){ int d2 = dx * dx + dy * dy + dz * dz; if(d2 == 0 || d2 > 4) continue; if(exp.contains(LocalPos.pack(x + dx, y + dy, z + dz))){ double d = Math.sqrt(d2); if(d < best) best = d; } }
			if(best <= 2.0) near.add(new NearBlock(LocalPos.pack(x, y, z), w.name(x + ox, y + oy, z + oz), (float) best)); }
		state.setNearBlocks(near); state.endScan();
	}
	// Everything a reader can see of a state
	static String snapshot(ValidationState s){
		StringBuilder b = new StringBuilder();
		b.append(s.isValidated()).append('|').append(s.getVersion()).append('|').append(s.getOk()).append('/').append(s.getMissing()).append('/').append(s.getIgnored()).append('/').append(s.getTotal());
		b.append('|').append(s.getScanOriginX()).append(',').append(s.getScanOriginY()).append(',').append(s.getScanOriginZ());
		for(byte st: new byte[]{ValidationState.OK, ValidationState.MISSING, ValidationState.IGNORED}) b.append('|').append(s.getPositions(st));
		for(long p: s.getPositions(ValidationState.IGNORED)) b.append(';').append(s.getIgnoredBlockName(p));
		for(NearBlock n: s.getNearBlocks()) b.append('#').append(n.localPos).append(n.blockName).append(n.distance);
		return b.toString();
	}

	// A random case: an island shell or a blob as the shape, a world with errors around it, exclusion boxes
	static class Case { List<Long> shape = new ArrayList<>(); List<Long> tracked = new ArrayList<>(); List<int[]> boxes = new ArrayList<>(); World world = new World(); int ox, oy, oz; }
	static Case randomCase(long seed){
		Random r = new Random(seed); Case c = new Case(); c.ox = r.nextInt(2000) - 1000; c.oy = r.nextInt(200) - 60; c.oz = r.nextInt(2000) - 1000;
		Set<Long> shape = new LinkedHashSet<>();
		if(seed % 2 == 0){ Params p = new Params(); p.outline = Outline.values()[r.nextInt(4)]; p.profile = Profile.values()[r.nextInt(3)]; p.widthX = 11 + r.nextInt(30); p.widthZ = 11 + r.nextInt(30); p.depth = 4 + r.nextInt(20); p.wall = 1 + r.nextInt(3); p.seed = seed;
			try { IslandGeometry.enumerate(p, (x, y, z) -> shape.add(LocalPos.pack(x, y, z))); } catch(InterruptedException e){ throw new RuntimeException(e); } }
		else for(int i = 0; i < 400 + r.nextInt(800); i++) shape.add(LocalPos.pack(r.nextInt(25) - 12, r.nextInt(15) - 7, r.nextInt(25) - 12));
		c.shape.addAll(new HashSet<>(shape)); // a HashSet's order, as Shape.expectedBlocks
		int boxes = r.nextInt(3); for(int i = 0; i < boxes; i++){ int x = r.nextInt(20) - 10, y = r.nextInt(10) - 8, z = r.nextInt(20) - 10; c.boxes.add(new int[]{x, y, z, x + r.nextInt(6), y + r.nextInt(4), z + r.nextInt(6)}); }
		ValidationState probe = new ValidationState(); probe.setExclusionBoxes(c.boxes);
		List<Long> tracked = new ArrayList<>(); for(long p: c.shape) if(!probe.isExcluded(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p))) tracked.add(p);
		Collections.shuffle(tracked, r); c.tracked = tracked; // the loader's order (a HashSet of world longs in the game)
		for(long p: c.shape){ int x = LocalPos.unpackX(p) + c.ox, y = LocalPos.unpackY(p) + c.oy, z = LocalPos.unpackZ(p) + c.oz; int k = r.nextInt(100);
			if(k < 70) c.world.set(x, y, z, SOLID, "Stone"); else if(k < 80) { } else if(k < 85) c.world.set(x, y, z, SOFT, "Torch"); else if(k < 92) c.world.set(x, y, z, IGN_SOLID, "Scaffolding"); else c.world.set(x, y, z, IGN_SOFT, "Ladder"); }
		for(long p: c.shape) for(int i = 0; i < 2; i++){ int x = LocalPos.unpackX(p) + r.nextInt(5) - 2 + c.ox, y = LocalPos.unpackY(p) + r.nextInt(5) - 2 + c.oy, z = LocalPos.unpackZ(p) + r.nextInt(5) - 2 + c.oz;
			if(shape.contains(LocalPos.pack(x - c.ox, y - c.oy, z - c.oz))) continue; int k = r.nextInt(100); if(k < 4) c.world.set(x, y, z, SOLID, "Dirt"); else if(k < 5) c.world.set(x, y, z, IGN_SOLID, "Scaffolding"); else if(k < 6) c.world.set(x, y, z, SOFT, "Grass"); }
		return c;
	}
	static ValidationState fresh(Case c){ ValidationState s = new ValidationState(); s.setExclusionBoxes(c.boxes); s.requestScan(0); return s; }
	static String legacyOn(Case c, World w){ ValidationState s = fresh(c); legacy(s, c.shape, c.tracked, c.ox, c.oy, c.oz, w); return snapshot(s); }
	static String slicedOn(Case c, World w, long budget){ ValidationState s = fresh(c); s.consumeScanRequest(); SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job.step(w, budget, () -> nanos)); job.publish(w); return snapshot(s); }

	public static void main(String[] a) throws Exception {
		System.out.println("-- sliced = instantaneous = the scan as it was");
		int same = 0, instantSame = 0, frames = 0, cases = 60;
		for(long seed = 1; seed <= cases; seed++){ Case c = randomCase(seed); String ref = legacyOn(c, c.world);
			if(slicedOn(c, c.world, 0).equals(ref)) instantSame++;
			ValidationState s = fresh(c); s.consumeScanRequest(); SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job.step(c.world, 30000, () -> nanos)); job.publish(c.world); frames += job.getFrames();
			if(snapshot(s).equals(ref)) same++; }
		check(instantSame == cases, "Instant (one frame): the same state as before slicing, in " + instantSame + " of " + cases + " random worlds (statuses, list orders, ignored names, structure errors, version, origin)");
		check(same == cases && frames > cases * 3, "sliced over " + frames + " frames: the same state, " + same + " of " + cases);

		System.out.println("-- blocks changed during the scan");
		int midSame = 0, midCases = 0;
		for(long seed = 101; seed <= 140; seed++){ Case c = randomCase(seed); World w = c.world.copy(); Random r = new Random(seed);
			ValidationState s = fresh(c); s.consumeScanRequest(); SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1);
			int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE; for(long p: c.tracked){ minY = Math.min(minY, LocalPos.unpackY(p)); maxY = Math.max(maxY, LocalPos.unpackY(p)); }
			job.step(w, 1, () -> nanos); job.step(w, 1, () -> nanos); job.step(w, 1, () -> nanos); // three layers from the bottom are done
			if(job.isDone()) continue; midCases++;
			List<long[]> changes = new ArrayList<>();
			for(long p: c.tracked){ int y = LocalPos.unpackY(p); if((y == minY || y == maxY) && r.nextInt(3) == 0) changes.add(new long[]{p, 0}); }
			for(long p: c.tracked){ if(r.nextInt(15) != 0) continue; int x = LocalPos.unpackX(p) + 1, y = LocalPos.unpackY(p), z = LocalPos.unpackZ(p); if(!c.shape.contains(LocalPos.pack(x, y, z))) changes.add(new long[]{LocalPos.pack(x, y, z), 1}); }
			for(long[] ch: changes){ int x = LocalPos.unpackX(ch[0]) + c.ox, y = LocalPos.unpackY(ch[0]) + c.oy, z = LocalPos.unpackZ(ch[0]) + c.oz; int k = r.nextInt(4);
				w.set(x, y, z, k == 0 ? IBlockProbe.FLAG_AIR : k == 1 ? SOLID : k == 2 ? IGN_SOLID : SOFT, k == 1 ? "Cobblestone" : k == 2 ? "Scaffolding" : "Torch"); job.markDirty(x, y, z); }
			while(!job.step(w, 30000, () -> nanos)); job.publish(w);
			if(snapshot(s).equals(legacyOn(c, w))) midSame++; }
		check(midSame == midCases && midCases > 20, "blocks changed in layers already scanned and not yet scanned (expected and around): the result equals an instantaneous scan of the final world, " + midSame + " of " + midCases);

		System.out.println("-- totals kept until the end; cancellation");
		Case c = randomCase(7); ValidationState s = fresh(c); legacy(s, c.shape, c.tracked, c.ox, c.oy, c.oz, c.world); String before = snapshot(s); long v = s.getVersion();
		s.requestScan(0); s.consumeScanRequest(); SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 4);
		job.step(c.world, 1, () -> nanos); job.step(c.world, 1, () -> nanos);
		check(s.getVersion() == v && s.isValidated() && job.percent() > 0 && job.percent() < 100, "during the scan the state keeps the previous totals (version " + v + " unchanged), progress " + job.percent() + " %");
		check(!job.isStale(s, 4), "not stale while nothing changed");
		s.setStatus(c.tracked.get(0), ValidationState.MISSING, null); s.updateBlock(c.tracked.get(1), true, false, false, null);
		check(!job.isStale(s, 4), "block events (incremental validation) do not make it stale");
		check(job.isStale(s, 5), "a new generation makes it stale");
		s.requestScan(0); check(job.isStale(s, 4), "a new scan request (origin, exclusions, ignored blocks) makes it stale");
		SliceScan job2 = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 4); s.invalidate(); check(job2.isStale(s, 4), "an invalidation makes it stale");
		check(job.isStale(new ValidationState(), 4), "another state (another shape) makes it stale");

		System.out.println("-- time budget (fake clock: 1 us per block read)");
		Case big = randomCase(8); ValidationState sb = fresh(big); sb.consumeScanRequest(); SliceScan bj = new SliceScan(sb, big.shape, big.tracked, big.ox, big.oy, big.oz, 1);
		int layers = 0; { int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE; for(long p: big.tracked){ mn = Math.min(mn, LocalPos.unpackY(p)); mx = Math.max(mx, LocalPos.unpackY(p)); } layers = mx - mn + 1 + 4; }
		int steps = 0; while(!bj.step(big.world, 1, () -> nanos)) steps++; steps++;
		check(steps == layers, "a 1 ns budget still makes progress: exactly 1 layer per frame (" + steps + " frames for " + layers + " layers)");
		ValidationState sc = fresh(big); sc.consumeScanRequest(); SliceScan cj = new SliceScan(sc, big.shape, big.tracked, big.ox, big.oy, big.oz, 1);
		// The most one layer can cost here: every cell of the expanded box plus the expected positions of the busiest layer, 1 us each
		int bx0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz0 = Integer.MAX_VALUE, bz1 = Integer.MIN_VALUE; Map<Integer, Integer> perY = new HashMap<>();
		for(long p: big.tracked){ bx0 = Math.min(bx0, LocalPos.unpackX(p)); bx1 = Math.max(bx1, LocalPos.unpackX(p)); bz0 = Math.min(bz0, LocalPos.unpackZ(p)); bz1 = Math.max(bz1, LocalPos.unpackZ(p)); perY.merge(LocalPos.unpackY(p), 1, Integer::sum); }
		long layerMax = ((long) (bx1 - bx0 + 5) * (bz1 - bz0 + 5) + Collections.max(perY.values())) * 1000; long budget = 3 * layerMax; boolean within = true; int cf = 0;
		while(true){ long t0 = nanos; boolean done = cj.step(big.world, budget, () -> nanos); long spent = nanos - t0; cf++; if(!done) within &= spent >= budget && spent < budget + layerMax; if(done) break; }
		check(within && cf > 1, "a budget of about 3 layers (" + budget / 1000 + " us): every frame but the last stops at the first layer boundary past it, never a whole layer later (" + cf + " frames)");
		ValidationState si = fresh(big); si.consumeScanRequest(); SliceScan ij = new SliceScan(si, big.shape, big.tracked, big.ox, big.oy, big.oz, 1);
		check(ij.step(big.world, SliceScan.Speed.INSTANT.budgetMillis * 1000000L, () -> nanos) && ij.getFrames() == 1, "Instant: the whole scan in one frame");
		check(SliceScan.Speed.FAST.budgetMillis == 12 && SliceScan.Speed.NORMAL.budgetMillis == 6 && SliceScan.Speed.SLOW.budgetMillis == 3 && SliceScan.Speed.INSTANT.budgetMillis == 0, "budgets: Fast 12 ms, Normal 6 ms, Slow 3 ms, Instant unlimited");

		System.out.println("-- timing line");
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add; TimingLog.recordScan(183, 31, 6, 30172);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=scan ms=183 slices=31 maxslice=6 blocks=30172")), "phase=scan ms=<total> slices=<n> maxslice=<ms> (blocks kept for comparison): " + lines);

		System.out.println("-- first scan vs repeated (investigation, pure Java part only)");
		Params pp = new Params(); pp.outline = Outline.ORGANIC; pp.widthX = 81; pp.widthZ = 81; pp.depth = 40; pp.wall = 2; Set<Long> isl = new HashSet<>(); IslandGeometry.enumerate(pp, (x, y, z) -> isl.add(LocalPos.pack(x, y, z)));
		World iw = new World(); iw.cost = 0; for(long p: isl) iw.set(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p), SOLID, "Stone"); List<Long> it = new ArrayList<>(isl);
		long[] times = new long[5]; for(int i = 0; i < 5; i++){ ValidationState q = new ValidationState(); q.requestScan(0); q.consumeScanRequest(); long t0 = System.nanoTime(); SliceScan qj = new SliceScan(q, isl, it, 0, 0, 0, 1); qj.step(iw, 0, System::nanoTime); qj.publish(iw); times[i] = (System.nanoTime() - t0) / 1000000; }
		System.out.println("   " + isl.size() + " blocks, five scans in a row: " + Arrays.toString(times) + " ms (first vs the rest: the JIT warming up)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
