package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

/**
 * Smart fill (2026-10-22): what the world shows of a shape, filled with blocks the player carries. Pure, so the
 * harness checks it; SmartFill runs it and the loader takes the blocks and places them.
 *
 * The cells (FillPlan.cells: shown, not excluded, loaded, empty) go in build order, bottom up, and in
 * groups by the colour the world draws them with (Shape.cubeRgb: the layers, the relief, the farm's water...).
 * Each group gets one block or a mix of several: a cell tries the group's blocks from a start its position
 * picks, so a mix spreads evenly over the cells and a cell always asks for the same block first, and takes
 * the first one the player still has.
 */
public final class SmartFillPlan {
	// More colours than this: the last group takes the rest (the screen has room for this many)
	public static final int maxGroups = 8;

	public static final class Group {
		public final int rgb;
		public final List<Long> cells = new ArrayList<Long>();
		// The blocks chosen for it: one fills it with that block, several mix them; none leaves it empty
		public final List<String> blocks = new ArrayList<String>();

		Group(int rgb) {
			this.rgb = rgb;
		}
	}

	private SmartFillPlan() {}

	/**
	 * Section by section (16 x 16 x 16 in world coordinates, the shape at origin ox, oy, oz), the bottom band of
	 * sections first, then by z and x; in a section, the bottom layer first, then by z and x. The client rebuilds a
	 * section's mesh after its blocks change, so a section filled at once is rebuilt about once (as the /fill boxes
	 * were sent, performance step 2), and nothing is placed above a cell of its column that is still empty.
	 */
	public static void sortForBuilding(List<Long> cells, int ox, int oy, int oz) {
		Collections.sort(cells, (a, b) -> {
			int sa = Long.compare(sectionKey(a, ox, oy, oz), sectionKey(b, ox, oy, oz));
			if(sa != 0) return sa;
			int ya = LocalPos.unpackY(a), yb = LocalPos.unpackY(b);
			if(ya != yb) return Integer.compare(ya, yb);
			int za = LocalPos.unpackZ(a), zb = LocalPos.unpackZ(b);
			if(za != zb) return Integer.compare(za, zb);
			return Integer.compare(LocalPos.unpackX(a), LocalPos.unpackX(b));
		});
	}

	// Sorts as section Y, then Z, then X (each shifted to be non-negative, 21 bits)
	private static long sectionKey(long local, int ox, int oy, int oz) {
		long sx = Math.floorDiv(ox + LocalPos.unpackX(local), 16) + 0x100000, sy = Math.floorDiv(oy + LocalPos.unpackY(local), 16) + 0x100000, sz = Math.floorDiv(oz + LocalPos.unpackZ(local), 16) + 0x100000;
		return (sy << 42) | (sz << 21) | sx;
	}

	// The cells by colour, the groups in the order their colours first come; cells keep their order in each.
	// groupOf (null: not wanted), parallel to cells, gets each cell's group
	public static List<Group> groups(List<Long> cells, LongToIntFunction rgbOf, int[] groupOf) {
		Map<Integer, Group> byColour = new LinkedHashMap<Integer, Group>();
		List<Group> out = new ArrayList<Group>();
		for(int i = 0;i < cells.size();++i) {
			long cell = cells.get(i);
			int rgb = rgbOf.applyAsInt(cell);
			Group group = byColour.get(rgb);
			if(group == null) {
				if(out.size() < maxGroups) {
					group = new Group(rgb);
					out.add(group);
				}else {
					group = out.get(maxGroups - 1);
				}
				byColour.put(rgb, group);
			}
			group.cells.add(cell);
			if(groupOf != null) groupOf[i] = out.indexOf(group);
		}
		return out;
	}

	// The blocks a cell tries, in order: the group's blocks from a start its position picks
	public static String[] candidates(long pos, List<String> blocks) {
		int n = blocks.size();
		String[] out = new String[n];
		if(n == 0) return out;
		int start = start(pos, n);
		for(int i = 0;i < n;++i) out[i] = blocks.get((start + i) % n);
		return out;
	}

	// A position's start among n blocks: a mix of its bits, so neighbours do not follow a pattern
	public static int start(long pos, int n) {
		long z = pos * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 31)) * 0xBF58476D1CE4E5B9L;
		z ^= z >>> 29;
		return (int) Long.remainderUnsigned(z, n);
	}

	// The first candidate the player has (has: whether one of that block can be taken), null when none
	public static String firstAvailable(String[] candidates, Predicate<String> has) {
		for(String block: candidates) {
			if(has.test(block)) return block;
		}
		return null;
	}

	/**
	 * How many cells of each group the stock covers, the groups taking from it in order, so a block two groups
	 * share is counted once. A group without blocks covers nothing.
	 */
	public static int[] coverage(List<Group> groups, Map<String, Integer> stock) {
		Map<String, Integer> left = new HashMap<String, Integer>(stock);
		int[] out = new int[groups.size()];
		for(int g = 0;g < groups.size();++g) {
			Group group = groups.get(g);
			int need = group.cells.size();
			for(String block: group.blocks) {
				if(need == 0) break;
				int have = left.getOrDefault(block, 0);
				int use = Math.min(have, need);
				left.put(block, have - use);
				need -= use;
				out[g] += use;
			}
		}
		return out;
	}
}
