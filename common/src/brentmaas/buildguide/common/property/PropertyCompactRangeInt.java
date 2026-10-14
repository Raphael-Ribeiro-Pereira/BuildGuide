package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * A PropertyRangeInt laid out as one of three narrow columns of a row (the Island's three cuts on one row,
 * 4D): - 10 px, field 26 px, + 10 px, from x + columnX(column). Same range, step and Enter as PropertyRangeInt,
 * persisted the same way (an int). It draws no label: a PropertyRowLabel on the same row does.
 */
public class PropertyCompactRangeInt extends PropertyInt {
	public static final int firstColumnX = 46, columnWidth = 46, buttonWidth = 10, textFieldWidth = 26;
	private final int minValue, maxValue, step, column;
	private IButton buttonDecrease, buttonIncrease;

	public PropertyCompactRangeInt(int value, Translatable name, Runnable onPress, int minValue, int maxValue, int step, int column) {
		super(Math.max(minValue, Math.min(maxValue, value)), name, onPress);
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.step = Math.max(1, step);
		this.column = column;
	}

	public static int columnX(int column) {
		return firstColumnX + column * columnWidth;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		int cx = x + columnX(column);
		buttonDecrease = BuildGuide.widgetHandler.createButton(cx, y, buttonWidth, rowHeight, new Translatable("-"), () -> {
			if(this.value > minValue) {
				this.value = Math.max(minValue, this.value - step);
				refresh();
				if(onPress != null) onPress.run();
			}
		});
		widgetList.add(buttonDecrease);
		valueTextField = BuildGuide.widgetHandler.createTextField(cx + buttonWidth, y, textFieldWidth, rowHeight, "");
		widgetList.add(valueTextField);
		valueTextField.setOnEnter(() -> {
			try {
				int typed = Integer.parseInt(valueTextField.getTextValue().trim());
				if(typed >= minValue && typed <= maxValue) {
					this.value = typed;
					refresh();
					if(onPress != null) onPress.run();
				}else {
					valueTextField.setTextColour(0xFF0000);
				}
			}catch(NumberFormatException e) {
				valueTextField.setTextColour(0xFF0000);
			}
		});
		buttonIncrease = BuildGuide.widgetHandler.createButton(cx + buttonWidth + textFieldWidth, y, buttonWidth, rowHeight, new Translatable("+"), () -> {
			if(this.value < maxValue) {
				this.value = Math.min(maxValue, this.value + step);
				refresh();
				if(onPress != null) onPress.run();
			}
		});
		widgetList.add(buttonIncrease);
		refresh();
	}

	private void refresh() {
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		buttonDecrease.setActive(value > minValue);
		buttonIncrease.setActive(value < maxValue);
	}

	@Override
	public void setValue(Integer value) {
		super.setValue(Math.max(minValue, Math.min(maxValue, value)));
		refresh();
	}

	@Override
	public boolean setValueFromString(String value) {
		try {
			int parsed = Integer.parseInt(value);
			if(parsed < minValue || parsed > maxValue) return false;
			setValue(parsed);
			return true;
		}catch(NumberFormatException e) {}
		return false;
	}

	@Override
	public void render(brentmaas.buildguide.common.screen.BaseScreen screen) {
		// The row's PropertyRowLabel draws the label
	}
}
