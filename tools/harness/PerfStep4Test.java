import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// Performance step 4 (2026-10-20): LongHashSet (the shape's expected set without boxing), the shape keeping its
// blocks in it, the preview snapshot as one copy; the scan stopping inside a layer (a chunk of positions or one
// row per unit) with the same result as at once and no frame much over its budget; the overlay's nearest cubes
// chosen without sorting every entry, the same choice and order as before; the preview's timing line.
public class PerfStep4Test {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long nanos = 0;
	// A world where every read costs 1 us of a fake clock
	static class World implements IBlockProbe {
		final Set<Long> solid; World(Set<Long> s){ solid = s; }
		public boolean isLoaded(int x, int y, int z){ return true; }
		public int flags(int x, int y, int z){ nanos += 1000; return solid.contains(LocalPos.pack(x, y, z)) ? FLAG_SOLID : FLAG_AIR; }
		public String name(int x, int y, int z){ return "Stone"; }
	}
	static String summary(ValidationState s){
		StringBuilder b = new StringBuilder("ok=" + s.getOk() + " missing=" + s.getMissing() + " ignored=" + s.getIgnored() + " near=" + s.getNearCount() + " v=" + s.getVersion());
		for(byte st: new byte[]{ValidationState.OK, ValidationState.MISSING, ValidationState.IGNORED}) b.append(" ").append(st).append(":").append(s.getPositions(st));
		for(NearBlock nb: s.getNearBlocks()) b.append(" n").append(nb.localPos).append("/").append(nb.distance);
		return b.toString();
	}

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(PerfStep4Test.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgperf4").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(PerfStep4Test.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add;

		System.out.println("-- LongHashSet");
		LongHashSet set = new LongHashSet(); Set<Long> ref = new HashSet<>(); List<Long> order = new ArrayList<>(); Random r = new Random(4);
		boolean agree = true;
		for(int k = 0; k < 200000; k++){ long q = LocalPos.pack(r.nextInt(200) - 100, r.nextInt(100) - 50, r.nextInt(200) - 100); boolean x = set.add(q), y = ref.add(q); if(y) order.add(q); if(x != y) agree = false; }
		check(agree && set.size() == ref.size() && set.equals(ref) && ref.equals(set) && set.hashCode() == ref.hashCode(), "200,000 adds with repeats: the same answers, size, equals and hashCode as a HashSet (" + set.size() + ")");
		boolean contains = true; for(int k = 0; k < 50000; k++){ long q = LocalPos.pack(r.nextInt(220) - 110, r.nextInt(120) - 60, r.nextInt(220) - 110); if(set.contains(q) != ref.contains(q) || set.contains((Object) q) != ref.contains((Object) q)) contains = false; }
		check(contains && !set.contains("x"), "contains, primitive and boxed, the same as the HashSet");
		List<Long> iterated = new ArrayList<>(set); long[] arr = set.toLongArray(); boolean same = arr.length == order.size(); for(int k = 0; same && k < arr.length; k++) same = arr[k] == order.get(k) && set.get(k) == arr[k];
		check(iterated.equals(order) && same, "iteration, get(k) and toLongArray in the order the positions were added");
		set.add(-1L); check(set.contains(-1L) && !set.add(-1L) && set.size() == ref.size() + 1, "-1 (the free mark) is still a value that can be held");
		int before = set.size(); set.clear(); check(set.isEmpty() && !set.contains(order.get(0)) && !set.contains(-1L) && set.toLongArray().length == 0, "clear: empty (" + before + " before)");
		set.add(7); set.add(3); set.add(7); check(set.size() == 2 && set.get(0) == 7 && set.get(1) == 3, "reused after clear");
		try { set.iterator().remove(); check(false, "no removal"); } catch(UnsupportedOperationException | IllegalStateException e){ check(true, "no removal through the iterator"); }

		System.out.println("-- the shape's expected set and the preview snapshot");
		ShapeIsland island = FocusWorldTest.place(new ShapeIsland()); island.update(); FocusWorldTest.settle(island);
		Set<Long> exp = island.getExpectedBlocks();
		check(exp instanceof LongHashSet && exp.size() == island.getNumberOfBlocks() && exp.equals(FocusWorldTest.current(island).shape()), "the shape keeps its blocks in a LongHashSet: the blocks it emitted (" + exp.size() + ")");
		PreviewModel snap = PreviewModel.snapshot(island);
		check(snap != null && Arrays.equals(snap.positions, ((LongHashSet) exp).toLongArray()), "the preview snapshot is one copy of it, in its order");
		PreviewModel boxed = PreviewModel.of(new ArrayList<>(exp));
		check(Arrays.equals(boxed.positions, snap.positions), "a boxed collection still gives the same positions");

		System.out.println("-- the scan stops inside a layer: same result, budget kept");
		int sameCount = 0, cases = 0; long worstOver = 0; int maxRowCells = 0;
		for(int k = 0; k < 12; k++){
			Params p = new Params(); p.outline = Outline.ORGANIC; p.widthX = 15 + r.nextInt(30); p.widthZ = 15 + r.nextInt(30); p.depth = 6 + r.nextInt(10); p.wall = 1 + r.nextInt(2); p.seed = r.nextInt(1000);
			Set<Long> shape = new LinkedHashSet<>(); IslandGeometry.enumerate(p, (x, y, z) -> shape.add(LocalPos.pack(x, y, z)));
			Set<Long> solid = new HashSet<>(); for(long q: shape) if(r.nextInt(3) == 0) solid.add(q);
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
			for(long q: shape){ minX = Math.min(minX, LocalPos.unpackX(q)); maxX = Math.max(maxX, LocalPos.unpackX(q)); minZ = Math.min(minZ, LocalPos.unpackZ(q)); maxZ = Math.max(maxZ, LocalPos.unpackZ(q)); minY = Math.min(minY, LocalPos.unpackY(q)); }
			for(int x = minX - 2; x <= maxX + 2; x++) for(int z = minZ - 2; z <= maxZ + 2; z++) for(int y = minY - 2; y < minY + 2; y++){ long q = LocalPos.pack(x, y, z); if(!shape.contains(q)) solid.add(q); }
			maxRowCells = Math.max(maxRowCells, maxZ - minZ + 5);
			World w = new World(solid); List<Long> list = new ArrayList<>(shape);
			ValidationState once = new ValidationState(); once.requestScan(0); once.consumeScanRequest();
			SliceScan a1 = SliceScan.prepare(once, list, 0, 0, 0, 1, once.getScanEpoch(), null, new ArrayList<>()); a1.step(w, 0, () -> nanos); a1.publish(w);
			ValidationState sliced = new ValidationState(); sliced.requestScan(0); sliced.consumeScanRequest();
			SliceScan a2 = SliceScan.prepare(sliced, list, 0, 0, 0, 1, sliced.getScanEpoch(), null, new ArrayList<>());
			long budget = 200000; // 200 reads of the fake clock
			while(true){ long t0 = nanos; boolean done = a2.step(w, budget, () -> nanos); worstOver = Math.max(worstOver, nanos - t0 - budget); if(done) break; }
			a2.publish(w);
			cases++; if(summary(once).equals(summary(sliced))) sameCount++;
		}
		check(sameCount == cases, "12 random islands, scanned at once and 200 reads per frame: the same state, lists and versions (" + sameCount + " of " + cases + ")");
		// A frame stops after the unit that crossed the budget: at most one more row (or chunk of positions) of reads
		long unitReads = Math.max(maxRowCells, SliceScan.classifyChunk);
		check(worstOver <= unitReads * 1000, "no frame goes over its budget by more than one unit: worst " + worstOver / 1000 + " reads over, a unit at most " + unitReads);

		System.out.println("-- overlay: the nearest cubes without sorting them all");
		Method nf = ValidationOverlay.class.getDeclaredMethod("nearestFirst", long[].class, int.class, ShapeSet.Origin.class); nf.setAccessible(true);
		int agreeSel = 0;
		for(int k = 0; k < 20; k++){
			int n = ValidationOverlay.maxCubes + 1 + r.nextInt(12000), spread = 25 + r.nextInt(40); // room for n distinct positions (4 spread^3 >= 62,500)
			LinkedHashSet<Long> uniq = new LinkedHashSet<>(); while(uniq.size() < n) uniq.add(LocalPos.pack(r.nextInt(spread * 2) - spread, r.nextInt(spread) - spread / 2, r.nextInt(spread * 2) - spread));
			long[] pos = new long[n]; int i = 0; for(long q: uniq) pos[i++] = q;
			ShapeSet.Origin pl = new ShapeSet.Origin(r.nextInt(10), r.nextInt(10), r.nextInt(10));
			int[] got = (int[]) nf.invoke(null, pos, n, pl);
			// The old way: every entry sorted by distance then position, the first maxCubes
			Integer[] all = new Integer[n]; for(int e = 0; e < n; e++) all[e] = e;
			final long[] fpos = pos;
			Arrays.sort(all, Comparator.<Integer>comparingLong(e -> { long dx = LocalPos.unpackX(fpos[e]) - pl.x, dy = LocalPos.unpackY(fpos[e]) - pl.y, dz = LocalPos.unpackZ(fpos[e]) - pl.z; return dx * dx + dy * dy + dz * dz; }).thenComparingLong(e -> fpos[e]));
			boolean eq = got.length == ValidationOverlay.maxCubes; for(int e = 0; eq && e < got.length; e++) eq = got[e] == all[e];
			if(eq) agreeSel++;
		}
		check(agreeSel == 20, "20 random sets over the cap (dense, many ties): the same cubes in the same order as sorting every entry (" + agreeSel + " of 20)");
		int[] few = (int[]) nf.invoke(null, new long[]{5, 9, 2}, 3, new ShapeSet.Origin(0, 0, 0));
		check(Arrays.equals(few, new int[]{0, 1, 2}), "under the cap: every entry in its order");

		System.out.println("-- timing line of a preview sent over frames");
		lines.clear(); TimingLog.recordPreview(9, 30, 41, 50000, "generation", 4, 2900000L);
		check(lines.size() == 1 && lines.get(0).contains("ms=9 build=30 wait=41 blocks=50000 reason=generation") && lines.get(0).endsWith(" frames=4 maxframe=2.9"), "frames= and maxframe= at the end: " + (lines.isEmpty() ? "" : lines.get(0)));
		lines.clear(); TimingLog.recordPreview(3, 14, 20, 26491, "validation");
		check(lines.size() == 1 && !lines.get(0).contains("frames="), "the old line unchanged without frames");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
