import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.FocusFilter.Part;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
import brentmaas.buildguide.common.shape.TerrainGeometry.Outline;
import brentmaas.buildguide.common.shape.TerrainGeometry.Params;
import brentmaas.buildguide.common.shape.TerrainGeometry.Type;
// Terrain, layers and the palette by height: the layer of a column from the height of its surface (t = h /
// Height, a surface on a cut goes to the next layer, small Heights, monotonic), the whole skin of a column in
// its layer; Layers on changes no block (the same cubes in the same order); LayerPalette.classify on the terrain
// (truth table) and the scan in layers equal to the instantaneous one and to an independent reference; the
// 4 ids and the cuts saved and restored, an older terrain and an Island load; the preview by layer, its legend
// in heights above the base, the rebuild key; the focus by layer; the Island's layers still by depth.
public class TerrainLayersTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final int AIR = IBlockProbe.FLAG_AIR, SOLID = IBlockProbe.FLAG_SOLID, IGN = IBlockProbe.FLAG_IGNORED, NONE = LayerPalette.NONE;
	@SuppressWarnings("unchecked") static <T> Property<T> prop(Shape s, String f) throws Exception { Field x = ShapeTerrain.class.getDeclaredField(f); x.setAccessible(true); return (Property<T>) x.get(s); }
	static Params paramsOf(ShapeTerrain s) throws Exception { Method m = ShapeTerrain.class.getDeclaredMethod("params"); m.setAccessible(true); return (Params) m.invoke(s); }
	// The layer of a height written out directly: 100 h against cut x Height
	static int reference(int h, int height, int[] cuts){ long b = 100L * h; return b < (long) cuts[0] * height ? 1 : b < (long) cuts[1] * height ? 2 : b < (long) cuts[2] * height ? 3 : 4; }

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(TerrainLayersTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);

		System.out.println("-- the layer by height");
		TerrainLayers l20 = new TerrainLayers(20, 25, 50, 75, new int[]{-1}, 0);
		check(l20.layerOfHeight(0) == 1 && l20.layerOfHeight(4) == 1 && l20.layerOfHeight(5) == 2 && l20.layerOfHeight(9) == 2 && l20.layerOfHeight(10) == 3 && l20.layerOfHeight(15) == 4 && l20.layerOfHeight(20) == 4,
				"Height 20, cuts 25/50/75: 0-4 layer 1, 5 (on the cut) layer 2, 10 layer 3, 15 and 20 layer 4");
		TerrainLayers l1 = new TerrainLayers(1, 25, 50, 75, new int[]{-1}, 0), l3 = new TerrainLayers(3, 25, 50, 75, new int[]{-1}, 0);
		check(l1.layerOfHeight(0) == 1 && l1.layerOfHeight(1) == 4 && l3.layerOfHeight(0) == 1 && l3.layerOfHeight(1) == 2 && l3.layerOfHeight(2) == 3 && l3.layerOfHeight(3) == 4, "Height 1: ground 1, the top 4; Height 3: 1, 2, 3, 4");
		boolean mono = true, same = true; Random r = new Random(5);
		for(int i = 0; i < 400; i++){ int[] c = ShapeLayers.normalize(5 + 5 * r.nextInt(19), 5 + 5 * r.nextInt(19), 5 + 5 * r.nextInt(19), r.nextInt(3)); int H = 1 + r.nextInt(60);
			TerrainLayers t = new TerrainLayers(H, c[0], c[1], c[2], new int[]{-1}, 0); int prev = 1;
			for(int h = 0; h <= H; h++){ int l = t.layerOfHeight(h); mono &= l >= prev; prev = l; same &= l == reference(h, H, c); } }
		check(mono && same, "400 random Heights and cuts: the layer never goes down as the surface rises, and is 100 h against cut x Height");
		check(l20.depthRange(1)[0] == 0 && l20.depthRange(1)[1] == 4 && l20.depthRange(4)[0] == 15 && l20.depthRange(4)[1] == -1 && ShapeScreen.depthLabel(l20.depthRange(2)).equals("5-9") && ShapeScreen.depthLabel(l20.depthRange(4)).equals("15+"),
				"the legend's bands are heights above the base: 0-4, 5-9, 10-14, 15+ (the same labels as the Island's, which read depths)");
		ShapeLayers island = new ShapeLayers(0, 20, 25, 50, 75);
		check(island.layerAt(7, -5, 3) == island.layerOf(-5) && island.layerAt(-40, -12, 99) == island.layerOf(-12) && !island.equals(new TerrainLayers(20, 25, 50, 75, new int[]{-1}, 0)), "the Island's layers: still by depth (layerAt = layerOf(y)), and never equal to a terrain's");

		System.out.println("-- the shape: the columns' layers, nothing else changes");
		File dir = Files.createTempDirectory("bgterrainl").toFile(); BuildGuide.config = new Config(dir); BuildGuide.config.focusFrom.value = 0;
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(TerrainLayersTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};
		ShapeTerrain t = FocusWorldTest.place(new ShapeTerrain()); prop(t, "propertyType").value = Type.MOUNTAINS; prop(t, "propertyScale").value = 10; prop(t, "propertyHeight").value = 30; prop(t, "propertySeed").value = 21;
		t.update(); FocusWorldTest.settle(t);
		List<Long> cubesOff = new ArrayList<>(((FocusWorldTest.RecBuffer) t.buffer).cubes); Set<Long> off = new HashSet<>(t.getExpectedBlocks());
		check(t.getLayers() == null && t.getPalette() == null, "Layers off: no layers, no palette");
		prop(t, "propertyLayers").value = true; t.update(); FocusWorldTest.settle(t);
		check(t.getExpectedBlocks().equals(off) && ((FocusWorldTest.RecBuffer) t.buffer).cubes.equals(cubesOff), "Layers on: the same blocks and the same cubes in the same order (only the colours differ)");
		Params tp = paramsOf(t); int[] tops = TerrainGeometry.tops(tp); int gh = TerrainGeometry.gridHalf(tp), gn = 2 * gh + 1; ShapeLayers tl = t.getLayers();
		boolean column = true; Map<Integer, Integer> perLayer = new TreeMap<>();
		for(long pos: off){ int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos); int want = reference(tops[(x + gh) * gn + (z + gh)], 30, new int[]{25, 50, 75}); column &= tl.layerAt(x, y, z) == want; perLayer.merge(want, 1, Integer::sum); }
		check(tl instanceof TerrainLayers && column && perLayer.size() == 4, "every block of a column (its whole skin) is in its column's layer, by the surface's height; blocks per layer " + perLayer);
		check(Arrays.equals(t.getPalette(), new String[]{"", "", "", ""}), "Layers on, no blocks chosen: a palette of four empty ids (any solid)");

		System.out.println("-- the palette on the terrain");
		// A column of layer 1 and one of layer 4, by their surfaces
		long low = -1, high = -1, lowBottom = -1;
		for(long pos: off){ int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos); int top = tops[(x + gh) * gn + (z + gh)];
			if(low == -1 && reference(top, 30, new int[]{25, 50, 75}) == 1 && y == top) low = pos; if(high == -1 && reference(top, 30, new int[]{25, 50, 75}) == 4 && y == top) high = pos; }
		int lx = LocalPos.unpackX(low), lz = LocalPos.unpackZ(low); int lowMin = Integer.MAX_VALUE; for(long pos: off) if(LocalPos.unpackX(pos) == lx && LocalPos.unpackZ(pos) == lz) lowMin = Math.min(lowMin, LocalPos.unpackY(pos)); lowBottom = LocalPos.pack(lx, lowMin, lz);
		int GRASS = 30, DIRT = 31, STONE = 32, SNOW = 33, OTHER = 40;
		LayerPalette pal = new LayerPalette(tl, new int[]{GRASS, DIRT, STONE, SNOW});
		int[] lo = {LocalPos.unpackX(low), LocalPos.unpackY(low), LocalPos.unpackZ(low)}, hi = {LocalPos.unpackX(high), LocalPos.unpackY(high), LocalPos.unpackZ(high)};
		check(LayerPalette.classify(pal, lo[0], lo[1], lo[2], SOLID, GRASS) == ValidationState.OK && LayerPalette.classify(pal, hi[0], hi[1], hi[2], SOLID, SNOW) == ValidationState.OK, "grass on a low column, snow on a peak: OK");
		check(LayerPalette.classify(pal, lo[0], lo[1], lo[2], SOLID, SNOW) == ValidationState.WRONG_LAYER && LayerPalette.classify(pal, hi[0], hi[1], hi[2], SOLID, GRASS) == ValidationState.WRONG_LAYER, "snow down low, grass on a peak: wrong layer");
		check(LayerPalette.classify(pal, lo[0], lo[1], lo[2], SOLID, OTHER) == ValidationState.WRONG_BLOCK && LayerPalette.classify(pal, lo[0], lo[1], lo[2], AIR, NONE) == ValidationState.MISSING && LayerPalette.classify(pal, lo[0], lo[1], lo[2], IGN, OTHER) == ValidationState.IGNORED && LayerPalette.classify(pal, lo[0], lo[1], lo[2], 0, OTHER) == ValidationState.MISSING,
				"a solid block in no layer: wrong block; air and other non-solid: missing; an ignored type: ignored");
		int[] lb = {LocalPos.unpackX(lowBottom), LocalPos.unpackY(lowBottom), LocalPos.unpackZ(lowBottom)};
		check(LayerPalette.classify(pal, lb[0], lb[1], lb[2], SOLID, GRASS) == ValidationState.OK && pal.needsId(lb[0], lb[1], lb[2]), "the bottom of a low column's skin is grass too (the column's layer, not its own y)");
		LayerPalette partial = new LayerPalette(tl, new int[]{GRASS, NONE, NONE, NONE});
		check(LayerPalette.classify(partial, hi[0], hi[1], hi[2], SOLID, OTHER) == ValidationState.OK && !partial.needsId(hi[0], hi[1], hi[2]) && LayerPalette.classify(partial, hi[0], hi[1], hi[2], SOLID, GRASS) == ValidationState.OK && LayerPalette.classify(partial, lo[0], lo[1], lo[2], SOLID, OTHER) == ValidationState.WRONG_BLOCK, "a layer with no block chosen accepts any solid, another layer's block too (its id is not even read; the 4D rule); the low layer with grass still wants grass");
		// The scan in layers equals the instantaneous one and an independent reference, in a random world
		SliceScanTest.Case c = new SliceScanTest.Case(); c.ox = 120; c.oy = 70; c.oz = -300; c.shape.addAll(off); c.tracked = new ArrayList<>(off); Collections.shuffle(c.tracked, new Random(3));
		PaletteTest.IdWorld w = new PaletteTest.IdWorld(); Random wr = new Random(8); String[] names = {"Grass", "Dirt", "Stone", "Glass", "Cobblestone", "Scaffolding", "Torch"};
		Map<String, Integer> ids = new HashMap<>(); ids.put("Grass", PaletteTest.id("Grass")); ids.put("Dirt", PaletteTest.id("Dirt")); ids.put("Stone", PaletteTest.id("Stone")); ids.put("Glass", PaletteTest.id("Glass"));
		Map<Long, int[]> placed = new HashMap<>();
		for(long pos: off){ int k = wr.nextInt(names.length + 1); if(k == names.length) continue; String n = names[k]; int f = n.equals("Scaffolding") ? IGN : n.equals("Torch") ? 0 : SOLID;
			w.set(LocalPos.unpackX(pos) + c.ox, LocalPos.unpackY(pos) + c.oy, LocalPos.unpackZ(pos) + c.oz, f, n); placed.put(pos, new int[]{f, PaletteTest.id(n)}); }
		LayerPalette scanPal = new LayerPalette(tl, new int[]{PaletteTest.id("Grass"), PaletteTest.id("Dirt"), PaletteTest.id("Stone"), PaletteTest.id("Glass")});
		String sliced = PaletteTest.scan(c, w, scanPal, 0), instant = PaletteTest.scan(c, w, scanPal, Long.MAX_VALUE);
		ValidationState vs = PaletteTest.scanState(c, w, scanPal); boolean ref = true; int wrongLayers = 0;
		int[] chosen = {PaletteTest.id("Grass"), PaletteTest.id("Dirt"), PaletteTest.id("Stone"), PaletteTest.id("Glass")};
		for(long pos: off){ int[] b = placed.get(pos); int f = b == null ? AIR : b[0], id = b == null ? NONE : b[1]; int layer = reference(tops[(LocalPos.unpackX(pos) + gh) * gn + (LocalPos.unpackZ(pos) + gh)], 30, new int[]{25, 50, 75});
			byte want; int wanted = chosen[layer - 1]; boolean inPal = false; for(int ch: chosen) inPal |= ch == id;
			if((f & AIR) != 0) want = ValidationState.MISSING; else if(id == wanted) want = ValidationState.OK; else if(inPal) want = ValidationState.WRONG_LAYER; else if((f & IGN) != 0) want = ValidationState.IGNORED; else if((f & SOLID) == 0) want = ValidationState.MISSING; else want = ValidationState.WRONG_BLOCK;
			ref &= vs.getStatus(pos) == want; if(want == ValidationState.WRONG_LAYER) wrongLayers++; }
		check(sliced.equals(instant), "the scan in layers (one per frame) equals the instantaneous scan on the terrain");
		check(ref && wrongLayers > 0 && vs.getWrongLayer() == wrongLayers, "and an independent reference (layer by the column's surface): " + off.size() + " positions, " + wrongLayers + " wrong layer");

		System.out.println("-- persistence");
		ShapeTerrain s = new ShapeTerrain(); prop(s, "propertyLayers").value = true; prop(s, "propertyCut1").value = 10; prop(s, "propertyCut2").value = 40; prop(s, "propertyCut3").value = 90;
		prop(s, "propertyBlock1").value = "minecraft:grass_block"; prop(s, "propertyBlock4").value = "minecraft:snow_block";
		String save = s.toPersistence(); ShapeTerrain back = new ShapeTerrain(); back.restorePersistence(save);
		check(!back.error && back.toPersistence().equals(save) && save.endsWith(",true,10,40,90,minecraft%3Agrass_block,,,minecraft%3Asnow_block") && back.hasLayers(), "Layers, the cuts and the 4 ids saved (URL-encoded) and restored: " + save.substring(save.length() - 62));
		ShapeTerrain fresh = new ShapeTerrain(); String full = fresh.toPersistence(); String phase1 = full.substring(0, full.length() - ",false,25,50,75,,,,".length());
		ShapeTerrain older = new ShapeTerrain(); older.restorePersistence(phase1);
		check(phase1.split(",", -1).length == 22 && !older.error && !older.hasLayers() && older.toPersistence().equals(full), "a terrain saved before the layers (22 values) loads with Layers off and no blocks, and saves the 30 of now");
		ShapeIsland is = new ShapeIsland(); String isave = is.toPersistence(); ShapeIsland is2 = new ShapeIsland(); is2.restorePersistence(isave);
		check(is.properties.size() == 44 && !is2.error && is2.toPersistence().equals(isave), "an Island loads as before (44 values)");
		for(int h: new int[]{brentmaas.buildguide.common.screen.ShapeLayout.minHeight, 360}){ ShapeTerrain lay = new ShapeTerrain(); prop(lay, "propertyLayers").value = true; int worst = 0; for(int sec = 0; sec < lay.getSectionCount(); sec++) worst = Math.max(worst, lay.countRows(sec));
			int capacity = (brentmaas.buildguide.common.screen.ShapeLayout.accordionHeight(h) - (1 + lay.getSectionCount()) * 11) / Property.rowHeight;
			check(lay.countRows(2) == 6 && worst <= capacity, "height " + h + ", Layers on: the Layers section has the Island's 6 rows (box, cuts, 4 blocks), largest section " + worst + ", capacity " + capacity); }

		System.out.println("-- the preview");
		PreviewModel m = PreviewModel.of(t.getExpectedBlocks(), t.getGeneration(), t.getLayers()).withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		boolean colours = true; for(int i = 0; i < m.positions.length; i++){ long pos = m.positions[i]; colours &= m.colourAt(i) == ShapeLayers.colour(reference(tops[(LocalPos.unpackX(pos) + gh) * gn + (LocalPos.unpackZ(pos) + gh)], 30, new int[]{25, 50, 75})); }
		check(m.colourMode == ColourMode.LAYER && colours, "Colour: Layer colours every cube by its column's layer");
		PreviewModel same2 = PreviewModel.of(t.getExpectedBlocks(), t.getGeneration(), t.getLayers()).withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		check(PreviewModel.meshChange(m, m.withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER)) == null, "the same layers: no rebuild");
		TerrainLayers moved = new TerrainLayers(30, 30, 50, 75, tops, gh);
		PreviewModel other = PreviewModel.of(t.getExpectedBlocks(), t.getGeneration(), moved).withView(PreviewFilter.ALL, PreviewModel.SLICE_OFF, 0, ColourMode.LAYER);
		check(PreviewModel.meshChange(m, other) != null && !moved.equals(t.getLayers()) && !new TerrainLayers(30, 25, 50, 75, tops.clone(), gh).equals(t.getLayers()) && same2.layers.equals(m.layers), "the rebuild key: other cuts, or another generation's columns, are other layers (rebuilt: " + PreviewModel.meshChange(m, other) + "); the same generation's are equal");
		TerrainLayers d = (TerrainLayers) t.getLayers();
		check(ShapeScreen.depthLabel(d.depthRange(1)).equals("0-7") && ShapeScreen.depthLabel(d.depthRange(4)).equals("23+"), "the legend for Height 30: " + ShapeScreen.depthLabel(d.depthRange(1)) + ", " + ShapeScreen.depthLabel(d.depthRange(2)) + ", " + ShapeScreen.depthLabel(d.depthRange(3)) + ", " + ShapeScreen.depthLabel(d.depthRange(4)) + " blocks above the base");

		System.out.println("-- the focus by layer");
		for(int layer = 1; layer <= 4; layer++){
			t.setFocus(Part.ALL, layer); FocusWorldTest.settle(t);
			Set<Long> want = new HashSet<>(); for(long pos: off) if(tl.layerAt(LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos)) == layer) want.add(pos);
			check(((FocusWorldTest.RecBuffer) t.buffer).shape().equals(want) && t.getExpectedBlocks().equals(off), "Layer " + layer + ": the world shows only its columns (" + want.size() + " blocks), every block still expected");
		}
		t.setFocus(Part.HIDDEN, 2); FocusWorldTest.settle(t);
		check(((FocusWorldTest.RecBuffer) t.buffer).cubes.size() == 1, "Hidden with a layer: nothing");
		t.setFocus(Part.ALL, 0); FocusWorldTest.settle(t);
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
