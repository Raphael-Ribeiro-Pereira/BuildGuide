package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.Theme;
import brentmaas.buildguide.common.screen.widget.ISlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class SliderImpl extends AbstractSliderButton implements ISlider {
	private double min, max;
	private Translatable name;
	
	public SliderImpl(int x, int y, int width, int height, Translatable name, double min, double max, double value) {
		super(x, y, width, height, Component.literal(name + ": " + Math.round(10.0 * value) / 10.0), (value - min) / (max - min));
		this.name = name;
		this.min = min;
		this.max = max;
	}
	 
	public void setYPosition(int y) { 
		this.setY(y); 
	} 
	 
	public void shiftX(int dx) {
		this.setX(getX() + dx);
	}

	public void setVisibility(boolean visible) { 
		this.visible = visible; 
	} 
	
	public void updateText() {
		setMessage(Component.literal(name + ": " + Math.round(10.0 * getSliderValue()) / 10.0));
	}
	
	// Flat look (Theme): a track filled up to the value in the accent colour, a slim handle, the label centred
	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		boolean hovered = active && isHoveredOrFocused();
		FlatStyle.box(graphics, getX(), getY(), getWidth(), getHeight(), Theme.FIELD, FlatStyle.buttonBorder(active, hovered));
		int handleX = getX() + (int) (value * (getWidth() - 4));
		if(handleX > getX() + 1) graphics.fill(getX() + 1, getY() + 1, handleX, getY() + getHeight() - 1, active ? Theme.SELECTED : Theme.CONTROL_DISABLED);
		graphics.fill(handleX, getY() + 1, handleX + 4, getY() + getHeight() - 1, active ? (hovered ? Theme.ACCENT : Theme.ACCENT_DIM) : Theme.BORDER);
		String label = FlatStyle.fit(Minecraft.getInstance().font, getMessage().getString(), getWidth() - 6);
		graphics.text(Minecraft.getInstance().font, label, getX() + (getWidth() - Minecraft.getInstance().font.width(label) + 1) / 2, getY() + (getHeight() - 7) / 2, active ? Theme.TEXT : Theme.TEXT_FAINT, false);
	}
	
	protected void applyValue() {
		
	}
	
	public void updateMessage() {
		updateText();
	}
	
	public void setSliderValue(double value) {
		this.value = (value - min) / (max - min);
	}
	
	public double getSliderValue() {
		return value * (max - min) + min;
	}
	
	public void setActive(boolean active) {
		this.active = active;
	}
}
