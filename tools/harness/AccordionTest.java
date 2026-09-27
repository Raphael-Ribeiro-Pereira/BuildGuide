import java.lang.reflect.Field;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.shape.*;
// GUI redesign E5: row counts of every accordion section of every shape, from the same dry run the
// headers use (Shape.countRows). Any widget access would throw here (no widget handler outside the
// game), so passing also proves the count pass moves nothing. Point counts are set to the maximum.
// Capacity: the accordion is 184 px (y 64..248) = headers x 12 + open section rows x 18, and the
// Origin section (4 rows) must fit too.
public class AccordionTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final int ACCORDION = 184, HEADER = 12, ORIGIN_ROWS = 4;
	static final Class<?>[] SHAPES = {ShapeCatenary.class, ShapeCircle.class, ShapeCone.class, ShapeCuboid.class, ShapeEllipse.class, ShapeEllipsoid.class, ShapeGrid.class, ShapeLine.class, ShapeParabola.class, ShapeParaboloid.class, ShapePolygon.class, ShapePolygonalPyramid.class, ShapeSphere.class, ShapeTorus.class, ShapeSpline.class, ShapeBridge.class};

	public static void main(String[] a) throws Exception {
		check(Property.rowHeight == 18, "row height 18");
		for(Class<?> c: SHAPES) {
			Shape s = (Shape) c.getDeclaredConstructor().newInstance();
			maxPoints(s);
			int headers = 1 + s.getSectionCount(); // Origin + the shape's sections
			int capacity = (ACCORDION - headers * HEADER) / Property.rowHeight;
			StringBuilder line = new StringBuilder(c.getSimpleName() + ": capacity " + capacity + " rows, sections");
			int worst = ORIGIN_ROWS;
			for(int i = 0;i < s.getSectionCount();++i) {
				int rows = s.countRows(i);
				line.append(" ").append(s.getSectionName(i).getTranslationKey().replace("property.buildguide.section.", "")).append("=").append(rows);
				worst = Math.max(worst, rows);
			}
			check(worst <= capacity, line.toString());
			check(s.getOpenSection() == 0, c.getSimpleName() + ": first section open by default");
		}
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}

	// Spline and Bridge show one row per point up to Point count: count the worst case
	static void maxPoints(Shape s) throws Exception {
		try {
			Field f = s.getClass().getDeclaredField("propertyPointCount");
			f.setAccessible(true);
			Property<?> p = (Property<?>) f.get(s);
			Field mf = s.getClass().getDeclaredField("maxPoints");
			mf.setAccessible(true);
			((Property<Object>) p).value = mf.get(null);
		}catch(NoSuchFieldException e) {}
	}
}
