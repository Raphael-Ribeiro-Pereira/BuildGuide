import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
// Farming land (2026-10-18): where the water goes in a farm. Prints maps (pass "print": W water, # edge,
// + field). Checks: registered last; crops: every farmland cell within 4 of water for random rectangles,
// polygons and circles at every spacing, a 9x9 square needs 1 water and 18x18 needs 4; sugar cane: water rows
// every third line, every cane cell beside water, the edge lines cane; parts and colours; persistence.
public class FarmingLandTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	@SuppressWarnings("unchecked") static void set(Shape s, String f, Object v) throws Exception { Field x = ShapeFarmingLand.class.getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	static ShapeFarmingLand make(Object... kv) throws Exception {
		ShapeFarmingLand s = FocusWorldTest.place(new ShapeFarmingLand());
		for(int i = 0; i < kv.length; i += 2) set(s, (String) kv[i], kv[i + 1]);
		s.update(); FocusWorldTest.settle(s);
		return s;
	}
	static List<int[]> cells(ShapeFarmingLand s, FocusFilter.Part part){ List<int[]> out = new ArrayList<>(); for(long p: s.getExpectedBlocks()){ int x = LocalPos.unpackX(p), z = LocalPos.unpackZ(p); if(s.partOf(x, 0, z) == part) out.add(new int[]{x, z}); } return out; }
	static boolean has(ShapeFarmingLand s, int x, int z, FocusFilter.Part part){ return s.getExpectedBlocks().contains(LocalPos.pack(x, 0, z)) && s.partOf(x, 0, z) == part; }
	static void print(ShapeFarmingLand s, int R){
		for(int z = -R; z <= R; z++){ StringBuilder sb = new StringBuilder("   "); for(int x = -R; x <= R; x++){ char c = '.'; if(s.getExpectedBlocks().contains(LocalPos.pack(x, 0, z))){ FocusFilter.Part p = s.partOf(x, 0, z); c = p == FocusFilter.Part.WATER ? 'W' : p == FocusFilter.Part.EDGE ? '#' : '+'; } sb.append(c); } System.out.println(sb); }
	}
	// Every farmland cell (the field shown) within 4 of a water cell on both axes
	static int dry(ShapeFarmingLand s){
		List<int[]> water = cells(s, FocusFilter.Part.WATER); int n = 0;
		for(long p: s.getExpectedBlocks()){ int x = LocalPos.unpackX(p), z = LocalPos.unpackZ(p); if(s.partOf(x, 0, z) == FocusFilter.Part.WATER) continue; boolean wet = false; for(int[] w: water) if(Math.abs(w[0] - x) <= 4 && Math.abs(w[1] - z) <= 4){ wet = true; break; } if(!wet) n++; }
		return n;
	}

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FarmingLandTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgfarm").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(FarmingLandTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};
		boolean show = a.length > 0 && a[0].equals("print");
		Object RECT = ShapeFarmingLand.Outline.RECTANGLE, POLY = ShapeFarmingLand.Outline.POLYGON, CIRC = ShapeFarmingLand.Outline.CIRCLE, CANE = ShapeFarmingLand.Crop.SUGAR_CANE;

		System.out.println("-- registry and panel");
		List<String> regs = new ArrayList<>(); for(String l: Files.readAllLines(new File("common/src/brentmaas/buildguide/common/BuildGuide.java").toPath())) if(l.contains("ShapeRegistry.registerShape(")) regs.add(l.trim());
		check(regs.get(regs.size() - 1).startsWith("ShapeRegistry.registerShape(ShapeFarmingLand.class"), "registered last");
		check(new ShapeFarmingLand().getSectionCount() == 2, "two sections: Outline, Farm");

		System.out.println("-- crops");
		ShapeFarmingLand nine = make("propertyWidthX", 9, "propertyWidthZ", 9, "propertyShowField", true);
		check(cells(nine, FocusFilter.Part.WATER).size() == 1 && has(nine, 0, 0, FocusFilter.Part.WATER) && dry(nine) == 0, "9 x 9: one water in the middle, all wet");
		ShapeFarmingLand eighteen = make("propertyShowField", true);
		if(show) print(eighteen, 10);
		check(cells(eighteen, FocusFilter.Part.WATER).size() == 4 && dry(eighteen) == 0, "18 x 18: four water, all wet");
		check(eighteen.getNumberOfBlocks() == 18 * 18 && eighteen.getExpectedBlocks().size() == 18 * 18, "every cell once with the field shown");
		ShapeFarmingLand hex = make("propertyOutline", POLY, "propertySides", 6, "propertyRadius", 12f, "propertyShowField", true);
		if(show) print(hex, 14);
		check(dry(hex) == 0, "hexagon radius 12: all wet with " + cells(hex, FocusFilter.Part.WATER).size() + " water");
		int bad = 0, cases = 0; Random r = new Random(5);
		for(int k = 0; k < 60; k++){
			int kind = r.nextInt(3), sp = 2 + r.nextInt(8); ShapeFarmingLand s;
			if(kind == 0) s = make("propertyWidthX", 1 + r.nextInt(40), "propertyWidthZ", 1 + r.nextInt(40), "propertySpacing", sp, "propertyShowField", true);
			else if(kind == 1) s = make("propertyOutline", POLY, "propertySides", 3 + r.nextInt(8), "propertyRadius", 2f + r.nextInt(20), "propertyAngle", r.nextInt(90), "propertySpacing", sp, "propertyShowField", true);
			else s = make("propertyOutline", CIRC, "propertyRadius", 1f + r.nextInt(25), "propertySpacing", sp, "propertyShowField", true);
			cases++; if(dry(s) != 0) bad++;
		}
		check(bad == 0, "60 random rectangles, polygons and circles at spacings 2..9: all wet (" + (cases - bad) + " of " + cases + ")");
		ShapeFarmingLand edgeOnly = make("propertyOutline", CIRC, "propertyRadius", 10f);
		if(show) print(edgeOnly, 11);
		check(cells(edgeOnly, FocusFilter.Part.FIELD).isEmpty() && cells(edgeOnly, FocusFilter.Part.EDGE).size() > 40, "Show field off: water and edge only");
		ShapeFarmingLand none = make("propertyShowEdge", false);
		check(none.getExpectedBlocks().size() == 4, "edge off too: only the water");

		long t0 = System.nanoTime(); ShapeFarmingLand big = make("propertyWidthX", 257, "propertyWidthZ", 257); long ms = (System.nanoTime() - t0) / 1000000;
		check(ms < 3000 && dry(big) == 0, "257 x 257 (every grid offset tried): " + ms + " ms, " + cells(big, FocusFilter.Part.WATER).size() + " water");
		System.out.println("-- sugar cane");
		ShapeFarmingLand cane = make("propertyCrop", CANE, "propertyWidthX", 12, "propertyWidthZ", 9, "propertyShowField", true);
		if(show) print(cane, 7);
		List<int[]> cw = cells(cane, FocusFilter.Part.WATER);
		check(cw.size() == 3 * 12, "12 x 9, rows along X: three water rows of 12: " + cw.size());
		boolean nextTo = true; for(long p: cane.getExpectedBlocks()){ int x = LocalPos.unpackX(p), z = LocalPos.unpackZ(p); if(cane.partOf(x, 0, z) == FocusFilter.Part.WATER) continue; boolean ok = false; for(int[] w: cw) if(Math.abs(w[0] - x) + Math.abs(w[1] - z) == 1) ok = true; if(!ok) nextTo = false; }
		check(nextTo, "every cane cell beside water");
		check(cane.getExpectedBlocks().size() == 12 * 9, "12 x 9: every line cane or water");
		ShapeFarmingLand caneZ = make("propertyCrop", CANE, "propertyRows", ShapeFarmingLand.Rows.Z, "propertyWidthX", 9, "propertyWidthZ", 12);
		check(cells(caneZ, FocusFilter.Part.WATER).size() == 3 * 12, "rows along Z: the same turned");

		System.out.println("-- parts and persistence");
		FocusFilter.Part q = FocusFilter.Part.ALL; StringBuilder seq = new StringBuilder(); for(int k = 0; k < 5; k++){ q = FocusFilter.nextPart(q, eighteen.focusParts()); seq.append(q).append(' '); }
		check(seq.toString().equals("WATER FIELD EDGE HIDDEN ALL "), "Part button: All, Water, Field, Edge, Hidden: " + seq);
		RecBufferColours.check(eighteen);
		String data = new ShapeFarmingLand().toPersistence();
		check(data.split(",").length == 13, "13 values: " + data);
		ShapeFarmingLand back = new ShapeFarmingLand(); back.restorePersistence(data.replace("RECTANGLE,18,18", "CIRCLE,30,18"));
		check(!back.error && back.toPersistence().startsWith("CIRCLE,30,18"), "saved and loaded: " + back.toPersistence());

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// The world buffer's colours: water blue, field green (the generation's setColour before each cube)
	static class RecBufferColours {
		static void check(ShapeFarmingLand s) throws Exception {
			Method m = ShapeFarmingLand.class.getDeclaredMethod("setColour", IShapeBuffer.class, FocusFilter.Part.class, int.class); m.setAccessible(true);
			int[] last = new int[3];
			IShapeBuffer b = (IShapeBuffer) Proxy.newProxyInstance(FarmingLandTest.class.getClassLoader(), new Class<?>[]{IShapeBuffer.class}, (p, mm, x) -> { if(mm.getName().equals("setColour")){ last[0] = (Integer) x[0]; last[1] = (Integer) x[1]; last[2] = (Integer) x[2]; } return mm.isDefault() ? InvocationHandler.invokeDefault(p, mm, x) : null; });
			m.invoke(s, b, FocusFilter.Part.WATER, 255); boolean blue = last[2] > last[0] && last[2] > last[1];
			m.invoke(s, b, FocusFilter.Part.FIELD, 255); boolean green = last[1] > last[0] && last[1] > last[2];
			FarmingLandTest.check(blue && green, "colours: water blue, field green");
		}
	}
}
