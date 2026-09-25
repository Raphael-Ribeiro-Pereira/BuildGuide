package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * A PropertyInt laid out as one of several narrow columns on a single row, so that e.g.
 * X, Y and Z can share a line. Persists exactly like PropertyInt. It draws no label and
 * has no Set button of its own: the row owner (see PropertyPointRow) draws the label and
 * commits the text fields of all its columns at once.
 */
public class PropertyCompactInt extends PropertyInt {
	// Point row (GUI redesign E4, D2b): label 14 · 3 × (− 10 · field 32 · + 10) · capture 14 = 184 px.
	// A 32-px field shows 4 characters (-120); longer values scroll inside it
	public static final int columnOffset = 14;
	public static final int columnWidth = 52;
	private static final int buttonWidth = 10;
	private static final int textFieldWidth = 32;

	private int column;

	public PropertyCompactInt(int value, Translatable name, Runnable onPress, int column) {
		super(value, name, onPress);
		this.column = column;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		int cx = x + columnOffset + column * columnWidth;
		widgetList.add(BuildGuide.widgetHandler.createButton(cx, y, buttonWidth, rowHeight, new Translatable("-"), () -> {
			--this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
		valueTextField = BuildGuide.widgetHandler.createTextField(cx + buttonWidth, y, textFieldWidth, rowHeight, "");
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		widgetList.add(valueTextField);
		widgetList.add(BuildGuide.widgetHandler.createButton(cx + buttonWidth + textFieldWidth, y, buttonWidth, rowHeight, new Translatable("+"), () -> {
			++this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
	}

	// Enter in this column's field; the row owner commits all three columns with one update
	public void setOnEnter(Runnable onEnter) {
		getWidgetList(); // Initialise `valueTextField` if still null
		valueTextField.setOnEnter(onEnter);
	}
	
	/**
	 * Parse the text field into the value without running onPress; the caller decides when
	 * to update. Returns false (and marks the field red) if the text is not an integer.
	 */
	public boolean commitTextField() {
		getWidgetList(); // Initialise `valueTextField` if still null
		try {
			this.value = Integer.parseInt(valueTextField.getTextValue());
			valueTextField.setTextColour(0xFFFFFF);
			BaseScreen.shouldUpdatePersistence = true;
			return true;
		}catch(NumberFormatException e) {
			valueTextField.setTextColour(0xFF0000);
			return false;
		}
	}

	@Override
	public void render(BaseScreen screen) {
		// The row owner draws the label
	}
}
