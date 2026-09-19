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

public abstract class BaseScreen {
	public static boolean shouldUpdatePersistence = false;
	
	protected Translatable title = new Translatable("screen.buildguide.title");
	protected Translatable titleNumberOfBlocksShape = new Translatable("screen.buildguide.numberofblocksshape");
	protected Translatable titleNumberOfBlocksTotal = new Translatable("screen.buildguide.numberofblockstotal");
	protected Translatable textEnabled = new Translatable("screen.buildguide.enable");
	protected IScreenWrapper wrapper;
	protected ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
	
	private IButton buttonClose;
	private ICheckboxRunnableButton buttonEnabled;
	private IButton buttonBuildGuide = BuildGuide.widgetHandler.createButton(5, 30, 96, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.shape"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Shape);
	private IButton buttonVisualisation = BuildGuide.widgetHandler.createButton(101, 30, 96, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.visualisation"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Visualisation)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Visualisation);
	private IButton buttonShapeList = BuildGuide.widgetHandler.createButton(197, 30, 96, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.shapelist"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shapelist)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Shapelist);
	private IButton buttonConfiguration = BuildGuide.widgetHandler.createButton(293, 30, 96, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.configuration"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Settings)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Settings);
	// Five 96-px tabs (5..485) fit a 480-px GUI; the upstream four 120-px ones ended at 500
	private IButton buttonExclusions = BuildGuide.widgetHandler.createButton(389, 30, 96, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.exclusions"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Exclusions)), BuildGuide.stateManager.getState().currentScreen != ActiveScreen.Exclusions);
	
	public void init() {
		buttonClose = BuildGuide.widgetHandler.createButton(wrapper.getWidth() - 25, 5, new Translatable("X"), () -> BuildGuide.screenHandler.showScreen(null));
		buttonEnabled = BuildGuide.widgetHandler.createCheckbox(5, 5, new Translatable(""), BuildGuide.stateManager.getState().isEnabled(), false, () -> {
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
		
		BuildGuide.stateManager.getState().initCheck();
	}
	
	public void render() {
		drawShadowCentred(title.toString(), wrapper.getWidth() / 2, 10, 0xFFFFFF);
		drawShadowLeft(textEnabled.toString(), 30, 10, 0xFFFFFF);
		
		int titlesMax = Math.max(wrapper.getTextWidth(titleNumberOfBlocksShape.toString()), wrapper.getTextWidth(titleNumberOfBlocksTotal.toString()));
		
		drawShadowCentred(titleNumberOfBlocksShape.toString(), wrapper.getWidth() / 2 - wrapper.getTextWidth(title.toString()) / 2 - titlesMax / 2 - 20, 5, 0xFFFFFF);
		int n = BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape().getNumberOfBlocks() : 0;
		drawShadowCentred(n + " (" + (n / 64) + " x 64 + " + (n % 64) + ")", wrapper.getWidth() / 2 - wrapper.getTextWidth(title.toString()) / 2 - titlesMax / 2 - 20, 20, 0xFFFFFF);
		
		drawShadowCentred(titleNumberOfBlocksTotal.toString(), wrapper.getWidth() / 2 + wrapper.getTextWidth(title.toString()) / 2 + titlesMax / 2 + 20, 5, 0xFFFFFF);
		int nTotal = BuildGuide.stateManager.getState().getNumberOfBlocks();
		drawShadowCentred(nTotal + " (" + (nTotal / 64) + " x 64 + " + (nTotal % 64) + ")", wrapper.getWidth() / 2 + wrapper.getTextWidth(title.toString()) / 2 + titlesMax / 2 + 20, 20, 0xFFFFFF);
		
		for(Property<?> p: properties) {
			p.render(this);
		}
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
}