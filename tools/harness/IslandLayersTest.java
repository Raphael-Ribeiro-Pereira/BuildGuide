import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandGeometry.Params;
// Layers and colours (palette stage 4C, visual only): the layer of a block from its depth below the top
// (edges on the cuts, Depth 0, deep spikes), the cuts kept in order with the automatic adjustment, the
// legend's depth ranges, the world colours (layer colour, the shape's alpha), and the proof that the
// blocks do not change: Layers on and off give the same set as the geometry the GOLDENs pin. Old islands
// load with Layers off.
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
		brentmaas.buildguide.common.BuildGuide.widgetHandler = new PresetTest.FakeWidgets(); // setValue touches the widgets
		System.out.println("-- the layer of a block");
		ShapeLayers l = new ShapeLayers(0, 20, 25, 50, 75);
		check(l.layerOf(0) == 1 && l.layerOf(-4) == 1, "Depth 20, cuts 25/50/75: depths 0 to 4 are layer 1");
		check(l.layerOf(-5) == 2 && l.layerOf(-10) == 3 && l.layerOf(-15) == 4, "exactly on a cut (5, 10, 15 = 25, 50, 75 % of 20): the deeper layer");
		check(l.layerOf(-9) == 2 && l.layerOf(-14) == 3 && l.layerOf(-20) == 4, "just above each next cut: 9 -> 2, 14 -> 3; the bottom (20) -> 4");
		check(l.layerOf(-21) == 4 && l.layerOf(-60) == 4, "below Depth (a spike 40 under the body): layer 4");
		ShapeLayers seven = new ShapeLayers(0, 7, 25, 50, 75);
		check(seven.layerOf(-1) == 1 && seven.layerOf(-2) == 2 && seven.layerOf(-3) == 2 && seven.layerOf(-4) == 3 && seven.layerOf(-5) == 3 && seven.layerOf(-6) == 4, "Depth 7 (cuts at 1.75, 3.5, 5.25): 0, 1 | 2, 3 | 4, 5 | 6 and below, in integers");
		ShapeLayers flat = new ShapeLayers(0, 0, 25, 50, 75);
		check(flat.layerOf(0) == 1 && flat.layerOf(-1) == 1 && flat.layerOf(-40) == 1, "Depth 0: everything layer 1, spikes included");
		check(ShapeLayers.colour(1) == ShapeLayers.LAYER_1 && ShapeLayers.colour(4) == ShapeLayers.LAYER_4, "colour by layer");
		Set<Integer> palette = new HashSet<>(Arrays.asList(ShapeLayers.LAYER_1, ShapeLayers.LAYER_2, ShapeLayers.LAYER_3, ShapeLayers.LAYER_4));
		Set<Integer> status = new HashSet<>(Arrays.asList(PreviewColours.OK, PreviewColours.MISSING, PreviewColours.IGNORED, PreviewColours.ERROR, PreviewColours.WHITE));
		palette.retainAll(status);
		check(palette.isEmpty() && new HashSet<>(Arrays.asList(ShapeLayers.LAYER_1, ShapeLayers.LAYER_2, ShapeLayers.LAYER_3, ShapeLayers.LAYER_4)).size() == 4, String.format("four distinct colours, none a status colour: %06X cyan, %06X magenta, %06X brown, %06X blue", ShapeLayers.LAYER_1, ShapeLayers.LAYER_2, ShapeLayers.LAYER_3, ShapeLayers.LAYER_4));

		System.out.println("-- the cuts stay in order (automatic adjustment)");
		check(Arrays.equals(ShapeLayers.normalize(60, 50, 75, 0), new int[]{60, 65, 75}), "Cut 1 typed 60 over Cut 2 50: Cut 2 goes to 65, Cut 3 stays 75");
		check(Arrays.equals(ShapeLayers.normalize(25, 50, 20, 2), new int[]{10, 15, 20}), "Cut 3 typed 20: Cut 2 down to 15, Cut 1 to 10");
		check(Arrays.equals(ShapeLayers.normalize(95, 50, 75, 0), new int[]{85, 90, 95}), "Cut 1 at 95: held at 85 so 90 and 95 still fit");
		check(Arrays.equals(ShapeLayers.normalize(5, 5, 5, 1), new int[]{5, 10, 15}), "all at 5, Cut 2 changed: 5 / 10 / 15");
		Random r = new Random(4); boolean ordered = true, kept = true;
		for(int i = 0; i < 2000; i++){
			int k = r.nextInt(3); int[] in = {r.nextInt(110) - 5, r.nextInt(110) - 5, r.nextInt(110) - 5}; int[] c = ShapeLayers.normalize(in[0], in[1], in[2], k);
			ordered &= c[0] >= 5 && c[2] <= 95 && c[1] - c[0] >= 5 && c[2] - c[1] >= 5;
			kept &= c[k] == Math.max(5 + 5 * k, Math.min(95 - 5 * (2 - k), in[k]));
		}
		check(ordered && kept, "2000 random cuts: always in 5..95, each at least 5 above the one before; the changed one keeps its value when it fits");
		ShapeIsland panel = new TestIsland();
		setProp(panel, "propertyCut1", 60); Method onCut = ShapeIsland.class.getDeclaredMethod("onCutChanged", int.class); onCut.setAccessible(true); onCut.invoke(panel, 0);
		check(prop(panel, "propertyCut1").equals(60) && prop(panel, "propertyCut2").equals(65) && prop(panel, "propertyCut3").equals(75), "through the Island: Cut 1 typed 60 moves Cut 2 to 65");

		System.out.println("-- the legend's depth ranges");
		ShapeLayers d24 = new ShapeLayers(0, 24, 25, 50, 75);
		check(Arrays.equals(d24.depthRange(1), new int[]{0, 5}) && Arrays.equals(d24.depthRange(2), new int[]{6, 11}) && Arrays.equals(d24.depthRange(3), new int[]{12, 17}) && Arrays.equals(d24.depthRange(4), new int[]{18, -1}), "Depth 24, 25/50/75: 0-5, 6-11, 12-17, 18+");
		ShapeLayers d2 = new ShapeLayers(0, 2, 25, 50, 75);
		check(Arrays.equals(d2.depthRange(1), new int[]{0, 0}) && d2.depthRange(2) == null && Arrays.equals(d2.depthRange(3), new int[]{1, 1}) && Arrays.equals(d2.depthRange(4), new int[]{2, -1}), "Depth 2: layer 2 holds no block depth (shown as -)");
		check(Arrays.equals(flat.depthRange(1), new int[]{0, -1}) && flat.depthRange(2) == null && flat.depthRange(4) == null, "Depth 0: layer 1 is everything");
		boolean agree = true;
		for(int i = 0; i < 500; i++){
			int[] c = ShapeLayers.normalize(r.nextInt(91) + 5, r.nextInt(91) + 5, r.nextInt(91) + 5, r.nextInt(3)); ShapeLayers x = new ShapeLayers(0, r.nextInt(81), c[0], c[1], c[2]);
			for(int dep = 0; dep <= x.depth + 5; dep++){ int layer = x.layerOf(-dep); int[] range = x.depthRange(layer); agree &= range != null && dep >= range[0] && (range[1] == -1 || dep <= range[1]); }
		}
		check(agree, "500 random layer sets: every depth lies in the range its layer reports");

		System.out.println("-- world colours");
		ShapeIsland s = new TestIsland(); set(s);
		ColourBuffer off = generate(s);
		boolean shapeColour = true; for(int[] v: off.vertices) shapeColour &= v[3] == 25 && v[4] == 51 && v[5] == 76 && v[6] == 76;
		check(shapeColour && s.getLayers() == null && !s.hasLayers(), "Layers off: every cube in the shape's colour, as before; no layers reported");
		Set<Long> plain = blocks(s);
		setProp(s, "propertyLayers", true);
		ColourBuffer on = generate(s);
		boolean layerColour = true; int[] seen = new int[5]; ShapeLayers mine = s.getLayers();
		for(int i = 0; i < on.vertices.size(); i++){ int[] v = on.vertices.get(i); int[] first = on.vertices.get(i - i % 24); int layer = mine.layerOf(first[1]); int rgb = ShapeLayers.colour(layer); seen[layer]++;
			layerColour &= v[3] == (rgb >> 16 & 0xFF) && v[4] == (rgb >> 8 & 0xFF) && v[5] == (rgb & 0xFF) && v[6] == 76; }
		check(layerColour && seen[1] > 0 && seen[2] > 0 && seen[3] > 0 && seen[4] > 0, "Layers on: each cube's 24 vertices in its layer's colour, the shape's alpha kept (76); all four layers present");
		check(s.hasLayers() && mine.equals(new ShapeLayers(0, 24, 25, 50, 75)), "the Island reports the layers of its generation (Depth 24, 25/50/75)");
		check(blocks(s).equals(plain) && on.vertices.size() == off.vertices.size(), "same blocks and same vertex count as with Layers off");

		System.out.println("-- the blocks do not change (GOLDEN islands, Layers off and on)");
		int same = 0, golden = 0, exact = 0, total = 0;
		List<Object[]> cases = new ArrayList<>();
		for(int i = 0; i < IslandGoldenTest.CASES.length; i++) cases.add(new Object[]{IslandGoldenTest.CASES[i], IslandGoldenTest.GOLDEN[i]});
		for(int i = 0; i < IslandGoldenSpikesTest.CASES.length; i++) cases.add(new Object[]{IslandGoldenSpikesTest.CASES[i], IslandGoldenSpikesTest.GOLDEN[i]});
		for(Object[] c: cases){
			Params p = (Params) c[0]; ShapeIsland g = new TestIsland(); set(g); apply(g, p); total++;
			setProp(g, "propertyLayers", false); generate(g); String hOff = hash(g.getExpectedBlocks());
			setProp(g, "propertyLayers", true); setProp(g, "propertyCut1", 10 + total % 3 * 5); generate(g); String hOn = hash(g.getExpectedBlocks());
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
		check(fresh.properties.size() == 38 && fresh.toPersistence().endsWith(",false,25,50,75"), "38 persisted properties: Layers, Cut 1 %, Cut 2 %, Cut 3 % appended after Gap (default off, 25/50/75)");
		String old = fresh.toPersistence(); old = old.substring(0, old.length() - ",false,25,50,75".length());
		check(old.split(",").length == 34, "an island saved before the layers: 34 values");
		ShapeIsland loaded = new TestIsland(); loaded.restorePersistence(old); set(loaded); generate(loaded);
		ShapeIsland ref = new TestIsland(); set(ref); generate(ref);
		check(!loaded.error && loaded.toPersistence().equals(old + ",false,25,50,75") && !loaded.hasLayers() && loaded.getLayers() == null && blocks(loaded).equals(blocks(ref)), "it loads with Layers off and the default cuts, and generates the same island");
		ShapeIsland saved = new TestIsland(); setProp(saved, "propertyLayers", true); setProp(saved, "propertyCut1", 10); setProp(saved, "propertyCut2", 40); setProp(saved, "propertyCut3", 90);
		ShapeIsland back = new TestIsland(); back.restorePersistence(saved.toPersistence());
		check(!back.error && back.hasLayers() && prop(back, "propertyCut1").equals(10) && prop(back, "propertyCut2").equals(40) && prop(back, "propertyCut3").equals(90), "Layers on with cuts 10/40/90: saved and loaded");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
