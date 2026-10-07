package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.PresetStore;
import brentmaas.buildguide.common.screen.BaseScreen;

public class ShapeSet {
	private static final String PERSISTENCE_INDEX = "index";
	private static final String PERSISTENCE_ORIGIN = "origin";
	private static final String PERSISTENCE_VISIBLE = "visible";
	private static final String PERSISTENCE_SHAPECOLOUR = "shapeColour";
	private static final String PERSISTENCE_ORIGINCOLOUR = "originColour";
	private static final String PERSISTENCE_SHAPECUBESIZE = "shapeCubeSize";
	private static final String PERSISTENCE_ORIGINCUBESIZE = "originCubeSize";
	private static final String PERSISTENCE_EXCLUSIONS = "exclusions";
	// Instance name typed in the Shape tab (E8; E6 kept it in memory only). Written last, URL-encoded: a jar
	// without it skips the unknown key, a save without it gives the default "Type #N"
	private static final String PERSISTENCE_NAME = "name";
	
	public Shape[] shapes;
	private int index;
	
	private Origin origin;
	
	private boolean visible = true;
	// null = default name ("Type #N")
	private String name = null;

	public static final float defaultColourShapeR = 1.0f;
	public static final float defaultColourShapeG = 1.0f;
	public static final float defaultColourShapeB = 1.0f;
	public static final float defaultColourShapeA = 0.5f;
	private float colourShapeR = defaultColourShapeR;
	private float colourShapeG = defaultColourShapeG;
	private float colourShapeB = defaultColourShapeB;
	private float colourShapeA = defaultColourShapeA;

	public static final float defaultColourOriginR = 1.0f;
	public static final float defaultColourOriginG = 0.0f;
	public static final float defaultColourOriginB = 0.0f;
	public static final float defaultColourOriginA = 0.5f;
	private float colourOriginR = defaultColourOriginR;
	private float colourOriginG = defaultColourOriginG;
	private float colourOriginB = defaultColourOriginB;
	private float colourOriginA = defaultColourOriginA;

	public static final double defaultShapeCubeSize = 0.6;
	public static final double defaultOriginCubeSize = 0.2;
	private double shapeCubeSize = defaultShapeCubeSize;
	private double originCubeSize = defaultOriginCubeSize;
	
	// Validation exclusion boxes (Etapa 2.3), local to the origin, inclusive corners. They belong to
	// the set, not the shape: switching shape keeps them. Persisted as 7 ints per box
	public static final int numExclusionBoxes = 4;
	public static class ExclusionBox {
		public boolean enabled = false;
		public int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
	}
	private ExclusionBox[] exclusionBoxes = new ExclusionBox[numExclusionBoxes];
	{ for(int i = 0;i < numExclusionBoxes;++i) exclusionBoxes[i] = new ExclusionBox(); }
	
	public ShapeSet(int startIndex) {
		shapes = new Shape[ShapeRegistry.getNumberOfShapes()];
		index = startIndex;
		shapes[index] = initialiseShape(ShapeRegistry.getClassIdentifier(index));
	}
	
	// The set was removed: every shape instance it made frees its buffers (Shape.dispose)
	public void dispose() {
		for(Shape shape: shapes) if(shape != null) shape.dispose();
	}
	
	private Shape initialiseShape(String shapeId) {
		Shape newShape = ShapeRegistry.getNewInstance(shapeId);
		newShape.shapeSet = this;
		newShape.captureDefaults();
		BaseScreen.shouldUpdatePersistence = true;
		return newShape;
	}
	
	public void resetOrigin() {
		Origin old = origin;
		origin = BuildGuide.shapeHandler.getPlayerPosition();
		BaseScreen.shouldUpdatePersistence = true;
		if(old == null || old.x != origin.x || old.y != origin.y || old.z != origin.z) onOriginChanged();
	}

	// Every way of moving the origin ends here (Origin fields and Enter, Set origin, key binds, the global
	// origin of the Shape list): the validation results stay at the old place until a rescan, which is
	// requested for every instantiated shape of the set and debounced by ValidationState (P4)
	private void onOriginChanged() {
		for(Shape s: shapes) {
			if(s != null) s.getValidationState().requestScan();
		}
	}
	
	public void updateShape() {
		if(shapes[index] != null) {
			shapes[index].update();
		}
	}
	
	public void updateAllShapes() {
		for(Shape s: shapes) {
			if(s != null) {
				s.update();
			}
		}
	}
	
	public void setOriginX(int x) {
		setOrigin(x, origin.y, origin.z);
	}

	public void setOriginY(int y) {
		setOrigin(origin.x, y, origin.z);
	}

	public void setOriginZ(int z) {
		setOrigin(origin.x, origin.y, z);
	}

	public void setOrigin(int x, int y, int z) {
		boolean changed = origin.x != x || origin.y != y || origin.z != z;
		origin.x = x;
		origin.y = y;
		origin.z = z;
		BaseScreen.shouldUpdatePersistence = true;
		if(changed) onOriginChanged();
	}

	public void shiftOrigin(int dx, int dy, int dz) {
		setOrigin(origin.x + dx, origin.y + dy, origin.z + dz);
	}
	
	public float getShapeColourR() {
		return colourShapeR;
	}
	
	public float getShapeColourG() {
		return colourShapeG;
	}
	
	public float getShapeColourB() {
		return colourShapeB;
	}
	
	public float getShapeColourA() {
		return colourShapeA;
	}
	
	public void setShapeColour(float r, float g, float b, float a) {
		colourShapeR = r;
		colourShapeG = g;
		colourShapeB = b;
		colourShapeA = a;
		updateAllShapes();
	}
	
	public float getOriginColourR() {
		return colourOriginR;
	}
	
	public float getOriginColourG() {
		return colourOriginG;
	}
	
	public float getOriginColourB() {
		return colourOriginB;
	}
	
	public float getOriginColourA() {
		return colourOriginA;
	}
	
	public void setOriginColour(float r, float g, float b, float a) {
		colourOriginR = r;
		colourOriginG = g;
		colourOriginB = b;
		colourOriginA = a;
		updateAllShapes();
	}
	
	public void setCubeSize(double shapeCubeSize, double originCubeSize) {
		this.shapeCubeSize = shapeCubeSize;
		this.originCubeSize = originCubeSize;
		updateAllShapes();
	}
	
	public double getShapeCubeSize() {
		return shapeCubeSize;
	}
	
	public double getOriginCubeSize() {
		return originCubeSize;
	}
	
	public boolean isShapeAvailable() {
		return isShapeAvailable(index);
	}
	
	public boolean isShapeAvailable(int index) {
		return shapes[index] != null;
	}
	
	// The instantiated shape at that registry index, or null; never instantiates
	public Shape getShapeIfAvailable(int index) {
		return shapes[index];
	}
	
	public Shape getShape() {
		if(shapes[index] == null) {
			shapes[index] = initialiseShape(ShapeRegistry.getClassIdentifier(index));
			shapes[index].update();
		}
		
		return shapes[index];
	}
	
	public int getIndex() {
		return index;
	}
	
	public void setIndex(int index) {
		this.index = index;
		BaseScreen.shouldUpdatePersistence = true;
	}
	
	public void setVisible(boolean visible) {
		this.visible = visible;
		BaseScreen.shouldUpdatePersistence = true;
	}
	
	// Typed instance name, null for the default
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name == null || name.trim().isEmpty() ? null : name.trim();
		BaseScreen.shouldUpdatePersistence = true;
	}
	
	public boolean isVisible() {
		return visible;
	}
	
	public boolean hasOrigin() {
		return origin != null;
	}
	
	public int getOriginX() {
		return origin.x;
	}
	
	public int getOriginY() {
		return origin.y;
	}
	
	public int getOriginZ() {
		return origin.z;
	}
	
	public String toPersistence() {
		String persistenceData = "";
		persistenceData += PERSISTENCE_INDEX + "=" + index + ";";
		persistenceData += PERSISTENCE_ORIGIN + "=" + origin.x + "," + origin.y + "," + origin.z + ";";
		persistenceData += PERSISTENCE_VISIBLE + "=" + visible + ";";
		persistenceData += PERSISTENCE_SHAPECOLOUR + "=" + colourShapeR + "," + colourShapeG + "," + colourShapeB + "," + colourShapeA + ";";
		persistenceData += PERSISTENCE_ORIGINCOLOUR + "=" + colourOriginR + "," + colourOriginG + "," + colourOriginB + "," + colourOriginA + ";";
		persistenceData += PERSISTENCE_SHAPECUBESIZE + "=" + shapeCubeSize + ";";
		persistenceData += PERSISTENCE_ORIGINCUBESIZE + "=" + originCubeSize + ";";
		persistenceData += PERSISTENCE_EXCLUSIONS + "=" + exclusionsPersistence() + ";";
		for(Shape s: shapes) {
			if(s != null) {
				persistenceData += s.getClass().getName() + "=" + s.toPersistence() + ";";
			}
		}
		if(name != null) persistenceData += PERSISTENCE_NAME + "=" + PresetStore.encode(name) + ";";
		return persistenceData;
	}

	private String exclusionsPersistence() {
		String exclusions = "";
		for(ExclusionBox b: exclusionBoxes) exclusions += (exclusions.isEmpty() ? "" : ",") + (b.enabled ? 1 : 0) + "," + b.minX + "," + b.minY + "," + b.minZ + "," + b.maxX + "," + b.maxY + "," + b.maxZ;
		return exclusions;
	}

	/**
	 * Preset of the current shape (E8), in the same "key=value;" format as the world save: type,
	 * origin, exclusion boxes, the shape's values, the name when typed, and the world it came from
	 * (for the slot label). Colours and cube sizes are the set's look, not the shape: left out
	 */
	public String toPresetString(String world) {
		Shape s = getShape();
		String data = PERSISTENCE_INDEX + "=" + index + ";";
		data += PERSISTENCE_ORIGIN + "=" + origin.x + "," + origin.y + "," + origin.z + ";";
		data += PERSISTENCE_EXCLUSIONS + "=" + exclusionsPersistence() + ";";
		data += s.getClass().getName() + "=" + s.toPersistence() + ";";
		if(name != null) data += PERSISTENCE_NAME + "=" + PresetStore.encode(name) + ";";
		data += PresetStore.KEY_WORLD + "=" + PresetStore.encode(world == null ? "" : world) + ";";
		return data;
	}
	
	public void restorePersistence(String persistenceData) {
		String[] splitData = persistenceData.split(";");
		for(String entry: splitData) {
			int separatorIndex = entry.indexOf("=");
			if(separatorIndex > 0) {
				String key = entry.substring(0, separatorIndex);
				String value = entry.substring(separatorIndex + 1);
				if(key.equals(PERSISTENCE_INDEX)) {
					index = Integer.parseInt(value);
				}else if(key.equals(PERSISTENCE_ORIGIN)) {
					String[] splitEntry = value.split(",");
					if(splitEntry.length == 3) {
						origin.x = Integer.parseInt(splitEntry[0]);
						origin.y = Integer.parseInt(splitEntry[1]);
						origin.z = Integer.parseInt(splitEntry[2]);
					}
				}else if (key.equals(PERSISTENCE_VISIBLE)) {
					visible = Boolean.parseBoolean(value);
				}else if(key.equals(PERSISTENCE_SHAPECOLOUR)) {
					String[] splitEntry = value.split(",");
					if(splitEntry.length == 4) {
						colourShapeR = Float.parseFloat(splitEntry[0]);
						colourShapeG = Float.parseFloat(splitEntry[1]);
						colourShapeB = Float.parseFloat(splitEntry[2]);
						colourShapeA = Float.parseFloat(splitEntry[3]);
					}
				}else if(key.equals(PERSISTENCE_ORIGINCOLOUR)) {
					String[] splitEntry = value.split(",");
					if(splitEntry.length == 4) {
						colourOriginR = Float.parseFloat(splitEntry[0]);
						colourOriginG = Float.parseFloat(splitEntry[1]);
						colourOriginB = Float.parseFloat(splitEntry[2]);
						colourOriginA = Float.parseFloat(splitEntry[3]);
					}
				}else if(key.equals(PERSISTENCE_SHAPECUBESIZE)){
					shapeCubeSize = Double.parseDouble(value);
				}else if(key.equals(PERSISTENCE_ORIGINCUBESIZE)){
					originCubeSize = Double.parseDouble(value);
				}else if(key.equals(PERSISTENCE_NAME)) {
					name = PresetStore.decode(value).trim();
					if(name.isEmpty()) name = null;
				}else if(key.equals(PERSISTENCE_EXCLUSIONS)) {
					String[] v = value.split(",");
					for(int i = 0;i < numExclusionBoxes && i * 7 + 6 < v.length;++i) {
						ExclusionBox b = exclusionBoxes[i];
						b.enabled = "1".equals(v[i * 7]);
						b.minX = Integer.parseInt(v[i * 7 + 1]);
						b.minY = Integer.parseInt(v[i * 7 + 2]);
						b.minZ = Integer.parseInt(v[i * 7 + 3]);
						b.maxX = Integer.parseInt(v[i * 7 + 4]);
						b.maxY = Integer.parseInt(v[i * 7 + 5]);
						b.maxZ = Integer.parseInt(v[i * 7 + 6]);
					}
				}else {
					int index = ShapeRegistry.getShapeId(key);
					if(index >= 0) {
						shapes[index] = initialiseShape(key);
						shapes[index].restorePersistence(value);
					}
				}
			}
		}
		index = Math.max(0, Math.min(shapes.length - 1, index));
	}

	/**
	 * Load a preset (E8) into this set, replacing the current shape: type, the shape's values, origin,
	 * exclusion boxes and name. Everything is parsed and checked first; on a problem nothing changes
	 * and a translation key is returned (unknown shape type: a preset from a jar with a shape this one
	 * lacks). The instance of that type is reused when the set already has one (other types' instances
	 * stay, reachable from the type dropdown), created with its defaults otherwise. Does not
	 * regenerate: applyPreset does, this split lets the harness run without the loader
	 */
	public String applyPresetValues(String data) {
		Map<String, String> e = PresetStore.entries(data);
		String shapeClass = PresetStore.describe(data)[0];
		int id = shapeClass == null ? -1 : ShapeRegistry.getShapeId(shapeClass);
		if(id < 0) return "screen.buildguide.preset.unknowntype";
		int[] o = new int[3];
		int[][] boxes = new int[numExclusionBoxes][7];
		try {
			String[] v = e.containsKey(PERSISTENCE_ORIGIN) ? e.get(PERSISTENCE_ORIGIN).split(",") : new String[0];
			if(v.length != 3) return "screen.buildguide.preset.invalid";
			for(int i = 0;i < 3;++i) o[i] = Integer.parseInt(v[i]);
			String[] x = e.containsKey(PERSISTENCE_EXCLUSIONS) ? e.get(PERSISTENCE_EXCLUSIONS).split(",") : new String[0];
			for(int i = 0;i < numExclusionBoxes && i * 7 + 6 < x.length;++i) {
				boxes[i][0] = "1".equals(x[i * 7]) ? 1 : 0;
				for(int k = 1;k < 7;++k) boxes[i][k] = Integer.parseInt(x[i * 7 + k]);
			}
		}catch(NumberFormatException ex) {
			return "screen.buildguide.preset.invalid";
		}

		Shape s = shapes[id];
		if(s == null) {
			s = initialiseShape(shapeClass);
			shapes[id] = s;
		}
		s.loadPresetValues(e.get(shapeClass));
		index = id;
		for(int i = 0;i < numExclusionBoxes;++i) {
			ExclusionBox b = exclusionBoxes[i];
			b.enabled = boxes[i][0] == 1;
			b.minX = boxes[i][1];
			b.minY = boxes[i][2];
			b.minZ = boxes[i][3];
			b.maxX = boxes[i][4];
			b.maxY = boxes[i][5];
			b.maxZ = boxes[i][6];
		}
		setName(e.containsKey(PERSISTENCE_NAME) ? PresetStore.decode(e.get(PERSISTENCE_NAME)) : null);
		setOrigin(o[0], o[1], o[2]); // a changed origin requests a scan (P4)
		onExclusionsChanged();
		BaseScreen.shouldUpdatePersistence = true; // the world save must write the loaded shape
		return null;
	}

	// applyPresetValues, then one regeneration of the loaded shape (it requests its own scan)
	public String applyPreset(String data) {
		String problem = applyPresetValues(data);
		if(problem == null) getShape().update();
		return problem;
	}

	public ExclusionBox getExclusionBox(int i) {
		return exclusionBoxes[i];
	}
	
	// Enabled boxes as {minX, minY, minZ, maxX, maxY, maxZ} with corners sorted, for ValidationState
	public List<int[]> getActiveExclusionBoxes() {
		List<int[]> result = new ArrayList<int[]>();
		for(ExclusionBox b: exclusionBoxes) {
			if(!b.enabled) continue;
			result.add(new int[] {Math.min(b.minX, b.maxX), Math.min(b.minY, b.maxY), Math.min(b.minZ, b.maxZ), Math.max(b.minX, b.maxX), Math.max(b.minY, b.maxY), Math.max(b.minZ, b.maxZ)});
		}
		return result;
	}
	
	// Exclusion boxes changed in the GUI: push to every instantiated shape (immediate feedback) and rescan
	public void onExclusionsChanged() {
		List<int[]> boxes = getActiveExclusionBoxes();
		for(Shape s: shapes) {
			if(s == null) continue;
			s.getValidationState().setExclusionBoxes(boxes);
			s.getValidationState().requestScan();
		}
		BaseScreen.shouldUpdatePersistence = true;
	}
	
	public static class Origin {
		public int x, y, z;
		
		public Origin(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
