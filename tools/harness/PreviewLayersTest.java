import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import brentmaas.buildguide.common.screen.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
// Layers in the preview (4C): "Colour: Status | Layer" colours the mesh by layer, filter and slice still
// apply, and the rebuild key holds the colour mode and the cuts: one rebuild per change, none for the
// same key. A shape without layers is always coloured by status. Frames as in PreviewRebuildTest.
public class PreviewLayersTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long now = 1000;
	static Method finish; static Field fe;
	static PreviewController c; static PreviewModel built = null, shown = null; static List<String> rebuilds = new ArrayList<>();

	// A shape that reports layers like the Island: those of its last generation
	static class Layered extends ShapeCuboid {
		ShapeLayers next, generated;
		@Override public boolean hasLayers(){ return next != null; }
		@Override public ShapeLayers getLayers(){ return generated; }
	}
	@SuppressWarnings("unchecked")
	static void generate(Shape s, long... blocks) throws Exception {
		s.lock.lock(); try { Set<Long> e = (Set<Long>) fe.get(s); e.clear(); for(long b: blocks) e.add(b); if(s instanceof Layered) ((Layered) s).generated = ((Layered) s).next; s.ready = false; s.error = false; finish.invoke(s); } finally { s.lock.unlock(); }
	}
	static void frames(Shape s, long millis){
		for(long t = 0; t < millis; t += 16){ now += 16; PreviewModel m = c.update(s); if(m == null) continue; shown = m; String r = PreviewModel.meshChange(built, m); if(r != null) rebuilds.add(r); built = m; }
	}
	static int drawn(PreviewModel m){ int n = 0; for(int i = 0; i < m.positions.length; i++) if(m.shows(i)) n++; return n; }

	public static void main(String[] a) throws Exception {
		finish = Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		fe = Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true);
		// A column 0..-23 (Depth 24, cuts 25/50/75: 6 blocks per layer)
		long[] column = new long[24]; for(int d = 0; d < 24; d++) column[d] = LocalPos.pack(0, -d, 0);
		ShapeLayers cuts = new ShapeLayers(0, 24, 25, 50, 75);

		System.out.println("-- the model");
		PreviewModel base = PreviewModel.of(Arrays.asList(column[0], column[6], column[12], column[18]), 1, cuts);
		check(base.colourMode == ColourMode.STATUS && base.colourAt(0) == PreviewColours.WHITE, "a snapshot is coloured by status (white, not validated)");
		PreviewModel byLayer = base.withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		check(byLayer.colourAt(0) == ShapeLayers.LAYER_1 && byLayer.colourAt(1) == ShapeLayers.LAYER_2 && byLayer.colourAt(2) == ShapeLayers.LAYER_3 && byLayer.colourAt(3) == ShapeLayers.LAYER_4, "Colour: Layer: depths 0, 6, 12, 18 in the four layer colours");
		PreviewModel plain = PreviewModel.of(Arrays.asList(column[0]), 1).withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		check(plain.colourMode == ColourMode.STATUS && plain.colourAt(0) == PreviewColours.WHITE, "a model without layers stays coloured by status");
		check(PreviewModel.meshChange(base, byLayer).equals("colour") && PreviewModel.meshChange(byLayer, base.withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER)) == null, "rebuild key: the colour mode (another instance in the same mode: no rebuild)");
		// Other cuts always come with a new generation; the key holds them anyway. Same positions, other cuts (set by reflection):
		PreviewModel twin = byLayer.withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		Field lf = PreviewModel.class.getDeclaredField("layers"); lf.setAccessible(true); lf.set(twin, new ShapeLayers(0, 24, 10, 50, 75));
		check("layers".equals(PreviewModel.meshChange(byLayer, twin)), "and the cuts: same positions with other cuts under Colour: Layer rebuilds, reason layers");

		System.out.println("-- through the controller (frames)");
		c = new PreviewController(() -> now);
		Layered s = new Layered(); s.next = cuts;
		generate(s, column); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("view")) && shown.colourMode == ColourMode.STATUS && shown.layers.equals(cuts), "first mesh by status (the default); the model carries the generation's layers: " + rebuilds);
		rebuilds.clear(); c.setColourMode(ColourMode.LAYER); frames(s, 500);
		check(rebuilds.equals(Arrays.asList("colour")) && shown.colourMode == ColourMode.LAYER, "Colour: Layer: exactly one rebuild, reason colour: " + rebuilds);
		rebuilds.clear(); c.setColourMode(ColourMode.LAYER); frames(s, 500);
		check(rebuilds.isEmpty(), "the same mode again: no rebuild");
		int[] perLayer = new int[5]; for(int i = 0; i < shown.positions.length; i++) for(int k = 1; k <= 4; k++) if(shown.colourAt(i) == ShapeLayers.colour(k)) perLayer[k]++;
		check(perLayer[1] == 6 && perLayer[2] == 6 && perLayer[3] == 6 && perLayer[4] == 6, "the column's 24 blocks: 6 per layer colour");
		rebuilds.clear(); c.setSlice(1, -7); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("slice")) && drawn(shown) == 1 && shown.colourMode == ColourMode.LAYER, "slice y = -7 under Colour: Layer: one rebuild, 1 block drawn, still by layer: " + rebuilds);
		rebuilds.clear(); c.setSlice(PreviewModel.SLICE_OFF, 0); c.setFilter(PreviewFilter.MISSING); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("filter")) && drawn(shown) == 0 && shown.isViewEmpty(), "slice off and filter Missing in the same frame: one rebuild (reason filter, checked first); nothing validated, so nothing drawn: the filter still applies by layer: " + rebuilds);
		c.setFilter(PreviewFilter.ALL); frames(s, 300);
		rebuilds.clear(); s.next = new ShapeLayers(0, 24, 40, 50, 75); generate(s, column); frames(s, 400);
		check(rebuilds.equals(Arrays.asList("generation")) && shown.layers.cut1 == 40, "cuts changed (a regeneration, as any shape change): one rebuild: " + rebuilds);
		rebuilds.clear(); c.setColourMode(ColourMode.STATUS); frames(s, 300);
		check(rebuilds.equals(Arrays.asList("colour")) && shown.colourMode == ColourMode.STATUS, "back to Colour: Status: one rebuild: " + rebuilds);
		c.setColourMode(ColourMode.LAYER); frames(s, 300);
		rebuilds.clear(); s.next = null; generate(s, column); frames(s, 400);
		check(rebuilds.equals(Arrays.asList("generation")) && shown.colourMode == ColourMode.STATUS && shown.layers == null, "Layers off (regenerated): coloured by status even with Layer chosen, one rebuild: " + rebuilds);
		rebuilds.clear(); c.setColourMode(ColourMode.STATUS); frames(s, 300); c.setColourMode(ColourMode.LAYER); frames(s, 300);
		check(rebuilds.isEmpty(), "and the toggle does nothing then (no rebuild)");
		Shape cube = new ShapeCuboid();
		check(!cube.hasLayers() && cube.getLayers() == null, "other shapes: no layers (Shape defaults)");

		System.out.println("-- labels");
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		for(String k: new String[]{"status", "layer"}){ Matcher m = Pattern.compile("\"screen\\.buildguide\\.colour\\." + k + "\": \"([^\"]*)\"").matcher(json); m.find();
			check(LabelWidthTest.width(m.group(1)) <= 80 - 8, "button \"" + m.group(1) + "\": " + LabelWidthTest.width(m.group(1)) + " px in an 80 px button"); }
		check(ShapeScreen.depthLabel(new int[]{0, 5}).equals("0-5") && ShapeScreen.depthLabel(new int[]{18, -1}).equals("18+") && ShapeScreen.depthLabel(new int[]{3, 3}).equals("3") && ShapeScreen.depthLabel(null).equals("-"), "legend texts: 0-5, 18+, 3, -");
		int widest = 0; String widestText = "";
		for(int depth = 0; depth <= IslandGeometry.maxDepth; depth++) for(int c1 = 5; c1 <= 85; c1 += 5) for(int c2 = c1 + 5; c2 <= 90; c2 += 5) for(int c3 = c2 + 5; c3 <= 95; c3 += 5){
			ShapeLayers x = new ShapeLayers(0, depth, c1, c2, c3); for(int k = 1; k <= 4; k++){ String t = ShapeScreen.depthLabel(x.depthRange(k)); if(LabelWidthTest.width(t) > widest){ widest = LabelWidthTest.width(t); widestText = t; } } }
		check(widest <= 72 - 16 - 2, "the widest legend text over every Depth and cut (\"" + widestText + "\", " + widest + " px) fits its 72-px entry after the swatch");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
