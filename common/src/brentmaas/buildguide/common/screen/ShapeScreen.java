package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.PreviewColours;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ShapeRegistry;

public class ShapeScreen extends BaseScreen{
	// Left panel (GUI redesign E5, plan §2.2): x 0..188, the accordion from y 42 (the shape dropdown
	// moved to the header in E6): one 12-px header per section (Origin first, then the shape's
	// sections), the open section's rows under its header. The right panel (x 192..480) is E6
	public static final int panelWidth = 188, accordionTop = 42, headerHeight = 12;
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
	private IButton buttonValidate = BuildGuide.widgetHandler.createButton(2, 250, 90, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.validate"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
	});
	private IButton buttonReset = BuildGuide.widgetHandler.createButton(96, 250, 90, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.reset"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().resetShownToDefaults();
	});
	// Right panel (E6), x 192..480: inline preview in y 40..175. Top row (y 42..60) for the filter and
	// slice controls, then the 3D area, then a hint line. The "+" in the corner opens the full-size
	// PreviewScreen; both views share the State's PreviewController (model, camera, input, D5)
	public static final int rightX = 192, previewTop = 40, previewBottom = 175, previewControlsHeight = 20, previewHintHeight = 12, expandSize = 12;
	private final PreviewController preview = BuildGuide.stateManager.getState().preview;
	private IButton buttonPreviewExpand;
	// Preview controls (top row): filter (cycles on click), slice axis (Off, X, Y, Z) and, when an axis
	// is set, a slider over the model's bounds on it. Changing filter or axis rebuilds the screen so the
	// button texts and the slider range follow (ISlider has no callback and a fixed range)
	private static final int controlsHeight = 16, filterWidth = 86, sliceWidth = 50;
	private IButton buttonFilter, buttonSliceAxis;
	private ISlider sliderSlice = null;
	private static final String[] axisNames = {"X", "Y", "Z"};
	// Under the preview: a 2-px validation progress line (y 175..177), three tabs (177..193) and the
	// error list of the selected tab (193..250). Replaces the bottom bar's progress on this screen
	public static final int progressTop = 175, tabsTop = 177, tabsBottom = 193, listBottom = 250;
	private final ValidationListComponent errorList = new ValidationListComponent();
	private Translatable textPreviewHint = new Translatable("screen.buildguide.previewhintinline");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");
	private Translatable textNoMatch = new Translatable("screen.buildguide.previewnomatch");
		public void init() {
		super.init();
		// Save is a placeholder until E7 (it will write the set, its name included): clickable, does nothing
		buttonSave = BuildGuide.widgetHandler.createButton(wrapper.getWidth() - 64, 0, saveWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.save"), () -> {});
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
		errorList.setCategory(initialTab());
		errorList.init(rightX, tabsBottom, wrapper.getWidth(), listBottom, currentShapeOrNull(), originX(), originY(), originZ());
		addWidget(errorList.getList());
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
		super.render();

		renderShapeHeader();
		renderPreview();
		renderListPanel();
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
		fillRect(rightX, previewTop, wrapper.getWidth(), previewBottom, 0xFF808080);
		fillRect(rightX + 1, previewTop + 1, wrapper.getWidth() - 1, previewBottom - 1, 0xFF000000);
	}

	private void renderPreview() {
		int x1 = rightX, x2 = wrapper.getWidth(), y1 = previewAreaTop(), y2 = previewAreaBottom();
		drawShadowCentred(textPreviewHint.toString(), (x1 + x2) / 2, previewBottom - previewHintHeight + 2, 0x888888);
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		updateSlice();
		PreviewModel model = preview.update(BuildGuide.stateManager.getState().getCurrentShape());
		int midY = (y1 + y2) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isViewEmpty()) drawShadowCentred(textNoMatch.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1 + 1, y1, x2 - 1, y2, model, preview.camera);
	}
	
	private void initPreviewControls() {
		int x = rightX + 2, y = previewTop + 2, axis = preview.getSliceAxis();
		buttonFilter = BuildGuide.widgetHandler.createButton(x, y, filterWidth, controlsHeight, new Translatable(preview.getFilter().translationKey), () -> {
			preview.setFilter(preview.getFilter().next());
			BuildGuide.screenHandler.showScreen(this);
		});
		String axisName = axis == PreviewModel.SLICE_OFF ? new Translatable("screen.buildguide.slice.off").toString() : axisNames[axis];
		buttonSliceAxis = BuildGuide.widgetHandler.createButton(x + filterWidth + 2, y, sliceWidth, controlsHeight, new Translatable("screen.buildguide.slice", axisName), () -> cycleSliceAxis());
		addWidget(buttonFilter);
		addWidget(buttonSliceAxis);
		sliderSlice = null;
		PreviewModel model = preview.getModel();
		if(axis != PreviewModel.SLICE_OFF && model != null && !model.isEmpty()) {
			int sliderX = x + filterWidth + sliceWidth + 4;
			// A one-block-thick shape on this axis still gets a working range (the value is clamped)
			sliderSlice = BuildGuide.widgetHandler.createSlider(sliderX, y, wrapper.getWidth() - expandSize - 4 - sliderX, controlsHeight, new Translatable(axisNames[axis]), model.min(axis), Math.max(model.max(axis), model.min(axis) + 1), preview.getSliceValue());
			addWidget(sliderSlice);
		}
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();
		buttonFilter.setActive(available);
		buttonSliceAxis.setActive(available);
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
	
	private ValidationListComponent.Category initialTab() {
		if(BuildGuide.stateManager.getState().listTab != null) return BuildGuide.stateManager.getState().listTab;
		int[] counts = ValidationListComponent.counts(currentShapeOrNull());
		return counts != null && counts[0] > 0 ? ValidationListComponent.Category.ERRORS : ValidationListComponent.Category.MISSING;
	}
	
	// Progress line, the three tabs with live counts, then the list rows (the list widget draws itself)
	private void renderListPanel() {
		int x1 = rightX, x2 = wrapper.getWidth();
		Shape shape = currentShapeOrNull();
		fillRect(x1, progressTop, x2, tabsTop, 0xFF303030);
		if(shape != null && shape.getValidationState().isValidated()) {
			ValidationState state = shape.getValidationState();
			int fill = x1 + (int) Math.round((x2 - x1) * state.getProgress());
			if(fill > x1) fillRect(x1, progressTop, fill, tabsTop, state.getOk() == state.getTotal() ? 0xFF40C040 : 0xFF40A0FF);
		}
		int[] counts = ValidationListComponent.counts(shape);
		ValidationListComponent.Category[] tabs = ValidationListComponent.Category.values();
		for(int i = 0;i < tabs.length;++i) {
			int tx1 = tabX(i), tx2 = tabX(i + 1);
			boolean selected = tabs[i] == errorList.getCategory();
			fillRect(tx1, tabsTop, tx2 - 1, tabsBottom, selected ? 0xC0505050 : 0xC0202020);
			String label = new Translatable(tabs[i].translationKey, counts == null ? "-" : "" + counts[i]).toString();
			drawShadowCentred(label, (tx1 + tx2) / 2, tabsTop + 4, selected ? 0xFFFFFF : 0xAAAAAA);
		}
		errorList.update(shape, originX(), originY(), originZ());
	}
	
	// Colour legend of the preview (E6), right of the bottom row: four 72-px entries, a swatch and a label
	private static final String[] legendKeys = {"screen.buildguide.legend.built", "screen.buildguide.legend.errors", "screen.buildguide.legend.ignored", "screen.buildguide.legend.missing"};
	private static final int[] legendColours = {PreviewColours.OK, PreviewColours.ERROR, PreviewColours.IGNORED, PreviewColours.MISSING};
	
	private void renderLegend() {
		int step = (wrapper.getWidth() - rightX) / legendKeys.length;
		for(int i = 0;i < legendKeys.length;++i) {
			int x = rightX + i * step;
			fillRect(x + 4, 256, x + 12, 264, 0xFF000000 | legendColours[i]);
			drawShadowLeft(new Translatable(legendKeys[i]).toString(), x + 16, 256, 0xAAAAAA);
		}
	}
	
	private int tabX(int i) {
		return rightX + i * (wrapper.getWidth() - rightX) / ValidationListComponent.Category.values().length;
	}
	
	private Shape currentShapeOrNull() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}
	
	private int originX() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX() : 0;
	}
	
	private int originY() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY() : 0;
	}
	
	private int originZ() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ() : 0;
	}
	
	@Override
	protected boolean hasBottomBar() {
		return false; // progress is the line above the tabs; the legend takes the right of the bar (E6)
	}
	
	private int previewAreaTop() {
		return previewTop + previewControlsHeight;
	}
	
	private int previewAreaBottom() {
		return previewBottom - previewHintHeight;
	}
	
	private boolean inPreviewArea(double x, double y) {
		return x >= rightX && x < wrapper.getWidth() && y >= previewAreaTop() && y < previewAreaBottom();
	}
	
	@Override
	public boolean onMouseDragged(double dx, double dy) {
		return preview.mouseDragged(dx, dy);
	}
	
	@Override
	public void onMouseReleased() {
		preview.mouseReleased();
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
		String name = BuildGuide.stateManager.getState().shapeSetNames.get(BuildGuide.stateManager.getState().getCurrentShapeSet());
		if(name != null) return name;
		return new Translatable(BuildGuide.stateManager.getState().getCurrentShape().getTranslationKey()).toString() + " #" + (BuildGuide.stateManager.getState().getShapeSetIndex() + 1);
	}
	
	private boolean editingName = false;
	
	private void applyName() {
		String name = textFieldName.getTextValue().trim();
		if(name.isEmpty()) BuildGuide.stateManager.getState().shapeSetNames.remove(BuildGuide.stateManager.getState().getCurrentShapeSet());
		else BuildGuide.stateManager.getState().shapeSetNames.put(BuildGuide.stateManager.getState().getCurrentShapeSet(), name);
		editingName = false;
		textFieldName.setVisibility(false);
	}
	
	// Cuts a string to the given pixel width, ending in ".."
	private String fit(String text, int width) {
		if(wrapper == null || wrapper.getTextWidth(text) <= width) return text;
		while(text.length() > 1 && wrapper.getTextWidth(text + "..") > width) text = text.substring(0, text.length() - 1);
		return text + "..";
	}
	
	// Previous / next shape set (wraps); the screen is rebuilt for the new set's properties
	private void switchShapeSet(int delta) {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		if(count < 2) return;
		BuildGuide.stateManager.getState().setShapeSetIndex(Math.floorMod(BuildGuide.stateManager.getState().getShapeSetIndex() + delta, count));
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}
	
	// Header row: "> Name" (closed) or "v Name" (open) on a dark strip, the row count on the right
	private void renderHeader(int y, String name, int rows, boolean open) {
		fillRect(0, y, panelWidth, y + headerHeight - 1, open ? 0xC0505050 : 0xC0202020);
		drawShadowLeft((open ? "v " : "> ") + name, 4, y + 2, 0xFFFFFF);
		drawShadowRight("" + rows, panelWidth - 4, y + 2, 0xAAAAAA);
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
		if(button == MOUSE_LEFT && x >= rightX && y >= tabsTop && y < tabsBottom) {
			ValidationListComponent.Category[] tabs = ValidationListComponent.Category.values();
			for(int i = 0;i < tabs.length;++i) {
				if(x >= tabX(i) && x < tabX(i + 1)) {
					BuildGuide.stateManager.getState().listTab = tabs[i];
					errorList.setCategory(tabs[i]);
					return true;
				}
			}
		}
		if(x >= rightX && y >= previewTop && y < previewBottom) {
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
