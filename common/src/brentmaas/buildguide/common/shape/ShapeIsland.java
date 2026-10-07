package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.IslandControls.Control;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.IslandGeometry.SpikeMode;

/**
 * Island (block A): a seeded floating island, always a hollow shell with a flat top. The plan
 * (outline) sets the distance from the centre to the edge per angle, the body hangs below it down
 * to Depth. Geometry lives in IslandGeometry, the Randomize / Naturalize recipes and which control
 * applies to which Outline in IslandControls; this class holds the properties and the panel.
 */
public class ShapeIsland extends Shape {
	static final float unitStep = 0.05f;
	static final int percentStep = 5;

	private String[] outlineNames = {"Circle", "Square", "Polygon", "Organic"};
	private String[] profileNames = {"Bowl", "Cone", "Terraced"};
	private String[] spikeModeNames = {"Random", "Ring", "Fill"};

	// Persistence order: outline, width X, width Z, sides, roundness, rotation, edge amplitude, edge
	// scale, wall, depth, profile, sharpness, roughness, seed, randomize (was New seed), then base %,
	// body %, naturalize, undo, then (block B) spikes, spike mode, spike length, spike base, length var %,
	// spread %, then (spikes 2, phase 1) jitter %. New properties go at the end. Labels: roundness reads "Corner round",
	// edge amplitude "Wobble", edge scale "Wobble size", wall "Thickness"
	private PropertyEnum<Outline> propertyOutline = new PropertyEnum<Outline>(Outline.CIRCLE, new Translatable("property.buildguide.outline"), () -> onOutlineChanged(), outlineNames);
	private PropertyRangeInt propertyWidthX = new PropertyRangeInt(41, new Translatable("property.buildguide.widthx"), () -> update(), IslandGeometry.minWidth, IslandGeometry.maxWidth);
	private PropertyRangeInt propertyWidthZ = new PropertyRangeInt(41, new Translatable("property.buildguide.widthz"), () -> update(), IslandGeometry.minWidth, IslandGeometry.maxWidth);
	private PropertyRangeInt propertySides = new PropertyRangeInt(6, new Translatable("property.buildguide.sides"), () -> update(), IslandGeometry.minSides, IslandGeometry.maxSides);
	// 0 = sharp corners, 1 = round (Square, Polygon and Organic)
	private PropertyFloat propertyRoundness = new PropertyFloat(0.7f, new Translatable("property.buildguide.roundness"), () -> onUnitChanged(this.propertyRoundness), unitStep);
	// Degrees around the vertical axis. Own label key: "property.buildguide.rotation" is the 90-degree
	// rotation enum of Polygon, Parabola and Polygonal pyramid, whose label must not change
	private PropertyFloat propertyRotation = new PropertyFloat(0.0f, new Translatable("property.buildguide.islandrotation"), () -> update());
	// Organic only: edge noise as a fraction of the radius, and about how many bumps around the edge
	private PropertyFloat propertyEdgeAmplitude = new PropertyFloat(0.0f, new Translatable("property.buildguide.edgeamplitude"), () -> onUnitChanged(this.propertyEdgeAmplitude), unitStep);
	private PropertyFloat propertyEdgeScale = new PropertyFloat(6.0f, new Translatable("property.buildguide.edgescale"), () -> update());
	private PropertyRangeInt propertyWall = new PropertyRangeInt(2, new Translatable("property.buildguide.wall"), () -> update(), IslandGeometry.minWall, IslandGeometry.maxWall);
	private PropertyRangeInt propertyDepth = new PropertyRangeInt(24, new Translatable("property.buildguide.depth"), () -> update(), 0, IslandGeometry.maxDepth);
	private PropertyEnum<Profile> propertyProfile = new PropertyEnum<Profile>(Profile.BOWL, new Translatable("property.buildguide.profile"), () -> update(), profileNames);
	// 0 = rounded underside, 1 = pointed
	private PropertyFloat propertySharpness = new PropertyFloat(0.0f, new Translatable("property.buildguide.sharpness"), () -> onUnitChanged(this.propertySharpness), unitStep);
	// Noise on the depth of each column (0 = smooth)
	private PropertyFloat propertyRoughness = new PropertyFloat(0.0f, new Translatable("property.buildguide.roughness"), () -> onUnitChanged(this.propertyRoughness), unitStep);
	private PropertyInt propertySeed = new PropertyInt(1, new Translatable("property.buildguide.seed"), () -> update());
	// PropertyRunnable renders as a button; buttons persist as a placeholder like Validate
	private PropertyRunnable propertyRandomize = new PropertyRunnable(() -> randomize(), new Translatable("property.buildguide.newseed"));
	// How far Randomize moves the Base (plan) and Body controls, in percent of each control's range
	private PropertyRangeInt propertyBasePercent = new PropertyRangeInt(15, new Translatable("property.buildguide.basepercent"), null, 0, 100, percentStep);
	private PropertyRangeInt propertyBodyPercent = new PropertyRangeInt(15, new Translatable("property.buildguide.bodypercent"), null, 0, 100, percentStep);
	private PropertyRunnable propertyNaturalize = new PropertyRunnable(() -> naturalize(), new Translatable("property.buildguide.naturalize"));
	private PropertyRunnable propertyUndo = new PropertyRunnable(() -> undo(), new Translatable("property.buildguide.undo"));
	// Spikes (block B), after the 19 above: cones under the body. Count 0 = none (the body exactly as without)
	private PropertyRangeInt propertySpikes = new PropertyRangeInt(0, new Translatable("property.buildguide.spikes"), () -> onSpikesChanged(), 0, IslandGeometry.maxSpikes);
	private PropertyEnum<SpikeMode> propertySpikeMode = new PropertyEnum<SpikeMode>(SpikeMode.RANDOM, new Translatable("property.buildguide.spikemode"), () -> onSpikesChanged(), spikeModeNames);
	private PropertyRangeInt propertySpikeLength = new PropertyRangeInt(12, new Translatable("property.buildguide.spikelength"), () -> update(), IslandGeometry.minSpikeLength, IslandGeometry.maxSpikeLength);
	private PropertyRangeInt propertySpikeBase = new PropertyRangeInt(3, new Translatable("property.buildguide.spikebase"), () -> update(), IslandGeometry.minSpikeBase, IslandGeometry.maxSpikeBase);
	private PropertyRangeInt propertyLengthVar = new PropertyRangeInt(0, new Translatable("property.buildguide.lengthvar"), () -> update(), 0, 100, percentStep);
	private PropertyRangeInt propertySpread = new PropertyRangeInt(50, new Translatable("property.buildguide.spread"), () -> update(), 0, 100, percentStep);
	// Spikes 2, phase 1 (after the 25 above): Fill only, how far each spike may leave the spiral
	private PropertyRangeInt propertyJitter = new PropertyRangeInt(20, new Translatable("property.buildguide.jitter"), () -> update(), 0, 100, percentStep);

	// Panel order per section (persistence order is the `properties` list)
	private Property<?>[] baseRows = {propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRoundness, propertyRotation, propertyEdgeAmplitude, propertyEdgeScale};
	private Control[] baseControls = {null, Control.WIDTH_X, Control.WIDTH_Z, Control.SIDES, Control.CORNER_ROUND, Control.ROTATION, Control.WOBBLE, Control.WOBBLE_SIZE};
	private Property<?>[] otherRows = {propertyWall, propertyDepth, propertyProfile, propertySharpness, propertyRoughness, propertyBasePercent, propertyBodyPercent, propertyRandomize, propertyNaturalize, propertyUndo, propertySeed};
	// Spikes section: Count alone while it is 0, then the five others
	private Property<?>[] spikeRows = {propertySpikeMode, propertySpikeLength, propertySpikeBase, propertyLengthVar, propertySpread, propertyJitter};

	// Randomize / Naturalize source; one-step Undo, in memory only
	Random random = new Random();
	private IslandControls.Values undoValues = null;

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
		properties.add(propertyRandomize);
		properties.add(propertyBasePercent);
		properties.add(propertyBodyPercent);
		properties.add(propertyNaturalize);
		properties.add(propertyUndo);
		properties.add(propertySpikes);
		properties.add(propertySpikeMode);
		properties.add(propertySpikeLength);
		properties.add(propertySpikeBase);
		properties.add(propertyLengthVar);
		properties.add(propertySpread);
		properties.add(propertyJitter);

		int sectionBase = declareSection(new Translatable("property.buildguide.section.base"));
		int sectionBody = declareSection(new Translatable("property.buildguide.section.body"));
		int sectionSpikes = declareSection(new Translatable("property.buildguide.section.spikes"));
		int sectionRandom = declareSection(new Translatable("property.buildguide.section.seed"));
		assignSection(sectionBase, baseRows);
		assignSection(sectionBody, propertyWall, propertyDepth, propertyProfile, propertySharpness, propertyRoughness);
		assignSection(sectionSpikes, propertySpikes);
		assignSection(sectionSpikes, spikeRows);
		assignSection(sectionRandom, propertyBasePercent, propertyBodyPercent, propertyRandomize, propertyNaturalize, propertyUndo, propertySeed);
		// Reset on the Random section must not throw away an island one likes
		protectFromReset(propertySeed);
	}

	// Rows of the open section: Base shows only the controls the Outline uses; the others in panel order
	@Override
	public void onSelectedInGUI() {
		int row = 0;
		for(int i = 0;i < baseRows.length;++i) {
			Property<?> p = baseRows[i];
			if(isShown(p) && (baseControls[i] == null || IslandControls.applies(propertyOutline.value, baseControls[i]))) row = placeRow(row, p);
			else hideRow(p);
		}
		for(Property<?> p: otherRows) {
			if(isShown(p)) row = placeRow(row, p);
			else hideRow(p);
		}
		if(isShown(propertySpikes)) row = placeRow(row, propertySpikes);
		else hideRow(propertySpikes);
		for(Property<?> p: spikeRows) {
			if(isShown(p) && propertySpikes.value > 0 && (p != propertyJitter || propertySpikeMode.value == SpikeMode.FILL)) row = placeRow(row, p);
			else hideRow(p);
		}
	}

	private void onSpikesChanged() {
		onSelectedInGUI(); // the spike controls show only with spikes, Jitter only in Fill
		update();
	}

	private void onOutlineChanged() {
		onSelectedInGUI(); // show / hide the plan controls
		update();
	}

	// Corner round, Wobble, Sharpness and Roughness are 0..1: the -/+ buttons move by unitStep and the
	// value is held in range here (no float property type has a maximum)
	private void onUnitChanged(PropertyFloat p) {
		if(!(p.value >= 0.0f)) p.setValue(0.0f);
		else if(p.value > 1.0f) p.setValue(1.0f);
		update();
	}

	IslandControls.Values values() {
		IslandControls.Values v = new IslandControls.Values();
		v.outline = propertyOutline.value;
		v.profile = propertyProfile.value;
		v.widthX = propertyWidthX.value;
		v.widthZ = propertyWidthZ.value;
		v.sides = propertySides.value;
		v.depth = propertyDepth.value;
		v.seed = propertySeed.value;
		v.cornerRound = propertyRoundness.value;
		v.rotation = propertyRotation.value;
		v.wobble = propertyEdgeAmplitude.value;
		v.wobbleSize = propertyEdgeScale.value;
		v.sharpness = propertySharpness.value;
		v.roughness = propertyRoughness.value;
		v.spikes = propertySpikes.value;
		v.spikeMode = propertySpikeMode.value;
		v.spikeLength = propertySpikeLength.value;
		v.spikeBase = propertySpikeBase.value;
		v.lengthVar = propertyLengthVar.value;
		v.spread = propertySpread.value;
		v.jitter = propertyJitter.value;
		return v;
	}

	// Writes every value (setValue runs no onPress), lays the panel out again, then regenerates once
	private void apply(IslandControls.Values v) {
		propertyOutline.setValue(v.outline);
		propertyProfile.setValue(v.profile);
		propertyWidthX.setValue(v.widthX);
		propertyWidthZ.setValue(v.widthZ);
		propertySides.setValue(v.sides);
		propertyDepth.setValue(v.depth);
		propertySeed.setValue(v.seed);
		propertyRoundness.setValue(v.cornerRound);
		propertyRotation.setValue(v.rotation);
		propertyEdgeAmplitude.setValue(v.wobble);
		propertyEdgeScale.setValue(v.wobbleSize);
		propertySharpness.setValue(v.sharpness);
		propertyRoughness.setValue(v.roughness);
		propertySpikes.setValue(v.spikes);
		propertySpikeMode.setValue(v.spikeMode);
		propertySpikeLength.setValue(v.spikeLength);
		propertySpikeBase.setValue(v.spikeBase);
		propertyLengthVar.setValue(v.lengthVar);
		propertySpread.setValue(v.spread);
		propertyJitter.setValue(v.jitter);
		onSelectedInGUI();
		update();
	}

	void randomize() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.randomize(before, propertyBasePercent.value, propertyBodyPercent.value, random);
		undoValues = before;
		apply(next);
	}

	void naturalize() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.naturalize(before, random);
		undoValues = before;
		apply(next);
	}

	// One step back from the last Randomize or Naturalize; a second Undo does nothing
	void undo() {
		if(undoValues == null) return;
		IslandControls.Values v = undoValues;
		undoValues = null;
		apply(v);
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
		p.spikes = propertySpikes.value;
		p.spikeMode = propertySpikeMode.value;
		p.spikeLength = propertySpikeLength.value;
		p.spikeBase = propertySpikeBase.value;
		p.lengthVar = propertyLengthVar.value;
		p.spread = propertySpread.value;
		p.jitter = propertyJitter.value;
		return p;
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		setOriginOffset(0, 0, 0);
		IslandGeometry.enumerate(params(), (x, y, z) -> addShapeCube(buffer, x, y, z));
	}
}
