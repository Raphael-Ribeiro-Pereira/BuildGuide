package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IWidget;

public class PropertyRunnable extends Property<Runnable> {
	private int xOffset = 0;
	private int width = rowWidth;
	
	public PropertyRunnable(Runnable value, Translatable name) {
		super(value, name);
	}
	
	// Narrower button at x + xOffset, so two buttons can share a row (e.g. Validate + Reset)
	public PropertyRunnable(Runnable value, Translatable name, int xOffset, int width) {
		super(value, name);
		this.xOffset = xOffset;
		this.width = width;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		widgetList.add(BuildGuide.widgetHandler.createButton(x + xOffset, y, width, rowHeight, name, () -> {
			this.value.run();
		}));
	}
	
	@Override
	public void render(BaseScreen screen) {
		
	}
	
	public String getStringValue() {
		return "Runnable";
	}
	
	public boolean setValueFromString(String value) {
		return true;
	}
}
