package brentmaas.buildguide.common.shape;

import java.util.HashSet;
import java.util.Set;

import brentmaas.buildguide.common.property.PropertyBoolean;
import brentmaas.buildguide.common.property.PropertyEnum;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.property.PropertyPositiveFloat;
import brentmaas.buildguide.common.property.PropertyPositiveInt;
import brentmaas.buildguide.common.property.PropertyRunnable;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;

public class ShapeCone extends Shape implements IValidatable {
	public enum direction{
		X,
		Y,
		Z
	}

	public enum Mode{
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
	private PropertyPositiveFloat propertyTaper = new PropertyPositiveFloat(1.0f, new Translatable("property.buildguide.taper"), () -> update());
	private PropertyPositiveInt propertyLayerThickness = new PropertyPositiveInt(1, new Translatable("property.buildguide.layerthickness"), () -> update());
	// PropertyRunnable renders as a button
	private PropertyRunnable propertyValidate = new PropertyRunnable(() -> triggerValidation(), new Translatable("property.buildguide.validate"));

	// Local (origin-relative) positions of every block in the shape, packed with packLocal
	private final Set<Long> expectedBlocks = new HashSet<Long>();
	private transient boolean validateNextRender = false;
	private transient ValidationState validationState = new ValidationState();

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
		properties.add(propertyValidate);
	}

	protected void updateShape(IShapeBuffer buffer) throws InterruptedException {
		expectedBlocks.clear();
		validationState.invalidate();

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

		enumerate(propertyDir.value, propertyRadius.value, propertyHeight.value, propertyEvenMode.value, propertyTopRadius.value, propertyMode.value, propertyTaper.value, propertyLayerThickness.value, (x, y, z) -> {
			addShapeCube(buffer, x, y, z);
			expectedBlocks.add(LocalPos.pack(x, y, z));
		});
	}
	
	/**
	 * Cone / frustum along `dir` with base radius, height (sign = direction), optional even
	 * mode, top radius, hollow/solid mode, taper and layer thickness, in local coordinates.
	 */
	public static void enumerate(direction dir, float baseRadius, float height, boolean evenMode, float topRadius, Mode mode, float taper, int layerThickness, IBlockConsumer out) throws InterruptedException {
		double offset = evenMode ? 0.5 : 0.0;
		topRadius = Math.max(0.0f, topRadius);
		boolean solid = mode == Mode.SOLID;
		double absHeight = Math.abs(height);
		float maxRadius = Math.max(baseRadius, topRadius);
		int thickness = Math.max(1, layerThickness);

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

						if(inShape) emit(dir, x, y, z, out);
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

						if(inShape) emit(dir, x, y, z, out);
					}
				}
			}
		}
	}

	// Map the cone-local (x, y, z) with z along the axis onto the chosen direction
	private static void emit(direction dir, int x, int y, int z, IBlockConsumer out) throws InterruptedException {
		int fx, fy, fz;
		switch(dir) {
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
		out.accept(fx, fy, fz);
	}

	/**
	 * Radius of the cone at height z. taper == 1 is linear; taper > 1 bulges outward
	 * (bell shaped), taper < 1 pinches inward (trumpet shaped).
	 */
	private static double coneRadiusAtZ(int z, float height, float baseRadius, float topRadius, float taper) {
		double absHeight = Math.abs(height);
		double t = absHeight == 0.0 ? 0.0 : Math.abs(z) / absHeight;
		if(t < 0.0) t = 0.0;
		if(t > 1.0) t = 1.0;
		double r = topRadius + (baseRadius - topRadius) * Math.pow(1.0 - t, taper);
		return r < 0.0 ? 0.0 : r;
	}

	private static boolean isInsideCone(int x, int y, int z, double offset, float height, float baseRadius, float topRadius, float taper) {
		double r = Math.sqrt((x - offset) * (x - offset) + (y - offset) * (y - offset));
		return r <= coneRadiusAtZ(z, height, baseRadius, topRadius, taper) + 0.5;
	}

	// Linear interpolation between the two smoothed layer radii surrounding absZ
	private static double smoothedRadius(int absZ, int thickness, float[] radiusSmooth) {
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

	// Called from the Validate button; the render handler picks it up on the next frame
	public void triggerValidation() {
		validateNextRender = true;
	}

	public Set<Long> getExpectedBlocks() {
		return expectedBlocks;
	}
	
	public ValidationState getValidationState() {
		return validationState;
	}

	// Returns true exactly once per button press
	public boolean consumeValidateRequest() {
		if(validateNextRender) {
			validateNextRender = false;
			return true;
		}
		return false;
	}
}
