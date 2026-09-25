package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.Shape;

/**
 * The Preview window: a panel over the GUI showing the shape selected when it was opened. The
 * game has one screen at a time, so "over the menu" is the DropdownOverlayScreen pattern: the
 * parent is replaced while the preview is open and shown again (same object, so its state is
 * kept) on Escape or a click outside the panel.
 *
 * Model, camera, refresh and input live in the State's PreviewController (shared with the inline
 * preview of the redesigned Shape screen, GUI redesign D5): this class only lays out the panel,
 * routes its mouse events there and draws through IScreenWrapper.drawShapePreview.
 */
public class PreviewScreen extends BaseScreen {
	private static final int panelMarginX = 40, panelTop = 55, panelMarginBottom = 10;
	// Room for the title above and the hint below the 3D area
	private static final int headerHeight = 16, footerHeight = 16;

	private final BaseScreen parent;
	// Captured on open: the preview keeps showing this shape even if the selection changes
	private final Shape shape;
	private final PreviewController preview;

	private Translatable titlePreview = new Translatable("screen.buildguide.preview");
	private Translatable textHint = new Translatable("screen.buildguide.previewhint");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");

	public PreviewScreen(BaseScreen parent) {
		this.parent = parent;
		shape = BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
		preview = BuildGuide.stateManager.getState().preview;
	}

	public void init() {
		super.init();
		preview.attach();
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
		PreviewModel model = preview.update(shape);
		int midY = (areaY1() + areaY2()) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1, areaY1(), x2, areaY2(), model, preview.camera);
	}

	public boolean onEscape() {
		back();
		return true;
	}

	// Outside the panel: back to the menu. Inside: the controller decides (3D area only)
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(!(x >= panelX1() && x < panelX2() && y >= panelTop && y < panelY2())) {
			back();
			return true;
		}
		return preview.mouseClicked(inArea(x, y), button, doubleClick);
	}

	public boolean onMouseDragged(double dx, double dy) {
		return preview.mouseDragged(dx, dy);
	}

	public void onMouseReleased() {
		preview.mouseReleased();
	}

	public boolean onMouseScrolled(double x, double y, double amount) {
		return preview.mouseScrolled(inArea(x, y), amount);
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
