package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeRegistry;

public class ShapeScreen extends BaseScreen{
	// Left panel (GUI redesign E5, plan §2.2): x 0..188; shape dropdown at y 42..62, then the accordion
	// from y 64: one 12-px header per section (Origin first, then the shape's sections), the open
	// section's rows under its header. The right side (x 192..480) is for the preview in E6
	public static final int panelWidth = 188, accordionTop = 64, headerHeight = 12;
	public static final int basePropertiesX = 2;
	public static final int basePropertiesY = accordionTop + headerHeight;
	private static final int originRowHeight = Property.rowHeight, originRows = 4;

	private Translatable titleOrigin = new Translatable("screen.buildguide.origin");

	private DropdownOverlayScreen dropdownOverlayShapeSelect = new DropdownOverlayScreen(this, 0, 42, panelWidth, AbstractWidgetHandler.defaultSize, ShapeRegistry.getTranslatables(), BuildGuide.stateManager.getState().getCurrentShapeIndex(), (int selected) -> setShape(selected));
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
	// Fixed Validate (manual full rescan of the current shape), Reset (restores the defaults of the
	// properties shown right now, i.e. the open accordion section; nothing while Origin or no section
	// is open; control points are protected by the shapes themselves) and Preview (3D view of the current shape),
	// sharing the bottom bar row left of the validation bar: three 78-px buttons, 5..247
	private IButton buttonValidate = BuildGuide.widgetHandler.createButton(5, 250, 78, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.validate"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
	});
	private IButton buttonReset = BuildGuide.widgetHandler.createButton(87, 250, 78, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.reset"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().resetShownToDefaults();
	});
	private IButton buttonPreview = BuildGuide.widgetHandler.createButton(169, 250, 78, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.preview"), () -> {
		BuildGuide.screenHandler.showScreen(new PreviewScreen(this));
	});
	public void init() {
		super.init();
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
		addWidget(buttonPreview);
		buttonPreview.setActive(BuildGuide.stateManager.getState().isShapeAvailable());
		
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

		int nameWidth = panelWidth - AbstractWidgetHandler.defaultSize; // left of the dropdown's open button
		drawShadowCentred(BuildGuide.screenHandler.getFormattedShapeName(BuildGuide.stateManager.getState().getCurrentShapeSet()), nameWidth / 2, 48, BuildGuide.screenHandler.getShapeProgressColour(BuildGuide.stateManager.getState().getCurrentShape()));

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
	
	private void setOrigin() {		BuildGuide.stateManager.getState().resetOrigin();
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
