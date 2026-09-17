package brentmaas.buildguide.common.shape;

/**
 * Receives one block position at a time in the shape's local coordinates. Shapes expose
 * their geometry as static enumerate(...) methods that feed one of these, so the same
 * geometry can go into a shape's own buffer or be composed (offset, clipped) by another
 * shape. See BlockOps for decorators.
 */
@FunctionalInterface
public interface IBlockConsumer {
	public void accept(int x, int y, int z) throws InterruptedException;
}
