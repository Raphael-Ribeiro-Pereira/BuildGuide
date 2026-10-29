package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.FocusFilter;
import brentmaas.buildguide.common.shape.PreviewColours;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeLayers;
import brentmaas.buildguide.common.shape.ShapeRegistry;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ValidationState;

/**
 * The Shape tab as the "Oficina" (2026-10-27, Raphael's choice among five prototypes): a rail of sections on the
 * left, a side panel that opens beside it (Shapes, Projects, Scan, Check), the 3D preview always on screen in the
 * middle, the shape's properties as an accordion at the right edge, and a status bar under the preview with Fill.
 *
 * The shape list closes by itself once a shape is picked, so the preview gets the room back. Origin, the
 * instances and the presets are one panel, Placement. Config opens the configuration screen. The other screens
 * are pages of the Oficina (BaseScreen's page bar). Since 2026-10-28 the left column follows the prototype: a
 * rail without boxes, one scrolled column of shapes in groups with a search field, the Placement cards, the Scan
 * modes and the Validation summary.
 */
public class ShapeScreen extends BaseScreen{
	// Columns: rail 0..44, side panel 44..194 while one is open, preview, inspector (the accordion) on the right
	public static final int railWidth = 44, sideWidth = 150, panelWidth = 188, headerHeight = 11;
	// Everything under the 20-px header; the accordion from y 22 to the inspector's bottom row
	public static final int top = 20, accordionTop = top + 2;
	public static final int basePropertiesX = 2;
	public static final int basePropertiesY = accordionTop + headerHeight;
	// The inspector's x for this screen width: the properties are laid out there (Shape.placeRow)
	public static int propertiesX = basePropertiesX;
	// Side panels; NONE = closed
	public static final int NONE = -1, SHAPES = 0, PLACEMENT = 1, SCAN = 2, CHECK = 3;
	private static final String[] railKeys = {"screen.buildguide.rail.shapes", "screen.buildguide.rail.placement", "screen.buildguide.rail.scan", "screen.buildguide.rail.check", "screen.buildguide.rail.config"};
	public static final String[] panelTitleKeys = {"screen.buildguide.panel.title.shapes", "screen.buildguide.panel.title.placement", "screen.buildguide.panel.title.scan", "screen.buildguide.panel.title.check"};
	private static final int railButtonHeight = 24, railGap = 4;
	// Header: shape button (opens the shape list), then the instance selector < N/Total >
	private static final int shapeButtonX = 22, shapeButtonWidth = 110, setPrevX = shapeButtonX + shapeButtonWidth + 4, arrowWidth = 14, setTextWidth = 30;
	// Preview controls (two 16-px rows at the top of the preview) only when the preview is at least this wide
	public static final int controlsMinWidth = 150, controlsHeight = 16, sliceWidth = 50, colourWidth = 80, partWidth = 80, layerWidth = 64, fillWidth = 72, expandSize = 12;
	public static final int previewTop = top, previewControlsHeight = 20, focusRowHeight = 18, legendHeight = 12;
	public static final int focusRowY = previewTop + previewControlsHeight;
	// The preview's warning above this many blocks
	public static final int largeShapeBlocks = 200000;

	private final PreviewController preview = BuildGuide.stateManager.getState().preview;
	private IButton buttonShape, buttonSetPrevious, buttonSetNext;
	private IButton buttonValidate, buttonReset, buttonPreviewExpand, buttonSliceAxis, buttonColour, buttonFocusPart, buttonFocusLayer, buttonFill;
	private ISlider sliderSlice = null;
	private ITextField textFieldX, textFieldY, textFieldZ, textFieldName;
	private boolean layerControls = false;
	private String fillTitle = null;
	private PreviewModel shownModel = null;
	private ShapeLayout bands = new ShapeLayout(ShapeLayout.minHeight);
	private static final String[] axisNames = {"X", "Y", "Z"};

	private Translatable textPreviewHint = new Translatable("screen.buildguide.previewhintinline");
	private Translatable textGenerating = new Translatable("screen.buildguide.previewgenerating");
	private Translatable textEmpty = new Translatable("screen.buildguide.previewempty");
	private Translatable textNoMatch = new Translatable("screen.buildguide.previewnomatch");
	private Translatable textWorldPending = new Translatable("screen.buildguide.worldupdatepending");

	// Preview x range for a screen width, with the side panel open or not
	public static int previewX1(boolean sideOpen) {
		return railWidth + (sideOpen ? sideWidth : 0) + 2;
	}

	public static int previewX2(int width) {
		return width - panelWidth - 2;
	}

	public static int railButtonY(int i) {
		return top + 6 + i * (railButtonHeight + railGap);
	}

	private static int sidePanel() {
		return BuildGuide.stateManager.getState().sidePanel;
	}

	private int px1() {
		return previewX1(sidePanel() != NONE);
	}

	private int px2() {
		return previewX2(wrapper.getWidth());
	}

	private int inspectorX() {
		return wrapper.getWidth() - panelWidth;
	}

	private boolean compact() {
		return px2() - px1() < controlsMinWidth;
	}

	@Override
	protected boolean hasTabs() {
		return false;
	}

	@Override
	protected boolean hasShapeHeader() {
		return true;
	}

	@Override
	protected boolean hasBottomBar() {
		return false;
	}

	public void init() {
		super.init();
		bands = new ShapeLayout(wrapper.getHeight());
		propertiesX = inspectorX() + basePropertiesX;
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();

		// Rail: the side panels (a click opens one, a second click closes it), then Config (its own screen)
		for(int i = 0;i < railKeys.length;++i) {
			final int panel = i;
			addWidget(BuildGuide.widgetHandler.createButton(2, railButtonY(i), railWidth - 4, railButtonHeight, new Translatable(railKeys[i]), () -> openPanel(panel)).setStyle(i == sidePanel() ? IButton.STYLE_RAIL_SELECTED : IButton.STYLE_RAIL));
		}

		// Header
		buttonShape = BuildGuide.widgetHandler.createButton(shapeButtonX, 0, shapeButtonWidth, AbstractWidgetHandler.defaultSize, new Translatable(fit(shapeName() + " v", shapeButtonWidth - 8)), () -> openPanel(sidePanel() == SHAPES ? NONE : SHAPES));
		buttonSetPrevious = BuildGuide.widgetHandler.createButton(setPrevX, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable("<"), () -> switchShapeSet(-1));
		buttonSetNext = BuildGuide.widgetHandler.createButton(setPrevX + arrowWidth + setTextWidth, 0, arrowWidth, AbstractWidgetHandler.defaultSize, new Translatable(">"), () -> switchShapeSet(1));
		boolean severalSets = BuildGuide.stateManager.getState().shapeSets.size() > 1;
		buttonSetPrevious.setActive(severalSets);
		buttonSetNext.setActive(severalSets);
		addWidget(buttonShape);
		addWidget(buttonSetPrevious);
		addWidget(buttonSetNext);

		initSidePanel(available);

		// Inspector's bottom row: Validate and Reset (the defaults of the open section)
		int ix = inspectorX();
		buttonValidate = BuildGuide.widgetHandler.createButton(ix + 2, bands.bottomRowY, 90, AbstractWidgetHandler.defaultSize, new Translatable("property.buildguide.validate"), () -> {
			if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
		});
		buttonReset = BuildGuide.widgetHandler.createButton(ix + 96, bands.bottomRowY, 90, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.reset"), () -> {
			if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().resetShownToDefaults();
		});
		buttonValidate.setActive(available);
		buttonReset.setActive(available);
		addWidget(buttonValidate);
		addWidget(buttonReset);

		initPreviewControls(available);
		preview.attach();

		if(available) {
			for(Shape shape: BuildGuide.stateManager.getState().getCurrentShapeSet().shapes) {
				if(shape != null) {
					shape.onDeselectedInGUI();
					for(Property<?> p: shape.getGuiProperties()) addProperty(p);
				}
			}
		}
		layout();
	}

	// ---- Side panels (the prototype's left column, 2026-10-28) ----

	private static final int sideRowHeight = 18, rowHeight = 14, groupHeight = 12, sidePad = 6;
	// The shape list: the prototype's groups in one column, scrolled with the wheel. Terrain and Farming land are
	// left out: they work on the real ground, so they belong to the Scan, whose panel offers them for now
	private static final String[][] shapeGroups = {
		{"screen.buildguide.shapes.group.solids", "shape.buildguide.cuboid", "shape.buildguide.sphere", "shape.buildguide.ellipsoid", "shape.buildguide.cone", "shape.buildguide.polygonalpyramid", "shape.buildguide.torus", "shape.buildguide.paraboloid"},
		{"screen.buildguide.shapes.group.flat", "shape.buildguide.circle", "shape.buildguide.ellipse", "shape.buildguide.polygon", "shape.buildguide.grid"},
		{"screen.buildguide.shapes.group.curves", "shape.buildguide.line", "shape.buildguide.catenary", "shape.buildguide.parabola", "shape.buildguide.spline"},
		{"screen.buildguide.shapes.group.nature", "shape.buildguide.island"},
		{"screen.buildguide.shapes.group.builds", "shape.buildguide.bridge", "shape.buildguide.polygonbuilding"}};
	public static final String[] scanShapes = {"shape.buildguide.terrain", "shape.buildguide.farmingland"};
	private static final String[] scanModeKeys = {"island", "under", "above", "region", "hole"};
	// Kept between inits (a pick re-inits the screen): the list's scroll and the search text
	private static int shapeScroll = 0;
	private static String shapeFilter = "";
	// The shape list in order: a group header (button null) or a shape row; its y after layoutShapeList (-1 hidden)
	private final List<String> listKeys = new ArrayList<String>();
	private final List<IButton> listButtons = new ArrayList<IButton>();
	private int[] listY = new int[0];
	private int listTotalHeight = 0;
	private ITextField textFieldSearch;
	private IButton buttonValidateNow;
	// Placement: the cards' row, the world list's header and the saved projects' header
	private static final int cardsY = top + 16, cardHeight = 28, placeHereY = top + 50, nameLabelY = top + 71, nameY = top + 81, worldY = top + 101, instanceRows = 4;
	private int savedY = 0;
	// Scan: the five mode cards, then the shapes that use the ground
	private static final int scanCardHeight = 23, scanCardsY = top + 16;

	private int sideX() {
		return railWidth + sidePad;
	}

	private int sideW() {
		return sideWidth - 2 * sidePad;
	}

	private int shapeListTop() {
		return top + 34;
	}

	private int shapeListBottom() {
		return wrapper.getHeight() - 4;
	}

	private static int registryIndex(String key) {
		for(int i = 0;i < ShapeRegistry.getNumberOfShapes();++i) {
			if(key.equals(ShapeRegistry.getTranslationKey(i))) return i;
		}
		return -1;
	}

	private void initSidePanel(boolean available) {
		int x = sideX(), w = sideW();
		buttonValidateNow = null;
		listKeys.clear();
		listButtons.clear();
		int current = BuildGuide.stateManager.getState().getCurrentShapeIndex();
		switch(sidePanel()) {
		case SHAPES: {
			// Search (applied after a pause in typing, without rebuilding the screen), then the groups
			textFieldSearch = BuildGuide.widgetHandler.createTextField(x, top + 15, w, 14, shapeFilter);
			textFieldSearch.setHint(new Translatable("screen.buildguide.shapes.search").toString());
			textFieldSearch.setOnEnter(() -> {
				shapeFilter = textFieldSearch.getTextValue().trim();
				shapeScroll = 0;
				layoutShapeList();
			});
			addWidget(textFieldSearch);
			List<String> listed = new ArrayList<String>();
			for(String[] group: shapeGroups) {
				listKeys.add(group[0]);
				listButtons.add(null);
				for(int i = 1;i < group.length;++i) {
					addShapeRow(group[i], x, w, current, available);
					listed.add(group[i]);
				}
			}
			// Shapes registered after these groups were written go under Other
			boolean other = false;
			for(int i = 0;i < ShapeRegistry.getNumberOfShapes();++i) {
				String key = ShapeRegistry.getTranslationKey(i);
				if(listed.contains(key) || Arrays.asList(scanShapes).contains(key)) continue;
				if(!other) {
					listKeys.add("screen.buildguide.shapes.group.other");
					listButtons.add(null);
					other = true;
				}
				addShapeRow(key, x, w, current, available);
			}
			layoutShapeList();
			break;
		}
		case PLACEMENT: {
			// Where it stands: X / Y / Z cards (the field, - and + in the card's corner), then "At my position"
			int cw = (w - 8) / 3;
			textFieldX = originCard(x, cw, 0);
			textFieldY = originCard(x + cw + 4, cw, 1);
			textFieldZ = originCard(x + 2 * (cw + 4), cw, 2);
			addWidget(BuildGuide.widgetHandler.createButton(x, placeHereY, w, 16, new Translatable("screen.buildguide.placement.here"), () -> setOrigin()).setStyle(IButton.STYLE_SOFT));
			// This instance's name (Enter stores it)
			textFieldName = BuildGuide.widgetHandler.createTextField(x, nameY, w, 14, available ? instanceName() : "");
			textFieldName.setOnEnter(() -> applyName());
			addWidget(textFieldName);
			// In this world: up to four instances around the current one, then the full list
			int count = BuildGuide.stateManager.getState().shapeSets.size(), set = BuildGuide.stateManager.getState().getShapeSetIndex();
			int first = Math.max(0, Math.min(set - 1, count - instanceRows)), ry = worldY + 11;
			for(int i = first;i < Math.min(count, first + instanceRows);++i) {
				final int index = i;
				addWidget(BuildGuide.widgetHandler.createButton(x, ry, w, rowHeight - 1, new Translatable(instanceLabel(i)), () -> selectShapeSet(index)).setStyle(i == set ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST));
				ry += rowHeight;
			}
			addWidget(BuildGuide.widgetHandler.createButton(x, ry, w, rowHeight - 1, new Translatable("screen.buildguide.placement.all", "" + count), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shapelist))).setStyle(IButton.STYLE_GHOST));
			// Saved projects: presets, carried to any world
			savedY = ry + rowHeight + 4;
			addWidget(BuildGuide.widgetHandler.createButton(x, savedY + 11, w, 16, new Translatable("screen.buildguide.placement.save"), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this))).setStyle(IButton.STYLE_PRIMARY));
			addWidget(BuildGuide.widgetHandler.createButton(x, savedY + 29, w, rowHeight - 1, new Translatable("screen.buildguide.placement.open"), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this))).setStyle(IButton.STYLE_GHOST));
			refreshOriginFields();
			break;
		}
		case SCAN: {
			// The modes are the next stage (drawn as cards); the shapes that use the real ground work now
			int uy = scanCardsY + scanModeKeys.length * (scanCardHeight + 2) + 4 + 11;
			for(String key: scanShapes) {
				int index = registryIndex(key);
				if(index < 0) continue;
				IButton b = BuildGuide.widgetHandler.createButton(x, uy, w, rowHeight - 1, new Translatable(key), () -> pickShape(index)).setStyle(index == current ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST);
				b.setActive(available);
				addWidget(b);
				uy += rowHeight;
			}
			break;
		}
		case CHECK: {
			buttonValidateNow = BuildGuide.widgetHandler.createButton(x, top + 44, w, 16, new Translatable("screen.buildguide.check.validate"), () -> {
				if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
			}).setStyle(IButton.STYLE_PRIMARY);
			buttonValidateNow.setActive(available);
			addWidget(buttonValidateNow);
			addWidget(BuildGuide.widgetHandler.createButton(x, top + 100, w, 16, new Translatable("screen.buildguide.panel.findblocks"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Visualisation))).setStyle(IButton.STYLE_OUTLINE));
			addWidget(BuildGuide.widgetHandler.createButton(x, top + 118, w, 16, new Translatable("screen.buildguide.check.exclusions"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Exclusions))).setStyle(IButton.STYLE_OUTLINE));
			break;
		}
		default:
		}
	}

	private void addShapeRow(String key, int x, int w, int current, boolean available) {
		int index = registryIndex(key);
		if(index < 0) return;
		IButton b = BuildGuide.widgetHandler.createButton(x, shapeListTop(), w - 4, rowHeight - 1, new Translatable(key), () -> pickShape(index)).setStyle(index == current ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST);
		b.setActive(available);
		addWidget(b);
		listKeys.add(key);
		listButtons.add(b);
	}

	private static boolean matches(String key, String filter) {
		return filter.isEmpty() || new Translatable(key).toString().toLowerCase(Locale.ROOT).contains(filter);
	}

	// Places the shape list for the search and the scroll: a group shows only with a matching shape; a row that
	// does not fit the list's area is hidden (so it cannot be clicked either)
	private void layoutShapeList() {
		String filter = shapeFilter.toLowerCase(Locale.ROOT);
		int n = listKeys.size();
		boolean[] shown = new boolean[n];
		boolean groupHasRow = false;
		for(int i = n - 1;i >= 0;--i) {
			if(listButtons.get(i) == null) {
				shown[i] = groupHasRow;
				groupHasRow = false;
			}else {
				shown[i] = matches(listKeys.get(i), filter);
				groupHasRow |= shown[i];
			}
		}
		listTotalHeight = 0;
		for(int i = 0;i < n;++i) if(shown[i]) listTotalHeight += listButtons.get(i) == null ? groupHeight : rowHeight;
		int view = shapeListBottom() - shapeListTop();
		shapeScroll = Math.max(0, Math.min(shapeScroll, listTotalHeight - view));
		listY = new int[n];
		int y = shapeListTop() - shapeScroll;
		for(int i = 0;i < n;++i) {
			IButton b = listButtons.get(i);
			listY[i] = shown[i] ? y : -1;
			if(b != null) {
				b.setYPosition(y);
				b.setVisibility(shown[i] && y >= shapeListTop() && y + rowHeight - 1 <= shapeListBottom());
			}
			if(shown[i]) y += b == null ? groupHeight : rowHeight;
		}
	}

	// One X / Y / Z card: the field fills its lower part, - and + sit in its top-right corner
	private ITextField originCard(int x, int w, int axis) {
		ITextField field = BuildGuide.widgetHandler.createTextField(x + 2, cardsY + 12, w - 4, 14, "");
		field.setOnEnter(() -> applyOrigin(field, axis));
		addWidget(BuildGuide.widgetHandler.createButton(x + w - 20, cardsY + 2, 9, 9, new Translatable("-"), () -> shiftOrigin(axis == 0 ? -1 : 0, axis == 1 ? -1 : 0, axis == 2 ? -1 : 0)).setStyle(IButton.STYLE_RAIL));
		addWidget(BuildGuide.widgetHandler.createButton(x + w - 10, cardsY + 2, 9, 9, new Translatable("+"), () -> shiftOrigin(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0)).setStyle(IButton.STYLE_RAIL));
		addWidget(field);
		return field;
	}

	// "2 - East island" (a middle dot between): the set's number and its name (or its shape's name)
	private String instanceLabel(int index) {
		ShapeSet set = BuildGuide.stateManager.getState().shapeSets.get(index);
		String name = set.getName();
		if(name == null) name = set.isShapeAvailable() ? new Translatable(set.getShape().getTranslationKey()).toString() + " #" + (index + 1) : "-";
		return (index + 1) + " \u00B7 " + name;
	}

	private void selectShapeSet(int index) {
		BuildGuide.stateManager.getState().setShapeSetIndex(index);
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}

	private void openPanel(int panel) {
		if(panel == railKeys.length - 1) {
			BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Settings));
			return;
		}
		BuildGuide.stateManager.getState().sidePanel = panel == sidePanel() ? NONE : panel;
		BuildGuide.screenHandler.showScreen(this);
	}

	// A shape picked in the list: the list closes, so the preview gets the room back
	private void pickShape(int index) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().getCurrentShape().onDeselectedInGUI();
		BuildGuide.stateManager.getState().setShape(index);
		BuildGuide.stateManager.getState().sidePanel = NONE;
		BuildGuide.screenHandler.showScreen(this);
	}

	// Under the widgets: the side panel's ground, its right border and the Placement cards
	private void renderSidePanelBackground() {
		int h = wrapper.getHeight(), x = sideX(), w = sideW();
		fillRect(railWidth, top, railWidth + sideWidth, h, Theme.PANEL);
		fillRect(railWidth + sideWidth - 1, top, railWidth + sideWidth, h, Theme.BORDER);
		if(sidePanel() == PLACEMENT) {
			int cw = (w - 8) / 3;
			for(int a = 0;a < 3;++a) fillRound(x + a * (cw + 4), cardsY, x + a * (cw + 4) + cw, cardsY + cardHeight, Theme.SURFACE);
		}
	}

	private void sectionLabel(String key, int y) {
		drawTextLeft(new Translatable(key).toString().toUpperCase(Locale.ROOT), sideX(), y, Theme.TEXT_FAINT);
	}

	private void renderSidePanel() {
		int panel = sidePanel();
		if(panel == NONE) return;
		int x = sideX(), w = sideW();
		drawTextLeft(new Translatable(panelTitleKeys[panel]).toString().toUpperCase(Locale.ROOT), x, top + 5, Theme.TEXT_MUTED);
		Shape shape = currentShapeOrNull();
		switch(panel) {
		case SHAPES: {
			for(int i = 0;i < listKeys.size() && i < listY.length;++i) {
				int y = listY[i];
				if(listButtons.get(i) != null || y < shapeListTop() || y + groupHeight > shapeListBottom()) continue;
				drawTextLeft(new Translatable(listKeys.get(i)).toString(), x, y + 3, Theme.TEXT_FAINT);
			}
			// A slim scrollbar when the list is longer than its area
			int view = shapeListBottom() - shapeListTop();
			if(listTotalHeight > view) {
				int bx = x + w - 2, thumb = Math.max(12, view * view / listTotalHeight);
				int ty = shapeListTop() + (view - thumb) * shapeScroll / (listTotalHeight - view);
				fillRect(bx, shapeListTop(), bx + 2, shapeListBottom(), Theme.CONTROL);
				fillRect(bx, ty, bx + 2, ty + thumb, Theme.ACCENT_DIM);
			}
			break;
		}
		case PLACEMENT: {
			int cw = (w - 8) / 3;
			for(int a = 0;a < 3;++a) drawTextLeft(axisNames[a], x + a * (cw + 4) + 4, cardsY + 3, Theme.TEXT_MUTED);
			sectionLabel("screen.buildguide.placement.name", nameLabelY);
			sectionLabel("screen.buildguide.placement.world", worldY + 2);
			sectionLabel("screen.buildguide.placement.saved", savedY + 2);
			break;
		}
		case SCAN: {
			drawTextRight(new Translatable("screen.buildguide.scan.soon").toString(), x + w, top + 5, Theme.WARN);
			for(int i = 0;i < scanModeKeys.length;++i) {
				int y = scanCardsY + i * (scanCardHeight + 2);
				fillRound(x, y, x + w, y + scanCardHeight, Theme.SURFACE);
				drawTextLeft(fit(new Translatable("screen.buildguide.scan." + scanModeKeys[i]).toString(), w - 10), x + 6, y + 3, Theme.TEXT_SOFT);
				drawTextLeft(fit(new Translatable("screen.buildguide.scan." + scanModeKeys[i] + ".hint").toString(), w - 10), x + 6, y + 13, Theme.TEXT_FAINT);
			}
			sectionLabel("screen.buildguide.scan.usenow", scanCardsY + scanModeKeys.length * (scanCardHeight + 2) + 6);
			break;
		}
		case CHECK: {
			if(shape == null) break;
			int[] counts = ValidationListComponent.counts(shape);
			if(buttonValidateNow != null) buttonValidateNow.setVisibility(counts == null);
			if(counts == null) {
				drawTextLeft(fit(new Translatable("screen.buildguide.check.notvalidated").toString(), w), x, top + 18, Theme.TEXT_SOFT);
				drawTextLeft(fit(new Translatable("screen.buildguide.check.toverify", "" + shape.getExpectedBlocks().size()).toString(), w), x, top + 30, Theme.TEXT_FAINT);
				break;
			}
			ValidationState state = shape.getValidationState();
			drawTextLeft("\u00A7l" + String.format(Locale.ROOT, "%.1f%%", 100.0 * state.getProgress()), x, top + 18, Theme.TEXT);
			fillRound(x, top + 30, x + w, top + 35, Theme.CONTROL);
			int fillX = x + (int) Math.round(w * state.getProgress());
			if(fillX > x + 1) fillRound(x, top + 30, fillX, top + 35, state.getOk() == state.getTotal() ? Theme.OK : Theme.ACCENT);
			String[] labels = {"screen.buildguide.check.built", "screen.buildguide.check.missing", "screen.buildguide.check.wronglayer", "screen.buildguide.check.errors"};
			int[] colours = {Theme.OK, Theme.TEXT_MUTED, Theme.WARN, Theme.ERROR};
			int[] values = {state.getOk(), counts[1], counts[3], counts[0]};
			for(int i = 0;i < labels.length;++i) {
				int y = top + 42 + i * 12;
				drawTextLeft(new Translatable(labels[i]).toString(), x, y, colours[i]);
				drawTextRight("\u00A7l" + values[i], x + w, y, Theme.TEXT);
			}
			break;
		}
		default:
		}
	}

	// ---- Rendering ----

	@Override
	public void renderBackground() {
		int h = wrapper.getHeight();
		// The flat look of the new interface (Theme): the top bar and the rail on the darkest ground, the panels a step lighter
		fillRect(0, 0, wrapper.getWidth(), top, Theme.BACKGROUND);
		fillRect(0, top, railWidth, h, Theme.BACKGROUND);
		if(sidePanel() != NONE) renderSidePanelBackground();
		fillRect(px1() - 1, previewTop, px2() + 1, bands.previewBottom, Theme.BORDER);
		fillRect(px1(), previewTop + 1, px2(), bands.previewBottom - 1, Theme.FIELD);
		fillRect(inspectorX(), top, wrapper.getWidth(), h, Theme.PANEL);
	}

	public void render() {
		updateFillTitle();
		super.render();
		renderHeaderText();
		renderSidePanel();
		renderPreview();
		renderStatusLine();
		renderLegend();
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		int[] headerY = headerPositions(shape);
		for(int s = 0;s < shape.getSectionCount();++s) renderSectionHeader(headerY[s], shape.getSectionName(s).toString(), shape.countRows(s), s == shape.getOpenSection());
	}

	private void renderHeaderText() {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		int index = BuildGuide.stateManager.getState().getShapeSetIndex();
		drawShadowCentred(count == 0 ? "-" : (index + 1) + "/" + count, setPrevX + arrowWidth + setTextWidth / 2, headerTextY, 0xFFFFFF);
		// The instance's name after the selector, up to the close button
		int nameX = setPrevX + 2 * arrowWidth + setTextWidth + 6, nameEnd = wrapper.getWidth() - AbstractWidgetHandler.defaultSize - 4;
		if(count > 0 && nameEnd > nameX + 20) drawShadowLeft(fit(instanceName(), nameEnd - nameX), nameX, headerTextY, 0xAAAAAA);
	}

	private void renderPreview() {
		int x1 = px1(), x2 = px2(), y1 = previewAreaTop(), y2 = previewAreaBottom();
		if(!compact()) drawShadowCentred(fit(textPreviewHint.toString(), x2 - x1 - 4), (x1 + x2) / 2, y2 + 1, 0x888888);
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		updateSlice();
		PreviewModel model = preview.update(shape);
		shownModel = model;
		int midY = (y1 + y2) / 2 - 4;
		if(model == null) drawShadowCentred(textGenerating.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isEmpty()) drawShadowCentred(textEmpty.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else if(model.isViewEmpty()) drawShadowCentred(textNoMatch.toString(), (x1 + x2) / 2, midY, 0xAAAAAA);
		else wrapper.drawShapePreview(x1, y1, x2, y2, model, preview.camera);
		String focus = focusNotice(shape);
		if(focus != null) drawShadowLeft(fit(focus, x2 - x1 - 6), x1 + 3, y1 + 3, 0x66CCFF);
		String fillStatus = SmartFill.status();
		if(fillStatus != null) drawShadowLeft(fit(fillStatus, x2 - x1 - 6), x1 + 3, y2 - 20, SmartFill.isRunning() ? 0x80FF80 : 0xAAAAAA);
		if(shape.worldGate.isPending()) drawShadowLeft(fit(textWorldPending.toString(), x2 - x1 - 6), x1 + 3, y2 - 10, 0xFFCC66);
		else {
			int scanning = shape.getValidationState().getScanPercent();
			if(scanning >= 0) drawShadowLeft(new Translatable("screen.buildguide.scanning", scanning + "%").toString(), x1 + 3, y2 - 10, 0xFFCC66);
		}
		int blocks = shape.getNumberOfBlocks();
		if(blocks > largeShapeBlocks) drawShadowRight(new Translatable("screen.buildguide.largeshape", "" + blocks).toString(), x2 - 3, y2 - 10, 0xFFCC66);
	}

	// Under the preview: the progress line, then "Missing N   built ok / total" left of Fill
	private void renderStatusLine() {
		int x1 = px1(), x2 = px2(), progressBottom = bands.progressTop + 2, textWidth = x2 - x1 - fillWidth - 8;
		Shape shape = currentShapeOrNull();
		fillRect(x1, bands.progressTop, x2, progressBottom, Theme.CONTROL);
		if(shape == null) {
			drawShadowLeft("-", x1 + 3, bands.statusTextY, 0x888888);
			return;
		}
		ValidationState state = shape.getValidationState();
		int[] counts = ValidationListComponent.counts(shape);
		if(counts == null) {
			drawShadowLeft(fit(new Translatable("screen.buildguide.status.notvalidated", "" + shape.getExpectedBlocks().size()).toString(), textWidth), x1 + 3, bands.statusTextY, 0xAAAAAA);
			return;
		}
		int fill = x1 + (int) Math.round((x2 - x1) * state.getProgress());
		if(fill > x1) fillRect(x1, bands.progressTop, fill, progressBottom, state.getOk() == state.getTotal() ? Theme.OK : Theme.ACCENT);
		String percent = String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * state.getProgress());
		String text = new Translatable("screen.buildguide.status.missing", "" + counts[1]) + "   " + new Translatable("screen.buildguide.status.built", "" + state.getOk(), "" + state.getTotal(), percent);
		if(counts[0] > 0) text += "   " + new Translatable("screen.buildguide.status.errors", "" + counts[0]);
		if(counts[3] > 0) text += "   " + new Translatable("screen.buildguide.status.wronglayer", "" + counts[3]);
		drawShadowLeft(fit(text, textWidth), x1 + 3, bands.statusTextY, counts[0] > 0 ? 0xFF8080 : 0xFFFFFF);
	}

	// The colour legend at the foot of the preview: by status (with the wrong-layer orange under a palette), or by
	// layer with what each layer is
	private static final String[] legendKeys = {"screen.buildguide.legend.built", "screen.buildguide.legend.errors", "screen.buildguide.legend.ignored", "screen.buildguide.legend.missing"};
	private static final int[] legendColours = {PreviewColours.OK, PreviewColours.ERROR, PreviewColours.IGNORED, PreviewColours.MISSING};
	public static final int legendSwatch = 8, legendTextX = 11;

	private void renderLegend() {
		boolean byLayer = shownModel != null && shownModel.colourMode == ColourMode.LAYER;
		int entries = byLayer ? shownModel.layers.count() : hasPalette() ? legendKeys.length + 1 : legendKeys.length;
		int x1 = px1(), step = (px2() - x1) / entries, y = bands.legendY;
		for(int i = 0;i < entries;++i) {
			int x = x1 + i * step;
			boolean wrongLayer = i == legendKeys.length;
			fillRect(x + 2, y, x + 2 + legendSwatch, y + legendSwatch, 0xFF000000 | (byLayer ? ShapeLayers.colour(i + 1) : wrongLayer ? PreviewColours.WRONG_LAYER : legendColours[i]));
			String text = byLayer ? legendLabel(i + 1, shownModel.layers.legend(i + 1)) : new Translatable(wrongLayer ? "screen.buildguide.legend.wronglayer" : legendKeys[i]).toString();
			drawShadowLeft(fit(text, step - legendTextX - 2), x + legendTextX, y, 0xAAAAAA);
		}
	}

	private boolean hasPalette() {
		Shape shape = currentShapeOrNull();
		return shape != null && shape.getValidationState().getPalette() != null;
	}

	// A layer's legend (ShapeLayers.legend), its number first: "2 3b" (3 blocks), "4 25%", "3 rest", "5 spots", "5 -"
	public static String legendLabel(int layer, int[] legend) {
		String n = "" + layer, value = "" + legend[1];
		switch(legend[0]) {
		case ShapeLayers.LEGEND_BLOCKS: return new Translatable("screen.buildguide.legend.blocks", n, value).toString();
		case ShapeLayers.LEGEND_BOTTOM:
		case ShapeLayers.LEGEND_TOP:
		case ShapeLayers.LEGEND_BASE: return new Translatable("screen.buildguide.legend.percent", n, value).toString();
		case ShapeLayers.LEGEND_REST: return new Translatable("screen.buildguide.legend.rest", n).toString();
		case ShapeLayers.LEGEND_PATCHES: return new Translatable("screen.buildguide.legend.patches", n).toString();
		default: return new Translatable("screen.buildguide.legend.empty", n).toString();
		}
	}

	// ---- Preview controls ----

	private void initPreviewControls(boolean available) {
		int x = px1() + 2, x2 = px2(), y = previewTop + 2, axis = preview.getSliceAxis();
		Shape shape = currentShapeOrNull();
		layerControls = shape != null && shape.hasLayers();
		buttonColour = null;
		buttonFocusLayer = null;
		sliderSlice = null;
		buttonSliceAxis = null;
		buttonFocusPart = null;
		buttonPreviewExpand = BuildGuide.widgetHandler.createButton(x2 - expandSize - 2, y, expandSize, expandSize, new Translatable("+"), () -> BuildGuide.screenHandler.showScreen(new PreviewScreen(this)));
		buttonPreviewExpand.setActive(available);
		addWidget(buttonPreviewExpand);
		// Fill at the status bar's right end
		fillTitle = SmartFill.title();
		buttonFill = BuildGuide.widgetHandler.createButton(x2 - fillWidth, bands.previewBottom + 2, fillWidth, controlsHeight, new Translatable(fillTitle), () -> {
			if(SmartFill.isRunning()) SmartFill.stop();
			else BuildGuide.screenHandler.showScreen(new FillScreen(this));
			updateFillTitle();
		});
		buttonFill.setActive(available);
		addWidget(buttonFill);
		if(compact()) return;
		// Row 1: colour (with Layers on), slice axis, slice slider. Row 2: Part, Layer
		int colourSpace = 0;
		if(layerControls) {
			buttonColour = BuildGuide.widgetHandler.createButton(x, y, colourWidth, controlsHeight, new Translatable(preview.getColourMode() == ColourMode.LAYER ? "screen.buildguide.colour.layer" : "screen.buildguide.colour.status"), () -> {
				preview.setColourMode(preview.getColourMode() == ColourMode.LAYER ? ColourMode.STATUS : ColourMode.LAYER);
				BuildGuide.screenHandler.showScreen(this);
			});
			addWidget(buttonColour);
			colourSpace = colourWidth + 2;
		}
		String axisName = axis == PreviewModel.SLICE_OFF ? new Translatable("screen.buildguide.slice.off").toString() : axisNames[axis];
		buttonSliceAxis = BuildGuide.widgetHandler.createButton(x + colourSpace, y, sliceWidth, controlsHeight, new Translatable("screen.buildguide.slice", axisName), () -> cycleSliceAxis());
		buttonSliceAxis.setActive(available);
		addWidget(buttonSliceAxis);
		PreviewModel model = preview.getModel();
		int sliderX = x + colourSpace + sliceWidth + 2, sliderW = x2 - expandSize - 4 - sliderX;
		if(axis != PreviewModel.SLICE_OFF && model != null && !model.isEmpty() && sliderW >= 30) {
			sliderSlice = BuildGuide.widgetHandler.createSlider(sliderX, y, sliderW, controlsHeight, new Translatable(axisNames[axis]), model.min(axis), Math.max(model.max(axis), model.min(axis) + 1), preview.getSliceValue());
			addWidget(sliderSlice);
		}
		buttonFocusPart = BuildGuide.widgetHandler.createButton(x, focusRowY, partWidth, controlsHeight, partTitle(shape), () -> {
			Shape s = currentShapeOrNull();
			if(s == null) return;
			s.setFocus(FocusFilter.nextPart(s.getFocusPart(), s.focusParts()), s.getFocusLayer());
			buttonFocusPart.setTitle(partTitle(s));
		});
		buttonFocusPart.setActive(available);
		addWidget(buttonFocusPart);
		if(layerControls) {
			buttonFocusLayer = BuildGuide.widgetHandler.createButton(x + partWidth + 2, focusRowY, layerWidth, controlsHeight, layerTitle(shape), () -> {
				Shape s = currentShapeOrNull();
				if(s == null) return;
				s.setFocus(s.getFocusPart(), FocusFilter.nextLayer(s.getFocusLayer(), s.getLayers() != null ? s.getLayers().count() : ShapeLayers.COUNT));
				buttonFocusLayer.setTitle(layerTitle(s));
			});
			addWidget(buttonFocusLayer);
		}
	}

	private void updateFillTitle() {
		String title = SmartFill.title();
		if(buttonFill == null || title.equals(fillTitle)) return;
		fillTitle = title;
		buttonFill.setTitle(new Translatable(title));
	}

	private static Translatable partTitle(Shape shape) {
		FocusFilter.Part part = shape == null || shape.getFocusPart() == null ? FocusFilter.Part.ALL : shape.getFocusPart();
		if(shape != null && !shape.hasParts() && part != FocusFilter.Part.HIDDEN) part = FocusFilter.Part.ALL;
		return new Translatable("screen.buildguide.focus.part", new Translatable(part.translationKey).toString());
	}

	private static Translatable layerTitle(Shape shape) {
		int layer = shape == null ? FocusFilter.LAYER_ALL : shape.getFocusLayer();
		return new Translatable("screen.buildguide.focus.layer", layer == FocusFilter.LAYER_ALL ? new Translatable("screen.buildguide.focus.layer.all").toString() : "" + layer);
	}

	/**
	 * The preview's corner note: "Focus: Cap, Layer 2: 2626 of 8097 blocks" while the world shows a focus,
	 * "Focus off: < 400 blocks" while one is selected but the shape is below the threshold, else null.
	 */
	public static String focusNotice(Shape shape) {
		if(shape == null || !shape.ready || shape.error) return null;
		FocusFilter focus = shape.getWorldFocus();
		int total = shape.getNumberOfBlocks();
		if(focus != null) {
			String what = focus.part == FocusFilter.Part.ALL ? "" : new Translatable(focus.part.translationKey).toString();
			if(focus.layer != FocusFilter.LAYER_ALL && focus.part != FocusFilter.Part.HIDDEN) {
				String layer = new Translatable("screen.buildguide.focus.layern", "" + focus.layer).toString();
				what = what.isEmpty() ? layer : what + ", " + layer;
			}
			return new Translatable("screen.buildguide.focus.on", what, "" + shape.getWorldBlocks(), "" + total).toString();
		}
		int threshold = Shape.focusThreshold();
		if(shape.isFocusSelected() && total < threshold) return new Translatable("screen.buildguide.focus.off", "" + threshold).toString();
		return null;
	}

	private void cycleSliceAxis() {
		int axis = preview.getSliceAxis() == 2 ? PreviewModel.SLICE_OFF : preview.getSliceAxis() + 1;
		PreviewModel model = preview.getModel();
		int value = axis == PreviewModel.SLICE_OFF || model == null || model.isEmpty() ? 0 : (model.min(axis) + model.max(axis)) / 2;
		preview.setSlice(axis, value);
		BuildGuide.screenHandler.showScreen(this);
	}

	private void updateSlice() {
		if(sliderSlice == null) return;
		PreviewModel model = preview.getModel();
		int axis = preview.getSliceAxis();
		int value = (int) Math.round(sliderSlice.getSliderValue());
		if(model != null) value = Math.max(model.min(axis), Math.min(model.max(axis), value));
		if(value != preview.getSliceValue()) preview.setSlice(axis, value);
		sliderSlice.setSliderValue(value);
		sliderSlice.updateText();
	}

	private int previewAreaTop() {
		return compact() ? previewTop + 1 : previewTop + previewControlsHeight + focusRowHeight;
	}

	private int previewAreaBottom() {
		return bands.legendY - (compact() ? 2 : legendHeight);
	}

	private boolean inPreviewArea(double x, double y) {
		return x >= px1() && x < px2() && y >= previewAreaTop() && y < previewAreaBottom();
	}

	// ---- Inspector (the accordion of the shape's sections) ----

	// Header strip: "> Name" or "v Name" on a dark strip, the row count on the right
	private void renderSectionHeader(int y, String name, int rows, boolean open) {
		int ix = inspectorX();
		fillRect(ix, y, wrapper.getWidth(), y + headerHeight - 1, open ? Theme.SELECTED : Theme.SURFACE);
		if(open) fillRect(ix, y, ix + 2, y + headerHeight - 1, Theme.ACCENT);
		drawShadowLeft(fit((open ? "v " : "> ") + name, panelWidth - 24), ix + 4, y + 1, Theme.rgb(open ? Theme.TEXT : Theme.TEXT_SOFT));
		drawShadowRight("" + rows, wrapper.getWidth() - 4, y + 1, Theme.rgb(Theme.TEXT_FAINT));
	}

	// Y of every section header; the open section's rows follow its header
	private int[] headerPositions(Shape shape) {
		int[] headerY = new int[shape.getSectionCount()];
		int y = accordionTop;
		for(int s = 0;s < shape.getSectionCount();++s) {
			headerY[s] = y;
			y += headerHeight;
			if(s == shape.getOpenSection()) y += shape.countRows(s) * Property.rowHeight;
		}
		return headerY;
	}

	private void layout() {
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		int open = shape.getOpenSection();
		if(open != -1) shape.setRowsTop(headerPositions(shape)[open] + headerHeight);
		shape.onSelectedInGUI();
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(x >= px1() && x < px2() && y >= previewTop && y < bands.previewBottom) {
			return preview.mouseClicked(inPreviewArea(x, y), button, doubleClick);
		}
		Shape shape = currentShapeOrNull();
		if(button != MOUSE_LEFT || x < inspectorX() || shape == null) return false;
		int[] headerY = headerPositions(shape);
		for(int i = 0;i < headerY.length;++i) {
			if(y < headerY[i] || y >= headerY[i] + headerHeight) continue;
			// Accordion: opening one section closes the other; clicking the open one closes it
			shape.setOpenSection(shape.getOpenSection() == i ? -1 : i);
			layout();
			return true;
		}
		return false;
	}

	@Override
	public boolean onMouseDragged(double dx, double dy) {
		return preview.mouseDragged(dx, dy);
	}

	// The Colour and Layer buttons follow the Layers box: a click that turned it on or off rebuilds the screen
	@Override
	public void onMouseReleased() {
		preview.mouseReleased();
		Shape shape = currentShapeOrNull();
		if((shape != null && shape.hasLayers()) != layerControls) BuildGuide.screenHandler.showScreen(this);
	}

	@Override
	public boolean onMouseScrolled(double x, double y, double amount) {
		// The wheel over the shape list scrolls it, two rows a notch
		if(sidePanel() == SHAPES && x >= railWidth && x < railWidth + sideWidth && y >= shapeListTop()) {
			shapeScroll -= (int) Math.signum(amount) * 2 * rowHeight;
			layoutShapeList();
			return true;
		}
		return preview.mouseScrolled(inPreviewArea(x, y), amount);
	}

	// ---- Instances, origin ----

	private Shape currentShapeOrNull() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	private String shapeName() {
		Shape shape = currentShapeOrNull();
		return shape == null ? "-" : new Translatable(shape.getTranslationKey()).toString();
	}

	// Name typed for the current set, or "Type #N" (N = its position in the list)
	private String instanceName() {
		String name = BuildGuide.stateManager.getState().getCurrentShapeSet().getName();
		if(name != null) return name;
		return shapeName() + " #" + (BuildGuide.stateManager.getState().getShapeSetIndex() + 1);
	}

	private void applyName() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().getCurrentShapeSet().setName(textFieldName.getTextValue()); // empty = default name; saved with the world
		BaseScreen.shouldUpdatePersistence = true;
	}

	private void switchShapeSet(int delta) {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		if(count < 2) return;
		BuildGuide.stateManager.getState().setShapeSetIndex(Math.floorMod(BuildGuide.stateManager.getState().getShapeSetIndex() + delta, count));
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}

	private void refreshOriginFields() {
		boolean available = BuildGuide.stateManager.getState().isShapeAvailable();
		ITextField[] fields = {textFieldX, textFieldY, textFieldZ};
		for(int a = 0;a < 3;++a) {
			if(fields[a] == null) continue;
			int v = !available ? 0 : a == 0 ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginX() : a == 1 ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginY() : BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginZ();
			fields[a].setTextValue(available ? "" + v : "-");
			fields[a].setTextColour(0xFFFFFF);
		}
	}

	// Enter in an origin field: set that axis, or mark the field red when it is not an integer
	private void applyOrigin(ITextField field, int axis) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		try {
			int value = Integer.parseInt(field.getTextValue().trim());
			if(axis == 0) BuildGuide.stateManager.getState().setOriginX(value);
			else if(axis == 1) BuildGuide.stateManager.getState().setOriginY(value);
			else BuildGuide.stateManager.getState().setOriginZ(value);
			field.setTextColour(0xFFFFFF);
		}catch(NumberFormatException e) {
			field.setTextColour(0xFF0000);
		}
	}

	private void setOrigin() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().resetOrigin();
		BaseScreen.shouldUpdatePersistence = true;
		refreshOriginFields();
	}

	private void shiftOrigin(int dx, int dy, int dz) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().shiftOrigin(dx, dy, dz);
		refreshOriginFields();
	}
}
