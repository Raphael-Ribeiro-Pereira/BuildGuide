package brentmaas.buildguide.common.screen;

import java.io.IOException;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.PresetStore;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.PresetConfirm.Action;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeRegistry;

/**
 * The Save menu of the Shape tab (GUI redesign E8): the PresetStore slots, one row each with the
 * slot label ("Type - name - world", cut with ".." to fit) and Save / Load / Clear. Load, Clear
 * and Save over an occupied slot ask for a second click (PresetConfirm). Same pattern as
 * PreviewScreen: the Shape screen is replaced while this is open and shown again on Back or Escape.
 */
public class PresetScreen extends BaseScreen {
	private static final int titleY = 46, rowsTop = 60, rowHeight = 26, buttonWidth = 56, buttonGap = 2, margin = 8, backWidth = 80;
	private static final Action[] actions = {Action.SAVE, Action.LOAD, Action.CLEAR};

	private final BaseScreen parent;
	private final PresetConfirm confirm = new PresetConfirm();
	private final IButton[][] buttons = new IButton[PresetStore.numSlots][actions.length];
	// Title key each button shows, so setTitle only runs when it changes
	private final String[][] shownKeys = new String[PresetStore.numSlots][actions.length];

	private Translatable titlePresets = new Translatable("screen.buildguide.preset.title");
	private Translatable textEmpty = new Translatable("screen.buildguide.preset.empty");

	public PresetScreen(BaseScreen parent) {
		this.parent = parent;
	}

	public void init() {
		super.init();
		for(int slot = 0;slot < PresetStore.numSlots;++slot) {
			for(int j = 0;j < actions.length;++j) {
				final int s = slot;
				final Action action = actions[j];
				String key = titleKey(slot, action);
				buttons[slot][j] = BuildGuide.widgetHandler.createButton(buttonsX() + j * (buttonWidth + buttonGap), rowY(slot), buttonWidth, AbstractWidgetHandler.defaultSize, new Translatable(key), () -> press(s, action));
				shownKeys[slot][j] = key;
				addWidget(buttons[slot][j]);
			}
		}
		addWidget(BuildGuide.widgetHandler.createButton((wrapper.getWidth() - backWidth) / 2, rowY(PresetStore.numSlots) + 8, backWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.preset.back"), () -> back()));
		refreshButtons();
	}

	@Override
	protected boolean hasBottomBar() {
		return false;
	}

	public void render() {
		refreshButtons(); // the confirmation times out while nothing is clicked
		super.render();
		drawShadowCentred(titlePresets.toString(), wrapper.getWidth() / 2, titleY, 0xFFFFFF);
		for(int slot = 0;slot < PresetStore.numSlots;++slot) {
			String data = BuildGuide.presets.get(slot);
			String label = (slot + 1) + ". " + (data == null ? textEmpty.toString() : label(data));
			drawShadowLeft(fit(label, buttonsX() - margin - 6), margin, rowY(slot) + 6, data == null ? 0x888888 : 0xFFFFFF);
		}
	}

	public boolean onEscape() {
		back();
		return true;
	}

	private void press(int slot, Action action) {
		boolean occupied = !BuildGuide.presets.isEmpty(slot);
		boolean needsConfirm = action == Action.SAVE ? occupied : true;
		if(confirm.press(slot, action, needsConfirm)) run(slot, action);
		refreshButtons();
	}

	private void run(int slot, Action action) {
		try {
			if(action == Action.SAVE) {
				if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
				BuildGuide.presets.set(slot, BuildGuide.stateManager.getState().getCurrentShapeSet().toPresetString(BuildGuide.stateManager.getWorldLabel()));
			}else if(action == Action.CLEAR) {
				BuildGuide.presets.clear(slot);
			}else if(action == Action.LOAD) {
				load(slot);
			}
		}catch(IOException e) {
			BuildGuide.logHandler.sendChatMessage("[Build Guide] Preset could not be written: " + e.getMessage());
		}
	}

	// Replace the current shape with the slot's (type, values, origin, exclusions, name) and go back to a
	// fresh Shape tab (its type dropdown and properties are built for the shape it opens with)
	private void load(int slot) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		Shape before = BuildGuide.stateManager.getState().getCurrentShape();
		String problem = BuildGuide.stateManager.getState().getCurrentShapeSet().applyPreset(BuildGuide.presets.get(slot));
		if(problem != null) {
			BuildGuide.logHandler.sendChatMessage("[Build Guide] " + new Translatable(problem));
			return; // nothing changed
		}
		if(before != BuildGuide.stateManager.getState().getCurrentShape()) before.onDeselectedInGUI();
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}
	
	// Titles and active states from the slots and the armed button
	private void refreshButtons() {
		boolean hasShape = BuildGuide.stateManager.getState().isShapeAvailable();
		for(int slot = 0;slot < PresetStore.numSlots;++slot) {
			boolean occupied = !BuildGuide.presets.isEmpty(slot);
			for(int j = 0;j < actions.length;++j) {
				IButton button = buttons[slot][j];
				if(button == null) continue;
				String key = titleKey(slot, actions[j]);
				if(!key.equals(shownKeys[slot][j])) {
					button.setTitle(new Translatable(key));
					shownKeys[slot][j] = key;
				}
				if(actions[j] == Action.SAVE) button.setActive(hasShape);
				else button.setActive(occupied); // Load and Clear need something in the slot
			}
		}
	}

	private String titleKey(int slot, Action action) {
		boolean armed = confirm.isArmed(slot, action);
		if(action == Action.SAVE) return armed ? "screen.buildguide.preset.overwrite" : "screen.buildguide.preset.save";
		if(action == Action.LOAD) return armed ? "screen.buildguide.preset.replace" : "screen.buildguide.preset.load";
		return armed ? "screen.buildguide.preset.clearconfirm" : "screen.buildguide.preset.clear";
	}

	// "Type - name - world"; the name is left out when none was typed, an unknown type shows "?"
	private static String label(String data) {
		String[] parts = PresetStore.describe(data);
		int id = parts[0] == null ? -1 : ShapeRegistry.getShapeId(parts[0]);
		String type = id >= 0 ? new Translatable(ShapeRegistry.getTranslationKey(id)).toString() : "?";
		String label = type;
		if(parts[1] != null) label += " - " + parts[1];
		if(!parts[2].isEmpty()) label += " - " + parts[2];
		return label;
	}

	private void back() {
		BuildGuide.screenHandler.showScreen(parent);
	}

	private int buttonsX() {
		return wrapper.getWidth() - margin - actions.length * buttonWidth - (actions.length - 1) * buttonGap;
	}

	private static int rowY(int slot) {
		return rowsTop + slot * rowHeight;
	}
}
