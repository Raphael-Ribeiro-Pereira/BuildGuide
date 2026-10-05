package brentmaas.buildguide.common.shape;

import java.util.List;
import java.util.Set;

/**
 * Area 3: which guideline cell a right click should fill. A voxel walk (Amanatides-Woo DDA) along
 * the view ray visits the cells in the order the ray enters them; the target is the first one that
 *  - belongs to the guideline (an expected position, outside every exclusion box),
 *  - is empty in the world: air or a replaceable block (water, tall grass), in a loaded chunk,
 *  - does not intersect the player's box (the placement would be obstructed and eat the click),
 *  - is entered strictly before the real hit (block or entity) and before the reach.
 * A tie with the real hit is the cell in front of the face being aimed at: vanilla fills the same
 * cell, so it is left to vanilla. The walk stops at the limit, so the cost depends on the ray
 * length, never on the size of the structure.
 *
 * Several shape sets are walked one by one and the nearest target wins (pick returns the entry
 * distance). Only primitives and java.*: no net.minecraft.
 */
public final class GuidelinePicker {
	// Ties with the real hit are decided in vanilla's favour within this margin
	public static final double tieEpsilon = 1e-6;

	public static final class Target {
		public final int x, y, z;
		// Distance from the eye to where the ray enters the cell
		public final double distance;

		public Target(int x, int y, int z, double distance) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.distance = distance;
		}
	}

	// The guideline of one shape set, in world coordinates
	public interface Cells {
		public boolean isTarget(int x, int y, int z);
	}

	// Expected positions (local) of a shape at the set's current origin, minus the exclusion boxes
	// (local, inclusive, corners sorted: ShapeSet.getActiveExclusionBoxes)
	public static Cells shapeCells(final Set<Long> expected, final int ox, final int oy, final int oz, final List<int[]> exclusionBoxes) {
		return (x, y, z) -> {
			int lx = x - ox, ly = y - oy, lz = z - oz;
			if(!expected.contains(LocalPos.pack(lx, ly, lz))) return false;
			for(int[] b: exclusionBoxes) {
				if(lx >= b[0] && lx <= b[3] && ly >= b[1] && ly <= b[4] && lz >= b[2] && lz <= b[5]) return false;
			}
			return true;
		};
	}

	private GuidelinePicker() {}

	/**
	 * eye (ex, ey, ez), direction (dx, dy, dz) need not be normalised; reach and realHit are distances
	 * from the eye (realHit = Double.POSITIVE_INFINITY when the ray hits nothing); player box
	 * {minX, minY, minZ, maxX, maxY, maxZ}. Returns null when no cell qualifies.
	 */
	public static Target pick(double ex, double ey, double ez, double dx, double dy, double dz, double reach, double realHit, double[] playerBox, Cells cells, IBlockProbe probe) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if(len == 0 || Double.isNaN(len)) return null;
		dx /= len;
		dy /= len;
		dz /= len;
		double limit = Math.min(reach, realHit - tieEpsilon);
		if(!(limit > 0)) return null;

		int x = (int) Math.floor(ex), y = (int) Math.floor(ey), z = (int) Math.floor(ez);
		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0, stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0, stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		// Distance along the ray to the next boundary on each axis, and between boundaries
		double tMaxX = boundary(ex, x, dx), tMaxY = boundary(ey, y, dy), tMaxZ = boundary(ez, z, dz);
		double tDeltaX = dx != 0 ? 1.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
		double tDeltaY = dy != 0 ? 1.0 / Math.abs(dy) : Double.POSITIVE_INFINITY;
		double tDeltaZ = dz != 0 ? 1.0 / Math.abs(dz) : Double.POSITIVE_INFINITY;

		double entry = 0; // the eye's own cell is entered at distance 0
		int guard = 3 * ((int) Math.ceil(limit) + 2);
		while(entry < limit && guard-- > 0) {
			if(qualifies(x, y, z, playerBox, cells, probe)) return new Target(x, y, z, entry);
			if(tMaxX <= tMaxY && tMaxX <= tMaxZ) {
				entry = tMaxX;
				tMaxX += tDeltaX;
				x += stepX;
			}else if(tMaxY <= tMaxZ) {
				entry = tMaxY;
				tMaxY += tDeltaY;
				y += stepY;
			}else {
				entry = tMaxZ;
				tMaxZ += tDeltaZ;
				z += stepZ;
			}
		}
		return null;
	}

	private static double boundary(double origin, int cell, double d) {
		if(d > 0) return (cell + 1 - origin) / d;
		if(d < 0) return (cell - origin) / d;
		return Double.POSITIVE_INFINITY;
	}

	private static boolean qualifies(int x, int y, int z, double[] box, Cells cells, IBlockProbe probe) {
		if(box != null && x < box[3] && x + 1 > box[0] && y < box[4] && y + 1 > box[1] && z < box[5] && z + 1 > box[2]) return false;
		if(!cells.isTarget(x, y, z)) return false;
		if(!probe.isLoaded(x, y, z)) return false;
		int f = probe.flags(x, y, z);
		return (f & (IBlockProbe.FLAG_AIR | IBlockProbe.FLAG_REPLACEABLE)) != 0;
	}
}
