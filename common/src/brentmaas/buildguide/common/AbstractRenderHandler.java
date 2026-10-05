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
}
