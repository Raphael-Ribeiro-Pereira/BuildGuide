package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.Theme;
import brentmaas.buildguide.common.screen.widget.IButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Draws the flat widgets of the new interface (Theme): rounded boxes (the corner pixels left out), labels without
// the Minecraft drop shadow, and the colours of each button style of the prototype
public class FlatStyle {
	// A rounded box; fill or border 0 leaves that part out (an outline only, or a fill only)
	public static void box(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
		if(border != 0) {
			graphics.fill(x + 1, y, x + width - 1, y + 1, border);
			graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, border);
			graphics.fill(x, y + 1, x + 1, y + height - 1, border);
			graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, border);
			if(fill != 0) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
		}else if(fill != 0) {
			graphics.fill(x + 1, y, x + width - 1, y + height, fill);
			graphics.fill(x, y + 1, x + 1, y + height - 1, fill);
			graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, fill);
		}
	}
	
	// Button colours: disabled, hovered (or focused), plain
	public static int buttonFill(boolean active, boolean hovered) {
		return !active ? Theme.CONTROL_DISABLED : hovered ? Theme.CONTROL_HOVER : Theme.CONTROL;
	}
	
	public static int buttonBorder(boolean active, boolean hovered) {
		return !active ? Theme.CONTROL_DISABLED : hovered ? Theme.BORDER_HOVER : Theme.BORDER;
	}
	
	// A button of the given style: its box, then its label (cut with ".." to the width), centred or at the left
	public static void button(GuiGraphicsExtractor graphics, int style, int x, int y, int width, int height, String label, boolean active, boolean hovered) {
		int fill, border = 0, text;
		boolean left = false;
		switch(style) {
		case IButton.STYLE_GHOST:
			fill = hovered ? Theme.CONTROL : 0;
			text = active ? (hovered ? Theme.TEXT : Theme.TEXT_SOFT) : Theme.TEXT_FAINT;
			left = true;
			break;
		case IButton.STYLE_GHOST_SELECTED:
			fill = Theme.SELECTED;
			text = Theme.TEXT;
			left = true;
			break;
		case IButton.STYLE_RAIL:
			fill = hovered ? Theme.CONTROL : 0;
			text = hovered ? Theme.TEXT_SOFT : Theme.TEXT_MUTED;
			break;
		case IButton.STYLE_RAIL_SELECTED:
			fill = Theme.SELECTED;
			text = Theme.ACCENT;
			break;
		case IButton.STYLE_SOFT:
			fill = active ? (hovered ? Theme.SELECTED_HOVER : Theme.SELECTED) : Theme.CONTROL_DISABLED;
			text = active ? Theme.ACCENT : Theme.TEXT_FAINT;
			break;
		case IButton.STYLE_PRIMARY:
			fill = active ? (hovered ? Theme.ACCENT_HOVER : Theme.ACCENT) : Theme.CONTROL_DISABLED;
			text = active ? Theme.ON_ACCENT : Theme.TEXT_FAINT;
			break;
		case IButton.STYLE_OUTLINE:
			fill = hovered ? Theme.CONTROL : 0;
			border = active && hovered ? Theme.BORDER_HOVER : Theme.BORDER;
			text = active ? Theme.TEXT_SOFT : Theme.TEXT_FAINT;
			break;
		case IButton.STYLE_SEGMENT:
			fill = hovered ? Theme.CONTROL : 0;
			text = active ? (hovered ? Theme.TEXT_SOFT : Theme.TEXT_MUTED) : Theme.TEXT_FAINT;
			break;
		case IButton.STYLE_SEGMENT_ON:
			fill = Theme.SEGMENT_ON;
			text = Theme.TEXT;
			break;
		case IButton.STYLE_TOGGLE_ON:
		case IButton.STYLE_TOGGLE_OFF:
		case IButton.STYLE_ROW_ACTION:
			fill = hovered ? Theme.CONTROL : 0;
			text = active ? Theme.TEXT_SOFT : Theme.TEXT_FAINT;
			left = true;
			break;
		default:
			fill = buttonFill(active, hovered);
			border = buttonBorder(active, hovered);
			text = active ? Theme.TEXT : Theme.TEXT_FAINT;
		}
		box(graphics, x, y, width, height, fill, border);
		Font font = Minecraft.getInstance().font;
		// "Label	right part": a pill (toggles) or an accent word (row actions) at the right end
		int tab = label.indexOf('	'), rightWidth = 0;
		if(tab >= 0) {
			String right = label.substring(tab + 1);
			label = label.substring(0, tab);
			rightWidth = font.width(right) + (style == IButton.STYLE_ROW_ACTION ? 6 : 12);
			int ry = y + (height - 7) / 2;
			if(style == IButton.STYLE_ROW_ACTION) {
				graphics.text(font, right, x + width - 5 - font.width(right), ry, active ? Theme.ACCENT : Theme.TEXT_FAINT, false);
			}else {
				boolean on = style == IButton.STYLE_TOGGLE_ON;
				int px = x + width - 3 - rightWidth + 6;
				box(graphics, px, y + (height - 11) / 2, rightWidth - 6, 11, on ? Theme.PILL_ON : Theme.CONTROL, 0);
				graphics.text(font, right, px + 3, ry, on ? Theme.OK : Theme.TEXT_MUTED, false);
			}
		}
		String shown = fit(font, label, width - (left ? 10 : 6) - rightWidth);
		int tx = left ? x + 6 : x + (width - font.width(shown) + 1) / 2;
		int ty = left && height > 18 ? y + 3 : y + (height - 7) / 2;
		graphics.text(font, shown, tx, ty, text, false);
	}
	
	public static String fit(Font font, String text, int width) {
		if(font.width(text) <= width) return text;
		while(text.length() > 1 && font.width(text + "..") > width) text = text.substring(0, text.length() - 1);
		return text + "..";
	}
}
