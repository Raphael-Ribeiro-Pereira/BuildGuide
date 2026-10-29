package brentmaas.buildguide.common.screen;

import java.util.ArrayList;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.screen.widget.IShapeList;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.screen.widget.IWidget;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ValidationState;

public abstract class BaseScreen {
	public static boolean shouldUpdatePersistence = false;
	// Layout bands (GUI redesign E3, design size 480 x 270): header y 0..20, tabs 20..40,
	// content 40..250, bottom bar 250..270 on the tabs that have one
	public static final int headerTextY = 6, headerGap = 10;
	// Bottom bar: validation progress at x 251..400 on the tabs that keep it. Its rows follow the bottom
	// edge (E7): bar h - 18..h - 12, text at h - 10 (252, 258, 260 at the minimum height 270)
	public static final int barX1 = 251, barX2 = 400;
	
	protected Translatable title = new Translatable("screen.buildguide.title");
	protected Translatable titleNumberOfBlocksShape = new Translatable("screen.buildguide.numberofblocksshape");
	protected Translatable titleNumberOfBlocksTotal = new Translatable("screen.buildguide.numberofblockstotal");
	protected Translatable textEnabled = new Translatable("screen.buildguide.enable");
	protected IScreenWrapper wrapper;
	protected ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
	
	private IButton buttonClose;
	private ICheckboxRunnableButton buttonEnabled;
	// The page bar (the Oficina, 2026-10-28) replaces the old five tabs on every screen but the Oficina itself:
	// "< Workshop" back to it at x 0..72, then the pages of the screen's section as tabs, the section's name on
	// the right. Sections are the Oficina's rail: Projects, Check and Config have pages; overlays have none
	public static final int pageBarTop = 20, pageBarHeight = 20, backWidth = 72, pageTabWidth = 96;
	public static final int SECTION_CONFIG = 4;
	private static final String[] sectionKeys = {"screen.buildguide.panel.title.shapes", "screen.buildguide.panel.title.placement", "screen.buildguide.panel.title.scan", "screen.buildguide.panel.title.check", "screen.buildguide.configuration"};
	private int currentPageTab = -1;
	
	public void init() {
		// A screen object can be shown again (back from a dropdown or the preview): its properties are
		// re-added below, so drop the ones of the previous init instead of rendering them twice
		properties.clear();
		// One-line header (GUI redesign E3): checkbox and close button in the corners of y 0..20
		buttonClose =BuildGuide.widgetHandler.createButton(wrapper.getWidth() - AbstractWidgetHandler.defaultSize, 0, new Translatable("X"), () -> BuildGuide.screenHandler.showScreen(null));
		// Shape header (E6): the vanilla checkbox is 17 px, at x 2..19, y 1..18
		buttonEnabled = BuildGuide.widgetHandler.createCheckbox(hasShapeHeader() ? 2 : 0, hasShapeHeader() ? 1 : 0, new Translatable(""), BuildGuide.stateManager.getState().isEnabled(), false, () -> {
			BuildGuide.stateManager.getState().setEnabled(buttonEnabled.isCheckboxSelected());
			BaseScreen.shouldUpdatePersistence = true;
		});
		
		if(hasEnabledCheckbox()) addWidget(buttonEnabled);
		addWidget(buttonClose);
		if(hasTabs()) addPageBar();
		
		BuildGuide.stateManager.getState().initCheck();
	}
	
	public void render() {
		if(!hasShapeHeader()) renderHeader();
		for(Property<?> p: properties) {
			p.render(this);
		}
		if(hasBottomBar()) renderBottomBar();
	}

	// Below the design minimum (GUI redesign E7, D1 option a): no cut layout, only a message and the
	// close button. The loader calls initChecked / renderBackgroundChecked / renderChecked and skips the
	// input hooks while isTooSmall(), so Escape and X always close. Re-checked on every init, which
	// also runs on a resize or a GUI scale change
	private boolean tooSmall = false;
	private Translatable textTooSmallHint = new Translatable("screen.buildguide.toosmall.hint");

	public static boolean fitsMinimum(int width, int height) {
		return width >= ShapeLayout.minWidth && height >= ShapeLayout.minHeight;
	}

	public boolean isTooSmall() {
		return tooSmall;
	}

	public void initChecked() {
		tooSmall = !fitsMinimum(wrapper.getWidth(), wrapper.getHeight());
		if(!tooSmall) {
			init();
			return;
		}
		properties.clear();
		addWidget(BuildGuide.widgetHandler.createButton(wrapper.getWidth() - AbstractWidgetHandler.defaultSize, 0, new Translatable("X"), () -> BuildGuide.screenHandler.showScreen(null)));
	}

	public void renderBackgroundChecked() {
		if(tooSmall) return;
		if(hasTabs()) renderChrome();
		renderBackground();
	}

	public void renderChecked() {
		if(!tooSmall) {
			render();
			return;
		}
		int x = wrapper.getWidth() / 2, y = wrapper.getHeight() / 2 - 10;
		drawShadowCentred(new Translatable("screen.buildguide.toosmall", "" + ShapeLayout.minWidth, "" + ShapeLayout.minHeight, "" + wrapper.getWidth(), "" + wrapper.getHeight()).toString(), x, y, 0xFFFFFF);
		drawShadowCentred(textTooSmallHint.toString(), x, y + 12, 0xAAAAAA);
	}
	// Drawn before the widgets (render() comes after them): panel fills that widgets sit on, such as
	// the preview's black area under the filter and slice controls (E6 fix)
	public void renderBackground() {
	}

	// The Shape tab draws its own header row (GUI redesign E6: set selector, type, name, Save); the
	// other tabs keep the E3 one. Only the Enabled checkbox and the close button are shared
	protected boolean hasShapeHeader() {
		return false;
	}

	private void renderHeader() {
		// One-line header (D4): "Enabled", the title in the centre, the shape's block count ending just
		// left of the title and the total starting just right of it (the x 64 + n breakdown is gone)
		int centre = wrapper.getWidth() / 2, halfTitle = wrapper.getTextWidth(title.toString()) / 2;
		drawShadowCentred(title.toString(), centre, headerTextY, Theme.rgb(Theme.TEXT));
		drawShadowLeft(textEnabled.toString(), 25, headerTextY, Theme.rgb(Theme.TEXT_SOFT));
		int n = BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape().getNumberOfBlocks() : 0;
		drawShadowRight(titleNumberOfBlocksShape + ": " + n, centre - halfTitle - headerGap, headerTextY, Theme.rgb(Theme.TEXT_MUTED));
		drawShadowLeft(titleNumberOfBlocksTotal + ": " + BuildGuide.stateManager.getState().getNumberOfBlocks(), centre + halfTitle + headerGap, headerTextY, Theme.rgb(Theme.TEXT_MUTED));
	}
	
	// The Enabled checkbox in the header's corner; the Oficina has it as "Show in the world" in its Config panel
	protected boolean hasEnabledCheckbox() {
		return true;
	}

	// The page bar at y 20..40. The Shape tab has its own rail instead (the Oficina, 2026-10-27)
	protected boolean hasTabs() {
		return true;
	}

	// The Oficina section this screen belongs to (ShapeScreen.PLACEMENT, CHECK or SECTION_CONFIG); NONE for the
	// overlays (fill, preview, presets, dropdowns), whose back button acts as Escape
	protected int section() {
		return ShapeScreen.NONE;
	}

	private void addPageBar() {
		addWidget(BuildGuide.widgetHandler.createButton(0, pageBarTop, backWidth, pageBarHeight, new Translatable("screen.buildguide.page.back"), this::backToOficina));
		String[] keys;
		Runnable[] actions;
		switch(section()) {
		case ShapeScreen.PLACEMENT:
			keys = new String[] {"screen.buildguide.shapelist", "screen.buildguide.page.presets"};
			actions = new Runnable[] {() -> openPage(ActiveScreen.Shapelist), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this))};
			break;
		case ShapeScreen.CHECK:
			keys = new String[] {"screen.buildguide.visualisation", "screen.buildguide.exclusions"};
			actions = new Runnable[] {() -> openPage(ActiveScreen.Visualisation), () -> openPage(ActiveScreen.Exclusions)};
			break;
		case SECTION_CONFIG:
			keys = new String[] {"screen.buildguide.configuration"};
			actions = new Runnable[] {() -> openPage(ActiveScreen.Settings)};
			break;
		default:
			keys = new String[0];
			actions = new Runnable[0];
		}
		currentPageTab = -1;
		for(int i = 0;i < keys.length;++i) {
			Runnable action = actions[i];
			addWidget(BuildGuide.widgetHandler.createButton(backWidth + 4 + i * pageTabWidth, pageBarTop, pageTabWidth - 2, pageBarHeight, new Translatable(keys[i]), action::run));
			if(isPage(keys[i])) currentPageTab = i;
		}
	}

	// The tab of this screen, underlined in the page bar
	private boolean isPage(String key) {
		ActiveScreen current = BuildGuide.stateManager.getState().currentScreen;
		return key.equals("screen.buildguide.shapelist") && current == ActiveScreen.Shapelist
			|| key.equals("screen.buildguide.visualisation") && current == ActiveScreen.Visualisation
			|| key.equals("screen.buildguide.exclusions") && current == ActiveScreen.Exclusions
			|| key.equals("screen.buildguide.configuration") && current == ActiveScreen.Settings;
	}

	private void openPage(ActiveScreen page) {
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(page));
	}

	// Back to the Oficina with this section's panel open; an overlay goes back the way Escape does
	private void backToOficina() {
		int s = section();
		if(s == ShapeScreen.NONE && onEscape()) return;
		BuildGuide.stateManager.getState().sidePanel = s == ShapeScreen.PLACEMENT || s == ShapeScreen.CHECK || s == SECTION_CONFIG ? s : ShapeScreen.NONE;
		openPage(ActiveScreen.Shape);
	}

	// The chrome of the screens with the page bar (Theme): the top and page bars on the darkest ground, the
	// content on the panel colour, the current page underlined, the section's name at the right
	private void renderChrome() {
		int w = wrapper.getWidth(), h = wrapper.getHeight(), barBottom = pageBarTop + pageBarHeight;
		fillRect(0, 0, w, barBottom, Theme.BACKGROUND);
		fillRect(0, barBottom, w, h, Theme.PANEL);
		fillRect(0, barBottom - 1, w, barBottom, Theme.BORDER);
		if(currentPageTab >= 0) {
			int x = backWidth + 4 + currentPageTab * pageTabWidth;
			fillRect(x, barBottom - 2, x + pageTabWidth - 2, barBottom, Theme.ACCENT);
		}
		if(section() != ShapeScreen.NONE) drawShadowRight(new Translatable(sectionKeys[section()]).toString(), w - 6, pageBarTop + 6, Theme.rgb(Theme.TEXT_FAINT));
	}

	// Screens that use the bottom band themselves (Shape tab, dropdowns, preview) return false
	protected boolean hasBottomBar() {
		return true;
	}
	
	// Validation progress of the current shape (moved from ShapeScreen, GUI redesign E3): bar and
	// "ok / total (p%)" text. Never scanned shows "- / total" (the total is known from the expected blocks)
	private void renderBottomBar() {
		int h = wrapper.getHeight(), barY1 = h - 18, barY2 = h - 12, barTextY = h - 10;
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) {
			drawShadowLeft("-", barX1, barTextY, 0x888888);
			return;
		}
		Shape shape = BuildGuide.stateManager.getState().getCurrentShape(); // every Shape is IValidatable
		ValidationState state = shape.getValidationState();
		fillRect(barX1, barY1, barX2, barY2, Theme.CONTROL);
		if(!state.isValidated()) {
			drawShadowLeft("- / " + shape.getExpectedBlocks().size(), barX1, barTextY, 0xAAAAAA);
			return;
		}
		int ok = state.getOk(), total = state.getTotal(), errors = state.getErrorCount();
		double progress = state.getProgress();
		int fillX2 = barX1 + (int) Math.round((barX2 - barX1) * progress);
		if(fillX2 > barX1) fillRect(barX1, barY1, fillX2, barY2, ok == total ? Theme.OK : Theme.ACCENT);
		String text = ok + " / " + total + " (" + String.format(java.util.Locale.ROOT, "%.1f", 100.0 * progress) + "%)";
		// Structure errors: solid blocks deforming the shape (Etapa 2.5)
		if(errors > 0) text += "  errors " + errors;
		// Palette per layer (4D): the blocks of another layer
		if(state.getWrongLayer() > 0) text += "  wrong layer " + state.getWrongLayer();
		drawShadowLeft(text, barX1, barTextY, errors > 0 ? 0xFF8080 : 0xFFFFFF);
	}
	
	public void setWrapper(IScreenWrapper wrapper) {
		this.wrapper = wrapper;
	}
	
	public void addWidget(IWidget widget) {
		if(wrapper != null) {
			if(widget instanceof IButton) {
				wrapper.addButton((IButton) widget);
			} else if(widget instanceof ITextField) {
				wrapper.addTextField((ITextField) widget);
			} else if(widget instanceof ICheckboxRunnableButton) {
				wrapper.addCheckbox((ICheckboxRunnableButton) widget);
			} else if(widget instanceof ISlider) {
				wrapper.addSlider((ISlider) widget);
			} else if(widget instanceof IShapeList) {
				wrapper.addShapeList((IShapeList) widget);
			} else if(widget instanceof ISelectorList) {
				wrapper.addSelectorList((ISelectorList) widget);
			}
		}
	}
	
	public void drawShadowLeft(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x, y, colour);
	}
	
	public void drawShadowCentred(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x - wrapper.getTextWidth(text) / 2, y, colour);
	}
	
	public void drawShadowRight(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawShadow(text, x - wrapper.getTextWidth(text), y, colour);
	}
	
	// Cuts a string to the given pixel width, ending in ".." (moved from ShapeScreen in E8, the preset menu uses it too)
	protected String fit(String text, int width) {
		if(wrapper == null || wrapper.getTextWidth(text) <= width) return text;
		while(text.length() > 1 && wrapper.getTextWidth(text + "..") > width) text = text.substring(0, text.length() - 1);
		return text + "..";
	}
	
	public void fillRect(int x1, int y1, int x2, int y2, int colour) {
		if(wrapper != null) wrapper.fillRect(x1, y1, x2, y2, colour);
	}

	// The prototype's rounded boxes: a fill with its four corner pixels left out
	public void fillRound(int x1, int y1, int x2, int y2, int colour) {
		fillRect(x1 + 1, y1, x2 - 1, y2, colour);
		fillRect(x1, y1 + 1, x1 + 1, y2 - 1, colour);
		fillRect(x2 - 1, y1 + 1, x2, y2 - 1, colour);
	}

	// Flat text (no drop shadow); colours are ARGB
	public void drawTextLeft(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawText(text, x, y, colour);
	}

	public void drawTextRight(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawText(text, x - wrapper.getTextWidth(text), y, colour);
	}

	public void drawTextCentred(String text, int x, int y, int colour) {
		if(wrapper != null) wrapper.drawText(text, x - wrapper.getTextWidth(text) / 2, y, colour);
	}
	
	protected void addProperty(Property<?> p) {
		properties.add(p);
		p.addToScreen(this);
	}
	
	protected void addDropdownOverlayScreen(DropdownOverlayScreen dropdown) {
		addWidget(dropdown.getOpenButton());
	}
	
	public void onScreenClosed() {
		if(BuildGuide.config.persistenceEnabled.value && shouldUpdatePersistence) {
			try {
				BuildGuide.stateManager.savePersistence();
				shouldUpdatePersistence = false;
			}catch(Exception e) {
				BuildGuide.logHandler.error("Build Guide persistence failed to save: " + e.getMessage() + "\n" + e.getStackTrace());
			}
		}
	}
	
	public boolean isPauseScreen() {
		return false;
	}

	// Escape pressed: return true to handle it here (the default closes the whole GUI)
	public boolean onEscape() {
		return false;
	}

	// Mouse events that no widget took, GUI coordinates; return true if handled. Buttons use the
	// loader's numbering (GLFW: 0 left, 1 right, 2 middle)
	public static final int MOUSE_LEFT = 0, MOUSE_MIDDLE = 2;

	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		return false;
	}

	// Drag by (dx, dy) GUI pixels after a click this screen handled
	public boolean onMouseDragged(double dx, double dy) {
		return false;
	}

	// Any button released (also after widget clicks, so a drag state can always end)
	public void onMouseReleased() {
	}

	// Scroll wheel at (x, y); amount > 0 is away from the user
	public boolean onMouseScrolled(double x, double y, double amount) {
		return false;
	}
}