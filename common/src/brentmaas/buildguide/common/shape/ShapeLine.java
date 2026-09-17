package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.PropertyInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;

public class ShapeLine extends Shape {
	private PropertyInt propertyDx = new PropertyInt(3, new Translatable("property.buildguide.delta", "X"), () -> update());
	private PropertyInt propertyDy = new PropertyInt(0, new Translatable("property.buildguide.delta", "Y"), () -> update());
	private PropertyInt propertyDz = new PropertyInt(0, new Translatable("property.buildguide.delta", "Z"), () -> update());
	private PropertyRunnable propertySetEndpoint = new PropertyRunnable(() -> {
		Origin pos = BuildGuide.shapeHandler.getPlayerPosition();
		propertyDx.setValue(pos.x - shapeSet.getOriginX());
		propertyDy.setValue(pos.y - shapeSet.getOriginY());
		propertyDz.setValue(pos.z - shapeSet.getOriginZ());
		update();
	}, new Translatable("property.buildguide.setendpoint"));
	
	public ShapeLine() {
		super();
		
		properties.add(propertyDx);
		properties.add(propertyDy);
		properties.add(propertyDz);
		properties.add(propertySetEndpoint);
	}
	
	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		enumerate(propertyDx.value, propertyDy.value, propertyDz.value, (x, y, z) -> addShapeCube(buffer, x, y, z));
	}
	
	// Straight line of blocks from the origin to (deltaX, deltaY, deltaZ), in local coordinates
	public static void enumerate(int deltaX, int deltaY, int deltaZ, IBlockConsumer out) throws InterruptedException {
		int d = Math.max(Math.max(Math.abs(deltaX), Math.abs(deltaY)), Math.abs(deltaZ));
		double dx = ((double) deltaX) / d;
		double dy = ((double) deltaY) / d;
		double dz = ((double) deltaZ) / d;
		for(int i = 0;i <= d;++i) {
			out.accept((int) (dx * i + 0.5 * Math.signum(deltaX)), (int) (dy * i + 0.5 * Math.signum(deltaY)), (int) (dz * i + 0.5 * Math.signum(deltaZ)));
		}
	}
}
