package brentmaas.buildguide.common.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

public interface IButton extends IWidget {
	// Looks of the prototype (the Oficina, 2026-10-28): PLAIN the boxed button; GHOST a list row (left text, lit
	// on hover), GHOST_SELECTED its chosen row; RAIL / RAIL_SELECTED the section rail; SOFT a light accent action;
	// PRIMARY the filled accent call to action; OUTLINE a secondary action. Loaders without them draw PLAIN
	public static final int STYLE_PLAIN = 0, STYLE_GHOST = 1, STYLE_GHOST_SELECTED = 2, STYLE_RAIL = 3, STYLE_RAIL_SELECTED = 4, STYLE_SOFT = 5, STYLE_PRIMARY = 6, STYLE_OUTLINE = 7;

	public void setActive(boolean active);

	public default IButton setStyle(int style) {
		return this;
	}
	
	// Change the label (E8: the preset menu's two-click confirmation). Default: no-op, so the 36 loader
	// ButtonImpls (and DropdownOverlayScreen) still compile; only the built Fabric one implements it
	public default void setTitle(Translatable title) {}
	
	public interface IPressable{
		public void onPress();
	}
}