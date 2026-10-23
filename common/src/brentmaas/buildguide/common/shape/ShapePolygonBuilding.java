package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyMinimumFloat;
import brentmaas.buildguide.common.property.PropertyMinimumInt;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

/**
 * Polygon building (2026-10-18): an X-sided house standing on the Y axis. Walls with windows, a floor, a
 * roof with a raised rim, an inner ring, spokes from the centre to the corners and a skylight, and pillars
 * under the corners. Relief (2026-10-18): columns on the corners and bands round the floor and the roof,
 * standing out of the walls, and the roof's pattern repeated under the floor (its skylight optional). Each
 * position is decided once (Geometry.partAt), so no block is emitted twice; the same function gives the
 * build focus parts (Walls, Floor, Roof, Details, Pillars).
 *
 * Shown as "Geometric capsule" since 2026-10-22 (the class and its registry key keep the old name, so saved
 * shapes and presets still load). Inner depth (2026-10-22): the roof's pattern copied to the inner ceiling,
 * hanging that many layers down from under the roof.
 */
public class ShapePolygonBuilding extends Shape {
	private static final String[] focusPartNames = {"All", "Cap", "Body", "Spikes", "Hidden", "Walls", "Floor", "Roof", "Details", "Pillars"};
	public static final int reliefRgb = 0x5B8DD9;
	private static final FocusFilter.Part[] buildingParts = {FocusFilter.Part.WALLS, FocusFilter.Part.FLOOR, FocusFilter.Part.ROOF, FocusFilter.Part.DETAILS, FocusFilter.Part.PILLARS};

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
	// Persisted after the values above (2026-10-18): Windows on the Walls; Relief; the build focus (not panel rows)
	private PropertyBoolean propertyWindows = new PropertyBoolean(true, new Translatable("property.buildguide.windows"), () -> update());
	private PropertyMinimumInt propertyRelief = new PropertyMinimumInt(1, new Translatable("property.buildguide.relief"), () -> update(), 0);
	private PropertyBoolean propertyColumns = new PropertyBoolean(true, new Translatable("property.buildguide.columns"), () -> update());
	private PropertyBoolean propertyBands = new PropertyBoolean(true, new Translatable("property.buildguide.bands"), () -> update());
	private PropertyBoolean propertyFloorPattern = new PropertyBoolean(false, new Translatable("property.buildguide.floorpattern"), () -> update());
	private PropertyBoolean propertyFloorSkylight = new PropertyBoolean(false, new Translatable("property.buildguide.floorskylight"), () -> update());
	// Persisted last (2026-10-22): the roof's pattern under the roof, inside, this many layers deep (0 = none)
	private PropertyMinimumInt propertyInnerDepth = new PropertyMinimumInt(0, new Translatable("property.buildguide.innerdepth"), () -> update(), 0);
	private PropertyEnum<FocusFilter.Part> propertyFocusPart = new PropertyEnum<FocusFilter.Part>(FocusFilter.Part.ALL, new Translatable("screen.buildguide.focus.part"), null, focusPartNames);
	private PropertyInt propertyFocusLayer = new PropertyInt(FocusFilter.LAYER_ALL, new Translatable("screen.buildguide.focus.layer"), null);

	// What the last generation's parts are computed from, set before its first block
	private volatile Geometry generated = null;

	public ShapePolygonBuilding() {
		super();

		Property<?>[] shapeRows = {propertySides, propertyRadius, propertyAngle, propertyEvenMode};
		Property<?>[] wallRows = {propertyHeight, propertyThickness, propertyFloor};
		Property<?>[] windowRows = {propertyWindows, propertyWindowBottom, propertyWindowHeight, propertyWindowWidth, propertyWindowGap};
		Property<?>[] roofRows = {propertyRoof, propertyRimWidth, propertyRimHeight, propertyRing, propertySpokes, propertySkylight};
		Property<?>[] reliefRows = {propertyRelief, propertyInnerDepth, propertyColumns, propertyBands, propertyFloorPattern, propertyFloorSkylight};
		Property<?>[] pillarRows = {propertyPillarHeight, propertyPillarWidth};
		// The order saved: the first values as they were, the newer ones after them
		Property<?>[] saved = {propertySides, propertyRadius, propertyAngle, propertyEvenMode, propertyHeight, propertyThickness, propertyFloor, propertyWindowBottom, propertyWindowHeight, propertyWindowWidth, propertyWindowGap,
				propertyRoof, propertyRimWidth, propertyRimHeight, propertyRing, propertySpokes, propertySkylight, propertyPillarHeight, propertyPillarWidth,
				propertyWindows, propertyRelief, propertyColumns, propertyBands, propertyFloorPattern, propertyFloorSkylight, propertyFocusPart, propertyFocusLayer,
				propertyInnerDepth};
		for(Property<?> p: saved) properties.add(p);
		hideFromGui(propertyFocusPart);
		hideFromGui(propertyFocusLayer);
		assignSection(declareSection(new Translatable("property.buildguide.section.shape")), shapeRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.walls")), wallRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.windows")), windowRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.roof")), roofRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.relief")), reliefRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.pillars")), pillarRows);
	}

	// Rows of the open section in the order of the panel, not of the save
	@Override
	public void onSelectedInGUI() {
		Property<?>[] order = {propertySides, propertyRadius, propertyAngle, propertyEvenMode,
				propertyHeight, propertyThickness, propertyFloor, propertyWindows, propertyWindowBottom, propertyWindowHeight, propertyWindowWidth, propertyWindowGap,
				propertyRoof, propertyRimWidth, propertyRimHeight, propertyRing, propertySpokes, propertySkylight,
				propertyRelief, propertyInnerDepth, propertyColumns, propertyBands, propertyFloorPattern, propertyFloorSkylight,
				propertyPillarHeight, propertyPillarWidth};
		int row = 0;
		for(Property<?> p: order) {
			if(isShown(p)) row = placeRow(row, p);
			else hideRow(p);
		}
		hideRow(propertyFocusPart, propertyFocusLayer);
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
		g.windows = propertyWindows.value;
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
		g.relief = propertyRelief.value;
		g.columns = propertyColumns.value;
		g.bands = propertyBands.value;
		g.floorPattern = propertyFloorPattern.value;
		g.floorSkylight = propertyFloorSkylight.value;
		g.innerDepth = propertyInnerDepth.value;
		g.pillarHeight = propertyPillarHeight.value;
		g.pillarWidth = propertyPillarWidth.value;
		g.prepare();
		return g;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		Geometry g = geometry();
		generated = g;
		setOriginOffset(g.offset, 0.0, g.offset);
		for(int x = -g.maxR;x <= g.maxR;++x) {
			for(int z = -g.maxR;z <= g.maxR;++z) {
				boolean relief = g.isRelief(x, z);
				for(int y = g.minY;y <= g.maxY;++y) {
					if(g.partAt(x, y, z) == null) continue;
					setCubeColour(buffer, relief);
					addShapeCube(buffer, x, y, z);
				}
			}
		}
	}

	// The relief, outside the walls, is a plain blue; everything else is the shape's colour
	private void setCubeColour(IShapeBuffer buffer, boolean relief) {
		int alpha = (int) (255 * shapeSet.getShapeColourA());
		if(relief) buffer.setColour((reliefRgb >> 16) & 0xFF, (reliefRgb >> 8) & 0xFF, reliefRgb & 0xFF, alpha);
		else buffer.setColour((int) (255 * shapeSet.getShapeColourR()), (int) (255 * shapeSet.getShapeColourG()), (int) (255 * shapeSet.getShapeColourB()), alpha);
	}

	// A world buffer made again for the focus keeps the colours
	@Override
	protected void colourCube(IShapeBuffer buffer, int x, int y, int z) {
		Geometry g = generated;
		setCubeColour(buffer, g != null && g.isRelief(x, z));
	}

	// Build focus: Walls, Floor, Roof, Details (rim, ring, spokes, relief, the floor's pattern) and Pillars
	@Override
	public boolean hasParts() {
		return true;
	}

	@Override
	public FocusFilter.Part[] focusParts() {
		return buildingParts;
	}

	@Override
	public FocusFilter.Part partOf(int x, int y, int z) {
		Geometry g = generated;
		FocusFilter.Part part = g == null ? null : g.partAt(x, y, z);
		return part == null ? FocusFilter.Part.WALLS : part;
	}

	@Override
	public FocusFilter.Part getFocusPart() {
		FocusFilter.Part part = propertyFocusPart.value;
		for(FocusFilter.Part p: buildingParts) if(p == part) return part;
		return part == FocusFilter.Part.HIDDEN ? part : FocusFilter.Part.ALL;
	}

	// A saved layer out of 0..4 counts as All
	@Override
	public int getFocusLayer() {
		int layer = propertyFocusLayer.value;
		return layer < 0 || layer > ShapeLayers.COUNT ? FocusFilter.LAYER_ALL : layer;
	}

	@Override
	protected void storeFocus(FocusFilter.Part part, int layer) {
		propertyFocusPart.setValue(part);
		propertyFocusLayer.setValue(layer);
	}

	/**
	 * The building as a pure function of a position (no net.minecraft). Distances are in the polygon's own
	 * measure: the distance along the normal of the nearest side, so a "radius" is the apothem, as in
	 * ShapePolygon (whose outline is the wall here at thickness 1). y = 0 is the floor, the walls go up to
	 * height - 1, the roof is at height; the pattern above it at height + 1, the floor's below at -1.
	 */
	static final class Geometry {
		int n;
		double r, angle, offset;
		int height, thickness;
		boolean floor, windows;
		int windowBottom, windowHeight, windowWidth, windowGap;
		boolean roof;
		int rimWidth, rimHeight;
		double ring;
		boolean spokes;
		double skylight;
		int relief;
		boolean columns, bands, floorPattern, floorSkylight;
		int innerDepth;
		int pillarHeight, pillarWidth;

		int maxR, minY, maxY;
		private double halfSide, circumR;
		private double[] vertexCos, vertexSin;
		private int[] pillarX, pillarZ;

		void prepare() {
			circumR = r / Math.cos(Math.PI / n);
			halfSide = r * Math.tan(Math.PI / n);
			maxR = (int) Math.ceil((r + relief + 1) / Math.cos(Math.PI / n) + offset) + 1;
			int top = roof && (ring > 0 || spokes || skylight > 0) ? 1 : 0;
			minY = -Math.max(pillarHeight, floorPattern ? 1 : 0);
			maxY = height - 1 + (roof ? 1 : 0) + Math.max(rimHeight, top);
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

		// Distance along side i's normal (the polygon's own "radius" of the point)
		private double polyDist(double px, double pz, int i) {
			double phi = 2 * Math.PI * i / n + angle;
			return px * Math.cos(phi) + pz * Math.sin(phi) + 1e-10; // Epsilon as in ShapePolygon
		}

		// Along side i, from its middle
		private double along(double px, double pz, int i) {
			double phi = 2 * Math.PI * i / n + angle;
			return -px * Math.sin(phi) + pz * Math.cos(phi);
		}

		// Outside the walls: only the relief is there
		boolean isRelief(int x, int z) {
			double px = x - offset, pz = z - offset;
			return polyDist(px, pz, side(px, pz)) - r >= 0.5;
		}

		boolean contains(int x, int y, int z) {
			return partAt(x, y, z) != null;
		}

		// The part of the block at this position, null where there is none
		FocusFilter.Part partAt(int x, int y, int z) {
			if(y < minY || y > maxY) return null;
			double px = x - offset, pz = z - offset;
			int i = side(px, pz);
			double d = polyDist(px, pz, i);
			double dr = d - r;
			if(dr >= 0.5) return relief(px, pz, i, dr, y) ? FocusFilter.Part.DETAILS : null;
			if(y < 0) {
				if(y == -1 && floorPattern && pattern(px, pz, d, dr, floorSkylight)) return FocusFilter.Part.DETAILS;
				return isPillar(x, z) && y >= -pillarHeight ? FocusFilter.Part.PILLARS : null;
			}
			boolean wall = dr >= 0.5 - thickness;
			if(y == 0) {
				if(floor && !(floorSkylight && hole(d))) return FocusFilter.Part.FLOOR;
				return wall ? FocusFilter.Part.WALLS : null;
			}
			if(y < height) {
				if(wall) return isWindow(px, pz, i, y) ? null : FocusFilter.Part.WALLS;
				// Inner depth: the roof's pattern under the roof, inside the walls, from the layer below it down
				// (never into the floor); open where the skylight is, as on top
				if(roof && innerDepth > 0 && y >= Math.max(1, height - innerDepth) && pattern(px, pz, d, dr, true)) return FocusFilter.Part.DETAILS;
				return null;
			}
			if(y == height) return roof && !hole(d) ? FocusFilter.Part.ROOF : null;
			// Above the roof: the rim on the outline, then (first layer only) the ring and the spokes
			int above = y - height;
			if(rimWidth > 0 && above <= rimHeight && dr >= 0.5 - rimWidth) return FocusFilter.Part.DETAILS;
			if(above == 1 && roof && pattern(px, pz, d, dr, true)) return FocusFilter.Part.DETAILS;
			return null;
		}

		private boolean hole(double d) {
			return skylight > 0 && d - skylight < -0.5;
		}

		// The roof's pattern (the floor's is the same): the rim's first layer, the ring, the skylight's border
		// and the spokes; with the skylight open nothing inside it
		private boolean pattern(double px, double pz, double d, double dr, boolean open) {
			if(open && hole(d)) return false;
			if(rimWidth > 0 && dr >= 0.5 - rimWidth) return true;
			if(ring > 0 && Math.abs(d - ring) < 0.5) return true;
			if(open && skylight > 0 && Math.abs(d - skylight) < 0.5 && (ring > 0 || spokes)) return true;
			if(spokes) {
				for(int v = 0;v < n;++v) {
					double a = px * vertexCos[v] + pz * vertexSin[v];
					double c = -px * vertexSin[v] + pz * vertexCos[v];
					if(Math.abs(c) < 0.5 && a >= -0.5) return true;
				}
			}
			return false;
		}

		// Out of the walls, up to `relief` blocks: columns on the corners from the bottom to the top of the
		// rim, and bands round the floor (and the floor's pattern) and the roof
		private boolean relief(double px, double pz, int i, double dr, int y) {
			if(relief <= 0 || dr >= 0.5 + relief) return false;
			int bottom = floorPattern ? -1 : 0, top = height + (roof ? Math.max(rimHeight, 0) : -1);
			if(y < bottom || y > top) return false;
			if(columns && Math.abs(along(px, pz, i)) >= halfSide - 1) return true;
			if(bands && (y == bottom || y == 0 || (roof && y == height))) return true;
			return false;
		}

		private boolean isWindow(double px, double pz, int i, int y) {
			if(!windows || windowHeight <= 0 || windowWidth <= 0) return false;
			if(y < windowBottom || y >= windowBottom + windowHeight) return false;
			double u = along(px, pz, i);
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
