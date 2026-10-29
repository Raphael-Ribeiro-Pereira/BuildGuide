package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.IWidget;

public class PropertyEnum<T extends Enum<T>> extends Property<T> {
	private String[] names;
	private Runnable onPress;
	// The switch's options, null with arrows
	private IButton[] segments = null;
	public static final int maxSegments = 3;
	
	public PropertyEnum(T value, Translatable name, Runnable onPress, String[] names) {
		super(value, name);
		this.names = names;
		this.onPress = onPress;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		T[] constants = this.value.getDeclaringClass().getEnumConstants();
		if(constants.length <= maxSegments) {
			segments = new IButton[constants.length];
			int width = (rowWidth - controlX) / constants.length;
			for(int i = 0;i < constants.length;++i) {
				final T choice = constants[i];
				segments[i] = BuildGuide.widgetHandler.createButton(x + controlX + i * width, y + 1, width - 1, rowHeight - 3, new Translatable(names[i]), () -> {
					this.value = choice;
					refreshSegments();
					if(onPress != null) onPress.run();
				});
				widgetList.add(segments[i]);
			}
			refreshSegments();
			return;
		}
		widgetList.add(BuildGuide.widgetHandler.createButton(x + controlX, y, stepWidth, rowHeight, new Translatable("<"), () -> {
			this.value = this.value.getDeclaringClass().getEnumConstants()[Math.floorMod(this.value.ordinal() - 1, this.value.getDeclaringClass().getEnumConstants().length)];
			if(onPress != null) onPress.run();
		}));
		widgetList.add(BuildGuide.widgetHandler.createButton(x + increaseX, y, stepWidth, rowHeight, new Translatable(">"), () -> {
			this.value = this.value.getDeclaringClass().getEnumConstants()[Math.floorMod(this.value.ordinal() + 1, this.value.getDeclaringClass().getEnumConstants().length)];
			if(onPress != null) onPress.run();
		}));
	}
	
	private void refreshSegments() {
		if(segments == null) return;
		for(int i = 0;i < segments.length;++i) segments[i].setStyle(i == value.ordinal() ? IButton.STYLE_SEGMENT_ON : IButton.STYLE_SEGMENT);
	}
	
	public void render(BaseScreen screen) {
		super.render(screen);
		if(segments != null) {
			refreshSegments(); // the value may have been set from elsewhere
			return;
		}
		drawStringCentred(screen, names[value.ordinal()], x + fieldX + fieldWidth / 2, y + 5, 0xFFFFFF);
	}
	
	public String getStringValue() {
		return value.name();
	}
	
	public boolean setValueFromString(String value) {
		for(int i = 0;i < this.value.getDeclaringClass().getEnumConstants().length;++i) {
			if(this.value.getDeclaringClass().getEnumConstants()[i].name().equals(value)) {
				setValue(this.value.getDeclaringClass().getEnumConstants()[i]);
				return true;
			}
		}
		return false;
	}
}
