package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;

/**
 * Island (block A): a seeded floating island, always a hollow shell with a flat top. The plan
 * (outline) sets the distance from the centre to the edge per angle, the body hangs below it down
 * to Depth. Geometry lives in IslandGeometry; this class only holds the properties.
 */
public class ShapeIsland extends Shape {
	private String[] outlineNames = {"Circle", "Square", "Polygon", "Organic"};
	private String[] profileNames = {"Bowl", "Cone", "Terraced"};

	// Persistence order: outline, width X, width Z, sides, roundness, rotation, edge amplitude, edge
	// scale, wall, depth, profile, sharpness, roughness, seed, new seed. New properties go at the end
	private PropertyEnum<Outline> propertyOutline = new PropertyEnum<Outline>(Outline.ORGANIC, new Translatable("property.buildguide.outline"), () -> update(), outlineNames);
	private PropertyRangeInt propertyWidthX = new PropertyRangeInt(41, new Translatable("property.buildguide.widthx"), () -> update(), IslandGeometry.minWidth, IslandGeometry.maxWidth);
	private PropertyRangeInt propertyWidthZ = new PropertyRangeInt(41, new Translatable("property.buildguide.widthz"), () -> update(), IslandGeometry.minWidth, IslandGeometry.maxWidth);
	private PropertyRangeInt propertySides = new PropertyRangeInt(6, new Translatable("property.buildguide.sides"), () -> update(), IslandGeometry.minSides, IslandGeometry.maxSides);
	// 0 = square corners, 1 = round (Square, Polygon and Organic)
	private PropertyFloat propertyRoundness = new PropertyFloat(0.7f, new Translatable("property.buildguide.roundness"), () -> update());
	// Degrees around the vertical axis
	private PropertyFloat propertyRotation = new PropertyFloat(0.0f, new Translatable("property.buildguide.rotation"), () -> update());
	// Organic only: edge noise as a fraction of the radius, and about how many bumps around the edge
	private PropertyFloat propertyEdgeAmplitude = new PropertyFloat(0.25f, new Translatable("property.buildguide.edgeamplitude"), () -> update());
	private PropertyFloat propertyEdgeScale = new PropertyFloat(6.0f, new Translatable("property.buildguide.edgescale"), () -> update());
	private PropertyRangeInt propertyWall = new PropertyRangeInt(2, new Translatable("property.buildguide.wall"), () -> update(), IslandGeometry.minWall, IslandGeometry.maxWall);
	private PropertyRangeInt propertyDepth = new PropertyRangeInt(24, new Translatable("property.buildguide.depth"), () -> update(), 0, IslandGeometry.maxDepth);
	private PropertyEnum<Profile> propertyProfile = new PropertyEnum<Profile>(Profile.BOWL, new Translatable("property.buildguide.profile"), () -> update(), profileNames);
	// 0 = rounded underside, 1 = pointed
	private PropertyFloat propertySharpness = new PropertyFloat(0.4f, new Translatable("property.buildguide.sharpness"), () -> update());
	// Noise on the depth of each column (0 = smooth)
	private PropertyFloat propertyRoughness = new PropertyFloat(0.3f, new Translatable("property.buildguide.roughness"), () -> update());
	private PropertyInt propertySeed = new PropertyInt(1, new Translatable("property.buildguide.seed"), () -> update());
	// PropertyRunnable renders as a button; persisted as a placeholder like Validate
	private PropertyRunnable propertyNewSeed = new PropertyRunnable(() -> newSeed(), new Translatable("property.buildguide.newseed"));

	public ShapeIsland() {
		super();

		properties.add(propertyOutline);
		properties.add(propertyWidthX);
		properties.add(propertyWidthZ);
		properties.add(propertySides);
		properties.add(propertyRoundness);
		properties.add(propertyRotation);
		properties.add(propertyEdgeAmplitude);
		properties.add(propertyEdgeScale);
		properties.add(propertyWall);
		properties.add(propertyDepth);
		properties.add(propertyProfile);
		properties.add(propertySharpness);
		properties.add(propertyRoughness);
		properties.add(propertySeed);
		properties.add(propertyNewSeed);

		int sectionBase = declareSection(new Translatable("property.buildguide.section.base"));
		int sectionBody = declareSection(new Translatable("property.buildguide.section.body"));
		int sectionSeed = declareSection(new Translatable("property.buildguide.section.seed"));
		assignSection(sectionBase, propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRoundness, propertyRotation, propertyEdgeAmplitude, propertyEdgeScale);
		assignSection(sectionBody, propertyWall, propertyDepth, propertyProfile, propertySharpness, propertyRoughness);
		assignSection(sectionSeed, propertySeed, propertyNewSeed);
		// Reset on the Seed section must not throw away an island one likes
		protectFromReset(propertySeed);
	}

	private void newSeed() {
		propertySeed.setValue(new Random().nextInt(1000000));
		update();
	}

	IslandGeometry.Params params() {
		IslandGeometry.Params p = new IslandGeometry.Params();
		p.outline = propertyOutline.value;
		p.widthX = propertyWidthX.value;
		p.widthZ = propertyWidthZ.value;
		p.sides = propertySides.value;
		p.roundness = propertyRoundness.value;
		p.rotationDeg = propertyRotation.value;
		p.edgeAmplitude = propertyEdgeAmplitude.value;
		p.edgeScale = propertyEdgeScale.value;
		p.wall = propertyWall.value;
		p.depth = propertyDepth.value;
		p.profile = propertyProfile.value;
		p.sharpness = propertySharpness.value;
		p.roughness = propertyRoughness.value;
		p.seed = propertySeed.value;
		return p;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		setOriginOffset(0, 0, 0);
		IslandGeometry.enumerate(params(), (x, y, z) -> addShapeCube(buffer, x, y, z));
	}
}
