package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;

/**
 * 3D preview of the shape selected when it was opened (Step 0 of the preview): a panel over
 * the GUI. The game has one screen at a time, so "over the menu" is the DropdownOverlayScreen
 * pattern: the parent is replaced while the preview is open and shown again (same object, so
 * its state is kept) on Escape or a click outside the panel. The geometry is a PreviewModel
 * snapshot taken under the shape's lock (retried every frame while the shape is generating),
 * drawn by the loader through IScreenWrapper.drawShapePreview.
 */
public class PreviewScreen extends BaseScreen {
	private static final int panelMarginX = 40, panelTop = 55, panelMarginBottom = 10;
	// Room for the title above and the hint below the 3D area
	private static final int headerHeight = 16, footerHeight = 16;

	private final BaseScreen parent;
	// Captured on open: the preview keeps showing this shape even if the selection changes
	private final Shape shape;
	// Snapshot of the shape's blocks; null until the shape is ready
	private PreviewModel model = null;
	private final PreviewCamera camera = new PreviewCamera();
	private static final long colourRefreshMillis = 100;
	private long lastColourRefresh = 0;
	// A left click started in the 3D area and the button is still down
	private boolean dragging = false;

	private Translatable titlePreview = new Translatable("screen.buildguide.preview");
	private Translatable textHint = new Translatable("screen.buildguide.previewhint");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");

	public PreviewScreen(BaseScreen parent) {
		this.parent = parent;
		shape = BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	public void render() {
		super.render();
		int x1 = panelX1(), y1 = panelTop, x2 = panelX2(), y2 = panelY2();
		fillRect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF808080);
		fillRect(x1, y1, x2, y2, 0xFF000000);
		String title = titlePreview + (shape != null ? " - " + new Translatable(shape.getTranslationKey()) : "");
		drawShadowCentred(title, (x1 + x2) / 2, y1 + 5, 0xFFFFFF);
		drawShadowCentred(textHint.toString(), (x1 + x2) / 2, y2 - 12, 0x888888);

		if(shape == null) return;
		// Geometry once (frozen from open); colours follow the live validation state, like the error
		// list and the overlay: compare its version, refresh at most every 100 ms
		ValidationState state = shape.getValidationState();
		long now = System.currentTimeMillis();
		if(model == null) {
			model = PreviewModel.snapshot(shape);
			if(model != null) {
				model = model.withValidation(state);
				lastColourRefresh = now;
			}
		}else if(state.getVersion() != model.stateVersion && now - lastColourRefresh >= colourRefreshMillis) {
			model = model.withValidation(state);
			lastColourRefresh = now;
		}
		int midY = (areaY1() + areaY2()) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1, areaY1(), x2, areaY2(), model, camera);
	}

	public boolean onEscape() {
		back();
		return true;
	}

	// Outside the panel: back to the menu. In the 3D area: middle button or double click resets the
	// camera, left button starts a drag (only a drag that starts here rotates)
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(!(x >= panelX1() && x < panelX2() && y >= panelTop && y < panelY2())) {
			back();
			return true;
		}
		if(!inArea(x, y)) return false;
		if(button == MOUSE_MIDDLE || doubleClick) {
			camera.reset();
			dragging = false;
			return true;
		}
		if(button == MOUSE_LEFT) {
			dragging = true;
			return true;
		}
		return false;
	}

	// Moves the camera only: the model (and so the mesh) stays; the renderer redraws its texture
	public boolean onMouseDragged(double dx, double dy) {
		if(!dragging) return false;
		camera.rotate(dx, dy);
		return true;
	}

	public void onMouseReleased() {
		dragging = false;
	}

	public boolean onMouseScrolled(double x, double y, double amount) {
		if(!inArea(x, y)) return false;
		camera.zoomBy(amount);
		return true;
	}

	// The 3D area: the panel minus its title and hint rows, exactly what drawShapePreview gets
	private boolean inArea(double x, double y) {
		return x >= panelX1() && x < panelX2() && y >= areaY1() && y < areaY2();
	}

	private int areaY1() {
		return panelTop + headerHeight;
	}

	private int areaY2() {
		return panelY2() - footerHeight;
	}

	private void back() {
		BuildGuide.screenHandler.showScreen(parent);
	}

	private int panelX1() {
		return panelMarginX;
	}

	private int panelX2() {
		return wrapper.getWidth() - panelMarginX;
	}

	private int panelY2() {
		return wrapper.getHeight() - panelMarginBottom;
	}
}
