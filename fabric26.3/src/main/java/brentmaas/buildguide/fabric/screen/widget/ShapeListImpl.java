package brentmaas.buildguide.fabric.screen.widget;

import brentmaas.buildguide.common.screen.Theme;
import org.jetbrains.annotations.Nullable;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.screen.widget.IShapeList;
import brentmaas.buildguide.common.screen.widget.IShapeList.IEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class ShapeListImpl extends ObjectSelectionList<ShapeListImpl.Entry> implements IShapeList {
	private Runnable update;
	
	public ShapeListImpl(Minecraft minecraft, int left, int right, int top, int bottom, int slotHeight, Runnable update) {
		super(minecraft, right - left, bottom - top, top, slotHeight);
		setX(left);
		//setRenderBackground(false);
		
		this.update = update;
		
		for(int shapeSetId = 0;shapeSetId < BuildGuide.stateManager.getState().shapeSets.size();++shapeSetId) {
			addEntry(new Entry(shapeSetId));
			if(shapeSetId == BuildGuide.stateManager.getState().getShapeSetIndex()) setSelected(children().get(children().size() - 1));
		}
	}
	
	public void setYPosition(int y) {
		//Shape lists don't do y position
	}
	
	public void setVisibility(boolean visible) {
		//Shape lists don't do visibility
	}
	
	public void addEntry(int shapeSetId) {
		addEntry(new Entry(shapeSetId));
		setSelected(children().get(shapeSetId));
	}
	
	@Override
	public void removeEntry(Entry entry) {
		for(Entry e: children()) {
			if(e.getShapeSetId() > entry.getShapeSetId()) {
				e.setShapeSetId(e.getShapeSetId() - 1);
			}
		}
		if(children().size() > entry.getShapeSetId() + 1) setSelected(children().get(entry.getShapeSetId() + 1));
		else if(children().size() > 1) setSelected(children().get(entry.getShapeSetId() - 1));
		super.removeEntry(entry);
	}
	
	public boolean removeEntry(IEntry entry) {
		removeEntry((Entry) entry);
		return true;
	}
	
	@Override
	public void setSelected(@Nullable Entry entry) {
		super.setSelected(entry);
		if(entry != null) BuildGuide.stateManager.getState().setShapeSetIndex(entry.getShapeSetId());
		update.run();
	}
	
	// Flat look (Theme): the list on the field colour, no vanilla separators, the selected row lit with an accent bar
	@Override
	protected void extractListBackground(GuiGraphicsExtractor guiGraphics) {
		guiGraphics.fill(getX(), getY(), getRight(), getBottom(), Theme.FIELD);
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor guiGraphics) {
	}

	@Override
	protected void extractSelection(GuiGraphicsExtractor guiGraphics, ShapeListImpl.Entry entry, int colour) {
		guiGraphics.fill(entry.getX(), entry.getY(), entry.getX() + entry.getWidth(), entry.getY() + entry.getHeight(), Theme.SELECTED);
		guiGraphics.fill(entry.getX(), entry.getY(), entry.getX() + 2, entry.getY() + entry.getHeight(), Theme.ACCENT);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTicks);
	}
	
	@Override
	public int getRowWidth() {
		return width - 12;
	}
	
	@Override
	protected int scrollBarX() {
		return getRight() - 6;
	}
	
	public final class Entry extends ObjectSelectionList.Entry<ShapeListImpl.Entry> implements IEntry {
		private int shapeSetId;
		
		public Entry(int shapeSetId) {
			this.shapeSetId = shapeSetId;
		}
		
		public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			int colour = BuildGuide.screenHandler.getShapeProgressColour(BuildGuide.stateManager.getState().shapeSets.get(shapeSetId).getShape());
			guiGraphics.text(Minecraft.getInstance().font, BuildGuide.screenHandler.getFormattedShapeName(BuildGuide.stateManager.getState().shapeSets.get(shapeSetId)), getX() + 5, getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2, ARGB.color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF), true);
		}
		
		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean b) {
			ShapeListImpl.this.setSelected(this);
			return false;
		}
		
		public void setShapeSetId(int shapeSetId) {
			this.shapeSetId = shapeSetId;
		}
		
		public int getShapeSetId() {
			return shapeSetId;
		}
		
		public Component getNarration() {
			return Component.literal("");
		}
	}
}
