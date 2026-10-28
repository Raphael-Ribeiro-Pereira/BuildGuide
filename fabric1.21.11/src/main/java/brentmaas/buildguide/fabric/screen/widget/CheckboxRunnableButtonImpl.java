package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import net.minecraft.network.chat.Component;

public class CheckboxRunnableButtonImpl implements ICheckboxRunnableButton {
	protected final ICheckboxRunnableButton.IPressable onPress;
	private int x, y;
	private Translatable title;
	private boolean checked;
	public FlatCheckbox checkbox;
	
	public CheckboxRunnableButtonImpl(int x, int y, int width, int height, Translatable title, boolean checked, boolean drawTitle, ICheckboxRunnableButton.IPressable onPress) {
		this.x = x;
		this.y = y;
		this.title = title;
		this.checked = checked;
		this.onPress = onPress;
	}
	
	public void initCheckboxIfNull() {
		if(checkbox == null) {
			checkbox = new FlatCheckbox(x, y, Component.translatable(title.getTranslationKey(), title.getValues()), checked, this::onPress);
			//checkbox.setWidth(width);
			//checkbox.setHeight(height);
		}
	}
	
	public void onPress() {
		onPress.onPress();
	}
	
	public void setChecked(boolean checked) {
		initCheckboxIfNull();
		if(checkbox.selected() != checked) checkbox.onPress(null);
	}
	
	public boolean isCheckboxSelected() {
		initCheckboxIfNull();
		return checkbox.selected();
	}
	
	public void setActive(boolean active) {
		initCheckboxIfNull();
		checkbox.active = active;
	}
	
	public void setYPosition(int y) {
		initCheckboxIfNull();
		checkbox.setY(y);
	}

	public void shiftX(int dx) {
		initCheckboxIfNull();
		checkbox.setX(checkbox.getX() + dx);
	}
	
	public void setVisibility(boolean visible) {
		initCheckboxIfNull();
		checkbox.visible = visible;
	}
}
