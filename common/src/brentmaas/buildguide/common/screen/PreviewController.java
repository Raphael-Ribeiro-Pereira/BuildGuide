package brentmaas.buildguide.common.screen;

import java.util.function.LongSupplier;

import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;

/**
 * Model, camera and input of the 3D preview, shared by every view of it (the Preview window
 * now, the inline panel of the Shape screen later). One per State (per world and dimension),
 * so the camera survives tab switches and reopening the window; nothing here is persisted.
 *
 * The camera is shared on purpose (GUI redesign D5). Only one screen is shown at a time, so
 * input never reaches two views at once; the screen that shows the preview routes its mouse
 * events here and calls attach() when it takes over, which drops a drag left by another view.
 *
 * Refresh rules, called every frame through update():
 * - geometry: a new snapshot when the shape instance changes (at once) or its generation moves
 *   (at most every geometryRefreshMillis while generations keep coming; the pending one is
 *   always taken once they stop, because it stays pending until the model catches up);
 * - colours: when the validation state's version moves, at most every colourRefreshMillis.
 *
 * The constructor only allocates plain fields: safe to build before the game is initialised.
 */
public class PreviewController {
	public static final long geometryRefreshMillis = 250, colourRefreshMillis = 100;

	public final PreviewCamera camera = new PreviewCamera();
	private final LongSupplier clock;
	private Shape shape = null;
	private PreviewModel model = null;
	private long lastGeometry = 0, lastColour = 0;
	// A left click started in a preview area and the button is still down
	private boolean dragging = false;

	public PreviewController() {
		this(System::currentTimeMillis);
	}

	// Injectable clock, for tests
	public PreviewController(LongSupplier clock) {
		this.clock = clock;
	}

	/**
	 * The model to draw for this shape this frame, or null when there is nothing to draw yet (no
	 * shape, or it is generating and no earlier snapshot of it exists). While a newer generation
	 * cannot be copied yet (lock busy, still generating) the previous model stays on screen.
	 */
	public PreviewModel update(Shape current) {
		if(current == null) {
			shape = null;
			model = null;
			return null;
		}
		long now = clock.getAsLong();
		if(current != shape) {
			shape = current;
			model = null;
		}
		ValidationState state = current.getValidationState();
		boolean newGeneration = model != null && current.getGeneration() != model.generation;
		if(model == null || (newGeneration && now - lastGeometry >= geometryRefreshMillis)) {
			PreviewModel snapshot = PreviewModel.snapshot(current);
			if(snapshot != null) {
				model = snapshot.withValidation(state);
				lastGeometry = now;
				lastColour = now;
			}
		}else if(state.getVersion() != model.stateVersion && now - lastColour >= colourRefreshMillis) {
			model = model.withValidation(state);
			lastColour = now;
		}
		return model;
	}

	// A view takes over the preview: forget any drag another view left behind
	public void attach() {
		dragging = false;
	}

	// Click in a view; inArea = inside its 3D area. Middle button or double click resets the camera,
	// left button starts a drag. Returns whether it was handled
	public boolean mouseClicked(boolean inArea, int button, boolean doubleClick) {
		if(!inArea) return false;
		if(button == BaseScreen.MOUSE_MIDDLE || doubleClick) {
			camera.reset();
			dragging = false;
			return true;
		}
		if(button == BaseScreen.MOUSE_LEFT) {
			dragging = true;
			return true;
		}
		return false;
	}

	// Only a drag that started in the area rotates. The model (and so the mesh) stays; the renderer redraws its texture
	public boolean mouseDragged(double dx, double dy) {
		if(!dragging) return false;
		camera.rotate(dx, dy);
		return true;
	}

	public void mouseReleased() {
		dragging = false;
	}

	public boolean mouseScrolled(boolean inArea, double amount) {
		if(!inArea) return false;
		camera.zoomBy(amount);
		return true;
	}

	public boolean isDragging() {
		return dragging;
	}
}
