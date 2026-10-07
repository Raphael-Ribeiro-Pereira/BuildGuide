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
// Island block B: spikes (vertical cones, tip down) under the body. With Count 0 the body is the
// GOLDEN one (IslandGoldenTest); here: the shell of the union, roots, lengths, rotation, and the
// Randomize / Naturalize / Undo / persistence / panel rules of the six new controls.
public class IslandSpikesTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params p(Outline o, Profile pr, int wall, SpikeMode m, long seed){
		Params p = new Params(); p.outline = o; p.profile = pr; p.wall = wall; p.spikeMode = m; p.seed = seed; p.widthX = 31; p.widthZ = 27; p.depth = 12;
		p.spikes = 5; p.spikeLength = 10; p.spikeBase = 3; p.lengthVar = 40; p.spread = 60; p.sides = 5; return p;
	}
	static Params copy(Params a){ Params p = p(a.outline, a.profile, a.wall, a.spikeMode, a.seed); p.widthX = a.widthX; p.widthZ = a.widthZ; p.depth = a.depth; p.spikes = a.spikes; p.spikeLength = a.spikeLength; p.spikeBase = a.spikeBase; p.lengthVar = a.lengthVar; p.spread = a.spread; p.rotationDeg = a.rotationDeg; p.sides = a.sides; p.roundness = a.roundness; p.edgeAmplitude = a.edgeAmplitude; p.roughness = a.roughness; p.sharpness = a.sharpness; return p; }
	static int bottomAt(Params p, int x, int z){ int h = IslandGeometry.halfBox(p), n = 2 * h + 1; return IslandGeometry.columns(p, 0)[(x + h) * n + (z + h)]; }

	// ShapeIsland with update() counting regenerations, as in IslandUxTest
	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }
	static Object call(Object o, String m) throws Exception { Method x = ShapeIsland.class.getDeclaredMethod(m); x.setAccessible(true); return x.invoke(o); }
	static Object get(Object o, String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) get(s, f)).value = v; }
	static void setRandom(ShapeIsland s, long seed) throws Exception { Field x = ShapeIsland.class.getDeclaredField("random"); x.setAccessible(true); x.set(s, new Random(seed)); }
	static Values values(ShapeIsland s) throws Exception { return (Values) call(s, "values"); }
	static boolean spikeSame(Values a, Values b){ return a.spikes == b.spikes && a.spikeMode == b.spikeMode && a.spikeLength == b.spikeLength && a.spikeBase == b.spikeBase && a.lengthVar == b.lengthVar && a.spread == b.spread; }
	static boolean spikeInLimits(Values v){ return v.spikes >= 1 && v.spikes <= IslandGeometry.maxSpikes && v.spikeLength >= 1 && v.spikeLength <= 40 && v.spikeBase >= 1 && v.spikeBase <= 8 && v.lengthVar >= 0 && v.lengthVar <= 100 && v.lengthVar % 5 == 0 && v.spread >= 0 && v.spread <= 100 && v.spread % 5 == 0; }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new FloatStepTest.Widgets();

		System.out.println("-- shell of the union: Outline x Profile x Wall x mode");
		int cases = 0, bad = 0; String firstBad = null;
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()) for(int w = 1; w <= 3; w++) for(SpikeMode m: SpikeMode.values()){
			Params p = p(o, pr, w, m, 11 + cases); p.rotationDeg = 20 * cases; cases++;
			List<int[]> l = IslandTest.gen(p); Set<Long> s = IslandTest.set(l); int[] r = IslandTest.shellCheck(p, s);
			if(r[0] != 0 || r[1] != 0 || s.size() != l.size()){ bad++; if(firstBad == null) firstBad = o + "/" + pr + " wall " + w + " " + m + ": mismatch " + r[0] + ", reached " + r[1]; }
		}
		check(bad == 0, cases + " islands with 5 spikes: shell = the body's shell definition on the union, outside fill reaches no interior cell, no duplicates" + (firstBad == null ? "" : " (first bad: " + firstBad + ")"));
		Params big = p(Outline.ORGANIC, Profile.BOWL, 3, SpikeMode.RING, 4); big.widthX = 61; big.widthZ = 61; big.depth = 30; big.spikes = 12; big.spikeLength = 40; big.spikeBase = 8; big.spread = 70;
		int[] rb = IslandTest.shellCheck(big, IslandTest.set(IslandTest.gen(big)));
		check(rb[0] == 0 && rb[1] == 0, "12 spikes at the maximum (length 40, base 8, Wall 3) on a 61 x 61 island: shell = reference, closed");
		Params tip = p(Outline.CIRCLE, Profile.BOWL, 2, SpikeMode.RING, 1); tip.spikes = 1; tip.lengthVar = 0; tip.roughness = 0;
		int surface = bottomAt(copyNoSpikes(tip), 0, 0), deepest = bottomAt(tip, 0, 0);
		check(deepest == surface + tip.spikeLength, "Ring with 1 spike: at the centre, its tip is Spike length (" + tip.spikeLength + ") below the bottom surface (" + surface + " -> " + deepest + ")");
		int minY = 0; for(int[] b: IslandTest.gen(tip)) minY = Math.min(minY, b[1]);
		check(minY == -deepest, "the lowest block is the tip (y " + minY + ")");

		System.out.println("-- cost: every limit at its maximum");
		int worstBlocks = 0; long worstMs = 0; String worstAt = "";
		for(Outline o: Outline.values()) for(SpikeMode m: SpikeMode.values()) for(Profile pr: Profile.values()) for(int spread: new int[]{0, 50, 70, 100}){
			Params w = p(o, pr, 3, m, 1); w.widthX = 121; w.widthZ = 121; w.depth = 80; w.spikes = 12; w.spikeLength = 40; w.spikeBase = 8; w.lengthVar = 0; w.spread = spread; w.roundness = 0; w.sides = 4; w.edgeAmplitude = 0.25; w.roughness = 0.3; w.sharpness = 0.4;
			int[] c = {0}; long t0 = System.nanoTime(); IslandGeometry.enumerate(w, (x, y, z) -> c[0]++); long ms = (System.nanoTime() - t0) / 1000000;
			if(c[0] > worstBlocks){ worstBlocks = c[0]; worstAt = o + " " + m + " " + pr + " spread " + spread; } worstMs = Math.max(worstMs, ms);
		}
		System.out.printf(Locale.ROOT, "   worst: %d blocks (%s), about %.1f MB of vertex buffer, slowest geometry %d ms%n", worstBlocks, worstAt, worstBlocks * 672 / 1048576.0, worstMs);
		check(worstBlocks < 150000 && worstMs < 1500, "121 x 121, Depth 80, Wall 3, 12 spikes 40 long with base 8: at most " + worstBlocks + " blocks (limit ~150k), " + worstMs + " ms");

		System.out.println("-- determinism and positions");
		Params r1 = p(Outline.ORGANIC, Profile.CONE, 2, SpikeMode.RANDOM, 123);
		check(Arrays.deepEquals(IslandGeometry.spikePlan(r1), IslandGeometry.spikePlan(copy(r1))) && IslandTest.set(IslandTest.gen(r1)).equals(IslandTest.set(IslandTest.gen(copy(r1)))), "same seed: same spikes, same island");
		Params r2 = copy(r1); r2.seed = 124;
		check(!Arrays.deepEquals(IslandGeometry.spikePlan(r1), IslandGeometry.spikePlan(r2)), "Random: another seed moves the spikes");
		Params g1 = copy(r1); g1.spikeMode = SpikeMode.RING; Params g2 = copy(g1); g2.seed = 999;
		boolean ringSame = true; double[][] a1 = IslandGeometry.spikePlan(g1), a2 = IslandGeometry.spikePlan(g2);
		for(int i = 0; i < a1.length; i++) ringSame &= a1[i][0] == a2[i][0] && a1[i][1] == a2[i][1] && a1[i][2] == a2[i][2] && a1[i][3] == a2[i][3];
		check(ringSame, "Ring: positions do not depend on the seed");
		boolean inside = true, equal = true; int pulled = 0;
		for(Outline o: Outline.values()) for(SpikeMode m: SpikeMode.values()) for(int seed = 0; seed < 200; seed++){
			Params q = p(o, Profile.BOWL, 2, m, seed); q.spikes = 1 + seed % 12; q.spread = (seed * 7) % 101; q.sides = 3 + seed % 10; q.rotationDeg = seed * 13; q.edgeAmplitude = 0.5; q.roundness = (seed % 5) / 4.0;
			Params body = copyNoSpikes(q); int h = IslandGeometry.halfBox(q), n = 2 * h + 1; int[] cols = IslandGeometry.columns(body, 0);
			double[][] plan = IslandGeometry.spikePlan(q);
			for(double[] s: plan){ inside &= IslandGeometry.insideOutline(q, s[0], s[1]) && cols[((int) s[2] + h) * n + ((int) s[3] + h)] >= 0; if(Math.hypot(s[0], s[1]) < q.spread / 100.0 - 1e-9 && m == SpikeMode.RING && q.spikes > 1) pulled++; }
			if(m == SpikeMode.RING && q.spikes > 1 && q.spread > 0){
				for(int i = 0; i < plan.length; i++){ double rho = Math.hypot(plan[i][0], plan[i][1]); if(rho < 1e-9) continue; double ang = Math.atan2(plan[i][1], plan[i][0]), want = 2 * Math.PI * i / q.spikes; double diff = Math.abs(Math.atan2(Math.sin(ang - want), Math.cos(ang - want))); equal &= diff < 1e-6; }
			}
		}
		check(inside, "roots always inside the outline and on a plan column (4 Outlines x 2 modes x 200 seeds)");
		check(equal, "Ring: angles equally spaced (360 / Count); a root pulled in keeps its angle (" + pulled + " pulled towards the centre)");
		Params rot0 = p(Outline.SQUARE, Profile.BOWL, 2, SpikeMode.RANDOM, 77), rot1 = copy(rot0); rot1.rotationDeg = 90;
		double[][] q0 = IslandGeometry.spikePlan(rot0), q1 = IslandGeometry.spikePlan(rot1); boolean turns = true;
		for(int i = 0; i < q0.length; i++){ turns &= q0[i][0] == q1[i][0] && q0[i][1] == q1[i][1]; double x = q0[i][0] * rot0.widthX / 2.0, z = q0[i][1] * rot0.widthZ / 2.0; turns &= q1[i][2] == Math.round(-z) && q1[i][3] == Math.round(x); }
		check(turns, "the spikes turn with the island (rotation 90: same plan point, column turned 90 degrees)");

		System.out.println("-- lengths");
		Params v0 = p(Outline.CIRCLE, Profile.BOWL, 2, SpikeMode.RANDOM, 5); v0.spikes = 12; v0.lengthVar = 0; v0.spikeLength = 17;
		boolean same = true; for(double[] s: IslandGeometry.spikePlan(v0)) same &= s[4] == 17; check(same, "Length var 0 %: all 12 spikes 17 long");
		boolean within = true, varied = false; for(int seed = 0; seed < 200; seed++){ Params v = copy(v0); v.seed = seed; v.lengthVar = 100; v.spikeLength = 30; double first = -1; for(double[] s: IslandGeometry.spikePlan(v)){ within &= s[4] >= 1 && s[4] <= 40; if(first < 0) first = s[4]; else if(s[4] != first) varied = true; } }
		check(within && varied, "Length var 100 %: every length within [1, 40], and they differ");

		System.out.println("-- Randomize, Naturalize, Undo");
		TestIsland none = new TestIsland(); setRandom(none, 1); setProp(none, "propertyBodyPercent", 100); Values n0 = values(none);
		for(int i = 0; i < 50; i++) call(none, "randomize");
		check(spikeSame(n0, values(none)) && values(none).spikes == 0, "Count 0: 50 Randomize at Body 100 % never touch a spike control (still none)");
		TestIsland some = new TestIsland(); setRandom(some, 2); setProp(some, "propertySpikes", 1); setProp(some, "propertySpikeMode", SpikeMode.RING); setProp(some, "propertyBodyPercent", 100);
		boolean ok = true, mode = true, moved = false; Values s0 = values(some);
		for(int i = 0; i < 300; i++){ updates = 0; call(some, "randomize"); Values x = values(some); ok &= spikeInLimits(x) && updates == 1; mode &= x.spikeMode == SpikeMode.RING; moved |= !spikeSame(s0, x); }
		check(ok && moved, "Count >= 1: 300 Randomize keep Count >= 1 and every spike control in range (percentages on 5), one regeneration each");
		check(mode, "Randomize never changes Spike mode");
		TestIsland u = new TestIsland(); setRandom(u, 3); setProp(u, "propertySpikes", 4); setProp(u, "propertyBodyPercent", 80); Values before = values(u);
		call(u, "randomize"); Values after = values(u); updates = 0; call(u, "undo");
		check(!spikeSame(before, after) && values(u).equals(before) && updates == 1, "Undo restores every spike control and the seed, one regeneration");
		TestIsland nat = new TestIsland(); setRandom(nat, 4); setProp(nat, "propertySpikes", 7); setProp(nat, "propertySpikeLength", 33); setProp(nat, "propertySpikeBase", 6); setProp(nat, "propertyLengthVar", 25); setProp(nat, "propertySpread", 80); setProp(nat, "propertySpikeMode", SpikeMode.RING);
		Values nb = values(nat); call(nat, "naturalize"); call(nat, "naturalize");
		check(spikeSame(nb, values(nat)), "Naturalize keeps all six spike controls");

		System.out.println("-- persistence and panel");
		String old = "ORGANIC,51,37,6,0.7,15.0,0.25,6.0,3,30,TERRACED,0.4,0.3,777,Runnable,20,35,Runnable,Runnable";
		ShapeIsland l = new TestIsland(); l.restorePersistence(old); String now = l.toPersistence();
		check(!l.error && now.startsWith(old + ",0,RANDOM,12,3,0,50"), "a 19-value Island loads, its 19 values intact, the 6 block B values at their defaults (Count 0; later properties follow): " + now);
		Values lv = values(l); check(lv.spikes == 0, "an old island has no spikes");
		ShapeIsland panel = new TestIsland();
		check(panel.getSectionCount() == 4 && panel.getSectionName(2).getTranslationKey().equals("property.buildguide.section.spikes"), "sections: Base, Body, Spikes, Random");
		check(panel.countRows(2) == 1, "Count 0: the Spikes section shows 1 row (Count)");
		setProp(panel, "propertySpikes", 3);
		check(panel.countRows(2) == 6, "Count 3: the Spikes section shows 6 rows");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
	static Params copyNoSpikes(Params a){ Params p = copy(a); p.spikes = 0; return p; }
}
