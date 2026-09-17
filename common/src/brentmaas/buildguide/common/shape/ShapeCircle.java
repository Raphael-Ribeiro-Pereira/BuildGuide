package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyNonzeroInt;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

public class ShapeCircle extends Shape{
	public enum direction{
		X,
		Y,
		Z
	}
	
	private String[] directionNames = {"X", "Y", "Z"};

	private PropertyEnum<direction> propertyDir = new PropertyEnum<direction>(direction.X, new Translatable("property.buildguide.direction"), () -> update(), directionNames);
	private PropertyPositiveFloat propertyRadius = new PropertyPositiveFloat(3, new Translatable("property.buildguide.radius"), () -> update());
	private PropertyNonzeroInt propertyDepth = new PropertyNonzeroInt(1, new Translatable("property.buildguide.depth"), () -> update());
	private PropertyBoolean propertyEvenMode = new PropertyBoolean(false, new Translatable("property.buildguide.evenmode"), () -> update());
	
	public ShapeCircle() {
		super();
		
		properties.add(propertyDir);
		properties.add(propertyRadius);
		properties.add(propertyDepth);
		properties.add(propertyEvenMode);
	}
	
	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		double offset = propertyEvenMode.value ? 0.5 : 0.0;
		setOriginOffset(propertyDir.value == direction.X ? 0 : offset, propertyDir.value == direction.Y ? 0 : offset, propertyDir.value == direction.Z ? 0 : offset);
		
		enumerate(propertyDir.value, propertyRadius.value, propertyDepth.value, propertyEvenMode.value, (x, y, z) -> addShapeCube(buffer, x, y, z));
	}
	
	// Ring of the given radius extruded `depth` blocks along `dir`, in local coordinates
	public static void enumerate(direction dir, float radius, int depth, boolean evenMode, IBlockConsumer out) throws InterruptedException {
		double offset = evenMode ? 0.5 : 0.0;
		
		for(int x = (int) Math.floor(-radius + offset);x <= (int) Math.ceil(radius + offset);++x) {
			for(int y = (int) Math.floor(-radius + offset);y <= (int) Math.ceil(radius + offset);++y) {
				double r2 = (x - offset) * (x - offset) + (y - offset) * (y - offset);
				if(r2 >= (radius - 0.5) * (radius - 0.5) && r2 <= (radius + 0.5) * (radius + 0.5)) {
					for(int z = (depth > 0 ? 0 : depth + 1);z < (depth > 0 ? depth : 1);++z) {
						switch(dir) {
						case X:
							out.accept(z, x, y);
							break;
						case Y:
							out.accept(x, z, y);
							break;
						case Z:
							out.accept(x, y, z);
							break;
						}
					}
				}
			}
		}
	}
}
