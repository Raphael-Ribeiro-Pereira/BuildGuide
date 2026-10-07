import java.util.*;
import java.util.function.LongSupplier;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.WorldUpdateGate;
import brentmaas.buildguide.common.WorldUpdateGate.Mode;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.*;
// Live apply: text fields apply by themselves (FieldDebounce, 500 ms after the last keystroke or on
// focus loss) and the world part of a change waits for the WorldUpdateGate (Live, Idle, On close).
// Both run on an injected clock. The fields are fakes that behave like TextFieldImpl: typing goes
// through the debounce, setTextValue does not, Enter and the debounce run the property's own action.
public class LiveApplyTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long now = 0;
	static final LongSupplier clock = () -> now;

	// A text field like TextFieldImpl, on the test clock
	static class LiveField implements ITextField {
		String text = ""; int colour = 0xFFFFFF; Runnable onEnter; final FieldDebounce debounce = new FieldDebounce(clock);
		public void setTextValue(String t){ text = t; debounce.onProgrammaticSet(t); }
		public void setTextColour(int c){ colour = c; }
		public String getTextValue(){ return text; }
		public void setOnEnter(Runnable r){ onEnter = r; }
		public void markApplied(){ debounce.markApplied(text); }
		public void setYPosition(int y){}
		public void setVisibility(boolean v){}
		void type(String t){ text = t; debounce.onUserEdit(t); }
		void enter(){ debounce.markApplied(text); if(onEnter != null) onEnter.run(); }
		void focusLost(){ if(onEnter != null && debounce.onFocusLost(text)) onEnter.run(); }
		void tick(){ if(onEnter != null && debounce.poll(text)) onEnter.run(); }
	}
	static final List<LiveField> fields = new ArrayList<>();
	static class Widgets extends PresetTest.FakeWidgets {
		@Override public ITextField createTextField(int x,int y,int w,int h,String v){ LiveField f = new LiveField(); f.text = v; fields.add(f); return f; }
	}
	static LiveField fieldOf(Property<?> p){ fields.clear(); p.getWidgetList(); return fields.get(0); }
	// Advance the clock to `until`, ticking the fields every 50 ms (a client tick)
	static void run(long until, LiveField... fs){ while(now < until){ now += 50; for(LiveField f: fs) f.tick(); } }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new Widgets();

		System.out.println("-- FieldDebounce");
		now = 0; FieldDebounce d = new FieldDebounce(clock); d.onProgrammaticSet("4"); int fired = 0; long firedAt = -1;
		for(long t = 0; t <= 900; t += 50){ now = t; if(t == 0) d.onUserEdit("1"); if(t == 200) d.onUserEdit("12"); if(t == 400) d.onUserEdit("123"); if(d.poll("123")){ fired++; firedAt = t; } }
		check(fired == 1 && firedAt == 900, "keys at 0, 200 and 400 ms: applied once, at 900 ms (" + fired + " at " + firedAt + ")");
		now = 0; d = new FieldDebounce(clock); d.onProgrammaticSet("4"); d.onUserEdit("7"); now = 10;
		check(d.onFocusLost("7") && !d.poll("7"), "focus lost 10 ms after typing: applied at once, and not again by the pause");
		now = 0; d = new FieldDebounce(clock); d.onProgrammaticSet("4"); d.onUserEdit("5"); now = 100; d.onUserEdit("4"); now = 2000;
		check(!d.poll("4") && !d.onFocusLost("4"), "typed back to the applied value: nothing applied");
		now = 0; d = new FieldDebounce(clock); d.onProgrammaticSet("4"); now = 100; d.onProgrammaticSet("9"); now = 2000;
		check(!d.poll("9") && !d.onFocusLost("9"), "a change made by code (setTextValue) never applies");
		now = 0; d = new FieldDebounce(clock); d.onProgrammaticSet("4"); d.onUserEdit("6"); d.markApplied("6"); now = 2000;
		check(!d.poll("6"), "Enter (markApplied) already applied it: the pause does not apply it again");

		System.out.println("-- through a property's own action");
		int[] presses = {0};
		PropertyRangeInt range = new PropertyRangeInt(4, new Translatable("x"), () -> presses[0]++, 1, 10);
		LiveField rf = fieldOf(range); now = 0;
		rf.type("7"); run(400, rf);
		check(range.value == 4 && presses[0] == 0, "400 ms after the keystroke: not applied yet");
		run(600, rf);
		check(range.value == 7 && presses[0] == 1, "after 500 ms: value 7, one regeneration");
		rf.type("abc"); run(1200, rf);
		check(range.value == 7 && presses[0] == 1 && rf.colour == 0xFF0000, "invalid text: not applied, field red");
		rf.type("42"); run(1800, rf);
		check(range.value == 7 && presses[0] == 1 && rf.colour == 0xFF0000, "out of range (42 > 10): not applied, field red");
		rf.type("7"); run(2400, rf);
		check(presses[0] == 2 && range.value == 7 && rf.colour == 0xFFFFFF, "typing 7 again after a red value: applied (the field turns white again)");
		rf.type("8"); rf.focusLost();
		check(range.value == 8 && presses[0] == 3, "focus lost: applied at once (8)");
		rf.type("9"); rf.enter(); run(4000, rf);
		check(range.value == 9 && presses[0] == 4, "Enter stays immediate and the pause adds nothing");
		range.setValue(3); run(5000, rf);
		check(range.value == 3 && presses[0] == 4, "setValue by code: shown in the field, never applied by the pause");

		System.out.println("-- point row: three fields, one regeneration");
		int[] rowUpdates = {0};
		PropertyCompactInt px = new PropertyCompactInt(1, new Translatable("x"), null, 0), py = new PropertyCompactInt(2, new Translatable("y"), null, 1), pz = new PropertyCompactInt(3, new Translatable("z"), null, 2);
		LiveField fx = fieldOf(px), fy = fieldOf(py), fz = fieldOf(pz);
		PropertyPointRow row = new PropertyPointRow(new Translatable("p"), px, py, pz, () -> rowUpdates[0]++, () -> new int[]{0, 0, 0});
		row.getWidgetList(); // the screen builds the row's widgets, which wires its three fields to one commit
		now = 10000; fx.type("11"); now += 100; fy.type("22"); run(now + 1000, fx, fy, fz);
		check(px.value == 11 && py.value == 22 && pz.value == 3 && rowUpdates[0] == 1, "X and Y typed: both applied with one regeneration (" + rowUpdates[0] + ")");
		fz.type("33"); fz.focusLost(); run(now + 1000, fx, fy, fz);
		check(pz.value == 33 && rowUpdates[0] == 2, "Z on focus loss: one more regeneration, X and Y not again");

		System.out.println("-- WorldUpdateGate");
		now = 0; WorldUpdateGate g = new WorldUpdateGate(clock); int applied = 0;
		for(int i = 0; i < 3; i++){ g.request(); if(g.shouldApply(Mode.LIVE, true)){ g.applied(); applied++; } now += 100; }
		check(applied == 3 && !g.isPending(), "Live: every request applies at once (3 of 3)");
		now = 0; g = new WorldUpdateGate(clock); applied = 0; long appliedAt = -1;
		for(long t = 0; t <= 3000; t += 50){ now = t; if(t % 200 == 0 && t <= 800) g.request(); if(g.shouldApply(Mode.IDLE, true)){ g.applied(); applied++; appliedAt = t; } }
		check(applied == 1 && appliedAt == 1800, "Idle: 5 requests (0..800 ms) coalesce into 1, applied 1 s after the last (" + applied + " at " + appliedAt + ")");
		now = 0; g = new WorldUpdateGate(clock); g.request(); now = 5000;
		boolean waited = !g.shouldApply(Mode.ON_CLOSE, true) && g.isPending();
		check(waited && g.shouldApply(Mode.ON_CLOSE, false), "On close: nothing while the menu is open (even 5 s later), applies when it closes");
		now = 0; g = new WorldUpdateGate(clock); g.request(); now = 300;
		check(!g.shouldApply(Mode.IDLE, true) && g.shouldApply(Mode.IDLE, false), "Idle: closing the menu applies at once (300 ms after the change)");
		now = 0; g = new WorldUpdateGate(clock); g.request(); now = 900; g.request(); now = 1500;
		check(!g.shouldApply(Mode.IDLE, true), "a new request replaces the pending one: the wait restarts (nothing at 1500 ms)");
		now = 1900;
		check(g.shouldApply(Mode.IDLE, true), "... and it applies 1 s after the newer request (1900 ms)");
		g.applied();
		check(!g.isPending() && !g.shouldApply(Mode.LIVE, true), "after applying: not pending, nothing more to apply");
		check(!new WorldUpdateGate(clock).isPending(), "a new gate is not pending");

		System.out.println("-- TimingLog");
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add;
		TimingLog.record(TimingLog.SCAN, 8, 10); TimingLog.record(TimingLog.SCAN, 9, 10);
		check(lines.size() == 1 && lines.get(0).equals("[Build Guide] timing: phase=scan ms=9 blocks=10"), "only phases above 8 ms, in the agreed format: " + lines);
		for(int i = 0; i < 300; i++) TimingLog.record(TimingLog.GENERATION, 20, i);
		check(lines.size() == TimingLog.maxLines && lines.get(lines.size() - 1).contains("limit of 200") && TimingLog.maxLines == 200, "at most 200 lines per session, the last one says so (" + lines.size() + ")");
		List<String> withReason = new ArrayList<>(); TimingLog.sink = withReason::add; java.lang.reflect.Method reset = TimingLog.class.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(null);
		TimingLog.record(TimingLog.PREVIEW_REBUILD, 29, 49568, "generation");
		check(withReason.equals(Arrays.asList("[Build Guide] timing: phase=preview-rebuild ms=29 blocks=49568 reason=generation")), "preview line carries reason=: " + withReason);
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
