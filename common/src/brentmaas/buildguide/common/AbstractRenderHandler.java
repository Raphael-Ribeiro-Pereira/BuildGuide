package brentmaas.buildguide.common;

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
		
		beginPlacementFrame();
		if(BuildGuide.stateManager.getState().isEnabled() && BuildGuide.stateManager.getState().isShapeAvailable() && BuildGuide.stateManager.getState().getCurrentShapeSet().hasOrigin()) {
			for(ShapeSet s: BuildGuide.stateManager.getState().shapeSets) renderShapeSet(s); 
		}
		endPlacementFrame();
		
		popProfiler();
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
	 */
	protected void renderShapeSetDeferred(ShapeSet shapeSet) {
		Shape shape = shapeSet.getShape();
		boolean locked = shape.lock.tryLock();
		try {
			boolean current = locked && shape.ready && !shape.error;
			if(current && shape.buffer != shape.shownBuffer && shape.worldGate.shouldApply(worldUpdateMode(), isMenuOpen())) {
				long started = System.currentTimeMillis();
				shape.buffer.end();
				shape.vertexBufferUnpacked = true;
				if(shape.shownBuffer != null) shape.shownBuffer.close();
				shape.shownBuffer = shape.buffer;
				shape.worldGate.applied();
				TimingLog.record(TimingLog.WORLD_BUFFER, System.currentTimeMillis() - started, shape.getNumberOfBlocks());
			}else if(current && shape.buffer == shape.shownBuffer && shape.worldGate.isPending()) {
				shape.worldGate.applied(); // a change that did not need a new buffer
			}
			boolean applied = current && !shape.worldGate.isPending();
			if(shapeSet.isVisible() && shape.shownBuffer != null) {
				setupRenderingShapeSet(shapeSet);
				renderShapeBuffer(shape);
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
	}
}
