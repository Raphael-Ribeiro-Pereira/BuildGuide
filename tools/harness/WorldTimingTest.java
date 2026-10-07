import java.util.*;
import brentmaas.buildguide.common.AbstractRenderHandler;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.WorldUpdateGate;
import brentmaas.buildguide.common.WorldUpdateGate.Mode;
import brentmaas.buildguide.common.shape.*;
// Measuring the world update (2026-10-07): the frame that applies a new buffer is timed every time it
// runs, split into world-end, world-close and world-other, with the reason that let it through, and
// written above 4 ms. The scan line gains the two single-frame steps outside its layers (start, publish).
// The deferred path runs here with fake buffers whose end() and close() take a known time, on an
// invisible set (nothing is drawn, scanned or picked: world-other is only the bookkeeping)
public class WorldTimingTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static void busy(double ms){ long t = System.nanoTime(); while(System.nanoTime() - t < ms * 1e6); }
	static List<String> lines = new ArrayList<>();
	static void reset() throws Exception { lines.clear(); TimingLog.sink = lines::add; java.lang.reflect.Method r = TimingLog.class.getDeclaredMethod("reset"); r.setAccessible(true); r.invoke(null); }

	static class TimedBuffer implements IShapeBuffer {
		final double endMs, closeMs; boolean ended = false, closed = false;
		TimedBuffer(double endMs, double closeMs){ this.endMs = endMs; this.closeMs = closeMs; }
		public void setColour(int r, int g, int b, int a){}
		public void pushVertex(double x, double y, double z){}
		public void end(){ busy(endMs); ended = true; }
		public void close(){ busy(closeMs); closed = true; }
	}
	static class Handler extends AbstractRenderHandler {
		Mode mode = Mode.ON_CLOSE; boolean menuOpen = true;
		public void register(){}
		public void renderShapeBuffer(Shape shape){}
		protected void setupRenderingShapeSet(ShapeSet shape){}
		protected void endRenderingShapeSet(){}
		protected void pushProfiler(String key){}
		protected void popProfiler(){}
		@Override protected boolean deferredWorldUpdates(){ return true; }
		@Override protected boolean isMenuOpen(){ return menuOpen; }
		@Override protected Mode worldUpdateMode(){ return mode; }
		void frame(ShapeSet s){ renderShapeSet(s); }
	}
	// A finished generation waiting for the gate: its buffer is new, the world still draws the old one
	static TimedBuffer generated(Shape shape, double endMs, double closeMs){ TimedBuffer b = new TimedBuffer(endMs, closeMs); shape.buffer = b; shape.ready = true; shape.error = false; shape.worldGate.request(); return b; }

	public static void main(String[] a) throws Exception {
		System.out.println("-- TimingLog.recordWorld");
		reset();
		TimingLog.recordWorld("close", 5200000, 300000, 1100000, 49568);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=world-buffer ms=7 blocks=49568 reason=close world-end=5.2 world-close=0.3 world-other=1.1")), "format: ms (the sum), blocks, reason, the three parts in ms with one decimal: " + lines);
		reset(); TimingLog.recordWorld("apply", 3000000, 500000, 500000, 10);
		check(lines.isEmpty() && TimingLog.worldThresholdMillis == 4, "4.0 ms in total: not written (threshold 4 ms, not the 8 of the other phases)");
		TimingLog.recordWorld("apply", 3000000, 500000, 600000, 10);
		check(lines.size() == 1 && lines.get(0).contains(" ms=4 ") && lines.get(0).contains("reason=apply"), "4.1 ms: written: " + lines);
		reset(); TimingLog.record(TimingLog.SCAN, 6, 10);
		check(lines.isEmpty(), "the other phases keep their 8 ms threshold");

		System.out.println("-- WorldUpdateGate.reason");
		check(WorldUpdateGate.reason(Mode.LIVE, true).equals("apply") && WorldUpdateGate.reason(Mode.IDLE, true).equals("idle"), "menu open: Live -> apply, Idle -> idle");
		check(WorldUpdateGate.reason(Mode.LIVE, false).equals("close") && WorldUpdateGate.reason(Mode.IDLE, false).equals("close") && WorldUpdateGate.reason(Mode.ON_CLOSE, false).equals("close"), "menu closed: close in every mode");

		System.out.println("-- the scan line: start and publish");
		reset(); TimingLog.recordScan(36, 10, 4, 49568, 21, 15);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=scan ms=36 slices=10 maxslice=4 blocks=49568 start=21 publish=15")), "start= and publish= after blocks=: " + lines);
		reset(); TimingLog.recordScan(3, 1, 3, 500, 4, 2);
		check(lines.size() == 1, "written when layers + start + publish pass 8 ms (3 + 4 + 2)");
		reset(); TimingLog.recordScan(2, 1, 2, 500, 3, 3);
		check(lines.isEmpty(), "not written at 8 ms in total (2 + 3 + 3)");
		reset(); TimingLog.recordScan(183, 31, 6, 30172);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=scan ms=183 slices=31 maxslice=6 blocks=30172")), "the 4-value form is unchanged: " + lines);
		SliceScan scan = new SliceScan(new ValidationState(), Arrays.asList(LocalPos.pack(0, 0, 0)), Arrays.asList(LocalPos.pack(0, 0, 0)), 0, 0, 0, 1);
		check(scan.getStartMillis() == -1, "a SliceScan whose start was not measured reports -1");
		scan.setStartNanos(12345678);
		check(scan.getStartMillis() == 12, "12 345 678 ns -> start=12");

		System.out.println("-- the deferred path (AbstractRenderHandler.renderShapeSetDeferred)");
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		Shape shape = new ShapeCuboid();
		ShapeSet set = OriginScanTest.newSet(new Shape[]{shape}, 0, 64, 0); // Unsafe instance: invisible
		Handler h = new Handler();
		TimedBuffer old = generated(shape, 0, 1.5); h.menuOpen = false; reset(); h.frame(set);
		// close() is timed on the buffer being replaced: old's 1.5 ms shows in world-close when next comes in
		TimedBuffer next = generated(shape, 5, 1.5); h.mode = Mode.ON_CLOSE; h.menuOpen = true; reset();
		h.frame(set); h.frame(set);
		check(shape.shownBuffer == old && !next.ended && lines.isEmpty(), "On close, menu open: the world keeps the old buffer, nothing timed");
		h.menuOpen = false; h.frame(set);
		check(shape.shownBuffer == next && next.ended && old.closed && !shape.worldGate.isPending(), "menu closed: the new buffer is sent, the old one closed");
		String line = lines.size() == 1 ? lines.get(0) : "";
		double end = part(line, "world-end"), close = part(line, "world-close"), other = part(line, "world-other");
		check(line.startsWith("[Build Guide] timing: phase=world-buffer ms=") && line.contains(" blocks=0 reason=close "), "one line, reason=close: " + line);
		check(end >= 5.0 && end < 9 && close >= 1.5 && close < 5 && other < 2, "world-end " + end + " (end() took 5 ms), world-close " + close + " (close() 1.5 ms), world-other " + other);
		h.frame(set); h.frame(set);
		check(lines.size() == 1, "the frames after it: no more lines");
		TimedBuffer third = generated(shape, 4.5, 0); h.mode = Mode.LIVE; h.menuOpen = true; h.frame(set);
		check(shape.shownBuffer == third && lines.size() == 2 && lines.get(1).contains("reason=apply"), "Live with the menu open: applied, reason=apply: " + lines.get(lines.size() - 1));
		generated(shape, 0, 0); h.frame(set);
		check(lines.size() == 2, "a quick update (under 4 ms): applied, not written");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
	static double part(String line, String key){ for(String w: line.split(" ")) if(w.startsWith(key + "=")) return Double.parseDouble(w.substring(key.length() + 1)); return -1; }
}
