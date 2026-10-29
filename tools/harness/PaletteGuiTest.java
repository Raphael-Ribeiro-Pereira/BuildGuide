import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import brentmaas.buildguide.common.property.*;
import brentmaas.buildguide.common.screen.ValidationListComponent;
import brentmaas.buildguide.common.screen.ValidationListComponent.Category;
import brentmaas.buildguide.common.shape.*;
// Palette per layer (4D), what is shown: the Layers section in compact rows (fits 270 and 360, labels within
// 76 px), the wrong-layer colour, filter, tab, counts and list rows, wrong blocks among the errors, the world
// overlay, and the preview's rebuild key (one rebuild per change).
public class PaletteGuiTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static int w(String s){ return LabelWidthTest.width(s); }
	static String lang(String json, String key){ Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\": \"([^\"]*)\"").matcher(json); return m.find() ? m.group(1) : null; }
	static double lum(int rgb){ double[] c = new double[3]; for(int i = 0; i < 3; i++){ double v = ((rgb >> (16 - 8 * i)) & 0xFF) / 255.0; c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); } return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
	static double hue(int rgb){ float[] h = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null); return h[0] * 360; }
	static class Recorder implements IShapeBuffer { int r, g, b; final List<int[]> cubes = new ArrayList<>(); int n = 0;
		public void setColour(int r, int g, int b, int a){ this.r = r; this.g = g; this.b = b; } public void pushVertex(double x, double y, double z){ if(n++ % 24 == 0) cubes.add(new int[]{r, g, b}); } public void end(){} public void close(){} }

	public static void main(String[] a) throws Exception {
		brentmaas.buildguide.common.BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		brentmaas.buildguide.common.BuildGuide.screenHandler = new brentmaas.buildguide.common.screen.AbstractScreenHandler(){ public brentmaas.buildguide.common.screen.IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");

		System.out.println("-- the Layers section");
		ShapeIsland s = new ShapeIsland(); Field lf = ShapeIsland.class.getDeclaredField("propertyLayers"); lf.setAccessible(true);
		check(s.getSectionCount() == 3 && s.countRows(1) == 5, "Layers are out of the Island for now (2026-10-28): 3 tabs, Body shows its 5 rows and no layer row");
		((Property<Object>) lf.get(s)).value = true;
		check(s.countRows(1) == 5, "even with the box on: no layer row in the Island's panel");
		check(PropertyCompactRangeInt.columnX(2) + PropertyCompactRangeInt.columnWidth == Property.rowWidth && PropertyCompactRangeInt.buttonWidth * 2 + PropertyCompactRangeInt.textFieldWidth == PropertyCompactRangeInt.columnWidth, "compact columns (- 10, field 26, + 10): the second and third end exactly at the row's 184 px");
		for(String k: new String[]{"layercount", "patches"}){ String t = lang(json, "property.buildguide." + k); check(w(t) <= PropertyCompactRangeInt.columnX(1) - 4, "\"" + t + "\": " + w(t) + " px before its first column (x " + PropertyCompactRangeInt.columnX(1) + ")"); }
		check(w(lang(json, "property.buildguide.editlayer")) <= 50 - 4, "\"" + lang(json, "property.buildguide.editlayer") + "\" before the layer's colour (x 50)");
		for(String k: new String[]{"block1", "block2", "block3", "block4", "block5", "mix", "spikelayer", "layer.blocks", "layer.bottom", "layer.top", "layer.base"}){ String t = lang(json, "property.buildguide." + k); check(t != null && w(t) <= 76, "\"" + t + "\": " + w(t) + " px (limit 76)"); }
		check(w(lang(json, "property.buildguide.layer.rest")) <= Property.rowWidth - 4, "\"" + lang(json, "property.buildguide.layer.rest") + "\" fits its row"); 
		check(w(lang(json, "property.buildguide.hand")) <= PropertyBlockId.handWidth - 4 && PropertyBlockId.handX + PropertyBlockId.handWidth == Property.rowWidth, "\"" + lang(json, "property.buildguide.hand") + "\" (" + w(lang(json, "property.buildguide.hand")) + " px) in its " + PropertyBlockId.handWidth + "-px button, after the 72-px id field");
		ShapeIsland cuts = new ShapeIsland(); Field c1 = ShapeIsland.class.getDeclaredField("propertyCut1"); c1.setAccessible(true);
		PropertyCompactRangeInt cut1 = (PropertyCompactRangeInt) c1.get(cuts);
		check(!cut1.setValueFromString("3") && !cut1.setValueFromString("99") && cut1.setValueFromString("40") && cut1.value == 40, "a cut column keeps the range 5..95 (\"3\" and \"99\" refused when loading)");
		String old4C = new ShapeIsland().toPersistence();
		check(old4C.contains(",false,25,50,75,"), "the cuts persist as before (25, 50, 75 after Layers)");

		System.out.println("-- colour");
		int orange = PreviewColours.WRONG_LAYER;
		Set<Integer> others = new HashSet<>(Arrays.asList(PreviewColours.OK, PreviewColours.MISSING, PreviewColours.IGNORED, PreviewColours.ERROR, PreviewColours.WHITE, ShapeLayers.LAYER_1, ShapeLayers.LAYER_2, ShapeLayers.LAYER_3, ShapeLayers.LAYER_4, ShapeLayers.LAYER_5));
		check(others.size() == 10, "the five layer colours differ from each other and from the five status colours");
		check(!others.contains(orange) && PreviewColours.forStatus(ValidationState.WRONG_LAYER) == orange && PreviewColours.forStatus(ValidationState.WRONG_BLOCK) == PreviewColours.ERROR, String.format("wrong layer is orange %06X (no status or layer colour); a wrong block is red like the errors", orange));
		double l1 = lum(orange), l2 = lum(PreviewColours.IGNORED), ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05), dh = Math.abs(hue(orange) - hue(PreviewColours.IGNORED));
		check(dh >= 15, String.format("against IGNORED's yellow %06X: hue %.0f vs %.0f degrees (%.0f apart), luminance contrast %.2f:1 (the hue tells them apart)", PreviewColours.IGNORED, hue(orange), hue(PreviewColours.IGNORED), dh, ratio));
		check(Math.abs(hue(orange) - hue(ShapeLayers.LAYER_3)) < 10 && lum(orange) > 3 * lum(ShapeLayers.LAYER_3), String.format("against the brown layer %06X (a close hue): %.1f times brighter", ShapeLayers.LAYER_3, lum(orange) / lum(ShapeLayers.LAYER_3)));

		System.out.println("-- filter");
		check(PreviewFilter.values()[PreviewFilter.values().length - 1] == PreviewFilter.WRONG_LAYER, "Wrong layer is the last filter");
		check(PreviewFilter.WRONG_LAYER.showsStatus(ValidationState.WRONG_LAYER) && !PreviewFilter.WRONG_LAYER.showsStatus(ValidationState.OK) && !PreviewFilter.WRONG_LAYER.showsErrors(), "it shows only the wrong-layer blocks");
		check(PreviewFilter.ERRORS.showsStatus(ValidationState.WRONG_BLOCK) && PreviewFilter.ERRORS.showsErrors() && !PreviewFilter.ERRORS.showsStatus(ValidationState.WRONG_LAYER), "Errors only: the structure errors and the wrong blocks on the shape");
		check(PreviewFilter.UNVALIDATED.next(false) == PreviewFilter.ALL && PreviewFilter.UNVALIDATED.next(true) == PreviewFilter.WRONG_LAYER && PreviewFilter.WRONG_LAYER.next(true) == PreviewFilter.ALL, "the filter button offers Wrong layer only with a palette");
		check(w(lang(json, "screen.buildguide.filter.wronglayer")) <= 86 - 8, "\"" + lang(json, "screen.buildguide.filter.wronglayer") + "\" fits the 86-px filter button");

		System.out.println("-- list, tabs, counts, overlay (a validated shape with a palette)");
		ShapeCuboid shape = new ShapeCuboid(); ValidationState st = shape.getValidationState();
		// Bands of 2 blocks over a body 8 deep (the old Depth 8 with 25/50/75)
		ShapeLayers layers = ShapeLayers.uniformDepth(new ShapeLayers.Spec(ShapeLayers.Axis.DEPTH, 4, 4, 0, 4, new int[]{2, 2, 2, 0, 0}, new int[5], new int[]{3, 3, 3, 3, 3}, 1), 0, 8);
		check(ValidationListComponent.tabs(shape).length == 3, "no palette: the three tabs of before");
		st.setPalette(new LayerPalette(layers, new int[]{10, 20, LayerPalette.NONE, LayerPalette.NONE}));
		List<Long> exp = new ArrayList<>(); for(int y = 0; y >= -7; y--) exp.add(LocalPos.pack(0, y, 0));
		st.beginScan(exp, 100, 64, 0);
		st.setStatus(exp.get(0), ValidationState.OK, null); st.setStatus(exp.get(1), ValidationState.WRONG_LAYER, "Dirt");
		st.setStatus(exp.get(2), ValidationState.WRONG_BLOCK, "Oak Planks"); st.setStatus(exp.get(3), ValidationState.WRONG_LAYER, "Stone");
		for(int i = 4; i < 8; i++) st.setStatus(exp.get(i), ValidationState.MISSING, null);
		st.setNearBlocks(Arrays.asList(new ValidationState.NearBlock(LocalPos.pack(1, 0, 0), "Dirt", 1f))); st.endScan();
		Category[] tabs = ValidationListComponent.tabs(shape); int[] counts = ValidationListComponent.counts(shape);
		check(tabs.length == 4 && tabs[3] == Category.WRONG_LAYER, "with a palette: a fourth tab, Wrong layer, last");
		check(counts[0] == 2 && counts[1] == 4 && counts[2] == 0 && counts[3] == 2, "counts: errors 2 (1 structure error + 1 wrong block), missing 4, ignored 0, wrong layer 2: " + Arrays.toString(counts));
		ValidationListComponent.Entries wl = ValidationListComponent.buildEntries(shape, Category.WRONG_LAYER), er = ValidationListComponent.buildEntries(shape, Category.ERRORS);
		check(wl.titles.size() == 2 && wl.titles.get(0).getTranslationKey().equals("[100, 61, 0] Stone (layer 2)") && wl.titles.get(1).getTranslationKey().equals("[100, 63, 0] Dirt (layer 1)"), "Wrong layer rows, sorted by position: the position, the block found, its layer: " + wl.titles.get(0).getTranslationKey() + " / " + wl.titles.get(1).getTranslationKey());
		check(er.titles.size() == 2 && er.titles.get(0).getTranslationKey().startsWith("[101, 64, 0] Dirt (d=") && er.titles.get(1).getTranslationKey().equals("[100, 62, 0] Oak Planks (layer 2)"), "Errors rows: the structure errors first, then the wrong blocks on the shape: " + er.titles);
		for(String k: new String[]{"errors", "missing", "ignored", "wronglayer"}){ String t = lang(json, "screen.buildguide.tab4." + k).replace("%s", "99999"); check(w(t) <= 72 - 4, "four tabs share 288 px: \"" + t + "\" " + w(t) + " px in 72"); }
		String legend = lang(json, "screen.buildguide.legend.wronglayer");
		check(w(legend) <= 288 / 5 - 16 - 1, "legend by status with a palette: five 57-px entries, \"" + legend + "\" " + w(legend) + " px after the swatch");
		Recorder rec = new Recorder(); ValidationOverlay.build(rec, st, null);
		int orangeCubes = 0, redCubes = 0; for(int[] cube: rec.cubes){ if(cube[0] == 255 && cube[1] == 140 && cube[2] == 26) orangeCubes++; if(cube[0] == 255 && cube[1] == 60 && cube[2] == 60) redCubes++; }
		check(orangeCubes == 2 && redCubes == 2 && ValidationOverlay.hasContent(st), "world overlay: 2 orange shells (wrong layer), 2 red (the structure error and the wrong block)");

		System.out.println("-- preview rebuild key");
		PreviewModel built = PreviewModel.of(exp, 1).withValidation(st);
		check(built.colourAt(1) == orange && built.colourAt(2) == PreviewColours.ERROR, "the preview colours wrong layer orange and a wrong block red");
		check(PreviewModel.meshChange(built, built.withValidation(st)) == null, "same validation version: no rebuild");
		st.setStatus(exp.get(4), ValidationState.WRONG_LAYER, "Dirt");
		PreviewModel next = built.withValidation(st);
		check("validation".equals(PreviewModel.meshChange(built, next)) && PreviewModel.meshChange(next, next.withValidation(st)) == null, "a block turning wrong-layer: one rebuild (validation), then none");
		PreviewModel onlyWrong = next.withView(PreviewFilter.WRONG_LAYER, PreviewModel.SLICE_OFF, 0); int shown = 0; for(int i = 0; i < onlyWrong.positions.length; i++) if(onlyWrong.shows(i)) shown++;
		check(shown == 3 && "filter".equals(PreviewModel.meshChange(next, onlyWrong)), "filter Wrong layer: the 3 wrong-layer blocks, one rebuild (filter)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
