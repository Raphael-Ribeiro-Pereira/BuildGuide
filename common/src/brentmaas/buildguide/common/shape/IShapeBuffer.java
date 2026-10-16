package brentmaas.buildguide.common.shape;

public interface IShapeBuffer {
	public void setColour(int r, int g, int b, int a);
	
	public void pushVertex(double x, double y, double z);
	
	public void end();
	
	public void close();
	
	// Frees the vertex data kept on the CPU side (the loader's native builder), not what is on the GPU.
	// Idempotent and callable from any thread, once nothing writes to the buffer any more. Default: nothing kept
	public default void releaseVertexData() {}

	// Sends part of the vertices to the GPU, until the clock reaches the deadline (at least one step), and says
	// whether all of them are sent. Render thread. Default: one step, all of it (end())
	public default boolean endPart(java.util.function.LongSupplier clock, long deadline) {
		end();
		return true;
	}
}
