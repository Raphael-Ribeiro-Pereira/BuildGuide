import java.util.*;
public class Step4Test {
	static void under(String label, int[][] pts, int width, int pw, String shape) throws Exception {
		BridgeTest.Cfg c = new BridgeTest.Cfg(pts); c.width=width; c.pillarMode="ON"; c.pillarShape=shape; c.pillarWidth=pw; c.pillarDepth=1; c.pillarSpacing=8;
		BridgeTest.Buf b = BridgeTest.run(label, c);
		int total=0, under=0;
		for(String s: b.blocks){ String[] q=s.split(","); int x=Integer.parseInt(q[0]), y=Integer.parseInt(q[1]), z=Integer.parseInt(q[2]); if(y!=-1) continue; total++; if(b.has(x,0,z)) under++; }
		System.out.printf("pillar blocks under deck: %d/%d %s%n", under, total, under==total?"OK":"FAIL");
		BridgeTest.map(b,-1,true);
	}
	static void hollow(String label, int rw, int rh) throws Exception {
		BridgeTest.Cfg c = new BridgeTest.Cfg(new int[][]{{0,0,0},{6,0,0}}); c.width=9; c.railMode="CONTINUOUS"; c.railProfile="ROUND"; c.railWidth=rw; c.railHeight=rh; c.railElevation=1;
		BridgeTest.Buf b = BridgeTest.run(label, c); BridgeTest.cross(b,3);
		int railBlocks=0; for(String s: b.blocks) if(Integer.parseInt(s.split(",")[1])>=1) railBlocks++;
		System.out.println("rail blocks per side per section: "+(railBlocks/2/7));
	}
	public static void main(String[] a) throws Exception {
		under("+x, deck 5, Square 5", new int[][]{{0,0,0},{20,0,0}}, 5, 5, "SQUARE");
		under("+z, deck 5, Square 5", new int[][]{{0,0,0},{0,0,20}}, 5, 5, "SQUARE");
		under("-x, deck 4, Square 4", new int[][]{{20,0,0},{0,0,0}}, 4, 4, "SQUARE");
		under("diagonal, deck 5, Square 5", new int[][]{{0,0,0},{20,0,13}}, 5, 5, "SQUARE");
		under("+x, deck 5, Taper 5 (top row)", new int[][]{{0,0,0},{20,0,0}}, 5, 5, "TAPER");
		under("short (len 2), deck 5, Square 5", new int[][]{{0,0,0},{2,0,0}}, 5, 5, "SQUARE");
		hollow("Round 3x3 hollow", 3, 3);
		hollow("Round 5x3 hollow", 5, 3);
		hollow("Round 2x2 (no inner)", 2, 2);
		hollow("Round 1x3 (no inner)", 1, 3);
		hollow("Round 6x4 hollow", 6, 4);
		// regression numbers from Steps 2/3
		BridgeTest.Cfg c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0},{20,0,20}}); c.width=9; c.railMode="CONTINUOUS"; BridgeTest.run("regression: L w9 continuous square rails (Step 2: 466)", c);
		c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0}}); c.width=5; c.railMode="BOTH"; c.railElevation=2; BridgeTest.run("regression: Both elevation 2 (Step 2: 159)", c);
		c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0}}); c.width=9; BridgeTest.run("regression: deck only L? no - straight w9 (Step1 style)", c);
	}
}
