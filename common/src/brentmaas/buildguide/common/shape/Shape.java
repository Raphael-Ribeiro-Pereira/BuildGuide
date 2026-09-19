package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertySection;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;

public abstract class Shape {
	public ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
	// Optional panel sections (see declareSection). The selector is UI state: not in `properties`, never persisted
	private PropertySection sectionSelector = null;
	private Map<Property<?>, Integer> propertySections = new IdentityHashMap<Property<?>, Integer>();
	// Properties that only exist for the panel (row owners, buttons): shown and laid out, never persisted
	private List<Property<?>> guiOnlyProperties = new ArrayList<Property<?>>();
	// Persisted properties that are no longer shown (kept in `properties` so saves stay aligned)
	private Set<Property<?>> hiddenProperties = Collections.newSetFromMap(new IdentityHashMap<Property<?>, Boolean>());
	// Reset support: constructor values of every persisted property (captured by ShapeSet right after
	// construction, before any persistence is restored) and the properties Reset must never touch
	private Map<Property<?>, Object> defaults = new IdentityHashMap<Property<?>, Object>();
	private Set<Property<?>> resetProtected = Collections.newSetFromMap(new IdentityHashMap<Property<?>, Boolean>());
	public IShapeBuffer buffer;
	private int nBlocks = 0;
	public boolean ready = false;
	public boolean vertexBufferUnpacked = false;
	protected ShapeSet shapeSet;
	
	protected double originOffsetX = 0.0;
	protected double originOffsetY = 0.0;
	protected double originOffsetZ = 0.0;
	
	private static ExecutorService executor = Executors.newCachedThreadPool();
	public ReentrantLock lock = new ReentrantLock();
	private Future<?> future = null;
	private long completedAt = 0;
	public boolean error = false;
	
	protected abstract void updateShape(IShapeBuffer builder) throws Exception;
	
	public void update() {
		BaseScreen.shouldUpdatePersistence = true;
		
		ready = false;
		vertexBufferUnpacked = false;
		if(BuildGuide.config.asyncEnabled.value) {
			cancelFuture();
		}
		if(buffer != null) {
			buffer.close(); // Can only be done in render thread
		}
		future = executor.submit(() -> {
			try {
				lock.lock();
				ready = false; // Again in case of a second thread started before a first thread ended
				vertexBufferUnpacked = false;
				error = false;
				doUpdate();
			}catch(InterruptedException e) {
				error = true;
			}catch(Exception e) {
				error = true;
				BuildGuide.logHandler.debugThrowable("An exception occurred while generating a shape.", e);
			}finally {
				completedAt = System.currentTimeMillis();
				ready = true;
				lock.unlock();
			}
		});
		if(!BuildGuide.config.asyncEnabled.value) {
			try {
				future.get();
			}catch(Exception e) {
				error = true;
				e.printStackTrace();
			}
		}
	}
	
	private void cancelFuture() {
		if(future != null && !(future.isDone() || future.isCancelled())) future.cancel(true);
	}
	
	private void doUpdate() throws Exception {
		nBlocks = 0;
		buffer = BuildGuide.shapeHandler.newBuffer();
		buffer.setColour((int) (255 * shapeSet.getShapeColourR()), (int) (255 * shapeSet.getShapeColourG()), (int) (255 * shapeSet.getShapeColourB()), (int) (255 * shapeSet.getShapeColourA()));
		updateShape(buffer);
		// Generation finished: ask the render handler for a fresh full scan (it reads the world, so it
		// cannot run here). A cancelled or failed generation throws before this line
		if(this instanceof IValidatable validatable) validatable.getValidationState().requestScan();
		buffer.setColour((int) (255 * shapeSet.getOriginColourR()), (int) (255 * shapeSet.getOriginColourG()), (int) (255 * shapeSet.getOriginColourB()), (int) (255 * shapeSet.getOriginColourA()));
		addOriginCube(buffer);
	}
	
	private void addCube(IShapeBuffer buffer, double x, double y, double z, double s) throws InterruptedException {
		if(Thread.currentThread().isInterrupted()) throw new InterruptedException(); //Interrupt check for concurrent shape generation
		
		//-X
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x, y, z+s);
		buffer.pushVertex(x, y+s, z+s);
		buffer.pushVertex(x, y+s, z);
		
		//-Y
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x+s, y, z);
		buffer.pushVertex(x+s, y, z+s);
		buffer.pushVertex(x, y, z+s);
		
		//-Z
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x, y+s, z);
		buffer.pushVertex(x+s, y+s, z);
		buffer.pushVertex(x+s, y, z);
		
		//+X
		buffer.pushVertex(x+s, y, z);
		buffer.pushVertex(x+s, y+s, z);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x+s, y, z+s);
		
		//+Y
		buffer.pushVertex(x, y+s, z);
		buffer.pushVertex(x, y+s, z+s);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x+s, y+s, z);
		
		//+Z
		buffer.pushVertex(x, y, z+s);
		buffer.pushVertex(x+s, y, z+s);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x, y+s, z+s);
	}
	
	protected void addShapeCube(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		addCube(buffer, x + 0.5 - shapeSet.getShapeCubeSize() / 2, y + 0.5 - shapeSet.getShapeCubeSize() / 2, z + 0.5 - shapeSet.getShapeCubeSize() / 2, shapeSet.getShapeCubeSize());
		
		++nBlocks;
	}
	
	protected void addOriginCube(IShapeBuffer buffer) throws InterruptedException {
		addCube(buffer, 0.5 - shapeSet.getOriginCubeSize() / 2 + originOffsetX, 0.5 - shapeSet.getOriginCubeSize() / 2 + originOffsetY, 0.5 - shapeSet.getOriginCubeSize() / 2 + originOffsetZ, shapeSet.getOriginCubeSize());
	}
	
	protected void setOriginOffset(double dx, double dy, double dz) {
		originOffsetX = dx;
		originOffsetY = dy;
		originOffsetZ = dz;
	}
	
	// Player block position relative to this shape set's origin, i.e. in the local coordinates shapes use
	protected ShapeSet.Origin getPlayerPositionLocal() {
		ShapeSet.Origin pos = BuildGuide.shapeHandler.getPlayerPosition();
		return new ShapeSet.Origin(pos.x - shapeSet.getOriginX(), pos.y - shapeSet.getOriginY(), pos.z - shapeSet.getOriginZ());
	}
	
	/**
	 * Declares a panel section and returns its index. Properties assigned to a section are
	 * shown only while it is selected; properties never assigned are shown in every section.
	 * Shapes that declare no section keep the plain single-column layout.
	 */
	protected int declareSection(Translatable name) {
		if(sectionSelector == null) sectionSelector = new PropertySection(new Translatable("property.buildguide.section"), () -> onSelectedInGUI());
		return sectionSelector.addSection(name);
	}
	
	protected void assignSection(int section, Property<?>... props) {
		for(Property<?> p: props) propertySections.put(p, section);
	}
	
	protected void addGuiOnly(Property<?> p) {
		guiOnlyProperties.add(p);
	}
	
	// Keep a property in the persistence list but out of the panel (for properties that became inert)
	protected void hideFromGui(Property<?> p) {
		hiddenProperties.add(p);
	}
	
	// Called once by ShapeSet after construction: remember what "default" means for this shape
	public void captureDefaults() {
		for(Property<?> p: properties) defaults.put(p, p.value);
	}
	
	// Properties Reset must leave alone (e.g. control points captured from the player position)
	protected void protectFromReset(Property<?>... props) {
		for(Property<?> p: props) resetProtected.add(p);
	}
	
	/**
	 * Restore defaults for the properties currently shown (the selected section when the shape
	 * has sections, all of them otherwise), except protected ones, then regenerate once.
	 * Property.setValue does not run onPress, so there is exactly one update().
	 */
	@SuppressWarnings("unchecked")
	public void resetShownToDefaults() {
		boolean changed = false;
		for(Property<?> p: properties) {
			if(resetProtected.contains(p) || !isShown(p) || !defaults.containsKey(p)) continue;
			Object def = defaults.get(p);
			if(def == null || def instanceof Runnable) continue; // buttons and row owners hold no value
			((Property<Object>) p).setValue(def);
			changed = true;
		}
		if(changed) update();
	}
	
	// Everything the screen must add as widgets: the persisted properties plus the section selector
	public List<Property<?>> getGuiProperties() {
		if(sectionSelector == null && guiOnlyProperties.isEmpty() && hiddenProperties.isEmpty()) return properties;
		List<Property<?>> all = new ArrayList<Property<?>>();
		for(Property<?> p: properties) if(!hiddenProperties.contains(p)) all.add(p);
		all.addAll(guiOnlyProperties);
		if(sectionSelector != null) all.add(sectionSelector);
		return all;
	}
	
	// True if the property belongs to the selected section or to no section
	protected boolean isShown(Property<?> p) {
		if(hiddenProperties.contains(p)) return false;
		Integer section = propertySections.get(p);
		return section == null || sectionSelector == null || section == sectionSelector.value;
	}
	
	// Places all given properties on the same row and shows them; returns the next row
	protected int placeRow(int row, Property<?>... props) {
		for(Property<?> p: props) {
			p.setX(ShapeScreen.basePropertiesX);
			p.setY(ShapeScreen.basePropertiesY + row * AbstractWidgetHandler.defaultSize);
			p.setVisibility(true);
		}
		return row + 1;
	}
	
	// Row 0 is the section selector when sections exist; returns the first row for properties
	protected int placeSectionSelector() {
		if(sectionSelector == null) return 0;
		return placeRow(0, sectionSelector);
	}
	
	public void onSelectedInGUI() {
		if(sectionSelector == null) {
			for(int i = 0;i < properties.size();++i) {
				properties.get(i).setX(ShapeScreen.basePropertiesX);
				properties.get(i).setY(ShapeScreen.basePropertiesY + i * AbstractWidgetHandler.defaultSize);
				properties.get(i).setVisibility(true);
			}
			return;
		}
		
		int row = placeSectionSelector();
		for(Property<?> p: properties) {
			if(isShown(p)) row = placeRow(row, p);
			else p.setVisibility(false);
		}
	}
	
	public void onDeselectedInGUI() {
		for(Property<?> p: properties) {
			p.setVisibility(false);
		}
		for(Property<?> p: guiOnlyProperties) p.setVisibility(false);
		if(sectionSelector != null) sectionSelector.setVisibility(false);
	}
	
	public int getNumberOfBlocks() {
		if(!ready) return 0;
		return nBlocks;
	}
	
	public long getHowLongAgoCompletedMillis() {
		return System.currentTimeMillis() - completedAt;
	}
	
	public final String getTranslationKey() {
		return ShapeRegistry.getTranslationKey(this);
	}
	
	public String toPersistence() {
		String persistenceData = "";
		for(Property<?> property: properties) {
			persistenceData += property.getStringValue() + ",";
		}
		return persistenceData.substring(0, persistenceData.length() - 1);
	}
	
	public void restorePersistence(String persistenceData) {
		String splitData[] = persistenceData.split(",");
		boolean success = true;
		for(int i = 0;i < Math.min(properties.size(), splitData.length);++i) {
			success = success & properties.get(i).setValueFromString(splitData[i]);
		}
		error = !success;
	}
}
