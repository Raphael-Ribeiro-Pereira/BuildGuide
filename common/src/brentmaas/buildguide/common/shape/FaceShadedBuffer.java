package brentmaas.buildguide.common.shape;

/**
 * IShapeBuffer decorator that darkens each face of CubeMesh cubes by its direction, like the
 * vanilla block shading, so a POSITION_COLOR mesh (no normals, no lighting) still reads as 3D.
 * Relies on CubeMesh's fixed order: 24 vertices per cube, faces -X, -Y, -Z, +X, +Y, +Z of 4
 * vertices each. Only push CubeMesh cubes through it.
 */
public class FaceShadedBuffer implements IShapeBuffer {
	// Per face in CubeMesh order; vanilla factors: up 1.0, north/south 0.8, east/west 0.6, down 0.5
	static final float[] shade = {0.6f, 0.5f, 0.8f, 0.6f, 1.0f, 0.8f};

	private final IShapeBuffer inner;
	private int r = 255, g = 255, b = 255, a = 255;
	private long vertices = 0;

	public FaceShadedBuffer(IShapeBuffer inner) {
		this.inner = inner;
	}

	public void setColour(int r, int g, int b, int a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	public void pushVertex(double x, double y, double z) {
		if(vertices % 4 == 0) {
			float f = shade[(int) ((vertices / 4) % 6)];
			inner.setColour(Math.round(r * f), Math.round(g * f), Math.round(b * f), a);
		}
		++vertices;
		inner.pushVertex(x, y, z);
	}

	public void end() {
		inner.end();
	}

	public void close() {
		inner.close();
	}
}
