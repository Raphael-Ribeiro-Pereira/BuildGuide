package brentmaas.buildguide.common.shape;

/**
 * Read-only view of the world that StateReconciler needs, in world coordinates. Implemented by the
 * loader (it knows Level, BlockPos and BlockState); common only sees ints.
 */
public interface IBlockProbe {
	public static final int FLAG_AIR = 1;
	// Not air and stops movement (what the scan counts as a solid block)
	public static final int FLAG_SOLID = 2;
	// Not air and an ignored block type (Configuration screen)
	public static final int FLAG_IGNORED = 4;
	// The block can be replaced by placing another one into it (air, water, tall grass): Block.canBeReplaced()
	public static final int FLAG_REPLACEABLE = 8;

	// The chunk holding this position is loaded. An unloaded chunk reads as air, which is not evidence
	public boolean isLoaded(int x, int y, int z);

	public int flags(int x, int y, int z);

	// Display name of the block, only asked for when a status is set to IGNORED
	public String name(int x, int y, int z);
}
