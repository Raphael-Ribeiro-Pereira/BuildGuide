package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.IButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
	
	public ButtonImpl(int x, int y, int width, int height, Translatable text, IButton.IPressable onPress) {
		super(x, y, width, height, Component.translatable(text.getTranslationKey(), text.getValues()), button -> onPress.onPress(), DEFAULT_NARRATION);
	}
	
	// Flat look (Theme) instead of the stone sprite; the label as vanilla draws it
	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		boolean hovered = active && isHoveredOrFocused();
		FlatStyle.box(graphics, getX(), getY(), getWidth(), getHeight(), FlatStyle.buttonFill(active, hovered), FlatStyle.buttonBorder(active, hovered));
		extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
	}
	
	public void setYPosition(int y) {
		super.setY(y);
	}

	public void shiftX(int dx) {
		super.setX(getX() + dx);
	}
}
