package brentmaas.buildguide.common.shape;

import java.util.Random;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.TerrainControls.Control;
import brentmaas.buildguide.common.shape.TerrainGeometry.Outline;
import brentmaas.buildguide.common.shape.TerrainGeometry.Type;

/**
 * Terrain: a seeded relief skin over a plan, never solid (1 to 3 blocks thick, no floor, no wall at the
 * edge). The plan uses the same four Outlines and Base rows as the Island, in the Terrain's own classes:
 * geometry in TerrainGeometry, the Randomize / Natural recipes, the height cap and which control applies to
 * which Outline in TerrainControls; this class holds the properties and the panel.
 */
public class ShapeTerrain extends Shape {
	static final float unitStep = 0.05f;
	static final int percentStep = 5;

	private static final String[] outlineNames = {"Circle", "Square", "Polygon", "Organic"};
	private static final String[] typeNames = {"Hills", "Mountains", "Dunes"};
	private static final String[] focusPartNames = {"All", "Cap", "Body", "Spikes", "Hidden"};

	// Persistence order: outline, width X, width Z, sides, corner round, rotation, wobble, wobble size, then
	// type, height, scale, roughness, edge fade %, skin, then base %, relief %, randomize, natural, undo, seed,
	// then (build focus) focus part, focus layer. New properties go at the end
	private PropertyEnum<Outline> propertyOutline = new PropertyEnum<Outline>(Outline.CIRCLE, new Translatable("property.buildguide.outline"), () -> onOutlineChanged(), outlineNames);
	private PropertyRangeInt propertyWidthX = new PropertyRangeInt(41, new Translatable("property.buildguide.widthx"), () -> update(), TerrainGeometry.minWidth, TerrainGeometry.maxWidth);
	private PropertyRangeInt propertyWidthZ = new PropertyRangeInt(41, new Translatable("property.buildguide.widthz"), () -> update(), TerrainGeometry.minWidth, TerrainGeometry.maxWidth);
	private PropertyRangeInt propertySides = new PropertyRangeInt(6, new Translatable("property.buildguide.sides"), () -> update(), TerrainGeometry.minSides, TerrainGeometry.maxSides);
	// 0 = sharp corners, 1 = round (Square, Polygon and Organic)
	private PropertyFloat propertyRoundness = new PropertyFloat(0.7f, new Translatable("property.buildguide.roundness"), () -> onUnitChanged(this.propertyRoundness), unitStep);
	// Degrees around the vertical axis; also the Dunes' direction
	private PropertyFloat propertyRotation = new PropertyFloat(0.0f, new Translatable("property.buildguide.islandrotation"), () -> update());
	// Organic only: edge noise as a fraction of the radius, and about how many bumps around the edge
	private PropertyFloat propertyEdgeAmplitude = new PropertyFloat(0.0f, new Translatable("property.buildguide.edgeamplitude"), () -> onUnitChanged(this.propertyEdgeAmplitude), unitStep);
	private PropertyFloat propertyEdgeScale = new PropertyFloat(6.0f, new Translatable("property.buildguide.edgescale"), () -> update());
	// Relief
	private PropertyEnum<Type> propertyType = new PropertyEnum<Type>(Type.HILLS, new Translatable("property.buildguide.terraintype"), () -> update(), typeNames);
	// Height is held under min(60, 3 x Scale) (TerrainGeometry.maxHeightFor): what the field shows is what is built
	private PropertyRangeInt propertyHeight = new PropertyRangeInt(12, new Translatable("property.buildguide.terrainheight"), () -> onReliefSizeChanged(), TerrainGeometry.minHeight, TerrainGeometry.maxHeight);
	private PropertyRangeInt propertyScale = new PropertyRangeInt(20, new Translatable("property.buildguide.terrainscale"), () -> onReliefSizeChanged(), TerrainGeometry.minScale, TerrainGeometry.maxScale);
	private PropertyFloat propertyRoughness = new PropertyFloat(0.0f, new Translatable("property.buildguide.roughness"), () -> onUnitChanged(this.propertyRoughness), unitStep);
	private PropertyRangeInt propertyEdgeFade = new PropertyRangeInt(0, new Translatable("property.buildguide.edgefade"), () -> update(), 0, 100, percentStep);
	private PropertyRangeInt propertySkin = new PropertyRangeInt(2, new Translatable("property.buildguide.skin"), () -> update(), TerrainGeometry.minSkin, TerrainGeometry.maxSkin);
	// Random: how far Randomize moves the Base (plan) and Relief controls, in percent of each control's range
	private PropertyRangeInt propertyBasePercent = new PropertyRangeInt(15, new Translatable("property.buildguide.basepercent"), null, 0, 100, percentStep);
	private PropertyRangeInt propertyReliefPercent = new PropertyRangeInt(15, new Translatable("property.buildguide.reliefpercent"), null, 0, 100, percentStep);
	// PropertyRunnable renders as a button; buttons persist as a placeholder like Validate
	private PropertyRunnable propertyRandomize = new PropertyRunnable(() -> randomize(), new Translatable("property.buildguide.newseed"));
	private PropertyRunnable propertyNatural = new PropertyRunnable(() -> natural(), new Translatable("property.buildguide.natural"));
	private PropertyRunnable propertyUndo = new PropertyRunnable(() -> undo(), new Translatable("property.buildguide.undo"));
	private PropertyInt propertySeed = new PropertyInt(1, new Translatable("property.buildguide.seed"), () -> update());
	// Build focus (after Seed): one part only (All or Hidden) and a layer; set by the preview's buttons, so not
	// panel rows; saved
	private PropertyEnum<FocusFilter.Part> propertyFocusPart = new PropertyEnum<FocusFilter.Part>(FocusFilter.Part.ALL, new Translatable("screen.buildguide.focus.part"), null, focusPartNames);
	private PropertyInt propertyFocusLayer = new PropertyInt(FocusFilter.LAYER_ALL, new Translatable("screen.buildguide.focus.layer"), null);

	// Panel order per section (persistence order is the `properties` list)
	private Property<?>[] baseRows = {propertyOutline, propertyWidthX, propertyWidthZ, propertySides, propertyRoundness, propertyRotation, propertyEdgeAmplitude, propertyEdgeScale};
	private Control[] baseControls = {null, Control.WIDTH_X, Control.WIDTH_Z, Control.SIDES, Control.CORNER_ROUND, Control.ROTATION, Control.WOBBLE, Control.WOBBLE_SIZE};

	// Randomize source; one-step Undo, in memory only
	Random random = new Random();
	private TerrainControls.Values undoValues = null;

	public ShapeTerrain() {
		super();

		properties.add(propertyOutline);
		properties.add(propertyWidthX);
		properties.add(propertyWidthZ);
		properties.add(propertySides);
		properties.add(propertyRoundness);
		properties.add(propertyRotation);
		properties.add(propertyEdgeAmplitude);
		properties.add(propertyEdgeScale);
		properties.add(propertyType);
		properties.add(propertyHeight);
		properties.add(propertyScale);
		properties.add(propertyRoughness);
		properties.add(propertyEdgeFade);
		properties.add(propertySkin);
		properties.add(propertyBasePercent);
		properties.add(propertyReliefPercent);
		properties.add(propertyRandomize);
		properties.add(propertyNatural);
		properties.add(propertyUndo);
		properties.add(propertySeed);
		properties.add(propertyFocusPart);
		properties.add(propertyFocusLayer);
		hideFromGui(propertyFocusPart);
		hideFromGui(propertyFocusLayer);

		int sectionBase = declareSection(new Translatable("property.buildguide.section.base"));
		int sectionRelief = declareSection(new Translatable("property.buildguide.section.relief"));
		int sectionRandom = declareSection(new Translatable("property.buildguide.section.seed"));
		assignSection(sectionBase, baseRows);
		assignSection(sectionRelief, propertyType, propertyHeight, propertyScale, propertyRoughness, propertyEdgeFade, propertySkin);
		assignSection(sectionRandom, propertyBasePercent, propertyReliefPercent, propertyRandomize, propertyNatural, propertyUndo, propertySeed);
		// Reset on the Random section must not throw away a terrain one likes
		protectFromReset(propertySeed);
	}

	// Rows of the open section: Base shows only the controls the Outline uses; the others in list order
	@Override
	public void onSelectedInGUI() {
		int row = 0;
		for(int i = 0;i < baseRows.length;++i) {
			Property<?> p = baseRows[i];
			if(isShown(p) && (baseControls[i] == null || TerrainControls.applies(propertyOutline.value, baseControls[i]))) row = placeRow(row, p);
			else hideRow(p);
		}
		for(Property<?> p: properties) {
			if(isBaseRow(p)) continue;
			if(isShown(p)) row = placeRow(row, p);
			else hideRow(p);
		}
	}

	private boolean isBaseRow(Property<?> p) {
		for(Property<?> b: baseRows) if(b == p) return true;
		return false;
	}

	// Height or Scale changed: the cap moves Height down to 3 x Scale (shown in its field), then one generation
	private void onReliefSizeChanged() {
		applyHeightCap();
		update();
	}

	// setValue shows the new Height in its field without running its action
	private void applyHeightCap() {
		int capped = TerrainControls.cappedHeight(propertyHeight.value, propertyScale.value);
		if(capped != propertyHeight.value) propertyHeight.setValue(capped);
	}

	// Values loaded (a save, a preset) above the cap are brought under it at once
	@Override
	public void restorePersistence(String persistenceData) {
		super.restorePersistence(persistenceData);
		applyHeightCap();
	}

	private void onOutlineChanged() {
		onSelectedInGUI(); // show / hide the plan controls
		update();
	}

	// Corner round, Wobble and Roughness are 0..1: the -/+ buttons move by unitStep and the value is held in range here
	private void onUnitChanged(PropertyFloat p) {
		if(!(p.value >= 0.0f)) p.setValue(0.0f);
		else if(p.value > 1.0f) p.setValue(1.0f);
		update();
	}

	TerrainGeometry.Params params() {
		TerrainGeometry.Params p = new TerrainGeometry.Params();
		p.outline = propertyOutline.value;
		p.widthX = propertyWidthX.value;
		p.widthZ = propertyWidthZ.value;
		p.sides = propertySides.value;
		p.roundness = propertyRoundness.value;
		p.rotationDeg = propertyRotation.value;
		p.edgeAmplitude = propertyEdgeAmplitude.value;
		p.edgeScale = propertyEdgeScale.value;
		p.type = propertyType.value;
		p.height = propertyHeight.value;
		p.scale = propertyScale.value;
		p.roughness = propertyRoughness.value;
		p.edgeFade = propertyEdgeFade.value;
		p.skin = propertySkin.value;
		p.seed = propertySeed.value;
		return p;
	}

	TerrainControls.Values values() {
		TerrainControls.Values v = new TerrainControls.Values();
		v.outline = propertyOutline.value;
		v.type = propertyType.value;
		v.widthX = propertyWidthX.value;
		v.widthZ = propertyWidthZ.value;
		v.sides = propertySides.value;
		v.height = propertyHeight.value;
		v.scale = propertyScale.value;
		v.edgeFade = propertyEdgeFade.value;
		v.skin = propertySkin.value;
		v.seed = propertySeed.value;
		v.cornerRound = propertyRoundness.value;
		v.rotation = propertyRotation.value;
		v.wobble = propertyEdgeAmplitude.value;
		v.wobbleSize = propertyEdgeScale.value;
		v.roughness = propertyRoughness.value;
		return v;
	}

	// Writes every value (setValue runs no onPress), holds the cap, lays the panel out again, then regenerates once
	private void apply(TerrainControls.Values v) {
		propertyOutline.setValue(v.outline);
		propertyType.setValue(v.type);
		propertyWidthX.setValue(v.widthX);
		propertyWidthZ.setValue(v.widthZ);
		propertySides.setValue(v.sides);
		propertyScale.setValue(v.scale);
		propertyHeight.setValue(v.height);
		propertyEdgeFade.setValue(v.edgeFade);
		propertySkin.setValue(v.skin);
		propertySeed.setValue(v.seed);
		propertyRoundness.setValue(v.cornerRound);
		propertyRotation.setValue(v.rotation);
		propertyEdgeAmplitude.setValue(v.wobble);
		propertyEdgeScale.setValue(v.wobbleSize);
		propertyRoughness.setValue(v.roughness);
		applyHeightCap();
		onSelectedInGUI();
		update();
	}

	void randomize() {
		TerrainControls.Values before = values();
		TerrainControls.Values next = TerrainControls.randomize(before, propertyBasePercent.value, propertyReliefPercent.value, random);
		undoValues = before;
		apply(next);
	}

	void natural() {
		TerrainControls.Values before = values();
		TerrainControls.Values next = TerrainControls.natural(before);
		undoValues = before;
		apply(next);
	}

	// One step back from the last Randomize or Natural (the seed too); a second Undo does nothing
	void undo() {
		if(undoValues == null) return;
		TerrainControls.Values v = undoValues;
		undoValues = null;
		apply(v);
	}

	// Build focus: one part (IFocusParts default), so Part is All or Hidden
	@Override
	public FocusFilter.Part getFocusPart() {
		return propertyFocusPart.value;
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

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		setOriginOffset(0, 0, 0);
		TerrainGeometry.enumerate(params(), (x, y, z) -> addShapeCubeIfNew(buffer, x, y, z));
	}
}
