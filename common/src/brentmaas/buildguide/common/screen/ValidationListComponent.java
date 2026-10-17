package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.LayerPalette;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Error list of a shape, read from its live ValidationState, in any rectangle of any screen (the
 * right panel of the Shape screen since GUI redesign E6, where it replaced the Validation tab).
 * One category at a time, chosen by the screen's tabs: structure errors (with block name and
 * distance), missing positions, or ignored blocks (with name), in world coordinates. Rebuilt when the state's
 * version or the shape changes, at most every rebuildIntervalMillis. Clicking a row highlights
 * that position (ValidationState.setHighlightedPos, drawn by the world overlay); clicking it
 * again or a header clears it.
 *
 * Two parts: buildEntries is pure (tested offline); the rest is the glue to an ISelectorList,
 * created through an injectable factory so tests need no loader.
 */
public class ValidationListComponent {
	public static final long rebuildIntervalMillis = 100;
	public static final int rowHeight = 12;
	// At most this many position rows per tab, the first ones by position, then one row saying how many more.
	// Like Litematica's lists, which hold what a person reads, not one row per block: a tab of 50 000 missing
	// positions cost seconds to fill (the vanilla list walks every row it holds to place the next one) and was
	// built again at most every 100 ms while the validation changed (performance step 3)
	public static final int maxRows = 1000;
	
	// Tabs of the Shape screen's list (GUI redesign E6): one category at a time, no headers
	public static enum Category {
		ERRORS("screen.buildguide.tab.errors", "screen.buildguide.tab4.errors"), MISSING("screen.buildguide.tab.missing", "screen.buildguide.tab4.missing"), IGNORED("screen.buildguide.tab.ignored", "screen.buildguide.tab4.ignored"),
		// Palette per layer (4D): a fourth tab, shown only while the shape has a palette
		WRONG_LAYER("screen.buildguide.tab.wronglayer", "screen.buildguide.tab4.wronglayer");
		
		public final String translationKey;
		// The shorter label when the four tabs share the width
		public final String shortKey;
		
		private Category(String translationKey, String shortKey) {
			this.translationKey = translationKey;
			this.shortKey = shortKey;
		}
	}

	// Same parameters as AbstractWidgetHandler.createSelectorList (note: left, right, top, bottom)
	public interface ListFactory {
		public ISelectorList create(int left, int right, int top, int bottom, int slotHeight, List<Translatable> titles, int current, ISelectorList.ISelectorListCallback callback);
	}

	// The rows of the list and, parallel to them, the local position behind each row (-1 for headers and messages)
	public static final class Entries {
		public final List<Translatable> titles;
		public final List<Long> positions;
		// ValidationState version shown; -1 without a shape
		public final long version;

		private Entries(List<Translatable> titles, List<Long> positions, long version) {
			this.titles = titles;
			this.positions = positions;
			this.version = version;
		}
	}

	private final LongSupplier clock;
	private final ListFactory factory;
	private ISelectorList list = null;
	private List<Long> rowPositions = new ArrayList<Long>();
	private Shape shape = null;
	private long shownVersion = -1;
	private long lastRebuild = 0;
	private Shape shownShape = null;
	private Category category = Category.ERRORS;
	public ValidationListComponent() {
		// The lambda defers the widget handler lookup to init(), when the loader has registered it
		this(System::currentTimeMillis, (left, right, top, bottom, slotHeight, titles, current, callback) -> BuildGuide.widgetHandler.createSelectorList(left, right, top, bottom, slotHeight, titles, current, callback));
	}

	public ValidationListComponent(LongSupplier clock, ListFactory factory) {
		this.clock = clock;
		this.factory = factory;
	}

	/**
	 * Creates the list in the rectangle (x1, y1)..(x2, y2) for this shape (null = none) with the
	 * world coordinates from the origin of its scan (P4); the caller adds getList() to its screen.
	 * The first update() rebuilds right away.
	 */
	public ISelectorList init(int x1, int y1, int x2, int y2, Shape shape) {
		this.shape = shape;
		Entries entries = buildEntries(shape, category);
		rowPositions = entries.positions;
		// The rows are current: the next update() rebuilds only once something changed
		shownVersion = entries.version;
		shownShape = shape;
		lastRebuild = clock.getAsLong();
		list = factory.create(x1, x2, y1, y2, rowHeight, entries.titles, 0, this::click);
		return list;
	}

	public ISelectorList getList() {
		return list;
	}
	
	public Category getCategory() {
		return category;
	}
	
	// The tab init() will build, without building anything now (a screen that opens again sets it before init)
	public void selectCategory(Category category) {
		this.category = category;
	}

	// Switch tab: the rows are rebuilt at once
	public void setCategory(Category category) {
		this.category = category;
		Entries entries = buildEntries(shape, category);
		rowPositions = entries.positions;
		shownVersion = entries.version;
		shownShape = shape;
		lastRebuild = clock.getAsLong();
		if(list != null) list.setEntries(entries.titles);
	}

	// Every frame: rebuild the rows when the state or the shape changed, at most every rebuildIntervalMillis
	public void update(Shape shape) {
		this.shape = shape;
		long version = shape != null ? shape.getValidationState().getVersion() : -1;
		long now = clock.getAsLong();
		if((version != shownVersion || shape != shownShape) && now - lastRebuild >= rebuildIntervalMillis) {
			Entries entries = buildEntries(shape, category);
			rowPositions = entries.positions;
			shownVersion = entries.version;
			shownShape = shape;
			lastRebuild = now;
			list.setEntries(entries.titles);
		}
	}

	// Row clicked (the list's callback): highlight its position; the highlighted row again, or a header, clears it
	public void click(int index) {
		if(index < 0 || index >= rowPositions.size() || shape == null) return;
		ValidationState state = shape.getValidationState();
		long pos = rowPositions.get(index);
		state.setHighlightedPos(pos == state.getHighlightedPos() ? -1 : pos);
	}

	/**
	 * Tab counts {errors, missing, ignored, wrong layer}, or null when there is no shape or it is not validated.
	 * Missing excludes the ignored positions, like the combined list's header. Errors are the structure errors
	 * around the shape plus the wrong blocks on it (4D)
	 */
	public static int[] counts(Shape shape) {
		if(shape == null || !shape.getValidationState().isValidated()) return null;
		ValidationState state = shape.getValidationState();
		return new int[] {state.getErrorCount(), state.getMissing() - state.getIgnored(), state.getIgnored(), state.getWrongLayer()};
	}
	
	// The tabs shown for a shape: the fourth (wrong layer) only while its state has a palette
	public static Category[] tabs(Shape shape) {
		boolean palette = shape != null && shape.getValidationState().getPalette() != null;
		return palette ? Category.values() : new Category[] {Category.ERRORS, Category.MISSING, Category.IGNORED};
	}
	
	/**
	 * The rows of one tab for a shape (null = none) at the origin of its last scan (P4): its
	 * positions in world coordinates, sorted, no header; one "None" row when empty, one message
	 * row without a shape or before validation. Pure: no widgets, no loader
	 */
	public static Entries buildEntries(Shape shape, Category category) {
		List<Translatable> titles = new ArrayList<Translatable>();
		List<Long> positions = new ArrayList<Long>();
		if(shape == null) {
			titles.add(new Translatable("screen.buildguide.novalidation"));
			positions.add(-1L);
			return new Entries(titles, positions, -1);
		}
		ValidationState state = shape.getValidationState();
		if(!state.isValidated()) {
			titles.add(new Translatable("screen.buildguide.notvalidated"));
			positions.add(-1L);
			return new Entries(titles, positions, state.getVersion());
		}
		// Rows stay where the scan saw them in the world, even after the origin moved (P4)
		int ox = state.getScanOriginX(), oy = state.getScanOriginY(), oz = state.getScanOriginZ();
		// Rows past maxRows are only counted
		int hidden = 0;
		if(category == Category.ERRORS) {
			List<NearBlock> errors = state.getNearBlocks();
			errors.sort(Comparator.comparingLong((NearBlock nb) -> sortKey(nb.localPos)));
			for(NearBlock nb: errors) {
				if(titles.size() >= maxRows) ++hidden;
				else row(titles, positions, nb.localPos, ox, oy, oz, nb.blockName, String.format(Locale.ROOT, "%.1f", nb.distance));
			}
			// Wrong blocks on the shape (4D): after the structure errors, with their layer
			for(long pos: sortedByPosition(state.getPositions(ValidationState.WRONG_BLOCK))) {
				if(titles.size() >= maxRows) ++hidden;
				else layerRow(titles, positions, state, pos, ox, oy, oz);
			}
		}else if(category == Category.WRONG_LAYER) {
			long[] sorted = sortedByPosition(state.getPositions(ValidationState.WRONG_LAYER));
			for(int i = 0;i < Math.min(sorted.length, maxRows);++i) layerRow(titles, positions, state, sorted[i], ox, oy, oz);
			hidden = Math.max(0, sorted.length - maxRows);
		}else if(category == Category.IGNORED) {
			long[] sorted = sortedByPosition(state.getPositions(ValidationState.IGNORED));
			for(int i = 0;i < Math.min(sorted.length, maxRows);++i) row(titles, positions, sorted[i], ox, oy, oz, state.getIgnoredBlockName(sorted[i]), null);
			hidden = Math.max(0, sorted.length - maxRows);
		}else {
			// Missing: where a block still has to go; no block name (air or a non-solid block)
			long[] sorted = sortedByPosition(state.getPositions(ValidationState.MISSING));
			for(int i = 0;i < Math.min(sorted.length, maxRows);++i) {
				long pos = sorted[i];
				titles.add(new Translatable("[" + (ox + LocalPos.unpackX(pos)) + ", " + (oy + LocalPos.unpackY(pos)) + ", " + (oz + LocalPos.unpackZ(pos)) + "]"));
				positions.add(pos);
			}
			hidden = Math.max(0, sorted.length - maxRows);
		}
		if(hidden > 0) {
			titles.add(new Translatable("screen.buildguide.errors.more", "" + hidden));
			positions.add(-1L);
		}
		if(titles.isEmpty()) {
			titles.add(new Translatable("screen.buildguide.errors.none"));
			positions.add(-1L);
		}
		return new Entries(titles, positions, state.getVersion());
	}
	
	private static void row(List<Translatable> titles, List<Long> positions, long pos, int ox, int oy, int oz, String blockName, String distance) {
		String text = "[" + (ox + LocalPos.unpackX(pos)) + ", " + (oy + LocalPos.unpackY(pos)) + ", " + (oz + LocalPos.unpackZ(pos)) + "] " + (blockName != null ? blockName : "?");
		if(distance != null) text += " (d=" + distance + ")";
		titles.add(new Translatable(text));
		positions.add(pos);
	}

	// "[x, y, z] Dirt (layer 2)": a position on the shape whose block is not its layer's
	private static void layerRow(List<Translatable> titles, List<Long> positions, ValidationState state, long pos, int ox, int oy, int oz) {
		LayerPalette palette = state.getPalette();
		String layer = palette == null ? "" : " (layer " + palette.layers.layerAt(LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos)) + ")";
		String name = state.getBlockName(pos);
		titles.add(new Translatable("[" + (ox + LocalPos.unpackX(pos)) + ", " + (oy + LocalPos.unpackY(pos)) + ", " + (oz + LocalPos.unpackZ(pos)) + "] " + (name != null ? name : "?") + layer));
		positions.add(pos);
	}

	// Sorted by x, then y, then z: a primitive sort of keys that order that way
	private static long[] sortedByPosition(List<Long> positions) {
		long[] keys = new long[positions.size()];
		for(int i = 0;i < keys.length;++i) keys[i] = sortKey(positions.get(i));
		java.util.Arrays.sort(keys);
		for(int i = 0;i < keys.length;++i) keys[i] = LocalPos.pack((int) ((keys[i] >>> 42) & 0x1FFFFF) - 0x100000, (int) ((keys[i] >>> 21) & 0x1FFFFF) - 0x100000, (int) (keys[i] & 0x1FFFFF) - 0x100000);
		return keys;
	}

	// Each coordinate shifted to be non-negative, x highest: ascending keys are ascending x, then y, then z
	private static long sortKey(long pos) {
		return (((long) LocalPos.unpackX(pos) + 0x100000L) << 42) | (((long) LocalPos.unpackY(pos) + 0x100000L) << 21) | ((long) LocalPos.unpackZ(pos) + 0x100000L);
	}
}
