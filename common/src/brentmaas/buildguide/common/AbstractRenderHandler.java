package brentmaas.buildguide.common;

import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;

public abstract class AbstractRenderHandler {
	public abstract void register();
	
	public abstract void renderShapeBuffer(Shape shape);
	
	protected abstract void setupRenderingShapeSet(ShapeSet shape);
	
	protected abstract void endRenderingShapeSet();
	
	protected abstract void pushProfiler(String key);
	
	protected abstract void popProfiler();
	
	// Hook for loader-specific world validation; called once per rendered shape set
	protected void validateShape(ShapeSet shapeSet) {}
	
	// Hook for the validation safety net (StateReconciler); called right after validateShape, under the shape's lock
	protected void reconcileShape(ShapeSet shapeSet) {}
	
	// Hook for the validation overlay (coloured error cubes); called inside the shape set's translation, after its buffer
	protected void renderValidationOverlay(ShapeSet shapeSet) {}
	
	// Area 3 (placing into the guideline): once per frame around the shape sets, and pickPlacementTarget for each
	// rendered set under its lock, so the guideline is read safely; the click only reads the last result
	protected void beginPlacementFrame() {}
	
	protected void pickPlacementTarget(ShapeSet shapeSet) {}
	
	protected void endPlacementFrame() {}
	
	// Live apply: handlers that return true draw Shape.shownBuffer and let the WorldUpdateGate decide when a new
	// generation replaces it (renderShapeSetDeferred). Default false: the original path, unchanged
	protected boolean deferredWorldUpdates() {
		return false;
	}
	
	// Whether one of the mod's menus is open (On close and Idle wait for it to close)
	protected boolean isMenuOpen() {
		return false;
	}
	
	protected WorldUpdateGate.Mode worldUpdateMode() {
		return BuildGuide.config.worldUpdateMode.value;
	}
	
	public void render() {
		pushProfiler(BuildGuide.modid);

		long started = System.nanoTime();
		boolean drawing = false;
		beginPlacementFrame();
		if(BuildGuide.stateManager.getState().isEnabled() && BuildGuide.stateManager.getState().isShapeAvailable() && BuildGuide.stateManager.getState().getCurrentShapeSet().hasOrigin()) {
			drawing = true;
			for(ShapeSet s: BuildGuide.stateManager.getState().shapeSets) renderShapeSet(s);
		}
		endPlacementFrame();
		// The steady cost for the timing log (phase=render every 30 s): only frames in which shapes are on
		if(drawing) RenderStats.frame(System.nanoTime() - started);

		popProfiler();
	}

	// The world buffer of a set, inside its translation (live apply). Default: the shape's buffer as a whole;
	// handlers that cull the world buffer's sections draw only the ones in view
	protected void renderWorldBuffer(ShapeSet shapeSet) {
		renderShapeBuffer(shapeSet.getShape());
	}
	
	protected void renderShapeSet(ShapeSet shapeSet) {
		if(deferredWorldUpdates()) {
			renderShapeSetDeferred(shapeSet);
			return;
		}
		if(shapeSet.getShape().lock.tryLock()) {
			try {
				if(shapeSet.isVisible() && shapeSet.getShape().ready && !shapeSet.getShape().error) {
					if(!shapeSet.getShape().vertexBufferUnpacked) {
						shapeSet.getShape().buffer.end();
						shapeSet.getShape().vertexBufferUnpacked = true;
					}
					
					setupRenderingShapeSet(shapeSet);
					renderShapeBuffer(shapeSet.getShape());
					if(BuildGuide.stateManager.getState().isHighlightErrors()) renderValidationOverlay(shapeSet);
					endRenderingShapeSet();
					validateShape(shapeSet);
					reconcileShape(shapeSet);
					pickPlacementTarget(shapeSet);
				}
			}finally {
				shapeSet.getShape().lock.unlock();
			}
		}
	}
	
	/**
	 * Live apply. The buffer drawn in the world (shownBuffer) is owned by the render thread, so it is
	 * drawn even while a newer generation holds the lock: the world keeps the last applied shape instead
	 * of blinking. A finished generation replaces it when the gate allows (Live: at once; Idle: a second
	 * after the last change; On close: when the menu closes). Until then the world is stale, so the
	 * scan, the error overlay and the Area 3 target wait too: they all read the expected set, which
	 * already is the new one.
	 *
	 * The new buffer is sent to the GPU a part per frame (WorldUpload: a sectioned buffer a few sections per
	 * frame, any other buffer in one go) and replaces the old one only when all of it is sent. Once started, the
	 * sending goes on without asking the gate again; a newer generation or focus drops it.
	 */
	protected void renderShapeSetDeferred(ShapeSet shapeSet) {
		Shape shape = shapeSet.getShape();
		boolean locked = shape.lock.tryLock();
		// Timing of the frame that applies a new buffer (TimingLog.recordWorld); reason stays null in any other frame
		String reason = null;
		long endNanos = 0, closeNanos = 0, otherStarted = 0, maxFrameNanos = 0;
		int blocks = 0, focused = 0, sections = 0, frames = 0;
		try {
			// A focus change (Shape.setFocus) still making its buffer counts as a generation still running
			boolean current = locked && shape.ready && !shape.error && !shape.isRefocusing();
			WorldUpload upload = shape.upload;
			if(current) upload.dropIfNot(shape.buffer);
			if(current && shape.buffer != shape.shownBuffer && (upload.isSending(shape.buffer) || shape.worldGate.shouldApply(worldUpdateMode(), isMenuOpen()))) {
				if(!upload.isSending(shape.buffer)) upload.start(shape.buffer, WorldUpdateGate.reason(worldUpdateMode(), isMenuOpen()));
				long started = System.nanoTime();
				boolean sent = shape.buffer.endPart(System::nanoTime, started + WorldUpload.budgetNanos);
				long ended = System.nanoTime();
				upload.frame(ended - started);
				if(sent) {
					reason = upload.getReason();
					blocks = shape.getNumberOfBlocks();
					focused = shape.getWorldBlocks();
					sections = shape.buffer instanceof SectionedBuffer ? ((SectionedBuffer) shape.buffer).getSectionCount() : 0;
					frames = upload.getFrames();
					maxFrameNanos = upload.getMaxFrameNanos();
					endNanos = upload.getSendNanos();
					upload.clear();
					shape.vertexBufferUnpacked = true;
					if(shape.shownBuffer != null) shape.shownBuffer.close();
					shape.shownBuffer = shape.buffer;
					shape.worldGate.applied();
					otherStarted = System.nanoTime();
					closeNanos = otherStarted - ended;
				}
			}else if(current && shape.buffer == shape.shownBuffer && shape.worldGate.isPending()) {
				shape.worldGate.applied(); // a change that did not need a new buffer
			}
			boolean applied = current && !shape.worldGate.isPending();
			if(shapeSet.isVisible() && shape.shownBuffer != null) {
				setupRenderingShapeSet(shapeSet);
				renderWorldBuffer(shapeSet);
				if(applied && BuildGuide.stateManager.getState().isHighlightErrors()) renderValidationOverlay(shapeSet);
				endRenderingShapeSet();
			}
			if(applied && shapeSet.isVisible()) {
				validateShape(shapeSet); // a scan in layers records its own timing when it ends (TimingLog.recordScan)
				reconcileShape(shapeSet);
				pickPlacementTarget(shapeSet);
			}
		}finally {
			if(locked) shape.lock.unlock();
		}
		if(reason != null) TimingLog.recordWorld(reason, endNanos, closeNanos, System.nanoTime() - otherStarted, blocks, focused, sections, frames, maxFrameNanos);
	}
}
