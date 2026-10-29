import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
// Layers and colours on the Island (visual only; the strata themselves are StrataTest's, 2026-10-27): the world
// colours (each cube its layer's colour, the shape's alpha; the spikes in the spike layer), and the proof that
// the blocks do not change: Layers on and off give the same set as the geometry the GOLDENs pin. Old islands
// load with Layers off; the three cuts of before are still saved where they were.
public class IslandLayersTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static class TestIsland extends ShapeIsland { @Override public void update(){} }
	static Field field(String f) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); return x; }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { ((Property<Object>) field(f).get(s)).value = v; }
	static Object prop(ShapeIsland s, String f) throws Exception { return ((Property<?>) field(f).get(s)).value; }

	// The world buffer as the shape fills it: the colour current at each vertex
	static class ColourBuffer implements IShapeBuffer {
		int r, g, b, a; final List<int[]> vertices = new ArrayList<>();
		public void setColour(int r, int g, int b, int a){ this.r = r; this.g = g; this.b = b; this.a = a; }
		public void pushVertex(double x, double y, double z){ vertices.add(new int[]{(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), r, g, b, a}); }
		public void end(){}
		public void close(){}
	}
	// A ShapeSet without the loader (Unsafe), shape colour (0.1, 0.2, 0.3, alpha 0.3), cube size 1
	static ShapeSet set(ShapeIsland s) throws Exception {
		ShapeSet set = OriginScanTest.newSet(new Shape[]{s}, 0, 64, 0);
		for(String[] f: new String[][]{{"colourShapeR", "0.1"}, {"colourShapeG", "0.2"}, {"colourShapeB", "0.3"}, {"colourShapeA", "0.3"}}){ Field x = ShapeSet.class.getDeclaredField(f[0]); x.setAccessible(true); x.setFloat(set, Float.parseFloat(f[1])); }
		Field cube = ShapeSet.class.getDeclaredField("shapeCubeSize"); cube.setAccessible(true); cube.setDouble(set, 1.0);
		Field owner = Shape.class.getDeclaredField("shapeSet"); owner.setAccessible(true); owner.set(s, set);
		return set;
	}
	// One generation's world part: the colour doUpdate sets, then updateShape. Returns the buffer
	static ColourBuffer generate(ShapeIsland s) throws Exception {
		ColourBuffer b = new ColourBuffer(); b.setColour(25, 51, 76, 76);
		Field exp = Shape.class.getDeclaredField("expectedBlocks"); exp.setAccessible(true); ((Set<?>) exp.get(s)).clear();
		Method m = ShapeIsland.class.getDeclaredMethod("updateShape", IShapeBuffer.class); m.setAccessible(true); m.invoke(s, b);
		return b;
	}
	static String hash(Collection<Long> blocks) throws Exception {
		List<Long> l = new ArrayList<>(blocks); Collections.sort(l); MessageDigest md = MessageDigest.getInstance("SHA-256");
		for(long v: l) for(int i = 0; i < 8; i++) md.update((byte) (v >>> (8 * i)));
		StringBuilder sb = new StringBuilder(); for(byte x: md.digest()) sb.append(String.format("%02x", x)); return l.size() + ":" + sb.substring(0, 16);
	}
	static void apply(ShapeIsland s, Params p) throws Exception {
		setProp(s, "propertyOutline", p.outline); setProp(s, "propertyWidthX", p.widthX); setProp(s, "propertyWidthZ", p.widthZ); setProp(s, "propertySides", p.sides);
		setProp(s, "propertyRoundness", (float) p.roundness); setProp(s, "propertyRotation", (float) p.rotationDeg); setProp(s, "propertyEdgeAmplitude", (float) p.edgeAmplitude);
		setProp(s, "propertyEdgeScale", (float) p.edgeScale); setProp(s, "propertyWall", p.wall); setProp(s, "propertyDepth", p.depth); setProp(s, "propertyProfile", p.profile);
		setProp(s, "propertySharpness", (float) p.sharpness); setProp(s, "propertyRoughness", (float) p.roughness); setProp(s, "propertySeed", (int) p.seed);
		setProp(s, "propertySpikes", p.spikes); setProp(s, "propertySpikeMode", p.spikeMode); setProp(s, "propertySpikeLength", p.spikeLength); setProp(s, "propertySpikeBase", p.spikeBase);
		setProp(s, "propertyLengthVar", p.lengthVar); setProp(s, "propertySpread", p.spread);
	}
	static Set<Long> blocks(ShapeIsland s) throws Exception { return new HashSet<>(s.getExpectedBlocks()); }

	public static void main(String[] a) throws Exception {
		ShapeIsland.layersInGame = true; // the layers' generation and saving (out of the Island's panel since 2026-10-28)
		brentmaas.buildguide.common.BuildGuide.widgetHandler = new PresetTest.FakeWidgets(); // setValue touches the widgets
		System.out.println("-- world colours");
		ShapeIsland s = new TestIsland(); set(s);
		ColourBuffer off = generate(s);
		boolean shapeColour = true; for(int[] v: off.vertices) shapeColour &= v[3] == 25 && v[4] == 51 && v[5] == 76 && v[6] == 76;
		check(shapeColour && s.getLayers() == null && !s.hasLayers(), "Layers off: every cube in the shape's colour, as before; no layers reported");
		Set<Long> plain = blocks(s);
		setProp(s, "propertyLayers", true);
		ColourBuffer on = generate(s);
		boolean layerColour = true; int[] seen = new int[5]; ShapeLayers mine = s.getLayers();
		for(int i = 0; i < on.vertices.size(); i++){ int[] v = on.vertices.get(i); int[] first = on.vertices.get(i - i % 24); int layer = mine.layerAt(first[0], first[1], first[2]); int rgb = ShapeLayers.colour(layer); seen[layer]++;
			layerColour &= v[3] == (rgb >> 16 & 0xFF) && v[4] == (rgb >> 8 & 0xFF) && v[5] == (rgb & 0xFF) && v[6] == 76; }
		check(layerColour && seen[1] > 0 && seen[2] > 0 && seen[3] > 0 && seen[4] > 0, "Layers on: each cube's 24 vertices in its layer's colour, the shape's alpha kept (76); all four layers present");
		check(s.hasLayers() && mine.spec.count == 4 && mine.spec.rest == 3 && mine.spec.axis == ShapeLayers.Axis.DEPTH && mine.spec.seed == 1, "the Island reports the layers of its generation (strata by depth: 4 layers, the rest third, its seed)");
		setProp(s, "propertySpikes", 8); ColourBuffer spiky = generate(s); ShapeLayers sl = s.getLayers(); boolean spikesLast = true; int spikeCubes = 0;
		for(int i = 0; i < spiky.vertices.size(); i += 24){ int[] v = spiky.vertices.get(i); if(s.partOf(v[0], v[1], v[2]) != FocusFilter.Part.SPIKES) continue; spikeCubes++; spikesLast &= sl.layerAt(v[0], v[1], v[2]) == 4; }
		check(spikeCubes > 0 && spikesLast, "spikes: every spike cube in the spike layer (4; no patches reach it by default): " + spikeCubes + " cubes");
		setProp(s, "propertySpikes", 0); generate(s);
		check(blocks(s).equals(plain) && on.vertices.size() == off.vertices.size(), "same blocks and same vertex count as with Layers off");

		System.out.println("-- the blocks do not change (GOLDEN islands, Layers off and on)");
		int same = 0, golden = 0, exact = 0, total = 0;
		List<Object[]> cases = new ArrayList<>();
		for(int i = 0; i < IslandGoldenTest.CASES.length; i++) cases.add(new Object[]{IslandGoldenTest.CASES[i], IslandGoldenTest.GOLDEN[i]});
		for(int i = 0; i < IslandGoldenSpikesTest.CASES.length; i++) cases.add(new Object[]{IslandGoldenSpikesTest.CASES[i], IslandGoldenSpikesTest.GOLDEN[i]});
		for(Object[] c: cases){
			Params p = (Params) c[0]; ShapeIsland g = new TestIsland(); set(g); apply(g, p); total++;
			setProp(g, "propertyLayers", false); generate(g); String hOff = hash(g.getExpectedBlocks());
			setProp(g, "propertyLayers", true); generate(g); String hOn = hash(g.getExpectedBlocks());
			List<Long> geo = new ArrayList<>(); Method params = ShapeIsland.class.getDeclaredMethod("params"); params.setAccessible(true);
			IslandGeometry.enumerate((Params) params.invoke(g), (x, y, z) -> geo.add(LocalPos.pack(x, y, z)));
			if(hOff.equals(hOn) && hOn.equals(hash(geo))) same++;
			boolean floatExact = (float) p.roundness == p.roundness && (float) p.rotationDeg == p.rotationDeg && (float) p.edgeAmplitude == p.edgeAmplitude && (float) p.edgeScale == p.edgeScale && (float) p.sharpness == p.sharpness && (float) p.roughness == p.roughness;
			if(floatExact){ exact++; if(hOn.equals(c[1])) golden++; }
		}
		check(same == total, "all " + total + " GOLDEN islands (15 without and 16 with spikes): Layers on = Layers off = the geometry, hash for hash (" + same + " of " + total + ")");
		check(exact > 0 && golden == exact, "and the " + exact + " whose values are exact as floats (the panel stores floats) hash to their GOLDEN with Layers on (" + golden + " of " + exact + ")");

		System.out.println("-- persistence");
		ShapeIsland fresh = new TestIsland();
		check(fresh.properties.size() >= 38 && fresh.toPersistence().contains(",Runnable,ATTACHED,2,2,false,25,50,75"), "Layers, Cut 1 %, Cut 2 %, Cut 3 % appended after Gap, later values follow (default off, 25/50/75)");
		String old = fresh.toPersistence(); old = old.substring(0, old.indexOf(",false,25,50,75"));
		check(old.split(",").length == 34, "an island saved before the layers: 34 values");
		ShapeIsland loaded = new TestIsland(); loaded.restorePersistence(old); set(loaded); generate(loaded);
		ShapeIsland ref = new TestIsland(); set(ref); generate(ref);
		check(!loaded.error && loaded.toPersistence().startsWith(old + ",false,25,50,75") && !loaded.hasLayers() && loaded.getLayers() == null && blocks(loaded).equals(blocks(ref)), "it loads with Layers off and the default cuts, and generates the same island");
		ShapeIsland saved = new TestIsland(); setProp(saved, "propertyLayers", true); setProp(saved, "propertyCut1", 10); setProp(saved, "propertyCut2", 40); setProp(saved, "propertyCut3", 90);
		ShapeIsland back = new TestIsland(); back.restorePersistence(saved.toPersistence());
		check(!back.error && back.hasLayers() && prop(back, "propertyCut1").equals(10) && prop(back, "propertyCut2").equals(40) && prop(back, "propertyCut3").equals(90), "the old cuts (10/40/90) are still saved and loaded where they were, though nothing reads them");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
