package brentmaas.buildguide.common.screen;

import java.util.List;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.FocusFilter;
import brentmaas.buildguide.common.shape.PreviewColours;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeLayers;
import brentmaas.buildguide.common.shape.ShapeRegistry;
import brentmaas.buildguide.common.shape.ValidationState;

/**
 * The Shape tab as the "Oficina" (2026-10-27, Raphael's choice among five prototypes): a rail of sections on the
 * left, a side panel that opens beside it (Shapes, Projects, Scan, Check), the 3D preview always on screen in the
 * middle, the shape's properties as an accordion at the right edge, and a status bar under the preview with Fill.
 *
 * The shape list closes by itself once a shape is picked, so the preview gets the room back. Origin, the
 * instances and the presets are one panel, Projects. Config opens the configuration screen. The old tab bar is
 * not shown here (the other screens still have it, with Shape to come back).
 */
public class ShapeScreen extends BaseScreen{
	// Columns: rail 0..44, side panel 44..194 while one is open, preview, inspector (the accordion) on the right
	public static final int railWidth = 44, sideWidth = 150, panelWidth = 188, headerHeight = 11;
	// Everything under the 20-px header; the accordion from y 22 to the inspector's bottom row
	public static final int top = 20, accordionTop = top + 2;
	public static final int basePropertiesX = 2;
	public static final int basePropertiesY = accordionTop + headerHeight;
	// The inspector's x for this screen width: the properties are laid out there (Shape.placeRow)
	public static int propertiesX = basePropertiesX;
	// Side panels; NONE = closed
	public static final int NONE = -1, SHAPES = 0, PROJECTS = 1, SCAN = 2, CHECK = 3;
	private static final String[] railKeys = {"screen.buildguide.rail.shapes", "screen.buildguide.rail.projects", "screen.buildguide.rail.scan", "screen.buildguide.rail.check", "screen.buildguide.rail.config"};
	private static final int railButtonHeight = 20, railGap = 2;
	// Header: shape button (opens the shape list), then the instance selector < N/Total >
	private static final int shapeButtonX = 22, shapeButtonWidth = 110, setPrevX = shapeButtonX + shapeButtonWidth + 4, arrowWidth = 14, setTextWidth = 30;
	// Preview controls (two 16-px rows at the top of the preview) only when the preview is at least this wide
	public static final int controlsMinWidth = 150, controlsHeight = 16, sliceWidth = 50, colourWidth = 80, partWidth = 80, layerWidth = 64, fillWidth = 72, expandSize = 12;
	public static final int previewTop = top, previewControlsHeight = 20, focusRowHeight = 18, legendHeight = 12;
	public static final int focusRowY = previewTop + previewControlsHeight;
	// The preview's warning above this many blocks
	public static final int largeShapeBlocks = 200000;

	private final PreviewController preview = BuildGuide.stateManager.getState().preview;
	private IButton buttonShape, buttonSetPrevious, buttonSetNext;
	private IButton buttonValidate, buttonReset, buttonPreviewExpand, buttonSliceAxis, buttonColour, buttonFocusPart, buttonFocusLayer, buttonFill;
	private ISlider sliderSlice = null;
	private ITextField textFieldX, textFieldY, textFieldZ, textFieldName;
	private boolean layerControls = false;
	private String fillTitle = null;
	private PreviewModel shownModel = null;
	private ShapeLayout bands = new ShapeLayout(ShapeLayout.minHeight);
	private static final String[] axisNames = {"X", "Y", "Z"};

	private Translatable textPreviewHint = new Translatable("screen.buildguide.previewhintinline");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");
	private Translatable textNoMatch = new Translatable("screen.buildguide.previewnomatch");
	private Translatable textWorldPending = new Translatable("screen.buildguide.worldupdatepending");

	// Preview x range for a screen width, with the side panel open or not
	public static int previewX1(boolean sideOpen) {
		return railWidth + (sideOpen ? sideWidth : 0) + 2;
	}

	public static int previewX2(int width) {
		return width - panelWidth - 2;
	}

	public static int railButtonY(int i) {
		return top + railGap + i * (railButtonHeight + railGap);
	}

	private static int sidePanel() {
		return BuildGuide.stateManager.getState().sidePanel;
	}

	private int px1() {
		return previewX1(sidePanel() != NONE);
	}

	private int px2() {
		return previewX2(wrapper.getWidth());
	}

	private int inspectorX() {
		return wrapper.getWidth() - panelWidth;
	}

	private boolean compact() {
		return px2() - px1() < controlsMinWidth;
	}

	@Override
	protected boolean hasTabs() {
		return false;
	}

	@Override
	protected boolean hasShapeHeader() {
		return true;
	}

	@Override
	protected boolean hasBottomBar() {
		return false;
	}

	public void init() {
		super.init();
		bands = new ShapeLayout(wrapper.getHeight());
		propertiesX = inspectorX() + basePropertiesX;
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();

		// Rail: the side panels (a click opens one, a second click closes it), then Config (its own screen)
		for(int i = 0;i < railKeys.length;++i) {
			final int panel = i;
			addWidget(BuildGuide.widgetHandler.createButton(2, railButtonY(i), railWidth - 3, railButtonHeight, new Translatable(railKeys[i]), () -> openPanel(panel)));
		}

		// Header
		buttonShape = BuildGuide.widgetHandler.createButton(shapeButtonX, 0, shapeButtonWidth, AbstractWidgetHandler.defaultSize, new Translatable(fit(shapeName() + " v", shapeButtonWidth - 8)), () -> openPanel(sidePanel() == SHAPES ? NONE : SHAPES));
		buttonSetPrevious = BuildGuide.widgetHandler.createButton(setPrevX, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable("<"), () -> switchShapeSet(-1));
		buttonSetNext = BuildGuide.widgetHandler.createButton(setPrevX + arrowWidth + setTextWidth, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable(">"), () -> switchShapeSet(1));
		boolean severalSets = BuildGuide.stateManager.getState().shapeSets.size() > 1;
		buttonSetPrevious.setActive(severalSets);
		buttonSetNext.setActive(severalSets);
		addWidget(buttonShape);
		addWidget(buttonSetPrevious);
		addWidget(buttonSetNext);

		initSidePanel(available);

		// Inspector's bottom row: Validate and Reset (the defaults of the open section)
		int ix = inspectorX();
		buttonValidate = BuildGuide.widgetHandler.createButton(ix + 2, bands.bottomRowY, 90, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.validate"), () -> {
			if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
		});
		buttonReset = BuildGuide.widgetHandler.createButton(ix + 96, bands.bottomRowY, 90, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.reset"), () -> {
			if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().resetShownToDefaults();
		});
		buttonValidate.setActive(available);
		buttonReset.setActive(available);
		addWidget(buttonValidate);
		addWidget(buttonReset);

		initPreviewControls(available);
		preview.attach();

		if(available) {
			for(Shape shape: BuildGuide.stateManager.getState().getCurrentShapeSet().shapes) {
				if(shape != null) {
					shape.onDeselectedInGUI();
					for(Property<?> p: shape.getGuiProperties()) addProperty(p);
				}
			}
		}
		layout();
	}

	// ---- Side panels ----

	private static final int sideRowHeight = 18;

	private int sideX() {
		return railWidth + 2;
	}

	private void initSidePanel(boolean available) {
		int x = sideX(), w = sideWidth - 4, y = top + 14;
		switch(sidePanel()) {
		case SHAPES: {
			// Two columns of the registered shapes; the current one inactive
			List<Translatable> names = ShapeRegistry.getTranslatables();
			int colWidth = (w - 2) / 2, current = BuildGuide.stateManager.getState().getCurrentShapeIndex();
			for(int i = 0;i < names.size();++i) {
				final int index = i;
				IButton b = BuildGuide.widgetHandler.createButton(x + (i % 2) * (colWidth + 2), y + (i / 2) * sideRowHeight, colWidth, sideRowHeight - 2, new Translatable(fit(names.get(i).toString(), colWidth - 6)), () -> pickShape(index));
				b.setActive(available && i != current);
				addWidget(b);
			}
			break;
		}
		case PROJECTS: {
			// Where it stands: to my position, then X, Y, Z as - field +
			addWidget(BuildGuide.widgetHandler.createButton(x, y, w, sideRowHeight - 2, new Translatable("screen.buildguide.setorigin"), () -> setOrigin()));
			textFieldX = originRow(x, y + sideRowHeight, w, 0);
			textFieldY = originRow(x, y + 2 * sideRowHeight, w, 1);
			textFieldZ = originRow(x, y + 3 * sideRowHeight, w, 2);
			// This instance's name (Enter stores it), the instance list and the presets
			int ny = y + 4 * sideRowHeight + 14;
			textFieldName = BuildGuide.widgetHandler.createTextField(x, ny, w, sideRowHeight - 2, available ? instanceName() : "");
			textFieldName.setOnEnter(() -> applyName());
			addWidget(textFieldName);
			addWidget(BuildGuide.widgetHandler.createButton(x, ny + sideRowHeight, w, sideRowHeight - 2, new Translatable("screen.buildguide.panel.instances"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shapelist))));
			addWidget(BuildGuide.widgetHandler.createButton(x, ny + 2 * sideRowHeight, w, sideRowHeight - 2, new Translatable("screen.buildguide.panel.presets"), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this))));
			refreshOriginFields();
			break;
		}
		case CHECK: {
			addWidget(BuildGuide.widgetHandler.createButton(x, y + 5 * 11 + 4, w, sideRowHeight - 2, new Translatable("screen.buildguide.panel.findblocks"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Visualisation))));
			addWidget(BuildGuide.widgetHandler.createButton(x, y + 5 * 11 + 4 + sideRowHeight, w, sideRowHeight - 2, new Translatable("screen.buildguide.exclusions"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Exclusions))));
			break;
		}
		default:
		}
	}

	private ITextField originRow(int x, int y, int w, int axis) {
		int fieldX = x + 12 + Property.stepWidth, fieldW = w - 12 - 2 * Property.stepWidth;
		ITextField field = BuildGuide.widgetHandler.createTextField(fieldX, y, fieldW, sideRowHeight - 2, "");
		field.setOnEnter(() -> applyOrigin(field, axis));
		addWidget(BuildGuide.widgetHandler.createButton(x + 12, y, Property.stepWidth, sideRowHeight - 2, new Translatable("-"), () -> shiftOrigin(axis == 0 ? -1 : 0, axis == 1 ? -1 : 0, axis == 2 ? -1 : 0)));
		addWidget(field);
		addWidget(BuildGuide.widgetHandler.createButton(fieldX + fieldW, y, Property.stepWidth, sideRowHeight - 2, new Translatable("+"), () -> shiftOrigin(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0)));
		return field;
	}

	private void openPanel(int panel) {
		if(panel == railKeys.length - 1) {
			BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Settings));
			return;
		}
		BuildGuide.stateManager.getState().sidePanel = panel == sidePanel() ? NONE : panel;
		BuildGuide.screenHandler.showScreen(this);
	}

	// A shape picked in the list: the list closes, so the preview gets the room back
	private void pickShape(int index) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().getCurrentShape().onDeselectedInGUI();
		BuildGuide.stateManager.getState().setShape(index);
		BuildGuide.stateManager.getState().sidePanel = NONE;
		BuildGuide.screenHandler.showScreen(this);
	}

	private void renderSidePanel() {
		int panel = sidePanel();
		if(panel == NONE) return;
		int x = sideX(), y = top + 3;
		drawShadowLeft(new Translatable(railKeys[panel]).toString(), x, y, 0xFFFFFF);
		Shape shape = currentShapeOrNull();
		switch(panel) {
		case PROJECTS: {
			int ry = top + 14 + sideRowHeight + 5;
			for(int a = 0;a < 3;++a) drawShadowLeft(axisNames[a], x + 2, ry + a * sideRowHeight, 0xFFFFFF);
			drawShadowLeft(new Translatable("screen.buildguide.panel.instance").toString(), x, top + 14 + 4 * sideRowHeight + 4, 0xAAAAAA);
			break;
		}
		case SCAN: {
			// The scan is the next stage of the plan: what it will offer
			String[] keys = {"screen.buildguide.panel.scan.next", "screen.buildguide.panel.scan.island", "screen.buildguide.panel.scan.under", "screen.buildguide.panel.scan.above", "screen.buildguide.panel.scan.region", "screen.buildguide.panel.scan.hole"};
			for(int i = 0;i < keys.length;++i) drawShadowLeft(fit(new Translatable(keys[i]).toString(), sideWidth - 4), x, top + 16 + i * 12, i == 0 ? 0xFFCC66 : 0xAAAAAA);
			break;
		}
		case CHECK: {
			if(shape == null) break;
			ValidationState state = shape.getValidationState();
			int[] counts = ValidationListComponent.counts(shape);
			int ly = top + 16;
			if(counts == null) {
				drawShadowLeft(fit(new Translatable("screen.buildguide.status.notvalidated", "" + shape.getExpectedBlocks().size()).toString(), sideWidth - 4), x, ly, 0xAAAAAA);
				break;
			}
			drawShadowLeft(String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * state.getProgress()), x, ly, 0xFFFFFF);
			drawShadowLeft(new Translatable("screen.buildguide.status.built", "" + state.getOk(), "" + state.getTotal(), "").toString().trim(), x, ly + 11, 0x80FF80);
			drawShadowLeft(new Translatable("screen.buildguide.status.missing", "" + counts[1]).toString(), x, ly + 22, 0xFFFFFF);
			drawShadowLeft(new Translatable("screen.buildguide.status.errors", "" + counts[0]).toString(), x, ly + 33, counts[0] > 0 ? 0xFF8080 : 0xAAAAAA);
			drawShadowLeft(new Translatable("screen.buildguide.status.wronglayer", "" + counts[3]).toString(), x, ly + 44, counts[3] > 0 ? 0xFFA040 : 0xAAAAAA);
			break;
		}
		default:
		}
	}

	// ---- Rendering ----

	@Override
	public void renderBackground() {
		int h = wrapper.getHeight();
		// The flat look of the new interface (Theme): the top bar and the rail on the darkest ground, the panels a step lighter
		fillRect(0, 0, wrapper.getWidth(), top, Theme.BACKGROUND);
		fillRect(0, top, railWidth, h, Theme.BACKGROUND);
		if(sidePanel() != NONE) {
			// The open panel: a blue mark beside its rail button, and the panel's ground
			fillRect(0, railButtonY(sidePanel()), 2, railButtonY(sidePanel()) + railButtonHeight, Theme.ACCENT);
			fillRect(railWidth, top, railWidth + sideWidth, h, Theme.PANEL);
		}
		fillRect(px1() - 1, previewTop, px2() + 1, bands.previewBottom, Theme.BORDER);
		fillRect(px1(), previewTop + 1, px2(), bands.previewBottom - 1, Theme.FIELD);
		fillRect(inspectorX(), top, wrapper.getWidth(), h, Theme.PANEL);
	}

	public void render() {
		updateFillTitle();
		super.render();
		renderHeaderText();
		renderSidePanel();
		renderPreview();
		renderStatusLine();
		renderLegend();
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		int[] headerY = headerPositions(shape);
		for(int s = 0;s < shape.getSectionCount();++s) renderSectionHeader(headerY[s], shape.getSectionName(s).toString(), shape.countRows(s), s == shape.getOpenSection());
	}

	private void renderHeaderText() {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		int index = BuildGuide.stateManager.getState().getShapeSetIndex();
		drawShadowCentred(count == 0 ? "-" : (index + 1) + "/" + count, setPrevX + arrowWidth + setTextWidth / 2, headerTextY, 0xFFFFFF);
		// The instance's name after the selector, up to the close button
		int nameX = setPrevX + 2 * arrowWidth + setTextWidth + 6, nameEnd = wrapper.getWidth() - AbstractWidgetHandler.defaultSize - 4;
		if(count > 0 && nameEnd > nameX + 20) drawShadowLeft(fit(instanceName(), nameEnd - nameX), nameX, headerTextY, 0xAAAAAA);
	}

	private void renderPreview() {
		int x1 = px1(), x2 = px2(), y1 = previewAreaTop(), y2 = previewAreaBottom();
		if(!compact()) drawShadowCentred(fit(textPreviewHint.toString(), x2 - x1 - 4), (x1 + x2) / 2, y2 + 1, 0x888888);
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		updateSlice();
		PreviewModel model = preview.update(shape);
		shownModel = model;
		int midY = (y1 + y2) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isViewEmpty()) drawShadowCentred(textNoMatch.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1, y1, x2, y2, model, preview.camera);
		String focus = focusNotice(shape);
		if(focus != null) drawShadowLeft(fit(focus, x2 - x1 - 6), x1 + 3, y1 + 3, 0x66CCFF);
		String fillStatus = SmartFill.status();
		if(fillStatus != null) drawShadowLeft(fit(fillStatus, x2 - x1 - 6), x1 + 3, y2 - 20, SmartFill.isRunning() ? 0x80FF80 : 0xAAAAAA);
		if(shape.worldGate.isPending()) drawShadowLeft(fit(textWorldPending.toString(), x2 - x1 - 6), x1 + 3, y2 - 10, 0xFFCC66);
		else {
			int scanning = shape.getValidationState().getScanPercent();
			if(scanning >= 0) drawShadowLeft(new Translatable("screen.buildguide.scanning", scanning + "%").toString(), x1 + 3, y2 - 10, 0xFFCC66);
		}
		int blocks = shape.getNumberOfBlocks();
		if(blocks > largeShapeBlocks) drawShadowRight(new Translatable("screen.buildguide.largeshape", "" + blocks).toString(), x2 - 3, y2 - 10, 0xFFCC66);
	}

	// Under the preview: the progress line, then "Missing N   built ok / total" left of Fill
	private void renderStatusLine() {
		int x1 = px1(), x2 = px2(), progressBottom = bands.progressTop + 2, textWidth = x2 - x1 - fillWidth - 8;
		Shape shape = currentShapeOrNull();
		fillRect(x1, bands.progressTop, x2, progressBottom, Theme.CONTROL);
		if(shape == null) {
			drawShadowLeft("-", x1 + 3, bands.statusTextY, 0x888888);
			return;
		}
		ValidationState state = shape.getValidationState();
		int[] counts = ValidationListComponent.counts(shape);
		if(counts == null) {
			drawShadowLeft(fit(new Translatable("screen.buildguide.status.notvalidated", "" + shape.getExpectedBlocks().size()).toString(), textWidth), x1 + 3, bands.statusTextY, 0xAAAAAA);
			return;
		}
		int fill = x1 + (int) Math.round((x2 - x1) * state.getProgress());
		if(fill > x1) fillRect(x1, bands.progressTop, fill, progressBottom, state.getOk() == state.getTotal() ? Theme.OK : Theme.ACCENT);
		String percent = String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * state.getProgress());
		String text = new Translatable("screen.buildguide.status.missing", "" + counts[1]) + "   " + new Translatable("screen.buildguide.status.built", "" + state.getOk(), "" + state.getTotal(), percent);
		if(counts[0] > 0) text += "   " + new Translatable("screen.buildguide.status.errors", "" + counts[0]);
		if(counts[3] > 0) text += "   " + new Translatable("screen.buildguide.status.wronglayer", "" + counts[3]);
		drawShadowLeft(fit(text, textWidth), x1 + 3, bands.statusTextY, counts[0] > 0 ? 0xFF8080 : 0xFFFFFF);
	}

	// The colour legend at the foot of the preview: by status (with the wrong-layer orange under a palette), or by
	// layer with what each layer is
	private static final String[] legendKeys = {"screen.buildguide.legend.built", "screen.buildguide.legend.errors", "screen.buildguide.legend.ignored", "screen.buildguide.legend.missing"};
	private static final int[] legendColours = {PreviewColours.OK, PreviewColours.ERROR, PreviewColours.IGNORED, PreviewColours.MISSING};
	public static final int legendSwatch = 8, legendTextX = 11;

	private void renderLegend() {
		boolean byLayer = shownModel != null && shownModel.colourMode == ColourMode.LAYER;
		int entries = byLayer ? shownModel.layers.count() : hasPalette() ? legendKeys.length + 1 : legendKeys.length;
		int x1 = px1(), step = (px2() - x1) / entries, y = bands.legendY;
		for(int i = 0;i < entries;++i) {
			int x = x1 + i * step;
			boolean wrongLayer = i == legendKeys.length;
			fillRect(x + 2, y, x + 2 + legendSwatch, y + legendSwatch, 0xFF000000 | (byLayer ? ShapeLayers.colour(i + 1) : wrongLayer ? PreviewColours.WRONG_LAYER : legendColours[i]));
			String text = byLayer ? legendLabel(i + 1, shownModel.layers.legend(i + 1)) : new Translatable(wrongLayer ? "screen.buildguide.legend.wronglayer" : legendKeys[i]).toString();
			drawShadowLeft(fit(text, step - legendTextX - 2), x + legendTextX, y, 0xAAAAAA);
		}
	}

	private boolean hasPalette() {
		Shape shape = currentShapeOrNull();
		return shape != null && shape.getValidationState().getPalette() != null;
	}

	// A layer's legend (ShapeLayers.legend), its number first: "2 3b" (3 blocks), "4 25%", "3 rest", "5 spots", "5 -"
	public static String legendLabel(int layer, int[] legend) {
		String n = "" + layer, value = "" + legend[1];
		switch(legend[0]) {
		case ShapeLayers.LEGEND_BLOCKS: return new Translatable("screen.buildguide.legend.blocks", n, value).toString();
		case ShapeLayers.LEGEND_BOTTOM:
		case ShapeLayers.LEGEND_TOP:
		case ShapeLayers.LEGEND_BASE: return new Translatable("screen.buildguide.legend.percent", n, value).toString();
		case ShapeLayers.LEGEND_REST: return new Translatable("screen.buildguide.legend.rest", n).toString();
		case ShapeLayers.LEGEND_PATCHES: return new Translatable("screen.buildguide.legend.patches", n).toString();
		default: return new Translatable("screen.buildguide.legend.empty", n).toString();
		}
	}

	// ---- Preview controls ----

	private void initPreviewControls(boolean available) {
		int x = px1() + 2, x2 = px2(), y = previewTop + 2, axis = preview.getSliceAxis();
		Shape shape = currentShapeOrNull();
		layerControls = shape != null && shape.hasLayers();
		buttonColour = null;
		buttonFocusLayer = null;
		sliderSlice = null;
		buttonSliceAxis = null;
		buttonFocusPart = null;
		buttonPreviewExpand = BuildGuide.widgetHandler.createButton(x2 - expandSize - 2, y, expandSize, expandSize, new Translatable("+"), () -> BuildGuide.screenHandler.showScreen(new PreviewScreen(this)));
		buttonPreviewExpand.setActive(available);
		addWidget(buttonPreviewExpand);
		// Fill at the status bar's right end
		fillTitle = SmartFill.title();
		buttonFill = BuildGuide.widgetHandler.createButton(x2 - fillWidth, bands.previewBottom + 2, fillWidth, controlsHeight, new Translatable(fillTitle), () -> {
			if(SmartFill.isRunning()) SmartFill.stop();
			else BuildGuide.screenHandler.showScreen(new FillScreen(this));
			updateFillTitle();
		});
		buttonFill.setActive(available);
		addWidget(buttonFill);
		if(compact()) return;
		// Row 1: colour (with Layers on), slice axis, slice slider. Row 2: Part, Layer
		int colourSpace = 0;
		if(layerControls) {
			buttonColour = BuildGuide.widgetHandler.createButton(x, y, colourWidth, controlsHeight, new Translatable(preview.getColourMode() == ColourMode.LAYER ? "screen.buildguide.colour.layer" : "screen.buildguide.colour.status"), () -> {
				preview.setColourMode(preview.getColourMode() == ColourMode.LAYER ? ColourMode.STATUS : ColourMode.LAYER);
				BuildGuide.screenHandler.showScreen(this);
			});
			addWidget(buttonColour);
			colourSpace = colourWidth + 2;
		}
		String axisName = axis == PreviewModel.SLICE_OFF ? new Translatable("screen.buildguide.slice.off").toString() : axisNames[axis];
		buttonSliceAxis = BuildGuide.widgetHandler.createButton(x + colourSpace, y, sliceWidth, controlsHeight, new Translatable("screen.buildguide.slice", axisName), () -> cycleSliceAxis());
		buttonSliceAxis.setActive(available);
		addWidget(buttonSliceAxis);
		PreviewModel model = preview.getModel();
		int sliderX = x + colourSpace + sliceWidth + 2, sliderW = x2 - expandSize - 4 - sliderX;
		if(axis != PreviewModel.SLICE_OFF && model != null && !model.isEmpty() && sliderW >= 30) {
			sliderSlice = BuildGuide.widgetHandler.createSlider(sliderX, y, sliderW, controlsHeight, new Translatable(axisNames[axis]), model.min(axis), Math.max(model.max(axis), model.min(axis) + 1), preview.getSliceValue());
			addWidget(sliderSlice);
		}
		buttonFocusPart = BuildGuide.widgetHandler.createButton(x, focusRowY, partWidth, controlsHeight, partTitle(shape), () -> {
			Shape s = currentShapeOrNull();
			if(s == null) return;
			s.setFocus(FocusFilter.nextPart(s.getFocusPart(), s.focusParts()), s.getFocusLayer());
			buttonFocusPart.setTitle(partTitle(s));
		});
		buttonFocusPart.setActive(available);
		addWidget(buttonFocusPart);
		if(layerControls) {
			buttonFocusLayer = BuildGuide.widgetHandler.createButton(x + partWidth + 2, focusRowY, layerWidth, controlsHeight, layerTitle(shape), () -> {
				Shape s = currentShapeOrNull();
				if(s == null) return;
				s.setFocus(s.getFocusPart(), FocusFilter.nextLayer(s.getFocusLayer(), s.getLayers() != null ? s.getLayers().count() : ShapeLayers.COUNT));
				buttonFocusLayer.setTitle(layerTitle(s));
			});
			addWidget(buttonFocusLayer);
		}
	}

	private void updateFillTitle() {
		String title = SmartFill.title();
		if(buttonFill == null || title.equals(fillTitle)) return;
		fillTitle = title;
		buttonFill.setTitle(new Translatable(title));
	}

	private static Translatable partTitle(Shape shape) {
		FocusFilter.Part part = shape == null || shape.getFocusPart() == null ? FocusFilter.Part.ALL : shape.getFocusPart();
		if(shape != null && !shape.hasParts() && part != FocusFilter.Part.HIDDEN) part = FocusFilter.Part.ALL;
		return new Translatable("screen.buildguide.focus.part", new Translatable(part.translationKey).toString());
	}

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

	private void cycleSliceAxis() {
		int axis = preview.getSliceAxis() == 2 ? PreviewModel.SLICE_OFF : preview.getSliceAxis() + 1;
		PreviewModel model = preview.getModel();
		int value = axis == PreviewModel.SLICE_OFF || model == null || model.isEmpty() ? 0 : (model.min(axis) + model.max(axis)) / 2;
		preview.setSlice(axis, value);
		BuildGuide.screenHandler.showScreen(this);
	}

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

	private int previewAreaTop() {
		return compact() ? previewTop + 1 : previewTop + previewControlsHeight + focusRowHeight;
	}

	private int previewAreaBottom() {
		return bands.legendY - (compact() ? 2 : legendHeight);
	}

	private boolean inPreviewArea(double x, double y) {
		return x >= px1() && x < px2() && y >= previewAreaTop() && y < previewAreaBottom();
	}

	// ---- Inspector (the accordion of the shape's sections) ----

	// Header strip: "> Name" or "v Name" on a dark strip, the row count on the right
	private void renderSectionHeader(int y, String name, int rows, boolean open) {
		int ix = inspectorX();
		fillRect(ix, y, wrapper.getWidth(), y + headerHeight - 1, open ? Theme.SELECTED : Theme.SURFACE);
		if(open) fillRect(ix, y, ix + 2, y + headerHeight - 1, Theme.ACCENT);
		drawShadowLeft(fit((open ? "v " : "> ") + name, panelWidth - 24), ix + 4, y + 1, Theme.rgb(open ? Theme.TEXT : Theme.TEXT_SOFT));
		drawShadowRight("" + rows, wrapper.getWidth() - 4, y + 1, Theme.rgb(Theme.TEXT_FAINT));
	}

	// Y of every section header; the open section's rows follow its header
	private int[] headerPositions(Shape shape) {
		int[] headerY = new int[shape.getSectionCount()];
		int y = accordionTop;
		for(int s = 0;s < shape.getSectionCount();++s) {
			headerY[s] = y;
			y += headerHeight;
			if(s == shape.getOpenSection()) y += shape.countRows(s) * Property.rowHeight;
		}
		return headerY;
	}

	private void layout() {
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		int open = shape.getOpenSection();
		if(open != -1) shape.setRowsTop(headerPositions(shape)[open] + headerHeight);
		shape.onSelectedInGUI();
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(x >= px1() && x < px2() && y >= previewTop && y < bands.previewBottom) {
			return preview.mouseClicked(inPreviewArea(x, y), button, doubleClick);
		}
		Shape shape = currentShapeOrNull();
		if(button != MOUSE_LEFT || x < inspectorX() || shape == null) return false;
		int[] headerY = headerPositions(shape);
		for(int i = 0;i < headerY.length;++i) {
			if(y < headerY[i] || y >= headerY[i] + headerHeight) continue;
			// Accordion: opening one section closes the other; clicking the open one closes it
			shape.setOpenSection(shape.getOpenSection() == i ? -1 : i);
			layout();
			return true;
		}
		return false;
	}

	@Override
	public boolean onMouseDragged(double dx, double dy) {
		return preview.mouseDragged(dx, dy);
	}

	// The Colour and Layer buttons follow the Layers box: a click that turned it on or off rebuilds the screen
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

	// ---- Instances, origin ----

	private Shape currentShapeOrNull() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	private String shapeName() {
		Shape shape = currentShapeOrNull();
		return shape == null ? "-" : new Translatable(shape.getTranslationKey()).toString();
	}

	// Name typed for the current set, or "Type #N" (N = its position in the list)
	private String instanceName() {
		String name = BuildGuide.stateManager.getState().getCurrentShapeSet().getName();
		if(name != null) return name;
		return shapeName() + " #" + (BuildGuide.stateManager.getState().getShapeSetIndex() + 1);
	}

	private void applyName() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().getCurrentShapeSet().setName(textFieldName.getTextValue()); // empty = default name; saved with the world
		BaseScreen.shouldUpdatePersistence = true;
	}

	private void switchShapeSet(int delta) {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		if(count < 2) return;
		BuildGuide.stateManager.getState().setShapeSetIndex(Math.floorMod(BuildGuide.stateManager.getState().getShapeSetIndex() + delta, count));
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}

	private void refreshOriginFields() {
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();
		ITextField[] fields = {textFieldX, textFieldY, textFieldZ};
		for(int a = 0;a < 3;++a) {
			if(fields[a] == null) continue;
			int v = !available ? 0 : a == 0 ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX() : a == 1 ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY() : BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ();
			fields[a].setTextValue(available ? "" + v : "-");
			fields[a].setTextColour(0xFFFFFF);
		}
	}

	// Enter in an origin field: set that axis, or mark the field red when it is not an integer
	private void applyOrigin(ITextField field, int axis) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		try {
			int value = Integer.parseInt(field.getTextValue().trim());
			if(axis == 0) BuildGuide.stateManager.getState().setOriginX(value);
			else if(axis == 1) BuildGuide.stateManager.getState().setOriginY(value);
			else BuildGuide.stateManager.getState().setOriginZ(value);
			field.setTextColour(0xFFFFFF);
		}catch(NumberFormatException e) {
			field.setTextColour(0xFF0000);
		}
	}

	private void setOrigin() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().resetOrigin();
		BaseScreen.shouldUpdatePersistence = true;
		refreshOriginFields();
	}

	private void shiftOrigin(int dx, int dy, int dz) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().shiftOrigin(dx, dy, dz);
		refreshOriginFields();
	}
}
