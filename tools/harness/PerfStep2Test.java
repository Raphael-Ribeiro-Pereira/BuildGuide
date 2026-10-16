import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.shape.*;
// Performance step 2 (2026-10-16, wip/perf2), the single-frame costs the fill-wip log showed: the scan's
// preparation off the render thread (SliceScan.prepare from a copy, in the shape's order: the same state as the
// scan as it was), layers without boxing (LongIndex, the bitmap, the index when the box is too big), publish by
// swapping the map in (publishScan: the same version and lists), the preview reading statuses under one lock and
// rebuilding only when a status or an error changed, and the timing lines' new fields.
public class PerfStep2Test {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	public static void main(String[] a) throws Exception {
		System.out.println("-- LongIndex");
		LongIndex ix = new LongIndex(5);
		check(ix.add(LocalPos.pack(1, 2, 3)) == 0 && ix.add(LocalPos.pack(-1, -2, -3)) == 1 && ix.add(LocalPos.pack(1, 2, 3)) == 0 && ix.size() == 2, "numbers keys in the order they come, a key twice keeps its number");
		check(ix.indexOf(LocalPos.pack(-1, -2, -3)) == 1 && ix.indexOf(LocalPos.pack(9, 9, 9)) == -1 && ix.contains(LocalPos.pack(1, 2, 3)) && !ix.contains(0), "indexOf and contains");
		LongIndex big = new LongIndex(100000); Random r = new Random(1); Map<Long, Integer> ref = new HashMap<>(); boolean same = true;
		for(int i = 0; i < 100000; i++){ long k = LocalPos.pack(r.nextInt(400) - 200, r.nextInt(400) - 200, r.nextInt(400) - 200); int got = big.add(k); Integer want = ref.putIfAbsent(k, ref.size()); same &= got == (want == null ? ref.size() - 1 : want); }
		for(Map.Entry<Long, Integer> e: ref.entrySet()) same &= big.indexOf(e.getKey()) == e.getValue();
		check(same && big.size() == ref.size(), "100 000 random positions: the same numbers as a HashMap would give (" + ref.size() + " distinct)");
		boolean full = false; LongIndex tiny = new LongIndex(1); try { for(int i = 0; i < 10; i++) tiny.add(i); } catch(IllegalStateException e){ full = true; }
		check(full, "past its capacity it says so instead of looping");

		System.out.println("-- SliceScan.prepare: the loader's scan, built from a copy");
		int equal = 0, cases = 40;
		for(long seed = 1; seed <= cases; seed++){
			SliceScanTest.Case c = SliceScanTest.randomCase(seed);
			// The loader's order now: the shape's own order, minus the excluded positions
			ValidationState probe = new ValidationState(); probe.setExclusionBoxes(c.boxes);
			List<Long> tracked = new ArrayList<>(); for(long p: c.shape) if(!probe.isExcluded(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p))) tracked.add(p);
			c.tracked = tracked;
			String ref2 = SliceScanTest.legacyOn(c, c.world);
			ValidationState s = SliceScanTest.fresh(c); s.consumeScanRequest();
			SliceScan job = SliceScan.prepare(s, new ArrayList<>(c.shape), c.ox, c.oy, c.oz, 1, s.getScanEpoch(), s.getPalette(), s.getExclusionBoxes());
			while(!job.step(c.world, 30000, () -> SliceScanTest.nanos)); job.publish(c.world);
			if(SliceScanTest.snapshot(s).equals(ref2) && job.getPrepMillis() >= 0) equal++;
		}
		check(equal == cases, "prepare (in the shape's order, exclusions applied there) gives the state of the scan as it was: " + equal + " of " + cases + " random worlds, version and list orders included");
		SliceScanTest.Case c = SliceScanTest.randomCase(3);
		ValidationState s = SliceScanTest.fresh(c);
		List<int[]> all = new ArrayList<>(); all.add(new int[]{-100, -100, -100, 100, 100, 100});
		check(SliceScan.prepare(s, new ArrayList<>(c.shape), 0, 0, 0, 1, s.getScanEpoch(), null, all) == null, "every position excluded: no scan (null)");
		SliceScan b = SliceScan.prepare(s, Arrays.asList(LocalPos.pack(-2, 3, 4), LocalPos.pack(5, -1, 0)), 100, 64, -50, 1, s.getScanEpoch(), null, new ArrayList<>());
		check(Arrays.equals(b.worldBounds(), new int[]{98, 63, -50, 105, 67, -46}), "worldBounds: the tracked box in world coordinates, for the chunk check: " + Arrays.toString(b.worldBounds()));
		check(new SliceScan(s, Arrays.asList(LocalPos.pack(0, 0, 0)), Arrays.asList(LocalPos.pack(0, 0, 0)), 0, 0, 0, 1).getPrepMillis() == -1, "a scan built on the caller's thread has no prep time (-1)");
		// The bitmap of the box (z fastest) or, for a box too big for it, the index: both answer alike
		Field bm = SliceScan.class.getDeclaredField("bitmap"); bm.setAccessible(true);
		List<Long> far = Arrays.asList(LocalPos.pack(0, 0, 0), LocalPos.pack(20000, 0, 20000));
		check(bm.get(new SliceScan(new ValidationState(), far, far, 0, 0, 0, 1)) == null && 20005L * 5 * 20005 > SliceScan.maxBitmapBits, "two blocks 20 000 apart (a box of 2 billion cells): no bitmap, the index answers");
		SliceScanTest.Case oc = SliceScanTest.randomCase(41);
		check(bm.get(new SliceScan(new ValidationState(), oc.shape, oc.tracked, oc.ox, oc.oy, oc.oz, 1)) != null, "an ordinary shape has the bitmap");
		int indexEqual = 0;
		for(long seed = 41; seed <= 50; seed++){
			SliceScanTest.Case ic = SliceScanTest.randomCase(seed); ValidationState is = SliceScanTest.fresh(ic); is.consumeScanRequest();
			SliceScan ij = new SliceScan(is, ic.shape, ic.tracked, ic.ox, ic.oy, ic.oz, 1);
			bm.set(ij, null); // the index's path, on the same case
			while(!ij.step(ic.world, 0, () -> SliceScanTest.nanos)); ij.publish(ic.world);
			if(SliceScanTest.snapshot(is).equals(SliceScanTest.legacyOn(ic, ic.world))) indexEqual++;
		}
		check(indexEqual == 10, "without the bitmap (the index answering): the same state as the scan as it was, " + indexEqual + " of 10");

		System.out.println("-- ValidationState: getStatuses, setExclusionBoxes with the same boxes");
		ValidationState v = new ValidationState(); List<Long> exp = new ArrayList<>(); for(int i = 0; i < 50; i++) exp.add(LocalPos.pack(i, 0, 0));
		v.beginScan(exp); for(int i = 0; i < 50; i++) v.setStatus(exp.get(i), i % 3 == 0 ? ValidationState.OK : ValidationState.MISSING, null); v.endScan();
		long[] q = new long[60]; for(int i = 0; i < 60; i++) q[i] = LocalPos.pack(i, 0, 0); byte[] out = new byte[60]; v.getStatuses(q, out); boolean eq = true;
		for(int i = 0; i < 60; i++) eq &= out[i] == v.getStatus(q[i]);
		check(eq && out[55] == ValidationState.UNKNOWN, "getStatuses: getStatus for every position under one lock (untracked: UNKNOWN)");
		List<int[]> boxes = new ArrayList<>(); boxes.add(new int[]{0, 0, 0, 4, 0, 0});
		v.setExclusionBoxes(boxes); long version = v.getVersion();
		check(v.getTotal() == 45 && version > 0, "new boxes exclude what they cover (45 of 50 left)");
		v.setExclusionBoxes(boxes);
		check(v.getTotal() == 45 && v.getVersion() == version, "the same boxes again (every scan start sets them): nothing to do, the version unchanged");

		System.out.println("-- the preview: a validation version that changes no colour keeps the mesh");
		brentmaas.buildguide.common.BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		ShapeCuboid shape = FocusWorldTest.place(new ShapeCuboid());
		Field fe = Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true); Method finish = Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		@SuppressWarnings("unchecked") Set<Long> e = (Set<Long>) fe.get(shape); long p0 = LocalPos.pack(0, 0, 0), p1 = LocalPos.pack(1, 0, 0);
		shape.lock.lock(); try { e.add(p0); e.add(p1); finish.invoke(shape); } finally { shape.lock.unlock(); }
		ValidationState vs = shape.getValidationState(); vs.beginScan(Arrays.asList(p0, p1)); vs.setStatus(p0, ValidationState.OK, null); vs.setStatus(p1, ValidationState.MISSING, null); vs.endScan();
		PreviewModel built = PreviewModel.snapshot(shape).withValidation(vs);
		vs.updateBlock(LocalPos.pack(0, 1, 0), false, true, false, "Grass Block"); // a structure error appears
		PreviewModel withError = built.withValidation(vs);
		check("validation".equals(PreviewModel.meshChange(built, withError)), "a new structure error: rebuild (validation)");
		vs.updateBlock(LocalPos.pack(0, 1, 0), false, true, false, "Dirt"); // the same error, another block name: the version moves
		PreviewModel renamed = withError.withValidation(vs);
		check(renamed.stateVersion != withError.stateVersion && PreviewModel.meshChange(withError, renamed) == null, "the grass on it turned to dirt (version moved, nothing the mesh shows changed): no rebuild");
		vs.setStatus(p1, ValidationState.OK, null);
		check("validation".equals(PreviewModel.meshChange(renamed, renamed.withValidation(vs))), "a status that changes: rebuild");

		System.out.println("-- timing lines");
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add; Method reset = TimingLog.class.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(null);
		TimingLog.recordScan(40, 9, 5, 59717, 0, 3, 6);
		check(lines.size() == 1 && lines.get(0).equals("[Build Guide] timing: phase=scan ms=40 slices=9 maxslice=5 blocks=59717 start=0 publish=3 prep=6"), "scan: prep= (the worker's time) last: " + lines);
		lines.clear(); TimingLog.recordScan(183, 31, 6, 30172, 21, 15);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=scan ms=183 slices=31 maxslice=6 blocks=30172 start=21 publish=15")), "the old form without prep=: unchanged");
		lines.clear(); TimingLog.recordPreview(3, 14, 20, 26491, "validation");
		check(lines.size() == 1 && lines.get(0).startsWith("[Build Guide] timing: phase=preview-rebuild ms=3 build=14 wait=20 blocks=26491 reason=validation native="), "preview-rebuild: ms= the render thread, build= the worker, wait= until shown: " + lines);
		lines.clear(); TimingLog.recordPreview(2, 6, 9, 100, "view");
		check(lines.isEmpty(), "under 8 ms of render + build: not written");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
