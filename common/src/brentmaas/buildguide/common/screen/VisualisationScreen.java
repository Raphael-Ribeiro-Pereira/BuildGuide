package brentmaas.buildguide.common.screen;

import java.util.Arrays;
import java.util.Random;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.ValidationListComponent.Category;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;

/**
 * Two topics (2026-10-22), chosen with the buttons right of the content: Display (colours, cube sizes,
 * rendering and Check errors, as before) and Find blocks: the positions of the current shape by coordinate,
 * one tab at a time (errors, missing, ignored, wrong layer), which the Shape tab showed under its preview until
 * then. Clicking a row marks that position in the world (the overlay's white cube).
 */
public class VisualisationScreen extends BaseScreen {
	// Topic buttons in the free column right of the cube size block (x 400..478)
	public static final int topicX = 400, topicWidth = 78;
	// Find blocks: tabs and list left of the topic buttons, above the bottom bar
	public static final int findX1 = 2, findX2 = 396, findTabsTop = 42, findTabsBottom = 58, findListTop = 60;
	private Translatable textCheckErrors = new Translatable("screen.buildguide.checkerrors");
	private Translatable textFindHint = new Translatable("screen.buildguide.find.hint");
	private ICheckboxRunnableButton buttonCheckErrors;
	private final ValidationListComponent findList = new ValidationListComponent();
	private Translatable titleShapeColour = new Translatable("screen.buildguide.shapecolour");
	private Translatable titleOriginColour = new Translatable("screen.buildguide.origincolour");
	private Translatable titleRendering = new Translatable("screen.buildguide.rendering");
	private Translatable textDepthTest = new Translatable("screen.buildguide.depthtest");
	private Translatable textHighlightErrors = new Translatable("screen.buildguide.highlighterrors");
	private Translatable titleCubeSize = new Translatable("screen.buildguide.cubesize");
	
	private ISlider sliderShapeR = BuildGuide.widgetHandler.createSlider(5, 60, new Translatable("R"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getShapeColourR() : ShapeSet.defaultColourShapeR);
	private ISlider sliderShapeG = BuildGuide.widgetHandler.createSlider(5, 80, new Translatable("G"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getShapeColourG() : ShapeSet.defaultColourShapeG);
	private ISlider sliderShapeB = BuildGuide.widgetHandler.createSlider(5, 100, new Translatable("B"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getShapeColourB() : ShapeSet.defaultColourShapeB);
	private ISlider sliderShapeA = BuildGuide.widgetHandler.createSlider(5, 120, new Translatable("A"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getShapeColourA() : ShapeSet.defaultColourShapeA);
	private ISlider sliderOriginR = BuildGuide.widgetHandler.createSlider(140, 60, new Translatable("R"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginColourR() : ShapeSet.defaultColourOriginR);
	private ISlider sliderOriginG = BuildGuide.widgetHandler.createSlider(140, 80, new Translatable("G"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginColourG() : ShapeSet.defaultColourOriginG);
	private ISlider sliderOriginB = BuildGuide.widgetHandler.createSlider(140, 100, new Translatable("B"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginColourB() : ShapeSet.defaultColourOriginB);
	private ISlider sliderOriginA = BuildGuide.widgetHandler.createSlider(140, 120, new Translatable("A"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginColourA() : ShapeSet.defaultColourOriginA);
	private ISlider sliderShapeCubeSize = BuildGuide.widgetHandler.createSlider(275, 60, new Translatable("screen.buildguide.shape"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getShapeCubeSize() : ShapeSet.defaultShapeCubeSize);
	private ISlider sliderOriginCubeSize = BuildGuide.widgetHandler.createSlider(275, 80, new Translatable("screen.buildguide.origin"), 0.0, 1.0, BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet().getOriginCubeSize() : ShapeSet.defaultOriginCubeSize);
	
	private IButton buttonSetShape = BuildGuide.widgetHandler.createButton(5, 140, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setShapeColour((float) sliderShapeR.getSliderValue(), (float) sliderShapeG.getSliderValue(), (float) sliderShapeB.getSliderValue(), (float) sliderShapeA.getSliderValue());
		}
	});
	private IButton buttonSetOrigin = BuildGuide.widgetHandler.createButton(140, 140, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setOriginColour((float) sliderOriginR.getSliderValue(), (float) sliderOriginG.getSliderValue(), (float) sliderOriginB.getSliderValue(), (float) sliderOriginA.getSliderValue());
		}
	});
	private IButton buttonSetShapeRandom = BuildGuide.widgetHandler.createButton(5, 160, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.setrandom"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			Random random = new Random();
			sliderShapeR.setSliderValue(random.nextDouble());
			sliderShapeG.setSliderValue(random.nextDouble());
			sliderShapeB.setSliderValue(random.nextDouble());
			sliderShapeR.updateText();
			sliderShapeG.updateText();
			sliderShapeB.updateText();
			BuildGuide.stateManager.getState().getCurrentShapeSet().setShapeColour((float) sliderShapeR.getSliderValue(), (float) sliderShapeG.getSliderValue(), (float) sliderShapeB.getSliderValue(), (float) sliderShapeA.getSliderValue());
		}
	});
	private IButton buttonSetOriginRandom = BuildGuide.widgetHandler.createButton(140, 160, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.setrandom"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			Random random = new Random();
			sliderOriginR.setSliderValue(random.nextDouble());
			sliderOriginG.setSliderValue(random.nextDouble());
			sliderOriginB.setSliderValue(random.nextDouble());
			sliderOriginR.updateText();
			sliderOriginG.updateText();
			sliderOriginB.updateText();
			BuildGuide.stateManager.getState().getCurrentShapeSet().setOriginColour((float) sliderOriginR.getSliderValue(), (float) sliderOriginG.getSliderValue(), (float) sliderOriginB.getSliderValue(), (float) sliderOriginA.getSliderValue());
		}
	});
	private IButton buttonDefaultShape = BuildGuide.widgetHandler.createButton(5, 180, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
		sliderShapeR.setSliderValue(1.0);
		sliderShapeG.setSliderValue(1.0);
		sliderShapeB.setSliderValue(1.0);
		sliderShapeA.setSliderValue(0.5);
		sliderShapeR.updateText();
		sliderShapeG.updateText();
		sliderShapeB.updateText();
		sliderShapeA.updateText();
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setShapeColour(1.0f, 1.0f, 1.0f, 0.5f);
		}
	});
	private IButton buttonDefaultOrigin = BuildGuide.widgetHandler.createButton(140, 180, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
		sliderOriginR.setSliderValue(1.0);
		sliderOriginG.setSliderValue(0.0);
		sliderOriginB.setSliderValue(0.0);
		sliderOriginA.setSliderValue(0.5);
		sliderOriginR.updateText();
		sliderOriginG.updateText();
		sliderOriginB.updateText();
		sliderOriginA.updateText();
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setOriginColour(1.0f, 0.0f, 0.0f, 0.5f);
		}
	});
	private ICheckboxRunnableButton buttonHighlightErrors;
	private ICheckboxRunnableButton buttonDepthTest;
	private IButton buttonSetCubeSize = BuildGuide.widgetHandler.createButton(275, 100, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setCubeSize(sliderShapeCubeSize.getSliderValue(), sliderOriginCubeSize.getSliderValue());
		}
	});
	private IButton buttonDefaultCubeSize = BuildGuide.widgetHandler.createButton(275, 120, 120, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
		sliderShapeCubeSize.setSliderValue(0.6);
		sliderOriginCubeSize.setSliderValue(0.2);
		sliderShapeCubeSize.updateText();
		sliderOriginCubeSize.updateText();
		if(BuildGuide.stateManager.getState().isShapeAvailable()) {
			BuildGuide.stateManager.getState().getCurrentShapeSet().setCubeSize(0.6, 0.2);
		}
	});
	
	public void init() {
		super.init();
		boolean find = BuildGuide.stateManager.getState().findBlocksOpen;
		addWidget(BuildGuide.widgetHandler.createButton(topicX, 42, topicWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.topic.display"), () -> showTopic(false), find));
		addWidget(BuildGuide.widgetHandler.createButton(topicX, 62, topicWidth, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.topic.find"), () -> showTopic(true), !find));
		if(find) {
			initFind();
			return;
		}

		if(!BuildGuide.stateManager.getState().isShapeAvailable()) {
			sliderShapeR.setActive(false);
			sliderShapeG.setActive(false);
			sliderShapeB.setActive(false);
			sliderShapeA.setActive(false);
			sliderOriginR.setActive(false);
			sliderOriginG.setActive(false);
			sliderOriginB.setActive(false);
			sliderOriginA.setActive(false);
			buttonSetShape.setActive(false);
			buttonSetShapeRandom.setActive(false);
			buttonDefaultShape.setActive(false);
			buttonSetOrigin.setActive(false);
			buttonSetOriginRandom.setActive(false);
			buttonDefaultOrigin.setActive(false);
		}
		
		buttonDepthTest = BuildGuide.widgetHandler.createCheckbox(275, 165, new Translatable(""), BuildGuide.stateManager.getState().isDepthTest(), false, () -> {
			BuildGuide.stateManager.getState().setDepthTest(buttonDepthTest.isCheckboxSelected());
			BaseScreen.shouldUpdatePersistence = true;
		});
		// Validation overlay toggle (red/yellow/orange cubes on wrong/ignored/near blocks)
		buttonHighlightErrors = BuildGuide.widgetHandler.createCheckbox(275, 185, new Translatable(""), BuildGuide.stateManager.getState().isHighlightErrors(), false, () -> {
			BuildGuide.stateManager.getState().setHighlightErrors(buttonHighlightErrors.isCheckboxSelected());
		});
		// Look for structure errors (solid blocks around the shape) at all; off by default (2026-10-22)
		buttonCheckErrors = BuildGuide.widgetHandler.createCheckbox(275, 205, new Translatable(""), BuildGuide.stateManager.getState().isCheckErrors(), false, () -> {
			BuildGuide.stateManager.getState().setCheckErrors(buttonCheckErrors.isCheckboxSelected());
		});
		
		addWidget(sliderShapeR);
		addWidget(sliderShapeG);
		addWidget(sliderShapeB);
		addWidget(sliderShapeA);
		addWidget(buttonSetShape);
		addWidget(buttonSetShapeRandom);
		addWidget(buttonDefaultShape);
		addWidget(sliderOriginR);
		addWidget(sliderOriginG);
		addWidget(sliderOriginB);
		addWidget(sliderOriginA);
		addWidget(buttonSetOrigin);
		addWidget(buttonSetOriginRandom);
		addWidget(buttonDefaultOrigin);
		addWidget(buttonDepthTest);
		addWidget(buttonHighlightErrors);
		addWidget(buttonCheckErrors);
		addWidget(sliderShapeCubeSize);
		addWidget(sliderOriginCubeSize);
		addWidget(buttonSetCubeSize);
		addWidget(buttonDefaultCubeSize);
	}
	
	// Three columns within y 40..250 since E7 (R10: the cube size and rendering blocks reached y 305):
	// shape colour x 5, origin colour x 140, cube size then rendering x 275. Fixed-size controls, so the
	// extra height of a larger window is left empty; the bottom bar is back on this tab
	public void render() {
		super.render();
		if(BuildGuide.stateManager.getState().findBlocksOpen) {
			renderFind();
			return;
		}

		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleShapeColour, 65, 45, 0xFFFFFF);
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleOriginColour, 200, 45, 0xFFFFFF);
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleRendering, 335, 150, 0xFFFFFF);
		drawShadowLeft(textDepthTest.toString(), 300, 170, 0xFFFFFF);
		drawShadowLeft(textHighlightErrors.toString(), 300, 190, 0xFFFFFF);
		drawShadowLeft(textCheckErrors.toString(), 300, 210, 0xFFFFFF);
		drawShadowCentred(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + titleCubeSize, 335, 45, 0xFFFFFF);
	}

	// The other topic: the screen is built again with its widgets
	private void showTopic(boolean find) {
		BuildGuide.stateManager.getState().findBlocksOpen = find;
		BuildGuide.screenHandler.showScreen(this);
	}

	private static Shape currentShapeOrNull() {
		return BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShape() : null;
	}

	// The tab picked before, else Errors when there are any, Missing otherwise
	private static Category initialTab(Shape shape) {
		if(BuildGuide.stateManager.getState().listTab != null) return BuildGuide.stateManager.getState().listTab;
		int[] counts = ValidationListComponent.counts(shape);
		return counts != null && counts[0] > 0 ? Category.ERRORS : Category.MISSING;
	}

	// The tab first, then one build of its rows by init (setCategory would build them for the old list too)
	private void initFind() {
		Shape shape = currentShapeOrNull();
		findList.selectCategory(initialTab(shape));
		findList.init(findX1, findListTop, findX2, wrapper.getHeight() - 22, shape);
		addWidget(findList.getList());
	}

	private int tabX(int i, int count) {
		return findX1 + i * (findX2 - findX1) / count;
	}

	// The tabs with live counts, then the list rows (the list widget draws itself), and a hint left of the bottom bar
	private void renderFind() {
		Shape shape = currentShapeOrNull();
		int[] counts = ValidationListComponent.counts(shape);
		Category[] tabs = ValidationListComponent.tabs(shape);
		// The wrong-layer tab went with the palette: back to the errors
		if(!Arrays.asList(tabs).contains(findList.getCategory())) findList.setCategory(Category.ERRORS);
		for(int i = 0;i < tabs.length;++i) {
			int tx1 = tabX(i, tabs.length), tx2 = tabX(i + 1, tabs.length);
			boolean selected = tabs[i] == findList.getCategory();
			fillRect(tx1, findTabsTop, tx2 - 1, findTabsBottom, selected ? 0xC0505050 : 0xC0202020);
			// Four tabs share the width with shorter labels (4D): "Err 3", "Miss 120", "Ign 0", "Layer 5"
			String label = new Translatable(tabs.length > 3 ? tabs[i].shortKey : tabs[i].translationKey, counts == null ? "-" : "" + counts[tabs[i].ordinal()]).toString();
			drawShadowCentred(label, (tx1 + tx2) / 2, findTabsTop + 4, selected ? 0xFFFFFF : 0xAAAAAA);
		}
		findList.update(shape);
		drawShadowLeft(fit(textFindHint.toString(), barX1 - 10), 5, wrapper.getHeight() - 10, 0x888888);
	}

	@Override
	public boolean onMouseClicked(double x, double y, int button, boolean doubleClick) {
		if(!BuildGuide.stateManager.getState().findBlocksOpen || button != MOUSE_LEFT || x < findX1 || x >= findX2 || y < findTabsTop || y >= findTabsBottom) return false;
		Category[] tabs = ValidationListComponent.tabs(currentShapeOrNull());
		for(int i = 0;i < tabs.length;++i) {
			if(x >= tabX(i, tabs.length) && x < tabX(i + 1, tabs.length)) {
				BuildGuide.stateManager.getState().listTab = tabs[i];
				findList.setCategory(tabs[i]);
				return true;
			}
		}
		return false;
	}
}
