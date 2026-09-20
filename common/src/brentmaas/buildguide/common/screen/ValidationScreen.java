package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Error list of the current shape (Etapa 2.4), read from the live ValidationState: Missing
 * shows only its count; Wrong, Ignored and Near list world coordinates and block names.
 * Rebuilt when the state's version changes, at most every 100 ms. Clicking a row highlights
 * that position in the world overlay.
 */
public class ValidationScreen extends BaseScreen {
	private static final long rebuildIntervalMillis = 100;
	private static final int listTop = 50, listBottom = 265, listLeft = 5, listRight = 475;

	private Translatable titleValidation = new Translatable("screen.buildguide.validation");

	private ISelectorList list;
	// Local position behind each row, or -1 for headers; parallel to the entries
	private List<Long> rowPositions = new ArrayList<Long>();
	private long shownVersion = -1;
	private long lastRebuild = 0;
	private Shape shownShape = null;

	public void init() {
		super.init();
		List<Translatable> entries = new ArrayList<Translatable>();
		rowPositions.clear();
		buildEntries(entries);
		list = BuildGuide.widgetHandler.createSelectorList(listLeft, listRight, listTop, listBottom, 12, entries, 0, index -> {
			if(index < 0 || index >= rowPositions.size()) return;
			Shape shape = currentShape();
			if(shape == null) return;
			long pos = rowPositions.get(index);
			// Clicking a header or the already highlighted row clears the highlight
			shape.getValidationState().setHighlightedPos(pos == shape.getValidationState().getHighlightedPos() ? -1 : pos);
		});
		addWidget(list);
	}

	private Shape currentShape() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	// Fills `entries` (and rowPositions) from the current shape's state; returns the version shown
	private long buildEntries(List<Translatable> entries) {
		rowPositions.clear();
		Shape shape = currentShape();
		if(shape == null) {
			entries.add(new Translatable("screen.buildguide.novalidation"));
			rowPositions.add(-1L);
			return -1;
		}
		ValidationState state = shape.getValidationState();
		ShapeSet set = BuildGuide.stateManager.getState().getCurrentShapeSet();
		int ox = set.getOriginX(), oy = set.getOriginY(), oz = set.getOriginZ();
		if(!state.isValidated()) {
			entries.add(new Translatable("screen.buildguide.notvalidated"));
			rowPositions.add(-1L);
			return state.getVersion();
		}

		header(entries, "screen.buildguide.errors.missing", state.getMissing() - state.getIgnored());

		List<Long> wrong = sortedByPosition(state.getPositions(ValidationState.WRONG));
		header(entries, "screen.buildguide.errors.wrong", wrong.size());
		for(long pos: wrong) row(entries, pos, ox, oy, oz, state.getWrongBlockName(pos), null);

		List<Long> ignored = sortedByPosition(state.getPositions(ValidationState.IGNORED));
		header(entries, "screen.buildguide.errors.ignored", ignored.size());
		for(long pos: ignored) row(entries, pos, ox, oy, oz, state.getWrongBlockName(pos), null);

		List<NearBlock> near = state.getNearBlocks();
		near.sort(Comparator.comparingInt((NearBlock nb) -> LocalPos.unpackX(nb.localPos)).thenComparingInt(nb -> LocalPos.unpackY(nb.localPos)).thenComparingInt(nb -> LocalPos.unpackZ(nb.localPos)));
		header(entries, "screen.buildguide.errors.near", near.size());
		for(NearBlock nb: near) row(entries, nb.localPos, ox, oy, oz, nb.blockName, String.format(Locale.ROOT, "%.1f", nb.distance));
		return state.getVersion();
	}

	private void header(List<Translatable> entries, String key, int count) {
		entries.add(new Translatable(key, "" + count));
		rowPositions.add(-1L);
	}

	private void row(List<Translatable> entries, long pos, int ox, int oy, int oz, String blockName, String distance) {
		String text = "  [" + (ox + LocalPos.unpackX(pos)) + ", " + (oy + LocalPos.unpackY(pos)) + ", " + (oz + LocalPos.unpackZ(pos)) + "] " + (blockName != null ? blockName : "?");
		if(distance != null) text += " (d=" + distance + ")";
		entries.add(new Translatable(text));
		rowPositions.add(pos);
	}

	private static List<Long> sortedByPosition(List<Long> positions) {
		positions.sort(Comparator.comparingInt((Long p) -> LocalPos.unpackX(p)).thenComparingInt(p -> LocalPos.unpackY(p)).thenComparingInt(p -> LocalPos.unpackZ(p)));
		return positions;
	}

	public void render() {
		super.render();
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleValidation, 240, 40, 0xFFFFFF);

		// Live: rebuild the rows when the state changed (or the shape switched), at most every 100 ms
		Shape shape = currentShape();
		long version = shape != null ? shape.getValidationState().getVersion() : -1;
		long now = System.currentTimeMillis();
		if((version != shownVersion || shape != shownShape) && now - lastRebuild >= rebuildIntervalMillis) {
			List<Translatable> entries = new ArrayList<Translatable>();
			shownVersion = buildEntries(entries);
			shownShape = shape;
			lastRebuild = now;
			list.setEntries(entries);
		}
	}
}
