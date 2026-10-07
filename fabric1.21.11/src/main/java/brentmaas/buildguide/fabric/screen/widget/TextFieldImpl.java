package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.widget.FieldDebounce;
import brentmaas.buildguide.common.screen.widget.ITextField;
import net.minecraft.client.Minecraft;
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

	public TextFieldImpl(int x, int y, int width, int height, String value) {
		super(Minecraft.getInstance().font, x, y, width, height, Component.literal(value));
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
		super.setY(y);
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
