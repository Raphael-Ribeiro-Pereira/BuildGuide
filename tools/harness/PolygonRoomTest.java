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
// Polygon as a closed room (2026-10-17): Floor and Ceiling fill the inside of the first and the last layer along
// the depth. Off (the default) the polygon is exactly what it was (the old loop, over sides, radii, directions,
// rotations, depths and even mode); on, the floor and ceiling layers are the whole polygon and the layers between
// keep only the outline; a depth of 1 gives one full layer without a block twice; a negative depth; persistence:
// the two values last, an older save loads with both off.
public class PolygonRoomTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// The polygon before the room (ShapePolygon.updateShape at c502556), as local positions
	static Set<Long> legacy(int n, float r, int dir, int rot, int depth, boolean even){
		int[] rotXX = {1, 0, -1, 0}, rotXY = {0, -1, 0, 1}, rotYX = {0, 1, 0, -1};
		double offset = even ? 0.5 : 0.0; Set<Long> out = new HashSet<>();
		int maxR = (int) Math.ceil(((r + offset) / Math.cos(Math.PI / n)));
		for(int a = -maxR; a <= maxR; ++a) for(int b = -maxR; b <= maxR; ++b){
			double theta = Math.atan2(b - offset, a - offset) + Math.PI; if(theta < 0) theta += 2 * Math.PI;
			int i = (int) (n * theta / 2 / Math.PI + 0.5);
			double dr = (a - offset) * Math.cos(2 * i * Math.PI / n - Math.PI) + (b - offset) * Math.sin(2 * i * Math.PI / n - Math.PI) - r + 1e-10;
			if(dr >= -0.5 && dr < 0.5) for(int h = (depth > 0 ? 0 : depth + 1); h < (depth > 0 ? depth : 1); ++h){
				if(dir == 0) out.add(LocalPos.pack(h, a * rotXX[rot] + b * rotYX[rot], b * rotXX[rot] + a * rotXY[rot]));
				else if(dir == 1) out.add(LocalPos.pack(a * rotXX[rot] + b * rotYX[rot], h, b * rotXX[rot] + a * rotXY[rot]));
				else out.add(LocalPos.pack(b * rotXX[rot] + a * rotXY[rot], a * rotXX[rot] + b * rotYX[rot], h));
			}
		}
		return out;
	}
	@SuppressWarnings("unchecked") static void set(ShapePolygon s, String f, Object v) throws Exception { Field x = ShapePolygon.class.getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	@SuppressWarnings("unchecked") static Object enumValue(String field, int ordinal) throws Exception { Field x = ShapePolygon.class.getDeclaredField(field); x.setAccessible(true); Object[] values = ((Property<Object>) x.get(new ShapePolygon())).value.getClass().getEnumConstants(); return values[ordinal]; }
	static ShapePolygon make(int n, float r, int dir, int rot, int depth, boolean even, boolean floor, boolean ceiling) throws Exception {
		ShapePolygon s = FocusWorldTest.place(new ShapePolygon());
		set(s, "propertySides", n); set(s, "propertyRadius", r); set(s, "propertyDir", enumValue("propertyDir", dir)); set(s, "propertyRot", enumValue("propertyRot", rot));
		set(s, "propertyDepth", depth); set(s, "propertyEvenMode", even); set(s, "propertyFloor", floor); set(s, "propertyCeiling", ceiling);
		s.update(); FocusWorldTest.settle(s);
		return s;
	}
	// The cells of one layer h along the depth axis
	static Set<Long> layer(Set<Long> cells, int dir, int h){ Set<Long> out = new HashSet<>(); for(long p: cells){ int c = dir == 0 ? LocalPos.unpackX(p) : dir == 1 ? LocalPos.unpackY(p) : LocalPos.unpackZ(p); if(c == h) out.add(p); } return out; }
	// The whole polygon of a layer: every cell inside the outline too, by the same rule (dr < 0.5)
	static Set<Long> filled(int n, float r, int dir, int rot, boolean even, int h){
		Set<Long> ring = legacy(n, r, dir, rot, 1, even), out = new HashSet<>();
		int[] rotXX = {1, 0, -1, 0}, rotXY = {0, -1, 0, 1}, rotYX = {0, 1, 0, -1}; double offset = even ? 0.5 : 0.0; int maxR = (int) Math.ceil(((r + offset) / Math.cos(Math.PI / n)));
		for(int a = -maxR; a <= maxR; ++a) for(int b = -maxR; b <= maxR; ++b){
			double theta = Math.atan2(b - offset, a - offset) + Math.PI; if(theta < 0) theta += 2 * Math.PI; int i = (int) (n * theta / 2 / Math.PI + 0.5);
			double dr = (a - offset) * Math.cos(2 * i * Math.PI / n - Math.PI) + (b - offset) * Math.sin(2 * i * Math.PI / n - Math.PI) - r + 1e-10;
			if(dr < 0.5){ int x, y, z; if(dir == 0){ x = h; y = a * rotXX[rot] + b * rotYX[rot]; z = b * rotXX[rot] + a * rotXY[rot]; } else if(dir == 1){ x = a * rotXX[rot] + b * rotYX[rot]; y = h; z = b * rotXX[rot] + a * rotXY[rot]; } else { x = b * rotXX[rot] + a * rotXY[rot]; y = a * rotXX[rot] + b * rotYX[rot]; z = h; } out.add(LocalPos.pack(x, y, z)); }
		}
		return out;
	}

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(PolygonRoomTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgpolygon").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(PolygonRoomTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		TimingLog.sink = line -> {};

		System.out.println("-- Floor and Ceiling off: the polygon as it was");
		int same = 0, cases = 0; Random r = new Random(11);
		for(int k = 0; k < 40; k++){
			int n = 3 + r.nextInt(8); float rad = 2 + r.nextInt(12) + (r.nextBoolean() ? 0.5f : 0f); int d = r.nextInt(3), rot = r.nextInt(4), depth = r.nextBoolean() ? 1 + r.nextInt(6) : -(1 + r.nextInt(6)); boolean even = r.nextBoolean();
			ShapePolygon s = make(n, rad, d, rot, depth, even, false, false); cases++;
			if(s.getExpectedBlocks().equals(legacy(n, rad, d, rot, depth, even)) && s.getNumberOfBlocks() == s.getExpectedBlocks().size()) same++;
		}
		check(same == cases, "40 random polygons (sides, radius, direction, rotation, depth +/-, even mode): the same blocks as before, " + same + " of " + cases);

		System.out.println("-- a room: Floor and Ceiling");
		ShapePolygon room = make(6, 7, 1, 0, 5, false, true, true);
		Set<Long> cells = room.getExpectedBlocks(), walls = legacy(6, 7, 1, 0, 5, false), ring = layer(walls, 1, 2);
		check(layer(cells, 1, 0).equals(filled(6, 7, 1, 0, false, 0)) && layer(cells, 1, 4).equals(filled(6, 7, 1, 0, false, 4)), "hexagon r 7, depth 5, direction Y: layer 0 (floor) and layer 4 (ceiling) are the whole hexagon (" + layer(cells, 1, 0).size() + " blocks each)");
		check(layer(cells, 1, 1).equals(layer(walls, 1, 1)) && layer(cells, 1, 2).equals(ring) && layer(cells, 1, 3).equals(layer(walls, 1, 3)), "the layers between keep only the outline (" + ring.size() + " blocks each)");
		check(room.getNumberOfBlocks() == cells.size(), "no block twice (" + cells.size() + ")");
		ShapePolygon floorOnly = make(6, 7, 1, 0, 5, false, true, false);
		check(layer(floorOnly.getExpectedBlocks(), 1, 0).size() > ring.size() && layer(floorOnly.getExpectedBlocks(), 1, 4).equals(layer(legacy(6, 7, 1, 0, 5, false), 1, 4)), "Floor alone: the floor full, the top layer only the outline");
		ShapePolygon flat = make(5, 6, 1, 1, 1, false, true, true);
		check(flat.getExpectedBlocks().equals(filled(5, 6, 1, 1, false, 0)) && flat.getNumberOfBlocks() == flat.getExpectedBlocks().size(), "depth 1 with both: one full pentagon, no block twice (" + flat.getNumberOfBlocks() + ")");
		ShapePolygon down = make(4, 5, 1, 0, -4, false, true, true);
		check(layer(down.getExpectedBlocks(), 1, -3).equals(filled(4, 5, 1, 0, false, -3)) && layer(down.getExpectedBlocks(), 1, 0).equals(filled(4, 5, 1, 0, false, 0)) && layer(down.getExpectedBlocks(), 1, -1).equals(layer(legacy(4, 5, 1, 0, -4, false), 1, -1)), "depth -4: the floor at -3 (the lowest), the ceiling at 0");
		ShapePolygon side = make(8, 6, 0, 2, 3, true, true, true);
		check(layer(side.getExpectedBlocks(), 0, 0).equals(filled(8, 6, 0, 2, true, 0)) && layer(side.getExpectedBlocks(), 0, 2).equals(filled(8, 6, 0, 2, true, 2)), "direction X, rotation 180, even mode: the two ends along X are filled");

		System.out.println("-- persistence");
		ShapePolygon p = new ShapePolygon(); String data = p.toPersistence(); String[] parts = data.split(",");
		check(parts.length == 8 && parts[6].equals("false") && parts[7].equals("false"), "8 values, Floor and Ceiling last: " + data);
		ShapePolygon q = new ShapePolygon(); q.restorePersistence("5,4.0,Y,ROT90,3,true");
		check(!q.error && q.toPersistence().equals("5,4.0,Y,ROT90,3,true,false,false"), "an older save (6 values) loads, both off: " + q.toPersistence());
		q.restorePersistence("5,4.0,Y,ROT90,3,true,true,false");
		check(q.toPersistence().endsWith(",true,false"), "Floor on, Ceiling off: saved and loaded");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
