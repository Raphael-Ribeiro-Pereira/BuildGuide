package brentmaas.buildguide.common.shape;

// The 24-vertex quad mesh of an axis-aligned cube, in the order Shape.addCube has always used.
// Shared by shape generation, the validation overlay and (later) the preview
public class CubeMesh {
	public static void push(IShapeBuffer buffer, double x, double y, double z, double s) {
		//-X
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x, y, z+s);
		buffer.pushVertex(x, y+s, z+s);
		buffer.pushVertex(x, y+s, z);
		
		//-Y
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x+s, y, z);
		buffer.pushVertex(x+s, y, z+s);
		buffer.pushVertex(x, y, z+s);
		
		//-Z
		buffer.pushVertex(x, y, z);
		buffer.pushVertex(x, y+s, z);
		buffer.pushVertex(x+s, y+s, z);
		buffer.pushVertex(x+s, y, z);
		
		//+X
		buffer.pushVertex(x+s, y, z);
		buffer.pushVertex(x+s, y+s, z);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x+s, y, z+s);
		
		//+Y
		buffer.pushVertex(x, y+s, z);
		buffer.pushVertex(x, y+s, z+s);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x+s, y+s, z);
		
		//+Z
		buffer.pushVertex(x, y, z+s);
		buffer.pushVertex(x+s, y, z+s);
		buffer.pushVertex(x+s, y+s, z+s);
		buffer.pushVertex(x, y+s, z+s);
	}
}
