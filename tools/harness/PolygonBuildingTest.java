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
// Polygon building (2026-10-18): an X-sided house on the Y axis. Prints the layers of a hexagon (pass "print")
// and checks: registered last, sections, the wall at thickness 1 is one ring, the floor full, windows are holes
// only in the window band and never at corners, the roof full except the skylight, the rim, ring and spokes on
// the first layer above, spokes reach every corner, pillars n x w x w per layer below, a 4-sided building is
// the same turned by 90 degrees, every block once, persistence.
public class PolygonBuildingTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	@SuppressWarnings("unchecked") static void set(Shape s, String f, Object v) throws Exception { Field x = ShapePolygonBuilding.class.getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	static ShapePolygonBuilding make(Object... kv) throws Exception {
		ShapePolygonBuilding s = FocusWorldTest.place(new ShapePolygonBuilding());
		for(int i = 0; i < kv.length; i += 2) set(s, (String) kv[i], kv[i + 1]);
		s.update(); FocusWorldTest.settle(s);
		return s;
	}
	static Set<Long> layer(Set<Long> cells, int y){ Set<Long> out = new HashSet<>(); for(long p: cells) if(LocalPos.unpackY(p) == y) out.add(p); return out; }
	static boolean has(Set<Long> c, int x, int y, int z){ return c.contains(LocalPos.pack(x, y, z)); }
	static void print(Set<Long> cells, int y, int R){
		System.out.println("   y=" + y + " (" + layer(cells, y).size() + ")");
		for(int z = -R; z <= R; z++){ StringBuilder sb = new StringBuilder("   "); for(int x = -R; x <= R; x++) sb.append(has(cells, x, y, z) ? "#" : x == 0 && z == 0 ? "+" : "."); System.out.println(sb); }
	}

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(PolygonBuildingTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgbuilding").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(PolygonBuildingTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};
		boolean show = a.length > 0 && a[0].equals("print");

		System.out.println("-- registry and panel");
		List<String> regs = new ArrayList<>(); for(String l: Files.readAllLines(new File("common/src/brentmaas/buildguide/common/BuildGuide.java").toPath())) if(l.contains("ShapeRegistry.registerShape(")) regs.add(l.trim());
		int bi = -1, ti = -1; for(int k = 0; k < regs.size(); k++){ if(regs.get(k).startsWith("ShapeRegistry.registerShape(ShapePolygonBuilding.class")) bi = k; if(regs.get(k).startsWith("ShapeRegistry.registerShape(ShapeTerrain.class")) ti = k; }
		check(ti >= 0 && bi == ti + 1, "registered right after the Terrain (saved shapes keep their index)");
		ShapePolygonBuilding fresh = new ShapePolygonBuilding();
		check(fresh.getSectionCount() == 6, "six sections: Shape, Walls, Windows, Roof, Relief, Pillars");

		System.out.println("-- a hexagon, radius 10, height 5, the defaults");
		ShapePolygonBuilding hex = make("propertyPillarHeight", 3, "propertyRelief", 0);
		Set<Long> c = hex.getExpectedBlocks();
		check(!hex.error && hex.getNumberOfBlocks() == c.size(), "every block once: " + c.size());
		if(show) for(int y = -1; y <= 6; y++) print(c, y, 12);
		Set<Long> floor = layer(c, 0), wall = layer(c, 1), win = layer(c, 2), roof = layer(c, 5), top = layer(c, 6);
		check(floor.size() > 300 && has(c, 0, 0, 0), "floor full: " + floor.size());
		check(wall.size() > 40 && !has(c, 0, 1, 0) && wall.size() < 80, "y=1 only the wall ring: " + wall.size());
		check(win.size() < wall.size() && win.size() > 0, "y=2 (window band) has holes: " + win.size() + " < " + wall.size());
		check(layer(c, 4).size() == wall.size(), "y=4 above the windows, the whole ring again");
		check(!has(c, 0, 5, 0) && roof.size() > floor.size() - 30, "roof full except the skylight: " + roof.size());
		check(top.size() > 60, "first layer above: rim, ring, spokes: " + top.size());
		check(c.stream().mapToInt(LocalPos::unpackY).max().getAsInt() == 6, "nothing above the first layer with rim height 1");
		int pillars = layer(c, -1).size();
		check(pillars == 6 * 4 && layer(c, -3).size() == 24 && layer(c, -4).size() == 0, "pillars 6 x 2 x 2, 3 deep: " + pillars);

		System.out.println("-- switches");
		ShapePolygonBuilding bare = make("propertyFloor", false, "propertyRoof", false, "propertyWindows", false, "propertyRimWidth", 0, "propertyRelief", 0);
		Set<Long> b = bare.getExpectedBlocks();
		check(layer(b, 0).equals(Set.copyOf(layer(b, 3)).stream().map(p -> LocalPos.pack(LocalPos.unpackX(p), 0, LocalPos.unpackZ(p))).collect(java.util.stream.Collectors.toSet())), "no floor, no windows: every wall layer the same ring");
		check(layer(b, 5).isEmpty() && layer(b, 6).isEmpty(), "no roof: nothing above the walls");
		ShapePolygonBuilding thick = make("propertyThickness", 2, "propertyWindowHeight", 0, "propertyRelief", 0);
		check(layer(thick.getExpectedBlocks(), 1).size() > wall.size() + 40, "thickness 2: a wider ring");
		ShapePolygonBuilding tall = make("propertyRimHeight", 3);
		check(tall.getExpectedBlocks().stream().mapToInt(LocalPos::unpackY).max().getAsInt() == 8, "rim height 3 rises to y=8");

		System.out.println("-- spokes reach the corners, square turned by 90");
		ShapePolygonBuilding sq = make("propertySides", 4, "propertyRadius", 8f, "propertyRing", 0f, "propertySkylight", 0f, "propertyRimWidth", 0, "propertyFloorPattern", true);
		Set<Long> s6 = layer(sq.getExpectedBlocks(), 6);
		Set<Long> turned = new HashSet<>(); for(long p: s6) turned.add(LocalPos.pack(-LocalPos.unpackZ(p), 6, LocalPos.unpackX(p)));
		check(turned.equals(s6), "4 sides: the spokes layer is the same turned by 90 degrees");
		check(has(sq.getExpectedBlocks(), 7, 6, 7) || has(sq.getExpectedBlocks(), 8, 6, 8), "a spoke reaches the corner");
		Set<Long> all = sq.getExpectedBlocks(); Set<Long> t2 = new HashSet<>(); for(long p: all) t2.add(LocalPos.pack(-LocalPos.unpackZ(p), LocalPos.unpackY(p), LocalPos.unpackX(p)));
		check(t2.equals(all), "4 sides: the whole building the same turned by 90 degrees");

		System.out.println("-- relief, the floor's pattern, parts");
		ShapePolygonBuilding rel = make("propertyFloorPattern", true);
		Set<Long> rc = rel.getExpectedBlocks();
		if(show) for(int y = -1; y <= 6; y++) print(rc, y, 13);
		check(rel.getNumberOfBlocks() == rc.size(), "every block once with relief: " + rc.size());
		check(layer(rc, 2).size() > win.size() + 6 && layer(rc, 0).size() > floor.size() + 40, "columns on the corners (y=2) and a band round the floor: " + layer(rc, 2).size() + ", " + layer(rc, 0).size());
		Set<Long> under = layer(rc, -1), over = new HashSet<>(); for(long p: layer(rc, 6)) over.add(LocalPos.pack(LocalPos.unpackX(p), -1, LocalPos.unpackZ(p)));
		Set<Long> diff = new HashSet<>(under); diff.removeAll(over);
		check(under.size() > 60 && has(rc, 0, -1, 0) && !has(rc, 0, 6, 0), "under the floor the roof's pattern, the centre closed (no floor skylight): " + under.size() + " extra " + diff.size());
		check(has(rc, 0, 0, 0), "the floor closed without Floor skylight");
		ShapePolygonBuilding sun = make("propertyFloorPattern", true, "propertyFloorSkylight", true);
		check(!has(sun.getExpectedBlocks(), 0, 0, 0) && !has(sun.getExpectedBlocks(), 0, -1, 0), "Floor skylight: the floor and its pattern open in the middle");
		ShapePolygonBuilding nowin = make("propertyWindows", false, "propertyRelief", 0);
		check(layer(nowin.getExpectedBlocks(), 2).size() == wall.size(), "Windows off: y=2 the whole ring");
		check(rel.hasParts() && rel.focusParts().length == 5 && rel.partOf(0, 0, 0) == FocusFilter.Part.FLOOR && rel.partOf(0, 5, 3) == FocusFilter.Part.ROOF && rel.partOf(0, 6, 3) == FocusFilter.Part.DETAILS && rel.partOf(10, 1, 0) == FocusFilter.Part.WALLS, "parts: floor, roof, details, walls");
		ShapePolygonBuilding pil = make("propertyPillarHeight", 2);
		long pils = pil.getExpectedBlocks().stream().filter(p -> pil.partOf(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) == FocusFilter.Part.PILLARS).count();
		check(pils == 48, "parts: pillars 6 x 4 x 2 = " + pils);
		FocusFilter.Part q = FocusFilter.Part.ALL; StringBuilder seq = new StringBuilder(); for(int k = 0; k < 8; k++){ q = FocusFilter.nextPart(q, rel.focusParts()); seq.append(q).append(' '); }
		check(seq.toString().equals("WALLS FLOOR ROOF DETAILS PILLARS HIDDEN ALL WALLS "), "Part button: All, Walls, Floor, Roof, Details, Pillars, Hidden: " + seq);
		check(FocusFilter.nextPart(FocusFilter.Part.SPIKES, true) == FocusFilter.Part.HIDDEN, "the Island's button unchanged");
		rel.setFocus(FocusFilter.Part.ROOF, 0); FocusWorldTest.settle(rel);
		ShapePolygonBuilding re = new ShapePolygonBuilding(); re.restorePersistence(rel.toPersistence());
		check(re.getFocusPart() == FocusFilter.Part.ROOF, "the focus saved: " + re.getFocusPart());

		System.out.println("-- inner depth: the roof's pattern under the roof");
		ShapePolygonBuilding plain = make("propertyRelief", 0), in1 = make("propertyRelief", 0, "propertyInnerDepth", 1), in2 = make("propertyRelief", 0, "propertyInnerDepth", 2), in9 = make("propertyRelief", 0, "propertyInnerDepth", 9);
		Set<Long> pc = plain.getExpectedBlocks();
		Set<Long> added4 = new HashSet<>(layer(in1.getExpectedBlocks(), 4)); added4.removeAll(layer(pc, 4));
		Set<Long> roofPattern = new HashSet<>(); for(long p: layer(pc, 6)){ long below = LocalPos.pack(LocalPos.unpackX(p), 4, LocalPos.unpackZ(p)); if(!pc.contains(below)) roofPattern.add(below); }
		if(show) print(in1.getExpectedBlocks(), 4, 12);
		check(!added4.isEmpty() && added4.equals(roofPattern), "depth 1: the layer under the roof gets the roof's pattern inside the walls (" + added4.size() + " blocks)");
		check(!in1.getExpectedBlocks().contains(LocalPos.pack(0, 4, 0)) && layer(in1.getExpectedBlocks(), 3).equals(layer(pc, 3)), "the skylight stays open under it too, nothing deeper than 1 layer");
		Set<Long> added3 = new HashSet<>(layer(in2.getExpectedBlocks(), 3)); added3.removeAll(layer(pc, 3)); Set<Long> moved = new HashSet<>(); for(long p: added4) moved.add(LocalPos.pack(LocalPos.unpackX(p), 3, LocalPos.unpackZ(p)));
		check(added3.equals(moved), "depth 2: the window band layer too, the windows still open");
		check(layer(in9.getExpectedBlocks(), 0).equals(layer(pc, 0)) && in9.getNumberOfBlocks() == in9.getExpectedBlocks().size(), "a depth beyond the walls stops above the floor, every block once");
		boolean details = true; for(long p: added4) if(in1.partOf(LocalPos.unpackX(p), 4, LocalPos.unpackZ(p)) != FocusFilter.Part.DETAILS) details = false;
		check(details, "its part is Details");
		ShapePolygonBuilding noRoof = make("propertyRelief", 0, "propertyInnerDepth", 2, "propertyRoof", false);
		check(noRoof.getExpectedBlocks().equals(make("propertyRelief", 0, "propertyRoof", false).getExpectedBlocks()), "no roof: no inner pattern either");

		System.out.println("-- persistence");
		String data = fresh.toPersistence();
		check(data.split(",").length == 28 && data.endsWith(",ALL,0,0"), "28 values, Inner depth last: " + data);
		ShapePolygonBuilding v2 = new ShapePolygonBuilding(); v2.restorePersistence("6,10.0,0,false,5,1,true,2,2,2,2,true,1,1,5.0,true,2.0,0,2,true,1,true,true,false,false,ROOF,0");
		check(!v2.error && v2.toPersistence().endsWith(",ROOF,0,0"), "a save of the second version (27 values) loads with Inner depth 0: " + v2.toPersistence());
		ShapePolygonBuilding old = new ShapePolygonBuilding(); old.restorePersistence("6,10.0,0,false,5,1,true,2,2,2,2,true,1,1,5.0,true,2.0,0,2");
		check(!old.error && old.toPersistence().startsWith("6,10.0,0,false,5,1,true,2,2,2,2,true,1,1,5.0,true,2.0,0,2,true,1,true,true,false,false"), "a save of the first version (19 values) loads: " + old.toPersistence());
		ShapePolygonBuilding back = new ShapePolygonBuilding(); back.restorePersistence(data.replace("6,10.0", "8,12.0"));
		check(!back.error && back.toPersistence().startsWith("8,12.0"), "saved and loaded: " + back.toPersistence());

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
