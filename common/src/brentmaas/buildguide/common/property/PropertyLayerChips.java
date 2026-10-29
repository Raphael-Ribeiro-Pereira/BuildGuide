package brentmaas.buildguide.common.property;

import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.ShapeLayers;

/**
 * The Layers tab's chips (the Oficina prototype, 2026-10-28): one button per layer in use, the chosen one lit,
 * each with its layer colour under it, then "+" to add a layer. Panel only: no value of its own, add it with
 * Shape.addGuiOnly. The layer count and the chosen layer belong to the caller (LayerControls).
 */
public class PropertyLayerChips extends Property<Void> {
	public static final int chipWidth = 26, chipGap = 2, addWidth = 20;
	private final IntSupplier count, chosen;
	private final IntConsumer pick;
	private final Runnable add;
	private final IButton[] chips = new IButton[ShapeLayers.COUNT];
	private IButton buttonAdd;

	public PropertyLayerChips(IntSupplier count, IntSupplier chosen, IntConsumer pick, Runnable add) {
		super(null, new Translatable(""));
		this.count = count;
		this.chosen = chosen;
		this.pick = pick;
		this.add = add;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		for(int i = 0;i < chips.length;++i) {
			final int layer = i + 1;
			chips[i] = BuildGuide.widgetHandler.createButton(x + labelX + i * (chipWidth + chipGap), y, chipWidth, rowHeight - 1, new Translatable("" + layer), () -> pick.accept(layer));
			widgetList.add(chips[i]);
		}
		buttonAdd = BuildGuide.widgetHandler.createButton(x + labelX + ShapeLayers.COUNT * (chipWidth + chipGap) + 4, y, addWidth, rowHeight - 1, new Translatable("+"), () -> add.run());
		buttonAdd.setStyle(IButton.STYLE_OUTLINE);
		widgetList.add(buttonAdd);
		refresh();
	}

	// The chips of the layers in use, the chosen one lit; "+" while a layer can be added
	private void refresh() {
		if(buttonAdd == null) return;
		int n = count.getAsInt(), c = chosen.getAsInt();
		for(int i = 0;i < chips.length;++i) {
			chips[i].setVisibility(visible && i < n);
			chips[i].setStyle(i + 1 == c ? IButton.STYLE_SEGMENT_ON : IButton.STYLE_OUTLINE);
		}
		buttonAdd.setVisibility(visible);
		buttonAdd.setActive(n < ShapeLayers.COUNT);
	}

	@Override
	public void setVisibility(boolean visible) {
		super.setVisibility(visible);
		refresh();
	}

	// The colour of each layer as a bar along its chip's foot
	@Override
	public void render(BaseScreen screen) {
		refresh();
		if(!visible) return;
		int n = count.getAsInt(), c = chosen.getAsInt();
		for(int i = 0;i < n;++i) {
			int cx = x + labelX + i * (chipWidth + chipGap);
			screen.fillRect(cx + 3, y + rowHeight - (i + 1 == c ? 4 : 3), cx + chipWidth - 3, y + rowHeight - 2, 0xFF000000 | ShapeLayers.colour(i + 1));
		}
	}

	public String getStringValue() {
		return "";
	}

	public boolean setValueFromString(String value) {
		return true;
	}
}
