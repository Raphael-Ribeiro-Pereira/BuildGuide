package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyMinimumInt;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

/**
 * Farming land (2026-10-18): where the water goes in a farm of any outline (rectangle, polygon, circle), one
 * layer at ground level (y = 0). Crops: water on a grid (a water block hydrates farmland up to 4 blocks away,
 * squares of 9 by 9), then more water wherever the outline leaves farmland out of reach. Sugar cane: rows of
 * water every third line, the cane on both sides of each. The water is blue, the edge of the field in the
 * shape's colour and, optionally, the field (the farmland or the cane) green. Build focus parts: Water,
 * Field, Edge.
 */
public class ShapeFarmingLand extends Shape {
	public enum Outline {
		RECTANGLE,
		POLYGON,
		CIRCLE
	}
	public enum Crop {
		CROPS,
		SUGAR_CANE
	}
	public enum Rows {
		X,
		Z
	}
	private static final String[] outlineNames = {"Rectangle", "Polygon", "Circle"};
	private static final String[] cropNames = {"Crops", "Sugar cane"};
	private static final String[] rowNames = {"X", "Z"};
	private static final String[] focusPartNames = {"All", "Cap", "Body", "Spikes", "Hidden", "Walls", "Floor", "Roof", "Details", "Pillars", "Water", "Field", "Edge"};
	private static final FocusFilter.Part[] farmParts = {FocusFilter.Part.WATER, FocusFilter.Part.FIELD, FocusFilter.Part.EDGE};
	// A water block hydrates farmland up to this many blocks away on both axes (vanilla FarmBlock)
	public static final int hydration = 4;
	public static final int maxSize = 257;
	private static final int waterRgb = 0x3F76E4, fieldRgb = 0x7CBD3B;

	// Outline
	private PropertyEnum<Outline> propertyOutline = new PropertyEnum<Outline>(Outline.RECTANGLE, new Translatable("property.buildguide.outline"), () -> onLayoutChanged(), outlineNames);
	private PropertyRangeInt propertyWidthX = new PropertyRangeInt(18, new Translatable("property.buildguide.widthx"), () -> update(), 1, maxSize);
	private PropertyRangeInt propertyWidthZ = new PropertyRangeInt(18, new Translatable("property.buildguide.widthz"), () -> update(), 1, maxSize);
	private PropertyMinimumInt propertySides = new PropertyMinimumInt(6, new Translatable("property.buildguide.sides"), () -> update(), 3);
	private PropertyPositiveFloat propertyRadius = new PropertyPositiveFloat(10, new Translatable("property.buildguide.radius"), () -> update());
	private PropertyInt propertyAngle = new PropertyInt(0, new Translatable("property.buildguide.angle"), () -> update());
	// Farm
	private PropertyEnum<Crop> propertyCrop = new PropertyEnum<Crop>(Crop.CROPS, new Translatable("property.buildguide.crop"), () -> onLayoutChanged(), cropNames);
	private PropertyRangeInt propertySpacing = new PropertyRangeInt(9, new Translatable("property.buildguide.waterspacing"), () -> update(), 2, 2 * hydration + 1);
	private PropertyEnum<Rows> propertyRows = new PropertyEnum<Rows>(Rows.X, new Translatable("property.buildguide.rows"), () -> update(), rowNames);
	private PropertyBoolean propertyShowField = new PropertyBoolean(false, new Translatable("property.buildguide.showfield"), () -> update());
	private PropertyBoolean propertyShowEdge = new PropertyBoolean(true, new Translatable("property.buildguide.showedge"), () -> update());
	private PropertyEnum<FocusFilter.Part> propertyFocusPart = new PropertyEnum<FocusFilter.Part>(FocusFilter.Part.ALL, new Translatable("screen.buildguide.focus.part"), null, focusPartNames);
	private PropertyInt propertyFocusLayer = new PropertyInt(FocusFilter.LAYER_ALL, new Translatable("screen.buildguide.focus.layer"), null);

	private Property<?>[] outlineRows = {propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRadius, propertyAngle};
	private Property<?>[] farmRows = {propertyCrop, propertySpacing, propertyRows, propertyShowField, propertyShowEdge};

	// The last generation's layout (parts and colours), set before its first block
	private volatile Layout generated = null;

	public ShapeFarmingLand() {
		super();

		for(Property<?> p: outlineRows) properties.add(p);
		for(Property<?> p: farmRows) properties.add(p);
		properties.add(propertyFocusPart);
		properties.add(propertyFocusLayer);
		hideFromGui(propertyFocusPart);
		hideFromGui(propertyFocusLayer);
		assignSection(declareSection(new Translatable("property.buildguide.section.outline")), outlineRows);
		assignSection(declareSection(new Translatable("property.buildguide.section.farm")), farmRows);
	}

	// Only the rows the outline and the crop use
	@Override
	public void onSelectedInGUI() {
		Outline o = propertyOutline.value;
		boolean cane = propertyCrop.value == Crop.SUGAR_CANE;
		Property<?>[] rows = {propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRadius, propertyAngle, propertyCrop, propertySpacing, propertyRows, propertyShowField, propertyShowEdge};
		boolean[] used = {true, o == Outline.RECTANGLE, o == Outline.RECTANGLE, o == Outline.POLYGON, o != Outline.RECTANGLE, o == Outline.POLYGON, true, !cane, cane, true, true};
		int row = 0;
		for(int i = 0;i < rows.length;++i) {
			if(used[i] && isShown(rows[i])) row = placeRow(row, rows[i]);
			else hideRow(rows[i]);
		}
		hideRow(propertyFocusPart, propertyFocusLayer);
	}

	private void onLayoutChanged() {
		onSelectedInGUI();
		update();
	}

	Layout layout() {
		Layout l = new Layout();
		l.outline = propertyOutline.value;
		l.widthX = propertyWidthX.value;
		l.widthZ = propertyWidthZ.value;
		l.sides = propertySides.value;
		l.radius = Math.min(propertyRadius.value, maxSize / 2);
		l.angle = Math.toRadians(propertyAngle.value);
		l.crop = propertyCrop.value;
		l.spacing = propertySpacing.value;
		l.rows = propertyRows.value;
		l.showField = propertyShowField.value;
		l.showEdge = propertyShowEdge.value;
		l.build();
		return l;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		Layout l = layout();
		generated = l;
		int alpha = (int) (255 * shapeSet.getShapeColourA());
		for(int x = l.minX;x <= l.maxX;++x) {
			for(int z = l.minZ;z <= l.maxZ;++z) {
				FocusFilter.Part part = l.partAt(x, 0, z);
				if(part == null) continue;
				setColour(buffer, part, alpha);
				addShapeCube(buffer, x, 0, z);
			}
		}
	}

	private void setColour(IShapeBuffer buffer, FocusFilter.Part part, int alpha) {
		if(part == FocusFilter.Part.EDGE) {
			buffer.setColour((int) (255 * shapeSet.getShapeColourR()), (int) (255 * shapeSet.getShapeColourG()), (int) (255 * shapeSet.getShapeColourB()), alpha);
			return;
		}
		int rgb = part == FocusFilter.Part.WATER ? waterRgb : fieldRgb;
		buffer.setColour((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
	}

	// A world buffer made again for the focus keeps the colours
	@Override
	protected void colourCube(IShapeBuffer buffer, int x, int y, int z) {
		Layout l = generated;
		FocusFilter.Part part = l == null ? null : l.partAt(x, y, z);
		if(part != null) setColour(buffer, part, (int) (255 * shapeSet.getShapeColourA()));
	}

	@Override
	public boolean hasParts() {
		return true;
	}

	@Override
	public FocusFilter.Part[] focusParts() {
		return farmParts;
	}

	@Override
	public FocusFilter.Part partOf(int x, int y, int z) {
		Layout l = generated;
		FocusFilter.Part part = l == null ? null : l.partAt(x, y, z);
		return part == null ? FocusFilter.Part.FIELD : part;
	}

	@Override
	public FocusFilter.Part getFocusPart() {
		FocusFilter.Part part = propertyFocusPart.value;
		for(FocusFilter.Part p: farmParts) if(p == part) return part;
		return part == FocusFilter.Part.HIDDEN ? part : FocusFilter.Part.ALL;
	}

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
	 * The farm as a grid of cells (no net.minecraft): which cells are in the outline, which hold water, and
	 * from those the edge (a field cell next to a cell outside) and the field. Built once per generation.
	 */
	static final class Layout {
		Outline outline;
		int widthX, widthZ, sides;
		double radius, angle;
		Crop crop;
		int spacing;
		Rows rows;
		boolean showField, showEdge;

		int minX, maxX, minZ, maxZ;
		private int sizeX, sizeZ;
		private boolean[] inside, water;

		void build() {
			if(outline == Outline.RECTANGLE) {
				minX = -(widthX - 1) / 2;
				maxX = minX + widthX - 1;
				minZ = -(widthZ - 1) / 2;
				maxZ = minZ + widthZ - 1;
			}else {
				int r = (int) Math.ceil(outline == Outline.POLYGON ? (radius + 0.5) / Math.cos(Math.PI / sides) : radius + 0.5);
				minX = minZ = -r;
				maxX = maxZ = r;
			}
			sizeX = maxX - minX + 1;
			sizeZ = maxZ - minZ + 1;
			inside = new boolean[sizeX * sizeZ];
			water = new boolean[sizeX * sizeZ];
			for(int x = minX;x <= maxX;++x) {
				for(int z = minZ;z <= maxZ;++z) inside[index(x, z)] = isInOutline(x, z);
			}
			if(crop == Crop.SUGAR_CANE) placeCaneWater();
			else placeCropWater();
		}

		private int index(int x, int z) {
			return (x - minX) * sizeZ + (z - minZ);
		}

		boolean isInside(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ && inside[index(x, z)];
		}

		boolean isWater(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ && water[index(x, z)];
		}

		private boolean isInOutline(int x, int z) {
			switch(outline) {
			case POLYGON:
				double theta = Math.atan2(z, x) - angle;
				int i = (int) Math.floor(sides * theta / 2 / Math.PI + 0.5);
				double phi = 2 * Math.PI * i / sides + angle;
				return x * Math.cos(phi) + z * Math.sin(phi) - radius + 1e-10 < 0.5; // as ShapePolygon's outline
			case CIRCLE:
				return x * x + z * z <= (radius + 0.5) * (radius + 0.5);
			default:
				return true;
			}
		}

		// Crops: water every `spacing` cells on both axes, each grid point moved inside the outline when it falls
		// out; then any farmland still out of reach gets water of its own, as far inwards as it still covers
		// that cell. Every offset of the grid is tried and the one with the least water kept (ties: the first)
		private void placeCropWater() {
			boolean[] best = null;
			int bestCount = Integer.MAX_VALUE;
			for(int ox = 0;ox < spacing;++ox) {
				for(int oz = 0;oz < spacing;++oz) {
					java.util.Arrays.fill(water, false);
					int count = placeCropWater(ox, oz);
					if(count < bestCount) {
						bestCount = count;
						best = water.clone();
					}
				}
			}
			System.arraycopy(best, 0, water, 0, water.length);
		}

		private int placeCropWater(int ox, int oz) {
			int reach = Math.min(hydration, spacing / 2), count = 0;
			boolean[] wet = new boolean[inside.length];
			for(int gx = minX + ox;gx <= maxX + reach;gx += spacing) {
				for(int gz = minZ + oz;gz <= maxZ + reach;gz += spacing) {
					int[] p = nearestInside(gx, gz, reach);
					if(p != null && !water[index(p[0], p[1])]) {
						water[index(p[0], p[1])] = true;
						wetAround(wet, p[0], p[1]);
						++count;
					}
				}
			}
			for(int x = minX;x <= maxX;++x) {
				for(int z = minZ;z <= maxZ;++z) {
					int i = index(x, z);
					if(!inside[i] || water[i] || wet[i]) continue;
					int[] p = inwards(x, z);
					if(!water[index(p[0], p[1])]) ++count;
					water[index(p[0], p[1])] = true;
					wetAround(wet, p[0], p[1]);
				}
			}
			return count;
		}

		private void wetAround(boolean[] wet, int wx, int wz) {
			for(int x = Math.max(minX, wx - hydration);x <= Math.min(maxX, wx + hydration);++x) {
				for(int z = Math.max(minZ, wz - hydration);z <= Math.min(maxZ, wz + hydration);++z) wet[index(x, z)] = true;
			}
		}

		// The cell inside the outline nearest to (gx, gz) within `reach` on both axes, null when there is none
		private int[] nearestInside(int gx, int gz, int reach) {
			int[] best = null;
			int bestD = Integer.MAX_VALUE;
			for(int x = gx - reach;x <= gx + reach;++x) {
				for(int z = gz - reach;z <= gz + reach;++z) {
					if(!isInside(x, z)) continue;
					int d = (x - gx) * (x - gx) + (z - gz) * (z - gz);
					if(d < bestD) {
						bestD = d;
						best = new int[] {x, z};
					}
				}
			}
			return best;
		}

		// From (x, z) up to `hydration` cells towards the centre, the last one still inside the outline
		private int[] inwards(int x, int z) {
			int bx = x, bz = z;
			for(int k = 1;k <= hydration;++k) {
				int nx = x - Integer.signum(x) * Math.min(k, Math.abs(x)), nz = z - Integer.signum(z) * Math.min(k, Math.abs(z));
				if(isInside(nx, nz)) {
					bx = nx;
					bz = nz;
				}
			}
			return new int[] {bx, bz};
		}

		// Sugar cane: a row of water every third line across the rows' axis (cane, water, cane), the first
		// one on the second line so the edge lines are cane
		private void placeCaneWater() {
			for(int x = minX;x <= maxX;++x) {
				for(int z = minZ;z <= maxZ;++z) {
					int line = rows == Rows.X ? z - minZ : x - minX;
					if(Math.floorMod(line - 1, 3) == 0 && inside[index(x, z)]) water[index(x, z)] = true;
				}
			}
		}

		// The field: farmland for crops (every inside cell but the water), for cane only the cells beside water
		private boolean isField(int x, int z) {
			if(!isInside(x, z) || isWater(x, z)) return false;
			if(crop != Crop.SUGAR_CANE) return true;
			return isWater(x + 1, z) || isWater(x - 1, z) || isWater(x, z + 1) || isWater(x, z - 1);
		}

		private boolean isEdge(int x, int z) {
			return isInside(x, z) && !isWater(x, z) && (!isInside(x + 1, z) || !isInside(x - 1, z) || !isInside(x, z + 1) || !isInside(x, z - 1));
		}

		// The part of the cube at this position, null where there is none
		FocusFilter.Part partAt(int x, int y, int z) {
			if(y != 0 || !isInside(x, z)) return null;
			if(isWater(x, z)) return FocusFilter.Part.WATER;
			if(showEdge && isEdge(x, z)) return FocusFilter.Part.EDGE;
			if(showField && isField(x, z)) return FocusFilter.Part.FIELD;
			return null;
		}

		int countWater() {
			int n = 0;
			for(boolean w: water) if(w) ++n;
			return n;
		}
	}
}
