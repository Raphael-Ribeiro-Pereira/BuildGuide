package brentmaas.buildguide.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brentmaas.buildguide.common.shape.FillPlan;
import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.SmartFillPlan;
import brentmaas.buildguide.common.shape.SmartFillPlan.Group;

/**
 * Smart fill (2026-10-22, in place of the /fill of the block in hand): the Shape tab's Fill button opens
 * FillScreen on a Plan, what the world shows of the shape (the Part and Layer buttons) and is still empty, in
 * groups by the colour the world draws it with. The player gives each group a block or a mix of blocks they
 * carry, and start() places them, one batch per client tick, through the loader (IShapeHandler.placeBlocks),
 * which takes each block out of the inventory, a shulker box or a backpack, as in survival. Only in a world this
 * game runs (singleplayer). One fill at a time; client thread only, except the loader's answer, which comes from
 * the server thread and is read on the next tick.
 */
public final class SmartFill {
	public static final int blocksPerTick = 32;
	// What happened at a cell (IShapeHandler.placeBlocks): placed; none of its blocks left; taken meanwhile, or
	// the block cannot stand there; its chunk not loaded
	public static final byte PLACED = 0, NO_BLOCKS = 1, TAKEN = 2, UNLOADED = 3;

	public static final class Plan {
		public final ShapeSet set;
		// The shape's origin when planned: the cells are local to it
		public final int originX, originY, originZ;
		// In build order, and the group of each
		public final List<Long> cells;
		public final int[] groupOf;
		public final List<Group> groups;
		// Why nothing can be filled (a translation key), null when the plan can start
		public final String message;

		private Plan(ShapeSet set, List<Long> cells, int[] groupOf, List<Group> groups, String message) {
			this.set = set;
			originX = set == null ? 0 : set.getOriginX();
			originY = set == null ? 0 : set.getOriginY();
			originZ = set == null ? 0 : set.getOriginZ();
			this.cells = cells;
			this.groupOf = groupOf;
			this.groups = groups;
			this.message = message;
		}

		static Plan message(String key) {
			return new Plan(null, new ArrayList<Long>(), new int[0], new ArrayList<Group>(), key);
		}
	}

	private static final class Cell {
		final int x, y, z, group;
		final String[] candidates;

		Cell(int x, int y, int z, int group, String[] candidates) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.group = group;
			this.candidates = candidates;
		}
	}

	private static final class Answer {
		final long ticket;
		final byte[] codes;

		Answer(long ticket, byte[] codes) {
			this.ticket = ticket;
			this.codes = codes;
		}
	}

	// The blocks chosen for a colour, kept while the game runs: the next fill of that colour starts with them
	private static final Map<Integer, List<String>> chosen = new HashMap<Integer, List<String>>();

	private static final ArrayDeque<Cell> queue = new ArrayDeque<Cell>();
	private static boolean running = false;
	private static boolean[] groupOut = new boolean[0];
	private static int cells = 0, placed = 0, taken = 0, unloaded = 0, noBlocks = 0;
	// The batch sent and not answered yet; the ticket tells its answer from one a stopped fill left behind
	private static List<Cell> batch = null;
	private static long ticket = 0;
	private static volatile Answer answer = null;

	private SmartFill() {}

	// The Fill button's title: "Fill", or "Stop (45%)" while one runs
	public static String title() {
		if(running) return BuildGuide.screenHandler.translate("screen.buildguide.smartfill.stop", percent() + "%");
		return BuildGuide.screenHandler.translate("screen.buildguide.fill");
	}

	public static boolean isRunning() {
		return running;
	}

	public static int percent() {
		return cells == 0 ? 0 : (int) (100L * (placed + taken + unloaded + noBlocks) / cells);
	}

	// What there is to fill; a Plan with a message when nothing can be (another kind of world, generating, nothing empty)
	public static Plan prepare(ShapeSet set) {
		if(set == null || set.getShape() == null) return Plan.message("screen.buildguide.fill.nothing");
		if(!BuildGuide.shapeHandler.canSmartFill()) return Plan.message("screen.buildguide.smartfill.singleplayer");
		IBlockProbe probe = BuildGuide.shapeHandler.worldProbe();
		if(probe == null) return Plan.message("screen.buildguide.smartfill.singleplayer");
		final Shape shape = set.getShape();
		if(!shape.lock.tryLock()) return Plan.message("screen.buildguide.fill.generating");
		List<Long> found;
		int[] groupOf;
		List<Group> groups;
		try {
			if(!shape.ready || shape.error) return Plan.message("screen.buildguide.fill.generating");
			found = FillPlan.cells(shape.getExpectedBlocks(), set.getOriginX(), set.getOriginY(), set.getOriginZ(), set.getActiveExclusionBoxes(), shape.worldFocusTest(), probe, BuildGuide.shapeHandler.playerBox());
			if(found.isEmpty()) return Plan.message("screen.buildguide.fill.nothing");
			SmartFillPlan.sortForBuilding(found, set.getOriginX(), set.getOriginY(), set.getOriginZ());
			groupOf = new int[found.size()];
			groups = SmartFillPlan.groups(found, pos -> shape.cubeRgb(LocalPos.unpackX(pos), LocalPos.unpackY(pos), LocalPos.unpackZ(pos)), groupOf);
		}finally {
			shape.lock.unlock();
		}
		for(Group group: groups) {
			List<String> before = chosen.get(group.rgb);
			if(before != null) group.blocks.addAll(before);
		}
		return new Plan(set, found, groupOf, groups, null);
	}

	// With what the player carries known: blocks they no longer carry leave the choice, and a group left with none
	// takes the block in hand when they carry it
	public static void fitToStock(Plan plan, Map<String, Integer> stock, String held) {
		for(Group group: plan.groups) {
			group.blocks.removeIf(block -> stock.getOrDefault(block, 0) <= 0);
			if(group.blocks.isEmpty() && held != null && stock.getOrDefault(held, 0) > 0) group.blocks.add(held);
		}
	}

	// A block in or out of a group's choice (a second block makes a mix); remembered for its colour
	public static void toggle(Plan plan, int group, String block) {
		Group g = plan.groups.get(group);
		if(!g.blocks.remove(block)) g.blocks.add(block);
		chosen.put(g.rgb, new ArrayList<String>(g.blocks));
	}

	public static void clear(Plan plan, int group) {
		Group g = plan.groups.get(group);
		g.blocks.clear();
		chosen.put(g.rgb, new ArrayList<String>());
	}

	// Starts the plan: the cells of the groups that have blocks, in build order. False when nothing would be placed
	public static boolean start(Plan plan) {
		if(running || plan == null || plan.message != null) return false;
		queue.clear();
		for(int i = 0;i < plan.cells.size();++i) {
			int g = plan.groupOf[i];
			List<String> blocks = plan.groups.get(g).blocks;
			if(blocks.isEmpty()) continue;
			long pos = plan.cells.get(i);
			queue.add(new Cell(plan.originX + LocalPos.unpackX(pos), plan.originY + LocalPos.unpackY(pos), plan.originZ + LocalPos.unpackZ(pos), g, SmartFillPlan.candidates(pos, blocks)));
		}
		if(queue.isEmpty()) {
			chat("screen.buildguide.smartfill.noblocks");
			return false;
		}
		groupOut = new boolean[plan.groups.size()];
		cells = queue.size();
		placed = 0;
		taken = 0;
		unloaded = 0;
		noBlocks = 0;
		batch = null;
		answer = null;
		++ticket;
		running = true;
		chat("screen.buildguide.smartfill.start", "" + cells);
		return true;
	}

	// Every client tick (the loader): the answer to the last batch, then the next batch
	public static void tick() {
		if(!running) return;
		if(batch != null) {
			Answer a = answer;
			if(a == null || a.ticket != ticket) return;
			answer = null;
			count(batch, a.codes);
			batch = null;
		}
		List<Cell> next = new ArrayList<Cell>();
		while(next.size() < blocksPerTick && !queue.isEmpty()) {
			Cell cell = queue.poll();
			if(groupOut[cell.group]) ++noBlocks; // that group ran out of every block it had
			else next.add(cell);
		}
		if(next.isEmpty()) {
			finish();
			return;
		}
		int[] xyz = new int[3 * next.size()];
		String[][] candidates = new String[next.size()][];
		for(int i = 0;i < next.size();++i) {
			Cell cell = next.get(i);
			xyz[3 * i] = cell.x;
			xyz[3 * i + 1] = cell.y;
			xyz[3 * i + 2] = cell.z;
			candidates[i] = cell.candidates;
		}
		batch = next;
		final long mine = ticket;
		if(!BuildGuide.shapeHandler.placeBlocks(xyz, candidates, codes -> answer = new Answer(mine, codes))) stop();
	}

	private static void count(List<Cell> sent, byte[] codes) {
		for(int i = 0;i < sent.size();++i) {
			byte code = i < codes.length ? codes[i] : TAKEN;
			if(code == PLACED) {
				++placed;
			}else if(code == NO_BLOCKS) {
				++noBlocks;
				groupOut[sent.get(i).group] = true;
			}else if(code == UNLOADED) {
				++unloaded;
			}else {
				++taken;
			}
		}
	}

	public static void stop() {
		if(!running) return;
		running = false;
		queue.clear();
		batch = null;
		chat("screen.buildguide.smartfill.stopped", "" + placed, "" + cells);
	}

	private static void finish() {
		running = false;
		chat("screen.buildguide.smartfill.done", "" + placed, "" + cells, "" + taken, "" + unloaded, "" + noBlocks);
	}

	private static void chat(String key, Object... values) {
		BuildGuide.logHandler.sendChatMessage("[Build Guide] " + BuildGuide.screenHandler.translate(key, values));
	}

	// Offline harness only
	static void reset() {
		queue.clear();
		running = false;
		batch = null;
		answer = null;
		chosen.clear();
	}
}
