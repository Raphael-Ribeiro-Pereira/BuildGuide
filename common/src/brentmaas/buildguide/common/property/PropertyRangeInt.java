package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.IWidget;

// PropertyInt clamped to [minValue, maxValue]; the -/+ buttons are disabled at the bounds
public class PropertyRangeInt extends PropertyInt {
	private int minValue, maxValue;
	private IButton buttonDecrease, buttonIncrease;
	
	public PropertyRangeInt(int value, Translatable name, Runnable onPress, int minValue, int maxValue) {
		super(Math.max(minValue, Math.min(maxValue, value)), name, onPress);
		this.minValue = minValue;
		this.maxValue = maxValue;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		buttonDecrease = BuildGuide.widgetHandler.createButton(x + 90, y, new Translatable("-"), () -> {
			if(this.value > minValue) {
				--this.value;
				refresh();
				if(onPress != null) onPress.run();
			}
		});
		widgetList.add(buttonDecrease);
		valueTextField = BuildGuide.widgetHandler.createTextField(x + 110, y, 50, AbstractWidgetHandler.defaultSize, "");
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		widgetList.add(valueTextField);
		widgetList.add(BuildGuide.widgetHandler.createButton(x + 160, y, 30, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
			try {
				int newVal = Integer.parseInt(valueTextField.getTextValue());
				if(newVal >= minValue && newVal <= maxValue) {
					this.value = newVal;
					refresh();
					if(onPress != null) onPress.run();
				}else {
					valueTextField.setTextColour(0xFF0000);
				}
			}catch(NumberFormatException e) {
				valueTextField.setTextColour(0xFF0000);
			}
		}));
		buttonIncrease = BuildGuide.widgetHandler.createButton(x + 190, y, new Translatable("+"), () -> {
			if(this.value < maxValue) {
				++this.value;
				refresh();
				if(onPress != null) onPress.run();
			}
		});
		widgetList.add(buttonIncrease);
		refresh();
	}
	
	// Sync text field and button states with the value
	private void refresh() {
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		buttonDecrease.setActive(value > minValue);
		buttonIncrease.setActive(value < maxValue);
	}
	
	@Override
	public void setValue(Integer value) {
		int clamped = Math.max(minValue, Math.min(maxValue, value));
		super.setValue(clamped); // PropertyInt.setValue initialises widgets and sets the text
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
}
