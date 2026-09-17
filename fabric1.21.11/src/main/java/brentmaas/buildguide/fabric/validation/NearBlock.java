package brentmaas.buildguide.fabric.validation;

// A solid block close to the shape that is not part of it
public class NearBlock {
	public final int x, y, z;
	public final String blockName;
	public final float distance;
	
	public NearBlock(int x, int y, int z, String blockName, float distance) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.blockName = blockName;
		this.distance = distance;
	}
}
