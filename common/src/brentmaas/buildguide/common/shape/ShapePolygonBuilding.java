package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyMinimumFloat;
import brentmaas.buildguide.common.property.PropertyMinimumInt;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

/**
 * Polygon building (2026-10-18): an X-sided house standing on the Y axis. Walls with windows, a floor, a
 * roof with a raised rim, an inner ring, spokes from the centre to the corners and a skylight, and pillars
 * under the corners. Each column is decided once (Geometry.contains), so no block is emitted twice.
 */
public class ShapePolygonBuilding extends Shape {
	// Shape
	private PropertyMinimumInt propertySides = new PropertyMinimumInt(6, new Translatable("property.buildguide.sides"), () -> update(), 3);
	private PropertyPositiveFloat propertyRadius = new PropertyPositiveFloat(10, new Translatable("property.buildguide.radius"), () -> update());
	private PropertyInt propertyAngle = new PropertyInt(0, new Translatable("property.buildguide.angle"), () -> update());
	private PropertyBoolean propertyEvenMode = new PropertyBoolean(false, new Translatable("property.buildguide.evenmode"), () -> update());
	// Walls
	private PropertyMinimumInt propertyHeight = new PropertyMinimumInt(5, new Translatable("property.buildguide.height"), () -> update(), 1);
	private PropertyMinimumInt propertyThickness = new PropertyMinimumInt(1, new Translatable("property.buildguide.thickness"), () -> update(), 1);
	private PropertyBoolean propertyFloor = new PropertyBoolean(true, new Translatable("property.buildguide.floor"), () -> update());
	private PropertyMinimumInt propertyWindowBottom = new PropertyMinimumInt(2, new Translatable("property.buildguide.windowbottom"), () -> update(), 0);
	private PropertyMinimumInt propertyWindowHeight = new PropertyMinimumInt(2, new Translatable("property.buildguide.windowheight"), () -> update(), 0);
	private PropertyMinimumInt propertyWindowWidth = new PropertyMinimumInt(2, new Translatable("property.buildguide.windowwidth"), () -> update(), 0);
	private PropertyMinimumInt propertyWindowGap = new PropertyMinimumInt(2, new Translatable("property.buildguide.windowgap"), () -> update(), 1);
	// Roof
	private PropertyBoolean propertyRoof = new PropertyBoolean(true, new Translatable("property.buildguide.roof"), () -> update());
	private PropertyMinimumInt propertyRimWidth = new PropertyMinimumInt(1, new Translatable("property.buildguide.rimwidth"), () -> update(), 0);
	private PropertyMinimumInt propertyRimHeight = new PropertyMinimumInt(1, new Translatable("property.buildguide.rimheight"), () -> update(), 0);
	private PropertyMinimumFloat propertyRing = new PropertyMinimumFloat(5, new Translatable("property.buildguide.ring"), () -> update(), 0, true);
	private PropertyBoolean propertySpokes = new PropertyBoolean(true, new Translatable("property.buildguide.spokes"), () -> update());
	private PropertyMinimumFloat propertySkylight = new PropertyMinimumFloat(2, new Translatable("property.buildguide.skylight"), () -> update(), 0, true);
	// Pillars
	private PropertyMinimumInt propertyPillarHeight = new PropertyMinimumInt(0, new Translatable("property.buildguide.pillarheight"), () -> update(), 0);
	private PropertyMinimumInt propertyPillarWidth = new PropertyMinimumInt(2, new Translatable("property.buildguide.pillarwidth"), () -> update(), 1);

	public ShapePolygonBuilding() {
		super();

		Property<?>[] shapeRows = {propertySides, propertyRadius, propertyAngle, propertyEvenMode};
		Property<?>[] wallRows = {propertyHeight, propertyThickness, propertyFloor, propertyWindowBottom, propertyWindowHeight, propertyWindowWidth, propertyWindowGap};
		Property<?>[] roofRows = {propertyRoof, propertyRimWidth, propertyRimHeight, propertyRing, propertySpokes, propertySkylight};
		Property<?>[] pillarRows = {propertyPillarHeight, propertyPillarWidth};
		for(Property<?> p: shapeRows) properties.add(p);
		for(Property<?> p: wallRows) properties.add(p);
		for(Property<?> p: roofRows) properties.add(p);
		for(Property<?> p: pillarRows) properties.add(p);
		assignSection(declareSection(new Translatable("property.buildguide.section.shape")), shapeRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.walls")), wallRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.roof")), roofRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.pillars")), pillarRows);
	}

	Geometry geometry() {
		Geometry g = new Geometry();
		g.n = propertySides.value;
		g.r = propertyRadius.value;
		g.angle = Math.toRadians(propertyAngle.value);
		g.offset = propertyEvenMode.value ? 0.5 : 0.0;
		g.height = propertyHeight.value;
		g.thickness = propertyThickness.value;
		g.floor = propertyFloor.value;
		g.windowBottom = propertyWindowBottom.value;
		g.windowHeight = propertyWindowHeight.value;
		g.windowWidth = propertyWindowWidth.value;
		g.windowGap = propertyWindowGap.value;
		g.roof = propertyRoof.value;
		g.rimWidth = propertyRimWidth.value;
		g.rimHeight = propertyRimHeight.value;
		g.ring = propertyRing.value;
		g.spokes = propertySpokes.value;
		g.skylight = propertySkylight.value;
		g.pillarHeight = propertyPillarHeight.value;
		g.pillarWidth = propertyPillarWidth.value;
		g.prepare();
		return g;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		Geometry g = geometry();
		setOriginOffset(g.offset, 0.0, g.offset);
		for(int x = -g.maxR;x <= g.maxR;++x) {
			for(int z = -g.maxR;z <= g.maxR;++z) {
				for(int y = g.minY;y <= g.maxY;++y) {
					if(g.contains(x, y, z)) addShapeCube(buffer, x, y, z);
				}
			}
		}
	}

	/**
	 * The building as a pure function of a position (no net.minecraft). Distances are in the polygon's own
	 * measure: the distance along the normal of the nearest side, so a "radius" is the apothem, as in
	 * ShapePolygon (whose outline is the wall here at thickness 1).
	 */
	static final class Geometry {
		int n;
		double r, angle, offset;
		int height, thickness;
		boolean floor;
		int windowBottom, windowHeight, windowWidth, windowGap;
		boolean roof;
		int rimWidth, rimHeight;
		double ring;
		boolean spokes;
		double skylight;
		int pillarHeight, pillarWidth;

		int maxR, minY, maxY;
		private double halfSide, circumR;
		private double[] vertexCos, vertexSin;
		private int[] pillarX, pillarZ;

		void prepare() {
			circumR = r / Math.cos(Math.PI / n);
			halfSide = r * Math.tan(Math.PI / n);
			maxR = (int) Math.ceil(circumR + offset) + 1;
			minY = -pillarHeight;
			maxY = height - 1 + (roof ? 1 : 0) + Math.max(rimHeight, roof && (ring > 0 || spokes) ? 1 : 0);
			vertexCos = new double[n];
			vertexSin = new double[n];
			pillarX = new int[n];
			pillarZ = new int[n];
			for(int i = 0;i < n;++i) {
				double psi = 2 * Math.PI * i / n + Math.PI / n + angle;
				vertexCos[i] = Math.cos(psi);
				vertexSin[i] = Math.sin(psi);
				// The pillar's square sits inside the corner, its far edge on the wall's corner
				double d = Math.max(0, circumR - pillarWidth * 0.5 - 0.5);
				pillarX[i] = (int) Math.floor(offset + d * vertexCos[i] + 0.5) - (pillarWidth - 1) / 2;
				pillarZ[i] = (int) Math.floor(offset + d * vertexSin[i] + 0.5) - (pillarWidth - 1) / 2;
			}
		}

		// The nearest side's index for a point relative to the centre
		private int side(double px, double pz) {
			double theta = Math.atan2(pz, px) - angle;
			return (int) Math.floor(n * theta / 2 / Math.PI + 0.5);
		}

		// Distance along the nearest side's normal (the polygon's own "radius" of the point)
		private double polyDist(double px, double pz, int i) {
			double phi = 2 * Math.PI * i / n + angle;
			return px * Math.cos(phi) + pz * Math.sin(phi) + 1e-10; // Epsilon as in ShapePolygon
		}

		boolean contains(int x, int y, int z) {
			if(y < minY || y > maxY) return false;
			double px = x - offset, pz = z - offset;
			if(y < 0) return isPillar(x, z);
			int i = side(px, pz);
			double d = polyDist(px, pz, i);
			double dr = d - r;
			if(dr >= 0.5) return false;
			boolean wall = dr >= 0.5 - thickness;
			if(y < height) {
				if(wall) return !isWindow(px, pz, i, y);
				return y == 0 && floor;
			}
			boolean hole = skylight > 0 && d - skylight < -0.5;
			if(y == height) {
				if(roof) return !hole;
				return false;
			}
			// Above the roof: the rim on the outline, then (first layer only) the ring and the spokes
			int above = y - height;
			if(above <= rimHeight && dr >= 0.5 - Math.max(rimWidth, 0) && rimWidth > 0) return true;
			if(above != 1 || !roof || hole) return false;
			if(ring > 0 && Math.abs(d - ring) < 0.5) return true;
			if(skylight > 0 && Math.abs(d - skylight) < 0.5 && (ring > 0 || spokes)) return true; // the skylight's border
			if(spokes) {
				for(int v = 0;v < n;++v) {
					double along = px * vertexCos[v] + pz * vertexSin[v];
					double across = -px * vertexSin[v] + pz * vertexCos[v];
					if(Math.abs(across) < 0.5 && along >= -0.5) return true;
				}
			}
			return false;
		}

		private boolean isWindow(double px, double pz, int i, int y) {
			if(windowHeight <= 0 || windowWidth <= 0) return false;
			if(y < windowBottom || y >= windowBottom + windowHeight) return false;
			double phi = 2 * Math.PI * i / n + angle;
			double u = -px * Math.sin(phi) + pz * Math.cos(phi);
			if(Math.abs(u) > halfSide - 1.5) return false; // keep the corners solid
			int m = (int) Math.floor(u + 0.5);
			int period = windowWidth + windowGap;
			return Math.floorMod(m + windowWidth / 2, period) < windowWidth;
		}

		private boolean isPillar(int x, int z) {
			if(pillarHeight <= 0) return false;
			for(int v = 0;v < n;++v) {
				if(x >= pillarX[v] && x < pillarX[v] + pillarWidth && z >= pillarZ[v] && z < pillarZ[v] + pillarWidth) return true;
			}
			return false;
		}
	}
}
