import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.ShapeLayout;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.ShapeLayers.Axis;
import brentmaas.buildguide.common.shape.ShapeLayers.Spec;
// Layers (2026-10-27): the strata of a piece of ground (Depth, the Island) and the zones by height (Elevation, the
// Terrain), one engine. Bands from the top in blocks, from the bottom in percent, the rest between; thin
// columns; five layers; a layer with no band; the spikes' layer; the mix across boundaries (its share by
// distance, only near them); patches (their share, only in the neighbouring bands, a layer of patches only);
// the Elevation's whole column; the legend and its width; equality and determinism. Then the shared panel rows
// (LayerControls): at most what the accordion fits in every state, the count keeping the rest, the edited layer
// and the spike layer in range, the thickness row's unit, and the values saved after the old ones.
public class StrataTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static Spec spec(Axis axis, int count, int rest, int mix, int spike, int[] t, int[] p, int[] s, long seed){ return new Spec(axis, count, rest, mix, spike, t, p, s, seed); }
	static Spec depth(int count, int rest, int mix, int spike, int[] t, int[] p){ return spec(Axis.DEPTH, count, rest, mix, spike, t, p, new int[]{3, 3, 3, 3, 3}, 7); }
	static String layers(ShapeLayers l, int from, int to){ StringBuilder sb = new StringBuilder(); for(int d = from; d <= to; d++) sb.append(l.layerAt(0, -d, 0)); return sb.toString(); }
	static Object field(Object o, String f) throws Exception { Class<?> c = o.getClass(); while(c != null){ try { Field x = c.getDeclaredField(f); x.setAccessible(true); return x.get(o); } catch(NoSuchFieldException e){ c = c.getSuperclass(); } } throw new NoSuchFieldException(f); }
	@SuppressWarnings("unchecked") static Property<Object> prop(Object o, String f) throws Exception { return (Property<Object>) field(o, f); }
	static void call(Object o, String m) throws Exception { Method x = o.getClass().getDeclaredMethod(m); x.setAccessible(true); x.invoke(o); }
	static String nameKey(Property<?> p) throws Exception { Field f = Property.class.getDeclaredField("name"); f.setAccessible(true); return ((AbstractScreenHandler.Translatable) f.get(p)).getTranslationKey(); }

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.config = new brentmaas.buildguide.common.Config(Files.createTempDirectory("bgstrata").toFile());

		System.out.println("-- Depth: the bands of a column");
		int[] none = {0, 0, 0, 0, 0};
		Spec island = depth(4, 3, 0, 4, new int[]{1, 3, 0, 25, 10}, none);
		ShapeLayers l24 = ShapeLayers.uniformDepth(island, 0, 24);
		check(layers(l24, 0, 24).equals("1222" + "333333333333333" + "444444"), "body 24 (25 blocks): 1 block, 3 blocks, the rest (15), the bottom 25 % (6): " + layers(l24, 0, 24));
		check(l24.layerAt(0, -25, 0) == 4 && l24.layerAt(0, -60, 0) == 4 && l24.layerAt(3, -30, -5) == 4, "under the body (the spikes): the spike layer (4)");
		check(ShapeLayers.uniformDepth(depth(4, 3, 0, 2, new int[]{1, 3, 0, 25, 10}, none), 0, 24).layerAt(0, -30, 0) == 2, "Spike layer 2: the spikes take layer 2");
		check(layers(ShapeLayers.uniformDepth(island, 0, 2), 0, 2).equals("124"), "a column of 3: the top and the bottom first (1, 4), then the next one in (2); no rest: " + layers(ShapeLayers.uniformDepth(island, 0, 2), 0, 2));
		check(layers(ShapeLayers.uniformDepth(island, 0, 1), 0, 1).equals("14") && layers(ShapeLayers.uniformDepth(island, 0, 0), 0, 0).equals("1"), "columns of 2 and of 1: 1 4, and 1");
		ShapeLayers five = ShapeLayers.uniformDepth(depth(5, 3, 0, 5, new int[]{1, 3, 0, 25, 10}, none), 0, 24);
		check(layers(five, 0, 24).equals("1222" + "333333333333" + "444444" + "555"), "five layers: 4 at 25 % (6) above 5 at 10 % (3), both from the bottom: " + layers(five, 0, 24));
		check(layers(ShapeLayers.uniformDepth(depth(4, 3, 0, 4, new int[]{1, 0, 0, 25, 0}, none), 0, 24), 0, 24).indexOf('2') < 0, "a layer of thickness 0 (and no patches) is nowhere");
		check(layers(ShapeLayers.uniformDepth(depth(3, 1, 0, 3, new int[]{0, 40, 20, 0, 0}, none), 0, 9), 0, 9).equals("1111" + "2222" + "33"), "the rest on top: the others from the bottom, 20 % (2) and 40 % (4): " + layers(ShapeLayers.uniformDepth(depth(3, 1, 0, 3, new int[]{0, 40, 20, 0, 0}, none), 0, 9), 0, 9));
		int[] body = new int[9]; Arrays.fill(body, -1); body[4] = 24; body[7] = 4; ShapeLayers grid = ShapeLayers.depth(island, 0, body, 1);
		check(grid.layerAt(0, -3, 0) == 2 && grid.layerAt(1, -3, 0) == 2 && grid.layerAt(1, -4, 0) == 4 && grid.layerAt(0, -40, 0) == 4 && grid.layerAt(-1, 0, -1) == 4 && grid.layerAt(9, 0, 9) == 4, "per column (a 3 x 3 grid): each its own depth (24, and 4: 1, 2 2 2, 4); off the plan or the grid (a loose piece) the spike layer");

		System.out.println("-- the mix across boundaries");
		ShapeLayers mixed = ShapeLayers.uniformDepth(depth(4, 3, 2, 4, new int[]{1, 3, 0, 25, 10}, none), 0, 24);
		int samples = 4000; int[] lower = new int[25];
		for(int d = 0; d <= 24; d++) for(int i = 0; i < samples; i++){ int x = i % 63 - 31, z = i / 63; if(mixed.layerAt(x, -d, z) == 4) lower[d]++; }
		double s17 = lower[17] / (double) samples, s18 = lower[18] / (double) samples, s19 = lower[19] / (double) samples, s20 = lower[20] / (double) samples;
		check(lower[16] == 0 && lower[21] == samples && lower[15] == 0, "Mix 2: two blocks either side of the 3/4 boundary (depth 19) mix; depth 16 is all 3, 21 all 4");
		check(s17 > 0.05 && s17 < 0.25 && s18 > 0.25 && s18 < 0.5 && s19 > 0.5 && s19 < 0.75 && s20 > 0.75 && s20 < 0.95, String.format(Locale.ROOT, "the closer to the boundary, the more even: layer 4 at depths 17 to 20: %.2f %.2f %.2f %.2f", s17, s18, s19, s20));
		ShapeLayers mixedAgain = ShapeLayers.uniformDepth(depth(4, 3, 2, 4, new int[]{1, 3, 0, 25, 10}, none), 0, 24);
		boolean same = true, top = true; for(int i = 0; i < 2000; i++){ int x = i % 41, z = i / 41, d = i % 25; same &= mixed.layerAt(x, -d, z) == mixedAgain.layerAt(x, -d, z); }
		for(int i = 0; i < 2000; i++) top &= mixed.layerAt(i % 41, 0, i / 41) == 1 || mixed.layerAt(i % 41, 0, i / 41) == 2;
		check(same, "the same position always has the same layer (the world, the preview, the scan and Fill agree)");
		check(top, "a 1-block top band mixes only with the band under it");

		System.out.println("-- patches");
		ShapeLayers spots = ShapeLayers.uniformDepth(depth(4, 3, 0, 4, new int[]{1, 3, 0, 25, 10}, new int[]{0, 0, 0, 30, 0}), 0, 24);
		int inRest = 0, of = 0, inTop = 0;
		for(int i = 0; i < 6000; i++){ int x = i % 77, z = i / 77; for(int d = 4; d <= 18; d++){ of++; if(spots.layerAt(x, -d, z) == 4) inRest++; } for(int d = 0; d <= 3; d++) if(spots.layerAt(x, -d, z) == 4) inTop++; }
		double share = inRest / (double) of;
		check(share > 0.24 && share < 0.36 && inTop == 0, String.format(Locale.ROOT, "layer 4 with 30 %% patches: %.3f of the rest band above it, none in layers 1 and 2 (not next to it)", share));
		for(int size: new int[]{1, 8}){ ShapeLayers sz = ShapeLayers.uniformDepth(spec(Axis.DEPTH, 4, 3, 0, 4, new int[]{1, 3, 0, 25, 10}, new int[]{0, 0, 0, 20, 0}, new int[]{3, 3, 3, size, 3}, 7), 0, 24); int k = 0, n = 0; for(int i = 0; i < 6000; i++) for(int d = 4; d <= 18; d++){ n++; if(sz.layerAt(i % 77, -d, i / 77) == 4) k++; }
			check(Math.abs(k / (double) n - 0.20) < 0.07, String.format(Locale.ROOT, "size %d: still about the share asked (20 %%): %.3f", size, k / (double) n)); }
		ShapeLayers accent = ShapeLayers.uniformDepth(depth(5, 3, 0, 4, new int[]{1, 3, 0, 25, 0}, new int[]{0, 0, 0, 0, 25}), 0, 24);
		int[] where = new int[6]; for(int i = 0; i < 3000; i++) for(int d = 0; d <= 30; d++){ int got = accent.layerAt(i % 55, -d, i / 55); if(got == 5) where[d <= 3 ? 1 : d <= 18 ? 3 : 4]++; }
		check(where[1] == 0 && where[3] == 0 && where[4] > 0, "layer 5 as patches only (thickness 0): only in its neighbour band (4, and the spikes, also 4): " + Arrays.toString(where));

		System.out.println("-- Elevation: zones by height");
		Spec terrain = spec(Axis.ELEVATION, 4, 2, 0, 4, new int[]{15, 0, 30, 15, 10}, none, new int[]{3, 3, 3, 3, 3}, 3);
		StringBuilder byH = new StringBuilder(); for(int h = 0; h <= 30; h++) byH.append(ShapeLayers.elevation(terrain, 30, new int[]{h}, 0).layerAt(0, h, 0));
		check(byH.toString().equals("44444" + "333333333" + "222222222222" + "11111"), "Height 30 (31 heights): the lowest 15 % (5), then 30 % (9), the rest (12), the top 15 % (5): " + byH);
		ShapeLayers col = ShapeLayers.elevation(spec(Axis.ELEVATION, 4, 2, 2, 4, new int[]{15, 0, 30, 15, 10}, new int[]{0, 0, 40, 0, 0}, new int[]{3, 3, 3, 3, 3}, 3), 30, new int[]{17, 5, 26}, 0);
		boolean whole = true; for(int y = -3; y <= 17; y++) whole &= col.layerAt(0, y, 0) == col.layerAt(0, 17, 0);
		check(whole, "with mix and patches too, the whole skin of a column has one layer");
		check(ShapeLayers.elevation(terrain, 30, new int[]{-1}, 0).layerAt(0, 2, 0) == 4 && ShapeLayers.elevation(terrain, 30, new int[]{-1}, 0).layerAt(4, 29, 4) == 1, "off the plan: by the position's own height");

		System.out.println("-- legend");
		check(Arrays.equals(l24.legend(1), new int[]{ShapeLayers.LEGEND_BLOCKS, 1}) && Arrays.equals(l24.legend(3), new int[]{ShapeLayers.LEGEND_REST, 0}) && Arrays.equals(l24.legend(4), new int[]{ShapeLayers.LEGEND_BOTTOM, 25}) && l24.legend(5)[0] == ShapeLayers.LEGEND_NONE, "Island: 1 block, rest, 25 % from the bottom, no layer 5");
		ShapeLayers el = ShapeLayers.elevation(terrain, 30, new int[]{0}, 0);
		check(el.legend(1)[0] == ShapeLayers.LEGEND_TOP && el.legend(3)[0] == ShapeLayers.LEGEND_BASE && accent.legend(5)[0] == ShapeLayers.LEGEND_PATCHES, "Terrain: % from the top above the rest, from the base below; patches only");
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		int widest = 0; String widestText = "";
		for(String key: new String[]{"blocks", "percent", "rest", "patches", "empty"}){ java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"screen\\.buildguide\\.legend\\." + key + "\": \"([^\"]*)\"").matcher(json); check(m.find(), "legend text " + key); String t = String.format(m.group(1), "5", "100"); if(LabelWidthTest.width(t) > widest){ widest = LabelWidthTest.width(t); widestText = t; } }
		int entry = (ShapeScreen.previewX2(ShapeLayout.minWidth) - ShapeScreen.previewX1(false)) / ShapeLayers.COUNT;
		check(widest <= entry - ShapeScreen.legendTextX - 2, "the widest legend text (\"" + widestText + "\", " + widest + " px) fits a fifth of the legend (" + entry + " px) after its swatch");

		System.out.println("-- equality and seeds");
		int[] b2 = {24}; ShapeLayers e1 = ShapeLayers.depth(island, 0, b2, 0), e2 = ShapeLayers.depth(depth(4, 3, 0, 4, new int[]{1, 3, 0, 25, 10}, none), 0, b2, 0);
		check(e1.equals(e2) && !e1.equals(ShapeLayers.depth(island, 0, new int[]{24}, 0)) && !e1.equals(ShapeLayers.depth(depth(4, 3, 0, 4, new int[]{2, 3, 0, 25, 10}, none), 0, b2, 0)), "equal: the same spec over the same generation's columns; another generation's columns or another thickness are not");
		ShapeLayers seedA = ShapeLayers.uniformDepth(spec(Axis.DEPTH, 4, 3, 2, 4, new int[]{1, 3, 0, 25, 10}, new int[]{0, 0, 0, 30, 0}, new int[]{3, 3, 3, 3, 3}, 1), 0, 24), seedB = ShapeLayers.uniformDepth(spec(Axis.DEPTH, 4, 3, 2, 4, new int[]{1, 3, 0, 25, 10}, new int[]{0, 0, 0, 30, 0}, new int[]{3, 3, 3, 3, 3}, 2), 0, 24);
		int differ = 0; for(int i = 0; i < 3000; i++) if(seedA.layerAt(i % 50, -(i % 25), i / 50) != seedB.layerAt(i % 50, -(i % 25), i / 50)) differ++;
		check(differ > 100, "another seed: other mixes and patches (" + differ + " of 3000 positions differ)");

		System.out.println("-- the panel rows (LayerControls)");
		ShapeIsland is = new ShapeIsland(){ @Override public void update(){} }; prop(is, "propertyLayers").value = true; prop(is, "propertySpikes").value = 6;
		Object lc = field(is, "layerControls");
		int capacity = brentmaas.buildguide.common.screen.ShapeScreen.inspectorCapacity(ShapeLayout.minHeight, is.getSectionCount());
		int worstLayers = 0, worstSpikes = 0;
		for(int count = 2; count <= 5; count++) for(int rest = 1; rest <= count; rest++) for(int edit = 1; edit <= count; edit++){
			prop(lc, "count").value = count; prop(lc, "rest").value = rest; prop(lc, "edit").value = edit;
			worstLayers = Math.max(worstLayers, is.countRows(4)); worstSpikes = Math.max(worstSpikes, is.countRows(3)); }
		check(worstLayers <= capacity && worstLayers == 9 && worstSpikes <= capacity, "Island, every count, rest and edited layer: Layers " + worstLayers + " rows, Spike shape " + worstSpikes + ", capacity " + capacity + " at the minimum height");
		is.setOpenSection(4); prop(lc, "count").value = 5; prop(lc, "rest").value = 3; prop(lc, "edit").value = 2; is.onSelectedInGUI();
		Property<?>[] thick = (Property<?>[]) field(lc, "thickness");
		check(nameKey(thick[1]).equals("property.buildguide.layer.blocks"), "layer 2, above the rest: its thickness in blocks");
		prop(lc, "edit").value = 4; is.onSelectedInGUI();
		check(nameKey(thick[3]).equals("property.buildguide.layer.bottom"), "layer 4, below the rest: in % from the bottom");
		prop(lc, "edit").value = 5; prop(lc, "spikeLayer").value = 5; prop(lc, "count").value = 3; call(lc, "onCountChanged");
		check(prop(lc, "rest").value.equals(3) && prop(lc, "edit").value.equals(3) && prop(lc, "spikeLayer").value.equals(3), "count down to 3: the edited layer and the spike layer come down to 3, the rest stays 3");
		prop(lc, "count").value = 2; call(lc, "onCountChanged");
		check(prop(lc, "rest").value.equals(2) && prop(lc, "edit").value.equals(2), "count 2: the rest and the edited layer come down to 2");
		ShapeTerrain te = new ShapeTerrain(); prop(te, "propertyLayers").value = true; Object tc = field(te, "layerControls");
		int tcap = brentmaas.buildguide.common.screen.ShapeScreen.inspectorCapacity(ShapeLayout.minHeight, te.getSectionCount()), tworst = 0;
		for(int count = 2; count <= 5; count++) for(int rest = 1; rest <= count; rest++) for(int edit = 1; edit <= count; edit++){ prop(tc, "count").value = count; prop(tc, "rest").value = rest; prop(tc, "edit").value = edit; tworst = Math.max(tworst, te.countRows(2)); }
		check(tworst <= tcap && field(tc, "spikeLayer") == null, "Terrain: Layers " + tworst + " rows (capacity " + tcap + "), no spike layer");
		te.setOpenSection(2); prop(tc, "count").value = 4; prop(tc, "rest").value = 2; prop(tc, "edit").value = 1; te.onSelectedInGUI(); Property<?>[] tt = (Property<?>[]) field(tc, "thickness");
		check(nameKey(tt[0]).equals("property.buildguide.layer.top"), "Terrain, layer 1 (above the rest): % from the top");
		prop(tc, "edit").value = 3; te.onSelectedInGUI();
		check(nameKey(tt[2]).equals("property.buildguide.layer.base"), "Terrain, layer 3: % from the base");

		System.out.println("-- saved after the old values");
		ShapeIsland fresh = new ShapeIsland(); String iSave = fresh.toPersistence();
		check(fresh.properties.size() == 44 + 20 && iSave.endsWith(",4,3,2,,1,3,0,25,10,0,0,0,15,0,3,3,3,3,3,4"), "Island: 20 values after the 44 of before (count, rest, mix, block 5, 5 thicknesses, 5 patches, 5 sizes, spike layer): " + iSave.substring(iSave.length() - 44));
		ShapeIsland old = new ShapeIsland(); old.restorePersistence(iSave.substring(0, iSave.length() - ",4,3,2,,1,3,0,25,10,0,0,0,15,0,3,3,3,3,3,4".length()));
		check(!old.error && old.toPersistence().equals(iSave), "an island saved before (44 values) loads with the new defaults");
		ShapeIsland changed = new ShapeIsland(); Object cc = field(changed, "layerControls"); prop(cc, "count").value = 5; prop(cc, "mix").value = 4; ((Property<Object>[]) field(cc, "patches"))[2].value = 35; ((Property<Object>[]) field(cc, "blocks"))[4].value = "minecraft:tuff";
		ShapeIsland back = new ShapeIsland(); back.restorePersistence(changed.toPersistence());
		check(!back.error && back.toPersistence().equals(changed.toPersistence()) && back.toPersistence().contains("minecraft%3Atuff"), "five layers, mix 4, patches, block 5: saved and loaded");
		ShapeTerrain tf = new ShapeTerrain(); String tSave = tf.toPersistence();
		check(tf.properties.size() == 30 + 19 && tSave.endsWith(",4,2,1,,15,0,30,15,10,0,0,0,0,0,3,3,3,3,3"), "Terrain: 19 values after the 30 of before (no spike layer): " + tSave.substring(tSave.length() - 40));
		ShapeTerrain told = new ShapeTerrain(); told.restorePersistence(tSave.substring(0, tSave.length() - ",4,2,1,,15,0,30,15,10,0,0,0,0,0,3,3,3,3,3".length()));
		check(!told.error && told.toPersistence().equals(tSave), "a terrain saved before (30 values) loads with the new defaults");
		ShapeIsland pal = new ShapeIsland(); prop(pal, "propertyLayers").value = true; ((Property<Object>[]) field(field(pal, "layerControls"), "blocks"))[4].value = "minecraft:tuff";
		Method palette = field(pal, "layerControls").getClass().getDeclaredMethod("palette"); palette.setAccessible(true);
		check(Arrays.equals((String[]) palette.invoke(field(pal, "layerControls")), new String[]{"", "", "", "", ""}), "4 layers: block 5 is not in the palette (a layer not in use asks for nothing)");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
