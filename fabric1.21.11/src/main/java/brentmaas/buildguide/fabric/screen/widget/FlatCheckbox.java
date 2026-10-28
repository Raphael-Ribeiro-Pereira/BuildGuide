package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

// The checkbox of the new interface (Theme): a flat box with an accent fill and a tick when selected, the title (if
// any) to its right. Vanilla's Checkbox cannot be subclassed from here (package-private constructor)
public class FlatCheckbox extends AbstractButton {
	public static final int boxSize = 17;
	private boolean selected;
	private final Runnable onValueChange;
	
	public FlatCheckbox(int x, int y, Component title, boolean selected, Runnable onValueChange) {
		super(x, y, boxSize + (title.getString().isEmpty() ? 0 : 4 + Minecraft.getInstance().font.width(title)), boxSize, title);
		this.selected = selected;
		this.onValueChange = onValueChange;
	}
	
	public boolean selected() {
		return selected;
	}
	
	@Override
	public void onPress(InputWithModifiers input) {
		selected = !selected;
		onValueChange.run();
	}
	
	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		boolean hovered = active && isHoveredOrFocused();
		int x = getX(), y = getY();
		int fill = !active ? Theme.CONTROL_DISABLED : selected ? Theme.ACCENT_DIM : hovered ? Theme.CONTROL_HOVER : Theme.FIELD;
		FlatStyle.box(graphics, x, y, boxSize, boxSize, fill, selected && active ? Theme.ACCENT : FlatStyle.buttonBorder(active, hovered));
		if(selected) {
			// A tick drawn from short runs: down-right from (4, 8), then up-right to (12, 4)
			int tick = active ? Theme.TEXT : Theme.TEXT_FAINT;
			for(int i = 0;i < 4;++i) graphics.fill(x + 4 + i, y + 8 + i, x + 6 + i, y + 10 + i, tick);
			for(int i = 0;i < 6;++i) graphics.fill(x + 7 + i, y + 10 - i, x + 9 + i, y + 12 - i, tick);
		}
		if(!getMessage().getString().isEmpty()) graphics.drawString(Minecraft.getInstance().font, getMessage(), x + boxSize + 4, y + (boxSize - 8) / 2, active ? Theme.TEXT : Theme.TEXT_FAINT);
	}
	
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
