package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Fill with the block in hand: the guideline cells the world shows that are still empty, and the /fill commands
 * that place a block in exactly those. Pure (the world comes in through IBlockProbe), so the harness checks it;
 * FillTask sends the commands.
 *
 * Cells: those Area 3 could target (GuidelinePicker.qualifies: expected, in the world buffer's focus, outside the
 * exclusion boxes, in a loaded chunk, empty, meaning air or a replaceable block such as water or grass, and not
 * inside the player), inside the build height. Boxes: per Y layer, greedy rectangles (a run along X grown along
 * Z), then a box grows up through the layers while the same rectangle repeats, never more than maxVolume blocks
 * (the server's default limit for /fill). The command replaces only #minecraft:replaceable, so a block placed
 * meanwhile is never overwritten.
 */
public final class FillPlan {
	public static final int maxVolume = 32768;
	public static final String filter = "#minecraft:replaceable";

	private FillPlan() {}

	// The local positions to fill, of a shape at origin (ox, oy, oz); playerBox in world coordinates (null: none)
	public static List<Long> cells(Set<Long> expected, int ox, int oy, int oz, List<int[]> exclusionBoxes, LongPredicate inFocus, IBlockProbe probe, double[] playerBox) {
		GuidelinePicker.Cells shown = GuidelinePicker.shapeCells(expected, ox, oy, oz, exclusionBoxes, inFocus);
		List<Long> out = new ArrayList<Long>();
		for(long local: expected) {
			int x = ox + LocalPos.unpackX(local), y = oy + LocalPos.unpackY(local), z = oz + LocalPos.unpackZ(local);
			if(probe.isInBuildHeight(y) && GuidelinePicker.qualifies(x, y, z, playerBox, shown, probe)) out.add(local);
		}
		return out;
	}

	/**
	 * Boxes {x1, y1, z1, x2, y2, z2} (inclusive, local) that cover exactly the given cells, each at most maxVolume
	 * blocks. In ascending Y, then Z, then X.
	 */
	public static List<int[]> boxes(List<Long> cells, int maxVolume) {
		List<long[]> sorted = new ArrayList<long[]>(cells.size());
		for(long c: cells) sorted.add(new long[] {LocalPos.unpackY(c), LocalPos.unpackZ(c), LocalPos.unpackX(c)});
		Collections.sort(sorted, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : a[1] != b[1] ? Long.compare(a[1], b[1]) : Long.compare(a[2], b[2]));
		List<int[]> out = new ArrayList<int[]>();
		// Boxes that reached the previous layer, by their rectangle: the next layer may extend them
		Map<String, int[]> open = new HashMap<String, int[]>();
		int i = 0;
		while(i < sorted.size()) {
			int y = (int) sorted.get(i)[0];
			int j = i;
			while(j < sorted.size() && sorted.get(j)[0] == y) ++j;
			Map<String, int[]> next = new HashMap<String, int[]>();
			for(int[] r: rectangles(sorted.subList(i, j), maxVolume)) {
				String key = r[0] + "," + r[1] + "," + r[2] + "," + r[3];
				int[] box = open.get(key);
				long area = (long) (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
				if(box != null && box[4] == y - 1 && area * (y - box[1] + 1) <= maxVolume) {
					box[4] = y;
				}else {
					box = new int[] {r[0], y, r[1], r[2], y, r[3]};
					out.add(box);
				}
				next.put(key, box);
			}
			open = next;
			i = j;
		}
		return out;
	}

	// Greedy rectangles {x1, z1, x2, z2} over one layer's cells (sorted by Z then X: {y, z, x})
	private static List<int[]> rectangles(List<long[]> layer, int maxVolume) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for(long[] c: layer) {
			minX = Math.min(minX, (int) c[2]);
			maxX = Math.max(maxX, (int) c[2]);
			minZ = Math.min(minZ, (int) c[1]);
			maxZ = Math.max(maxZ, (int) c[1]);
		}
		int w = maxX - minX + 1, d = maxZ - minZ + 1;
		boolean[] free = new boolean[w * d];
		for(long[] c: layer) free[(int) (c[2] - minX) + (int) (c[1] - minZ) * w] = true;
		List<int[]> out = new ArrayList<int[]>();
		for(int z = 0;z < d;++z) {
			for(int x = 0;x < w;++x) {
				if(!free[x + z * w]) continue;
				int width = 1;
				while(x + width < w && free[x + width + z * w] && width + 1 <= maxVolume) ++width;
				int depth = 1;
				grow:
				while(z + depth < d && (long) width * (depth + 1) <= maxVolume) {
					for(int k = 0;k < width;++k) if(!free[x + k + (z + depth) * w]) break grow;
					++depth;
				}
				for(int dz = 0;dz < depth;++dz) for(int k = 0;k < width;++k) free[x + k + (z + dz) * w] = false;
				out.add(new int[] {minX + x, minZ + z, minX + x + width - 1, minZ + z + depth - 1});
			}
		}
		return out;
	}

	// The command for one box of a shape at origin (ox, oy, oz), without the slash
	public static String command(int[] box, int ox, int oy, int oz, String blockId) {
		return "fill " + (ox + box[0]) + " " + (oy + box[1]) + " " + (oz + box[2]) + " " + (ox + box[3]) + " " + (oy + box[4]) + " " + (oz + box[5]) + " " + blockId + " replace " + filter;
	}

	public static long volume(int[] box) {
		return (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
	}
}
