package brentmaas.buildguide.common.screen.widget;

public interface IWidget {
	public void setYPosition(int y);

	// Moves the widget sideways by dx (the Oficina layout, 2026-10-27: the inspector sits at the right edge, so a
	// property's widgets follow its x when the screen width changes). A widget that cannot move ignores it
	public default void shiftX(int dx) {
	}
	
	public void setVisibility(boolean visible);
}
