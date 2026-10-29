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
import brentmaas.buildguide.common.WorldUpload;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.BaseScreen;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;

public abstract class Shape implements IValidatable, IFocusParts {
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
	// The buffer the gate let through, while its parts are still being sent to the GPU (render thread)
	public final transient WorldUpload upload = new WorldUpload();
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
	// packed local position; the render handler scans it and the GUI reads the state. Primitive (LongHashSet,
	// performance step 4): no Long and no node per block, in the order the blocks were emitted
	protected final LongHashSet expectedBlocks = new LongHashSet();
	private transient ValidationState validationState = new ValidationState();
	private transient boolean validateNextRender = false;
	// World overlay of validation errors (coloured cubes), rebuilt by the render handler when the
	// state version changes; rendered right after the shape's own buffer
	public transient IShapeBuffer overlayBuffer = null;
	public transient long overlayVersion = -1;
	public transient long overlayBuiltAt = 0;
	// Build focus (FocusFilter): the focus the generation running emits with (generation thread), the one the
	// latest buffer was made with (null: every block) and how many cubes it holds; set under the lock
	private transient FocusFilter emitFocus = null;
	private transient FocusFilter bufferFocus = null;
	private transient int worldBlocks = 0;
	// A focus change makes only the world buffer again (setFocus). Tickets: the last focus change asked for and
	// the last one done, so the render thread waits for its buffer (isRefocusing) instead of taking the old one
	private Future<?> refocusFuture = null;
	private volatile long refocusAsked = 0, refocusDone = 0;
	
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
				// A cancelled or failed generation never reaches the world (it needs !error): its vertex data
				// can go now. This task owns the buffer and stopped writing to it
				if(error && buffer != null) buffer.releaseVertexData();
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
	
	/**
	 * The shape is gone (its shape set was removed): its generation stops and what it holds is freed. The GPU
	 * buffers (the one in the world, the error overlay) are closed here, on the render thread. The vertex data
	 * of the last generation goes under the lock: at once when no generation runs, else by a task that waits
	 * for the running one to stop (it may still be writing). Nothing draws this shape afterwards.
	 */
	public void dispose() {
		cancelFuture();
		if(refocusFuture != null && !refocusFuture.isDone()) refocusFuture.cancel(true);
		if(buffer != null && buffer != shownBuffer) buffer.close();
		if(shownBuffer != null) shownBuffer.close();
		shownBuffer = null;
		if(overlayBuffer != null) overlayBuffer.close();
		overlayBuffer = null;
		Runnable release = () -> {
			lock.lock();
			try {
				if(buffer != null) buffer.releaseVertexData();
			}finally {
				lock.unlock();
			}
		};
		if(lock.tryLock()) {
			try {
				release.run();
			}finally {
				lock.unlock();
			}
		}else {
			executor.submit(release);
		}
	}
	
	private void doUpdate() throws Exception {
		nBlocks = 0;
		// Invalidate before clearing: block events check isValidated() and never touch expectedBlocks
		validationState.invalidate();
		expectedBlocks.clear();
		// The previous generation's buffer, unless it is the one the world shows: a newer generation replaces it,
		// so its vertex data can go (the shown one already freed it when it was sent; a second release does
		// nothing). Under the lock: no generation still writes to it and the render thread cannot be sending it
		if(buffer != null && buffer != shownBuffer) buffer.releaseVertexData();
		buffer = BuildGuide.shapeHandler.newBuffer();
		setShapeColour(buffer);
		// Build focus: only the selected blocks reach the buffer while it is generated (the threshold needs the
		// total, known only at the end). Every block is still expected
		worldBlocks = 0;
		emitFocus = selectedFocus();
		updateShape(buffer);
		FocusFilter focus = focusFor(nBlocks);
		if(emitFocus != null && focus == null) {
			// Selected, but the shape is below the threshold (or the selection selects everything): every block
			buffer.releaseVertexData();
			buffer = BuildGuide.shapeHandler.newBuffer();
			setShapeColour(buffer);
			worldBlocks = fillWorldBuffer(buffer, null);
		}
		emitFocus = null;
		bufferFocus = focus;
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
	
	// Rotate 90 degrees (2026-10-28): the generators work unturned; addShapeCube turns every block it emits by the
	// set's quarter turns, so the world, the expected set, the validation and Fill all see the turned shape. A
	// lookup by a stored position (focus, colour, water) turns it back first (the *Stored methods)
	public static int turnX(int x, int z, int turns) {
		switch(turns & 3) {
		case 1: return -z;
		case 2: return -x;
		case 3: return z;
		default: return x;
		}
	}

	public static int turnZ(int x, int z, int turns) {
		switch(turns & 3) {
		case 1: return x;
		case 2: return -z;
		case 3: return -x;
		default: return z;
		}
	}

	private int quarterTurns() {
		return shapeSet == null ? 0 : shapeSet.getQuarterTurns();
	}

	// isInFocus for a stored (turned) position
	public boolean isInFocusStored(FocusFilter f, int x, int y, int z) {
		int back = (4 - quarterTurns()) & 3;
		return isInFocus(f, turnX(x, z, back), y, turnZ(x, z, back));
	}

	// waterTest for stored (turned) positions
	public final java.util.function.LongPredicate storedWaterTest() {
		java.util.function.LongPredicate test = waterTest();
		int back = (4 - quarterTurns()) & 3;
		if(test == null || back == 0) return test;
		return pos -> {
			int x = LocalPos.unpackX(pos), z = LocalPos.unpackZ(pos);
			return test.test(LocalPos.pack(turnX(x, z, back), LocalPos.unpackY(pos), turnZ(x, z, back)));
		};
	}

	protected void addShapeCube(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		int turns = quarterTurns(), tx = turnX(x, z, turns), tz = turnZ(x, z, turns);
		if(emitFocus == null || isInFocus(emitFocus, x, y, z)) {
			addCube(buffer, tx + 0.5 - shapeSet.getShapeCubeSize() / 2, y + 0.5 - shapeSet.getShapeCubeSize() / 2, tz + 0.5 - shapeSet.getShapeCubeSize() / 2, shapeSet.getShapeCubeSize());
			++worldBlocks;
		}else if(Thread.currentThread().isInterrupted()) {
			throw new InterruptedException();
		}
		expectedBlocks.add(LocalPos.pack(tx, y, tz));

		++nBlocks;
	}

	private void setShapeColour(IShapeBuffer buffer) {
		buffer.setColour((int) (255 * shapeSet.getShapeColourR()), (int) (255 * shapeSet.getShapeColourG()), (int) (255 * shapeSet.getShapeColourB()), (int) (255 * shapeSet.getShapeColourA()));
	}

	// The colour of one cube when the world buffer is filled again from the expected set (fillWorldBuffer): the
	// shape's colour, already set, by default; shapes that colour cubes one by one set it here
	protected void colourCube(IShapeBuffer buffer, int x, int y, int z) {}

	// Water (2026-10-22): the last generation's positions that want water instead of a block (local), for the
	// validation and Fill; null when none (the default). It must not change with a later generation: a snapshot
	public java.util.function.LongPredicate waterTest() {
		return null;
	}

	// Smart fill (2026-10-22): the colour (0xRRGGBB) the world draws the cube at this local position with: the
	// shape's colour, or the one colourCube sets for shapes that colour cubes one by one (layers, parts)
	public int cubeRgb(int x, int y, int z) {
		int back = (4 - quarterTurns()) & 3, ux = turnX(x, z, back), uz = turnZ(x, z, back);
		final int[] rgb = {((int) (255 * shapeSet.getShapeColourR()) << 16) | ((int) (255 * shapeSet.getShapeColourG()) << 8) | (int) (255 * shapeSet.getShapeColourB())};
		colourCube(new IShapeBuffer() {
			public void setColour(int r, int g, int b, int a) {
				rgb[0] = (r << 16) | (g << 8) | b;
			}

			public void pushVertex(double px, double py, double pz) {}

			public void end() {}

			public void close() {}
		}, ux, y, uz);
		return rgb[0];
	}

	/**
	 * Build focus: the world buffer from the last generation's expected set, only the blocks in focus f (null:
	 * all), without generating anything again. Returns how many cubes it holds (the origin cube not counted,
	 * the caller adds it). Under the lock; an interrupt stops it with InterruptedException.
	 */
	private int fillWorldBuffer(IShapeBuffer buffer, FocusFilter f) throws InterruptedException {
		int count = 0;
		double s = shapeSet.getShapeCubeSize(), o = 0.5 - s / 2;
		for(int k = 0, n = expectedBlocks.size();k < n;++k) {
			long pos = expectedBlocks.get(k);
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(!isInFocusStored(f, x, y, z)) continue;
			int back = (4 - quarterTurns()) & 3;
			colourCube(buffer, turnX(x, z, back), y, turnZ(x, z, back));
			addCube(buffer, x + o, y + o, z + o, s);
			++count;
		}
		return count;
	}

	/**
	 * Build focus: the selectors changed. Only the world buffer is made again, from the expected set the last
	 * generation left (and only when the focus it shows changes); the generation, the expected set, the
	 * validation and its scan stay as they are. The new buffer reaches the world through the WorldUpdateGate,
	 * like a shape change, and the error overlay is built again for it. Render thread (the preview's buttons).
	 */
	public void setFocus(FocusFilter.Part part, int layer) {
		storeFocus(part, layer);
		BaseScreen.shouldUpdatePersistence = true;
		refreshFocus();
	}

	// The same for the selectors as they are: the threshold changed (Configuration, Focus from)
	public void refreshFocus() {
		overlayVersion = -1;
		worldGate.request();
		if(refocusFuture != null && !refocusFuture.isDone()) refocusFuture.cancel(true);
		final long ticket = ++refocusAsked;
		refocusFuture = executor.submit(() -> {
			lock.lock();
			try {
				refocus();
			}catch(InterruptedException e) {
				// a newer focus replaced this one: it runs next
			}finally {
				if(ticket == refocusAsked) refocusDone = ticket;
				lock.unlock();
			}
		});
		if(!BuildGuide.config.asyncEnabled.value) {
			try {
				refocusFuture.get();
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	// Under the lock. A generation that failed or is still due leaves nothing to refocus (the next one uses the
	// new selectors anyway); a new buffer that is interrupted is freed and the old one stays
	private void refocus() throws InterruptedException {
		if(!ready || error || buffer == null || shapeSet == null) return;
		FocusFilter focus = focusFor(nBlocks);
		if(sameFocus(focus, bufferFocus)) return;
		IShapeBuffer next = BuildGuide.shapeHandler.newBuffer();
		int count;
		try {
			setShapeColour(next);
			count = fillWorldBuffer(next, focus);
			next.setColour((int) (255 * shapeSet.getOriginColourR()), (int) (255 * shapeSet.getOriginColourG()), (int) (255 * shapeSet.getOriginColourB()), (int) (255 * shapeSet.getOriginColourA()));
			addOriginCube(next);
		}catch(InterruptedException e) {
			next.releaseVertexData();
			throw e;
		}
		// The buffer it replaces: freed now unless the world shows it (then when the new one is sent)
		if(buffer != shownBuffer) buffer.releaseVertexData();
		buffer = next;
		vertexBufferUnpacked = false;
		worldBlocks = count;
		bufferFocus = focus;
	}

	// A focus change whose world buffer is not made yet: the world keeps what it shows until it is
	public boolean isRefocusing() {
		return refocusDone != refocusAsked;
	}

	private static boolean sameFocus(FocusFilter a, FocusFilter b) {
		return a == null ? b == null : b != null && a.part == b.part && a.layer == b.layer;
	}

	// The focus the latest world buffer was made with (null: every block)
	public FocusFilter getWorldFocus() {
		return bufferFocus;
	}

	// Cubes in the latest world buffer (the origin cube not counted): getNumberOfBlocks() without a focus
	public int getWorldBlocks() {
		return ready ? worldBlocks : 0;
	}

	// For what the world draws from the expected set (the error overlay, the Area 3 target): whether a local
	// position is in the latest buffer's focus; null when every position is. Under the lock
	public java.util.function.LongPredicate worldFocusTest() {
		final FocusFilter f = bufferFocus;
		if(f == null) return null;
		return pos -> isInFocusStored(f, LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos));
	}
	
	// Emit only if this position has not been emitted in this generation; returns whether it was new
	protected boolean addShapeCubeIfNew(IShapeBuffer buffer, int x, int y, int z) throws InterruptedException {
		int turns = quarterTurns();
		if(expectedBlocks.contains(LocalPos.pack(turnX(x, z, turns), y, turnZ(x, z, turns)))) return false;
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
	
	// Whether a property is kept out of the panel (LayerControls: the Island's hidden Layers box hides its rows)
	boolean isHiddenFromGui(Property<?> p) {
		return hiddenProperties.contains(p);
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
			p.setX(ShapeScreen.propertiesX);
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

	// Layers by depth (palette stage 4C, visual only): whether this shape's layers are switched on now (the
	// panel's setting), and the layers its last generation coloured the world with (null: none). Shapes
	// without layers keep both defaults
	public boolean hasLayers() {
		return false;
	}

	public ShapeLayers getLayers() {
		return null;
	}
	
	// Palette per layer (4D): the block ids its last generation was made with, one per layer ("" = any
	// solid block), or null without layers or palette. Shapes without layers keep the default
	public String[] getPalette() {
		return null;
	}
	
	// Build focus (FocusFilter): the two selectors. Per instance and transient here (other shapes: All or
	// Hidden until a restart); the Island keeps them in its properties, so they are saved
	private transient FocusFilter.Part focusPart = FocusFilter.Part.ALL;
	private transient int focusLayer = FocusFilter.LAYER_ALL;

	public FocusFilter.Part getFocusPart() {
		return focusPart;
	}

	public int getFocusLayer() {
		return focusLayer;
	}

	protected void storeFocus(FocusFilter.Part part, int layer) {
		focusPart = part;
		focusLayer = layer;
	}

	// Configuration "Focus from" (the default without a config, as in the harness)
	public static int focusThreshold() {
		return BuildGuide.config == null ? FocusFilter.defaultThreshold : BuildGuide.config.focusFrom.value;
	}

	// What the selectors ask for, without the threshold: null when they select everything. Without parts only
	// Hidden counts (any other part is All). The layer selector only matters with layers (isInFocus)
	FocusFilter selectedFocus() {
		FocusFilter.Part part = getFocusPart();
		if(part == null || (!hasParts() && part != FocusFilter.Part.HIDDEN)) part = FocusFilter.Part.ALL;
		int layer = getFocusLayer();
		return part == FocusFilter.Part.ALL && layer == FocusFilter.LAYER_ALL ? null : new FocusFilter(part, layer);
	}

	// The focus the world uses for the last generation, of `blocks` blocks: null below the threshold or when the
	// selection is everything (a layer alone without the generation's layers)
	FocusFilter focusFor(int blocks) {
		FocusFilter f = selectedFocus();
		if(f != null && f.part == FocusFilter.Part.ALL && getLayers() == null) return null;
		return f != null && FocusFilter.isActive(f.part, f.layer, focusThreshold(), blocks) ? f : null;
	}

	// Whether the selectors select less than everything for the last generation (the threshold aside): the
	// preview says "Focus off" when they do but the shape is below the threshold
	public boolean isFocusSelected() {
		FocusFilter f = selectedFocus();
		return f != null && !(f.part == FocusFilter.Part.ALL && getLayers() == null);
	}

	// Whether the last generation's block at this local position is drawn with focus f (null: every block).
	// Without the generation's layers the layer selector selects every layer
	public boolean isInFocus(FocusFilter f, int x, int y, int z) {
		if(f == null) return true;
		ShapeLayers layers = getLayers();
		return f.accepts(partOf(x, y, z), layers == null ? f.layer : layers.layerAt(x, y, z));
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
