package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.WorldUpdateGate;
import brentmaas.buildguide.common.shape.SliceScan;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ITextField;

public class ConfigurationScreen extends BaseScreen {
	private ICheckboxRunnableButton buttonAsyncEnabled, buttonAdvancedRandomColorsDefaultEnabled, buttonPersistenceEnabled, buttonDebugGenerationTiminigsEnabled;
	private IButton buttonAsyncEnabledDefault, buttonAdvancedRandomColorsDefaultEnabledDefault, buttonPersistenceEnabledDefault, buttonDebugGenerationTimingsEnabledDefault;
	private ITextField textFieldIgnoredBlocks;
	private IButton buttonIgnoredBlocksSet, buttonIgnoredBlocksDefault;
	// Live apply: cycles Live, Idle, On close (WorldUpdateGate.Mode)
	private IButton buttonWorldUpdate;
	// Scan in Y layers: cycles Instant, Fast, Normal, Slow (SliceScan.Speed); top right, where no comment runs
	private IButton buttonScanSpeed;
	
	public void init() {
		super.init();
		
		buttonAsyncEnabled = BuildGuide.widgetHandler.createCheckbox(255, 40, new Translatable(""), BuildGuide.config.asyncEnabled.value, false, () -> {
			BuildGuide.config.asyncEnabled.setValue(buttonAsyncEnabled.isCheckboxSelected());
			BuildGuide.config.write();
		});
		buttonAsyncEnabledDefault = BuildGuide.widgetHandler.createButton(280, 40, 50, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
			buttonAsyncEnabled.setChecked(BuildGuide.config.asyncEnabled.getDefault());
			BuildGuide.config.write();
		});
		
		buttonAdvancedRandomColorsDefaultEnabled = BuildGuide.widgetHandler.createCheckbox(255, 90, new Translatable(""), BuildGuide.config.shapeListRandomColorsDefaultEnabled.value, false, () -> {
			BuildGuide.config.shapeListRandomColorsDefaultEnabled.setValue(buttonAdvancedRandomColorsDefaultEnabled.isCheckboxSelected());
			BuildGuide.config.write();
		});
		buttonAdvancedRandomColorsDefaultEnabledDefault = BuildGuide.widgetHandler.createButton(280, 90, 50, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
			buttonAdvancedRandomColorsDefaultEnabled.setChecked(BuildGuide.config.shapeListRandomColorsDefaultEnabled.getDefault());
			BuildGuide.config.write();
		});
		
		buttonPersistenceEnabled = BuildGuide.widgetHandler.createCheckbox(255, 140, new Translatable(""), BuildGuide.config.persistenceEnabled.value, false, () -> {
			BuildGuide.config.persistenceEnabled.setValue(buttonPersistenceEnabled.isCheckboxSelected());
			BuildGuide.config.write();
		});
		buttonPersistenceEnabledDefault = BuildGuide.widgetHandler.createButton(280, 140, 50, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
			buttonPersistenceEnabled.setChecked(BuildGuide.config.persistenceEnabled.getDefault());
			BuildGuide.config.write();
		});
		
		// Ignored block ids: free text, applied on Set (validation rescans through the shapes' own requests)
		textFieldIgnoredBlocks = BuildGuide.widgetHandler.createTextField(10, 210, 240, AbstractWidgetHandler.defaultSize, "");
		textFieldIgnoredBlocks.setTextValue(BuildGuide.config.ignoredBlocks.value);
		buttonIgnoredBlocksSet = BuildGuide.widgetHandler.createButton(255, 210, 50, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.set"), () -> {
			BuildGuide.config.ignoredBlocks.setValue(textFieldIgnoredBlocks.getTextValue());
			textFieldIgnoredBlocks.setTextValue(BuildGuide.config.ignoredBlocks.value);
			BuildGuide.config.write();
			BuildGuide.stateManager.getState().requestRescanAll();
		});
		buttonIgnoredBlocksDefault = BuildGuide.widgetHandler.createButton(310, 210, 50, AbstractWidgetHandler.defaultSize, new Translatable("screen.buildguide.default"), () -> {
			BuildGuide.config.ignoredBlocks.setValue(BuildGuide.config.ignoredBlocks.getDefault());
			textFieldIgnoredBlocks.setTextValue(BuildGuide.config.ignoredBlocks.value);
			BuildGuide.config.write();
			BuildGuide.stateManager.getState().requestRescanAll();
		});
		
		addWidget(buttonAsyncEnabled);
		addWidget(buttonAsyncEnabledDefault);
		addWidget(buttonAdvancedRandomColorsDefaultEnabled);
		addWidget(buttonAdvancedRandomColorsDefaultEnabledDefault);
		addWidget(buttonPersistenceEnabled);
		addWidget(buttonPersistenceEnabledDefault);
		buttonWorldUpdate = BuildGuide.widgetHandler.createButton(370, 210, 105, AbstractWidgetHandler.defaultSize, modeTitle(), () -> {
			WorldUpdateGate.Mode[] modes = WorldUpdateGate.Mode.values();
			BuildGuide.config.worldUpdateMode.setValue(modes[(BuildGuide.config.worldUpdateMode.value.ordinal() + 1) % modes.length]);
			BuildGuide.config.write();
			buttonWorldUpdate.setTitle(modeTitle());
		});
		
		buttonScanSpeed = BuildGuide.widgetHandler.createButton(370, 58, 105, AbstractWidgetHandler.defaultSize, speedTitle(), () -> {
			SliceScan.Speed[] speeds = SliceScan.Speed.values();
			BuildGuide.config.scanSpeed.setValue(speeds[(BuildGuide.config.scanSpeed.value.ordinal() + 1) % speeds.length]);
			BuildGuide.config.write();
			buttonScanSpeed.setTitle(speedTitle());
		});
		
		addWidget(textFieldIgnoredBlocks);
		addWidget(buttonScanSpeed);
		addWidget(buttonWorldUpdate);
		addWidget(buttonIgnoredBlocksSet);
		addWidget(buttonIgnoredBlocksDefault);
		addWidget(buttonDebugGenerationTiminigsEnabled);
		addWidget(buttonDebugGenerationTimingsEnabledDefault);
	}
	
	public void render() {
		super.render();
		
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.asyncEnabled.translationKey), 10, 45, 0xFFFFFF);
		drawShadowLeft(new Translatable(BuildGuide.config.asyncEnabled.commentTranslationKey).toString(), 10, 64, 0xFFFFFF);
		
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.shapeListRandomColorsDefaultEnabled.translationKey), 10, 95, 0xFFFFFF);
		drawShadowLeft(new Translatable(BuildGuide.config.shapeListRandomColorsDefaultEnabled.commentTranslationKey).toString(), 10, 115, 0xFFFFFF);
		
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.persistenceEnabled.translationKey), 10, 145, 0xFFFFFF);
		drawShadowLeft(new Translatable(BuildGuide.config.persistenceEnabled.commentTranslationKey).toString(), 10, 165, 0xFFFFFF);
		
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.ignoredBlocks.translationKey), 10, 190, 0xFFFFFF);
		drawShadowLeft(new Translatable(BuildGuide.config.ignoredBlocks.commentTranslationKey).toString(), 10, 234, 0xFFFFFF);
		
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.worldUpdateMode.translationKey), 370, 195, 0xFFFFFF);
		drawShadowLeft(BuildGuide.screenHandler.TEXT_MODIFIER_UNDERLINE + new Translatable(BuildGuide.config.scanSpeed.translationKey), 370, 45, 0xFFFFFF);
	}
	
	private static Translatable speedTitle() {
		return new Translatable("screen.buildguide.scanspeed." + BuildGuide.config.scanSpeed.value.name().toLowerCase(java.util.Locale.ROOT));
	}
	
	private static Translatable modeTitle() {
		return new Translatable("screen.buildguide.worldupdate." + BuildGuide.config.worldUpdateMode.value.name().toLowerCase(java.util.Locale.ROOT));
	}
}
