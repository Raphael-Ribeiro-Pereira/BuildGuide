import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.LongPredicate;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.FocusFilter.Part;
// Build focus in the world, on the real generation executor: the world buffer gets only the blocks in focus (a
// recording buffer), the preview and the expected set all of them; a focus change makes only the world buffer
// again, through the WorldUpdateGate, without a generation, without touching the validation or starting a
// scan; the threshold; a generation with a focus selected; shapes without parts (All, Hidden); the overlay keeps
// near and ignored positions and filters the shape's own states; the timing line's focus=<n>/<total>.
public class FocusWorldTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// Records the block of every cube (24 vertices each; the cube is inside its block, so the floor of its corner)
	static final List<RecBuffer> buffers = Collections.synchronizedList(new ArrayList<>());
	static class RecBuffer implements IShapeBuffer {
		final List<Long> cubes = new ArrayList<>(); double mx, my, mz; int v = 0; int released = 0;
		RecBuffer(){ buffers.add(this); }
		public void setColour(int r, int g, int b, int a){}
		public void pushVertex(double x, double y, double z){
			if(v % 24 == 0){ mx = x; my = y; mz = z; } else { mx = Math.min(mx, x); my = Math.min(my, y); mz = Math.min(mz, z); }
			if(++v % 24 == 0) cubes.add(LocalPos.pack((int) Math.floor(mx + 1e-6), (int) Math.floor(my + 1e-6), (int) Math.floor(mz + 1e-6)));
		}
		public void end(){} public void close(){}
		@Override public void releaseVertexData(){ released++; }
		// The shape's cubes: all but the last one (the origin cube)
		Set<Long> shape(){ return new HashSet<>(cubes.subList(0, cubes.size() - 1)); }
	}
	static void settle(Shape s) throws Exception {
		long stable = 0;
		for(int i = 0; i < 1000 && stable < 60; i++){ Thread.sleep(5); stable = !s.lock.isLocked() && !s.lock.hasQueuedThreads() && s.ready && !s.isRefocusing() ? stable + 5 : 0; }
	}
	static RecBuffer current(Shape s){ return (RecBuffer) s.buffer; }
	static Set<Long> select(Shape s, FocusFilter f){ Set<Long> out = new HashSet<>(); for(long p: s.getExpectedBlocks()) if(s.isInFocus(f, LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p))) out.add(p); return out; }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	static <T extends Shape> T place(T s) throws Exception { ShapeSet set = OriginScanTest.newSet(new Shape[]{s}, 0, 64, 0); Field f = Shape.class.getDeclaredField("shapeSet"); f.setAccessible(true); f.set(s, set); return s; }

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FocusWorldTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgfocusw").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(FocusWorldTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};
		BuildGuide.config.focusFrom.value = 0;

		System.out.println("-- the world buffer: only the blocks in focus");
		ShapeIsland island = place(new ShapeIsland());
		setProp(island, "propertySpikes", 10); setProp(island, "propertyLayers", true);
		island.update(); settle(island);
		Set<Long> expected = new HashSet<>(island.getExpectedBlocks()); int total = expected.size(); long generation = island.getGeneration();
		check(current(island).shape().equals(expected) && island.getWorldFocus() == null && island.getWorldBlocks() == total, "All, All: the world buffer has every block (" + total + "), no focus");
		// A validated state, as a finished scan leaves it
		ValidationState state = island.getValidationState(); state.consumeScanRequest();
		List<Long> sorted = new ArrayList<>(expected); Collections.sort(sorted);
		state.beginScan(sorted); for(long p: sorted) state.setStatus(p, ValidationState.MISSING, "Air"); state.endScan();
		long version = state.getVersion(), epoch = state.getScanEpoch(); int validated = state.getTotal();
		island.worldGate.applied();
		island.setFocus(Part.CAP, 0); settle(island);
		Set<Long> cap = select(island, new FocusFilter(Part.CAP, 0));
		check(!cap.isEmpty() && cap.size() < total && current(island).shape().equals(cap) && island.getWorldBlocks() == cap.size(), "Cap: the world buffer has exactly the cap's blocks (" + cap.size() + " of " + total + ")");
		check(island.getGeneration() == generation && island.getExpectedBlocks().equals(expected) && island.getNumberOfBlocks() == total, "no new generation: the same expected set and totals");
		check(state.getVersion() == version && state.getScanEpoch() == epoch && state.isValidated() && state.getTotal() == validated && !state.isScanRequested(), "the validation untouched: same version and scan epoch, still validated, no scan asked for");
		check(island.worldGate.isPending(), "through the WorldUpdateGate, like a shape change");
		PreviewModel model = PreviewModel.snapshot(island);
		check(model != null && model.positions.length == total, "the preview still gets every block (" + (model == null ? "-" : "" + model.positions.length) + ")");
		island.setFocus(Part.BODY, 2); settle(island);
		Set<Long> body2 = select(island, new FocusFilter(Part.BODY, 2)); boolean both = true;
		for(long p: body2) both &= island.partOf(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) == Part.BODY && island.getLayers().layerAt(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) == 2;
		check(!body2.isEmpty() && both && current(island).shape().equals(body2), "Body x Layer 2: the intersection (" + body2.size() + ")");
		island.setFocus(Part.SPIKES, 0); settle(island);
		Set<Long> spikes = select(island, new FocusFilter(Part.SPIKES, 0));
		check(!spikes.isEmpty() && current(island).shape().equals(spikes), "Spikes: only the spikes (" + spikes.size() + ")");
		island.setFocus(Part.HIDDEN, 0); settle(island);
		check(current(island).cubes.size() == 1 && island.getWorldBlocks() == 0, "Hidden: nothing of the shape (the origin cube only)");
		island.setFocus(Part.ALL, 0); settle(island);
		check(current(island).shape().equals(expected) && island.getWorldFocus() == null, "All, All again: every block");
		check(island.getGeneration() == generation && state.getVersion() == version && !state.isScanRequested(), "after five focus changes: still the same generation and validation");
		int before = buffers.size(); island.setFocus(Part.ALL, 0); settle(island);
		check(buffers.size() == before, "a change that does not change what the world shows makes no buffer");

		System.out.println("-- the threshold");
		BuildGuide.config.focusFrom.value = total + 1;
		island.setFocus(Part.CAP, 0); settle(island);
		check(island.getWorldFocus() == null && island.getWorldBlocks() == total, "below N (" + total + " < " + (total + 1) + "): the world shows everything");
		BuildGuide.config.focusFrom.value = total;
		island.setFocus(Part.CAP, 1); settle(island); island.setFocus(Part.CAP, 0); settle(island);
		check(island.getWorldFocus() != null && island.getWorldBlocks() == cap.size(), "at N: the focus applies");

		System.out.println("-- a generation with a focus selected");
		BuildGuide.config.focusFrom.value = 0;
		island.update(); settle(island);
		check(current(island).shape().equals(cap) && island.getExpectedBlocks().equals(expected) && island.getGeneration() == generation + 1, "the generation emits only the cap to the world buffer, and still expects every block");
		BuildGuide.config.focusFrom.value = 1000000;
		island.update(); settle(island);
		check(current(island).shape().equals(expected) && island.getWorldFocus() == null, "the same generation below the threshold: the world buffer is made again with every block");
		BuildGuide.config.focusFrom.value = 0;
		setProp(island, "propertyLayers", false); island.setFocus(Part.ALL, 3); island.update(); settle(island);
		check(current(island).shape().equals(expected) && island.getWorldFocus() == null, "a layer selected with Layers off: everything (the layer selector needs layers)");

		System.out.println("-- shapes without parts");
		ShapeCuboid cube = place(new ShapeCuboid());
		cube.update(); settle(cube); int cubeBlocks = cube.getExpectedBlocks().size();
		cube.setFocus(Part.HIDDEN, 0); settle(cube);
		check(current(cube).cubes.size() == 1 && cube.getExpectedBlocks().size() == cubeBlocks, "Cuboid, Hidden: nothing in the world, every block still expected");
		cube.setFocus(Part.CAP, 0); settle(cube);
		check(current(cube).shape().equals(new HashSet<>(cube.getExpectedBlocks())) && cube.getWorldFocus() == null, "a part it has not (Cap) counts as All");

		System.out.println("-- the overlay");
		ValidationState vs = new ValidationState();
		long onShape = LocalPos.pack(0, 0, 0), wrongL = LocalPos.pack(1, 0, 0), ign = LocalPos.pack(2, 0, 0), nearPos = LocalPos.pack(5, 5, 5);
		vs.beginScan(Arrays.asList(onShape, wrongL, ign)); vs.setStatus(onShape, ValidationState.WRONG_BLOCK, "Dirt"); vs.setStatus(wrongL, ValidationState.WRONG_LAYER, "Stone"); vs.setStatus(ign, ValidationState.IGNORED, "Scaffolding");
		vs.setNearBlocks(Arrays.asList(new ValidationState.NearBlock(nearPos, "Stone", 1f))); vs.endScan();
		RecBuffer all = new RecBuffer(); ValidationOverlay.build(all, vs, null);
		RecBuffer none = new RecBuffer(); ValidationOverlay.build(none, vs, null, p -> false);
		RecBuffer some = new RecBuffer(); ValidationOverlay.build(some, vs, null, p -> p == wrongL);
		check(all.cubes.size() == 4 && none.cubes.size() == 2, "out of focus: wrong block and wrong layer go, the ignored position and the near block stay (" + all.cubes.size() + " -> " + none.cubes.size() + ")");
		check(some.cubes.size() == 3, "in focus: the wrong layer block is drawn");
		vs.setHighlightedPos(onShape); RecBuffer hl = new RecBuffer(); ValidationOverlay.build(hl, vs, null, p -> false);
		check(hl.cubes.size() == 3, "the position picked in the list is drawn even out of focus");
		island.setFocus(Part.CAP, 0); island.update(); settle(island);
		LongPredicate test = island.worldFocusTest(); long capPos = cap.iterator().next(); long other = -1; for(long p: expected) if(!cap.contains(p)){ other = p; break; }
		check(test != null && test.test(capPos) && !test.test(other), "the shape's test follows its world buffer (in the cap: yes, elsewhere: no)");
		island.setFocus(Part.ALL, 0); settle(island);
		check(island.worldFocusTest() == null, "and is null without a focus (no filter at all)");

		System.out.println("-- the timing line");
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add; Method reset = TimingLog.class.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(null);
		TimingLog.recordWorld("apply", 6000000, 0, 0, 107000, 30123);
		check(lines.size() == 1 && lines.get(0).startsWith("[Build Guide] timing: phase=world-buffer ms=6 blocks=107000 reason=apply") && lines.get(0).endsWith(" focus=30123/107000"), "world-buffer ... focus=<n>/<total>: " + lines);
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
