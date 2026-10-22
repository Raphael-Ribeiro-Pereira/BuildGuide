package brentmaas.buildguide.common.screen;

/**
 * Vertical bands of the Shape tab for a GUI height (GUI redesign E7, R13). Pure: no widgets, no
 * loader, so the harness checks it. The bottom row, the legend and the accordion's lower limit follow
 * the bottom edge. The accordion's top and its rows do not move (ShapeScreen.accordionTop,
 * basePropertiesY): it grows down from y 42, only its lower limit, i.e. its capacity, depends on the height.
 *
 * 2026-10-22: the list of positions moved to Visualisation ("Find blocks"). The preview reaches down to one
 * status line (a 2-px progress line, then the missing count) right above the bottom row, and gets all the
 * extra height of a taller window. Below the minimum height the bands stay those of the minimum.
 */
public final class ShapeLayout {
	public static final int minWidth = 480, minHeight = 270;
	private static final int statusHeight = 14, progressHeight = 2, bottomRowHeight = 20;

	public final int height;
	// Preview 40..previewBottom; the status line previewBottom..bottomRowY: progress from progressTop, text at statusTextY
	public final int previewBottom, progressTop, statusTextY;
	public final int bottomRowY, legendY, accordionBottom;

	public ShapeLayout(int height) {
		this.height = height;
		bottomRowY = height - bottomRowHeight;
		legendY = bottomRowY + 6;
		accordionBottom = bottomRowY - 2;
		previewBottom = Math.max(height, minHeight) - bottomRowHeight - statusHeight;
		progressTop = previewBottom;
		statusTextY = progressTop + progressHeight + 3;
	}

	// Height available to the accordion's headers and rows (206 at the minimum height)
	public int accordionHeight() {
		return accordionBottom - ShapeScreen.accordionTop;
	}

	public static int accordionHeight(int height) {
		return new ShapeLayout(height).accordionHeight();
	}
}
