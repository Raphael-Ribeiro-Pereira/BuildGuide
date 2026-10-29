package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.IButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ButtonImpl extends Button.Plain implements IButton {
	public void setVisibility(boolean visible) {
		this.visible = visible;
	}
	
	public void setActive(boolean active) {
		this.active = active;
	}
	
	@Override
	public void setTitle(Translatable title) {
		setMessage(Component.translatable(title.getTranslationKey(), title.getValues()));
	}
	
	private int style = IButton.STYLE_PLAIN;
	
	@Override
	public IButton setStyle(int style) {
		this.style = style;
		return this;
	}
	
	public ButtonImpl(int x, int y, int width, int height, Translatable text, IButton.IPressable onPress) {
		super(x, y, width, height, Component.translatable(text.getTranslationKey(), text.getValues()), button -> onPress.onPress(), DEFAULT_NARRATION);
	}
	
	// Flat look (Theme, the prototype's styles) instead of the stone sprite; the label without a shadow
	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		FlatStyle.button(graphics, style, getX(), getY(), getWidth(), getHeight(), getMessage().getString(), active, active && isHoveredOrFocused());
	}
	
	public void setYPosition(int y) {
		super.setY(y);
	}

	public void shiftX(int dx) {
		super.setX(getX() + dx);
	}
}
