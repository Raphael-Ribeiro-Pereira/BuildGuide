package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBlockId;
import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyCompactRangeInt;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.IslandControls.Control;
import brentmaas.buildguide.common.shape.IslandGeometry.Outline;
import brentmaas.buildguide.common.shape.IslandGeometry.Profile;
import brentmaas.buildguide.common.shape.IslandGeometry.BreakMode;
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
	static final float taperStep = 0.1f;

	private String[] outlineNames = {"Circle", "Square", "Polygon", "Organic"};
	private String[] profileNames = {"Bowl", "Cone", "Terraced"};
	private String[] spikeModeNames = {"Random", "Ring", "Fill"};
	private String[] breakNames = {"Attached", "Segmented"};
	private static final String[] focusPartNames = {"All", "Cap", "Body", "Spikes", "Hidden"};

	// Persistence order: outline, width X, width Z, sides, roundness, rotation, edge amplitude, edge
	// scale, wall, depth, profile, sharpness, roughness, seed, randomize (was New seed), then base %,
	// body %, naturalize, undo, then (block B) spikes, spike mode, spike length, spike base, length var %,
	// spread %, then (spikes 2, phase 1) jitter %, (phase 2) dripstone, taper, edge falloff %, spikes %,
	// naturalize spikes, (phase 3) break, pieces, gap, then (layers, 4C) layers, cut 1 %, cut 2 %, cut 3 %, then
	// (palette, 4D) block 1 to block 4, then (build focus) focus part, focus layer, then (layers, 2026-10-27)
	// layer count, rest layer, mix, block 5, thickness 1 to 5, patches 1 to 5, patch size 1 to 5, spike layer
	// (LayerControls.persisted; the three cuts stay saved but do nothing). New properties go at the end. Labels: roundness reads "Corner round",
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
	// Naturalise everything at once (2026-10-28): the body, the spikes and, with Layers on, the layers. Panel only
	private PropertyRunnable propertyNaturaliseAll = new PropertyRunnable(() -> naturaliseAll(), new Translatable("property.buildguide.naturaliseall"));
	// Spikes (block B), after the 19 above: cones under the body. Count 0 = none (the body exactly as without)
	private PropertyRangeInt propertySpikes = new PropertyRangeInt(0, new Translatable("property.buildguide.spikes"), () -> onSpikesChanged(), 0, IslandGeometry.maxSpikes);
	private PropertyEnum<SpikeMode> propertySpikeMode = new PropertyEnum<SpikeMode>(SpikeMode.RANDOM, new Translatable("property.buildguide.spikemode"), () -> onSpikesChanged(), spikeModeNames);
	private PropertyRangeInt propertySpikeLength = new PropertyRangeInt(12, new Translatable("property.buildguide.spikelength"), () -> update(), IslandGeometry.minSpikeLength, IslandGeometry.maxSpikeLength);
	private PropertyRangeInt propertySpikeBase = new PropertyRangeInt(3, new Translatable("property.buildguide.spikebase"), () -> update(), IslandGeometry.minSpikeBase, IslandGeometry.maxSpikeBase);
	private PropertyRangeInt propertyLengthVar = new PropertyRangeInt(0, new Translatable("property.buildguide.lengthvar"), () -> update(), 0, 100, percentStep);
	private PropertyRangeInt propertySpread = new PropertyRangeInt(50, new Translatable("property.buildguide.spread"), () -> update(), 0, 100, percentStep);
	// Spikes 2, phase 1 (after the 25 above): Fill only, how far each spike may leave the spiral
	private PropertyRangeInt propertyJitter = new PropertyRangeInt(20, new Translatable("property.buildguide.jitter"), () -> update(), 0, 100, percentStep);
	// Spikes 2, phase 2 (after Jitter): the spikes' shape, and the Spikes group of Randomize
	private PropertyBoolean propertyDripstone = new PropertyBoolean(false, new Translatable("property.buildguide.dripstone"), () -> update());
	// Radius profile Base x (1 - t)^Taper, 0.5 to 3 by 0.1 (held in range here, as the 0..1 floats)
	private PropertyFloat propertyTaper = new PropertyFloat(1.0f, new Translatable("property.buildguide.taper"), () -> onTaperChanged(), taperStep);
	private PropertyRangeInt propertyFalloff = new PropertyRangeInt(0, new Translatable("property.buildguide.falloff"), () -> update(), 0, 100, percentStep);
	private PropertyRangeInt propertySpikesPercent = new PropertyRangeInt(15, new Translatable("property.buildguide.spikespercent"), null, 0, 100, percentStep);
	private PropertyRunnable propertyNaturalizeSpikes = new PropertyRunnable(() -> naturalizeSpikes(), new Translatable("property.buildguide.naturalizespikes"));
	// Spikes 2, phase 3 (after Naturalize spikes): a spike breaks into pieces, the top one attached
	private PropertyEnum<BreakMode> propertyBreak = new PropertyEnum<BreakMode>(BreakMode.ATTACHED, new Translatable("property.buildguide.break"), () -> onSpikesChanged(), breakNames);
	private PropertyRangeInt propertyPieces = new PropertyRangeInt(2, new Translatable("property.buildguide.pieces"), () -> update(), IslandGeometry.minPieces, IslandGeometry.maxPieces);
	private PropertyRangeInt propertyGap = new PropertyRangeInt(2, new Translatable("property.buildguide.gap"), () -> update(), IslandGeometry.minGap, IslandGeometry.maxGap);
	// Layers (palette stage 4C, after Gap): the world and the preview coloured by layer (ShapeLayers: strata by
	// depth since 2026-10-27, LayerControls). Visual only: the blocks are the same with or without
	private PropertyBoolean propertyLayers = new PropertyBoolean(false, new Translatable("property.buildguide.layers"), () -> onLayersChanged());
	// The three cuts of before 2026-10-27: still saved where they were, out of the panel, read by nothing
	private PropertyCompactRangeInt propertyCut1 = new PropertyCompactRangeInt(25, new Translatable("property.buildguide.cut1"), null, 5, 95, 5, 0);
	private PropertyCompactRangeInt propertyCut2 = new PropertyCompactRangeInt(50, new Translatable("property.buildguide.cut2"), null, 5, 95, 5, 1);
	private PropertyCompactRangeInt propertyCut3 = new PropertyCompactRangeInt(75, new Translatable("property.buildguide.cut3"), null, 5, 95, 5, 2);
	// The layers the last generation coloured the world with (null: Layers off), for the preview
	private volatile ShapeLayers generatedLayers = null;
	// Palette per layer (4D, after Cut 3 %): the block each layer must be built with, by id; empty = any solid
	// block. Validation only (the geometry and the colours do not depend on it); a change regenerates, which
	// rescans with the new palette
	private PropertyBlockId propertyBlock1 = new PropertyBlockId("", new Translatable("property.buildguide.block1"), () -> update());
	private PropertyBlockId propertyBlock2 = new PropertyBlockId("", new Translatable("property.buildguide.block2"), () -> update());
	private PropertyBlockId propertyBlock3 = new PropertyBlockId("", new Translatable("property.buildguide.block3"), () -> update());
	private PropertyBlockId propertyBlock4 = new PropertyBlockId("", new Translatable("property.buildguide.block4"), () -> update());
	private PropertyBlockId[] blockRows = {propertyBlock1, propertyBlock2, propertyBlock3, propertyBlock4};
	// Strata of a piece of ground (2026-10-27): 4 layers, a 1-block top, 3 blocks under it, the rest, and the
	// bottom 25 % of each column, with patches of the bottom layer in the rest; layer 5 (when used) 10 %
	private final LayerControls layerControls = new LayerControls(this, ShapeLayers.Axis.DEPTH, propertyLayers, blockRows, 4, 3, 2, new int[] {1, 3, 0, 25, 10}, new int[] {0, 0, 0, 15, 0}, true);
	// The palette of the last generation (null: Layers off), for the scan: generation and palette stay together
	private volatile String[] generatedPalette = null;
	// Build focus (after Block 4): the part and the layer the world shows (FocusFilter). Not panel rows (the
	// preview's Part and Layer buttons set them), so hidden from the panel; saved, so a restart keeps the focus.
	// Islands saved before it load with All, All
	private PropertyEnum<FocusFilter.Part> propertyFocusPart = new PropertyEnum<FocusFilter.Part>(FocusFilter.Part.ALL, new Translatable("screen.buildguide.focus.part"), null, focusPartNames);
	private PropertyInt propertyFocusLayer = new PropertyInt(FocusFilter.LAYER_ALL, new Translatable("screen.buildguide.focus.layer"), null);
	// What the last generation's parts are computed from (IslandGeometry.Parts), set before its first block
	private volatile IslandGeometry.Parts generatedParts = null;

	// Panel order per section (persistence order is the `properties` list)
	private Property<?>[] baseRows = {propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRoundness, propertyRotation, propertyEdgeAmplitude, propertyEdgeScale};
	private Control[] baseControls = {null, Control.WIDTH_X, Control.WIDTH_Z, Control.SIDES, Control.CORNER_ROUND, Control.ROTATION, Control.WOBBLE, Control.WOBBLE_SIZE};
	private Property<?>[] otherRows = {propertyWall, propertyDepth, propertyProfile, propertySharpness, propertyRoughness, propertyBasePercent, propertyBodyPercent, propertySpikesPercent, propertyRandomize, propertyNaturaliseAll, propertyNaturalize, propertyNaturalizeSpikes, propertyUndo, propertySeed};
	// Spike shape section: shown only with spikes
	private Property<?>[] spikeShapeRows = {propertyDripstone, propertyTaper, propertyFalloff, propertyBreak, propertyPieces, propertyGap};
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
		properties.add(propertyDripstone);
		properties.add(propertyTaper);
		properties.add(propertyFalloff);
		properties.add(propertySpikesPercent);
		properties.add(propertyNaturalizeSpikes);
		properties.add(propertyBreak);
		properties.add(propertyPieces);
		properties.add(propertyGap);
		properties.add(propertyLayers);
		properties.add(propertyCut1);
		properties.add(propertyCut2);
		properties.add(propertyCut3);
		properties.add(propertyBlock1);
		properties.add(propertyBlock2);
		properties.add(propertyBlock3);
		properties.add(propertyBlock4);
		properties.add(propertyFocusPart);
		properties.add(propertyFocusLayer);
		properties.addAll(layerControls.persisted());
		hideFromGui(propertyFocusPart);
		hideFromGui(propertyFocusLayer);
		hideFromGui(propertyCut1);
		hideFromGui(propertyCut2);
		hideFromGui(propertyCut3);

		int sectionBase = declareSection(new Translatable("property.buildguide.section.base"));
		int sectionBody = declareSection(new Translatable("property.buildguide.section.body"));
		int sectionSpikes = declareSection(new Translatable("property.buildguide.section.spikes"));
		assignSection(sectionBase, baseRows);
		assignSection(sectionBody, propertyWall, propertyDepth, propertyProfile, propertySharpness, propertyRoughness);
		assignSection(sectionSpikes, propertySpikes);
		assignSection(sectionSpikes, spikeRows);
		// Only Base, Body and Spikes (Raphael, 2026-10-28): the spikes' shape is part of Spikes (the inspector
		// scrolls when they do not fit); Layers and Random are out for now
		assignSection(sectionSpikes, spikeShapeRows);
		// Layers left out of the Island for now: the box hidden and off (restorePersistence), its rows never shown
		layerControls.assign(sectionBody, sectionSpikes);
		hideFromGui(propertyLayers);
		// Randomize and Undo at the end of Base (the prototype's Randomize); the percentages and the Naturalize
		// buttons are left out for now (still saved)
		assignSection(sectionBase, propertyRandomize, propertyUndo);
		assignSection(sectionBody, propertyBasePercent, propertyBodyPercent, propertySpikesPercent, propertyNaturaliseAll, propertyNaturalize, propertyNaturalizeSpikes, propertySeed);
		hideFromGui(propertyBasePercent);
		hideFromGui(propertyBodyPercent);
		hideFromGui(propertySpikesPercent);
		hideFromGui(propertyNaturalize);
		hideFromGui(propertyNaturalizeSpikes);
		hideFromGui(propertyNaturaliseAll);
		// Random has 8 rows with Seed; at the minimum GUI height (270) the sections leave room for 7 (six of
		// them since Layers, with 11-px headers), so the Seed row is not shown (still saved, still in presets,
		// still drawn by Randomize and New seed)
		hideFromGui(propertySeed);
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
		for(Property<?> p: spikeShapeRows) {
			boolean segmentedOnly = p == propertyPieces || p == propertyGap;
			if(isShown(p) && propertySpikes.value > 0 && (!segmentedOnly || propertyBreak.value == BreakMode.SEGMENTED)) row = placeRow(row, p);
			else hideRow(p);
		}
		// The spikes' layer, last in Spike shape (7 rows at most with it)
		row = layerControls.placeSpikeLayer(row, propertySpikes.value > 0);
		// Layers: the box, then (on) count and rest, mix, the edited layer and its block, thickness, patches
		row = layerControls.place(row);
	}

	// Layers are out of the Island for now (2026-10-28): an island saved with them loads without. The tests of the
	// layers' generation and saving turn this on
	public static boolean layersInGame = false;

	@Override
	public void restorePersistence(String persistenceData) {
		super.restorePersistence(persistenceData);
		if(!layersInGame) propertyLayers.value = false;
	}

	private void onLayersChanged() {
		onSelectedInGUI(); // the layer rows show only with Layers on
		update();
	}

	@Override
	public boolean hasLayers() {
		return propertyLayers.value;
	}

	@Override
	public ShapeLayers getLayers() {
		return generatedLayers;
	}

	@Override
	public String[] getPalette() {
		String[] p = generatedPalette;
		return p == null ? null : p.clone();
	}

	// Build focus: Cap, Body and Spikes (IslandGeometry.Parts); before a first generation every block is Body
	@Override
	public boolean hasParts() {
		return true;
	}

	@Override
	public FocusFilter.Part partOf(int x, int y, int z) {
		IslandGeometry.Parts parts = generatedParts;
		return parts == null ? FocusFilter.Part.BODY : parts.partOf(x, y, z);
	}

	// A world buffer made again for the focus has the generation's colours: by layer with Layers on
	@Override
	protected void colourCube(IShapeBuffer buffer, int x, int y, int z) {
		ShapeLayers layers = generatedLayers;
		if(layers == null) return;
		int rgb = ShapeLayers.colour(layers.layerAt(x, y, z));
		buffer.setColour((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, (int) (255 * shapeSet.getShapeColourA()));
	}

	@Override
	public FocusFilter.Part getFocusPart() {
		return propertyFocusPart.value;
	}

	// A saved layer out of 0..5 counts as All
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

	private void onTaperChanged() {
		if(!(propertyTaper.value >= IslandGeometry.minTaper)) propertyTaper.setValue((float) IslandGeometry.minTaper);
		else if(propertyTaper.value > IslandGeometry.maxTaper) propertyTaper.setValue((float) IslandGeometry.maxTaper);
		update();
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
		v.falloff = propertyFalloff.value;
		v.taper = propertyTaper.value;
		v.dripstone = propertyDripstone.value;
		v.breakMode = propertyBreak.value;
		v.pieces = propertyPieces.value;
		v.gap = propertyGap.value;
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
		propertyFalloff.setValue(v.falloff);
		propertyTaper.setValue(v.taper);
		propertyDripstone.setValue(v.dripstone);
		propertyBreak.setValue(v.breakMode);
		propertyPieces.setValue(v.pieces);
		propertyGap.setValue(v.gap);
		onSelectedInGUI();
		update();
	}

	void randomize() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.randomize(before, propertyBasePercent.value, propertyBodyPercent.value, propertySpikesPercent.value, random);
		undoValues = before;
		apply(next);
	}

	void naturalize() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.naturalize(before, random);
		undoValues = before;
		apply(next);
	}

	void naturalizeSpikes() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.naturalizeSpikes(before);
		undoValues = before;
		apply(next);
	}

	// Body and spikes in one step (Undo goes back over both), then the layers
	void naturaliseAll() {
		IslandControls.Values before = values();
		IslandControls.Values next = IslandControls.naturalizeSpikes(IslandControls.naturalize(before, random));
		undoValues = before;
		if(propertyLayers.value) layerControls.naturalise(random);
		apply(next);
	}

	// One step back from the last Randomize, Naturalize or Naturalize spikes; a second Undo does nothing
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
		p.falloff = propertyFalloff.value;
		p.taper = propertyTaper.value;
		p.dripstone = propertyDripstone.value;
		p.breakMode = propertyBreak.value;
		p.pieces = propertyPieces.value;
		p.gap = propertyGap.value;
		return p;
	}

	// The layers' spec for the current values (the island's seed drives the mix and the patches), null with Layers off
	ShapeLayers.Spec layerSpec() {
		return propertyLayers.value ? layerControls.spec(propertySeed.value) : null;
	}

	// With Layers on, each cube takes its layer's colour (and the shape's alpha) before it is added: the
	// blocks and their order are exactly those of Layers off, only the vertex colours differ. The layers need
	// the generation's columns, so they are made when its Parts come, before the first block
	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		setOriginOffset(0, 0, 0);
		ShapeLayers.Spec spec = layerSpec();
		generatedLayers = null;
		generatedPalette = spec == null ? null : layerControls.palette();
		generatedParts = null;
		if(spec == null) {
			IslandGeometry.enumerate(params(), parts -> generatedParts = parts, (x, y, z) -> addShapeCube(buffer, x, y, z));
			return;
		}
		int alpha = (int) (255 * shapeSet.getShapeColourA());
		IslandGeometry.enumerate(params(), parts -> {
			generatedParts = parts;
			generatedLayers = parts.layers(spec);
		}, (x, y, z) -> {
			int rgb = ShapeLayers.colour(generatedLayers.layerAt(x, y, z));
			buffer.setColour((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
			addShapeCube(buffer, x, y, z);
		});
	}
}
