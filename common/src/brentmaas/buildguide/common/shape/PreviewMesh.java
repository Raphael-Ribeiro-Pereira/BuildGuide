package brentmaas.buildguide.common.shape;

/**
 * Geometry of the 3D preview, loader-free: fills an IShapeBuffer with a PreviewModel. Two loops
 * into one buffer (one draw call): the shape's positions coloured by status, then the structure
 * errors in red. An error is never an expected position (the scan skips those), so the two sets
 * never share a cell and their order does not matter. Every cube goes through FaceShadedBuffer.
 */
public class PreviewMesh {
	// Slightly smaller than a block so the grid between blocks stays readable
	public static final double cubeSize = 0.92;

	public static void fill(IShapeBuffer target, PreviewModel model) {
		FaceShadedBuffer shaded = new FaceShadedBuffer(target);
		for(int i = 0;i < model.positions.length;++i) {
			setColour(shaded, model.colourAt(i));
			pushCube(shaded, model.positions[i]);
		}
		setColour(shaded, PreviewColours.ERROR);
		for(long pos: model.errors) pushCube(shaded, pos);
	}

	private static void setColour(IShapeBuffer buffer, int rgb) {
		buffer.setColour((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
	}

	private static void pushCube(IShapeBuffer buffer, long pos) {
		double inset = (1 - cubeSize) / 2;
		CubeMesh.push(buffer, LocalPos.unpackX(pos) + inset, LocalPos.unpackY(pos) + inset, LocalPos.unpackZ(pos) + inset, cubeSize);
	}
}
