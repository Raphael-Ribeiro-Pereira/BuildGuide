package brentmaas.buildguide.common.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.IButton;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.SmartFillPlan;
import brentmaas.buildguide.common.shape.SmartFillPlan.Group;

/**
 * Smart fill (2026-10-22): the panel the Shape tab's Fill button opens, over the menu like the Preview window.
 * Left, the colours of what the world shows of the shape and is still empty (the Part and Layer buttons choose
 * it): one button each, beside its colour, with how many blocks and the blocks chosen. Right, the blocks the
 * player carries, the shulker boxes and backpacks among them counted in: a click puts one in the selected
 * colour's choice or takes it out, and two or more mix. Start places them (SmartFill) and goes back.
 *
 * The plan and the inventory are read once, when the panel opens; opening it again reads them again.
 */
public class FillScreen extends BaseScreen {
	public static final int panelX1 = 20, panelTop = 42, panelMarginBottom = 4;
	// Left column: the colours, at most SmartFillPlan.maxGroups rows; right column: the carried blocks
	public static final int swatchX = 26, groupX = 42, groupWidth = 152, groupsTop = 70, groupRowHeight = 18, listX1 = 204, listTop = 70;
	public static final int bottomButtonWidth = 80, bottomRowFromBottom = 26;

	private final BaseScreen parent;
	private final SmartFill.Plan plan;
	// Block id -> how many the player carries, most first; null when it could not be read
	private final Map<String, Integer> stock;
	private final List<String> stockIds = new ArrayList<String>();
	private final Map<String, String> names = new HashMap<String, String>();
	private String message;
	private int selected = 0;
	private IButton[] groupButtons = new IButton[0];
	private ISelectorList list = null;
	private IButton buttonStart = null;

	public FillScreen(BaseScreen parent) {
		this.parent = parent;
		plan = SmartFill.prepare(BuildGuide.stateManager.getState().isShapeAvailable() ? BuildGuide.stateManager.getState().getCurrentShapeSet() : null);
		message = plan.message;
		Map<String, Integer> carried = message == null ? BuildGuide.shapeHandler.carriedBlocks() : null;
		if(message == null && carried == null) message = "screen.buildguide.smartfill.noinventory";
		stock = carried;
		if(stock != null) {
			stockIds.addAll(stock.keySet());
			stockIds.sort((a, b) -> stock.get(a).equals(stock.get(b)) ? name(a).compareTo(name(b)) : Integer.compare(stock.get(b), stock.get(a)));
			SmartFill.fitToStock(plan, stock, BuildGuide.shapeHandler.heldBlockId());
		}
	}

	@Override
	public void init() {
		super.init();
		int h = wrapper.getHeight(), w = wrapper.getWidth(), bottomY = h - bottomRowFromBottom;
		addWidget(BuildGuide.widgetHandler.createButton(w - panelX1 - 2 * bottomButtonWidth - 6, bottomY, bottomButtonWidth, 20, new Translatable("screen.buildguide.smartfill.back"), () -> back()));
		if(message != null) return;
		groupButtons = new IButton[plan.groups.size()];
		for(int i = 0;i < groupButtons.length;++i) {
			final int group = i;
			groupButtons[i] = BuildGuide.widgetHandler.createButton(groupX, groupsTop + i * groupRowHeight, groupWidth, groupRowHeight - 2, new Translatable(groupTitle(i)), () -> {
				selected = group;
				refresh();
			});
			addWidget(groupButtons[i]);
		}
		addWidget(BuildGuide.widgetHandler.createButton(swatchX, bottomY, bottomButtonWidth, 20, new Translatable("screen.buildguide.smartfill.clear"), () -> {
			SmartFill.clear(plan, selected);
			refresh();
		}));
		buttonStart = BuildGuide.widgetHandler.createButton(w - panelX1 - bottomButtonWidth - 2, bottomY, bottomButtonWidth, 20, new Translatable("screen.buildguide.smartfill.go"), () -> {
			if(SmartFill.start(plan)) back();
		});
		addWidget(buttonStart);
		if(!stockIds.isEmpty()) {
			list = BuildGuide.widgetHandler.createSelectorList(listX1, w - panelX1 - 4, listTop, bottomY - 6, ValidationListComponent.rowHeight, rows(), 0, index -> {
				if(index < 0 || index >= stockIds.size()) return;
				SmartFill.toggle(plan, selected, stockIds.get(index));
				refresh();
			});
			addWidget(list);
		}
		refresh();
	}

	@Override
	protected boolean hasBottomBar() {
		return false; // the panel uses the full height
	}

	@Override
	public void renderBackground() {
		int w = wrapper.getWidth(), h = wrapper.getHeight();
		fillRect(panelX1 - 1, panelTop - 1, w - panelX1 + 1, h - panelMarginBottom + 1, 0xFF808080);
		fillRect(panelX1, panelTop, w - panelX1, h - panelMarginBottom, 0xE0000000);
	}

	@Override
	public void render() {
		super.render();
		int w = wrapper.getWidth(), h = wrapper.getHeight();
		drawShadowCentred(new Translatable("screen.buildguide.smartfill.title").toString(), w / 2, panelTop + 4, 0xFFFFFF);
		if(message != null) {
			drawShadowCentred(new Translatable(message).toString(), w / 2, (panelTop + h) / 2 - 4, 0xFFCC66);
			return;
		}
		drawShadowLeft(new Translatable("screen.buildguide.smartfill.colours", "" + plan.cells.size()).toString(), swatchX, groupsTop - 12, 0xAAAAAA);
		drawShadowLeft(new Translatable("screen.buildguide.smartfill.carried").toString(), listX1, listTop - 12, 0xAAAAAA);
		for(int i = 0;i < plan.groups.size();++i) {
			int y = groupsTop + i * groupRowHeight;
			fillRect(swatchX - 1, y + 1, swatchX + 13, y + 15, i == selected ? 0xFFFFFFFF : 0xFF404040);
			fillRect(swatchX, y + 2, swatchX + 12, y + 14, 0xFF000000 | plan.groups.get(i).rgb);
		}
		if(stockIds.isEmpty()) drawShadowLeft(new Translatable("screen.buildguide.smartfill.nocarried").toString(), listX1, listTop + 4, 0xFFCC66);
		// Under the colours: the selected one's choice and whether what is carried covers it
		int summaryY = groupsTop + plan.groups.size() * groupRowHeight + 4;
		Group group = plan.groups.get(selected);
		int[] covered = SmartFillPlan.coverage(plan.groups, stock);
		String choice = group.blocks.isEmpty() ? new Translatable("screen.buildguide.smartfill.none").toString() : String.join(", ", namesOf(group.blocks));
		drawShadowLeft(fit(choice, listX1 - swatchX - 6), swatchX, summaryY, 0xFFFFFF);
		if(!group.blocks.isEmpty()) {
			boolean enough = covered[selected] >= group.cells.size();
			drawShadowLeft(new Translatable("screen.buildguide.smartfill.enough", "" + covered[selected], "" + group.cells.size()).toString(), swatchX, summaryY + 10, enough ? 0x80FF80 : 0xFF8080);
		}
		// Between Clear and Back: where the blocks come from
		int hintX = swatchX + bottomButtonWidth + 6, backX = w - panelX1 - 2 * bottomButtonWidth - 6;
		drawShadowLeft(fit(new Translatable("screen.buildguide.smartfill.hint").toString(), backX - hintX - 4), hintX, h - bottomRowFromBottom + 6, 0x888888);
	}

	private void refresh() {
		for(int i = 0;i < groupButtons.length;++i) groupButtons[i].setTitle(new Translatable(groupTitle(i)));
		if(list != null) list.setEntries(rows());
		if(buttonStart != null) {
			boolean any = false;
			for(Group g: plan.groups) any |= !g.blocks.isEmpty();
			buttonStart.setActive(any);
		}
	}

	// "> 1234: Stone", "1234: Mix of 3", "1234: none"
	private String groupTitle(int i) {
		Group g = plan.groups.get(i);
		String what = g.blocks.isEmpty() ? new Translatable("screen.buildguide.smartfill.none").toString() : g.blocks.size() == 1 ? name(g.blocks.get(0)) : new Translatable("screen.buildguide.smartfill.mix", "" + g.blocks.size()).toString();
		return fit((i == selected ? "> " : "") + g.cells.size() + ": " + what, groupWidth - 8);
	}

	// "[x] Stone  128": marked when the selected colour uses it
	private List<Translatable> rows() {
		List<Translatable> out = new ArrayList<Translatable>();
		List<String> used = plan.groups.get(selected).blocks;
		for(String id: stockIds) out.add(new Translatable((used.contains(id) ? "[x] " : "[  ] ") + name(id) + "  " + stock.get(id)));
		return out;
	}

	private List<String> namesOf(List<String> ids) {
		List<String> out = new ArrayList<String>();
		for(String id: ids) out.add(name(id));
		return out;
	}

	private String name(String id) {
		return names.computeIfAbsent(id, BuildGuide.shapeHandler::blockName);
	}

	@Override
	public boolean onEscape() {
		back();
		return true;
	}

	private void back() {
		BuildGuide.screenHandler.showScreen(parent);
	}
}
