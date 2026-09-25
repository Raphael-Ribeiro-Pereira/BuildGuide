package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.widget.IWidget;

public abstract class Property<T> {
	// Compact rows (GUI redesign E4): 18 px high, 184 px wide. Label x+2..x+78, controls from x+80:
	// step button 14 Â· field 76 Â· step button 14 (Enum/Section: arrow Â· value Â· arrow)
	public static final int rowHeight = 18, rowWidth = 184, labelX = 2, controlX = 80, stepWidth = 14, fieldWidth = 76;
	public static final int fieldX = controlX + stepWidth, increaseX = fieldX + fieldWidth;
	
	protected int x;
	protected int y;
	public T value;
	protected Translatable name;
	private ArrayList<IWidget> widgetList = null;
	protected boolean visible = true;
	
	public Property(T value, Translatable name) {
		x = ShapeScreen.basePropertiesX;
		y = ShapeScreen.basePropertiesY;
		this.value = value;
		this.name = name;
	}
	
	protected abstract void initWidgets(ArrayList<IWidget> widgetList);
	
	public ArrayList<IWidget> getWidgetList() {
		if(widgetList == null) {
			widgetList = new ArrayList<IWidget>();
			initWidgets(widgetList);
		}
		return widgetList;
	}
	
	public void addToScreen(BaseScreen screen) {
		for(IWidget widget: getWidgetList()) {
			screen.addWidget(widget);
		}
	}
	
	public void setValue(T value) {
		this.value = value;
		BaseScreen.shouldUpdatePersistence = true;
	}
	
	public abstract String getStringValue();
	
	public abstract boolean setValueFromString(String value);
	
	public void setName(Translatable name) {
		this.name = name;
	}
	
	public void setX(int x) {
		this.x = x;
	}
	
	public void setY(int y) {
		this.y = y;
		for(IWidget widget: getWidgetList()) {
			widget.setYPosition(y);
		}
	}
	
	public void setVisibility(boolean visible) {
		for(IWidget widget: getWidgetList()) {
			widget.setVisibility(visible);
		}
		this.visible = visible;
	}
	
	public void render(BaseScreen screen) {
		drawString(screen, name.toString(), x + labelX, y + 5, 0xFFFFFF);
	}
	
	public void drawString(BaseScreen screen, String text, int x, int y, int colour) {
		if(visible) screen.drawShadowLeft(text, x, y, 0xFFFFFF);
	}
	
	public void drawStringCentred(BaseScreen screen, String text, int x, int y, int colour) {
		if(visible) screen.drawShadowCentred(text, x, y, 0xFFFFFF);
	}
}
