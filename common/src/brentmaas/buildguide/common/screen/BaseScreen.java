package brentmaas.buildguide.common.screen;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.screen.widget.IShapeList;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;

public abstract class BaseScreen {
	public static boolean shouldUpdatePersistence = false;
	// Layout bands (GUI redesign E3, design size 480 x 270): header y 0..20, tabs 20..40,
	// content 40..250, bottom bar 250..270 on the tabs that have one
	public static final int headerTextY = 6, headerGap = 10;
	// Bottom bar: validation progress right of the Shape tab's buttons (they end at x 247)
	public static final int barX1 = 251, barX2 = 400, barY1 = 252, barY2 = 258, barTextY = 260;
	
	protected Translatable title = new Translatable("screen.buildguide.title");
	protected Translatable titleNumberOfBlocksShape = new Translatable("screen.buildguide.numberofblocksshape");
	protected Translatable titleNumberOfBlocksTotal = new Translatable("screen.buildguide.numberofblockstotal");
	protected Translatable textEnabled = new Translatable("screen.buildguide.enable");
	protected IScreenWrapper wrapper;
	protected ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
	
	private IButton buttonClose;
	private ICheckboxRunnableButton buttonEnabled;
	private IButton buttonBuildGuide = BuildGuide.widgetHandler.createButton(0, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.shape"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Shape);
	private IButton buttonVisualisation = BuildGuide.widgetHandler.createButton(80, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.visualisation"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Visualisation)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Visualisation);
	private IButton buttonShapeList = BuildGuide.widgetHandler.createButton(160, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.shapelist"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shapelist)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Shapelist);
	private IButton buttonConfiguration = BuildGuide.widgetHandler.createButton(240, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.configuration"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Settings)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Settings);
	// Six 80-px tabs (0..480, y 20..40) fill a 480-px GUI; the upstream four 120-px ones ended at 500
	private IButton buttonExclusions = BuildGuide.widgetHandler.createButton(320, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.exclusions"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Exclusions)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Exclusions);
	private IButton buttonValidation = BuildGuide.widgetHandler.createButton(400, 20, 80, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.validation"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Validation)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Validation);
	
	public void init() {
		// A screen object can be shown again (back from a dropdown or the preview): its properties are
		// re-added below, so drop the ones of the previous init instead of rendering them twice
		properties.clear();
		// One-line header (GUI redesign E3): checkbox and close button in the corners of y 0..20
		buttonClose =BuildGuide.widgetHandler.createButton(wrapper.getWidth() - AbstractWidgetHandler.defaultSize, 0, new Translatable("X"), () -> BuildGuide.screenHandler.showScreen(null));
		buttonEnabled = BuildGuide.widgetHandler.createCheckbox(0, 0, new Translatable(""), BuildGuide.stateManager.getState().isEnabled(), false, () -> {
			BuildGuide.stateManager.getState().setEnabled(buttonEnabled.isCheckboxSelected());
			BaseScreen.shouldUpdatePersistence = true;
		});
		
		addWidget(buttonEnabled);
		addWidget(buttonClose);
		addWidget(buttonBuildGuide);
		addWidget(buttonVisualisation);
		addWidget(buttonShapeList);
		addWidget(buttonConfiguration);
		addWidget(buttonExclusions);
		addWidget(buttonValidation);
		
		BuildGuide.stateManager.getState().initCheck();
	}
	
	public void render() {
		// One-line header (D4): "Enabled", the title in the centre, the shape's block count ending just
		// left of the title and the total starting just right of it (the x 64 + n breakdown is gone)
		int centre = wrapper.getWidth() / 2, halfTitle = wrapper.getTextWidth(title.toString()) / 2;
		drawShadowCentred(title.toString(), centre, headerTextY, 0xFFFFFF);
		drawShadowLeft(textEnabled.toString(), 25, headerTextY, 0xFFFFFF);
		int n = BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape().getNumberOfBlocks() : 0;
		drawShadowRight(titleNumberOfBlocksShape + ": " + n, centre - halfTitle - headerGap, headerTextY, 0xFFFFFF);
		drawShadowLeft(titleNumberOfBlocksTotal + ": " + BuildGuide.stateManager.getState().getNumberOfBlocks(), centre + halfTitle + headerGap, headerTextY, 0xFFFFFF);

		for(Property<?> p: properties) {
			p.render(this);
		}
		if(hasBottomBar()) renderBottomBar();
	}
	
	// Screens that use y 250..270 themselves (dropdowns, preview, visualisation until E7) return false
	protected boolean hasBottomBar() {
		return true;
	}
	
	// Validation progress of the current shape (moved from ShapeScreen, GUI redesign E3): bar and
	// "ok / total (p%)" text. Never scanned shows "- / total" (the total is known from the expected blocks)
	private void renderBottomBar() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) {
			drawShadowLeft("-", barX1, barTextY, 0x888888);
			return;
		}
		Shape shape = BuildGuide.stateManager.getState().getCurrentShape(); // every Shape is IValidatable
		ValidationState state = shape.getValidationState();
		fillRect(barX1, barY1, barX2, barY2, 0xFF303030);
		if(!state.isValidated()) {
			drawShadowLeft("- / " + shape.getExpectedBlocks().size(), barX1, barTextY, 0xAAAAAA);
			return;
		}
		int ok = state.getOk(), total = state.getTotal(), errors = state.getNearCount();
		double progress = state.getProgress();
		int fillX2 = barX1 + (int) Math.round((barX2 - barX1) * progress);
		if(fillX2 > barX1) fillRect(barX1, barY1, fillX2, barY2, ok == total ? 0xFF40C040 : 0xFF40A0FF);
		String text = ok + " / " + total + " (" + String.format(java.util.Locale.ROOT, "%.1f", 100.0 * progress) + "%)";
		// Structure errors: solid blocks deforming the shape (Etapa 2.5)
		if(errors > 0) text += "  errors " + errors;
		drawShadowLeft(text, barX1, barTextY, errors > 0 ? 0xFF8080 : 0xFFFFFF);
	}
	
	public void setWrapper(IScreenWrapper wrapper) {
		this.wrapper = wrapper;
	}
	
	public void addWidget(IWidget widget) {
		if(wrapper != null) {
			if(widget instanceof IButton) {
				wrapper.addButton((IButton) widget);
			} else if(widget instanceof ITextField) {
				wrapper.addTextField((ITextField) widget);
			} else if(widget instanceof ICheckboxRunnableButton) {
				wrapper.addCheckbox((ICheckboxRunnableButton) widget);
			} else if(widget instanceof ISlider) {
				wrapper.addSlider((ISlider) widget);
			} else if(widget instanceof IShapeList) {
				wrapper.addShapeList((IShapeList) widget);
			} else if(widget instanceof ISelectorList) {
				wrapper.addSelectorList((ISelectorList) widget);
			}
		}
	}
	
	public void drawShadowLeft(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x, y, colour);
	}
	
	public void drawShadowCentred(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x - wrapper.getTextWidth(text) / 2, y, colour);
	}
	
	public void drawShadowRight(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x - wrapper.getTextWidth(text), y, colour);
	}
	
	public void fillRect(int x1, int y1, int x2, int y2, int colour) {
		if(wrapper != null) wrapper.fillRect(x1, y1, x2, y2, colour);
	}
	
	protected void addProperty(Property<?> p) {
		properties.add(p);
		p.addToScreen(this);
	}
	
	protected void addDropdownOverlayScreen(DropdownOverlayScreen dropdown) {
		addWidget(dropdown.getOpenButton());
	}
	
	public void onScreenClosed() {
		if(BuildGuide.config.persistenceEnabled.value && shouldUpdatePersistence) {
			try {
				BuildGuide.stateManager.savePersistence();
				shouldUpdatePersistence = false;
			}catch(Exception e) {
				BuildGuide.logHandler.sendChatMessage("Build Guide persistence failed to save: " + e.getMessage());
				BuildGuide.logHandler.error(e.getMessage() + "\n" + e.getStackTrace());
			}
		}
	}
	
	public boolean isPauseScreen() {
		return false;
	}

	// Escape pressed: return true to handle it here (the default closes the whole GUI)
	public boolean onEscape() {
		return false;
	}

	// Mouse events that no widget took, GUI coordinates; return true if handled. Buttons use the
	// loader's numbering (GLFW: 0 left, 1 right, 2 middle)
	public static final int MOUSE_LEFT = 0, MOUSE_MIDDLE = 2;

	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		return false;
	}

	// Drag by (dx, dy) GUI pixels after a click this screen handled
	public boolean onMouseDragged(double dx, double dy) {
		return false;
	}

	// Any button released (also after widget clicks, so a drag state can always end)
	public void onMouseReleased() {
	}

	// Scroll wheel at (x, y); amount > 0 is away from the user
	public boolean onMouseScrolled(double x, double y, double amount) {
		return false;
	}
}