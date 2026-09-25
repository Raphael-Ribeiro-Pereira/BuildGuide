package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Error list of a shape, read from its live ValidationState, in any rectangle of any screen:
 * the Validation tab today, the right panel of the redesigned Shape screen later (GUI redesign
 * E6). Structure errors and Ignored list world coordinates and block names; Missing
 * (non-solid or empty guideline positions) shows only its count, last. Rebuilt when the state's
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
	 * shape set's origin (ox, oy, oz); the caller adds getList() to its screen. The first update()
	 * rebuilds right away.
	 */
	public ISelectorList init(int x1, int y1, int x2, int y2, Shape shape, int ox, int oy, int oz) {
		this.shape = shape;
		Entries entries = buildEntries(shape, ox, oy, oz);
		rowPositions = entries.positions;
		list = factory.create(x1, x2, y1, y2, rowHeight, entries.titles, 0, this::click);
		return list;
	}

	public ISelectorList getList() {
		return list;
	}

	// Every frame: rebuild the rows when the state or the shape changed, at most every rebuildIntervalMillis
	public void update(Shape shape, int ox, int oy, int oz) {
		this.shape = shape;
		long version = shape != null ? shape.getValidationState().getVersion() : -1;
		long now = clock.getAsLong();
		if((version != shownVersion || shape != shownShape) && now - lastRebuild >= rebuildIntervalMillis) {
			Entries entries = buildEntries(shape, ox, oy, oz);
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

	// The rows for a shape (null = none) whose set has its origin at (ox, oy, oz). Pure: no widgets, no loader
	public static Entries buildEntries(Shape shape, int ox, int oy, int oz) {
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

		// Most actionable first: structure errors, then ignored blocks, then the missing count
		List<NearBlock> errors = state.getNearBlocks();
		errors.sort(Comparator.comparingInt((NearBlock nb) -> LocalPos.unpackX(nb.localPos)).thenComparingInt(nb -> LocalPos.unpackY(nb.localPos)).thenComparingInt(nb -> LocalPos.unpackZ(nb.localPos)));
		header(titles, positions, "screen.buildguide.errors.structure", errors.size());
		for(NearBlock nb: errors) row(titles, positions, nb.localPos, ox, oy, oz, nb.blockName, String.format(Locale.ROOT, "%.1f", nb.distance));

		List<Long> ignored = sortedByPosition(state.getPositions(ValidationState.IGNORED));
		header(titles, positions, "screen.buildguide.errors.ignored", ignored.size());
		for(long pos: ignored) row(titles, positions, pos, ox, oy, oz, state.getIgnoredBlockName(pos), null);

		header(titles, positions, "screen.buildguide.errors.missing", state.getMissing() - state.getIgnored());
		return new Entries(titles, positions, state.getVersion());
	}

	private static void header(List<Translatable> titles, List<Long> positions, String key, int count) {
		titles.add(new Translatable(key, "" + count));
		positions.add(-1L);
	}

	private static void row(List<Translatable> titles, List<Long> positions, long pos, int ox, int oy, int oz, String blockName, String distance) {
		String text = "  [" + (ox + LocalPos.unpackX(pos)) + ", " + (oy + LocalPos.unpackY(pos)) + ", " + (oz + LocalPos.unpackZ(pos)) + "] " + (blockName != null ? blockName : "?");
		if(distance != null) text += " (d=" + distance + ")";
		titles.add(new Translatable(text));
		positions.add(pos);
	}

	private static List<Long> sortedByPosition(List<Long> positions) {
		positions.sort(Comparator.comparingInt((Long p) -> LocalPos.unpackX(p)).thenComparingInt(p -> LocalPos.unpackY(p)).thenComparingInt(p -> LocalPos.unpackZ(p)));
		return positions;
	}
}
