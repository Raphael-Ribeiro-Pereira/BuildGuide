package brentmaas.buildguide.common.shape;

// Decorators over IBlockConsumer for composing shapes
public class BlockOps {
	// Translate every block by (dx, dy, dz) before passing it on
	public static IBlockConsumer offset(int dx, int dy, int dz, IBlockConsumer next) {
		return (x, y, z) -> next.accept(x + dx, y + dy, z + dz);
	}
	
	// Pass on only blocks inside the inclusive box [min, max]
	public static IBlockConsumer clipAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, IBlockConsumer next) {
		return (x, y, z) -> {
			if(x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) next.accept(x, y, z);
		};
	}
	
	// Drop blocks inside the inclusive box [min, max] (exclusion volume)
	public static IBlockConsumer excludeAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, IBlockConsumer next) {
		return (x, y, z) -> {
			if(x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) next.accept(x, y, z);
		};
	}
}
