package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;

public class PropertyInt extends Property<Integer> {
	protected ITextField valueTextField;
	protected Runnable onPress;
	
	public PropertyInt(int value, Translatable name, Runnable onPress) {
		super(value, name);
		this.onPress = onPress;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		widgetList.add(BuildGuide.widgetHandler.createButton(x + controlX, y, stepWidth, rowHeight, new Translatable("-"), () -> {
			--this.value;
			valueTextField.setTextValue("" + this.value);
			valueTextField.setTextColour(0xFFFFFF);
			if(onPress != null) onPress.run();
		}));
		valueTextField = BuildGuide.widgetHandler.createTextField(x + fieldX, y, fieldWidth, rowHeight, "");
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
		widgetList.add(valueTextField);
		// Enter in the field applies the value (no Set button since GUI redesign E4)
		valueTextField.setOnEnter(() -> {
			try {
				int newval = Integer.parseInt(valueTextField.getTextValue());
				this.value = newval;
				valueTextField.setTextColour(0xFFFFFF);
				if(onPress != null) onPress.run();
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
		super.setValue(value);
		getWidgetList(); // Initialise `valueTextField` if still null
		valueTextField.setTextValue("" + value);
		valueTextField.setTextColour(0xFFFFFF);
	}
	
	public String getStringValue() {
		return value.toString();
	}
	
	public boolean setValueFromString(String value) {
		try {
			setValue(Integer.parseInt(value));
			return true;
		}catch(NumberFormatException e) {}
		return false;
	}
}
