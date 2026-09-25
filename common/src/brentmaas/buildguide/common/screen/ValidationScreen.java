package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;

/**
 * Validation tab: the error list of the current shape (ValidationListComponent, Etapa 2.4,
 * reordered in 2.5, extracted in GUI redesign E2) filling the screen under its title. The list
 * itself (rows, 100-ms rebuild, click to highlight) lives in the component, which the
 * redesigned Shape screen will reuse in its right panel.
 */
public class ValidationScreen extends BaseScreen {
	private static final int listTop = 52, listBottom = 250, listLeft = 5, listRight = 475;

	private Translatable titleValidation = new Translatable("screen.buildguide.validation");

	private final ValidationListComponent errorList = new ValidationListComponent();

	public void init() {
		super.init();
		ShapeSet set = currentShapeSet();
		errorList.init(listLeft, listTop, listRight, listBottom, currentShape(), originX(set), originY(set), originZ(set));
		addWidget(errorList.getList());
	}

	public void render() {
		super.render();
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleValidation, 240, 42, 0xFFFFFF);
		ShapeSet set = currentShapeSet();
		errorList.update(currentShape(), originX(set), originY(set), originZ(set));
	}

	private Shape currentShape() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	private ShapeSet currentShapeSet() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet() : null;
	}

	private static int originX(ShapeSet set) {
		return set != null ? set.getOriginX() : 0;
	}

	private static int originY(ShapeSet set) {
		return set != null ? set.getOriginY() : 0;
	}

	private static int originZ(ShapeSet set) {
		return set != null ? set.getOriginZ() : 0;
	}
}
