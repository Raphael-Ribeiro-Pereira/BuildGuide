package brentmaas.buildguide.common.screen;

/**
 * Vertical bands of the Shape tab for a GUI height (GUI redesign E7, R13). Pure: no widgets, no
 * loader, so the harness checks it. At the minimum height (270) every band is exactly the E6 one;
 * above it the bottom row, the legend and the accordion's lower limit follow the bottom edge, the
 * preview gets 60% of the extra height and the error list the other 40%.
 * The accordion's top and its rows do not move (ShapeScreen.accordionTop, basePropertiesY): it
 * grows down from y 42, only its lower limit, i.e. its capacity, depends on the height.
 */
public final class ShapeLayout {
	public static final int minWidth = 480, minHeight = 270;
	// E6 values at the minimum height
	private static final int previewBottomMin = 175, progressHeight = 2, tabsHeight = 16, bottomRowHeight = 20;

	public final int height;
	public final int previewBottom, progressTop, tabsTop, tabsBottom, listBottom;
	public final int bottomRowY, legendY, accordionBottom;

	public ShapeLayout(int height) {
		this.height = height;
		int extra = Math.max(0, height - minHeight);
		bottomRowY = height - bottomRowHeight;
		legendY = bottomRowY + 6;
		accordionBottom = bottomRowY - 2;
		previewBottom = previewBottomMin + (6 * extra + 5) / 10;
		progressTop = previewBottom;
		tabsTop = progressTop + progressHeight;
		tabsBottom = tabsTop + tabsHeight;
		listBottom = bottomRowY;
	}

	// Height available to the accordion's headers and rows (206 at the minimum height)
	public int accordionHeight() {
		return accordionBottom - ShapeScreen.accordionTop;
	}

	public static int accordionHeight(int height) {
		return new ShapeLayout(height).accordionHeight();
	}
}
