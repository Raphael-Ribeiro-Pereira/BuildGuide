package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;

public class PropertyMinimumInt extends Property<Integer> {
	private ITextField valueTextField;
	private Runnable onPress;
	private int minValue;
	
	public PropertyMinimumInt(int value, Translatable name, Runnable onPress, int minValue) {
		super(value, name);
		this.onPress = onPress;
		this.minValue = minValue;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		widgetList.add(BuildGuide.widgetHandler.createButton(x + controlX, y, stepWidth, rowHeight, new Translatable("-"), () -> {
			if(this.value > this.minValue) {
				--this.value;
				valueTextField.setTextValue("" + this.value);
				valueTextField.setTextColour(0xFFFFFF);
				if(onPress != null) onPress.run(); 
			}
		}));
		valueTextField = BuildGuide.widgetHandler.createTextField(x + fieldX, y, fieldWidth, rowHeight, "");
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		widgetList.add(valueTextField);
		// Enter in the field applies the value (no Set button since GUI redesign E4)
		valueTextField.setOnEnter(() -> {
			try {
				int newVal = Integer.parseInt(valueTextField.getTextValue());
				if(newVal >= this.minValue) {
					this.value = newVal;
					valueTextField.setTextColour(0xFFFFFF);
					if(onPress != null) onPress.run();
				}else {
					valueTextField.setTextColour(0xFF0000);
				}
			}catch(NumberFormatException e) {
				valueTextField.setTextColour(0xFF0000);
			}
		});
		widgetList.add(BuildGuide.widgetHandler.createButton(x + increaseX, y, stepWidth, rowHeight, new Translatable("+"), () -> {
			++this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
	}
	
	public void setValue(Integer value) {
		if(value >= minValue) {
			super.setValue(value);
			getWidgetList(); // Initialise `valueTextField` if still null
			valueTextField.setTextValue("" + value);
			valueTextField.setTextColour(0xFFFFFF);
		}
	}
	
	public String getStringValue() {
		return value.toString();
	}
	
	public boolean setValueFromString(String value) {
		try {
			int parsedValue = Integer.parseInt(value);
			if(parsedValue >= minValue) {
				setValue(parsedValue);
				return true;
			}
		}catch(NumberFormatException e) {}
		return false;
	}
}
