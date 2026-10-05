package brentmaas.buildguide.common.shape;

/**
 * Area 3: the mesh around the targeted guideline cell, in the cell's own frame (the cell spans 0..1
 * on each axis; the caller translates to the cell's corner). A cube centred on (0.5, 0.5, 0.5) whose
 * side is the set's shape cube size plus TARGET_GROW (at most a full block), with faces in the error
 * list's highlight white, and its 12 edges drawn as thin opaque black boxes (EDGE_THICKNESS square,
 * side + EDGE_THICKNESS long). Everything goes into one buffer, so one draw call in the overlay's
 * pipeline: no new pipeline, no GL lines. 24 vertices per box, 13 boxes.
 *
 * Only primitives and java.*: no net.minecraft.
 */
public final class TargetOutline {
	public static final double TARGET_GROW = 0.15;
	public static final double EDGE_THICKNESS = 0.03;
	public static final int BOXES = 1 + 12;
	public static final int VERTICES = 24 * BOXES;
	// Opaque black edges
	private static final int edgeR = 0, edgeG = 0, edgeB = 0, edgeA = 255;

	private TargetOutline() {}

	// The outline's side for a set whose guideline cubes are cubeSize wide
	public static double side(double cubeSize) {
		return Math.min(1.0, cubeSize + TARGET_GROW);
	}

	public static void build(IShapeBuffer buffer, double cubeSize) {
		double s = side(cubeSize), lo = 0.5 - s / 2, hi = 0.5 + s / 2, h = EDGE_THICKNESS / 2;
		buffer.setColour(ValidationOverlay.highlightR, ValidationOverlay.highlightG, ValidationOverlay.highlightB, ValidationOverlay.highlightA);
		CubeMesh.push(buffer, lo, lo, lo, s);
		buffer.setColour(edgeR, edgeG, edgeB, edgeA);
		double[] corners = {lo, hi};
		for(double a: corners) {
			for(double b: corners) {
				pushBox(buffer, lo - h, a - h, b - h, hi + h, a + h, b + h); // along X, at y a, z b
				pushBox(buffer, a - h, lo - h, b - h, a + h, hi + h, b + h); // along Y, at x a, z b
				pushBox(buffer, a - h, b - h, lo - h, a + h, b + h, hi + h); // along Z, at x a, y b
			}
		}
	}

	// An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), with CubeMesh.push's face and vertex order
	private static void pushBox(IShapeBuffer buffer, double x0, double y0, double z0, double x1, double y1, double z1) {
		//-X
		buffer.pushVertex(x0, y0, z0);
		buffer.pushVertex(x0, y0, z1);
		buffer.pushVertex(x0, y1, z1);
		buffer.pushVertex(x0, y1, z0);
		//-Y
		buffer.pushVertex(x0, y0, z0);
		buffer.pushVertex(x1, y0, z0);
		buffer.pushVertex(x1, y0, z1);
		buffer.pushVertex(x0, y0, z1);
		//-Z
		buffer.pushVertex(x0, y0, z0);
		buffer.pushVertex(x0, y1, z0);
		buffer.pushVertex(x1, y1, z0);
		buffer.pushVertex(x1, y0, z0);
		//+X
		buffer.pushVertex(x1, y0, z0);
		buffer.pushVertex(x1, y1, z0);
		buffer.pushVertex(x1, y1, z1);
		buffer.pushVertex(x1, y0, z1);
		//+Y
		buffer.pushVertex(x0, y1, z0);
		buffer.pushVertex(x0, y1, z1);
		buffer.pushVertex(x1, y1, z1);
		buffer.pushVertex(x1, y1, z0);
		//+Z
		buffer.pushVertex(x0, y0, z1);
		buffer.pushVertex(x1, y0, z1);
		buffer.pushVertex(x1, y1, z1);
		buffer.pushVertex(x0, y1, z1);
	}
}
