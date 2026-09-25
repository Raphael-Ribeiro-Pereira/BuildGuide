package brentmaas.buildguide.fabric.screen;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class ScreenWrapper extends Screen implements IScreenWrapper {
	private BaseScreen attachedScreen;
	private GuiGraphics guiGraphicsInstance;
	
	public ScreenWrapper(Component title) {
		super(title);
	}
	
	@Override
	public void init() {
		super.init();
		attachedScreen.init();
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		guiGraphicsInstance = guiGraphics;
		attachedScreen.render();
	}
	
	@Override
	public void renderTransparentBackground(GuiGraphics guiGraphics) {
		// Disable dark background
	}
	
	@Override
	public boolean isPauseScreen() {
		return attachedScreen.isPauseScreen();
	}
	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if(event.isEscape() && attachedScreen.onEscape()) return true;
		return super.keyPressed(event);
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if(super.mouseClicked(event, doubleClick)) return true;
		return attachedScreen.onMouseClicked(event.x(), event.y(), event.button(), doubleClick);
	}
	
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if(super.mouseDragged(event, dragX, dragY)) return true;
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
		Minecraft.getInstance().setScreen(this);
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
		guiGraphicsInstance.drawString(this.minecraft.font, text, x, y, ARGB.color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF), true);
	}
	
	public void fillRect(int x1, int y1, int x2, int y2, int colour) {
		guiGraphicsInstance.fill(x1, y1, x2, y2, colour);
	}
	
	// Submitted to the GUI render state; PreviewRenderer (registered in BuildGuideFabric) draws it into its own texture
	public void drawShapePreview(int x1, int y1, int x2, int y2, PreviewModel model, PreviewCamera camera) {
		guiGraphicsInstance.guiRenderState.submitPicturesInPictureState(new PreviewRenderState(model, camera.yaw, camera.pitch, camera.zoom, x1, y1, x2, y2, guiGraphicsInstance.scissorStack.peek()));
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
