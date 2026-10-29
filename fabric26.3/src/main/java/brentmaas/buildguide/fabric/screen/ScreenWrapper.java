package brentmaas.buildguide.fabric.screen;

import com.mojang.blaze3d.platform.InputConstants;

import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.screen.widget.IShapeList;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.fabric.preview.PreviewRenderState;
import brentmaas.buildguide.fabric.screen.widget.ButtonImpl;
import brentmaas.buildguide.fabric.screen.widget.CheckboxRunnableButtonImpl;
import brentmaas.buildguide.fabric.screen.widget.SelectorListImpl;
import brentmaas.buildguide.fabric.screen.widget.ShapeListImpl;
import brentmaas.buildguide.fabric.screen.widget.SliderImpl;
import brentmaas.buildguide.fabric.screen.widget.TextFieldImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class ScreenWrapper extends Screen implements IScreenWrapper {
	private BaseScreen attachedScreen;
	private GuiGraphicsExtractor guiGraphicsInstance;
	
	public ScreenWrapper(Component title) {
		super(title);
	}
	
	@Override
	public void init() {
		super.init();
		attachedScreen.initChecked();
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		guiGraphicsInstance = guiGraphics;
		// The panel fills go under the widgets (the preview's black area, E6), the texts and the 3D view over them
		attachedScreen.renderBackgroundChecked();
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		attachedScreen.renderChecked();
	}
	
	// Live apply: text fields apply after a pause in typing; checked once per client tick, outside rendering,
	// on a copy (an applied value may rebuild the widgets)
	@Override
	public void tick() {
		super.tick();
		for(Object child: new java.util.ArrayList<Object>(children())) {
			if(child instanceof TextFieldImpl) ((TextFieldImpl) child).pollLiveApply();
		}
	}
	
	@Override
	public void extractTransparentBackground(GuiGraphicsExtractor guiGraphics) {
		// Disable dark background
	}
	
	@Override
	public boolean isPauseScreen() {
		return attachedScreen.isPauseScreen();
	}
	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if(event.isEscape() && !attachedScreen.isTooSmall() && attachedScreen.onEscape()) return true;
		return super.keyPressed(event);
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if(super.mouseClicked(event, doubleClick)) return true;
		if(attachedScreen.isTooSmall()) return false; // only the message and X (D1)
		return attachedScreen.onMouseClicked(event.x(), event.y(), glfwButton(event.button()), doubleClick);
	}

	// Minecraft 26.3 numbers the mouse buttons from 1 (InputConstants.MOUSE_BUTTON_LEFT 1, MIDDLE 2, RIGHT 3); common
	// keeps GLFW's numbers (BaseScreen.MOUSE_LEFT 0, right 1, MOUSE_MIDDLE 2). Without this a left click reached the
	// screens as a right click: the accordion headers, the name and the preview's drag ignored it
	static int glfwButton(int button) {
		if(button == InputConstants.MOUSE_BUTTON_LEFT) return 0;
		if(button == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
		if(button == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
		return button - 1; // the side buttons keep their order
	}
	
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if(super.mouseDragged(event, dragX, dragY)) return true;
		if(attachedScreen.isTooSmall()) return false;
		return attachedScreen.onMouseDragged(dragX, dragY);
	}
	
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		attachedScreen.onMouseReleased();
		return handled;
	}
	
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(super.mouseScrolled(x, y, scrollX, scrollY)) return true;
		if(attachedScreen.isTooSmall()) return false;
		return attachedScreen.onMouseScrolled(x, y, scrollY);
	}
	
	@Override
	public void onClose() {
		super.onClose();
		attachedScreen.onScreenClosed();
	}
	
	public void attachScreen(BaseScreen screen) {
		attachedScreen = screen;
		screen.setWrapper(this);
	}
	
	public void show() {
		Minecraft.getInstance().gui.setScreen(this);
	}
	
	public void addButton(IButton button) {
		addRenderableWidget((ButtonImpl) button);
	}
	
	public void addTextField(ITextField textField) {
		addRenderableWidget((TextFieldImpl) textField);
	}
	
	public void addCheckbox(ICheckboxRunnableButton checkbox) {
		((CheckboxRunnableButtonImpl) checkbox).initCheckboxIfNull();
		addRenderableWidget(((CheckboxRunnableButtonImpl) checkbox).checkbox);
	}
	
	public void addSlider(ISlider slider) {
		slider.updateText();
		addRenderableWidget((SliderImpl) slider);
	}
	
	public void addShapeList(IShapeList shapeList) {
		addRenderableWidget((ShapeListImpl) shapeList);
	}
	
	public void addSelectorList(ISelectorList selectorList) {
		addRenderableWidget((SelectorListImpl) selectorList);
	}
	
	public void drawShadow(String text, int x, int y, int colour) {
		guiGraphicsInstance.text(this.minecraft.font, text, x, y, ARGB.color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF), true);
	}
	
	// Flat text of the new interface: ARGB colour, no drop shadow
	@Override
	public void drawText(String text, int x, int y, int colour) {
		guiGraphicsInstance.text(this.minecraft.font, text, x, y, colour, false);
	}
	
	public void fillRect(int x1, int y1, int x2, int y2, int colour) {
		guiGraphicsInstance.fill(x1, y1, x2, y2, colour);
	}
	
	// Submitted to the GUI render state; PreviewRenderer (registered in BuildGuideFabric) draws it into its own texture
	public void drawShapePreview(int x1, int y1, int x2, int y2, PreviewModel model, PreviewCamera camera) {
		guiGraphicsInstance.guiRenderState.addPicturesInPictureState(new PreviewRenderState(model, camera.yaw, camera.pitch, camera.zoom, x1, y1, x2, y2, guiGraphicsInstance.scissorStack.peek()));
	}
	
	public int getTextWidth(String text) {
		return font.width(text);
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
}
