package brentmaas.buildguide.common.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

public interface IButton extends IWidget {
	public void setActive(boolean active);
	
	// Change the label (E8: the preset menu's two-click confirmation). Default: no-op, so the 36 loader
	// ButtonImpls (and DropdownOverlayScreen) still compile; only the built Fabric one implements it
	public default void setTitle(Translatable title) {}
	
	public interface IPressable{
		public void onPress();
	}
}