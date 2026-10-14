package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * Only the label of a row whose controls are other properties laid out on the same y (the Island's cuts,
 * PropertyCompactRangeInt). No widgets and no value: add it with Shape.addGuiOnly, never to the persisted list.
 */
public class PropertyRowLabel extends Property<Void> {
	public PropertyRowLabel(Translatable name) {
		super(null, name);
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {}

	public String getStringValue() {
		return "";
	}

	public boolean setValueFromString(String value) {
		return true;
	}
}
