import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.screen.*;
import brentmaas.buildguide.common.shape.*;
// Preview performance (2026-10-07 timing log): the preview rebuilt its mesh twice per change, the
// first time over the old model, because a generation starts by invalidating the validation state
// and the colour refresh recoloured the old geometry. Frames are simulated as PreviewRenderer does
// them: a mesh is rebuilt only when PreviewModel.meshChange(built, shown) gives a reason.
public class PreviewRebuildTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long now = 1000;
	static Method finish; static Field fe;
	static PreviewController c; static PreviewModel built = null, shown = null; static List<String> rebuilds = new ArrayList<>();

	@SuppressWarnings("unchecked")
	static void generate(Shape s, long... blocks) throws Exception {
		s.lock.lock(); try { Set<Long> e = (Set<Long>) fe.get(s); e.clear(); for(long b: blocks) e.add(b); s.ready = false; s.error = false; finish.invoke(s); } finally { s.lock.unlock(); }
	}
	// What Shape.update / doUpdate do before the new blocks exist: not ready, validation invalidated
	static void startGeneration(Shape s){ s.lock.lock(); try { s.ready = false; s.getValidationState().invalidate(); } finally { s.lock.unlock(); } }
	// One frame every 16 ms for `millis`
	static void frames(Shape s, long millis){
		for(long t = 0; t < millis; t += 16){ now += 16; PreviewModel m = c.update(s); if(m == null) continue; shown = m; String r = PreviewModel.meshChange(built, m); if(r != null) rebuilds.add(r); built = m; }
	}
	static void scan(Shape s, long... blocks){ ValidationState vs = s.getValidationState(); List<Long> exp = new ArrayList<>(); for(long b: blocks) exp.add(b); vs.beginScan(exp); for(long b: blocks) vs.setStatus(b, ValidationState.OK, null); vs.endScan(); }

	public static void main(String[] a) throws Exception {
		finish = Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		fe = Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true);
		c = new PreviewController(() -> now);
		ShapeBridge s = new ShapeBridge();
		long p0 = LocalPos.pack(0, 0, 0), p1 = LocalPos.pack(1, 0, 0), p2 = LocalPos.pack(2, 0, 0);
		generate(s, p0, p1); frames(s, 300); scan(s, p0, p1); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("view", "validation")), "setup: first mesh (view), then the scan's colours (validation): " + rebuilds);

		System.out.println("-- same key");
		rebuilds.clear(); frames(s, 2000);
		check(rebuilds.isEmpty(), "2 s of frames with nothing changed: no rebuild");
		PreviewModel same = built.withValidation(s.getValidationState());
		check(same != built && PreviewModel.meshChange(built, same) == null, "a new instance with the same generation, view and validation version: no rebuild");

		System.out.println("-- one change = one generation rebuild, never over the old model");
		rebuilds.clear(); PreviewModel before = built;
		startGeneration(s); frames(s, 500);
		check(rebuilds.isEmpty() && shown.positions == before.positions && shown.status != null, "while regenerating (state invalidated): no rebuild, the current mesh stays with its colours");
		generate(s, p0, p1, p2); frames(s, 400);
		check(rebuilds.equals(Arrays.asList("generation")), "generation finished: exactly one rebuild, reason generation: " + rebuilds);
		check(shown.positions.length == 3 && shown.generation == s.getGeneration(), "the preview shows the NEW model (3 blocks, generation " + shown.generation + ")");
		rebuilds.clear(); scan(s, p0, p1, p2); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("validation")), "its scan lands: one rebuild, reason validation (colours depend on the scan, by design): " + rebuilds);
		// A quick generation that finishes inside the geometry throttle: the colour refresh must not recolour the old model meanwhile
		rebuilds.clear(); startGeneration(s); generate(s, p0); frames(s, 400);
		check(rebuilds.equals(Arrays.asList("generation")) && shown.positions.length == 1, "generation finished inside the 250 ms throttle: still one rebuild, on the new model: " + rebuilds);

		System.out.println("-- filter and slice");
		rebuilds.clear(); c.setFilter(c.getFilter().next()); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("filter")), "filter change: one rebuild, reason filter: " + rebuilds);
		rebuilds.clear(); c.setSlice(1, 0); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("slice")), "slice on: one rebuild, reason slice: " + rebuilds);
		rebuilds.clear(); c.setSlice(1, 0); frames(s, 300);
		check(rebuilds.isEmpty(), "same slice again: no rebuild");
		rebuilds.clear(); c.setSlice(1, 1); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("slice")), "slice moved: one rebuild: " + rebuilds);
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
