package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyCompactInt;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyPointRow;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyPositiveInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

/**
 * Composed shape: a Catmull-Rom curve through 2..5 control points with a deck stamped
 * along it, optional handrails (continuous rail and/or posts) on either side. The curve is
 * sampled by arc length; at each sample horizontal cross-sections are placed perpendicular
 * to the curve. Conventions: the curve is the top row of the deck and deck thickness grows
 * downward; rails sit `Rail elevation` blocks above the curve and grow upward.
 *
 * Geometry comes from the Step 0 enumerators (ShapeCuboid, Profiles); nothing is
 * instantiated. Pillars are appended in Step 3.
 */
public class ShapeBridge extends Shape {
	public enum Profile{
		FLAT,
		BOX,
		DISK
	}

	public enum RailMode{
		NONE,
		CONTINUOUS,
		POSTS,
		BOTH
	}

	public enum RailSides{
		LEFT,
		RIGHT,
		BOTH
	}

	public enum RailProfile{
		SQUARE,
		ROUND
	}
	
	public enum PillarMode{
		NONE,
		ON
	}
	
	public enum PillarShape{
		SQUARE,
		ROUND,
		LINE,
		TAPER
	}

	// How a w x h section is filled when placed
	private enum SectionKind{
		FILLED,
		OUTLINE,
		ELLIPSE,
		HOLLOW_ELLIPSE
	}

	private static final int maxPoints = 5;
	private static final int minPoints = 2;
	// Consecutive samples are subdivided until no lateral edge moves more than this (blocks)
	private static final double maxEdgeStep = 0.75;

	private String[] profileNames = {"Flat", "Box", "Disk"};
	private String[] railModeNames = {"None", "Continuous", "Posts only", "Both"};
	private String[] railSidesNames = {"Left", "Right", "Both"};
	private String[] railProfileNames = {"Square", "Round"};
	private String[] pillarModeNames = {"None", "On"};
	private String[] pillarShapeNames = {"Square", "Round", "Line", "Taper"};

	// Persistence order: p1x, p1y, p1z, ... p5z, count, sample step, profile, width, thickness, validate,
	// then (Step 2) rail mode, rail sides, rail profile, rail width, rail height, rail elevation, rail inset,
	// post spacing, then (Step 3) pillar mode, pillar shape, pillar width, pillar depth, pillar spacing,
	// pillar taper. Point rows are GUI-only (not persisted).
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
	// Distance along the curve between cross-sections on straight stretches, in blocks; bends are subdivided adaptively
	private PropertyPositiveFloat propertySampleStep = new PropertyPositiveFloat(1.0f, new Translatable("property.buildguide.samplestep"), () -> update());
	private PropertyEnum<Profile> propertyProfile = new PropertyEnum<Profile>(Profile.FLAT, new Translatable("property.buildguide.profile"), () -> update(), profileNames);
	private PropertyPositiveInt propertyWidth = new PropertyPositiveInt(3, new Translatable("property.buildguide.width"), () -> update());
	private PropertyPositiveInt propertyThickness = new PropertyPositiveInt(1, new Translatable("property.buildguide.thickness"), () -> update());
	// PropertyRunnable renders as a button
	private PropertyRunnable propertyValidate = new PropertyRunnable(() -> triggerValidation(), new Translatable("property.buildguide.validate"));
	// Rails (Step 2). Rail width/height are the rail's cross-section; elevation is how far above
	// the curve the section starts; inset moves it from the deck edge inward (negative = outward)
	private PropertyEnum<RailMode> propertyRailMode = new PropertyEnum<RailMode>(RailMode.NONE, new Translatable("property.buildguide.railmode"), () -> update(), railModeNames);
	private PropertyEnum<RailSides> propertyRailSides = new PropertyEnum<RailSides>(RailSides.BOTH, new Translatable("property.buildguide.railsides"), () -> update(), railSidesNames);
	private PropertyEnum<RailProfile> propertyRailProfile = new PropertyEnum<RailProfile>(RailProfile.SQUARE, new Translatable("property.buildguide.railprofile"), () -> update(), railProfileNames);
	private PropertyPositiveInt propertyRailWidth = new PropertyPositiveInt(1, new Translatable("property.buildguide.railwidth"), () -> update());
	private PropertyPositiveInt propertyRailHeight = new PropertyPositiveInt(1, new Translatable("property.buildguide.railheight"), () -> update());
	private PropertyInt propertyRailElevation = new PropertyInt(1, new Translatable("property.buildguide.railelevation"), () -> update());
	private PropertyInt propertyRailInset = new PropertyInt(0, new Translatable("property.buildguide.railinset"), () -> update());
	// Target distance between posts along the curve; posts are spread evenly and always sit at both ends
	private PropertyPositiveInt propertyPostSpacing = new PropertyPositiveInt(4, new Translatable("property.buildguide.postspacing"), () -> update());
	// Pillars (Step 3): columns centred on the curve, from the row under the deck down `Pillar depth`
	// rows. Taper is the base/top width ratio of the Taper shape (round frustum); 1 = straight
	private PropertyEnum<PillarMode> propertyPillarMode = new PropertyEnum<PillarMode>(PillarMode.NONE, new Translatable("property.buildguide.pillarmode"), () -> update(), pillarModeNames);
	private PropertyEnum<PillarShape> propertyPillarShape = new PropertyEnum<PillarShape>(PillarShape.SQUARE, new Translatable("property.buildguide.pillarshape"), () -> update(), pillarShapeNames);
	private PropertyPositiveInt propertyPillarWidth = new PropertyPositiveInt(3, new Translatable("property.buildguide.pillarwidth"), () -> update());
	private PropertyPositiveInt propertyPillarDepth = new PropertyPositiveInt(10, new Translatable("property.buildguide.pillardepth"), () -> update());
	private PropertyPositiveInt propertyPillarSpacing = new PropertyPositiveInt(12, new Translatable("property.buildguide.pillarspacing"), () -> update());
	private PropertyPositiveFloat propertyPillarTaper = new PropertyPositiveFloat(1.0f, new Translatable("property.buildguide.pillartaper"), () -> update());

	private PropertyCompactInt[][] points = {{p1x, p1y, p1z}, {p2x, p2y, p2z}, {p3x, p3y, p3z}, {p4x, p4y, p4z}, {p5x, p5y, p5z}};
	private PropertyPointRow[] pointRows = new PropertyPointRow[maxPoints];

	// Lateral normal of the previous section; reused when the tangent has no horizontal
	// component (vertical or degenerate stretch) so the deck does not twist abruptly
	private double lastNx = 1.0, lastNz = 0.0;

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
		properties.add(propertyRailMode);
		properties.add(propertyRailSides);
		properties.add(propertyRailProfile);
		properties.add(propertyRailWidth);
		properties.add(propertyRailHeight);
		properties.add(propertyRailElevation);
		properties.add(propertyRailInset);
		properties.add(propertyPostSpacing);
		properties.add(propertyPillarMode);
		properties.add(propertyPillarShape);
		properties.add(propertyPillarWidth);
		properties.add(propertyPillarDepth);
		properties.add(propertyPillarSpacing);
		properties.add(propertyPillarTaper);

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
		int sectionRails = declareSection(new Translatable("property.buildguide.section.rails"));
		int sectionSupports = declareSection(new Translatable("property.buildguide.section.supports"));
		assignSection(sectionShape, propertyPointCount);
		hideFromGui(propertySampleStep); // inert since adaptive subdivision; kept for persistence alignment
		for(int i = 0;i < maxPoints;++i) assignSection(sectionShape, points[i][0], points[i][1], points[i][2], pointRows[i]);
		assignSection(sectionDeck, propertyProfile, propertyWidth, propertyThickness);
		assignSection(sectionRails, propertyRailMode, propertyRailSides, propertyRailProfile, propertyRailWidth, propertyRailHeight, propertyRailElevation, propertyRailInset, propertyPostSpacing);
		assignSection(sectionSupports, propertyPillarMode, propertyPillarShape, propertyPillarWidth, propertyPillarDepth, propertyPillarSpacing, propertyPillarTaper);
		
		// The screen's Reset button restores the current section; control points and count are never reset
		for(int i = 0;i < maxPoints;++i) protectFromReset(points[i][0], points[i][1], points[i][2]);
		protectFromReset(propertyPointCount);
	}

	private void onPointCountChanged() {
		onSelectedInGUI(); // show/hide point rows
		update();
	}

	// Rows: section selector; then the current section's properties in list order (point rows only up to the count); then Validate
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
		Property<?>[] rest = {propertyProfile, propertyWidth, propertyThickness, propertyRailMode, propertyRailSides, propertyRailProfile, propertyRailWidth, propertyRailHeight, propertyRailElevation, propertyRailInset, propertyPostSpacing, propertyPillarMode, propertyPillarShape, propertyPillarWidth, propertyPillarDepth, propertyPillarSpacing, propertyPillarTaper};
		for(Property<?> p: rest) {
			if(isShown(p)) row = placeRow(row, p);
			else p.setVisibility(false);
		}
		row = placeRow(row, propertyValidate);
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		lastNx = 1.0;
		lastNz = 0.0;

		int count = Math.max(minPoints, Math.min(maxPoints, propertyPointCount.value));
		int[][] used = new int[count][];
		for(int i = 0;i < count;++i) used[i] = new int[] {points[i][0].value, points[i][1].value, points[i][2].value};
		CatmullRomCurve curve = new CatmullRomCurve(used);

		double length = curve.getLength();
		double step = Math.max(0.05, propertySampleStep.value);
		int width = Math.max(1, propertyWidth.value);
		int thickness = Math.max(1, propertyThickness.value);
		SectionKind deckKind = propertyProfile.value == Profile.FLAT ? SectionKind.FILLED : propertyProfile.value == Profile.BOX ? SectionKind.OUTLINE : SectionKind.ELLIPSE;
		double halfWidth = (width - 1) / 2.0;

		RailMode railMode = propertyRailMode.value;
		boolean continuousRail = railMode == RailMode.CONTINUOUS || railMode == RailMode.BOTH;
		boolean posts = railMode == RailMode.POSTS || railMode == RailMode.BOTH;
		boolean leftRail = propertyRailSides.value != RailSides.RIGHT;
		boolean rightRail = propertyRailSides.value != RailSides.LEFT;
		int railWidth = Math.max(1, propertyRailWidth.value);
		int railHeight = Math.max(1, propertyRailHeight.value);
		int railElevation = propertyRailElevation.value;
		double railLateral = halfWidth - propertyRailInset.value;
		SectionKind railKind = propertyRailProfile.value == RailProfile.ROUND ? SectionKind.HOLLOW_ELLIPSE : SectionKind.FILLED;

		// The outermost element decides how densely a bend is sampled
		double ext = halfWidth;
		if(continuousRail) ext = Math.max(ext, Math.abs(railLateral) + (railWidth - 1) / 2.0);

		// Walk the curve by arc length. `Sample step` sets the spacing at the centre line; on a
		// bend the outer edge moves further than the centre, so consecutive samples are
		// subdivided until neither edge jumps more than maxEdgeStep blocks
		double prevS = 0.0;
		double[] prevEdgeL = null, prevEdgeR = null;
		for(double s = 0.0;;s += step) {
			boolean last = s >= length;
			if(last) s = length;
			if(prevEdgeL != null) {
				double[] f = frameAt(curve, s);
				double moved = Math.max(edgeDistance(prevEdgeL, f, -ext), edgeDistance(prevEdgeR, f, ext));
				int sub = (int) Math.ceil(moved / maxEdgeStep);
				for(int k = 1;k < sub;++k) {
					double[] fk = frameAt(curve, prevS + (s - prevS) * k / sub);
					emitDeck(buffer, fk, width, thickness, deckKind);
					if(continuousRail) emitRails(buffer, fk, leftRail, rightRail, railLateral, railElevation, railWidth, railHeight, railKind);
				}
			}
			double[] f = frameAt(curve, s);
			emitDeck(buffer, f, width, thickness, deckKind);
			if(continuousRail) emitRails(buffer, f, leftRail, rightRail, railLateral, railElevation, railWidth, railHeight, railKind);
			prevEdgeL = edgePoint(f, -ext);
			prevEdgeR = edgePoint(f, ext);
			prevS = s;
			if(last) break;
		}

		// Posts: spread evenly along the curve, always at both ends. `Post spacing` is a
		// target; the real spacing is length / (n - 1) so the last gap is never a stub
		if(posts) {
			double spacing = Math.max(1, propertyPostSpacing.value);
			int n = Math.max(2, (int) Math.round(length / spacing) + 1);
			int postHeight = railElevation + railHeight - 1; // from the row above the deck up to the rail top
			for(int k = 0;k < n;++k) {
				double[] f = frameAt(curve, length * k / (n - 1));
				emitPosts(buffer, f, leftRail, rightRail, railLateral, railWidth, postHeight);
			}
		}
		
		// Pillars: same even spread as posts, own spacing; centred on the curve, from the row
		// under the deck down `Pillar depth` rows
		if(propertyPillarMode.value == PillarMode.ON) {
			double spacing = Math.max(1, propertyPillarSpacing.value);
			int n = Math.max(2, (int) Math.round(length / spacing) + 1);
			int pillarWidth = Math.max(1, propertyPillarWidth.value);
			int depth = Math.max(1, propertyPillarDepth.value);
			// End pillars are pulled inward by half their footprint so they stay under the deck
			// instead of being centred on the very end of the curve
			double endInset = Math.min(length / 2.0, (pillarWidth - 1) / 2.0);
			double first = endInset, last = length - endInset;
			for(int k = 0;k < n;++k) {
				double[] f = frameAt(curve, first + (last - first) * k / (n - 1));
				int yTop = (int) Math.round(f[1]) - thickness;
				placeColumn(buffer, f, yTop, depth, pillarWidth, propertyPillarShape.value, propertyPillarTaper.value);
			}
		}
	}

	// {cx, cy, cz, nx, nz} at arc length s; n = normalize(-tz, 0, tx), +n is the right-hand side of travel
	private double[] frameAt(CatmullRomCurve curve, double s) {
		double[] param = curve.parameterAtLength(s);
		int seg = (int) param[0];
		double t = param[1];
		double[] c = curve.sample(seg, t);
		double[] tangent = curve.tangent(seg, t);
		double h = Math.sqrt(tangent[0] * tangent[0] + tangent[2] * tangent[2]);
		if(h > 1e-6) {
			lastNx = -tangent[2] / h;
			lastNz = tangent[0] / h;
		}
		return new double[] {c[0], c[1], c[2], lastNx, lastNz};
	}

	private static double[] edgePoint(double[] f, double u) {
		return new double[] {f[0] + u * f[3], f[1], f[2] + u * f[4]};
	}

	private static double edgeDistance(double[] prevEdge, double[] f, double u) {
		double[] e = edgePoint(f, u);
		double dx = e[0] - prevEdge[0], dy = e[1] - prevEdge[1], dz = e[2] - prevEdge[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// Deck: centred on the curve, top row at the curve, thickness downward
	private void emitDeck(IShapeBuffer buffer, double[] f, int width, int thickness, SectionKind kind) throws InterruptedException {
		placeSection(buffer, f, 0.0, (int) Math.round(f[1]), -1, width, thickness, kind);
	}

	// Continuous rail(s): section base `elevation` rows above the curve, growing upward
	private void emitRails(IShapeBuffer buffer, double[] f, boolean left, boolean right, double lateral, int elevation, int w, int h, SectionKind kind) throws InterruptedException {
		int yBase = (int) Math.round(f[1]) + elevation;
		if(left) placeSection(buffer, f, -lateral, yBase, 1, w, h, kind);
		if(right) placeSection(buffer, f, lateral, yBase, 1, w, h, kind);
	}

	// Post(s): column as wide as the rail, from the row above the deck up to the rail top
	private void emitPosts(IShapeBuffer buffer, double[] f, boolean left, boolean right, double lateral, int w, int height) throws InterruptedException {
		if(height < 1) return;
		int yBase = (int) Math.round(f[1]) + 1;
		if(left) placeSection(buffer, f, -lateral, yBase, 1, w, height, SectionKind.FILLED);
		if(right) placeSection(buffer, f, lateral, yBase, 1, w, height, SectionKind.FILLED);
	}

	/**
	 * Places one w x h cross-section at frame f, centred `lateral` blocks along the normal.
	 * Sections are enumerated in section space (u in [0, w), v in [0, h)) and mapped as:
	 * x = round(cx + (lateral + u - (w-1)/2) * nx), y = yBase + ySign * v, z likewise.
	 */
	private void placeSection(IShapeBuffer buffer, double[] f, double lateral, int yBase, int ySign, int w, int h, SectionKind kind) throws InterruptedException {
		double cx = f[0], cz = f[2], nx = f[3], nz = f[4];
		double uCentre = (w - 1) / 2.0;
		IBlockConsumer place = (u, v, d) -> {
			double uc = lateral + u - uCentre;
			emit(buffer, (int) Math.round(cx + uc * nx), yBase + ySign * v, (int) Math.round(cz + uc * nz));
		};
		switch(kind) {
		case FILLED:
			ShapeCuboid.enumerate(w, h, 1, ShapeCuboid.walls.ALL, false, place);
			break;
		case OUTLINE:
			ShapeCuboid.enumerate(w, h, 1, ShapeCuboid.walls.NONE, false, place);
			break;
		case ELLIPSE:
			Profiles.filledEllipse(w, h, place);
			break;
		case HOLLOW_ELLIPSE:
			Profiles.hollowEllipse(w, h, place);
			break;
		}
	}

	// Horizontal unit tangent {tx, tz} recovered from the lateral normal (n is t rotated +90 degrees about Y)
	static double[] tangentOf(double nx, double nz) {
		return new double[] {nz, -nx};
	}
	
	/**
	 * Places a vertical column at frame f: footprint w x w in the horizontal plane (u along
	 * the normal, v along the tangent), rows yTop, yTop-1, ... yTop-depth+1. Square/Round
	 * stamp a 2D footprint per row; Line is a single block; Taper is a solid round frustum
	 * from ShapeCone.enumerate with height < 0 (grows downward), base/top width ratio `taper`.
	 */
	private void placeColumn(IShapeBuffer buffer, double[] f, int yTop, int depth, int w, PillarShape shape, float taper) throws InterruptedException {
		double cx = f[0], cz = f[2], nx = f[3], nz = f[4];
		double[] t = tangentOf(nx, nz);
		double centre = (w - 1) / 2.0;
		switch(shape) {
		case LINE:
			for(int row = 0;row < depth;++row) emit(buffer, (int) Math.round(cx), yTop - row, (int) Math.round(cz));
			break;
		case SQUARE:
		case ROUND:
			for(int row = 0;row < depth;++row) {
				int y = yTop - row;
				IBlockConsumer place = (u, v, d) -> {
					double uc = u - centre, vc = v - centre;
					emit(buffer, (int) Math.round(cx + uc * nx + vc * t[0]), y, (int) Math.round(cz + uc * nz + vc * t[1]));
				};
				if(shape == PillarShape.SQUARE) ShapeCuboid.enumerate(w, w, 1, ShapeCuboid.walls.ALL, false, place);
				else Profiles.filledEllipse(w, w, place);
			}
			break;
		case TAPER:
			// Cone axis along its Y: it emits (x, z, y) with z in [-(depth-1), 0]; even widths use the
			// cone's 0.5 offset, so the footprint is centred by subtracting it back
			boolean evenMode = w % 2 == 0;
			double shift = evenMode ? 0.5 : 0.0;
			float radius = (w - 1) / 2.0f;
			ShapeCone.enumerate(ShapeCone.direction.Y, radius, -(depth - 1), evenMode, radius * Math.max(0.0f, taper), ShapeCone.Mode.SOLID, 1.0f, 1, (x, y, z) -> {
				double uc = x - shift, vc = z - shift;
				emit(buffer, (int) Math.round(cx + uc * nx + vc * t[0]), yTop + y, (int) Math.round(cz + uc * nz + vc * t[1]));
			});
			break;
		}
	}
	
	// Deduplicated emit: overlapping sections share blocks (the base set also feeds validation)
	private void emit(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		addShapeCubeIfNew(buffer, x, y, z);
	}
}
