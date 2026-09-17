package brentmaas.buildguide.common.shape;

import java.util.HashSet;
import java.util.Set;

import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyPositiveInt;
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

	public enum ValidateMode{
		OFF,
		COUNT
	}

	private String[] directionNames = {"X", "Y", "Z"};
	private String[] modeNames = {"Hollow", "Solid"};
	private String[] validateModeNames = {"Off", "Count"};

	private PropertyEnum<direction> propertyDir = new PropertyEnum<direction>(direction.X, new Translatable("property.buildguide.direction"), () -> update(), directionNames);
	private PropertyPositiveFloat propertyRadius = new PropertyPositiveFloat(3, new Translatable("property.buildguide.radius"), () -> update());
	private PropertyFloat propertyHeight = new PropertyFloat(3.0f, new Translatable("property.buildguide.height"), () -> update());
	private PropertyBoolean propertyEvenMode = new PropertyBoolean(false, new Translatable("property.buildguide.evenmode"), () -> update());
	// New properties go at the end so saved shapes from older versions still load
	private PropertyFloat propertyTopRadius = new PropertyFloat(0.0f, new Translatable("property.buildguide.topradius"), () -> update());
	private PropertyEnum<Mode> propertyMode = new PropertyEnum<Mode>(Mode.HOLLOW, new Translatable("property.buildguide.mode"), () -> update(), modeNames);
	private PropertyPositiveFloat propertyTaper = new PropertyPositiveFloat(1.0f, new Translatable("property.buildguide.taper"), () -> update());
	private PropertyPositiveInt propertyLayerThickness = new PropertyPositiveInt(1, new Translatable("property.buildguide.layerthickness"), () -> update());
	private PropertyEnum<ValidateMode> propertyValidateMode = new PropertyEnum<ValidateMode>(ValidateMode.OFF, new Translatable("property.buildguide.validatemode"), () -> update(), validateModeNames);

	// Local (origin-relative) positions of every block in the shape, packed with packLocal; only filled when validating
	private final Set<Long> expectedBlocks = new HashSet<Long>();
	private boolean needsValidation = false;

	public ShapeCone() {
		super();

		properties.add(propertyDir);
		properties.add(propertyRadius);
		properties.add(propertyHeight);
		properties.add(propertyEvenMode);
		properties.add(propertyTopRadius);
		properties.add(propertyMode);
		properties.add(propertyTaper);
		properties.add(propertyLayerThickness);
		properties.add(propertyValidateMode);
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		expectedBlocks.clear();

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
		float taper = propertyTaper.value;
		int thickness = Math.max(1, propertyLayerThickness.value);

		int zMin = height < 0 ? (int) Math.floor(height) : 0;
		int zMax = height > 0 ? (int) Math.ceil(height) : 0;
		int xyMin = (int) Math.floor(-maxRadius + offset);
		int xyMax = (int) Math.ceil(maxRadius + offset);

		if(thickness <= 1) {
			// Per-block evaluation
			for(int x = xyMin;x <= xyMax;++x) {
				for(int y = xyMin;y <= xyMax;++y) {
					for(int z = zMin;z <= zMax;++z) {
						boolean inShape;
						if(taper == 1.0f) {
							// Linear slope: keep the exact frustum logic
							double r = Math.sqrt((x - offset) * (x - offset) + (y - offset) * (y - offset));
							double absZ = Math.abs(z);
							double radiusAtZ = absHeight == 0.0 ? baseRadius : baseRadius + (topRadius - baseRadius) * (absZ / absHeight);
							if(radiusAtZ < 0.0) radiusAtZ = 0.0;

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
						}else if(!isInsideCone(x, y, z, offset, height, baseRadius, topRadius, taper)) {
							inShape = false;
						}else if(solid) {
							inShape = true;
						}else {
							// Curved slope: a block is wall if any horizontal neighbour is outside the cone
							inShape = !isInsideCone(x + 1, y, z, offset, height, baseRadius, topRadius, taper)
									|| !isInsideCone(x - 1, y, z, offset, height, baseRadius, topRadius, taper)
									|| !isInsideCone(x, y + 1, z, offset, height, baseRadius, topRadius, taper)
									|| !isInsideCone(x, y - 1, z, offset, height, baseRadius, topRadius, taper);
						}

						if(inShape) emit(buffer, x, y, z);
					}
				}
			}
		}else {
			// Layered: sample the radius once per layer of `thickness` blocks, then smooth the
			// samples with a [0.25, 0.5, 0.25] kernel so consecutive layers don't step too abruptly
			int numLayers = (int) Math.ceil(absHeight / thickness) + 2;
			float[] radiusRaw = new float[numLayers];
			for(int c = 0;c < numLayers;++c) {
				radiusRaw[c] = (float) coneRadiusAtZ(c * thickness, height, baseRadius, topRadius, taper);
			}
			float[] radiusSmooth = new float[numLayers];
			for(int c = 0;c < numLayers;++c) {
				float prev = radiusRaw[c > 0 ? c - 1 : 0];
				float curr = radiusRaw[c];
				float next = radiusRaw[c < numLayers - 1 ? c + 1 : c];
				radiusSmooth[c] = prev * 0.25f + curr * 0.5f + next * 0.25f;
			}

			for(int z = zMin;z <= zMax;++z) {
				double rz = smoothedRadius(Math.abs(z), thickness, radiusSmooth);
				for(int x = xyMin;x <= xyMax;++x) {
					for(int y = xyMin;y <= xyMax;++y) {
						double rx = Math.sqrt((x - offset) * (x - offset) + (y - offset) * (y - offset));
						boolean inShape;
						if(rx > rz + 0.5) {
							inShape = false;
						}else if(solid) {
							inShape = true;
						}else {
							inShape = Math.sqrt((x + 1 - offset) * (x + 1 - offset) + (y - offset) * (y - offset)) > rz + 0.5
									|| Math.sqrt((x - 1 - offset) * (x - 1 - offset) + (y - offset) * (y - offset)) > rz + 0.5
									|| Math.sqrt((x - offset) * (x - offset) + (y + 1 - offset) * (y + 1 - offset)) > rz + 0.5
									|| Math.sqrt((x - offset) * (x - offset) + (y - 1 - offset) * (y - 1 - offset)) > rz + 0.5;
						}

						if(inShape) emit(buffer, x, y, z);
					}
				}
			}
		}

		// Ask the render handler to validate against the world on the next frame
		if(propertyValidateMode.value != ValidateMode.OFF) needsValidation = true;
	}

	private void emit(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		int fx, fy, fz;
		switch(propertyDir.value) {
		case X:
			fx = z;
			fy = x;
			fz = y;
			break;
		case Y:
			fx = x;
			fy = z;
			fz = y;
			break;
		default:
			fx = x;
			fy = y;
			fz = z;
			break;
		}
		addShapeCube(buffer, fx, fy, fz);
		if(propertyValidateMode.value != ValidateMode.OFF) expectedBlocks.add(packLocal(fx, fy, fz));
	}

	/**
	 * Radius of the cone at height z. taper == 1 is linear; taper > 1 bulges outward
	 * (bell shaped), taper < 1 pinches inward (trumpet shaped).
	 */
	private double coneRadiusAtZ(int z, float height, float baseRadius, float topRadius, float taper) {
		double absHeight = Math.abs(height);
		double t = absHeight == 0.0 ? 0.0 : Math.abs(z) / absHeight;
		if(t < 0.0) t = 0.0;
		if(t > 1.0) t = 1.0;
		double r = topRadius + (baseRadius - topRadius) * Math.pow(1.0 - t, taper);
		return r < 0.0 ? 0.0 : r;
	}

	private boolean isInsideCone(int x, int y, int z, double offset, float height, float baseRadius, float topRadius, float taper) {
		double r = Math.sqrt((x - offset) * (x - offset) + (y - offset) * (y - offset));
		return r <= coneRadiusAtZ(z, height, baseRadius, topRadius, taper) + 0.5;
	}

	// Linear interpolation between the two smoothed layer radii surrounding absZ
	private double smoothedRadius(int absZ, int thickness, float[] radiusSmooth) {
		double c = (double) absZ / thickness;
		int last = radiusSmooth.length - 1;
		int cLow = (int) Math.floor(c);
		int cHigh = (int) Math.ceil(c);
		if(cLow < 0) cLow = 0;
		else if(cLow > last) cLow = last;
		if(cHigh < 0) cHigh = 0;
		else if(cHigh > last) cHigh = last;
		if(cLow == cHigh) return radiusSmooth[cLow];
		double frac = c - Math.floor(c);
		return radiusSmooth[cLow] + (radiusSmooth[cHigh] - radiusSmooth[cLow]) * frac;
	}

	public ValidateMode getValidateMode() {
		return propertyValidateMode.value;
	}

	public Set<Long> getExpectedBlocks() {
		return expectedBlocks;
	}

	// Returns true exactly once per shape update while validation is on
	public boolean consumeNeedsValidation() {
		if(needsValidation) {
			needsValidation = false;
			return true;
		}
		return false;
	}

	// 21 bits per axis (signed), enough for any local shape coordinate
	public static long packLocal(int x, int y, int z) {
		return ((x & 0x1FFFFFL) << 42) | ((y & 0x1FFFFFL) << 21) | (z & 0x1FFFFFL);
	}

	public static int unpackLocalX(long key) {
		return signExtend21((int) ((key >> 42) & 0x1FFFFFL));
	}

	public static int unpackLocalY(long key) {
		return signExtend21((int) ((key >> 21) & 0x1FFFFFL));
	}

	public static int unpackLocalZ(long key) {
		return signExtend21((int) (key & 0x1FFFFFL));
	}

	private static int signExtend21(int v) {
		return (v << 11) >> 11;
	}
}
