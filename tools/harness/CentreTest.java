import java.util.*;
public class CentreTest {
	// Compare the set of (x,z) columns occupied by the deck top row with the pillar's top row
	static void check(String label, int[][] pts, int width, int pw, String shape) throws Exception {
		BridgeTest.Cfg c = new BridgeTest.Cfg(pts); c.width=width; c.pillarMode="ON"; c.pillarShape=shape; c.pillarWidth=pw; c.pillarDepth=1; c.pillarSpacing=1000;
		BridgeTest.Buf b = BridgeTest.run(label, c);
		// pillar rows are at y = -1 (thickness 1); deck at y = 0
		int total=0, under=0; double sx=0, sz=0; int n=0;
		for(String s: b.blocks){ String[] q=s.split(","); int x=Integer.parseInt(q[0]), y=Integer.parseInt(q[1]), z=Integer.parseInt(q[2]); if(y!=-1) continue; total++; if(b.has(x,0,z)) under++; sx+=x; sz+=z; n++; }
		System.out.printf("pillar blocks: %d, under deck: %d, pillar centroid=(%.2f, %.2f)%n", total, under, sx/n, sz/n);
		BridgeTest.map(b, 0, true); BridgeTest.map(b, -1, true);
	}
	public static void main(String[] a) throws Exception {
		check("+x straight, deck 5, Square 5", new int[][]{{0,0,0},{10,0,0}}, 5, 5, "SQUARE");
		check("-x straight, deck 5, Square 5", new int[][]{{10,0,0},{0,0,0}}, 5, 5, "SQUARE");
		check("+z straight, deck 5, Square 5", new int[][]{{0,0,0},{0,0,10}}, 5, 5, "SQUARE");
		check("+x straight, deck 4, Square 4", new int[][]{{0,0,0},{10,0,0}}, 4, 4, "SQUARE");
		check("+x straight, deck 5, Round 5", new int[][]{{0,0,0},{10,0,0}}, 5, 5, "ROUND");
		check("+x straight, deck 5, Taper 5", new int[][]{{0,0,0},{10,0,0}}, 5, 5, "TAPER");
		check("+x straight, deck 4, Taper 4", new int[][]{{0,0,0},{10,0,0}}, 4, 4, "TAPER");
		check("diagonal, deck 5, Square 5", new int[][]{{0,0,0},{20,0,13}}, 5, 5, "SQUARE");
	}
}
