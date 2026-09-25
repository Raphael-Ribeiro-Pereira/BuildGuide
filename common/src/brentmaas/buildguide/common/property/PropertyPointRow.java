package brentmaas.buildguide.common.property;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IWidget;

/**
 * Row owner for three PropertyCompactInt columns (X, Y, Z): draws the label, makes Enter in
 * any of the three fields commit all three with a single update (no Set button since GUI
 * redesign E4), and has a button (@) that fills the three values from the player's position
 * (local to the shape origin).
 *
 * Holds no value of its own. It must be laid out on the same y as its columns (see
 * ShapeSpline.onSelectedInGUI). Persists as a constant so it can be appended to a shape's
 * property list without affecting older saves.
 */
public class PropertyPointRow extends Property<Void> {
	private static final int fromPlayerX = PropertyCompactInt.columnOffset + 3 * PropertyCompactInt.columnWidth;
	private static final int fromPlayerWidth = rowWidth - fromPlayerX;

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
		// Commit all three before updating so the shape regenerates once, whichever field had focus
		Runnable commit = () -> {
			boolean ok = px.commitTextField();
			ok &= py.commitTextField();
			ok &= pz.commitTextField();
			if(ok && onUpdate != null) onUpdate.run();
		};
		px.setOnEnter(commit);
		py.setOnEnter(commit);
		pz.setOnEnter(commit);
		widgetList.add(BuildGuide.widgetHandler.createButton(x + fromPlayerX, y, fromPlayerWidth, rowHeight, new Translatable("@"), () -> {
			int[] pos = playerPosition.get();
			px.setValue(pos[0]);
			py.setValue(pos[1]);
			pz.setValue(pos[2]);
			if(onUpdate != null) onUpdate.run();
		}));
	}

	@Override
	public void render(BaseScreen screen) {
		drawString(screen, name.toString(), x + labelX, y + 5, 0xFFFFFF);
	}

	public String getStringValue() {
		return "Row";
	}

	public boolean setValueFromString(String value) {
		return true;
	}
}
