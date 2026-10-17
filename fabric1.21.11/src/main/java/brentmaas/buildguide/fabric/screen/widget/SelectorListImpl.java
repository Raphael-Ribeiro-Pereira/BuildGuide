package brentmaas.buildguide.fabric.screen.widget;

import java.util.List;

import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.screen.widget.ISelectorList.IEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SelectorListImpl extends ObjectSelectionList<SelectorListImpl.Entry> implements ISelectorList {
	protected ISelectorListCallback callback;
	
	public SelectorListImpl(Minecraft minecraft, int left, int right, int top, int bottom, int slotHeight, List<Translatable> titles, int current, ISelectorListCallback callback) {
		super(minecraft, right - left, bottom - top, top, slotHeight);
		setX(left);
		//setRenderBackground(false);
		
		this.callback = callback;
		
		for(int i = 0;i < titles.size();++i) {
			addEntry(new Entry(i, titles.get(i)));
		}
		setSelected(children().get(current));
	}
	
	public void setEntries(List<Translatable> titles) {
		double scroll = scrollAmount();
		clearEntries();
		for(int i = 0;i < titles.size();++i) addEntry(new Entry(i, titles.get(i)));
		setScrollAmount(scroll);
	}
	
	/**
	 * Every row has the slot height, so the next row's y and the content height follow from the row count.
	 * Vanilla's AbstractSelectionList sums the height of every row it holds each time addEntry places a new
	 * one, so filling n rows cost n^2: 9 000 rows took about 0.2 s, 50 000 about 3 s (performance step 3)
	 */
	@Override
	public int getNextY() {
		return getY() + 2 - (int) scrollAmount() + children().size() * defaultEntryHeight;
	}
	
	@Override
	protected int contentHeight() {
		return children().size() * defaultEntryHeight + 4;
	}
	
	public void setYPosition(int y) {
		//Selector lists don't do y position
	}
	
	public void setVisibility(boolean visible) {
		//Selector lists don't do visibility
	}
	
	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		guiGraphics.fill(getX(), getY(), getRight(), getBottom(), (int) 0x33000000);
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
	}
	
	@Override
	public int getRowWidth() {
		return width - 12;
	}
	
	@Override
	protected int scrollBarX() {
		return getRight() - 6;
	}
	
	public final class Entry extends ObjectSelectionList.Entry<SelectorListImpl.Entry> implements IEntry {
		private int index;
		private Translatable title;
		
		public Entry(int index, Translatable title) {
			this.index = index;
			this.title = title;
		}
		
		public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			guiGraphics.drawString(Minecraft.getInstance().font, title.toString(), getX() + 5, getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2, 0xFFFFFFFF, true);
		}
		
		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean b) {
			SelectorListImpl.this.callback.run(index);
			return false;
		}
		
		public Component getNarration() {
			return Component.literal("");
		}
	}
}
