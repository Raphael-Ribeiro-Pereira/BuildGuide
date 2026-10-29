package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.widget.FieldDebounce;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

public class TextFieldImpl extends EditBox implements ITextField {
	private Runnable onEnter;
	// Live apply: the Enter action also runs by itself after a pause in typing or on focus loss
	private final FieldDebounce debounce = new FieldDebounce(System::currentTimeMillis);
	// EditBox.setValue calls the responder too: a change made by code must not count as typing
	private boolean settingByCode = false;
	// Flat look (Theme): the vanilla box is unbordered and sits inside our own box, inset by the padding the
	// bordered EditBox would have, so the text stays where it was; clicks count on the whole outer box
	private static final int padX = 4;
	private final int padY;

	public TextFieldImpl(int x, int y, int width, int height, String value) {
		super(Minecraft.getInstance().font, x + padX, y + (height - 8) / 2, width - 2 * padX, height - 2 * ((height - 8) / 2), Component.literal(value));
		padY = (height - 8) / 2;
		setBordered(false);
		setTextShadow(false);
		setResponder(text -> {
			if(!settingByCode) debounce.onUserEdit(text);
		});
	}

	public void setTextValue(String text) {
		settingByCode = true;
		try {
			setValue(text);
		}finally {
			settingByCode = false;
		}
		debounce.onProgrammaticSet(getValue());
	}

	public void setTextColour(int colour) {
		setTextColor(ARGB.color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF));
	}

	public void setVisibility(boolean visible) {
		setVisible(visible);
	}

	public void setYPosition(int y) {
		super.setY(y + padY);
	}

	public void shiftX(int dx) {
		super.setX(getX() + dx);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return isVisible() && active && mouseX >= getX() - padX && mouseX < getX() + getWidth() + padX && mouseY >= getY() - padY && mouseY < getY() + getHeight() + padY;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		if(!isVisible()) return;
		int border = isFocused() ? Theme.ACCENT_DIM : isMouseOver(mouseX, mouseY) ? Theme.BORDER_HOVER : Theme.BORDER;
		FlatStyle.box(graphics, getX() - padX, getY() - padY, getWidth() + 2 * padX, getHeight() + 2 * padY, Theme.FIELD, border);
		super.renderWidget(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public void setHint(String hint) {
		setHint(Component.literal(hint).withColor(Theme.TEXT_FAINT & 0xFFFFFF));
	}

	public String getTextValue() {
		return getValue();
	}

	public void setOnEnter(Runnable onEnter) {
		this.onEnter = onEnter;
	}

	@Override
	public void markApplied() {
		debounce.markApplied(getValue());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Only reached while focused (the screen forwards keys to its focused child)
		if(onEnter != null && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			debounce.markApplied(getValue());
			onEnter.run();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void setFocused(boolean focused) {
		boolean had = isFocused();
		super.setFocused(focused);
		if(had && !focused && onEnter != null && debounce.onFocusLost(getValue())) onEnter.run();
	}

	// Called by ScreenWrapper.tick (outside rendering, so the action may rebuild widgets): applies the text
	// once the typing pause is over
	public void pollLiveApply() {
		if(onEnter != null && debounce.poll(getValue())) onEnter.run();
	}
}
