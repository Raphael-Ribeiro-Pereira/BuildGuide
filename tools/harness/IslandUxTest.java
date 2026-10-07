import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.screen.widget.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandControls.Control;
import brentmaas.buildguide.common.shape.IslandControls.Values;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
// Island clarity: neutral defaults, controls per Outline, Randomize / Naturalize / Undo, the 0-100
// percent fields stepping by 5, and persistence of a 15-value Island saved by block A. Widgets come
// from FloatStepTest's fake (keeps the buttons' actions); update() only counts regenerations.
public class IslandUxTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }

	static Object get(Object o, String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
	static void set(Object o, String f, Object v) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); x.set(o, v); }
	static Object call(Object o, String m) throws Exception { Method x = ShapeIsland.class.getDeclaredMethod(m); x.setAccessible(true); return x.invoke(o); }
	static Values values(ShapeIsland s) throws Exception { return (Values) call(s, "values"); }
	static TestIsland island(long seed) throws Exception { TestIsland s = new TestIsland(); set(s, "random", new Random(seed)); return s; }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) get(s, f)).value = v; }

	static boolean inLimits(Values v){
		return v.widthX >= IslandGeometry.minWidth && v.widthX <= IslandGeometry.maxWidth && v.widthZ >= IslandGeometry.minWidth && v.widthZ <= IslandGeometry.maxWidth
			&& v.sides >= IslandGeometry.minSides && v.sides <= IslandGeometry.maxSides && v.depth >= 0 && v.depth <= IslandGeometry.maxDepth
			&& v.cornerRound >= 0 && v.cornerRound <= 1 && v.wobble >= 0 && v.wobble <= 1 && v.sharpness >= 0 && v.sharpness <= 1 && v.roughness >= 0 && v.roughness <= 1
			&& v.rotation >= IslandControls.rotationMin && v.rotation <= IslandControls.rotationMax && v.wobbleSize >= IslandControls.wobbleSizeMin && v.wobbleSize <= IslandControls.wobbleSizeMax
			&& v.seed >= 0 && v.seed < IslandControls.seedBound;
	}
	// Values read as clean decimals in the fields (on their grids)
	static boolean clean(Values v){
		for(float f: new float[]{v.cornerRound, v.wobble, v.sharpness, v.roughness}) if(Float.toString(f).length() > 4) return false;
		return Float.toString(v.rotation).endsWith(".0") && (Float.toString(v.wobbleSize).endsWith(".0") || Float.toString(v.wobbleSize).endsWith(".5"));
	}
	static boolean baseSame(Values a, Values b){ return a.widthX == b.widthX && a.widthZ == b.widthZ && a.sides == b.sides && a.cornerRound == b.cornerRound && a.rotation == b.rotation && a.wobble == b.wobble && a.wobbleSize == b.wobbleSize; }
	static boolean bodySame(Values a, Values b){ return a.depth == b.depth && a.sharpness == b.sharpness && a.roughness == b.roughness; }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new FloatStepTest.Widgets();

		System.out.println("-- neutral defaults (new instance)");
		ShapeIsland n = new TestIsland(); Values d = values(n);
		check(d.outline == Outline.CIRCLE && d.widthX == 41 && d.widthZ == 41 && d.rotation == 0 && d.wobble == 0 && d.roughness == 0 && d.profile == Profile.BOWL && d.sharpness == 0 && d.depth == 24 && d.seed == 1 && ((PropertyRangeInt) get(n, "propertyWall")).value == 2,
			"Circle 41 x 41, rotation 0, Wobble 0, Roughness 0, Bowl, Sharpness 0, Thickness 2, Depth 24, Seed 1");
		Params p = (Params) call(n, "params"); Set<Long> s = new HashSet<>(); IslandGeometry.enumerate(p, (x, y, z) -> s.add(LocalPos.pack(x, y, z)));
		boolean mirror = true; for(long k: s){ int x = LocalPos.unpackX(k), y = LocalPos.unpackY(k), z = LocalPos.unpackZ(k); mirror &= s.contains(LocalPos.pack(-x, y, z)) && s.contains(LocalPos.pack(x, y, -z)); }
		check(mirror && !s.isEmpty(), "default island (" + s.size() + " blocks): mirror symmetric in X and in Z");

		int bp = ((PropertyRangeInt) get(n, "propertyBasePercent")).value, bd = ((PropertyRangeInt) get(n, "propertyBodyPercent")).value;
		check(bp == 15 && bd == 15, "Base % and Body % default to 15 (" + bp + ", " + bd + ")");
		TestIsland rs = new TestIsland(); rs.captureDefaults(); /* as ShapeSet does right after construction */ setProp(rs, "propertyBasePercent", 60); setProp(rs, "propertyBodyPercent", 80); rs.setOpenSection(rs.getSectionCount() - 1); /* Random is the last section (Base, Body, Spikes, Spike shape, Random since spikes 2) */ rs.resetShownToDefaults();
		check(((PropertyRangeInt) get(rs, "propertyBasePercent")).value == 15 && ((PropertyRangeInt) get(rs, "propertyBodyPercent")).value == 15, "Reset on the Random section: Base % and Body % back to 15");
		ShapeIsland saved = new TestIsland(); saved.restorePersistence("CIRCLE,41,41,6,0.7,0.0,0.0,6.0,2,24,BOWL,0.0,0.0,1,Runnable,30,45,Runnable,Runnable");
		check(saved.toPersistence().contains(",Runnable,30,45,Runnable,Runnable,"), "a saved island keeps its own percentages (30, 45)");

		System.out.println("-- controls per Outline");
		for(Outline o: Outline.values()){
			StringBuilder shown = new StringBuilder();
			for(Control c: Control.values()) if(IslandControls.applies(o, c)) shown.append(" ").append(c);
			System.out.println("   " + o + ": OUTLINE" + shown);
		}
		check(IslandControls.applies(Outline.POLYGON, Control.SIDES) && !IslandControls.applies(Outline.CIRCLE, Control.SIDES) && !IslandControls.applies(Outline.SQUARE, Control.SIDES) && !IslandControls.applies(Outline.ORGANIC, Control.SIDES), "Sides: Polygon only");
		check(!IslandControls.applies(Outline.CIRCLE, Control.CORNER_ROUND) && IslandControls.applies(Outline.SQUARE, Control.CORNER_ROUND) && IslandControls.applies(Outline.POLYGON, Control.CORNER_ROUND) && IslandControls.applies(Outline.ORGANIC, Control.CORNER_ROUND), "Corner round: Square, Polygon and Organic (IslandGeometry.edge uses it there), not Circle");
		boolean wob = true; for(Outline o: Outline.values()) wob &= IslandControls.applies(o, Control.WOBBLE) == (o == Outline.ORGANIC) && IslandControls.applies(o, Control.WOBBLE_SIZE) == (o == Outline.ORGANIC);
		check(wob, "Wobble and Wobble size: Organic only");
		// The hidden controls really do not change the geometry of that Outline
		for(Outline o: Outline.values()){
			Params base = new Params(); base.outline = o; base.widthX = 31; base.widthZ = 25; base.roughness = 0; base.depth = 8;
			Params moved = new Params(); moved.outline = o; moved.widthX = 31; moved.widthZ = 25; moved.roughness = 0; moved.depth = 8;
			if(!IslandControls.applies(o, Control.SIDES)) moved.sides = 11;
			if(!IslandControls.applies(o, Control.CORNER_ROUND)) moved.roundness = 0.1;
			if(!IslandControls.applies(o, Control.WOBBLE)) moved.edgeAmplitude = 0.9;
			if(!IslandControls.applies(o, Control.WOBBLE_SIZE)) moved.edgeScale = 20;
			check(IslandTest.set(IslandTest.gen(base)).equals(IslandTest.set(IslandTest.gen(moved))), o + ": changing its hidden controls leaves the blocks unchanged");
		}
		ShapeIsland v = new TestIsland(); updates = 0;
		int[] expectBase = {4, 5, 6, 7}; // Outline, widths, rotation (+ Corner round, + Sides, + Wobble and its size)
		for(Outline o: Outline.values()){ setProp(v, "propertyOutline", o); check(v.countRows(0) == expectBase[o.ordinal()], o + ": Base shows " + v.countRows(0) + " rows (Body " + v.countRows(1) + ", Random " + v.countRows(v.getSectionCount() - 1) + ")"); }
		check(v.countRows(v.getSectionCount() - 1) == 7, "Random rows: Base %, Body %, Spikes %, Randomize, Naturalize, Natural spikes, Undo (Seed is saved but not shown since spikes 2: 7 rows fit at 270)");

		System.out.println("-- Randomize (seeded)");
		TestIsland r1 = island(42), r2 = island(42);
		updates = 0; call(r1, "randomize"); int one = updates; call(r2, "randomize");
		check(one == 1, "one Randomize = exactly one regeneration (" + one + ")");
		check(values(r1).equals(values(r2)), "same injected seed: the same result");
		boolean limits = true, kept = true, cleanAll = true; TestIsland rr = island(7);
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()){
			setProp(rr, "propertyOutline", o); setProp(rr, "propertyProfile", pr); setProp(rr, "propertyBasePercent", 100); setProp(rr, "propertyBodyPercent", 100);
			for(int i = 0; i < 50; i++){ call(rr, "randomize"); Values x = values(rr); limits &= inLimits(x); kept &= x.outline == o && x.profile == pr; cleanAll &= clean(x); }
		}
		check(limits, "600 Randomize at 100 %: every value within its limits");
		check(kept, "Randomize never changes Outline or Profile");
		check(cleanAll, "randomized values sit on their grids (0.05, 1 degree, 0.5), so the fields read clean");
		TestIsland z = island(3); setProp(z, "propertyOutline", Outline.ORGANIC); setProp(z, "propertyBasePercent", 0); setProp(z, "propertyBodyPercent", 50);
		Values z0 = values(z); call(z, "randomize"); Values z1 = values(z);
		check(baseSame(z0, z1) && !bodySame(z0, z1) && z0.seed != z1.seed, "Base 0 %: plan controls unchanged (seed and Body still move)");
		setProp(z, "propertyBasePercent", 50); setProp(z, "propertyBodyPercent", 0); z0 = values(z); call(z, "randomize"); z1 = values(z);
		check(bodySame(z0, z1) && !baseSame(z0, z1), "Body 0 %: Depth, Sharpness and Roughness unchanged");
		TestIsland c = island(5); setProp(c, "propertyOutline", Outline.CIRCLE); setProp(c, "propertyBasePercent", 100); Values c0 = values(c); call(c, "randomize"); Values c1 = values(c);
		check(c0.sides == c1.sides && c0.cornerRound == c1.cornerRound && c0.wobble == c1.wobble && c0.wobbleSize == c1.wobbleSize, "Circle: only the controls it shows move (Sides, Corner round, Wobble untouched)");

		System.out.println("-- Undo");
		TestIsland u = island(11); setProp(u, "propertyOutline", Outline.POLYGON); Values before = values(u);
		call(u, "randomize"); Values after = values(u); updates = 0; call(u, "undo");
		check(values(u).equals(before) && !after.equals(before) && updates == 1, "Undo after Randomize: every value and the seed back exactly, one regeneration");
		updates = 0; call(u, "undo");
		check(values(u).equals(before) && updates == 0, "a second Undo does nothing");
		call(u, "naturalize"); call(u, "undo");
		check(values(u).equals(before), "Undo after Naturalize: back to the values before it (Outline and Profile too)");

		System.out.println("-- Naturalize (200 seeds)");
		boolean natLimits = true, preserved = true, recipe = true, closed = true; int checkedShells = 0;
		for(int seed = 0; seed < 200; seed++){
			TestIsland t = island(seed); setProp(t, "propertyOutline", Outline.values()[seed % 4]); setProp(t, "propertyWidthX", 25 + seed % 40); setProp(t, "propertyWidthZ", 61 - seed % 30); setProp(t, "propertyDepth", seed % 50); setProp(t, "propertyWall", 1 + seed % 3);
			Values b = values(t); int wall = ((PropertyRangeInt) get(t, "propertyWall")).value; updates = 0; call(t, "naturalize"); Values x = values(t);
			natLimits &= inLimits(x) && clean(x) && updates == 1;
			preserved &= x.widthX == b.widthX && x.widthZ == b.widthZ && x.depth == b.depth && ((PropertyRangeInt) get(t, "propertyWall")).value == wall;
			recipe &= x.outline == Outline.ORGANIC && x.profile == Profile.BOWL && Math.abs(x.wobble - IslandControls.naturalWobble) <= IslandControls.naturalWobbleSpread + 0.03 && Math.abs(x.sharpness - IslandControls.naturalSharpness) <= IslandControls.naturalSharpnessSpread + 0.03;
			if(seed % 20 == 0){ Params q = (Params) call(t, "params"); int[] r = IslandTest.shellCheck(q, IslandTest.set(IslandTest.gen(q))); closed &= r[0] == 0 && r[1] == 0; checkedShells++; }
		}
		check(natLimits, "every value within its limits, on its grid, one regeneration each");
		check(preserved, "widths, Depth and Thickness preserved (the origin lives in ShapeSet, which Island never writes)");
		check(recipe, "Organic + Bowl, Wobble and Sharpness within the recipe's spread");
		check(closed, "shell still equal to the reference and closed in 6-connectivity (" + checkedShells + " naturalized islands)");

		System.out.println("-- percent fields step by 5");
		PropertyRangeInt base = (PropertyRangeInt) get(new TestIsland(), "propertyBasePercent");
		FloatStepTest.buttons.clear(); base.getWidgetList(); IButton.IPressable minus = null, plus = null;
		for(Object[] b: FloatStepTest.buttons){ if("-".equals(b[0])) minus = (IButton.IPressable) b[1]; if("+".equals(b[0])) plus = (IButton.IPressable) b[1]; }
		plus.onPress(); boolean up = base.value == 20; for(int i = 0; i < 20; i++) plus.onPress();
		boolean top = base.value == 100; for(int i = 0; i < 30; i++) minus.onPress();
		check(up && top && base.value == 0, "Base %: 15 + = 20, stops at 100 and at 0");
		ShapeSpline sp = new ShapeSpline(); Field pc = ShapeSpline.class.getDeclaredField("propertyPointCount"); pc.setAccessible(true); PropertyRangeInt count = (PropertyRangeInt) pc.get(sp);
		FloatStepTest.buttons.clear(); count.getWidgetList(); for(Object[] b: FloatStepTest.buttons) if("+".equals(b[0])) plus = (IButton.IPressable) b[1];
		int c2 = count.value; try { plus.onPress(); } catch(Exception e){} // Spline's own onPress regenerates; only the value matters here
		check(count.value == c2 + 1, "Spline Point count (no step given) still moves by 1: " + c2 + " -> " + count.value);

		System.out.println("-- persistence");
		String old = "ORGANIC,51,37,6,0.7,15.0,0.25,6.0,3,30,TERRACED,0.4,0.3,777,Runnable";
		ShapeIsland l = new TestIsland(); l.restorePersistence(old);
		String now = l.toPersistence();
		check(!l.error && now.startsWith(old + ","), "a 15-value Island from block A loads and keeps its 15 positions: " + now);
		check(now.startsWith(old + ",15,15,Runnable,Runnable,0,RANDOM,12,3,0,50"), "new properties at the end, at their defaults (Base 15 %, Body 15 %, Naturalize, Undo, then the 6 spike controls; later ones follow)");
		check(new TestIsland().properties.size() >= 25, "at least 25 persisted properties (15 + 4 + 6 spikes; spikes 2 adds more at the end)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
