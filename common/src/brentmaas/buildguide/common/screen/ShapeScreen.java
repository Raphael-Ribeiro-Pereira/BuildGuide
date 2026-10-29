package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.PresetStore;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.State.ActiveScreen;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.FocusFilter;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.PreviewColours;
import brentmaas.buildguide.common.shape.PreviewModel;
import brentmaas.buildguide.common.shape.PreviewModel.ColourMode;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeLayers;
import brentmaas.buildguide.common.shape.ShapeRegistry;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;
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
	public static final int NONE = -1, SHAPES = 0, PLACEMENT = 1, SCAN = 2, CHECK = 3, CONFIG = 4;
	private static final String[] railKeys = {"screen.buildguide.rail.shapes", "screen.buildguide.rail.placement", "screen.buildguide.rail.scan", "screen.buildguide.rail.check", "screen.buildguide.rail.config"};
	public static final String[] panelTitleKeys = {"screen.buildguide.panel.title.shapes", "screen.buildguide.panel.title.placement", "screen.buildguide.panel.title.scan", "screen.buildguide.panel.title.check", "screen.buildguide.panel.title.config"};
	private static final int railButtonHeight = 24, railGap = 4;
	// Header (the prototype's): the shape button (opens the shape list), "< 2 of 3 >", and at the right the two
	// switches, Preview | World and, with Layers, Status | Layers
	private static final int shapeButtonX = railWidth + 4, shapeButtonWidth = 96, setPrevX = shapeButtonX + shapeButtonWidth + 4, arrowWidth = 12, setTextWidth = 40, segmentWidth = 44;
	// The view in the preview's place: the 3D preview, or the loaded world from the top (WorldView)
	private static boolean worldView = false;
	private final WorldView world = new WorldView();
	private int[] switchX = {-1, -1};
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

	private Translatable textPreviewHint = new Translatable("screen.buildguide.preview.hint");
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

	@Override
	protected boolean hasEnabledCheckbox() {
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
		buttonShape = BuildGuide.widgetHandler.createButton(shapeButtonX, 2, shapeButtonWidth, 16, new Translatable(fit(shapeName() + " v", shapeButtonWidth - 8)), () -> openPanel(SHAPES)).setStyle(IButton.STYLE_OUTLINE);
		buttonSetPrevious = BuildGuide.widgetHandler.createButton(setPrevX, 4, arrowWidth, 12, new Translatable("<"), () -> switchShapeSet(-1)).setStyle(IButton.STYLE_RAIL);
		buttonSetNext = BuildGuide.widgetHandler.createButton(setPrevX + arrowWidth + setTextWidth, 4, arrowWidth, 12, new Translatable(">"), () -> switchShapeSet(1)).setStyle(IButton.STYLE_RAIL);
		boolean severalSets = BuildGuide.stateManager.getState().shapeSets.size() > 1;
		buttonSetPrevious.setActive(severalSets);
		buttonSetNext.setActive(severalSets);
		addWidget(buttonShape);
		addWidget(buttonSetPrevious);
		addWidget(buttonSetNext);
		initHeaderSwitches();

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
		buttonValidate.setStyle(IButton.STYLE_OUTLINE);
		buttonReset.setStyle(IButton.STYLE_OUTLINE);
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

	// The header's switches: Preview | World, and Status | Layers while the shape has Layers (in the 3D preview)
	private void initHeaderSwitches() {
		switchX = new int[] {-1, -1};
		int x = wrapper.getWidth() - AbstractWidgetHandler.defaultSize - 4;
		Shape shape = currentShapeOrNull();
		if(shape != null && shape.hasLayers() && !worldView && !scanView) {
			x -= 2 * segmentWidth + 4;
			boolean byLayer = preview.getColourMode() == ColourMode.LAYER;
			addWidget(BuildGuide.widgetHandler.createButton(x + 2, 3, segmentWidth, 14, new Translatable("screen.buildguide.view.status"), () -> setColour(ColourMode.STATUS)).setStyle(byLayer ? IButton.STYLE_SEGMENT : IButton.STYLE_SEGMENT_ON));
			addWidget(BuildGuide.widgetHandler.createButton(x + 2 + segmentWidth, 3, segmentWidth, 14, new Translatable("screen.buildguide.view.layers"), () -> setColour(ColourMode.LAYER)).setStyle(byLayer ? IButton.STYLE_SEGMENT_ON : IButton.STYLE_SEGMENT));
			switchX[1] = x;
			x -= 4;
		}
		x -= 2 * segmentWidth + 4;
		addWidget(BuildGuide.widgetHandler.createButton(x + 2, 3, segmentWidth, 14, new Translatable("screen.buildguide.view.preview"), () -> setWorldView(false)).setStyle(worldView || scanView ? IButton.STYLE_SEGMENT : IButton.STYLE_SEGMENT_ON));
		addWidget(BuildGuide.widgetHandler.createButton(x + 2 + segmentWidth, 3, segmentWidth, 14, new Translatable("screen.buildguide.view.world"), () -> setWorldView(true)).setStyle(worldView && !scanView ? IButton.STYLE_SEGMENT_ON : IButton.STYLE_SEGMENT));
		switchX[0] = x;
	}

	private void setColour(ColourMode mode) {
		preview.setColourMode(mode);
		BuildGuide.screenHandler.showScreen(this);
	}

	private void setWorldView(boolean on) {
		worldView = on;
		scanView = false;
		BuildGuide.screenHandler.showScreen(this);
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
	private static final int SCAN_UNDER = 1, SCAN_ABOVE = 2, SCAN_REGION = 3;
	// Kept between inits (a pick re-inits the screen): the list's scroll, the search text, the scan's choices
	private static int shapeScroll = 0;
	private static String shapeFilter = "";
	private static int scanMode = 0;
	private static String scanY = "", scanX1 = "", scanY1 = "", scanZ1 = "";
	// The shape list in order: a group header (button null) or a shape row; its y after layoutShapeList (-1 hidden)
	private final List<String> listKeys = new ArrayList<String>();
	private final List<IButton> listButtons = new ArrayList<IButton>();
	private int[] listY = new int[0];
	private int listTotalHeight = 0;
	private ITextField textFieldSearch;
	private IButton buttonValidateNow;
	// Validation: the three "Nearest to you" rows, refreshed twice a second
	private IButton[] nearestRows = new IButton[0];
	private long[][] nearest = new long[0][];
	private long nearestAt = 0;
	// Placement: the cards, the three actions, the name, the world list's header; the saved header and rows
	private static final int cardsY = top + 16, cardHeight = 28, placeHereY = top + 48, crosshairY = top + 66, rotateY = top + 84, nameLabelY = top + 105, nameY = top + 115, worldY = top + 134, instanceRows = 3;
	private int savedY = 0, savedRowsEnd = 0;
	// Scan: the five modes, then the mode's input, the Scan button and the shapes that use the ground
	private static final int scanRowHeight = 20, scanRowsY = top + 16, scanInputY = top + 123, scanButtonY = top + 149, scanGroundY = top + 168;
	// The scan's uses (the prototype's): relief under a build, farming land on the real ground, terraform, lighting
	private static final String[] scanUseKeys = {"relief", "farm", "terraform", "light"};
	private static final int USE_FARM = 1, USE_TERRAFORM = 2, USE_LIGHT = 3;
	// The scanned area {x0, z0, width, depth} once Scan was pressed, the chosen use, Slime chunk for lighting
	private static int[] scanArea = null;
	private static int scanUse = -1;
	private static boolean slimeChunk = false;
	private static boolean scanView = false;

	// Validation rows
	private static final int checkRowsY = top + 42, nearestY = top + 92, checkButtonsY = top + 148;

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
		nearestRows = new IButton[0];
		nearestAt = 0;
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
			// Where it stands: X / Y / Z cards (the field, - and + in the card's corner), then the three actions
			int cw = (w - 8) / 3;
			textFieldX = originCard(x, cw, 0);
			textFieldY = originCard(x + cw + 4, cw, 1);
			textFieldZ = originCard(x + 2 * (cw + 4), cw, 2);
			addWidget(BuildGuide.widgetHandler.createButton(x, placeHereY, w, 16, new Translatable("screen.buildguide.placement.here"), () -> setOrigin()).setStyle(IButton.STYLE_SOFT));
			IButton crosshair = BuildGuide.widgetHandler.createButton(x, crosshairY, w, 16, new Translatable("screen.buildguide.placement.crosshair"), () -> setOriginAtCrosshair()).setStyle(IButton.STYLE_SOFT);
			crosshair.setActive(available && BuildGuide.shapeHandler.crosshairBlock() != null);
			addWidget(crosshair);
			// Rotate 90 degrees: one more quarter turn about the origin (ShapeSet.rotate), the shape generated again
			int turns = available ? BuildGuide.stateManager.getState().getCurrentShapeSet().getQuarterTurns() : 0;
			IButton rotate = BuildGuide.widgetHandler.createButton(x, rotateY, w, 16, new Translatable("screen.buildguide.placement.rotate", "" + 90 * turns), () -> rotateShape()).setStyle(IButton.STYLE_OUTLINE);
			rotate.setActive(available);
			addWidget(rotate);
			// This instance's name (Enter stores it)
			textFieldName = BuildGuide.widgetHandler.createTextField(x, nameY, w, 14, available ? instanceName() : "");
			textFieldName.setOnEnter(() -> applyName());
			addWidget(textFieldName);
			// In this world: up to three instances around the current one, then the full list
			int count = BuildGuide.stateManager.getState().shapeSets.size(), set = BuildGuide.stateManager.getState().getShapeSetIndex();
			int first = Math.max(0, Math.min(set - 1, count - instanceRows)), ry = worldY + 11;
			for(int i = first;i < Math.min(count, first + instanceRows);++i) {
				final int index = i;
				addWidget(BuildGuide.widgetHandler.createButton(x, ry, w, rowHeight - 1, new Translatable(instanceLabel(i)), () -> selectShapeSet(index)).setStyle(i == set ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST));
				ry += rowHeight;
			}
			addWidget(BuildGuide.widgetHandler.createButton(x, ry, w, rowHeight - 1, new Translatable("screen.buildguide.placement.all", "" + count), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shapelist))).setStyle(IButton.STYLE_GHOST));
			// Saved projects (presets, carried to any world): Save, then each saved one with Open
			savedY = ry + rowHeight + 4;
			IButton save = BuildGuide.widgetHandler.createButton(x, savedY + 11, w, 16, new Translatable("screen.buildguide.placement.save"), () -> BuildGuide.screenHandler.showScreen(new PresetScreen(this))).setStyle(IButton.STYLE_PRIMARY);
			save.setActive(available);
			addWidget(save);
			int sy = savedY + 30;
			for(int slot = 0;slot < PresetStore.numSlots;++slot) {
				if(BuildGuide.presets.isEmpty(slot) || sy + rowHeight - 1 > wrapper.getHeight() - 2) continue;
				final int s = slot;
				IButton open = BuildGuide.widgetHandler.createButton(x, sy, w, rowHeight - 1, new Translatable(presetLabel(slot) + "\t" + new Translatable("screen.buildguide.placement.openone")), () -> loadPreset(s)).setStyle(IButton.STYLE_ROW_ACTION);
				open.setActive(available);
				addWidget(open);
				sy += rowHeight;
			}
			savedRowsEnd = sy;
			refreshOriginFields();
			break;
		}
		case SCAN: {
			// The five modes (one chosen), the chosen mode's input, then Scan: the reading itself is the next
			// stage, so its button is not clickable yet. The shapes that use the real ground work now
			for(int i = 0;i < scanModeKeys.length;++i) {
				final int mode = i;
				addWidget(BuildGuide.widgetHandler.createButton(x, scanRowsY + i * (scanRowHeight + 1), w, scanRowHeight, new Translatable("screen.buildguide.scan." + scanModeKeys[i]), () -> {
					scanMode = mode;
					BuildGuide.screenHandler.showScreen(this);
				}).setStyle(i == scanMode ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST));
			}
			if(scanMode == SCAN_UNDER || scanMode == SCAN_ABOVE) {
				ITextField field = BuildGuide.widgetHandler.createTextField(x, scanInputY + 10, w, 14, scanY);
				field.setOnEnter(() -> scanY = field.getTextValue().trim());
				addWidget(field);
			}else if(scanMode == SCAN_REGION) {
				int cw = (w - 8) / 3;
				ITextField fx = BuildGuide.widgetHandler.createTextField(x, scanInputY + 10, cw, 14, scanX1);
				ITextField fy = BuildGuide.widgetHandler.createTextField(x + cw + 4, scanInputY + 10, cw, 14, scanY1);
				ITextField fz = BuildGuide.widgetHandler.createTextField(x + 2 * (cw + 4), scanInputY + 10, cw, 14, scanZ1);
				fx.setOnEnter(() -> scanX1 = fx.getTextValue().trim());
				fy.setOnEnter(() -> scanY1 = fy.getTextValue().trim());
				fz.setOnEnter(() -> scanZ1 = fz.getTextValue().trim());
				addWidget(fx);
				addWidget(fy);
				addWidget(fz);
			}
			addWidget(BuildGuide.widgetHandler.createButton(x, scanButtonY, w, 16, new Translatable("screen.buildguide.scan.run"), () -> doScan()).setStyle(IButton.STYLE_PRIMARY));
			// What to use the scan for, once there is one. Farming land and Terraform open their shapes (Farming
			// land, Terrain), which work on the real ground now; Relief and Lighting are the next stage
			int uy = scanGroundY + 11;
			for(int i = 0;i < scanUseKeys.length;++i) {
				final int use = i;
				IButton b = BuildGuide.widgetHandler.createButton(x, uy, w, rowHeight - 1, new Translatable("screen.buildguide.scan.use." + scanUseKeys[i]), () -> pickUse(use)).setStyle(i == scanUse ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST);
				b.setActive(scanArea != null);
				addWidget(b);
				uy += rowHeight;
			}
			if(scanUse == USE_LIGHT) {
				String state = new Translatable(slimeChunk ? "screen.buildguide.config.on" : "screen.buildguide.config.off").toString();
				addWidget(BuildGuide.widgetHandler.createButton(x, uy, w, rowHeight - 1, new Translatable(new Translatable("screen.buildguide.scan.slime").toString() + "\t" + state), () -> {
					slimeChunk = !slimeChunk;
					BuildGuide.screenHandler.showScreen(this);
				}).setStyle(slimeChunk ? IButton.STYLE_TOGGLE_ON : IButton.STYLE_TOGGLE_OFF));
			}
			break;
		}
		case CHECK: {
			buttonValidateNow = BuildGuide.widgetHandler.createButton(x, top + 44, w, 16, new Translatable("screen.buildguide.check.validate"), () -> {
				if(BuildGuide.stateManager.getState().isShapeAvailable()) BuildGuide.stateManager.getState().getCurrentShape().triggerValidation();
			}).setStyle(IButton.STYLE_PRIMARY);
			buttonValidateNow.setActive(available);
			addWidget(buttonValidateNow);
			// Nearest to you: a click lights that block in the world (again: off)
			nearestRows = new IButton[3];
			for(int i = 0;i < nearestRows.length;++i) {
				final int row = i;
				nearestRows[i] = BuildGuide.widgetHandler.createButton(x, nearestY + 11 + i * rowHeight, w, rowHeight - 1, new Translatable(""), () -> highlightNearest(row)).setStyle(IButton.STYLE_GHOST);
				nearestRows[i].setVisibility(false);
				addWidget(nearestRows[i]);
			}
			addWidget(BuildGuide.widgetHandler.createButton(x, checkButtonsY, w, 16, new Translatable("screen.buildguide.panel.findblocks"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Visualisation))).setStyle(IButton.STYLE_OUTLINE));
			addWidget(BuildGuide.widgetHandler.createButton(x, checkButtonsY + 18, w, 16, new Translatable("screen.buildguide.check.exclusions"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Exclusions))).setStyle(IButton.STYLE_OUTLINE));
			break;
		}
		case CONFIG: {
			// The prototype's settings: a row per option with an on / off pill, then the full configuration screen
			int y = top + 16;
			y = toggleRow(x, y, w, "screen.buildguide.config.showinworld", BuildGuide.stateManager.getState().isEnabled(), () -> {
				BuildGuide.stateManager.getState().setEnabled(!BuildGuide.stateManager.getState().isEnabled());
				BaseScreen.shouldUpdatePersistence = true;
			});
			y = toggleRow(x, y, w, "screen.buildguide.config.async", BuildGuide.config.asyncEnabled.value, () -> BuildGuide.config.asyncEnabled.setValue(!BuildGuide.config.asyncEnabled.value));
			y = toggleRow(x, y, w, "screen.buildguide.config.persistence", BuildGuide.config.persistenceEnabled.value, () -> BuildGuide.config.persistenceEnabled.setValue(!BuildGuide.config.persistenceEnabled.value));
			y = toggleRow(x, y, w, "screen.buildguide.config.randomcolours", BuildGuide.config.shapeListRandomColorsDefaultEnabled.value, () -> BuildGuide.config.shapeListRandomColorsDefaultEnabled.setValue(!BuildGuide.config.shapeListRandomColorsDefaultEnabled.value));
			addWidget(BuildGuide.widgetHandler.createButton(x, y + 6, w, 16, new Translatable("screen.buildguide.config.all"), () -> BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Settings))).setStyle(IButton.STYLE_OUTLINE));
			break;
		}
		default:
		}
	}

	// A settings row: the label and an on / off pill; a click flips it, stores the configuration and redraws
	private int toggleRow(int x, int y, int w, String key, boolean on, Runnable flip) {
		String state = new Translatable(on ? "screen.buildguide.config.on" : "screen.buildguide.config.off").toString();
		addWidget(BuildGuide.widgetHandler.createButton(x, y, w, 18, new Translatable(new Translatable(key).toString() + "\t" + state), () -> {
			flip.run();
			BuildGuide.config.write();
			BuildGuide.screenHandler.showScreen(this);
		}).setStyle(on ? IButton.STYLE_TOGGLE_ON : IButton.STYLE_TOGGLE_OFF));
		return y + 20;
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

	// A saved project's row: its name (or "-") and its shape
	private static String presetLabel(int slot) {
		String[] parts = PresetStore.describe(BuildGuide.presets.get(slot));
		String shape = "?";
		for(int i = 0;i < ShapeRegistry.getNumberOfShapes();++i) {
			if(ShapeRegistry.getClassIdentifier(i).equals(parts[0])) shape = new Translatable(ShapeRegistry.getTranslationKey(i)).toString();
		}
		return (parts[1] != null ? parts[1] + " \u00B7 " : "") + shape;
	}

	// Open a saved project into this instance, as the preset menu's Load does
	private void loadPreset(int slot) {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		Shape before = BuildGuide.stateManager.getState().getCurrentShape();
		String problem = BuildGuide.stateManager.getState().getCurrentShapeSet().applyPreset(BuildGuide.presets.get(slot));
		if(problem != null) return;
		if(before != BuildGuide.stateManager.getState().getCurrentShape()) before.onDeselectedInGUI();
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}

	private void selectShapeSet(int index) {
		BuildGuide.stateManager.getState().setShapeSetIndex(index);
		BuildGuide.screenHandler.showScreen(BuildGuide.stateManager.getState().createNewScreen(ActiveScreen.Shape));
	}

	private void openPanel(int panel) {
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
			if(savedRowsEnd == savedY + 30 && savedY + 32 < wrapper.getHeight() - 8) drawTextLeft(new Translatable("screen.buildguide.placement.none").toString(), x + 6, savedY + 33, Theme.TEXT_FAINT);
			break;
		}
		case SCAN: {
			for(int i = 0;i < scanModeKeys.length;++i) {
				int y = scanRowsY + i * (scanRowHeight + 1);
				drawTextLeft(fit(new Translatable("screen.buildguide.scan." + scanModeKeys[i] + ".hint").toString(), w - 12), x + 6, y + 12, Theme.TEXT_MUTED);
			}
			if(scanMode == SCAN_UNDER || scanMode == SCAN_ABOVE) drawTextLeft(new Translatable("screen.buildguide.scan.toy").toString(), x, scanInputY + 1, Theme.TEXT_MUTED);
			else if(scanMode == SCAN_REGION) drawTextLeft(new Translatable("screen.buildguide.scan.box").toString(), x, scanInputY + 1, Theme.TEXT_MUTED);
			sectionLabel("screen.buildguide.scan.usefor", scanGroundY + 2);
			break;
		}
		case CHECK: {
			if(shape == null) break;
			int[] counts = ValidationListComponent.counts(shape);
			if(buttonValidateNow != null) buttonValidateNow.setVisibility(counts == null);
			if(counts == null) {
				for(IButton row: nearestRows) row.setVisibility(false);
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
				int y = checkRowsY + i * 12;
				drawTextLeft(new Translatable(labels[i]).toString(), x, y, colours[i]);
				drawTextRight("\u00A7l" + values[i], x + w, y, Theme.TEXT);
			}
			sectionLabel("screen.buildguide.check.nearest", nearestY + 2);
			refreshNearest(shape);
			break;
		}
		default:
		}
	}

	// The three problems closest to the player, read again twice a second: "x y z \u00B7 what"
	private void refreshNearest(Shape shape) {
		long now = System.currentTimeMillis();
		if(now - nearestAt < 500) return;
		nearestAt = now;
		Origin player = BuildGuide.shapeHandler.getPlayerPosition();
		nearest = ValidationListComponent.nearest(shape, player.x, player.y, player.z, nearestRows.length);
		ValidationState state = shape.getValidationState();
		int ox = state.getScanOriginX(), oy = state.getScanOriginY(), oz = state.getScanOriginZ();
		for(int i = 0;i < nearestRows.length;++i) {
			if(i >= nearest.length) {
				nearestRows[i].setVisibility(false);
				continue;
			}
			long pos = nearest[i][0];
			int kind = (int) nearest[i][1];
			String what = kind == 0 ? state.getBlockName(pos) : kind == 1 ? new Translatable("screen.buildguide.check.kind.error").toString() : kind == 2 ? new Translatable("screen.buildguide.check.kind.layer").toString() : new Translatable("screen.buildguide.check.kind.missing").toString();
			nearestRows[i].setTitle(new Translatable((ox + LocalPos.unpackX(pos)) + " " + (oy + LocalPos.unpackY(pos)) + " " + (oz + LocalPos.unpackZ(pos)) + " \u00B7 " + (what == null ? "?" : what)));
			nearestRows[i].setStyle(pos == state.getHighlightedPos() ? IButton.STYLE_GHOST_SELECTED : IButton.STYLE_GHOST);
			nearestRows[i].setVisibility(true);
		}
	}

	private void highlightNearest(int row) {
		Shape shape = currentShapeOrNull();
		if(shape == null || row >= nearest.length) return;
		ValidationState state = shape.getValidationState();
		long pos = nearest[row][0];
		state.setHighlightedPos(pos == state.getHighlightedPos() ? -1 : pos);
		nearestAt = 0;
	}

	// Scan: the blocks of the chosen mode's box that can be seen, read from the loaded world and shown in the 3D
	// preview in their map colours (drag to turn it, wheel to zoom, as a shape). The box: 96 x 56 around you,
	// from a little under you up to a little over (Island, Hole lower), down to the typed Y (Under me), up to it
	// (Above me), or from the typed corner to you (Region); never more than 192 x 192 x 128
	public static final int scanMaxBlocks = 150000, scanMaxSide = 192, scanMaxHeight = 128;
	private static PreviewModel scanModel = null;
	private static long scanCount = 0;

	private void doScan() {
		Origin player = BuildGuide.shapeHandler.getPlayerPosition();
		int width = 96, depth = 56, x0 = player.x - width / 2, z0 = player.z - depth / 2;
		int yMin = player.y - 32, yMax = player.y + 16;
		Integer typedY = parseOrNull(scanY);
		if(scanMode == SCAN_UNDER) {
			yMax = player.y;
			yMin = typedY != null ? Math.min(typedY, player.y) : player.y - 48;
		}else if(scanMode == SCAN_ABOVE) {
			yMin = player.y;
			yMax = typedY != null ? Math.max(typedY, player.y) : player.y + 48;
		}else if(scanMode == SCAN_REGION) {
			Integer cx = parseOrNull(scanX1), cy = parseOrNull(scanY1), cz = parseOrNull(scanZ1);
			if(cx != null && cz != null) {
				x0 = Math.min(cx, player.x);
				z0 = Math.min(cz, player.z);
				width = Math.abs(cx - player.x) + 1;
				depth = Math.abs(cz - player.z) + 1;
			}
			if(cy != null) {
				yMin = Math.min(cy, player.y);
				yMax = Math.max(cy, player.y);
			}
		}else if(scanMode == 4) {
			// Hole: the space under water or the ground, below you
			yMax = player.y;
			yMin = player.y - 48;
		}
		width = Math.min(width, scanMaxSide);
		depth = Math.min(depth, scanMaxSide);
		yMin = Math.max(yMin, yMax - scanMaxHeight);
		int[] blocks = BuildGuide.shapeHandler.scanBlocks(x0, yMin, z0, width, yMax, depth, scanMaxBlocks);
		if(blocks == null) return;
		int n = blocks.length / 4, cx = x0 + width / 2, cz = z0 + depth / 2;
		long[] positions = new long[n];
		int[] rgb = new int[n];
		for(int i = 0;i < n;++i) {
			positions[i] = LocalPos.pack(blocks[4 * i] - cx, blocks[4 * i + 1] - yMin, blocks[4 * i + 2] - cz);
			rgb[i] = blocks[4 * i + 3];
		}
		// A generation of its own (below the shapes' ones), so the preview builds a new mesh for every scan
		scanModel = PreviewModel.ofColoured(positions, rgb, -2 - (++scanCount));
		scanArea = new int[] {x0, z0, width, depth, n};
		scanView = true;
		worldView = false;
		preview.camera.reset();
		BuildGuide.screenHandler.showScreen(this);
	}

	private static Integer parseOrNull(String text) {
		try {
			return Integer.valueOf(text.trim());
		}catch(NumberFormatException e) {
			return null;
		}
	}

	// A use of the scan: Farming land and Terraform open their shape, keeping the Scan panel
	private void pickUse(int use) {
		scanUse = use;
		String key = use == USE_FARM ? "shape.buildguide.farmingland" : use == USE_TERRAFORM ? "shape.buildguide.terrain" : null;
		int index = key == null ? -1 : registryIndex(key);
		if(index >= 0 && BuildGuide.stateManager.getState().isShapeAvailable() && index != BuildGuide.stateManager.getState().getCurrentShapeIndex()) {
			BuildGuide.stateManager.getState().getCurrentShape().onDeselectedInGUI();
			BuildGuide.stateManager.getState().setShape(index);
		}
		BuildGuide.screenHandler.showScreen(this);
	}

	// The scanned blocks in the 3D preview; what was scanned at the top, what the chosen use does at the foot
	private void renderScanView(int x1, int x2) {
		int y1 = previewTop + 1, y2 = bands.previewBottom - 1;
		if(scanModel != null && !scanModel.isEmpty()) wrapper.drawShapePreview(x1, y1, x2, y2, scanModel, preview.camera);
		else drawTextCentred(new Translatable("screen.buildguide.scan.empty").toString(), (x1 + x2) / 2, (y1 + y2) / 2 - 4, Theme.TEXT_MUTED);
		String mode = new Translatable("screen.buildguide.scan." + scanModeKeys[scanMode]).toString();
		chip(new Translatable("screen.buildguide.scan.title", mode, "" + scanArea[2], "" + scanArea[3]).toString(), x1 + 4, y1 + 4, x2 - x1 - 8);
		String note = scanUse < 0 ? "screen.buildguide.scan.note.choose" : "screen.buildguide.scan.note." + scanUseKeys[scanUse] + (scanUse == USE_LIGHT && slimeChunk ? ".slime" : "");
		chip(new Translatable(note).toString(), x1 + 4, y2 - 16, x2 - x1 - 8);
	}

	private void rotateShape() {
		if(!BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().getCurrentShapeSet().rotate();
		BuildGuide.stateManager.getState().getCurrentShape().update();
		BaseScreen.shouldUpdatePersistence = true;
		BuildGuide.screenHandler.showScreen(this);
	}

	// "At the crosshair block": the shape stands on the block the crosshair was on when the screen opened
	private void setOriginAtCrosshair() {
		int[] hit = BuildGuide.shapeHandler.crosshairBlock();
		if(hit == null || !BuildGuide.stateManager.getState().isShapeAvailable()) return;
		BuildGuide.stateManager.getState().setOriginX(hit[0]);
		BuildGuide.stateManager.getState().setOriginY(hit[1] + 1);
		BuildGuide.stateManager.getState().setOriginZ(hit[2]);
		BaseScreen.shouldUpdatePersistence = true;
		refreshOriginFields();
	}

	// ---- Rendering ----

	@Override
	public void renderBackground() {
		int h = wrapper.getHeight();
		// The flat look of the new interface (Theme): the top bar and the rail on the darkest ground, the panels a step lighter
		fillRect(0, 0, wrapper.getWidth(), top, Theme.BACKGROUND);
		fillRect(0, top, railWidth, h, Theme.BACKGROUND);
		if(sidePanel() != NONE) renderSidePanelBackground();
		// A row a property moved (a change in the shape re-lays its rows) is clipped again before the widgets draw
		clipInspector();
		fillRect(px1() - 1, previewTop, px2() + 1, bands.previewBottom, Theme.BORDER);
		fillRect(px1(), previewTop + 1, px2(), bands.previewBottom - 1, Theme.FIELD);
		fillRect(inspectorX(), top, wrapper.getWidth(), h, Theme.PANEL);
		fillRect(px1() - 1, bands.previewBottom, px2() + 1, h, Theme.BACKGROUND);
		for(int x: switchX) if(x >= 0) fillRound(x, 1, x + 2 * segmentWidth + 4, 19, Theme.FIELD);
	}

	public void render() {
		updateFillTitle();
		super.render();
		renderHeaderText();
		renderSidePanel();
		renderPreview();
		renderStatusLine();
		renderLegend();
		drawTextCentred("\u00A7lBG", railWidth / 2, 6, Theme.ACCENT);
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		renderTabs(shape);
		renderInspectorScrollbar(shape);
	}

	private void renderHeaderText() {
		int count = BuildGuide.stateManager.getState().shapeSets.size();
		int index = BuildGuide.stateManager.getState().getShapeSetIndex();
		drawTextCentred(count == 0 ? "-" : new Translatable("screen.buildguide.header.instance", "" + (index + 1), "" + count).toString(), setPrevX + arrowWidth + setTextWidth / 2, headerTextY, Theme.TEXT_FAINT);
	}

	private void renderPreview() {
		int x1 = px1(), x2 = px2(), y1 = previewAreaTop(), y2 = previewAreaBottom();
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		if(scanView && scanArea != null && scanModel != null) {
			renderScanView(x1, x2);
			return;
		}
		if(worldView) {
			renderWorldView(shape, x1, x2);
			return;
		}
		if(!compact()) drawTextRight(fit(textPreviewHint.toString(), (x2 - x1) / 2), x2 - 4, y1 + 3, Theme.TEXT_FAINT);
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

	// See in the world: the loaded ground from the top with the shape in blue, a note at the top and at the foot
	private void renderWorldView(Shape shape, int x1, int x2) {
		int y1 = previewTop + 1, y2 = bands.previewBottom - 1;
		world.draw(this, x1, y1, x2, y2, shape, BuildGuide.stateManager.getState().getCurrentShapeSet());
		chip(new Translatable("screen.buildguide.world.title").toString(), x1 + 4, y1 + 4, x2 - x1 - 8);
		chip(new Translatable("screen.buildguide.world.note", "" + world.getStep()).toString(), x1 + 4, y2 - 16, x2 - x1 - 8);
	}

	// A note on a dark rounded ground, over the preview
	private void chip(String text, int x, int y, int maxWidth) {
		String shown = fit(text, maxWidth - 8);
		fillRound(x, y, x + wrapper.getTextWidth(shown) + 8, y + 12, Theme.OVERLAY);
		drawTextLeft(shown, x + 4, y + 2, Theme.TEXT_SOFT);
	}

	// The status bar (the prototype's): "12430 blocks", a short bar, "82% built", the problems; Fill at the right
	private void renderStatusLine() {
		int x = px1() + 3, end = px2() - fillWidth - 6, y = bands.previewBottom + 6;
		Shape shape = currentShapeOrNull();
		if(shape == null) {
			drawTextLeft("-", x, y, Theme.TEXT_FAINT);
			return;
		}
		x = statusPiece("\u00A7l" + shape.getNumberOfBlocks() + "\u00A7r " + new Translatable("screen.buildguide.status.blocks"), x, end, y, Theme.TEXT_SOFT);
		ValidationState state = shape.getValidationState();
		int[] counts = ValidationListComponent.counts(shape);
		if(counts == null) {
			statusPiece(new Translatable("screen.buildguide.check.notvalidated").toString(), x, end, y, Theme.TEXT_FAINT);
			return;
		}
		if(x + 40 <= end) {
			fillRound(x, y + 2, x + 36, y + 6, Theme.CONTROL);
			int fill = x + (int) Math.round(36 * state.getProgress());
			if(fill > x + 1) fillRound(x, y + 2, fill, y + 6, state.getOk() == state.getTotal() ? Theme.OK : Theme.ACCENT);
			x += 42;
		}
		x = statusPiece(new Translatable("screen.buildguide.status.builtpercent", String.format(Locale.ROOT, "%.0f%%", 100.0 * state.getProgress())).toString(), x, end, y, Theme.OK);
		if(counts[3] > 0) x = statusPiece(new Translatable("screen.buildguide.status.wronglayercount", "" + counts[3]).toString(), x, end, y, Theme.WARN);
		if(counts[0] > 0) statusPiece(new Translatable("screen.buildguide.status.errorscount", "" + counts[0]).toString(), x, end, y, Theme.ERROR);
	}

	// One piece of the status bar if it fits before end; the x after it (end when it did not fit)
	private int statusPiece(String text, int x, int end, int y, int colour) {
		int width = wrapper.getTextWidth(text);
		if(x + width > end) return end;
		drawTextLeft(text, x, y, colour);
		return x + width + 8;
	}

	// The colour legend at the foot of the preview as the prototype's chips: by status (with the wrong-layer orange
	// under a palette), or by layer with what each layer is; the chips that do not fit are left out
	private static final String[] legendKeys = {"screen.buildguide.legend.built", "screen.buildguide.legend.errors", "screen.buildguide.legend.ignored", "screen.buildguide.legend.missing"};
	private static final int[] legendColours = {PreviewColours.OK, PreviewColours.ERROR, PreviewColours.IGNORED, PreviewColours.MISSING};
	public static final int legendSwatch = 8, legendTextX = 11;

	private void renderLegend() {
		if(worldView || scanView) return;
		boolean byLayer = shownModel != null && shownModel.colourMode == ColourMode.LAYER;
		int entries = byLayer ? shownModel.layers.count() : hasPalette() ? legendKeys.length + 1 : legendKeys.length;
		int x = px1() + 3, end = px2() - 3, y = bands.legendY;
		for(int i = 0;i < entries;++i) {
			boolean wrongLayer = i == legendKeys.length;
			int colour = 0xFF000000 | (byLayer ? ShapeLayers.colour(i + 1) : wrongLayer ? PreviewColours.WRONG_LAYER : legendColours[i]);
			String text = byLayer ? legendLabel(i + 1, shownModel.layers.legend(i + 1)) : new Translatable(wrongLayer ? "screen.buildguide.legend.wronglayer" : legendKeys[i]).toString();
			int width = 14 + wrapper.getTextWidth(text) + 4;
			if(x + width > end) break;
			fillRound(x, y - 1, x + width, y + 10, Theme.CHIP);
			fillRect(x + 4, y + 1, x + 10, y + 7, colour);
			drawTextLeft(text, x + 14, y + 1, Theme.TEXT_SOFT);
			x += width + 3;
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
		buttonPreviewExpand.setStyle(IButton.STYLE_OUTLINE);
		if(!worldView && !scanView) addWidget(buttonPreviewExpand);
		// Fill at the status bar's right end
		fillTitle = SmartFill.title();
		buttonFill = BuildGuide.widgetHandler.createButton(x2 - fillWidth, bands.previewBottom + 2, fillWidth, controlsHeight, new Translatable(fillTitle), () -> {
			if(SmartFill.isRunning()) SmartFill.stop();
			else BuildGuide.screenHandler.showScreen(new FillScreen(this));
			updateFillTitle();
		});
		buttonFill.setActive(available);
		buttonFill.setStyle(IButton.STYLE_PRIMARY);
		addWidget(buttonFill);
		if(compact() || worldView || scanView) return;
		// Row 1: slice axis, slice slider (the colour is the header's Status | Layers). Row 2: Part, Layer
		int colourSpace = 0;
		String axisName = axis == PreviewModel.SLICE_OFF ? new Translatable("screen.buildguide.slice.off").toString() : axisNames[axis];
		buttonSliceAxis = BuildGuide.widgetHandler.createButton(x + colourSpace, y, sliceWidth, controlsHeight, new Translatable("screen.buildguide.slice", axisName), () -> cycleSliceAxis());
		buttonSliceAxis.setActive(available);
		buttonSliceAxis.setStyle(IButton.STYLE_OUTLINE);
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
		buttonFocusPart.setStyle(IButton.STYLE_OUTLINE);
		addWidget(buttonFocusPart);
		if(layerControls) {
			buttonFocusLayer = BuildGuide.widgetHandler.createButton(x + partWidth + 2, focusRowY, layerWidth, controlsHeight, layerTitle(shape), () -> {
				Shape s = currentShapeOrNull();
				if(s == null) return;
				s.setFocus(s.getFocusPart(), FocusFilter.nextLayer(s.getFocusLayer(), s.getLayers() != null ? s.getLayers().count() : ShapeLayers.COUNT));
				buttonFocusLayer.setTitle(layerTitle(s));
			});
			buttonFocusLayer.setStyle(IButton.STYLE_OUTLINE);
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

	// ---- Inspector (the prototype's tabs: one per section of the shape, on one row or two) ----

	public static final int tabHeight = 14;

	private static int tabsPerRow(int sections) {
		return sections <= 3 ? Math.max(1, sections) : (sections + 1) / 2;
	}

	private static int tabsPerRow(Shape shape) {
		return tabsPerRow(shape.getSectionCount());
	}

	private static int tabRows(int sections) {
		return (sections + tabsPerRow(sections) - 1) / tabsPerRow(sections);
	}

	private static int tabRows(Shape shape) {
		return tabRows(shape.getSectionCount());
	}

	// The rows the inspector holds under its tabs at a GUI height, for a shape with this many sections (the tests
	// check every shape's largest section against it)
	public static int inspectorCapacity(int height, int sections) {
		return (ShapeLayout.accordionHeight(height) - tabRows(sections) * tabHeight - 4) / Property.rowHeight;
	}

	// Tab i's box {x1, y1, x2, y2}
	private int[] tabBox(Shape shape, int i) {
		int perRow = tabsPerRow(shape), ix = inspectorX(), w = panelWidth / perRow;
		int row = i / perRow, column = i % perRow;
		int x1 = ix + column * w, x2 = column == perRow - 1 ? wrapper.getWidth() : x1 + w;
		return new int[] {x1, accordionTop + row * tabHeight, x2, accordionTop + (row + 1) * tabHeight};
	}

	// The open tab in white with the accent under it, the others muted; a line under the tabs
	private void renderTabs(Shape shape) {
		int open = shape.getOpenSection();
		for(int i = 0;i < shape.getSectionCount();++i) {
			int[] box = tabBox(shape, i);
			boolean on = i == open;
			if(on) fillRect(box[0] + 2, box[3] - 2, box[2] - 2, box[3], Theme.ACCENT);
			drawTextCentred(fit(shape.getSectionName(i).toString(), box[2] - box[0] - 4), (box[0] + box[2]) / 2, box[1] + 3, on ? Theme.TEXT : Theme.TEXT_MUTED);
		}
		int bottom = accordionTop + tabRows(shape) * tabHeight;
		fillRect(inspectorX(), bottom, wrapper.getWidth(), bottom + 1, Theme.BORDER);
	}

	// The inspector scrolls (2026-10-28) when the open tab has more rows than fit: the wheel over it moves the rows
	// a row a notch, the rows outside its area are clipped (Property.setClipped)
	private static int inspectorScroll = 0;

	private int rowsTop(Shape shape) {
		return accordionTop + tabRows(shape) * tabHeight + 4;
	}

	private int maxInspectorScroll(Shape shape) {
		int open = shape.getOpenSection();
		if(open < 0) return 0;
		return Math.max(0, shape.countRows(open) * Property.rowHeight - (bands.accordionBottom - rowsTop(shape)));
	}

	private void layout() {
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		// A tab is always open (the accordion allowed none)
		if(shape.getOpenSection() < 0 && shape.getSectionCount() > 0) shape.setOpenSection(0);
		inspectorScroll = Math.max(0, Math.min(inspectorScroll, maxInspectorScroll(shape)));
		shape.setRowsTop(rowsTop(shape) - inspectorScroll);
		shape.onSelectedInGUI();
		clipInspector();
	}

	// Rows above the tabs' line or below the inspector's area are not drawn and cannot be clicked
	private void clipInspector() {
		Shape shape = currentShapeOrNull();
		if(shape == null) return;
		int top = rowsTop(shape), bottom = bands.accordionBottom;
		for(Property<?> p: properties) p.setClipped(p.getY() < top || p.getY() + Property.rowHeight > bottom);
	}

	// A slim bar at the inspector's right edge while it scrolls
	private void renderInspectorScrollbar(Shape shape) {
		int max = maxInspectorScroll(shape);
		if(max <= 0) return;
		int top = rowsTop(shape), bottom = bands.accordionBottom, view = bottom - top, total = view + max;
		int thumb = Math.max(12, view * view / total), ty = top + (view - thumb) * inspectorScroll / max, bx = wrapper.getWidth() - 3;
		fillRect(bx, top, bx + 2, bottom, Theme.CONTROL);
		fillRect(bx, ty, bx + 2, ty + thumb, Theme.ACCENT_DIM);
	}

	// ---- Input ----

	@Override
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(x >= px1() && x < px2() && y >= previewTop && y < bands.previewBottom) {
			return preview.mouseClicked(inPreviewArea(x, y), button, doubleClick);
		}
		Shape shape = currentShapeOrNull();
		if(button != MOUSE_LEFT || x < inspectorX() || shape == null) return false;
		for(int i = 0;i < shape.getSectionCount();++i) {
			int[] box = tabBox(shape, i);
			if(x < box[0] || x >= box[2] || y < box[1] || y >= box[3]) continue;
			shape.setOpenSection(i);
			inspectorScroll = 0;
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
		// The wheel over the inspector's rows scrolls them, a row a notch
		Shape scrolled = currentShapeOrNull();
		if(scrolled != null && x >= inspectorX() && y >= rowsTop(scrolled) && y < bands.accordionBottom && maxInspectorScroll(scrolled) > 0) {
			inspectorScroll -= (int) Math.signum(amount) * Property.rowHeight;
			layout();
			return true;
		}
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
