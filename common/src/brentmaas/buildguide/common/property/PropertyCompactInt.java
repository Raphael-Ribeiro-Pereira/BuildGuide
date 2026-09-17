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
	public static final int columnOffset = 50;
	public static final int columnWidth = 62;
	private static final int buttonWidth = 16;
	private static final int textFieldWidth = 30;

	private int column;

	public PropertyCompactInt(int value, Translatable name, Runnable onPress, int column) {
		super(value, name, onPress);
		this.column = column;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		int cx = x + columnOffset + column * columnWidth;
		widgetList.add(BuildGuide.widgetHandler.createButton(cx, y, buttonWidth, AbstractWidgetHandler.defaultSize, new Translatable("-"), () -> {
			--this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
		valueTextField = BuildGuide.widgetHandler.createTextField(cx + buttonWidth, y, textFieldWidth, AbstractWidgetHandler.defaultSize, "");
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		widgetList.add(valueTextField);
		widgetList.add(BuildGuide.widgetHandler.createButton(cx + buttonWidth + textFieldWidth, y, buttonWidth, AbstractWidgetHandler.defaultSize, new Translatable("+"), () -> {
			++this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
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
