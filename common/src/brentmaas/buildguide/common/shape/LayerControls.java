package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.List;

import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBlockId;
import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyCompactRangeInt;
import brentmaas.buildguide.common.property.PropertyLayerChips;
import brentmaas.buildguide.common.property.PropertyRangeInt;
import brentmaas.buildguide.common.property.PropertyRowLabel;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;

/**
 * The layer rows the Island and the Terrain share, in their Layers tab, as the Oficina prototype has them
 * (2026-10-28; 9 rows, what the tabs leave at the minimum height):
 *
 *   Layers [x]
 *   [1] [2] [3] [4] [+]              (the layers in use, the chosen one lit; + adds one; not saved)
 *   Block n         [id] Hand
 *   Blocks thick / % from ...  - n + ("Fills the rest" for the rest layer)
 *   Spots %         [patches]
 *   Spot size       [size]
 *   [Make this the rest] [Remove a layer]
 *   Edge blend      - blocks +
 *   [Naturalise layers]
 *
 * The count and the rest are saved values without rows of their own: the chips, + and the two buttons set them.
 *
 * plus, for the Island, "Spike layer" in its Spike shape section. The Layers box and blocks 1 to 4 are the
 * shape's own (saved where they always were); the rest is saved after the shape's last value, in the order of
 * persisted(). Only primitives and common: no net.minecraft.
 */
final class LayerControls {
	private final Shape shape;
	private final ShapeLayers.Axis axis;
	final PropertyBoolean enabled;
	final PropertyBlockId[] blocks = new PropertyBlockId[ShapeLayers.COUNT];
	final PropertyCompactRangeInt count, rest;
	final PropertyRangeInt mix, edit, spikeLayer;
	final PropertyRangeInt[] thickness = new PropertyRangeInt[ShapeLayers.COUNT];
	final PropertyCompactRangeInt[] patches = new PropertyCompactRangeInt[ShapeLayers.COUNT], size = new PropertyCompactRangeInt[ShapeLayers.COUNT];
	private final PropertyRowLabel countLabel = new PropertyRowLabel(new Translatable("property.buildguide.layercount"));
	private final PropertyRowLabel restLabel = new PropertyRowLabel(new Translatable("property.buildguide.layer.rest"));
	private final PropertyRowLabel patchesLabel = new PropertyRowLabel(new Translatable("property.buildguide.patches"));
	private final PropertyRowLabel sizeLabel = new PropertyRowLabel(new Translatable("property.buildguide.patchsize"));
	private final PropertyLayerChips chips = new PropertyLayerChips(() -> countValue(), () -> edited(), layer -> pickLayer(layer), () -> addLayer());
	private final PropertyRunnable makeRest = new PropertyRunnable(() -> makeRest(), new Translatable("property.buildguide.layer.makerest"), 0, Property.rowWidth / 2 - 1);
	private final PropertyRunnable removeLayer = new PropertyRunnable(() -> removeLayer(), new Translatable("property.buildguide.layer.remove"), Property.rowWidth / 2 + 1, Property.rowWidth / 2 - 1);
	private final PropertyRunnable naturalise = new PropertyRunnable(() -> naturalise(new java.util.Random()), new Translatable("property.buildguide.layer.naturalise"));

	/**
	 * @param blocks1to4 the shape's blocks of layers 1 to 4 (block 5 is made here)
	 * @param thicknessDefaults per layer: blocks above the rest (Depth), percent otherwise
	 * @param withSpikes whether the shape has spikes (the Spike layer row)
	 */
	LayerControls(Shape shape, ShapeLayers.Axis axis, PropertyBoolean enabled, PropertyBlockId[] blocks1to4, int countDefault, int restDefault, int mixDefault, int[] thicknessDefaults, int[] patchDefaults, boolean withSpikes) {
		this.shape = shape;
		this.axis = axis;
		this.enabled = enabled;
		for(int i = 0;i < 4;++i) blocks[i] = blocks1to4[i];
		blocks[4] = new PropertyBlockId("", new Translatable("property.buildguide.block5"), () -> shape.update());
		count = new PropertyCompactRangeInt(countDefault, new Translatable("property.buildguide.layercount"), () -> onCountChanged(), ShapeLayers.minCount, ShapeLayers.COUNT, 1, 1);
		rest = new PropertyCompactRangeInt(restDefault, new Translatable("property.buildguide.restlayer"), () -> onRestChanged(), 1, ShapeLayers.COUNT, 1, 2);
		mix = new PropertyRangeInt(mixDefault, new Translatable("property.buildguide.mix"), () -> shape.update(), 0, ShapeLayers.maxMix);
		// The layer the rows below edit, with its colour beside the label
		edit = new PropertyRangeInt(1, new Translatable("property.buildguide.editlayer"), () -> onEditChanged(), 1, ShapeLayers.COUNT) {
			@Override
			public void render(BaseScreen screen) {
				super.render(screen);
				if(visible) {
					screen.fillRect(x + 50, y + 4, x + 60, y + 14, 0xFF000000);
					screen.fillRect(x + 51, y + 5, x + 59, y + 13, 0xFF000000 | ShapeLayers.colour(value));
				}
			}
		};
		for(int i = 0;i < ShapeLayers.COUNT;++i) {
			thickness[i] = new PropertyRangeInt(thicknessDefaults[i], new Translatable("property.buildguide.layer.blocks"), () -> shape.update(), 0, ShapeLayers.maxThickness);
			patches[i] = new PropertyCompactRangeInt(patchDefaults[i], new Translatable("property.buildguide.patches"), () -> shape.update(), 0, ShapeLayers.maxPatches, ShapeLayers.patchStep, 1);
			size[i] = new PropertyCompactRangeInt(3, new Translatable("property.buildguide.patchsize"), () -> shape.update(), ShapeLayers.minSize, ShapeLayers.maxSize, 1, 2);
		}
		spikeLayer = withSpikes ? new PropertyRangeInt(countDefault, new Translatable("property.buildguide.spikelayer"), () -> shape.update(), 1, ShapeLayers.COUNT) : null;
	}

	// Saved after the shape's last value, in this order (new ones only ever at the end)
	List<Property<?>> persisted() {
		List<Property<?>> out = new ArrayList<Property<?>>();
		out.add(count);
		out.add(rest);
		out.add(mix);
		out.add(blocks[4]);
		for(Property<?> p: thickness) out.add(p);
		for(Property<?> p: patches) out.add(p);
		for(Property<?> p: size) out.add(p);
		if(spikeLayer != null) out.add(spikeLayer);
		return out;
	}

	// The rows into their sections; the labels and the edited layer are panel only
	void assign(int section, int spikeSection) {
		shape.addGuiOnly(countLabel);
		shape.addGuiOnly(restLabel);
		shape.addGuiOnly(patchesLabel);
		shape.addGuiOnly(sizeLabel);
		shape.addGuiOnly(edit);
		shape.addGuiOnly(chips);
		shape.addGuiOnly(makeRest);
		shape.addGuiOnly(removeLayer);
		shape.addGuiOnly(naturalise);
		shape.assignSection(section, enabled, countLabel, count, rest, mix, edit, chips, restLabel, patchesLabel, sizeLabel, makeRest, removeLayer, naturalise);
		shape.assignSection(section, blocks);
		shape.assignSection(section, thickness);
		shape.assignSection(section, patches);
		shape.assignSection(section, size);
		if(spikeLayer != null) shape.assignSection(spikeSection, spikeLayer);
	}

	int countValue() {
		return Math.max(ShapeLayers.minCount, Math.min(ShapeLayers.COUNT, count.value));
	}

	private int edited() {
		return Math.max(1, Math.min(countValue(), edit.value));
	}

	/** The Layers section's rows; returns the next row. */
	int place(int row) {
		if(shape.isShown(enabled)) row = shape.placeRow(row, enabled);
		else shape.hideRow(enabled);
		boolean on = enabled.value && shape.isShown(countLabel);
		int e = edited();
		// The count, the rest and the edited layer are set by the chips and the buttons
		shape.hideRow(countLabel, count, rest, edit);
		if(on) row = shape.placeRow(row, chips);
		else shape.hideRow(chips);
		for(int i = 0;i < ShapeLayers.COUNT;++i) {
			if(on && i == e - 1) row = shape.placeRow(row, blocks[i]);
			else shape.hideRow(blocks[i]);
		}
		boolean restEdited = e == Math.min(countValue(), rest.value);
		for(int i = 0;i < ShapeLayers.COUNT;++i) {
			if(on && !restEdited && i == e - 1) {
				thickness[i].setName(new Translatable(unitKey(e)));
				row = shape.placeRow(row, thickness[i]);
			}else {
				shape.hideRow(thickness[i]);
			}
		}
		if(on && restEdited) row = shape.placeRow(row, restLabel);
		else shape.hideRow(restLabel);
		if(on) {
			row = shape.placeRow(row, patchesLabel, patches[e - 1]);
			row = shape.placeRow(row, sizeLabel, size[e - 1]);
			row = shape.placeRow(row, makeRest, removeLayer);
			row = shape.placeRow(row, mix);
			row = shape.placeRow(row, naturalise);
		}else {
			shape.hideRow(patchesLabel, sizeLabel, makeRest, removeLayer, mix, naturalise);
		}
		for(int i = 0;i < ShapeLayers.COUNT;++i) {
			if(!on || i != e - 1) shape.hideRow(patches[i], size[i]);
		}
		return row;
	}

	// The thickness row's label: blocks or a percentage, and from where
	private String unitKey(int layer) {
		boolean above = layer < Math.min(countValue(), rest.value);
		if(axis == ShapeLayers.Axis.ELEVATION) return above ? "property.buildguide.layer.top" : "property.buildguide.layer.base";
		return above ? "property.buildguide.layer.blocks" : "property.buildguide.layer.bottom";
	}

	/** The Spike layer row (Island), shown with spikes and Layers on; returns the next row. */
	int placeSpikeLayer(int row, boolean spikes) {
		if(spikeLayer == null) return row;
		if(spikes && enabled.value && shape.isShown(spikeLayer)) return shape.placeRow(row, spikeLayer);
		shape.hideRow(spikeLayer);
		return row;
	}

	// Fewer layers: the rest, the edited layer and the spike layer stay among them (shown in their fields)
	private void onCountChanged() {
		int c = countValue();
		if(rest.value > c) rest.setValue(c);
		if(edit.value > c) edit.setValue(c);
		if(spikeLayer != null && spikeLayer.value > c) spikeLayer.setValue(c);
		shape.onSelectedInGUI();
		shape.update();
	}

	private void onRestChanged() {
		if(rest.value > countValue()) rest.setValue(countValue());
		shape.onSelectedInGUI(); // the thickness row's unit
		shape.update();
	}

	// A chip: that layer is the one the rows below edit
	private void pickLayer(int layer) {
		edit.setValue(layer);
		onEditChanged();
	}

	// + : one more layer (at the bottom), chosen at once
	private void addLayer() {
		if(countValue() >= ShapeLayers.COUNT) return;
		count.setValue(countValue() + 1);
		edit.setValue(countValue());
		onCountChanged();
	}

	// Remove a layer: the last one goes (its values stay saved for when it comes back)
	private void removeLayer() {
		if(countValue() <= ShapeLayers.minCount) return;
		count.setValue(countValue() - 1);
		onCountChanged();
	}

	// The chosen layer becomes the one that fills what the others leave
	private void makeRest() {
		rest.setValue(edited());
		onRestChanged();
	}

	/**
	 * Naturalise (Raphael's definition, 2026-10-28): the chosen blocks laid out the way chunk generation does it,
	 * with controlled randomness, so the layers look like part of the world. A thin top, a few blocks under it,
	 * the edges blended, and spots of each lower layer reaching into the one above; the blocks stay as chosen.
	 */
	void naturalise(java.util.Random random) {
		int c = countValue(), r = Math.min(c, rest.value);
		mix.setValue(1 + random.nextInt(3));
		for(int i = 0;i < c;++i) {
			int maxSpots = Math.min(20, ShapeLayers.maxPatches) / ShapeLayers.patchStep;
			patches[i].setValue(i == 0 ? 0 : ShapeLayers.patchStep * random.nextInt(maxSpots + 1));
			size[i].setValue(Math.max(ShapeLayers.minSize, Math.min(ShapeLayers.maxSize, 2 + random.nextInt(4))));
			if(axis == ShapeLayers.Axis.DEPTH && i < r - 1) thickness[i].setValue(i == 0 ? 1 : 2 + random.nextInt(3));
		}
		shape.onSelectedInGUI();
		shape.update();
	}

	// Only the panel changes: the edited layer is not part of the shape
	private void onEditChanged() {
		if(edit.value > countValue()) edit.setValue(countValue());
		shape.onSelectedInGUI();
	}

	ShapeLayers.Spec spec(long seed) {
		int[] t = new int[ShapeLayers.COUNT], p = new int[ShapeLayers.COUNT], s = new int[ShapeLayers.COUNT];
		for(int i = 0;i < ShapeLayers.COUNT;++i) {
			t[i] = thickness[i].value;
			p[i] = patches[i].value;
			s[i] = size[i].value;
		}
		return new ShapeLayers.Spec(axis, countValue(), rest.value, mix.value, spikeLayer == null ? countValue() : spikeLayer.value, t, p, s, seed);
	}

	// The block ids of the layers in use ("" = any solid block), for the scan
	String[] palette() {
		String[] out = new String[ShapeLayers.COUNT];
		for(int i = 0;i < ShapeLayers.COUNT;++i) out[i] = i < countValue() ? blocks[i].value : "";
		return out;
	}
}
