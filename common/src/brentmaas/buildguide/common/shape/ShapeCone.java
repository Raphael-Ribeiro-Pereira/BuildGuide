package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

public class ShapeCone extends Shape {
	private enum direction{
		X,
		Y,
		Z
	}
	
	private enum Mode{
		HOLLOW,
		SOLID
	}
	
	private String[] directionNames = {"X", "Y", "Z"};
	private String[] modeNames = {"Hollow", "Solid"};
	
	private PropertyEnum<direction> propertyDir = new PropertyEnum<direction>(direction.X, new Translatable("property.buildguide.direction"), () -> update(), directionNames);
	private PropertyPositiveFloat propertyRadius = new PropertyPositiveFloat(3, new Translatable("property.buildguide.radius"), () -> update());
	private PropertyFloat propertyHeight = new PropertyFloat(3.0f, new Translatable("property.buildguide.height"), () -> update());
	private PropertyBoolean propertyEvenMode = new PropertyBoolean(false, new Translatable("property.buildguide.evenmode"), () -> update());
	// New properties go at the end so saved shapes from older versions still load
	private PropertyFloat propertyTopRadius = new PropertyFloat(0.0f, new Translatable("property.buildguide.topradius"), () -> update());
	private PropertyEnum<Mode> propertyMode = new PropertyEnum<Mode>(Mode.HOLLOW, new Translatable("property.buildguide.mode"), () -> update(), modeNames);
	
	public ShapeCone() {
		super();
		
		properties.add(propertyDir);
		properties.add(propertyRadius);
		properties.add(propertyHeight);
		properties.add(propertyEvenMode);
		properties.add(propertyTopRadius);
		properties.add(propertyMode);
	}
	
	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		double offset = propertyEvenMode.value ? 0.5 : 0.0;
		switch(propertyDir.value) {
		case X:
			setOriginOffset(0, offset, offset);
			break;
		case Y:
			setOriginOffset(offset, 0, offset);
			break;
		case Z:
			setOriginOffset(offset, offset, 0);
			break;
		}
		
		float baseRadius = propertyRadius.value;
		float topRadius = Math.max(0.0f, propertyTopRadius.value);
		float height = propertyHeight.value;
		boolean solid = propertyMode.value == Mode.SOLID;
		double absHeight = Math.abs(height);
		float maxRadius = Math.max(baseRadius, topRadius);
		for(int x = (int) Math.floor(-maxRadius + offset);x <= (int) Math.ceil(maxRadius + offset);++x) {
			for(int y = (int) Math.floor(-maxRadius + offset);y <= (int) Math.ceil(maxRadius + offset);++y) {
				for(int z = height < 0 ? (int) Math.floor(height) : 0;z <= (height > 0 ? (int) Math.ceil(height) : 0);++z) {
					double r = Math.sqrt((x - offset) * (x - offset) + (y - offset) * (y - offset));
					double absZ = Math.abs(z);
					// Linear interpolation between base and top radius (frustum); topRadius == 0 gives the classic cone
					double radiusAtZ = absHeight == 0.0 ? baseRadius : baseRadius + (topRadius - baseRadius) * (absZ / absHeight);
					if(radiusAtZ < 0.0) radiusAtZ = 0.0;
					
					boolean inShape;
					if(solid) {
						inShape = r <= radiusAtZ + 0.5;
					}else {
						inShape = false;
						if(r <= maxRadius + 0.5) {
							if(r >= radiusAtZ - 0.5 && r <= radiusAtZ + 0.5) {
								inShape = true;
							}else if(Math.abs(topRadius - baseRadius) > 1e-6) {
								// Also fill along the slope so steep walls don't leave gaps
								double heightAtR = absHeight * (r - baseRadius) / (topRadius - baseRadius);
								if(heightAtR >= absZ - 0.5 && heightAtR <= absZ + 0.5) inShape = true;
							}
						}
					}
					
					if(inShape) {
						switch(propertyDir.value) {
						case X:
							addShapeCube(buffer, z, x, y);
							break;
						case Y:
							addShapeCube(buffer, x, z, y);
							break;
						case Z:
							addShapeCube(buffer, x, y, z);
							break;
						}
					}
				}
			}
		}
	}
}
