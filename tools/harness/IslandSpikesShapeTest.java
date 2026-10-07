import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandControls.Values;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.IslandGeometry.SpikeMode;
// Island spikes 2, phase 2: the spikes' shape (Taper, Dripstone, Edge falloff in its own section), the
// Spikes % group of Randomize, Naturalize spikes, persistence and the panel. Taper 1 without Dripstone
// is the block B cone (IslandGoldenSpikesTest).
public class IslandSpikesShapeTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params spikes(Outline o, Profile pr, int wall, int count, SpikeMode m, long seed){
		Params p = new Params(); p.outline = o; p.profile = pr; p.wall = wall; p.seed = seed; p.widthX = 41; p.widthZ = 33; p.depth = 16; p.sides = 5;
		p.spikes = count; p.spikeMode = m; p.spikeLength = 14; p.spikeBase = 4; p.lengthVar = 20; p.spread = 70; return p;
	}
	static Method reach;
	static int reach(Params p, double d, int length) throws Exception { Params c = p; return (Integer) reach.invoke(null, c, d, length, p.spikeBase + 0.5); }
	// Cells of one spike's cross-section at offset j below its root's surface (horizontal disc around the axis)
	static int section(Params p, int length, int j) throws Exception { int n = 0; for(int dx = -p.spikeBase; dx <= p.spikeBase; dx++) for(int dz = -p.spikeBase; dz <= p.spikeBase; dz++) if(reach(p, Math.hypot(dx, dz), length) >= j) n++; return n; }

	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }
	static Object call(Object o, String m) throws Exception { Method x = ShapeIsland.class.getDeclaredMethod(m); x.setAccessible(true); return x.invoke(o); }
	static Object get(Object o, String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) get(s, f)).value = v; }
	static void setRandom(ShapeIsland s, long seed) throws Exception { Field x = ShapeIsland.class.getDeclaredField("random"); x.setAccessible(true); x.set(s, new Random(seed)); }
	static Values values(ShapeIsland s) throws Exception { return (Values) call(s, "values"); }
	static boolean spikeSame(Values a, Values b){ return a.spikes == b.spikes && a.spikeMode == b.spikeMode && a.spikeLength == b.spikeLength && a.spikeBase == b.spikeBase && a.lengthVar == b.lengthVar && a.spread == b.spread && a.jitter == b.jitter && a.taper == b.taper && a.falloff == b.falloff && a.dripstone == b.dripstone; }
	static boolean onGrid(int percent){ return percent >= 0 && percent <= 100 && percent % 5 == 0; }
	static boolean taperOk(float t){ return t >= 0.5f && t <= 3.0f && Float.toString(t).length() <= 3; }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new FloatStepTest.Widgets();
		reach = IslandGeometry.class.getDeclaredMethod("spikeReach", Params.class, double.class, int.class, double.class); reach.setAccessible(true);

		System.out.println("-- Taper");
		Params k1 = spikes(Outline.CIRCLE, Profile.BOWL, 2, 6, SpikeMode.RANDOM, 3); Params k1b = spikes(Outline.CIRCLE, Profile.BOWL, 2, 6, SpikeMode.RANDOM, 3); k1b.taper = 1.0;
		check(IslandTest.set(IslandTest.gen(k1)).equals(IslandTest.set(IslandTest.gen(k1b))), "Taper 1.0: the same island as block B (the GOLDEN-SPIKES islands pass too)");
		Params k2 = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); k2.taper = 2.0; k2.spikeBase = 6;
		check(Math.abs(IslandGeometry.spikeRadius(k2, 0.5, 20) - 6 * 0.25) < 1e-12, "Taper 2: r(0.5) = Base x 0.25 (" + IslandGeometry.spikeRadius(k2, 0.5, 20) + ")");
		boolean never = true, tipZero = true;
		for(double k = 0.5; k <= 3.0 + 1e-9; k += 0.1) for(boolean drip: new boolean[]{false, true}){ Params q = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); q.taper = k; q.dripstone = drip; double prev = Double.MAX_VALUE;
			for(int s = 0; s <= 400; s++){ double r = IslandGeometry.spikeRadius(q, s / 400.0, 20); never &= r <= prev + 1e-12; prev = r; } tipZero &= IslandGeometry.spikeRadius(q, 1.0, 20) == 0; }
		check(never, "the radius never grows towards the tip (Taper 0.5 to 3.0, with and without Dripstone)");
		check(tipZero, "the radius is 0 at the tip");
		Params thin = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); thin.taper = 3.0; Params fat = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); fat.taper = 0.5;
		check(section(thin, 14, 7) < section(k1, 14, 7) && section(k1, 14, 7) < section(fat, 14, 7), "halfway down: Taper 3 thinner than 1, thinner than 0.5 (" + section(thin, 14, 7) + " < " + section(k1, 14, 7) + " < " + section(fat, 14, 7) + " cells)");

		System.out.println("-- Dripstone (Length 20, Base 4)");
		Params drip = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); drip.dripstone = true; drip.spikeBase = 4; drip.spikeLength = 20;
		int[] sec = new int[21]; for(int j = 1; j <= 20; j++) sec[j] = section(drip, 20, j);
		int j0 = 1; while(j0 <= 20 && sec[j0] > 1) j0++;
		boolean tail = true; for(int j = j0; j <= 20; j++) tail &= sec[j] == 1;
		boolean shrinking = true; for(int j = 2; j <= 20; j++) shrinking &= sec[j] <= sec[j - 1];
		System.out.println("   cross-section per block from the root: " + Arrays.toString(Arrays.copyOfRange(sec, 1, 21)));
		check(sec[1] > 1 && j0 <= 20 - IslandGeometry.DRIP_TIP_BLOCKS && tail && shrinking, "thick base (" + sec[1] + " cells), then a 1-block section from block " + j0 + " through the last " + IslandGeometry.DRIP_TIP_BLOCKS + " blocks to the tip at 20");
		Params plain = spikes(Outline.CIRCLE, Profile.BOWL, 2, 1, SpikeMode.RING, 1); plain.spikeBase = 4; plain.spikeLength = 20; int plainOne = 1; while(plainOne <= 20 && section(plain, 20, plainOne) > 1) plainOne++;
		check(j0 < plainOne, "Dripstone becomes a needle sooner than the plain cone (1 block from block " + j0 + " instead of " + plainOne + ")");
		check(IslandGeometry.DRIP_EXP_FACTOR == 2.0 && IslandGeometry.DRIP_TAIL_R == 0.5 && IslandGeometry.DRIP_TIP_BLOCKS == 2, "constants: DRIP_EXP_FACTOR 2.0, DRIP_TAIL_R 0.5, DRIP_TIP_BLOCKS 2");
		int cases = 0, bad = 0; String firstBad = null;
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()) for(int w = 1; w <= 3; w++){
			Params p = spikes(o, pr, w, 12, SpikeMode.values()[cases % 3], 40 + cases); p.dripstone = true; p.taper = 0.5 + (cases % 6) * 0.5; p.falloff = 30; p.rotationDeg = 13 * cases; cases++;
			List<int[]> l = IslandTest.gen(p); Set<Long> s = IslandTest.set(l); int[] r = IslandTest.shellCheck(p, s);
			if(r[0] != 0 || r[1] != 0 || s.size() != l.size()){ bad++; if(firstBad == null) firstBad = o + "/" + pr + " wall " + w; }
		}
		check(bad == 0, cases + " islands with 12 Dripstone spikes (Taper 0.5 to 3): shell = reference, closed, nothing outside the shell definition" + (firstBad == null ? "" : " (first bad: " + firstBad + ")"));
		int tcases = 0, tbad = 0;
		for(Outline o: Outline.values()) for(double k: new double[]{0.5, 2.0, 3.0}){ Params p = spikes(o, Profile.CONE, 2, 20, SpikeMode.FILL, 70 + tcases); p.taper = k; tcases++; int[] r = IslandTest.shellCheck(p, IslandTest.set(IslandTest.gen(p))); if(r[0] != 0 || r[1] != 0) tbad++; }
		check(tbad == 0, tcases + " islands with 20 spikes, Taper 0.5, 2 and 3: shell = reference, closed");

		System.out.println("-- cost: Taper 0.5 (the widest spikes), 64 of them");
		int worst = 0; long worstMs = 0; String at = "";
		for(SpikeMode m: SpikeMode.values()) for(Outline o: new Outline[]{Outline.ORGANIC, Outline.SQUARE}) for(boolean d: new boolean[]{false, true}){
			Params w = spikes(o, Profile.BOWL, 3, 64, m, 1); w.widthX = 121; w.widthZ = 121; w.depth = 80; w.spikeLength = 40; w.spikeBase = 8; w.spread = 100; w.taper = 0.5; w.dripstone = d; w.lengthVar = 0; w.roundness = 0; w.edgeAmplitude = 0.25; w.roughness = 0.3; w.sharpness = 0.4;
			int[] c = {0}; long t0 = System.nanoTime(); IslandGeometry.enumerate(w, (x, y, z) -> c[0]++); long ms = (System.nanoTime() - t0) / 1000000; worstMs = Math.max(worstMs, ms);
			if(c[0] > worst){ worst = c[0]; at = m + " " + o + (d ? " dripstone" : ""); }
		}
		System.out.printf(Locale.ROOT, "   worst: %d blocks (%s), about %.1f MB of vertex buffer, slowest geometry %d ms%n", worst, at, worst * 672 / 1048576.0, worstMs);
		check(worst < 400000 && worst * 672L < 260L * 1048576 && worstMs < 1500, "64 spikes at Taper 0.5 stay under ~400k blocks and ~260 MB: " + worst + " blocks (cap 64 kept)");

		System.out.println("-- Randomize: the Spikes % group");
		TestIsland none = new TestIsland(); setRandom(none, 1); setProp(none, "propertySpikesPercent", 100); Values n0 = values(none);
		for(int i = 0; i < 50; i++) call(none, "randomize");
		check(spikeSame(n0, values(none)), "Count 0: 50 Randomize at Spikes 100 % change nothing in the spikes");
		boolean limits = true, fixed = true, one = true, moved = false;
		for(int start: new int[]{1, 30, 64}) for(SpikeMode m: SpikeMode.values()) for(boolean d: new boolean[]{false, true}){
			TestIsland s = new TestIsland(); setRandom(s, start * 7 + m.ordinal()); setProp(s, "propertySpikes", start); setProp(s, "propertySpikeMode", m); setProp(s, "propertyDripstone", d); setProp(s, "propertySpikesPercent", 100); Values s0 = values(s);
			for(int i = 0; i < 40; i++){ updates = 0; call(s, "randomize"); Values x = values(s); one &= updates == 1;
				limits &= x.spikes >= 1 && x.spikes <= 64 && x.spikeLength >= 1 && x.spikeLength <= 40 && x.spikeBase >= 1 && x.spikeBase <= 8 && onGrid(x.lengthVar) && onGrid(x.spread) && onGrid(x.jitter) && onGrid(x.falloff) && taperOk(x.taper);
				fixed &= x.spikeMode == m && x.dripstone == d; moved |= !spikeSame(s0, x); }
		}
		check(limits && moved, "Count 1, 30 and 64, every mode: Count stays >= 1, every spike control within its limits and grid");
		check(fixed, "Spike mode and Dripstone never change");
		check(one, "one regeneration per Randomize");
		TestIsland jit = new TestIsland(); setRandom(jit, 5); setProp(jit, "propertySpikes", 10); setProp(jit, "propertySpikeMode", SpikeMode.RING); setProp(jit, "propertySpikesPercent", 100); int j0v = values(jit).jitter; for(int i = 0; i < 20; i++) call(jit, "randomize");
		check(values(jit).jitter == j0v, "Jitter only moves in Fill (Ring: unchanged)");
		TestIsland body = new TestIsland(); setRandom(body, 6); setProp(body, "propertySpikes", 12); setProp(body, "propertyBodyPercent", 100); setProp(body, "propertySpikesPercent", 0); Values b0 = values(body);
		for(int i = 0; i < 30; i++) call(body, "randomize");
		check(spikeSame(b0, values(body)) && values(body).depth != b0.depth, "Body 100 %, Spikes 0 %: Depth moves, the spikes do not (Body no longer touches them)");
		TestIsland u = new TestIsland(); setRandom(u, 7); setProp(u, "propertySpikes", 9); setProp(u, "propertySpikesPercent", 60); setProp(u, "propertyTaper", 2.2f); setProp(u, "propertyFalloff", 35); Values before = values(u);
		call(u, "randomize"); updates = 0; call(u, "undo");
		check(values(u).equals(before) && updates == 1, "Undo restores every spike control (Taper, falloff included) and the seed");

		System.out.println("-- Naturalize spikes");
		TestIsland ns = new TestIsland(); setProp(ns, "propertySeed", 1234); Values v0 = values(ns); updates = 0; call(ns, "naturalizeSpikes"); Values v1 = values(ns);
		check(v1.spikes == IslandControls.naturalSpikeCount && v1.spikes == 8 && updates == 1, "Count 0 becomes 8, one regeneration");
		check(v1.seed == v0.seed && v1.spikeMode == v0.spikeMode && v1.dripstone == v0.dripstone && v1.spikeLength == v0.spikeLength && v1.spikeBase == v0.spikeBase && v1.spread == v0.spread, "seed, Spike mode, Dripstone, Length, Base and Spread unchanged");
		TestIsland ns2 = new TestIsland(); setProp(ns2, "propertySeed", 1234); call(ns2, "naturalizeSpikes");
		check(values(ns2).equals(v1), "drawn from the current seed: the same seed gives the same spikes");
		boolean recipe = true, keep = true;
		for(int seed = 0; seed < 200; seed++){ TestIsland t = new TestIsland(); setProp(t, "propertySeed", seed); setProp(t, "propertySpikes", 1 + seed % 64); setProp(t, "propertySpikeMode", SpikeMode.values()[seed % 3]); setProp(t, "propertyDripstone", seed % 2 == 0); Values t0 = values(t); call(t, "naturalizeSpikes"); Values x = values(t);
			recipe &= Math.abs(x.falloff - 60) <= 15 && Math.abs(x.lengthVar - 30) <= 10 && Math.abs(x.taper - 1.4f) <= 0.3f + 1e-6 && Math.abs(x.jitter - 25) <= 10 && onGrid(x.falloff) && onGrid(x.lengthVar) && onGrid(x.jitter) && taperOk(x.taper);
			keep &= x.spikes == t0.spikes && x.seed == t0.seed && x.spikeMode == t0.spikeMode && x.dripstone == t0.dripstone; }
		check(recipe, "200 seeds: Edge falloff 60 +/- 15, Length var 30 +/- 10, Taper 1.4 +/- 0.3, Jitter 25 +/- 10, on their grids");
		check(keep, "with Count > 0 the count stays, and seed, mode and Dripstone stay");
		TestIsland nu = new TestIsland(); setProp(nu, "propertySpikes", 5); Values nb = values(nu); call(nu, "naturalizeSpikes"); updates = 0; call(nu, "undo");
		check(values(nu).equals(nb) && updates == 1, "Undo after Naturalize spikes restores everything");

		System.out.println("-- persistence and panel");
		String old = "ORGANIC,51,37,6,0.7,15.0,0.25,6.0,3,30,TERRACED,0.4,0.3,777,Runnable,20,35,Runnable,Runnable,9,FILL,22,5,15,70,35";
		ShapeIsland l = new TestIsland(); l.restorePersistence(old);
		check(!l.error && l.toPersistence().startsWith(old + ",false,1.0,0,15,Runnable"), "a 26-value Island loads unchanged, the 5 new at their defaults (no Dripstone, Taper 1.0, falloff 0, Spikes 15 %): " + l.toPersistence());
		ShapeIsland g = new TestIsland(); g.restorePersistence(old + ",true,2.3,45,60,Runnable");
		check(!g.error && g.toPersistence().startsWith(old + ",true,2.3,45,60,Runnable"), "Dripstone, Taper 2.3, falloff 45 and Spikes 60 % saved and loaded");
		ShapeIsland panel = new TestIsland(); int shape = 3, random = panel.getSectionCount() - 1;
		check(panel.getSectionCount() == 5 && panel.getSectionName(shape).getTranslationKey().equals("property.buildguide.section.spikeshape") && panel.getSectionName(random).getTranslationKey().equals("property.buildguide.section.seed"), "sections: Base, Body, Spikes, Spike shape, Random");
		check(panel.countRows(shape) == 0, "Count 0: Spike shape has no rows (the accordion's sections are fixed, so the header stays)");
		setProp(panel, "propertySpikes", 4); check(panel.countRows(shape) == 4, "with spikes: Dripstone, Taper, Edge falloff %, and Break since phase 3 (4 rows; Pieces and Gap only in Segmented)");
		check(panel.countRows(random) == 7, "Random: 7 rows (Seed not shown, still saved)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
