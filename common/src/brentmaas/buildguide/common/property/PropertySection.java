package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * Section selector shown at the top of a shape's property panel when the shape declared
 * sections (see Shape.declareSection). UI state only: it lives outside Shape.properties
 * and is never persisted. Looks like PropertyEnum: "<-" name "->".
 */
public class PropertySection extends Property<Integer> {
	private ArrayList<Translatable> names = new ArrayList<Translatable>();
	private Runnable onChange;
	
	public PropertySection(Translatable name, Runnable onChange) {
		super(0, name);
		this.onChange = onChange;
	}
	
	// Returns the index of the new section
	public int addSection(Translatable sectionName) {
		names.add(sectionName);
		return names.size() - 1;
	}
	
	protected void initWidgets(ArrayList<IWidget> widgetList) {
		widgetList.add(BuildGuide.widgetHandler.createButton(x + 90, y, new Translatable("<-"), () -> {
			value = Math.floorMod(value - 1, names.size());
			if(onChange != null) onChange.run();
		}));
		widgetList.add(BuildGuide.widgetHandler.createButton(x + 190, y, new Translatable("->"), () -> {
			value = Math.floorMod(value + 1, names.size());
			if(onChange != null) onChange.run();
		}));
	}
	
	public void render(BaseScreen screen) {
		super.render(screen);
		drawStringCentred(screen, names.get(value).toString(), x + 150, y + 5, 0xFFFFFF);
	}
	
	public String getStringValue() {
		return "Section";
	}
	
	public boolean setValueFromString(String value) {
		return true;
	}
}
