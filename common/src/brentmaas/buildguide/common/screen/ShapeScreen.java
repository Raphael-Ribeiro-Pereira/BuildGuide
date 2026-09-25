package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeRegistry;

public class ShapeScreen extends BaseScreen{
	public static final int basePropertiesX = 180;
	public static final int basePropertiesY = 42;
	
	private Translatable titleOrigin = new Translatable("screen.buildguide.origin");
	private Translatable titleShape = new Translatable("screen.buildguide.shape");
	
	private DropdownOverlayScreen dropdownOverlayShapeSelect = new DropdownOverlayScreen(this, 5, 55, 160, AbstractWidgetHandler.defaultSize, ShapeRegistry.getTranslatables(), BuildGuide.stateManager.getState().getCurrentShapeIndex(), (int selected) -> setShape(selected));
	private IButton buttonOrigin = BuildGuide.widgetHandler.createButton(5, 100, 160, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.setorigin"), () -> setOrigin());
	private IButton buttonOriginXDecrease = BuildGuide.widgetHandler.createButton(25, 120, new Translatable("-"), () -> shiftOrigin(-1, 0, 0));
	private IButton buttonOriginXIncrease = BuildGuide.widgetHandler.createButton(145, 120, new Translatable("+"), () -> shiftOrigin(1, 0, 0));
	private IButton buttonOriginYDecrease = BuildGuide.widgetHandler.createButton(25, 140, new Translatable("-"), () -> shiftOrigin(0, -1, 0));
	private IButton buttonOriginYIncrease = BuildGuide.widgetHandler.createButton(145, 140, new Translatable("+"), () -> shiftOrigin(0, 1, 0));
	private IButton buttonOriginZDecrease = BuildGuide.widgetHandler.createButton(25, 160, new Translatable("-"), () -> shiftOrigin(0, 0, -1));
	private IButton buttonOriginZIncrease = BuildGuide.widgetHandler.createButton(145, 160, new Translatable("+"), () -> shiftOrigin(0, 0, 1));
	private ITextField textFieldX = BuildGuide.widgetHandler.createTextField(45, 120, "");
	private ITextField textFieldY = BuildGuide.widgetHandler.createTextField(45, 140, "");
	private ITextField textFieldZ = BuildGuide.widgetHandler.createTextField(45, 160, "");
	// Fixed Validate (manual full rescan of the current shape), Reset (restores the defaults of the
	// properties shown right now: current section, or all when the shape has no sections; control
	// points are protected by the shapes themselves) and Preview (3D view of the current shape),
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
	private IButton buttonSetX = BuildGuide.widgetHandler.createButton(115, 120, 30, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		try {
			int newval = Integer.parseInt(textFieldX.getTextValue());
			BuildGuide.stateManager.getState().setOriginX(newval);
		}catch(NumberFormatException e) {
			textFieldX.setTextColour(0xFF0000);
		}
	});
	private IButton buttonSetY = BuildGuide.widgetHandler.createButton(115, 140, 30, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		try {
			int newval = Integer.parseInt(textFieldY.getTextValue());
			BuildGuide.stateManager.getState().setOriginY(newval);
		}catch(NumberFormatException e) {
			textFieldY.setTextColour(0xFF0000);
		}
	});
	private IButton buttonSetZ = BuildGuide.widgetHandler.createButton(115, 160, 30, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		try {
			int newval = Integer.parseInt(textFieldZ.getTextValue());
			BuildGuide.stateManager.getState().setOriginZ(newval);
		}catch(NumberFormatException e) {
			textFieldZ.setTextColour(0xFF0000);
		}
	});
	
	public void init() {
		super.init();
		
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) {
			dropdownOverlayShapeSelect.setActive(false);
			buttonOrigin.setActive(false);
			buttonOriginXDecrease.setActive(false);
			buttonOriginXIncrease.setActive(false);
			buttonOriginYDecrease.setActive(false);
			buttonOriginYIncrease.setActive(false);
			buttonOriginZDecrease.setActive(false);
			buttonOriginZIncrease.setActive(false);
			buttonSetX.setActive(false);
			buttonSetY.setActive(false);
			buttonSetZ.setActive(false);
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
		addWidget(buttonSetX);
		addWidget(buttonOriginXIncrease);
		addWidget(buttonOriginYDecrease);
		addWidget(textFieldY);
		addWidget(buttonSetY);
		addWidget(buttonOriginYIncrease);
		addWidget(buttonOriginZDecrease);
		addWidget(textFieldZ);
		addWidget(buttonSetZ);
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
			BuildGuide.stateManager.getState().getCurrentShape().onSelectedInGUI();
		}
	}
	
	public void render() {
		super.render();
		
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleShape, 85, 42, 0xFFFFFF);
		drawShadowCentred(BuildGuide.screenHandler.getFormattedShapeName(BuildGuide.stateManager.getState().getCurrentShapeSet()), 85, 60, BuildGuide.screenHandler.getShapeProgressColour(BuildGuide.stateManager.getState().getCurrentShape()));
		
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleOrigin, 85, 85, 0xFFFFFF);
		drawShadowLeft("X", 10, 125, BuildGuide.stateManager.getState().isShapeAvailable() ? 0xFFFFFF : 0x444444);
		drawShadowLeft("Y", 10, 145, BuildGuide.stateManager.getState().isShapeAvailable() ? 0xFFFFFF : 0x444444);
		drawShadowLeft("Z", 10, 165, BuildGuide.stateManager.getState().isShapeAvailable() ? 0xFFFFFF : 0x444444);
		
		
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
		
		BuildGuide.stateManager.getState().getCurrentShape().onSelectedInGUI();
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
