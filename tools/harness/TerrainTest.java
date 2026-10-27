import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.ShapeLayout;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.FocusFilter.Part;
import brentmaas.buildguide.common.shape.TerrainGeometry.*;
import brentmaas.buildguide.common.shape.TerrainGeometry.Type;
// Terrain, the shape: the height cap in the panel (lowering Scale lowers Height and its field, one generation;
// a typed Height above the cap is brought to it; loaded values too), Randomize / Natural / Undo by their rules
// and never above the cap, the build focus (one part: All or Hidden; saved; the threshold), persistence, the
// accordion at 270 and 360 for every Outline, label widths, registered last, the generation is the geometry,
// and the GOLDEN of 12 terrains (the hashes the layers stage must keep).
public class TerrainTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static class TestTerrain extends ShapeTerrain { int updates = 0; @Override public void update(){ updates++; } }
	static final List<Field2> fields = new ArrayList<>();
	static class Field2 implements ITextField {
		String text = ""; int colour = 0xFFFFFF; Runnable enter; Field2(){ fields.add(this); }
		public void setTextValue(String t){ text = t; } public void setTextColour(int c){ colour = c; } public String getTextValue(){ return text; }
		public void setOnEnter(Runnable r){ enter = r; } public void setYPosition(int y){} public void setVisibility(boolean v){}
	}
	static class Widgets extends PresetTest.FakeWidgets { @Override public ITextField createTextField(int x, int y, int w, int h, String v){ return new Field2(); } }
	@SuppressWarnings("unchecked") static <T> Property<T> prop(Shape s, String f) throws Exception { Field x = ShapeTerrain.class.getDeclaredField(f); x.setAccessible(true); return (Property<T>) x.get(s); }
	static Field2 field(Shape s, String f) throws Exception { fields.clear(); prop(s, f).getWidgetList(); return fields.get(0); }
	static void type(Field2 f, int v){ f.text = "" + v; f.enter.run(); }
	static int intOf(Shape s, String f) throws Exception { return (Integer) prop(s, f).value; }
	static float floatOf(Shape s, String f) throws Exception { return (Float) prop(s, f).value; }
	static Object values(ShapeTerrain s) throws Exception { Method m = ShapeTerrain.class.getDeclaredMethod("values"); m.setAccessible(true); return m.invoke(s); }
	static void call(ShapeTerrain s, String name) throws Exception { Method m = ShapeTerrain.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(s); }
	static void seedRandom(ShapeTerrain s, long seed) throws Exception { Field x = ShapeTerrain.class.getDeclaredField("random"); x.setAccessible(true); x.set(s, new Random(seed)); }
	static Params paramsOf(ShapeTerrain s) throws Exception { Method m = ShapeTerrain.class.getDeclaredMethod("params"); m.setAccessible(true); return (Params) m.invoke(s); }
	static boolean onGrid(float v, float grid){ return new java.math.BigDecimal(Float.toString(v)).remainder(new java.math.BigDecimal(Float.toString(grid))).signum() == 0; }
	static long hash(Params p) throws Exception { long[] h = {0xcbf29ce484222325L, 0}; TerrainGeometry.enumerate(p, (x, y, z) -> { for(int v: new int[]{x, y, z}){ h[0] ^= v; h[0] *= 0x100000001b3L; } h[1]++; }); return h[0] ^ (h[1] << 40); }

	// The GOLDEN: 12 terrains over the 4 Outlines and 3 Types (written at the end of the terrain's first phase)
	static Params[] golden(){
		Params[] g = new Params[12]; for(int i = 0; i < g.length; i++) g[i] = new Params();
		g[1].outline = Outline.SQUARE; g[1].roundness = 0; g[1].widthX = 61; g[1].widthZ = 45; g[1].rotationDeg = 30; g[1].skin = 1; g[1].seed = 7;
		g[2].outline = Outline.POLYGON; g[2].sides = 5; g[2].type = Type.MOUNTAINS; g[2].scale = 8; g[2].height = 24; g[2].roughness = 0.5; g[2].edgeFade = 30; g[2].skin = 3; g[2].seed = 2;
		g[3].outline = Outline.ORGANIC; g[3].edgeAmplitude = 0.4; g[3].edgeScale = 5; g[3].type = Type.MOUNTAINS; g[3].scale = 4; g[3].height = 12; g[3].roughness = 1; g[3].seed = 3;
		g[4].type = Type.DUNES; g[4].scale = 16; g[4].height = 30; g[4].seed = 4;
		g[5].outline = Outline.SQUARE; g[5].roundness = 0.3; g[5].type = Type.DUNES; g[5].rotationDeg = 45; g[5].edgeFade = 50; g[5].seed = 5;
		g[6].outline = Outline.POLYGON; g[6].sides = 8; g[6].roughness = 1; g[6].height = 60; g[6].scale = 20; g[6].skin = 3; g[6].seed = 6;
		g[7].outline = Outline.ORGANIC; g[7].edgeAmplitude = 0.2; g[7].type = Type.DUNES; g[7].rotationDeg = -60; g[7].edgeFade = 100; g[7].seed = 8;
		g[8].widthX = 121; g[8].widthZ = 121; g[8].type = Type.MOUNTAINS; g[8].scale = 20; g[8].height = 60; g[8].roughness = 1; g[8].skin = 3; g[8].seed = 9;
		g[9].outline = Outline.SQUARE; g[9].widthX = 3; g[9].widthZ = 3; g[9].height = 1;
		g[10].outline = Outline.ORGANIC; g[10].edgeAmplitude = 0.25; g[10].roughness = 0.35; g[10].edgeFade = 35; g[10].seed = 123456;
		g[11].outline = Outline.POLYGON; g[11].sides = 3; g[11].type = Type.DUNES; g[11].scale = 64; g[11].height = 60; g[11].seed = 11;
		return g;
	}
	static final long[] GOLDEN = {0x0275901AF608CA35L, 0xE08AEA2EBA3CE04DL, 0xAC62E3BAA5B474EEL, 0x55FE545103C90152L, 0x8C845E564F3BC177L, 0x71290485226058DCL, 0x7A3B1D85BAA58713L, 0xB8BBAF7995E8857CL, 0x074A649C8B03B74AL, 0xB25BF6F69CD9528CL, 0x5CD73CD972DFA35BL, 0xFDB326206D350EABL};

	public static void main(String[] a) throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		Map<String, String> lang = new HashMap<>(); Matcher mm = Pattern.compile("\"([^\"]+)\": \"([^\"]*)\"").matcher(json); while(mm.find()) lang.put(mm.group(1), mm.group(2));
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new Widgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(TerrainTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);

		System.out.println("-- the height cap in the panel");
		TestTerrain t = new TestTerrain(); Field2 scale = field(t, "propertyScale"), height = field(t, "propertyHeight");
		type(scale, 64); type(height, 60);
		check(intOf(t, "propertyHeight") == 60 && height.text.equals("60") && t.updates == 2, "Scale 64, Height 60: allowed (one generation each)");
		boolean followed = true, once = true;
		for(int s = 63; s >= 4; s--){ int before = t.updates; type(scale, s); int max = Math.min(60, 3 * s); followed &= intOf(t, "propertyHeight") == Math.min(60, max) && height.text.equals("" + Math.min(60, max)); once &= t.updates == before + 1; }
		check(followed && once, "lowering Scale from 64 to 4: Height follows min(60, 3 x Scale) down to 12, its field shows it, one generation per change");
		type(scale, 10); int u = t.updates; type(height, 45);
		check(intOf(t, "propertyHeight") == 30 && height.text.equals("30") && t.updates == u + 1, "typing Height 45 with Scale 10: brought to 30 and shown");
		type(height, 20); type(scale, 30);
		check(intOf(t, "propertyHeight") == 20, "raising Scale leaves Height where it was (20)");
		TestTerrain loaded = new TestTerrain(); String save = loaded.toPersistence(); String[] parts = save.split(",", -1); parts[9] = "60"; parts[10] = "4";
		loaded.restorePersistence(String.join(",", parts));
		check(!loaded.error && intOf(loaded, "propertyHeight") == 12 && intOf(loaded, "propertyScale") == 4, "a save with Height 60 and Scale 4 loads with Height 12");
		TestTerrain preset = new TestTerrain(); preset.captureDefaults(); preset.loadPresetValues(String.join(",", parts));
		check(intOf(preset, "propertyHeight") == 12, "a preset too");

		System.out.println("-- Randomize, Natural, Undo");
		TestTerrain r = new TestTerrain(); seedRandom(r, 42); Field2 base = field(r, "propertyBasePercent"), relief = field(r, "propertyReliefPercent");
		type(base, 100); type(relief, 100);
		boolean capOk = true, keep = true, grid = true, single = true; Set<Integer> heights = new HashSet<>();
		for(int i = 0; i < 300; i++){
			int before = r.updates; call(r, "randomize");
			int s = intOf(r, "propertyScale"), h = intOf(r, "propertyHeight"); heights.add(h);
			capOk &= h >= 1 && h <= TerrainGeometry.maxHeightFor(s);
			keep &= prop(r, "propertyOutline").value == Outline.CIRCLE && prop(r, "propertyType").value == Type.HILLS && intOf(r, "propertySkin") == 2 && intOf(r, "propertySides") == 6;
			grid &= onGrid(floatOf(r, "propertyRoughness"), 0.05f) && onGrid(floatOf(r, "propertyRoundness"), 0.05f) && onGrid(floatOf(r, "propertyEdgeAmplitude"), 0.05f) && onGrid(floatOf(r, "propertyRotation"), 1f) && onGrid(floatOf(r, "propertyEdgeScale"), 0.5f) && intOf(r, "propertyEdgeFade") % 5 == 0;
			single &= r.updates == before + 1;
		}
		check(capOk && heights.size() > 10, "300 Randomize at 100 % / 100 %: Height always within the cap (" + heights.size() + " different heights)");
		check(keep, "Randomize never changes Outline, Type or Skin; Sides only for a Polygon");
		check(grid && single, "values on clean steps (0.05, 1 deg, 0.5, 5 %), one generation each");
		prop(r, "propertyOutline").value = Outline.POLYGON; boolean sidesMoved = false; for(int i = 0; i < 20; i++){ call(r, "randomize"); sidesMoved |= intOf(r, "propertySides") != 6; }
		check(sidesMoved, "for a Polygon, Sides moves too");
		TestTerrain z = new TestTerrain(); seedRandom(z, 1); type(field(z, "propertyBasePercent"), 0); type(field(z, "propertyReliefPercent"), 0);
		Object z0 = values(z); call(z, "randomize");
		check(intOf(z, "propertyWidthX") == 41 && intOf(z, "propertyHeight") == 12 && intOf(z, "propertyScale") == 20 && floatOf(z, "propertyRoughness") == 0f && intOf(z, "propertySeed") != 1, "Base 0 % and Relief 0 %: only a new seed");
		TestTerrain n = new TestTerrain(); prop(n, "propertySeed").value = 77; prop(n, "propertyWidthX").value = 70; prop(n, "propertyScale").value = 10; prop(n, "propertyHeight").value = 25; prop(n, "propertyType").value = Type.DUNES; prop(n, "propertySkin").value = 3; prop(n, "propertyFocusPart").value = Part.HIDDEN;
		call(n, "natural");
		float cr = floatOf(n, "propertyRoundness"), wb = floatOf(n, "propertyEdgeAmplitude"), ws = floatOf(n, "propertyEdgeScale"), ro = floatOf(n, "propertyRoughness"); int ef = intOf(n, "propertyEdgeFade");
		check(prop(n, "propertyOutline").value == Outline.ORGANIC && cr >= 0.6f && cr <= 0.9f && wb >= 0.17f && wb <= 0.33f && ws >= 4f && ws <= 8f && ro >= 0.25f && ro <= 0.55f && ef >= 25 && ef <= 45,
				"Natural: Organic, Corner round " + cr + " (0.75 +- 0.15), Wobble " + wb + " (0.25 +- 0.08), Wobble size " + ws + " (6 +- 2), Roughness " + ro + " (0.40 +- 0.15), Edge fade " + ef + " (35 +- 10)");
		check(intOf(n, "propertyWidthX") == 70 && intOf(n, "propertyHeight") == 25 && intOf(n, "propertyScale") == 10 && prop(n, "propertyType").value == Type.DUNES && intOf(n, "propertySkin") == 3 && n.getFocusPart() == Part.HIDDEN && intOf(n, "propertySeed") == 77 && n.updates == 1,
				"Natural keeps the widths, Height, Scale, Type, Skin, the focus and the seed (drawn from it); one generation");
		TestTerrain n2 = new TestTerrain(); prop(n2, "propertySeed").value = 77; prop(n2, "propertyWidthX").value = 70; prop(n2, "propertyScale").value = 10; prop(n2, "propertyHeight").value = 25; prop(n2, "propertyType").value = Type.DUNES; prop(n2, "propertySkin").value = 3; call(n2, "natural");
		check(floatOf(n2, "propertyRoundness") == cr && floatOf(n2, "propertyRoughness") == ro && intOf(n2, "propertyEdgeFade") == ef, "the same seed gives the same Natural");
		TestTerrain un = new TestTerrain(); seedRandom(un, 9); type(field(un, "propertyReliefPercent"), 100); prop(un, "propertyScale").value = 64; prop(un, "propertyHeight").value = 60;
		Object before = values(un); call(un, "randomize"); call(un, "undo");
		check(values(un).equals(before) && intOf(un, "propertySeed") == 1, "Undo restores every value, the seed too");
		int ub = un.updates; call(un, "undo");
		check(un.updates == ub && values(un).equals(before), "a second Undo does nothing");
		boolean undoCap = true; for(int i = 0; i < 100; i++){ call(un, "randomize"); call(un, "natural"); call(un, "undo"); undoCap &= intOf(un, "propertyHeight") <= TerrainGeometry.maxHeightFor(intOf(un, "propertyScale")); }
		check(undoCap, "Randomize, Natural and Undo in any order: Height never above the cap");

		System.out.println("-- persistence, labels, accordion, registry");
		ShapeTerrain fresh = new ShapeTerrain(); String fs = fresh.toPersistence();
		check(fresh.properties.size() == 49 && fs.equals("CIRCLE,41,41,6,0.7,0.0,0.0,6.0,HILLS,12,20,0.0,0,2,15,15,Runnable,Runnable,Runnable,1,ALL,0,false,25,50,75,,,,,4,2,1,,15,0,30,15,10,0,0,0,0,0,3,3,3,3,3"), "49 values (22 of phase 1, then Layers, the unused cuts and 4 block ids, then the strata's 19), neutral defaults: " + fs);
		ShapeTerrain back = new ShapeTerrain(); prop(back, "propertyType").value = Type.MOUNTAINS; prop(back, "propertySeed").value = 4242; prop(back, "propertyOutline").value = Outline.POLYGON; String bs = back.toPersistence();
		ShapeTerrain back2 = new ShapeTerrain(); back2.restorePersistence(bs);
		check(!back2.error && back2.toPersistence().equals(bs), "saved and restored exactly");
		String[] keys = {"outline", "widthx", "widthz", "sides", "roundness", "islandrotation", "edgeamplitude", "edgescale", "terraintype", "terrainheight", "terrainscale", "roughness", "edgefade", "skin", "basepercent", "reliefpercent", "newseed", "natural", "undo", "seed"};
		int widest = 0; String widestLabel = ""; boolean present = true;
		for(String k: keys){ String v = lang.get("property.buildguide." + k); if(v == null){ present = false; continue; } int w = LabelWidthTest.width(v); if(w > widest){ widest = w; widestLabel = v; } }
		check(present && widest <= 76 && "Skin (blocks)".equals(lang.get("property.buildguide.skin")) && "Terrain".equals(lang.get("shape.buildguide.terrain")) && "Relief".equals(lang.get("property.buildguide.section.relief")), "every Terrain label fits 76 px (widest \"" + widestLabel + "\", " + widest + " px); Skin reads \"Skin (blocks)\" (" + LabelWidthTest.width("Skin (blocks)") + " px)");
		for(int h: new int[]{ShapeLayout.minHeight, 360}){
			int acc = ShapeLayout.accordionHeight(h); int worst = 0;
			for(Outline o: Outline.values()){ ShapeTerrain s = new ShapeTerrain(); prop(s, "propertyOutline").value = o; for(int sec = 0; sec < s.getSectionCount(); sec++) worst = Math.max(worst, s.countRows(sec)); }
			int capacity = (acc - (1 + new ShapeTerrain().getSectionCount()) * 11) / Property.rowHeight;
			check(worst <= capacity, "height " + h + ": sections Base, Relief, Layers, Random; the largest open section (every Outline) " + worst + " rows, capacity " + capacity + "; Seed is a visible row");
		}
		ShapeTerrain sec = new ShapeTerrain();
		check(sec.getSectionCount() == 4 && sec.getSectionName(0).getTranslationKey().equals("property.buildguide.section.base") && sec.getSectionName(1).getTranslationKey().equals("property.buildguide.section.relief") && sec.getSectionName(2).getTranslationKey().equals("property.buildguide.section.layers") && sec.getSectionName(3).getTranslationKey().equals("property.buildguide.section.seed") && sec.countRows(3) == 6 && sec.countRows(2) == 1, "Base, Relief, Layers (the box alone while off), Random (6 rows, Seed included)");
		List<String> reg = new ArrayList<>(); for(String l: Files.readAllLines(Paths.get("common/src/brentmaas/buildguide/common/BuildGuide.java"))) if(l.contains("registerShape(")) reg.add(l.trim());
		int ti = -1, ii = -1; for(int k = 0; k < reg.size(); k++){ if(reg.get(k).startsWith("ShapeRegistry.registerShape(ShapeTerrain.class")) ti = k; if(reg.get(k).startsWith("ShapeRegistry.registerShape(ShapeIsland.class")) ii = k; }
		check(ii >= 0 && ti == ii + 1, "registered right after the Island (the shapes after it are newer)");

		System.out.println("-- generation and the build focus");
		File dir = Files.createTempDirectory("bgterrain").toFile(); BuildGuide.config = new Config(dir); BuildGuide.config.focusFrom.value = 0;
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(TerrainTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};
		ShapeTerrain g = FocusWorldTest.place(new ShapeTerrain()); prop(g, "propertyType").value = Type.MOUNTAINS; prop(g, "propertyScale").value = 8; prop(g, "propertyHeight").value = 24;
		g.update(); FocusWorldTest.settle(g);
		Set<Long> geo = new HashSet<>(); TerrainGeometry.enumerate(paramsOf(g), (x, y, zz) -> geo.add(LocalPos.pack(x, y, zz)));
		check(!g.error && g.getExpectedBlocks().equals(geo) && g.getNumberOfBlocks() == geo.size() && ((FocusWorldTest.RecBuffer) g.buffer).shape().equals(geo), "the generation is the geometry (" + geo.size() + " blocks), all of them in the world buffer");
		check(!g.hasParts() && FocusFilter.nextPart(Part.ALL, g.hasParts()) == Part.HIDDEN && FocusFilter.nextPart(Part.HIDDEN, g.hasParts()) == Part.ALL, "one part: the Part button is All or Hidden");
		g.setFocus(Part.HIDDEN, 0); FocusWorldTest.settle(g);
		check(((FocusWorldTest.RecBuffer) g.buffer).cubes.size() == 1 && g.getExpectedBlocks().equals(geo), "Hidden: nothing of the terrain in the world, every block still expected");
		String hs = g.toPersistence(); ShapeTerrain hb = new ShapeTerrain(); hb.restorePersistence(hs);
		check(hs.split(",", -1)[20].equals("HIDDEN") && hs.split(",", -1)[21].equals("0") && hb.getFocusPart() == Part.HIDDEN, "the selectors are saved, values 21 and 22 (Hidden, All)");
		g.setFocus(Part.ALL, 0); FocusWorldTest.settle(g);
		check(((FocusWorldTest.RecBuffer) g.buffer).shape().equals(geo) && g.getWorldFocus() == null, "All: no filter");
		BuildGuide.config.focusFrom.value = geo.size() + 1; g.setFocus(Part.HIDDEN, 0); FocusWorldTest.settle(g);
		check(((FocusWorldTest.RecBuffer) g.buffer).shape().equals(geo), "below the threshold Hidden does nothing");
		BuildGuide.config.focusFrom.value = 0; g.setFocus(Part.ALL, 0); FocusWorldTest.settle(g);

		System.out.println("-- the GOLDEN of the terrain");
		Params[] gs = golden(); long[] got = new long[gs.length]; for(int i = 0; i < gs.length; i++) got[i] = hash(gs[i]);
		StringBuilder line = new StringBuilder(); for(long v: got) line.append(String.format("0x%016XL, ", v));
		System.out.println("   hashes: " + line);
		check(GOLDEN.length == 12 && Arrays.equals(got, GOLDEN), "12 terrains (4 Outlines x 3 Types, every control) match their GOLDEN hashes");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
