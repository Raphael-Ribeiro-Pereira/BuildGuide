package brentmaas.buildguide.common.screen.widget;

public interface ITextField extends IWidget {
	public void setTextValue(String text);
	
	public void setTextColour(int colour);
	
	public String getTextValue();
	
	// Run when Enter is pressed while the field has focus (replaces the Set buttons, GUI redesign E4).
	// Default no-op: only the loaders that implement it (fabric1.21.11) apply values with Enter
	public default void setOnEnter(Runnable onEnter) {}
}
