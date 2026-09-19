package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ShapeSet.ExclusionBox;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;

/**
 * Validation exclusion boxes of the current shape set (Etapa 2.3). Positions inside an enabled
 * box are not validated at all (not ok/missing/wrong/near, out of the total); the shape still
 * draws there. Coordinates are local to the shape set origin, like control points. Each box
 * takes two rows: min corner and max corner, each with X Y Z fields and a "Pos" button that
 * captures the player position.
 */
public class ExclusionScreen extends BaseScreen {
	private static final int baseY = 70;
	private static final int rowHeight = AbstractWidgetHandler.defaultSize;
	private static final int fieldWidth = 40;

	private Translatable titleExclusions = new Translatable("screen.buildguide.exclusions");
	private Translatable titleHint = new Translatable("screen.buildguide.exclusionshint");

	private ICheckboxRunnableButton[] enabled = new ICheckboxRunnableButton[ShapeSet.numExclusionBoxes];
	private ITextField[][] fields = new ITextField[ShapeSet.numExclusionBoxes][6]; // minX minY minZ maxX maxY maxZ
	private IButton[] setButtons = new IButton[ShapeSet.numExclusionBoxes];
	private IButton[] posMinButtons = new IButton[ShapeSet.numExclusionBoxes];
	private IButton[] posMaxButtons = new IButton[ShapeSet.numExclusionBoxes];

	public void init() {
		super.init();

		ShapeSet set = currentSet();
		for(int i = 0;i < ShapeSet.numExclusionBoxes;++i) {
			final int box = i;
			int yMin = baseY + 2 * i * rowHeight;
			int yMax = yMin + rowHeight;
			ExclusionBox b = set != null ? set.getExclusionBox(i) : new ExclusionBox();

			enabled[i] = BuildGuide.widgetHandler.createCheckbox(60, yMin, new Translatable(""), b.enabled, false, () -> {
				ShapeSet s = currentSet();
				if(s == null) return;
				s.getExclusionBox(box).enabled = enabled[box].isCheckboxSelected();
				s.onExclusionsChanged();
			});
			int[] values = {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
			for(int k = 0;k < 6;++k) {
				int x = 120 + (k % 3) * (fieldWidth + 4);
				int y = k < 3 ? yMin : yMax;
				fields[i][k] = BuildGuide.widgetHandler.createTextField(x, y, fieldWidth, rowHeight, "");
				fields[i][k].setTextValue("" + values[k]);
				fields[i][k].setTextColour(0xFFFFFF);
			}
			posMinButtons[i] = BuildGuide.widgetHandler.createButton(256, yMin, 36, rowHeight, new Translatable("property.buildguide.fromplayer"), () -> capture(box, 0));
			posMaxButtons[i] = BuildGuide.widgetHandler.createButton(256, yMax, 36, rowHeight, new Translatable("property.buildguide.fromplayer"), () -> capture(box, 3));
			setButtons[i] = BuildGuide.widgetHandler.createButton(296, yMin, 40, 2 * rowHeight, new Translatable("screen.buildguide.set"), () -> commit(box));
		}

		for(int i = 0;i < ShapeSet.numExclusionBoxes;++i) {
			addWidget(enabled[i]);
			for(int k = 0;k < 6;++k) addWidget(fields[i][k]);
			addWidget(posMinButtons[i]);
			addWidget(posMaxButtons[i]);
			addWidget(setButtons[i]);
		}
	}

	private ShapeSet currentSet() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet() : null;
	}

	// Fill one corner (field offset 0 = min, 3 = max) with the player position relative to the origin, then apply
	private void capture(int box, int cornerOffset) {
		ShapeSet s = currentSet();
		if(s == null) return;
		Origin pos = BuildGuide.shapeHandler.getPlayerPosition();
		fields[box][cornerOffset].setTextValue("" + (pos.x - s.getOriginX()));
		fields[box][cornerOffset + 1].setTextValue("" + (pos.y - s.getOriginY()));
		fields[box][cornerOffset + 2].setTextValue("" + (pos.z - s.getOriginZ()));
		commit(box);
	}

	// Parse the six fields; invalid ones turn red and nothing is applied
	private void commit(int box) {
		ShapeSet s = currentSet();
		if(s == null) return;
		int[] values = new int[6];
		boolean ok = true;
		for(int k = 0;k < 6;++k) {
			try {
				values[k] = Integer.parseInt(fields[box][k].getTextValue().trim());
				fields[box][k].setTextColour(0xFFFFFF);
			}catch(NumberFormatException e) {
				fields[box][k].setTextColour(0xFF0000);
				ok = false;
			}
		}
		if(!ok) return;
		ExclusionBox b = s.getExclusionBox(box);
		b.minX = values[0];
		b.minY = values[1];
		b.minZ = values[2];
		b.maxX = values[3];
		b.maxY = values[4];
		b.maxZ = values[5];
		s.onExclusionsChanged();
	}

	public void render() {
		super.render();

		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleExclusions, 180, 55, 0xFFFFFF);
		for(int i = 0;i < ShapeSet.numExclusionBoxes;++i) {
			int yMin = baseY + 2 * i * rowHeight;
			drawShadowLeft(new Translatable("screen.buildguide.exclusionbox", "" + (i + 1)).toString(), 10, yMin + 6, 0xFFFFFF);
			drawShadowLeft("min", 88, yMin + 6, 0xAAAAAA);
			drawShadowLeft("max", 88, yMin + rowHeight + 6, 0xAAAAAA);
		}
		drawShadowLeft(titleHint.toString(), 10, baseY + 2 * ShapeSet.numExclusionBoxes * rowHeight + 6, 0x888888);
	}
}
