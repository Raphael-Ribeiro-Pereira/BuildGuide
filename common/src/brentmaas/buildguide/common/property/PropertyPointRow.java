package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * Row owner for three PropertyCompactInt columns (X, Y, Z): draws the label, a Set button
 * that commits all three text fields with a single update, and a button that fills the
 * three values from the player's position (local to the shape origin).
 *
 * Holds no value of its own. It must be laid out on the same y as its columns (see
 * ShapeSpline.onSelectedInGUI). Persists as a constant so it can be appended to a shape's
 * property list without affecting older saves.
 */
public class PropertyPointRow extends Property<Void> {
	private static final int setX = PropertyCompactInt.columnOffset + 3 * PropertyCompactInt.columnWidth;
	private static final int setWidth = 26;
	private static final int fromPlayerX = setX + setWidth + 2;
	private static final int fromPlayerWidth = 26;

	private PropertyCompactInt px, py, pz;
	private Runnable onUpdate;
	private IPositionSource playerPosition;

	public interface IPositionSource {
		// Returns {x, y, z} local to the shape origin
		public int[] get();
	}

	public PropertyPointRow(Translatable name, PropertyCompactInt px, PropertyCompactInt py, PropertyCompactInt pz, Runnable onUpdate, IPositionSource playerPosition) {
		super(null, name);
		this.px = px;
		this.py = py;
		this.pz = pz;
		this.onUpdate = onUpdate;
		this.playerPosition = playerPosition;
	}

	protected void initWidgets(ArrayList<IWidget> widgetList) {
		widgetList.add(BuildGuide.widgetHandler.createButton(x + setX, y, setWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
			// Commit all three before updating so the shape regenerates once
			boolean ok = px.commitTextField();
			ok &= py.commitTextField();
			ok &= pz.commitTextField();
			if(ok && onUpdate != null) onUpdate.run();
		}));
		widgetList.add(BuildGuide.widgetHandler.createButton(x + fromPlayerX, y, fromPlayerWidth, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.fromplayer"), () -> {
			int[] pos = playerPosition.get();
			px.setValue(pos[0]);
			py.setValue(pos[1]);
			pz.setValue(pos[2]);
			if(onUpdate != null) onUpdate.run();
		}));
	}

	@Override
	public void render(BaseScreen screen) {
		drawString(screen, name.toString(), x + 5, y + 5, 0xFFFFFF);
	}

	public String getStringValue() {
		return "Row";
	}

	public boolean setValueFromString(String value) {
		return true;
	}
}
