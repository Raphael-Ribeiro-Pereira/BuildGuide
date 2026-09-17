package brentmaas.buildguide.common.shape;

import java.util.HashSet;
import java.util.Set;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyCompactInt;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyPointRow;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyPositiveInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;

// Catmull-Rom spline through five control points, extruded as a disc of the given diameter
public class ShapeSpline extends Shape implements IValidatable {
	private enum direction{
		X,
		Y,
		Z
	}

	private static final int numPoints = 5;

	private String[] directionNames = {"X", "Y", "Z"};

	private PropertyEnum<direction> propertyDir = new PropertyEnum<direction>(direction.Y, new Translatable("property.buildguide.direction"), () -> update(), directionNames);
	// Persistence order: p1x, p1y, p1z, p2x, ... p5z. Do not reorder; the compact layout is handled in onSelectedInGUI
	private PropertyCompactInt p1x = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "X"), () -> update(), 0);
	private PropertyCompactInt p1y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "Y"), () -> update(), 1);
	private PropertyCompactInt p1z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "Z"), () -> update(), 2);
	private PropertyCompactInt p2x = new PropertyCompactInt(5, new Translatable("property.buildguide.point", "2", "X"), () -> update(), 0);
	private PropertyCompactInt p2y = new PropertyCompactInt(3, new Translatable("property.buildguide.point", "2", "Y"), () -> update(), 1);
	private PropertyCompactInt p2z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "2", "Z"), () -> update(), 2);
	private PropertyCompactInt p3x = new PropertyCompactInt(10, new Translatable("property.buildguide.point", "3", "X"), () -> update(), 0);
	private PropertyCompactInt p3y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "3", "Y"), () -> update(), 1);
	private PropertyCompactInt p3z = new PropertyCompactInt(5, new Translatable("property.buildguide.point", "3", "Z"), () -> update(), 2);
	private PropertyCompactInt p4x = new PropertyCompactInt(15, new Translatable("property.buildguide.point", "4", "X"), () -> update(), 0);
	private PropertyCompactInt p4y = new PropertyCompactInt(3, new Translatable("property.buildguide.point", "4", "Y"), () -> update(), 1);
	private PropertyCompactInt p4z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "4", "Z"), () -> update(), 2);
	private PropertyCompactInt p5x = new PropertyCompactInt(20, new Translatable("property.buildguide.point", "5", "X"), () -> update(), 0);
	private PropertyCompactInt p5y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "5", "Y"), () -> update(), 1);
	private PropertyCompactInt p5z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "5", "Z"), () -> update(), 2);
	private PropertyPositiveFloat propertyDiameter = new PropertyPositiveFloat(1.0f, new Translatable("property.buildguide.diameter"), () -> update());
	private PropertyPositiveInt propertyStepsPerSegment = new PropertyPositiveInt(8, new Translatable("property.buildguide.stepspersegment"), () -> update());
	// PropertyRunnable renders as a button
	private PropertyRunnable propertyValidate = new PropertyRunnable(() -> triggerValidation(), new Translatable("property.buildguide.validate"));
	// Row owners (label + Set + from-player) for the five points; appended last so older saves still load
	private PropertyPointRow[] pointRows = new PropertyPointRow[numPoints];

	private final Set<Long> expectedBlocks = new HashSet<Long>();
	private transient boolean validateNextRender = false;

	public ShapeSpline() {
		super();

		properties.add(propertyDir);
		properties.add(p1x);
		properties.add(p1y);
		properties.add(p1z);
		properties.add(p2x);
		properties.add(p2y);
		properties.add(p2z);
		properties.add(p3x);
		properties.add(p3y);
		properties.add(p3z);
		properties.add(p4x);
		properties.add(p4y);
		properties.add(p4z);
		properties.add(p5x);
		properties.add(p5y);
		properties.add(p5z);
		properties.add(propertyDiameter);
		properties.add(propertyStepsPerSegment);
		properties.add(propertyValidate);

		PropertyCompactInt[][] points = {{p1x, p1y, p1z}, {p2x, p2y, p2z}, {p3x, p3y, p3z}, {p4x, p4y, p4z}, {p5x, p5y, p5z}};
		for(int i = 0;i < numPoints;++i) {
			pointRows[i] = new PropertyPointRow(new Translatable("property.buildguide.pointrow", "" + (i + 1)), points[i][0], points[i][1], points[i][2], () -> update(), () -> {
				ShapeSet.Origin pos = getPlayerPositionLocal();
				return new int[] {pos.x, pos.y, pos.z};
			});
			properties.add(pointRows[i]);
		}
	}

	/**
	 * Custom layout: one row per point (X Y Z Set Pos) instead of three, so the panel fits
	 * on screen. Rows, top to bottom: direction, point 1..5, diameter, steps, validate.
	 */
	@Override
	public void onSelectedInGUI() {
		int row = 0;
		row = placeRow(propertyDir, row);
		Property<?>[] pointProps = {p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z, p4x, p4y, p4z, p5x, p5y, p5z};
		for(int i = 0;i < numPoints;++i) {
			placeRow(pointProps[3 * i], row);
			placeRow(pointProps[3 * i + 1], row);
			placeRow(pointProps[3 * i + 2], row);
			row = placeRow(pointRows[i], row);
		}
		row = placeRow(propertyDiameter, row);
		row = placeRow(propertyStepsPerSegment, row);
		row = placeRow(propertyValidate, row);
	}

	private int placeRow(Property<?> p, int row) {
		p.setX(ShapeScreen.basePropertiesX);
		p.setY(ShapeScreen.basePropertiesY + row * AbstractWidgetHandler.defaultSize);
		p.setVisibility(true);
		return row + 1;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		expectedBlocks.clear();

		int[] cp1 = {p1x.value, p1y.value, p1z.value};
		int[] cp2 = {p2x.value, p2y.value, p2z.value};
		int[] cp3 = {p3x.value, p3y.value, p3z.value};
		int[] cp4 = {p4x.value, p4y.value, p4z.value};
		int[] cp5 = {p5x.value, p5y.value, p5z.value};
		double radius = propertyDiameter.value / 2.0;
		int steps = Math.max(1, propertyStepsPerSegment.value);

		// Endpoints are duplicated so the curve passes through the first and last control point
		int[][][] segments = {
			{cp1, cp1, cp2, cp3},
			{cp1, cp2, cp3, cp4},
			{cp2, cp3, cp4, cp5},
			{cp3, cp4, cp5, cp5}
		};

		Set<Long> emitted = new HashSet<Long>();
		for(int[][] seg: segments) {
			for(int i = 0;i <= steps;++i) {
				double t = (double) i / steps;
				double cx = catmullRom(seg[0][0], seg[1][0], seg[2][0], seg[3][0], t);
				double cy = catmullRom(seg[0][1], seg[1][1], seg[2][1], seg[3][1], t);
				double cz = catmullRom(seg[0][2], seg[1][2], seg[2][2], seg[3][2], t);
				emitDisk(buffer, cx, cy, cz, radius, emitted);
			}
		}
	}

	private double catmullRom(int p0, int p1, int p2, int p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	// Disc perpendicular to the chosen direction, deduplicated against previously emitted blocks
	private void emitDisk(IShapeBuffer buffer, double cx, double cy, double cz, double radius, Set<Long> emitted) throws InterruptedException {
		int bx0 = (int) Math.round(cx);
		int by0 = (int) Math.round(cy);
		int bz0 = (int) Math.round(cz);
		int radiusCeil = (int) Math.ceil(radius);
		for(int da = -radiusCeil;da <= radiusCeil;++da) {
			for(int db = -radiusCeil;db <= radiusCeil;++db) {
				if(Math.sqrt(da * da + db * db) > radius) continue;
				int bx = bx0, by = by0, bz = bz0;
				switch(propertyDir.value) {
				case X:
					by = by0 + da;
					bz = bz0 + db;
					break;
				case Y:
					bx = bx0 + da;
					bz = bz0 + db;
					break;
				case Z:
					bx = bx0 + da;
					by = by0 + db;
					break;
				}
				long key = LocalPos.pack(bx, by, bz);
				if(emitted.add(key)) {
					addShapeCube(buffer, bx, by, bz);
					expectedBlocks.add(key);
				}
			}
		}
	}

	public void triggerValidation() {
		validateNextRender = true;
	}

	public boolean consumeValidateRequest() {
		if(validateNextRender) {
			validateNextRender = false;
			return true;
		}
		return false;
	}

	public Set<Long> getExpectedBlocks() {
		return expectedBlocks;
	}
}
