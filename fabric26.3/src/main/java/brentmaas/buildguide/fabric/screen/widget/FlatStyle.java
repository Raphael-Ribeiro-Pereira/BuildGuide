package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Draws the flat widgets of the new interface (Theme): a filled box with a 1 px border
public class FlatStyle {
	public static void box(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
		graphics.fill(x, y, x + width, y + height, border);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
	}
	
	// Button colours: disabled, hovered (or focused), plain
	public static int buttonFill(boolean active, boolean hovered) {
		return !active ? Theme.CONTROL_DISABLED : hovered ? Theme.CONTROL_HOVER : Theme.CONTROL;
	}
	
	public static int buttonBorder(boolean active, boolean hovered) {
		return !active ? Theme.CONTROL_DISABLED : hovered ? Theme.BORDER_HOVER : Theme.BORDER;
	}
}
