package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.screen.widget.IShapeList;
import brentmaas.buildguide.common.screen.widget.ISlider;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.PreviewCamera;
import brentmaas.buildguide.common.shape.PreviewModel;

public interface IScreenWrapper {
	public void attachScreen(BaseScreen screen);
	
	public void show();
	
	public void addButton(IButton button);
	
	public void addTextField(ITextField textField);
	
	public void addCheckbox(ICheckboxRunnableButton checkbox);
	
	public void addSlider(ISlider slider);
	
	public void addShapeList(IShapeList shapeList);
	
	public void addSelectorList(ISelectorList selectorList);
	
	public void drawShadow(String text, int x, int y, int colour);
	
	// Filled rectangle from (x1, y1) inclusive to (x2, y2) exclusive, ARGB colour
	public void fillRect(int x1, int y1, int x2, int y2, int colour);

	// 3D preview of a model in the area (x1, y1)..(x2, y2), GUI coordinates. Default: nothing, so
	// loaders without a preview renderer keep compiling
	public default void drawShapePreview(int x1, int y1, int x2, int y2, PreviewModel model, PreviewCamera camera) {}
	
	public int getTextWidth(String text);
	
	public int getWidth();
	
	public int getHeight();
}
