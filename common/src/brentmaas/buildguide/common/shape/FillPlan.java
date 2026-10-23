package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Fill: the guideline cells the world shows that are still empty. Pure (the world comes in through IBlockProbe),
 * so the harness checks it; SmartFill places blocks in them (2026-10-22: the /fill commands and their boxes went
 * with the fill of the block in hand).
 *
 * Cells: those Area 3 could target (GuidelinePicker.qualifies: expected, in the world buffer's focus, outside the
 * exclusion boxes, in a loaded chunk, empty, meaning air or a replaceable block such as water or grass, and not
 * inside the player), inside the build height.
 */
public final class FillPlan {
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
}
