package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.WorldUpdateGate;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;

public abstract class Shape implements IValidatable {
	public ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
	// Panel sections (see declareSection), shown as accordion headers by ShapeScreen. UI state only:
	// never persisted. A shape that declares none has one implicit section, "Properties"
	private List<Translatable> sectionNames = new ArrayList<Translatable>();
	private Map<Property<?>, Integer> propertySections = new IdentityHashMap<Property<?>, Integer>();
	// Open accordion section, -1 = all closed. Per instance and transient: survives tab switches and
	// the preview (same instance), resets on restart
	private int openSection = 0;
	// Top of the open section's rows, set by ShapeScreen from the header above it
	private int rowsTop = ShapeScreen.basePropertiesY;
	// Row-count pass (countRows): placeRow/hideRow only count, nothing moves or changes visibility
	private boolean counting = false;
	private int countingSection, countedRows;
	// Properties that only exist for the panel (row owners, buttons): shown and laid out, never persisted
	private List<Property<?>> guiOnlyProperties = new ArrayList<Property<?>>();
	// Persisted properties that are no longer shown (kept in `properties` so saves stay aligned)
	private Set<Property<?>> hiddenProperties = Collections.newSetFromMap(new IdentityHashMap<Property<?>, Boolean>());
	// Reset support: constructor values of every persisted property (captured by ShapeSet right after
	// construction, before any persistence is restored) and the properties Reset must never touch
	private Map<Property<?>, Object> defaults = new IdentityHashMap<Property<?>, Object>();
	private Set<Property<?>> resetProtected = Collections.newSetFromMap(new IdentityHashMap<Property<?>, Boolean>());
	public IShapeBuffer buffer;
	// Live apply: the buffer drawn in the world, when the render handler defers world updates
	// (AbstractRenderHandler.deferredWorldUpdates): the last generated buffer it uploaded, kept on screen while
	// newer generations wait for the WorldUpdateGate. Render thread only; stays null for handlers that do not defer
	public transient IShapeBuffer shownBuffer = null;
	public final transient WorldUpdateGate worldGate = new WorldUpdateGate(System::currentTimeMillis);
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
	// Advanced only by a successful generation (finishGeneration); volatile so the preview can poll it cheaply
	private volatile long generation = 0;
	
	// Validation (all shapes): every block emitted through addShapeCube is recorded here as a
	// packed local position; the render handler scans it and the GUI reads the state
	protected final Set<Long> expectedBlocks = new HashSet<Long>();
	private transient ValidationState validationState = new ValidationState();
	private transient boolean validateNextRender = false;
	// World overlay of validation errors (coloured cubes), rebuilt by the render handler when the
	// state version changes; rendered right after the shape's own buffer
	public transient IShapeBuffer overlayBuffer = null;
	public transient long overlayVersion = -1;
	public transient long overlayBuiltAt = 0;
	
	protected abstract void updateShape(IShapeBuffer builder) throws Exception;
	
	public void update() {
		BaseScreen.shouldUpdatePersistence = true;
		
		ready = false;
		vertexBufferUnpacked = false;
		if(BuildGuide.config.asyncEnabled.value) {
			cancelFuture();
		}
		if(buffer != null && buffer != shownBuffer) {
			buffer.close(); // Can only be done in render thread; the buffer still drawn in the world is closed when it is replaced
		}
		worldGate.request();
		future = executor.submit(() -> {
			try {
				lock.lock();
				ready = false; // Again in case of a second thread started before a first thread ended
				vertexBufferUnpacked = false;
				error = false;
				long started = System.currentTimeMillis();
				doUpdate();
				TimingLog.record(TimingLog.GENERATION, System.currentTimeMillis() - started, nBlocks);
			}catch(InterruptedException e) {
				error = true;
			}catch(Exception e) {
				error = true;
				BuildGuide.logHandler.debugThrowable("An exception occurred while generating a shape.", e);
			}finally {
				finishGeneration();
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
	
	/**
	 * End of every generation, successful or not, called in update()'s finally while the lock is
	 * still held. Only a successful one advances `generation`: a cancelled or failed generation
	 * leaves expectedBlocks partial and must not look like new content.
	 *
	 * Memory model: `ready` is a plain field, so writing `generation` before it does not by itself
	 * make the pair visible together. What does is the lock: this runs before unlock(), so a reader
	 * that takes the same lock (PreviewModel.snapshot) sees ready, error and generation consistent.
	 * A getGeneration() read without the lock is only a hint that a new snapshot is worth trying.
	 */
	void finishGeneration() {
		completedAt = System.currentTimeMillis();
		if(!error) ++generation;
		ready = true;
	}

	// Number of successfully completed generations (see finishGeneration)
	public long getGeneration() {
		return generation;
	}

	private void cancelFuture() {
		if(future != null && !(future.isDone() || future.isCancelled())) future.cancel(true);
	}
	
	private void doUpdate() throws Exception {
		nBlocks = 0;
		// Invalidate before clearing: block events check isValidated() and never touch expectedBlocks
		validationState.invalidate();
		expectedBlocks.clear();
		buffer = BuildGuide.shapeHandler.newBuffer();
		buffer.setColour((int) (255 * shapeSet.getShapeColourR()), (int) (255 * shapeSet.getShapeColourG()), (int) (255 * shapeSet.getShapeColourB()), (int) (255 * shapeSet.getShapeColourA()));
		updateShape(buffer);
		// Generation finished: ask the render handler for a fresh full scan (it reads the world, so it
		// cannot run here). A cancelled or failed generation throws before this line
		validationState.requestScan();
		buffer.setColour((int) (255 * shapeSet.getOriginColourR()), (int) (255 * shapeSet.getOriginColourG()), (int) (255 * shapeSet.getOriginColourB()), (int) (255 * shapeSet.getOriginColourA()));
		addOriginCube(buffer);
	}
	
	private void addCube(IShapeBuffer buffer, double x, double y, double z, double s) throws InterruptedException {
		if(Thread.currentThread().isInterrupted()) throw new InterruptedException(); //Interrupt check for concurrent shape generation
		
		CubeMesh.push(buffer, x, y, z, s);
	}
	
	protected void addShapeCube(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		addCube(buffer, x + 0.5 - shapeSet.getShapeCubeSize() / 2, y + 0.5 - shapeSet.getShapeCubeSize() / 2, z + 0.5 - shapeSet.getShapeCubeSize() / 2, shapeSet.getShapeCubeSize());
		expectedBlocks.add(LocalPos.pack(x, y, z));
		
		++nBlocks;
	}
	
	// Emit only if this position has not been emitted in this generation; returns whether it was new
	protected boolean addShapeCubeIfNew(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		if(!expectedBlocks.add(LocalPos.pack(x, y, z))) return false;
		addShapeCube(buffer, x, y, z);
		return true;
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
	 * Declares a panel section (an accordion header) and returns its index. Properties assigned
	 * to a section are shown only while it is open; properties never assigned belong to section
	 * 0. Shapes that declare no section get one implicit "Properties" section.
	 */
	protected int declareSection(Translatable name) {
		sectionNames.add(name);
		return sectionNames.size() - 1;
	}

	public int getSectionCount() {
		return Math.max(1, sectionNames.size());
	}

	public Translatable getSectionName(int section) {
		return sectionNames.isEmpty() ? new Translatable("property.buildguide.section.properties") : sectionNames.get(section);
	}

	public int getOpenSection() {
		return openSection;
	}

	// Opens one section (closing the previous one) or closes all with -1, then lays the rows out again
	public void setOpenSection(int section) {
		openSection = section;
		onSelectedInGUI();
	}

	public void setRowsTop(int y) {
		rowsTop = y;
	}

	/**
	 * Rows the given section takes when open: a dry run of onSelectedInGUI, so it follows custom
	 * layouts (a point row is four properties on one row) and dynamic counts (Point count).
	 * Moves nothing and changes no visibility.
	 */
	public int countRows(int section) {
		counting = true;
		countingSection = section;
		countedRows = 0;
		try {
			onSelectedInGUI();
		}finally {
			counting = false;
		}
		return countedRows;
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
	
	/**
	 * Preset load (E8): every property back to its default, the protected ones too (a preset from an
	 * older jar with fewer values must not keep this instance's control points), then the preset's
	 * values on top. A running generation is cancelled first and the values change under the lock,
	 * so the executor never reads them half-set. No update(): the caller regenerates once. Returns
	 * false if a value could not be parsed (restorePersistence sets error).
	 */
	@SuppressWarnings("unchecked")
	public boolean loadPresetValues(String persistenceData) {
		cancelFuture();
		lock.lock();
		try {
			for(Property<?> p: properties) {
				Object def = defaults.get(p);
				if(def == null || def instanceof Runnable) continue;
				((Property<Object>) p).setValue(def);
			}
			restorePersistence(persistenceData);
		}finally {
			lock.unlock();
		}
		return !error;
	}

	// Everything the screen must add as widgets: the persisted properties that are not hidden plus the GUI-only ones
	public List<Property<?>> getGuiProperties() {
		if(guiOnlyProperties.isEmpty() && hiddenProperties.isEmpty()) return properties;
		List<Property<?>> all = new ArrayList<Property<?>>();
		for(Property<?> p: properties) if(!hiddenProperties.contains(p)) all.add(p);
		all.addAll(guiOnlyProperties);
		return all;
	}

	// True if the property is not hidden and belongs to the open section (the counted one during countRows)
	protected boolean isShown(Property<?> p) {
		if(hiddenProperties.contains(p)) return false;
		Integer section = propertySections.get(p);
		return (section == null ? 0 : section) == (counting ? countingSection : openSection);
	}

	// Places all given properties on the same row and shows them; returns the next row
	protected int placeRow(int row, Property<?>... props) {
		if(counting) {
			countedRows = Math.max(countedRows, row + 1);
			return row + 1;
		}
		for(Property<?> p: props) {
			p.setX(ShapeScreen.basePropertiesX);
			p.setY(rowsTop + row * Property.rowHeight);
			p.setVisibility(true);
		}
		return row + 1;
	}

	// Hides properties that are not laid out (a no-op during countRows)
	protected void hideRow(Property<?>... props) {
		if(counting) return;
		for(Property<?> p: props) p.setVisibility(false);
	}

	// Default layout: the open section's properties in list order, one per row
	public void onSelectedInGUI() {
		int row = 0;
		for(Property<?> p: properties) {
			if(isShown(p)) row = placeRow(row, p);
			else hideRow(p);
		}
	}

	public void onDeselectedInGUI() {
		for(Property<?> p: properties) {
			p.setVisibility(false);
		}
		for(Property<?> p: guiOnlyProperties) p.setVisibility(false);
	}
	
	public int getNumberOfBlocks() {
		if(!ready) return 0;
		return nBlocks;
	}
	
	public long getHowLongAgoCompletedMillis() {
		return System.currentTimeMillis() - completedAt;
	}
	
	// IValidatable: every shape can be checked against the world
	public Set<Long> getExpectedBlocks() {
		return expectedBlocks;
	}
	
	public ValidationState getValidationState() {
		return validationState;
	}
	
	// Manual validation request (Validate button); the render handler picks it up on the next frame
	public void triggerValidation() {
		validateNextRender = true;
	}
	
	// Returns true exactly once per request
	public boolean consumeValidateRequest() {
		if(validateNextRender) {
			validateNextRender = false;
			return true;
		}
		return false;
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
