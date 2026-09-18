package brentmaas.buildguide.common.shape;

import java.util.HashSet;
import java.util.Set;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyCompactInt;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyPointRow;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyPositiveInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

/**
 * Composed shape: a Catmull-Rom curve through 2..5 control points with a deck stamped
 * along it. The curve is sampled by arc length; at each sample a horizontal cross-section
 * (Flat, Box or Disk profile) is placed perpendicular to the curve. The curve is the top row
 * of the deck and thickness grows downward.
 *
 * Geometry comes from the Step 0 enumerators (ShapeCuboid, Profiles); nothing is
 * instantiated. Handrails and pillars are appended in later steps.
 */
public class ShapeBridge extends Shape implements IValidatable {
	public enum Profile{
		FLAT,
		BOX,
		DISK
	}

	private static final int maxPoints = 5;
	private static final int minPoints = 2;

	private String[] profileNames = {"Flat", "Box", "Disk"};

	// Persistence order: p1x, p1y, p1z, ... p5z, count, sample step, profile, width, thickness, validate.
	// Point rows are GUI-only (not persisted). Handrail/pillar properties go after validate.
	private PropertyCompactInt p1x = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "X"), () -> update(), 0);
	private PropertyCompactInt p1y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "Y"), () -> update(), 1);
	private PropertyCompactInt p1z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "1", "Z"), () -> update(), 2);
	private PropertyCompactInt p2x = new PropertyCompactInt(10, new Translatable("property.buildguide.point", "2", "X"), () -> update(), 0);
	private PropertyCompactInt p2y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "2", "Y"), () -> update(), 1);
	private PropertyCompactInt p2z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "2", "Z"), () -> update(), 2);
	private PropertyCompactInt p3x = new PropertyCompactInt(20, new Translatable("property.buildguide.point", "3", "X"), () -> update(), 0);
	private PropertyCompactInt p3y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "3", "Y"), () -> update(), 1);
	private PropertyCompactInt p3z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "3", "Z"), () -> update(), 2);
	private PropertyCompactInt p4x = new PropertyCompactInt(30, new Translatable("property.buildguide.point", "4", "X"), () -> update(), 0);
	private PropertyCompactInt p4y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "4", "Y"), () -> update(), 1);
	private PropertyCompactInt p4z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "4", "Z"), () -> update(), 2);
	private PropertyCompactInt p5x = new PropertyCompactInt(40, new Translatable("property.buildguide.point", "5", "X"), () -> update(), 0);
	private PropertyCompactInt p5y = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "5", "Y"), () -> update(), 1);
	private PropertyCompactInt p5z = new PropertyCompactInt(0, new Translatable("property.buildguide.point", "5", "Z"), () -> update(), 2);
	private PropertyRangeInt propertyPointCount = new PropertyRangeInt(minPoints, new Translatable("property.buildguide.pointcount"), () -> onPointCountChanged(), minPoints, maxPoints);
	// Distance along the curve between cross-sections, in blocks. 0.5 closes gaps on the outer edge of bends
	private PropertyPositiveFloat propertySampleStep = new PropertyPositiveFloat(0.5f, new Translatable("property.buildguide.samplestep"), () -> update());
	private PropertyEnum<Profile> propertyProfile = new PropertyEnum<Profile>(Profile.FLAT, new Translatable("property.buildguide.profile"), () -> update(), profileNames);
	private PropertyPositiveInt propertyWidth = new PropertyPositiveInt(3, new Translatable("property.buildguide.width"), () -> update());
	private PropertyPositiveInt propertyThickness = new PropertyPositiveInt(1, new Translatable("property.buildguide.thickness"), () -> update());
	// PropertyRunnable renders as a button
	private PropertyRunnable propertyValidate = new PropertyRunnable(() -> triggerValidation(), new Translatable("property.buildguide.validate"));

	private PropertyCompactInt[][] points = {{p1x, p1y, p1z}, {p2x, p2y, p2z}, {p3x, p3y, p3z}, {p4x, p4y, p4z}, {p5x, p5y, p5z}};
	private PropertyPointRow[] pointRows = new PropertyPointRow[maxPoints];

	// Every emitted block (packed with LocalPos): dedup between overlapping sections and the validation set
	private final Set<Long> expectedBlocks = new HashSet<Long>();
	private transient boolean validateNextRender = false;

	public ShapeBridge() {
		super();

		for(int i = 0;i < maxPoints;++i) {
			properties.add(points[i][0]);
			properties.add(points[i][1]);
			properties.add(points[i][2]);
		}
		properties.add(propertyPointCount);
		properties.add(propertySampleStep);
		properties.add(propertyProfile);
		properties.add(propertyWidth);
		properties.add(propertyThickness);
		properties.add(propertyValidate);

		for(int i = 0;i < maxPoints;++i) {
			pointRows[i] = new PropertyPointRow(new Translatable("property.buildguide.pointrow", "" + (i + 1)), points[i][0], points[i][1], points[i][2], () -> update(), () -> {
				ShapeSet.Origin pos = getPlayerPositionLocal();
				return new int[] {pos.x, pos.y, pos.z};
			});
			addGuiOnly(pointRows[i]);
		}

		// Panel sections; Validate stays visible in all of them
		int sectionShape = declareSection(new Translatable("property.buildguide.section.shape"));
		int sectionDeck = declareSection(new Translatable("property.buildguide.section.deck"));
		assignSection(sectionShape, propertyPointCount, propertySampleStep);
		for(int i = 0;i < maxPoints;++i) assignSection(sectionShape, points[i][0], points[i][1], points[i][2], pointRows[i]);
		assignSection(sectionDeck, propertyProfile, propertyWidth, propertyThickness);
	}

	private void onPointCountChanged() {
		onSelectedInGUI(); // show/hide point rows
		update();
	}

	// Rows: section selector; Shape = count, point rows in use, sample step; Deck = profile, width, thickness; then Validate
	@Override
	public void onSelectedInGUI() {
		int row = placeSectionSelector();
		if(isShown(propertyPointCount)) row = placeRow(row, propertyPointCount);
		else propertyPointCount.setVisibility(false);
		for(int i = 0;i < maxPoints;++i) {
			Property<?>[] rowProps = {points[i][0], points[i][1], points[i][2], pointRows[i]};
			if(i < propertyPointCount.value && isShown(pointRows[i])) {
				row = placeRow(row, rowProps);
			}else {
				for(Property<?> p: rowProps) p.setVisibility(false);
			}
		}
		for(Property<?> p: new Property<?>[] {propertySampleStep, propertyProfile, propertyWidth, propertyThickness}) {
			if(isShown(p)) row = placeRow(row, p);
			else p.setVisibility(false);
		}
		row = placeRow(row, propertyValidate);
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		expectedBlocks.clear();

		int count = Math.max(minPoints, Math.min(maxPoints, propertyPointCount.value));
		int[][] used = new int[count][];
		for(int i = 0;i < count;++i) used[i] = new int[] {points[i][0].value, points[i][1].value, points[i][2].value};
		CatmullRomCurve curve = new CatmullRomCurve(used);

		double length = curve.getLength();
		double step = Math.max(0.05, propertySampleStep.value);
		int width = Math.max(1, propertyWidth.value);
		int thickness = Math.max(1, propertyThickness.value);
		Profile profile = propertyProfile.value;

		// Lateral normal of the previous section; reused when the tangent has no horizontal
		// component (vertical or degenerate stretch) so the deck does not twist abruptly
		double nx = 1.0, nz = 0.0;
		for(double s = 0.0;;s += step) {
			boolean last = s >= length;
			if(last) s = length;
			double[] param = curve.parameterAtLength(s);
			int seg = (int) param[0];
			double t = param[1];
			double[] c = curve.sample(seg, t);
			double[] tangent = curve.tangent(seg, t);
			double h = Math.sqrt(tangent[0] * tangent[0] + tangent[2] * tangent[2]);
			if(h > 1e-6) {
				nx = -tangent[2] / h;
				nz = tangent[0] / h;
			}
			emitSection(buffer, c[0], c[1], c[2], nx, nz, width, thickness, profile);
			if(last) break;
		}
	}

	/**
	 * Places one cross-section at curve point (cx, cy, cz) with lateral normal (nx, 0, nz).
	 * Profiles are enumerated in section space (u in [0, width), v in [0, thickness)) and
	 * mapped as: x = round(cx + (u - (width-1)/2) * nx), y = round(cy) - v, z likewise.
	 */
	private void emitSection(IShapeBuffer buffer, double cx, double cy, double cz, double nx, double nz, int width, int thickness, Profile profile) throws InterruptedException {
		double uCentre = (width - 1) / 2.0;
		int topY = (int) Math.round(cy);
		IBlockConsumer place = (u, v, w) -> {
			double uc = u - uCentre;
			emit(buffer, (int) Math.round(cx + uc * nx), topY - v, (int) Math.round(cz + uc * nz));
		};
		switch(profile) {
		case FLAT:
			ShapeCuboid.enumerate(width, thickness, 1, ShapeCuboid.walls.ALL, false, place);
			break;
		case BOX:
			ShapeCuboid.enumerate(width, thickness, 1, ShapeCuboid.walls.NONE, false, place);
			break;
		case DISK:
			Profiles.filledEllipse(width, thickness, place);
			break;
		}
	}

	// Deduplicated emit: overlapping sections share blocks, and the set doubles as the validation set
	private void emit(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		long key = LocalPos.pack(x, y, z);
		if(expectedBlocks.add(key)) addShapeCube(buffer, x, y, z);
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
