package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.FocusFilter;
import brentmaas.buildguide.common.shape.PreviewColours;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeLayers;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ShapeRegistry;

public class ShapeScreen extends BaseScreen{
	// Left panel (GUI redesign E5, plan §2.2): x 0..188, the accordion from y 42 (the shape dropdown
	// moved to the header in E6): one 11-px header per section (Origin first, then the shape's
	// sections), the open section's rows under its header. The right panel (x 192..480) is E6.
	// Headers were 12 px until the Island's sixth section (Layers, 4C): at 11, Origin and six sections
	// still leave room for 7 rows at the minimum height (77 + 7 x 18 = 203 of 206 px)
	public static final int panelWidth = 188, accordionTop = 42, headerHeight = 11;
	// Header row (E6), y 0..20: Enabled checkbox 2..19 (BaseScreen), set selector < N/Total > 22..78,
	// type dropdown 80..160, instance name 164..Save-4, Save (width - 64, 40 px), close X (BaseScreen)
	private static final int setPrevX = 22, setNextX = 64, arrowWidth = 14, typeX = 80, typeWidth = 80, nameX = 164, saveWidth = 40;
	public static final int basePropertiesX = 2;
	public static final int basePropertiesY = accordionTop + headerHeight;
	private static final int originRowHeight = Property.rowHeight, originRows = 4;

	private Translatable titleOrigin = new Translatable("screen.buildguide.origin");
	
	private IButton buttonSetPrevious = BuildGuide.widgetHandler.createButton(setPrevX, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable("<"), () -> switchShapeSet(-1));
	private IButton buttonSetNext = BuildGuide.widgetHandler.createButton(setNextX, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable(">"), () -> switchShapeSet(1));
	private IButton buttonSave;
	private ITextField textFieldName;

	private DropdownOverlayScreen dropdownOverlayShapeSelect = new DropdownOverlayScreen(this, typeX, 0, typeWidth, AbstractWidgetHandler.defaultSize, ShapeRegistry.getTranslatables(), BuildGuide.stateManager.getState().getCurrentShapeIndex(), (int selected) -> setShape(selected));
	// Origin section rows (compact like the properties, Enter applies a typed value): "Set origin"
	// (to the player), then X, Y, Z as label · - · field · +
	private IButton buttonOrigin = BuildGuide.widgetHandler.createButton(basePropertiesX, 0, Property.rowWidth, Property.rowHeight, new Translatable("screen.buildguide.setorigin"), () -> setOrigin());
	private IButton buttonOriginXDecrease = originStep(Property.controlX, "-", -1, 0, 0);
	private IButton buttonOriginXIncrease = originStep(Property.increaseX, "+", 1, 0, 0);
	private IButton buttonOriginYDecrease = originStep(Property.controlX, "-", 0, -1, 0);
	private IButton buttonOriginYIncrease = originStep(Property.increaseX, "+", 0, 1, 0);
	private IButton buttonOriginZDecrease = originStep(Property.controlX, "-", 0, 0, -1);
	private IButton buttonOriginZIncrease = originStep(Property.increaseX, "+", 0, 0, 1);
	private ITextField textFieldX = BuildGuide.widgetHandler.createTextField(basePropertiesX + Property.fieldX, 0, Property.fieldWidth, Property.rowHeight, "");
	private ITextField textFieldY = BuildGuide.widgetHandler.createTextField(basePropertiesX + Property.fieldX, 0, Property.fieldWidth, Property.rowHeight, "");
	private ITextField textFieldZ = BuildGuide.widgetHandler.createTextField(basePropertiesX + Property.fieldX, 0, Property.fieldWidth, Property.rowHeight, "");
	// Fixed Validate (manual full rescan of the current shape) and Reset (restores the defaults of the
	// properties shown right now, i.e. the open accordion section; nothing while Origin or no section
	// is open; control points are protected by the shapes themselves): the bottom row of the left
	// panel, two 90-px buttons at x 2..92 and 96..186 (the Preview button went with the inline preview, E6)
	private IButton buttonValidate = BuildGuide.widgetHandler.createButton(2, ShapeLayout.minHeight - 20, 90, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.validate"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
	});
	private IButton buttonReset = BuildGuide.widgetHandler.createButton(96, ShapeLayout.minHeight - 20, 90, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.reset"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().resetShownToDefaults();
	});
	// Right panel (E6), x 192..480: inline preview in y 40..175. Top row (y 42..60) for the filter and
	// slice controls, then the 3D area, then a hint line. The "+" in the corner opens the full-size
	// PreviewScreen; both views share the State's PreviewController (model, camera, input, D5)
	public static final int rightX = 192, previewTop = 40, previewControlsHeight = 20, previewHintHeight = 12, expandSize = 12;
	private final PreviewController preview = BuildGuide.stateManager.getState().preview;
	private IButton buttonPreviewExpand;
	// Preview controls (top row): slice axis (Off, X, Y, Z) and, when an axis is set, a slider over the model's
	// bounds on it. Changing the axis rebuilds the screen so the button text and the slider range follow (ISlider
	// has no callback and a fixed range). The status filter (Missing, Built...) went on 2026-10-22: the Part and
	// Layer buttons below are the one filter, of the world and the preview alike
	private static final int controlsHeight = 16, sliceWidth = 50, colourWidth = 80;
	private IButton buttonSliceAxis;
	// Layers (4C): "Colour: Status | Layer" first in the row, only while the shape's Layers is on (the slice
	// controls move right to make room). Whether it was laid out; a change rebuilds the screen (onMouseReleased)
	private IButton buttonColour = null;
	private boolean layerControls = false;
	// The model drawn this frame, for the legend (by status, or by layer with the depth ranges)
	private PreviewModel shownModel = null;
	private ISlider sliderSlice = null;
	private static final String[] axisNames = {"X", "Y", "Z"};
	// Build focus: a second row under the controls, "Part: ..." and, while the shape's Layers is on, "Layer: ...";
	// they choose what the world and the preview show (2026-10-22: one filter for both) and what Fill fills. The
	// 3D area starts below it
	public static final int focusRowY = previewTop + previewControlsHeight, focusRowHeight = 18, partWidth = 80, layerWidth = 64;
	private IButton buttonFocusPart, buttonFocusLayer = null;
	// Fill (SmartFill, 2026-10-22) at the row's right end: it opens FillScreen on what the row selects, to fill it
	// with blocks the player carries. Its title follows the task ("Fill", "Stop (45%)"), checked every frame
	private static final int fillWidth = 72;
	private IButton buttonFill;
	private String fillTitle = null;
	// Under the preview: a 2-px validation progress line and one line of text, the missing blocks first (at the
	// minimum height 236..250). The list of positions by coordinate is Visualisation's "Find blocks" topic since
	// 2026-10-22, so the preview reaches down to here. Vertical bands from the GUI height (E7): computed in init,
	// 270 until then
	private ShapeLayout bands = new ShapeLayout(ShapeLayout.minHeight);
	private Translatable textPreviewHint = new Translatable("screen.buildguide.previewhintinline");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");
	private Translatable textNoMatch = new Translatable("screen.buildguide.previewnomatch");
	private Translatable textWorldPending = new Translatable("screen.buildguide.worldupdatepending");
	// The preview's warning above this many blocks
	public static final int largeShapeBlocks = 200000;

	public void init() {
		super.init();
		// Init runs again on a resize or a GUI scale change (Screen.resize -> rebuildWidgets): widgets
		// kept as fields are the same objects, so the ones placed from the height move here
		bands = new ShapeLayout(wrapper.getHeight());
		buttonValidate.setYPosition(bands.bottomRowY);
		buttonReset.setYPosition(bands.bottomRowY);
		// Save opens the preset menu (E8): 3 global slots, saved, loaded and cleared there
		buttonSave = BuildGuide.widgetHandler.createButton(wrapper.getWidth() - 64, 0, saveWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.save"), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this)));
		// Instance name: a label; clicking it shows this field, Enter stores the name and hides it again
		textFieldName = BuildGuide.widgetHandler.createTextField(nameX, 0, nameEnd() - nameX, AbstractWidgetHandler.defaultSize, "");
		textFieldName.setVisibility(false);
		textFieldName.setOnEnter(() -> applyName());
		addWidget(buttonSetPrevious);
		addWidget(buttonSetNext);
		addWidget(buttonSave);
		addWidget(textFieldName);
		boolean severalSets = BuildGuide.stateManager.getState().shapeSets.size() > 1;
		buttonSetPrevious.setActive(severalSets);
		buttonSetNext.setActive(severalSets);
		textFieldX.setOnEnter(() -> applyOrigin(textFieldX, 0));
		textFieldY.setOnEnter(() -> applyOrigin(textFieldY, 1));
		textFieldZ.setOnEnter(() -> applyOrigin(textFieldZ, 2));
		
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) {
			dropdownOverlayShapeSelect.setActive(false);
			buttonOrigin.setActive(false);
			buttonOriginXDecrease.setActive(false);
			buttonOriginXIncrease.setActive(false);
			buttonOriginYDecrease.setActive(false);
			buttonOriginYIncrease.setActive(false);
			buttonOriginZDecrease.setActive(false);
			buttonOriginZIncrease.setActive(false);
		}
		
		textFieldX.setTextValue(BuildGuide.stateManager.getState().isShapeAvailable() ? "" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX() : "-");
		textFieldX.setTextColour(0xFFFFFF);
		textFieldY.setTextValue(BuildGuide.stateManager.getState().isShapeAvailable() ? "" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY() : "-");
		textFieldY.setTextColour(0xFFFFFF);
		textFieldZ.setTextValue(BuildGuide.stateManager.getState().isShapeAvailable() ? "" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ() : "-");
		textFieldZ.setTextColour(0xFFFFFF);
		
		addDropdownOverlayScreen(dropdownOverlayShapeSelect);
		addWidget(buttonOrigin);
		addWidget(buttonOriginXDecrease);
		addWidget(textFieldX);
		addWidget(buttonOriginXIncrease);
		addWidget(buttonOriginYDecrease);
		addWidget(textFieldY);
		addWidget(buttonOriginYIncrease);
		addWidget(buttonOriginZDecrease);
		addWidget(textFieldZ);
		addWidget(buttonOriginZIncrease);
		addWidget(buttonValidate);
		addWidget(buttonReset);
		buttonPreviewExpand = BuildGuide.widgetHandler.createButton(wrapper.getWidth() - expandSize - 2, previewTop + 2, expandSize, expandSize, new Translatable("+"), () -> BuildGuide.screenHandler.showScreen(new PreviewScreen(this)));
		buttonPreviewExpand.setActive(BuildGuide.stateManager.getState().isShapeAvailable());
		addWidget(buttonPreviewExpand);
		initPreviewControls();
		preview.attach();
		
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			for(Shape shape: BuildGuide.stateManager.getState().getCurrentShapeSet().shapes) {
				if(shape != null) {
					shape.onDeselectedInGUI();
					addShapeProperties(shape);
				}
			}
		}
		layout();
	}

	public void render() {
		updateFillTitle();
		super.render();

		renderShapeHeader();
		renderPreview();
		renderStatusLine();
		renderLegend();

		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		Shape shape = BuildGuide.stateManager.getState().getCurrentShape();
		boolean originOpen = BuildGuide.stateManager.getState().originOpen;
		int[] headerY = headerPositions(shape);
		renderHeader(headerY[0], titleOrigin.toString(), originRows, originOpen);
		for(int s = 0;s < shape.getSectionCount();++s) {
			renderHeader(headerY[s + 1], shape.getSectionName(s).toString(), shape.countRows(s), s == shape.getOpenSection());
		}
		if(originOpen) {
			int y = headerY[0] + headerHeight + originRowHeight + 5;
			drawShadowLeft("X", basePropertiesX + Property.labelX, y, 0xFFFFFF);
			drawShadowLeft("Y", basePropertiesX + Property.labelX, y + originRowHeight, 0xFFFFFF);
			drawShadowLeft("Z", basePropertiesX + Property.labelX, y + 2 * originRowHeight, 0xFFFFFF);
		}
	}

	// Preview frame and black area: under the widgets, or they would be painted over (E6 fix)
	@Override
	public void renderBackground() {
		fillRect(rightX, previewTop, wrapper.getWidth(), bands.previewBottom, 0xFF808080);
		fillRect(rightX + 1, previewTop + 1, wrapper.getWidth() - 1, bands.previewBottom - 1, 0xFF000000);
	}

	private void renderPreview() {
		int x1 = rightX, x2 = wrapper.getWidth(), y1 = previewAreaTop(), y2 = previewAreaBottom();
		drawShadowCentred(textPreviewHint.toString(), (x1 + x2) / 2, bands.previewBottom - previewHintHeight + 2, 0x888888);
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		updateSlice();
		PreviewModel model = preview.update(BuildGuide.stateManager.getState().getCurrentShape());
		shownModel = model;
		int midY = (y1 + y2) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isViewEmpty()) drawShadowCentred(textNoMatch.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1 + 1, y1, x2 - 1, y2, model, preview.camera);
		// Build focus: what the world shows, and so the preview above
		String focus = focusNotice(BuildGuide.stateManager.getState().getCurrentShape());
		if(focus != null) drawShadowLeft(focus, x1 + 3, y1 + 3, 0x66CCFF);
		// Live apply: the world still shows an older generation (Idle or On close mode)
		if(BuildGuide.stateManager.getState().getCurrentShape().worldGate.isPending()) drawShadowLeft(textWorldPending.toString(), x1 + 3, y2 - 10, 0xFFCC66);
		else {
			// Scan in Y layers in progress: the totals shown are still the previous scan's
			int scanning = BuildGuide.stateManager.getState().getCurrentShape().getValidationState().getScanPercent();
			if(scanning >= 0) drawShadowLeft(new Translatable("screen.buildguide.scanning", scanning + "%").toString(), x1 + 3, y2 - 10, 0xFFCC66);
		}
		// Spikes 2: a shape this large costs memory and time in the world (about 672 bytes of vertex buffer per block)
		int blocks = BuildGuide.stateManager.getState().getCurrentShape().getNumberOfBlocks();
		if(blocks > largeShapeBlocks) drawShadowRight(new Translatable("screen.buildguide.largeshape", "" + blocks).toString(), x2 - 3, y2 - 10, 0xFFCC66);
	}
	
	private void initPreviewControls() {
		int x = rightX + 2, y = previewTop + 2, axis = preview.getSliceAxis();
		Shape shape = currentShapeOrNull();
		layerControls = shape != null && shape.hasLayers();
		buttonColour = null;
		int colourSpace = 0;
		if(layerControls) {
			buttonColour = BuildGuide.widgetHandler.createButton(x, y, colourWidth, controlsHeight, new Translatable(preview.getColourMode() == ColourMode.LAYER ? "screen.buildguide.colour.layer" : "screen.buildguide.colour.status"), () -> {
				preview.setColourMode(preview.getColourMode() == ColourMode.LAYER ? ColourMode.STATUS : ColourMode.LAYER);
				BuildGuide.screenHandler.showScreen(this);
			});
			colourSpace = colourWidth + 2;
		}
		String axisName = axis == PreviewModel.SLICE_OFF ? new Translatable("screen.buildguide.slice.off").toString() : axisNames[axis];
		buttonSliceAxis = BuildGuide.widgetHandler.createButton(x + colourSpace, y, sliceWidth, controlsHeight, new Translatable("screen.buildguide.slice", axisName), () -> cycleSliceAxis());
		if(buttonColour != null) addWidget(buttonColour);
		addWidget(buttonSliceAxis);
		sliderSlice = null;
		PreviewModel model = preview.getModel();
		if(axis != PreviewModel.SLICE_OFF && model != null && !model.isEmpty()) {
			int sliderX = x + sliceWidth + 2 + colourSpace;
			// A one-block-thick shape on this axis still gets a working range (the value is clamped)
			sliderSlice = BuildGuide.widgetHandler.createSlider(sliderX, y, wrapper.getWidth() - expandSize - 4 - sliderX, controlsHeight, new Translatable(axisNames[axis]), model.min(axis), Math.max(model.max(axis), model.min(axis) + 1), preview.getSliceValue());
			addWidget(sliderSlice);
		}
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();
		buttonSliceAxis.setActive(available);
		buttonFocusPart = BuildGuide.widgetHandler.createButton(x, focusRowY, partWidth, controlsHeight, partTitle(shape), () -> {
			Shape s = currentShapeOrNull();
			if(s == null) return;
			s.setFocus(FocusFilter.nextPart(s.getFocusPart(), s.focusParts()), s.getFocusLayer());
			buttonFocusPart.setTitle(partTitle(s));
		});
		buttonFocusPart.setActive(available);
		addWidget(buttonFocusPart);
		buttonFocusLayer = null;
		if(layerControls) {
			buttonFocusLayer = BuildGuide.widgetHandler.createButton(x + partWidth + 2, focusRowY, layerWidth, controlsHeight, layerTitle(shape), () -> {
				Shape s = currentShapeOrNull();
				if(s == null) return;
				s.setFocus(s.getFocusPart(), FocusFilter.nextLayer(s.getFocusLayer()));
				buttonFocusLayer.setTitle(layerTitle(s));
			});
			addWidget(buttonFocusLayer);
		}
		fillTitle = SmartFill.title();
		buttonFill = BuildGuide.widgetHandler.createButton(wrapper.getWidth() - fillWidth - 2, focusRowY, fillWidth, controlsHeight, new Translatable(fillTitle), () -> {
			if(SmartFill.isRunning()) SmartFill.stop();
			else BuildGuide.screenHandler.showScreen(new FillScreen(this));
			updateFillTitle();
		});
		buttonFill.setActive(available);
		addWidget(buttonFill);
	}

	private void updateFillTitle() {
		String title = SmartFill.title();
		if(buttonFill == null || title.equals(fillTitle)) return;
		fillTitle = title;
		buttonFill.setTitle(new Translatable(title));
	}

	// "Part: Cap"; a part the shape does not have reads All (Shape.selectedFocus)
	private static Translatable partTitle(Shape shape) {
		FocusFilter.Part part = shape == null || shape.getFocusPart() == null ? FocusFilter.Part.ALL : shape.getFocusPart();
		if(shape != null && !shape.hasParts() && part != FocusFilter.Part.HIDDEN) part = FocusFilter.Part.ALL;
		return new Translatable("screen.buildguide.focus.part", new Translatable(part.translationKey).toString());
	}

	// "Layer: All", "Layer: 2"
	private static Translatable layerTitle(Shape shape) {
		int layer = shape == null ? FocusFilter.LAYER_ALL : shape.getFocusLayer();
		return new Translatable("screen.buildguide.focus.layer", layer == FocusFilter.LAYER_ALL ? new Translatable("screen.buildguide.focus.layer.all").toString() : "" + layer);
	}

	/**
	 * The preview's corner note: "Focus: Cap, Layer 2: 2626 of 8097 blocks" while the world shows a focus,
	 * "Focus off: < 400 blocks" while one is selected but the shape is below the threshold, else null.
	 */
	public static String focusNotice(Shape shape) {
		if(shape == null || !shape.ready || shape.error) return null;
		FocusFilter focus = shape.getWorldFocus();
		int total = shape.getNumberOfBlocks();
		if(focus != null) {
			String what = focus.part == FocusFilter.Part.ALL ? "" : new Translatable(focus.part.translationKey).toString();
			if(focus.layer != FocusFilter.LAYER_ALL && focus.part != FocusFilter.Part.HIDDEN) {
				String layer = new Translatable("screen.buildguide.focus.layern", "" + focus.layer).toString();
				what = what.isEmpty() ? layer : what + ", " + layer;
			}
			return new Translatable("screen.buildguide.focus.on", what, "" + shape.getWorldBlocks(), "" + total).toString();
		}
		int threshold = Shape.focusThreshold();
		if(shape.isFocusSelected() && total < threshold) return new Translatable("screen.buildguide.focus.off", "" + threshold).toString();
		return null;
	}
	
	// Off -> X -> Y -> Z -> Off; a new axis starts in the middle of the model on it
	private void cycleSliceAxis() {
		int axis = preview.getSliceAxis() == 2 ? PreviewModel.SLICE_OFF : preview.getSliceAxis() + 1;
		PreviewModel model = preview.getModel();
		int value = axis == PreviewModel.SLICE_OFF || model == null || model.isEmpty() ? 0 : (model.min(axis) + model.max(axis)) / 2;
		preview.setSlice(axis, value);
		BuildGuide.screenHandler.showScreen(this);
	}
	
	// Slider to a whole block: read every frame, snapped, and shown as that block's coordinate
	private void updateSlice() {
		if(sliderSlice == null) return;
		PreviewModel model = preview.getModel();
		int axis = preview.getSliceAxis();
		int value = (int) Math.round(sliderSlice.getSliderValue());
		if(model != null) value = Math.max(model.min(axis), Math.min(model.max(axis), value));
		if(value != preview.getSliceValue()) preview.setSlice(axis, value);
		sliderSlice.setSliderValue(value);
		sliderSlice.updateText();
	}
	
	// Under the preview (2026-10-22): the progress line and "Missing N   built ok / total (p%)", then the errors and
	// the wrong layer when there are any. Which positions those are: Visualisation, "Find blocks"
	private void renderStatusLine() {
		int x1 = rightX, x2 = wrapper.getWidth(), progressBottom = bands.progressTop + 2;
		Shape shape = currentShapeOrNull();
		fillRect(x1, bands.progressTop, x2, progressBottom, 0xFF303030);
		if(shape == null) {
			drawShadowLeft("-", x1 + 3, bands.statusTextY, 0x888888);
			return;
		}
		ValidationState state = shape.getValidationState();
		int[] counts = ValidationListComponent.counts(shape);
		if(counts == null) {
			drawShadowLeft(fit(new Translatable("screen.buildguide.status.notvalidated", "" + shape.getExpectedBlocks().size()).toString(), x2 - x1 - 6), x1 + 3, bands.statusTextY, 0xAAAAAA);
			return;
		}
		int fill = x1 + (int) Math.round((x2 - x1) * state.getProgress());
		if(fill > x1) fillRect(x1, bands.progressTop, fill, progressBottom, state.getOk() == state.getTotal() ? 0xFF40C040 : 0xFF40A0FF);
		String percent = String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * state.getProgress());
		String text = new Translatable("screen.buildguide.status.missing", "" + counts[1]) + "   " + new Translatable("screen.buildguide.status.built", "" + state.getOk(), "" + state.getTotal(), percent);
		if(counts[0] > 0) text += "   " + new Translatable("screen.buildguide.status.errors", "" + counts[0]);
		if(counts[3] > 0) text += "   " + new Translatable("screen.buildguide.status.wronglayer", "" + counts[3]);
		drawShadowLeft(fit(text, x2 - x1 - 6), x1 + 3, bands.statusTextY, counts[0] > 0 ? 0xFF8080 : 0xFFFFFF);
	}
	
	// Colour legend of the preview (E6), right of the bottom row: four 72-px entries, a swatch and a label.
	// Coloured by layer (4C): the four layer colours, each with the depths below the top it covers
	private static final String[] legendKeys = {"screen.buildguide.legend.built", "screen.buildguide.legend.errors", "screen.buildguide.legend.ignored", "screen.buildguide.legend.missing"};
	private static final int[] legendColours = {PreviewColours.OK, PreviewColours.ERROR, PreviewColours.IGNORED, PreviewColours.MISSING};
	
	private void renderLegend() {
		boolean byLayer = shownModel != null && shownModel.colourMode == ColourMode.LAYER;
		// By status with a palette (4D): a fifth entry, the orange of the wrong-layer blocks
		int entries = !byLayer && hasPalette() ? legendKeys.length + 1 : legendKeys.length;
		int step = (wrapper.getWidth() - rightX) / entries;
		for(int i = 0;i < entries;++i) {
			int x = rightX + i * step;
			boolean wrongLayer = i == legendKeys.length;
			fillRect(x + 4, bands.legendY, x + 12, bands.legendY + 8, 0xFF000000 | (byLayer ? ShapeLayers.colour(i + 1) : wrongLayer ? PreviewColours.WRONG_LAYER : legendColours[i]));
			String text = byLayer ? depthLabel(shownModel.layers.depthRange(i + 1)) : new Translatable(wrongLayer ? "screen.buildguide.legend.wronglayer" : legendKeys[i]).toString();
			drawShadowLeft(text, x + 16, bands.legendY, 0xAAAAAA);
		}
	}
	
	// The current shape's last scan used a palette per layer (4D)
	private boolean hasPalette() {
		Shape shape = currentShapeOrNull();
		return shape != null && shape.getValidationState().getPalette() != null;
	}
	
	// "0-5", "6" (one depth), "18+" (and everything below), "-" (no block depth falls in that layer)
	public static String depthLabel(int[] range) {
		if(range == null) return "-";
		if(range[1] == -1) return range[0] + "+";
		return range[0] == range[1] ? "" + range[0] : range[0] + "-" + range[1];
	}
	
	private Shape currentShapeOrNull() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}
	
	@Override
	protected boolean hasBottomBar() {
		return false; // progress is the status line under the preview; the legend takes the right of the bar (E6)
	}
	
	private int previewAreaTop() {
		return previewTop + previewControlsHeight + focusRowHeight;
	}
	
	private int previewAreaBottom() {
		return bands.previewBottom - previewHintHeight;
	}
	
	private boolean inPreviewArea(double x, double y) {
		return x >= rightX && x < wrapper.getWidth() && y >= previewAreaTop() && y < previewAreaBottom();
	}
	
	@Override
	public boolean onMouseDragged(double dx, double dy) {
		return preview.mouseDragged(dx, dy);
	}
	
	// Also where the Colour button follows the Layers box: a click on it (or Reset, or another shape) ends with
	// this release, so the controls row is laid out again when the shape's Layers no longer matches it
	@Override
	public void onMouseReleased() {
		preview.mouseReleased();
		Shape shape = currentShapeOrNull();
		if((shape != null && shape.hasLayers()) != layerControls) BuildGuide.screenHandler.showScreen(this);
	}
	
	@Override
	public boolean onMouseScrolled(double x, double y, double amount) {
		return preview.mouseScrolled(inPreviewArea(x, y), amount);
	}
	
	@Override
	protected boolean hasShapeHeader() {
		return true;
	}
	
	// Set selector text, type name on the dropdown, instance name (hidden while it is being edited)
	private void renderShapeHeader() {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		int index = BuildGuide.stateManager.getState().getShapeSetIndex();
		drawShadowCentred(count == 0 ? "-" : (index + 1) + "/" + count, (setPrevX + arrowWidth + setNextX) / 2, headerTextY, 0xFFFFFF);
		int typeTextWidth = typeWidth - AbstractWidgetHandler.defaultSize - 4; // left of the dropdown's open button
		drawShadowCentred(fit(BuildGuide.screenHandler.getFormattedShapeName(BuildGuide.stateManager.getState().getCurrentShapeSet()), typeTextWidth), typeX + (typeWidth - AbstractWidgetHandler.defaultSize) / 2, headerTextY, BuildGuide.screenHandler.getShapeProgressColour(BuildGuide.stateManager.getState().getCurrentShape()));
		if(count > 0 && !editingName) drawShadowLeft(fit(instanceName(), nameEnd() - nameX - 4), nameX + 2, headerTextY, 0xFFFFFF);
	}
	
	private int nameEnd() {
		return wrapper.getWidth() - 64 - 4;
	}
	
	// Name typed for the current set, or "Type #N" (N = its position in the list)
	private String instanceName() {
		String name = BuildGuide.stateManager.getState().getCurrentShapeSet().getName();
		if(name != null) return name;
		return new Translatable(BuildGuide.stateManager.getState().getCurrentShape().getTranslationKey()).toString() + " #" + (BuildGuide.stateManager.getState().getShapeSetIndex() + 1);
	}
	
	private boolean editingName = false;
	
	private void applyName() {
		BuildGuide.stateManager.getState().getCurrentShapeSet().setName(textFieldName.getTextValue()); // empty = default name; saved with the world since E8
		editingName = false;
		textFieldName.setVisibility(false);
	}
	
	// Previous / next shape set (wraps); the screen is rebuilt for the new set's properties
	private void switchShapeSet(int delta) {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		if(count < 2) return;
		BuildGuide.stateManager.getState().setShapeSetIndex(Math.floorMod(BuildGuide.stateManager.getState().getShapeSetIndex() + delta, count));
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}
	
	// Header row: "> Name" (closed) or "v Name" (open) on a dark strip, the row count on the right. The
	// text starts 1 px into the 10-px strip, so a descender's shadow still ends inside it
	private void renderHeader(int y, String name, int rows, boolean open) {
		fillRect(0, y, panelWidth, y + headerHeight - 1, open ? 0xC0505050 : 0xC0202020);
		drawShadowLeft((open ? "v " : "> ") + name, 4, y + 1, 0xFFFFFF);
		drawShadowRight("" + rows, panelWidth - 4, y + 1, 0xAAAAAA);
	}

	/**
	 * Y of every header, [0] = Origin, [1 + s] = shape section s. Each header is followed by its
	 * section's rows when open. Computed from the live row counts, so a changed Point count
	 * moves the headers below the open section at once.
	 */
	private int[] headerPositions(Shape shape) {
		int[] headerY = new int[1 + shape.getSectionCount()];
		int y = accordionTop;
		headerY[0] = y;
		y += headerHeight;
		if(BuildGuide.stateManager.getState().originOpen) y += originRows * originRowHeight;
		for(int s = 0;s < shape.getSectionCount();++s) {
			headerY[s + 1] = y;
			y += headerHeight;
			if(s == shape.getOpenSection()) y += shape.countRows(s) * Property.rowHeight;
		}
		return headerY;
	}

	// Places the Origin widgets and the open section's rows under their headers
	private void layout() {
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();
		boolean originOpen = available && BuildGuide.stateManager.getState().originOpen;
		int originTop = accordionTop + headerHeight;
		IWidget[][] rows = {{buttonOrigin}, {buttonOriginXDecrease, textFieldX, buttonOriginXIncrease}, {buttonOriginYDecrease, textFieldY, buttonOriginYIncrease}, {buttonOriginZDecrease, textFieldZ, buttonOriginZIncrease}};
		for(int r = 0;r < rows.length;++r) {
			for(IWidget w: rows[r]) {
				w.setYPosition(originTop + r * originRowHeight);
				w.setVisibility(originOpen);
			}
		}
		if(!available) return;

		Shape shape = BuildGuide.stateManager.getState().getCurrentShape();
		if(originOpen && shape.getOpenSection() != -1) shape.setOpenSection(-1); // Origin and a shape section are never open together
		int open = shape.getOpenSection();
		if(open != -1) shape.setRowsTop(headerPositions(shape)[open + 1] + headerHeight);
		shape.onSelectedInGUI();
	}

	@Override
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(button == MOUSE_LEFT && y < AbstractWidgetHandler.defaultSize && x >= nameX && x < nameEnd() && BuildGuide.stateManager.getState().isShapeAvailable()) {
			textFieldName.setTextValue(instanceName());
			textFieldName.setTextColour(0xFFFFFF);
			textFieldName.setVisibility(true);
			editingName = true;
			return true;
		}
		if(x >= rightX && y >= previewTop && y < bands.previewBottom) {
			return preview.mouseClicked(inPreviewArea(x, y), button, doubleClick);
		}
		if(button != MOUSE_LEFT || x < 0 || x >= panelWidth || !BuildGuide.stateManager.getState().isShapeAvailable()) return false;
		Shape shape = BuildGuide.stateManager.getState().getCurrentShape();
		int[] headerY = headerPositions(shape);
		for(int i = 0;i < headerY.length;++i) {
			if(y < headerY[i] || y >= headerY[i] + headerHeight) continue;
			// Accordion: opening one section closes the other; clicking the open one closes it
			if(i == 0) {
				BuildGuide.stateManager.getState().originOpen = !BuildGuide.stateManager.getState().originOpen;
			}else {
				BuildGuide.stateManager.getState().originOpen = false;
				shape.setOpenSection(shape.getOpenSection() == i - 1 ? -1 : i - 1);
			}
			layout();
			return true;
		}
		return false;
	}
	
	private void addShapeProperties(Shape shape) {
		for(Property<?> p: shape.getGuiProperties()) {
			addProperty(p);
		}
	}
	
	private void setShape(int i) {
		BuildGuide.stateManager.getState().getCurrentShape().onDeselectedInGUI();
		
		BuildGuide.stateManager.getState().setShape(i);
		if(!BuildGuide.stateManager.getState().getCurrentShapeSet().isShapeAvailable()) {
			addShapeProperties(BuildGuide.stateManager.getState().getCurrentShape());
		}

		layout();
	}
	
	private IButton originStep(int x, String text, int dx, int dy, int dz) {
		return BuildGuide.widgetHandler.createButton(basePropertiesX + x, 0, Property.stepWidth, Property.rowHeight, new Translatable(text), () -> shiftOrigin(dx, dy, dz));
	}
	
	// Enter in an origin field: set that axis, or mark the field red when it is not an integer
	private void applyOrigin(ITextField field, int axis) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		try {
			int value = Integer.parseInt(field.getTextValue());
			if(axis == 0) BuildGuide.stateManager.getState().setOriginX(value);
			else if(axis == 1) BuildGuide.stateManager.getState().setOriginY(value);
			else BuildGuide.stateManager.getState().setOriginZ(value);
			field.setTextColour(0xFFFFFF);
		}catch(NumberFormatException e) {
			field.setTextColour(0xFF0000);
		}
	}
	
	private void setOrigin() {
		BuildGuide.stateManager.getState().resetOrigin();
		BaseScreen.shouldUpdatePersistence = true;
		textFieldX.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX());
		textFieldX.setTextColour(0xFFFFFF);
		textFieldY.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY());
		textFieldY.setTextColour(0xFFFFFF);
		textFieldZ.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ());
		textFieldZ.setTextColour(0xFFFFFF);
	}
	
	private void shiftOrigin(int dx, int dy, int dz) {
		BuildGuide.stateManager.getState().shiftOrigin(dx, dy, dz);
		if(dx != 0) {
			textFieldX.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX());
			textFieldX.setTextColour(0xFFFFFF);
		}
		if(dy != 0) {
			textFieldY.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY());
			textFieldY.setTextColour(0xFFFFFF);
		}
		if(dz != 0) {
			textFieldZ.setTextValue("" + BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ());
			textFieldZ.setTextColour(0xFFFFFF);
		}
	}
}
