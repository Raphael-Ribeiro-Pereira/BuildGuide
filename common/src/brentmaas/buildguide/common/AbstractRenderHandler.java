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
	
	// Hook for the validation overlay (coloured error cubes); called inside the shape set's translation, after its buffer
	protected void renderValidationOverlay(ShapeSet shapeSet) {}
	
	public void render() {
		pushProfiler(BuildGuide.modid);
		
		if(BuildGuide.stateManager.getState().isEnabled() && BuildGuide.stateManager.getState().isShapeAvailable() && BuildGuide.stateManager.getState().getCurrentShapeSet().hasOrigin()) {
			for(ShapeSet s: BuildGuide.stateManager.getState().shapeSets) renderShapeSet(s); 
		}
		
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
				}
			}finally {
				shapeSet.getShape().lock.unlock();
			}
		}
	}
}
