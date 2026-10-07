import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandControls.Values;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.IslandGeometry.SpikeMode;
// Island spikes 2. Phase 1: Count up to 64, the Fill mode (sunflower spiral with a seeded jitter), the
// edge falloff of the lengths, the Spikes section with Jitter in Fill, persistence. The GOLDEN tests
// (IslandGoldenTest, IslandGoldenSpikesTest) guard the body and the block B spikes at the defaults.
public class IslandSpikes2Test {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params fill(Outline o, int count, int spread, int jitter, long seed){
		Params p = new Params(); p.outline = o; p.profile = Profile.BOWL; p.wall = 2; p.seed = seed; p.widthX = 41; p.widthZ = 33; p.depth = 16; p.sides = 5;
		p.spikes = count; p.spikeMode = SpikeMode.FILL; p.spikeLength = 12; p.spikeBase = 3; p.lengthVar = 0; p.spread = spread; p.jitter = jitter; return p;
	}
	static double rho(Params p, double u, double v){ return Math.hypot(u, v) / IslandGeometry.edge(p, Math.atan2(v, u)); }

	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }
	static Object get(Object o, String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x.get(o); }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) get(s, f)).value = v; }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new FloatStepTest.Widgets();

		System.out.println("-- Count up to 64");
		check(IslandGeometry.maxSpikes == 64, "the cap is 64");
		boolean exact = true; for(int n = 1; n <= 64; n++) exact &= IslandGeometry.spikePlan(fill(Outline.CIRCLE, n, 80, 20, n)).length == n;
		check(exact, "Fill makes exactly Count spikes, for every Count from 1 to 64");
		Params over = fill(Outline.CIRCLE, 500, 80, 20, 1); check(IslandGeometry.spikePlan(over).length == 64, "a typed Count above 64 is clamped to 64");

		System.out.println("-- cost with 64 spikes (121 x 121, Depth 80, Wall 3, Length 40, Base 8)");
		int worstBlocks = 0; long worstMs = 0; String worstAt = "";
		for(SpikeMode m: SpikeMode.values()) for(Outline o: new Outline[]{Outline.ORGANIC, Outline.SQUARE}) for(int spread: new int[]{80, 100}){
			Params w = fill(o, 64, spread, 20, 1); w.spikeMode = m; w.profile = Profile.BOWL; w.widthX = 121; w.widthZ = 121; w.depth = 80; w.wall = 3; w.spikeLength = 40; w.spikeBase = 8; w.roundness = 0; w.edgeAmplitude = 0.25; w.roughness = 0.3; w.sharpness = 0.4;
			int[] c = {0}; long t0 = System.nanoTime(); IslandGeometry.enumerate(w, (x, y, z) -> c[0]++); long ms = (System.nanoTime() - t0) / 1000000;
			if(c[0] > worstBlocks){ worstBlocks = c[0]; worstAt = m + " " + o + " spread " + spread; } worstMs = Math.max(worstMs, ms);
		}
		System.out.printf(Locale.ROOT, "   worst: %d blocks (%s), about %.1f MB of vertex buffer, slowest geometry %d ms%n", worstBlocks, worstAt, worstBlocks * 672 / 1048576.0, worstMs);
		check(worstBlocks < 400000 && worstBlocks * 672L < 260L * 1048576 && worstMs < 1500, "64 spikes stay under ~400k blocks and ~260 MB: " + worstBlocks + " blocks, " + worstMs + " ms (cap 64 kept)");

		System.out.println("-- Fill positions");
		boolean inside = true;
		for(Outline o: Outline.values()) for(int seed = 0; seed < 200; seed++){
			Params q = fill(o, 1 + seed % 64, (seed * 7) % 101, (seed * 13) % 101, seed); q.rotationDeg = seed * 11; q.edgeAmplitude = 0.5; q.roundness = (seed % 5) / 4.0; q.sides = 3 + seed % 10;
			Params body = fill(o, 0, 0, 0, seed); body.rotationDeg = q.rotationDeg; body.edgeAmplitude = q.edgeAmplitude; body.roundness = q.roundness; body.sides = q.sides;
			int h = IslandGeometry.halfBox(q), n = 2 * h + 1; int[] cols = IslandGeometry.columns(body, 0);
			for(double[] s: IslandGeometry.spikePlan(q)) inside &= IslandGeometry.insideOutline(q, s[0], s[1]) && cols[((int) s[2] + h) * n + ((int) s[3] + h)] >= 0;
		}
		check(inside, "roots inside the outline and on a plan column (4 Outlines x 200 seeds, Count 1 to 64, any spread and jitter)");
		boolean formula = true;
		for(Outline o: Outline.values()) for(int count: new int[]{2, 7, 30, 64}){
			Params q = fill(o, count, 80, 0, 5); double[][] plan = IslandGeometry.spikePlan(q);
			for(int i = 0; i < count; i++){ double ang = i * IslandGeometry.goldenAngle, f = Math.sqrt((i + 0.5) / count) * 0.8 * IslandGeometry.edge(q, ang); formula &= Math.abs(plan[i][0] - f * Math.cos(ang)) < 1e-12 && Math.abs(plan[i][1] - f * Math.sin(ang)) < 1e-12; }
		}
		check(formula, "Jitter 0: every root is exactly on the spiral (golden angle, fraction sqrt((i + 0.5) / N) x Spread of the edge), 4 Outlines");
		check(IslandGeometry.spikePlan(fill(Outline.SQUARE, 1, 80, 100, 3))[0][0] == 0 && IslandGeometry.spikePlan(fill(Outline.SQUARE, 1, 80, 100, 3))[0][1] == 0, "Fill with Count 1: the spike is at the centre");
		boolean bounded = true; double worst = 0;
		for(Outline o: Outline.values()) for(int seed = 0; seed < 50; seed++){
			Params j0 = fill(o, 40, 60, 0, seed), j = fill(o, 40, 60, 100, seed); double reach = 1.0 * 0.5 * IslandGeometry.fillSpacing(j, 0.6);
			double[][] p0 = IslandGeometry.spikePlan(j0), p1 = IslandGeometry.spikePlan(j);
			for(int i = 0; i < 40; i++){ double d = Math.hypot(p1[i][0] - p0[i][0], p1[i][1] - p0[i][1]); worst = Math.max(worst, d / reach); bounded &= d <= reach + 1e-12; }
		}
		check(bounded, String.format(Locale.ROOT, "Jitter 100 %%: no root moves more than 0.5 x the mean spacing from its spiral point (largest %.2f of it)", worst));
		Params d1 = fill(Outline.ORGANIC, 30, 70, 40, 9), d2 = fill(Outline.ORGANIC, 30, 70, 40, 9), d3 = fill(Outline.ORGANIC, 30, 70, 40, 10);
		check(Arrays.deepEquals(IslandGeometry.spikePlan(d1), IslandGeometry.spikePlan(d2)) && !Arrays.deepEquals(IslandGeometry.spikePlan(d1), IslandGeometry.spikePlan(d3)), "jitter follows the seed: same seed same roots, another seed other roots");

		System.out.println("-- edge falloff");
		Params f0 = fill(Outline.CIRCLE, 30, 90, 30, 4); f0.lengthVar = 50; Params f0b = fill(Outline.CIRCLE, 30, 90, 30, 4); f0b.lengthVar = 50; f0b.falloff = 0;
		check(Arrays.deepEquals(IslandGeometry.spikePlan(f0), IslandGeometry.spikePlan(f0b)), "Falloff 0: lengths exactly as before (the GOLDEN-SPIKES islands pass too)");
		Params ring = fill(Outline.CIRCLE, 4, 100, 0, 1); ring.spikeMode = SpikeMode.RING; ring.widthX = 40; ring.widthZ = 40; ring.spikeLength = 30; ring.falloff = 100;
		double[][] rp = IslandGeometry.spikePlan(ring); boolean edgeOne = true; for(double[] s: rp) edgeOne &= Math.abs(rho(ring, s[0], s[1]) - 1) < 1e-9 && s[4] == 1;
		check(edgeOne, "Falloff 100: a root on the edge (rho 1) gets length 1");
		Params centre = fill(Outline.CIRCLE, 1, 100, 0, 1); centre.spikeMode = SpikeMode.RING; centre.spikeLength = 30; centre.falloff = 100;
		check(IslandGeometry.spikePlan(centre)[0][4] == 30, "Falloff 100: the central spike keeps its full length (30)");
		boolean monotone = true;
		for(int fo: new int[]{25, 50, 100}){ Params q = fill(Outline.SQUARE, 64, 100, 20, 7); q.falloff = fo; q.spikeLength = 40; double[][] plan = IslandGeometry.spikePlan(q); Integer[] idx = new Integer[plan.length]; for(int i = 0; i < idx.length; i++) idx[i] = i;
			Arrays.sort(idx, (x, y) -> Double.compare(rho(q, plan[x][0], plan[x][1]), rho(q, plan[y][0], plan[y][1]))); for(int i = 1; i < idx.length; i++) monotone &= plan[idx[i]][4] <= plan[idx[i - 1]][4]; }
		check(monotone, "lengths never grow with rho (Length var 0, falloff 25, 50 and 100, 64 spikes)");

		System.out.println("-- shell with 64 spikes in Fill: Outline x Profile x Wall");
		int cases = 0, bad = 0; String firstBad = null;
		for(Outline o: Outline.values()) for(Profile pr: Profile.values()) for(int w = 1; w <= 3; w++){
			Params p = fill(o, 64, 90, 40, 20 + cases); p.profile = pr; p.wall = w; p.lengthVar = 30; p.falloff = 40; p.rotationDeg = 17 * cases; cases++;
			List<int[]> l = IslandTest.gen(p); Set<Long> s = IslandTest.set(l); int[] r = IslandTest.shellCheck(p, s);
			if(r[0] != 0 || r[1] != 0 || s.size() != l.size()){ bad++; if(firstBad == null) firstBad = o + "/" + pr + " wall " + w + ": mismatch " + r[0] + ", reached " + r[1]; }
		}
		check(bad == 0, cases + " islands with 64 spikes in Fill: shell = reference, closed, no duplicates" + (firstBad == null ? "" : " (first bad: " + firstBad + ")"));

		System.out.println("-- panel and persistence");
		ShapeIsland panel = new TestIsland();
		check(panel.countRows(2) == 1, "Count 0: Spikes shows 1 row");
		setProp(panel, "propertySpikes", 64); check(panel.countRows(2) == 6, "Count 64, Random: 6 rows");
		setProp(panel, "propertySpikeMode", SpikeMode.RING); check(panel.countRows(2) == 6, "Ring: 6 rows");
		setProp(panel, "propertySpikeMode", SpikeMode.FILL); check(panel.countRows(2) == 7, "Fill: 7 rows (Jitter % shows)");
		check(((PropertyRangeInt) get(panel, "propertySpikes")).setValueFromString("64") && !((PropertyRangeInt) get(panel, "propertySpikes")).setValueFromString("65"), "the Count field takes 64 and refuses 65");
		String old = "ORGANIC,51,37,6,0.7,15.0,0.25,6.0,3,30,TERRACED,0.4,0.3,777,Runnable,20,35,Runnable,Runnable,9,RING,22,5,15,70";
		ShapeIsland l = new TestIsland(); l.restorePersistence(old); String now = l.toPersistence();
		check(!l.error && now.startsWith(old + ",20"), "a 25-value Island (block B, 9 spikes) loads unchanged, Jitter % at its default 20: " + now);
		check(new TestIsland().toPersistence().split(",")[20].equals("RANDOM") && new TestIsland().properties.size() >= 26, "Spike mode still at position 21, the new property after the 25 existing ones");
		ShapeIsland fillLoad = new TestIsland(); fillLoad.restorePersistence(old.replace(",RING,", ",FILL,") + ",35");
		check(!fillLoad.error && fillLoad.toPersistence().startsWith(old.replace(",RING,", ",FILL,") + ",35"), "Fill and Jitter 35 saved and loaded by name and value");
		check(ShapeScreen.largeShapeBlocks == 200000, "the Large shape warning starts above 200 000 blocks");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
