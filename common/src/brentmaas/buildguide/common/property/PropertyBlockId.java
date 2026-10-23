package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.PresetStore;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * A block id typed as text (palette per layer, 4D): "minecraft:stone", or "stone", normalised to it; empty
 * means no block. The loader checks it (IShapeHandler.normalizeBlockId: registered and not air): an invalid
 * id turns the field red and is not applied. The Hand button takes the block in the player's main hand
 * (IShapeHandler.heldBlockId), or, with no block there, writes "Hold a block" in red in the field (2026-10-22:
 * nothing goes to the chat), which is not applied. Row: label, field 72 px, Hand.
 *
 * Persisted URL-encoded like the instance name (PresetStore.encode): a checked id holds only
 * namespace:path characters, but nothing typed must ever reach the save's ',' ';' '=' separators.
 */
public class PropertyBlockId extends Property<String> {
	public static final int idFieldWidth = 72, handX = controlX + idFieldWidth + 2, handWidth = rowWidth - handX;
	private ITextField field;
	private Runnable onPress;

	public PropertyBlockId(String value, Translatable name, Runnable onPress) {
		super(value, name);
		this.onPress = onPress;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		field = BuildGuide.widgetHandler.createTextField(x + controlX, y, idFieldWidth, rowHeight, "");
		field.setTextValue(value);
		field.setTextColour(0xFFFFFF);
		field.setOnEnter(() -> apply(field.getTextValue())); // Enter, and live apply after a pause in typing
		widgetList.add(field);
		widgetList.add(BuildGuide.widgetHandler.createButton(x + handX, y, handWidth, rowHeight, new Translatable("property.buildguide.hand"), () -> fromHand()));
	}

	/**
	 * A typed id: empty clears it, else the loader's normalised id is applied (shown in the field, then
	 * onPress). Returns false, with the field red and the value unchanged, when the id is not a block.
	 */
	public boolean apply(String typed) {
		getWidgetList(); // Initialise `field` if still null
		String text = typed == null ? "" : typed.trim();
		String id = text.isEmpty() ? "" : BuildGuide.shapeHandler.normalizeBlockId(text);
		if(id == null) {
			field.setTextColour(0xFF0000);
			return false;
		}
		value = id;
		BaseScreen.shouldUpdatePersistence = true;
		field.setTextValue(id);
		field.setTextColour(0xFFFFFF);
		field.markApplied();
		if(onPress != null) onPress.run();
		return true;
	}

	// The block in the main hand; nothing changes (and the field says so, in red) when the hand holds no block
	public boolean fromHand() {
		String held = BuildGuide.shapeHandler.heldBlockId();
		if(held == null) {
			getWidgetList(); // Initialise `field` if still null
			field.setTextValue(BuildGuide.screenHandler.translate("screen.buildguide.holdablock"));
			field.setTextColour(0xFF0000);
			return false;
		}
		return apply(held);
	}

	@Override
	public void setValue(String value) {
		super.setValue(value == null ? "" : value);
		getWidgetList(); // Initialise `field` if still null
		field.setTextValue(this.value);
		field.setTextColour(0xFFFFFF);
	}

	public String getStringValue() {
		return PresetStore.encode(value);
	}

	public boolean setValueFromString(String value) {
		setValue(PresetStore.decode(value));
		return true;
	}
}
