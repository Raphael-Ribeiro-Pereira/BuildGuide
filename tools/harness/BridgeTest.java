import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.property.*;

/**
 * Offline harness for ShapeBridge. Compile/run against common/build/classes/java/main:
 *   javac -cp <classes> -d bridgetest bridgetest/BridgeTest.java
 *   java  -cp "<classes>;bridgetest" BridgeTest
 * Property indices: 0-14 points, 15 count, 16 step, 17 profile, 18 width, 19 thickness, 20 validate,
 * 21 railMode, 22 railSides, 23 railProfile, 24 railWidth, 25 railHeight, 26 railElevation, 27 railInset, 28 postSpacing
 * 29 pillarMode, 30 pillarShape, 31 pillarWidth, 32 pillarDepth, 33 pillarSpacing, 34 pillarTaper
 */
public class BridgeTest {
	static class Buf implements IShapeBuffer {
		int n = 0; Set<String> blocks = new LinkedHashSet<>(); int minX=99999,maxX=-99999,minZ=99999,maxZ=-99999,minY=99999,maxY=-99999;
		public void setColour(int r,int g,int b,int a) {}
		public void pushVertex(double x,double y,double z) {
			if(n++ % 24 == 0) { int bx=(int)Math.floor(x), by=(int)Math.floor(y), bz=(int)Math.floor(z); blocks.add(bx+","+by+","+bz);
				minX=Math.min(minX,bx);maxX=Math.max(maxX,bx);minZ=Math.min(minZ,bz);maxZ=Math.max(maxZ,bz);minY=Math.min(minY,by);maxY=Math.max(maxY,by);}
		}
		public void end() {} public void close() {}
		boolean has(int x,int y,int z) { return blocks.contains(x+","+y+","+z); }
		int countAtY(int y) { int c=0; for(String s: blocks) if(s.split(",")[1].equals(""+y)) c++; return c; }
	}
	static class Cfg { int[][] pts; int width=3, thickness=1; String profile="FLAT"; float step=1f;
		String railMode="NONE", railSides="BOTH", railProfile="SQUARE"; int railWidth=1, railHeight=1, railElevation=1, railInset=0, postSpacing=4;
		String pillarMode="NONE", pillarShape="SQUARE"; int pillarWidth=3, pillarDepth=10, pillarSpacing=12; float pillarTaper=1f;
		Cfg(int[][] p){pts=p;} }
	static Object unsafe() throws Exception { Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true); return f.get(null); }
	@SuppressWarnings("unchecked")
	static Buf run(String label, Cfg c) throws Exception {
		ShapeBridge b = new ShapeBridge();
		Object u = unsafe();
		Object set = Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class.class).invoke(u, ShapeSet.class);
		Field fs = Shape.class.getDeclaredField("shapeSet"); fs.setAccessible(true); fs.set(b, set);
		List<Property<?>> p = b.properties;
		for(int i=0;i<5;i++) for(int k=0;k<3;k++) ((Property<Integer>)p.get(i*3+k)).value = i < c.pts.length ? c.pts[i][k] : 0;
		((Property<Integer>)p.get(15)).value = c.pts.length;
		((Property<Float>)p.get(16)).value = c.step;
		((Property<ShapeBridge.Profile>)p.get(17)).value = ShapeBridge.Profile.valueOf(c.profile);
		((Property<Integer>)p.get(18)).value = c.width;
		((Property<Integer>)p.get(19)).value = c.thickness;
		((Property<ShapeBridge.RailMode>)p.get(21)).value = ShapeBridge.RailMode.valueOf(c.railMode);
		((Property<ShapeBridge.RailSides>)p.get(22)).value = ShapeBridge.RailSides.valueOf(c.railSides);
		((Property<ShapeBridge.RailProfile>)p.get(23)).value = ShapeBridge.RailProfile.valueOf(c.railProfile);
		((Property<Integer>)p.get(24)).value = c.railWidth;
		((Property<Integer>)p.get(25)).value = c.railHeight;
		((Property<Integer>)p.get(26)).value = c.railElevation;
		((Property<Integer>)p.get(27)).value = c.railInset;
		((Property<Integer>)p.get(28)).value = c.postSpacing;
		((Property<ShapeBridge.PillarMode>)p.get(29)).value = ShapeBridge.PillarMode.valueOf(c.pillarMode);
		((Property<ShapeBridge.PillarShape>)p.get(30)).value = ShapeBridge.PillarShape.valueOf(c.pillarShape);
		((Property<Integer>)p.get(31)).value = c.pillarWidth;
		((Property<Integer>)p.get(32)).value = c.pillarDepth;
		((Property<Integer>)p.get(33)).value = c.pillarSpacing;
		((Property<Float>)p.get(34)).value = c.pillarTaper;
		Method m = ShapeBridge.class.getDeclaredMethod("updateShape", IShapeBuffer.class); m.setAccessible(true);
		Buf buf = new Buf();
		long t0 = System.nanoTime();
		m.invoke(b, buf);
		System.out.printf("%n=== %s: %d blocks, %.1f ms, x[%d..%d] y[%d..%d] z[%d..%d] ===%n", label, buf.blocks.size(), (System.nanoTime()-t0)/1e6, buf.minX,buf.maxX,buf.minY,buf.maxY,buf.minZ,buf.maxZ);
		return buf;
	}
	// Top-down map at height y; counts empty cells enclosed on 4 sides (holes)
	static int map(Buf buf, int y, boolean print) {
		int holes = 0;
		for(int z=buf.minZ;z<=buf.maxZ;z++) { StringBuilder sb=new StringBuilder(String.format("%4d ", z));
			for(int x=buf.minX;x<=buf.maxX;x++) { boolean on = buf.has(x,y,z); sb.append(on?'#':'.');
				if(!on && buf.has(x+1,y,z) && buf.has(x-1,y,z) && buf.has(x,y,z+1) && buf.has(x,y,z-1)) holes++; }
			if(print) System.out.println(sb); }
		System.out.println("y="+y+": "+buf.countAtY(y)+" blocks, enclosed holes: "+holes);
		return holes;
	}
	// Side view along x at a given z column (y up)
	static void side(Buf buf, int z) {
		for(int y=buf.maxY;y>=buf.minY;y--){ StringBuilder sb=new StringBuilder(String.format("y=%3d ", y)); for(int x=buf.minX;x<=buf.maxX;x++) sb.append(buf.has(x,y,z)?'#':'.'); System.out.println(sb); }
	}
	// Cross-section at a given x (z across, y up)
	static void cross(Buf buf, int x) {
		for(int y=buf.maxY;y>=buf.minY;y--){ StringBuilder sb=new StringBuilder(String.format("y=%3d ", y)); for(int z=buf.minZ;z<=buf.maxZ;z++) sb.append(buf.has(x,y,z)?'#':'.'); System.out.println(sb); }
	}
	// Number of 8-connected components at height y (a continuous rail should be 1 per side)
	static int components(Buf buf, int y) {
		Set<String> seen = new HashSet<>(); int comps = 0;
		for(String s: buf.blocks) { String[] q = s.split(","); if(!q[1].equals(""+y) || seen.contains(s)) continue; comps++;
			Deque<int[]> st = new ArrayDeque<>(); st.push(new int[]{Integer.parseInt(q[0]),Integer.parseInt(q[2])}); seen.add(s);
			while(!st.isEmpty()){ int[] c=st.pop(); int[][] nb={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}; for(int[] d: nb){ String k=(c[0]+d[0])+","+y+","+(c[1]+d[1]); if(buf.blocks.contains(k)&&!seen.contains(k)){seen.add(k);st.push(new int[]{c[0]+d[0],c[1]+d[1]});} } } }
		return comps;
	}
	public static void main(String[] a) throws Exception {
		int[][] straight = {{0,0,0},{20,0,0}};
		int[][] L = {{0,0,0},{20,0,0},{20,0,20}};
		int[][] diag = {{0,0,0},{20,0,13}};
		// 1. None == Step 1 numbers
		Cfg c = new Cfg(L); c.width=9; Buf b = run("1. None, L width 9 (Step 1: 378)", c); map(b,0,false);
		c = new Cfg(diag); c.width=5; b = run("1. None, diagonal width 5 (Step 1: 128)", c); map(b,0,false);
		c = new Cfg(new int[][]{{0,0,0},{6,0,0}}); c.thickness=3; b = run("1. None, thickness 3 (Step 1: 63)", c);
		// 2. Continuous both, width 5 straight
		c = new Cfg(straight); c.width=5; c.railMode="CONTINUOUS"; b = run("2. Continuous Both, straight width 5", c); map(b,1,true); System.out.println("rail components at y=1: "+components(b,1)+" (expect 2)");
		// 3. Continuous both on L width 9
		c = new Cfg(L); c.width=9; c.railMode="CONTINUOUS"; b = run("3. Continuous Both, L width 9", c); map(b,0,false); map(b,1,true); System.out.println("rail components at y=1: "+components(b,1)+" (expect 2)");
		// 4. Left / Right on straight (travel +x): left = -z (north), right = +z (south)
		c = new Cfg(straight); c.width=5; c.railMode="CONTINUOUS"; c.railSides="LEFT"; b = run("4. Left only", c); map(b,1,true);
		c = new Cfg(straight); c.width=5; c.railMode="CONTINUOUS"; c.railSides="RIGHT"; b = run("4. Right only", c); map(b,1,true);
		// 5. Posts only, spacing 4 on 20-long straight -> 6 posts per side
		c = new Cfg(straight); c.width=5; c.railMode="POSTS"; c.postSpacing=4; b = run("5. Posts only, spacing 4, railHeight 1", c); side(b,-2); map(b,1,false);
		c = new Cfg(straight); c.width=5; c.railMode="POSTS"; c.postSpacing=4; c.railHeight=3; b = run("5b. Posts only, spacing 4, railHeight 3", c); side(b,-2);
		// 6. Both: posts + rail connected
		c = new Cfg(straight); c.width=5; c.railMode="BOTH"; c.postSpacing=4; c.railElevation=2; b = run("6. Both, elevation 2", c); side(b,-2);
		// 7. Round rail 3x3
		c = new Cfg(straight); c.width=7; c.railMode="CONTINUOUS"; c.railProfile="ROUND"; c.railWidth=3; c.railHeight=3; b = run("7. Round 3x3 rail", c); cross(b,10);
		// 8. Inset negative -> rail outside deck edge
		c = new Cfg(straight); c.width=5; c.railMode="CONTINUOUS"; c.railInset=-1; b = run("8. Inset -1", c); cross(b,10);
		c = new Cfg(L); c.width=9; c.railMode="CONTINUOUS"; c.railInset=-2; b = run("8b. Inset -2 on L width 9 (ext > deck)", c); map(b,1,true); System.out.println("rail components at y=1: "+components(b,1)+" (expect 2)");
		// 9. Degenerate with rails
		c = new Cfg(new int[][]{{3,3,3},{3,3,3}}); c.width=5; c.railMode="BOTH"; b = run("9. coincident points, Both", c); System.out.println(b.blocks);
		c = new Cfg(new int[][]{{1,1,1},{1,1,1},{1,1,1}}); c.width=3; c.railMode="BOTH"; b = run("9b. all equal, Both", c); System.out.println(b.blocks);
		// 10. Short bridge < spacing
		c = new Cfg(new int[][]{{0,0,0},{2,0,0}}); c.width=3; c.railMode="POSTS"; c.postSpacing=8; b = run("10. length 2 < spacing 8", c); side(b,-1);
	}
}
class PillarTest {
	public static void main(String[] a) throws Exception {
		int[][] straight = {{0,0,0},{20,0,0}};
		int[][] L = {{0,0,0},{20,0,0},{20,0,20}};
		int[][] diag = {{0,0,0},{20,0,13}};
		// tangentOf: n = (-tz, tx)/h must give back (tx, tz)/h
		Method tg = ShapeBridge.class.getDeclaredMethod("tangentOf", double.class, double.class); tg.setAccessible(true);
		double[][] dirs = {{1,0},{0,1},{-1,0},{0,-1},{3,4},{-2,5}};
		boolean ok = true;
		for(double[] d: dirs) { double h=Math.hypot(d[0],d[1]); double nx=-d[1]/h, nz=d[0]/h; double[] t=(double[])tg.invoke(null,nx,nz); ok &= Math.abs(t[0]-d[0]/h)<1e-12 && Math.abs(t[1]-d[1]/h)<1e-12; }
		System.out.println("tangentOf consistent with n for 6 directions: "+(ok?"OK":"FAIL"));
		BridgeTest.Cfg c; BridgeTest.Buf b;
		// 1. None == Step 2 numbers
		c = new BridgeTest.Cfg(L); c.width=9; c.railMode="CONTINUOUS"; b = BridgeTest.run("1. None, L w9 continuous rails (Step 2: 466)", c);
		c = new BridgeTest.Cfg(straight); c.width=5; c.railMode="BOTH"; c.railElevation=2; b = BridgeTest.run("1. None, Both elevation 2 (Step 2: 159)", c);
		// 2. Square w3 depth 10 spacing 10 on 20 -> 3 pillars
		c = new BridgeTest.Cfg(straight); c.width=5; c.pillarMode="ON"; c.pillarWidth=3; c.pillarDepth=10; c.pillarSpacing=10; b = BridgeTest.run("2. Square w3 d10, spacing 10", c); BridgeTest.side(b,0); BridgeTest.map(b,-5,true);
		// 3. Round w5
		c = new BridgeTest.Cfg(straight); c.width=5; c.pillarMode="ON"; c.pillarShape="ROUND"; c.pillarWidth=5; c.pillarDepth=4; c.pillarSpacing=10; b = BridgeTest.run("3. Round w5 d4", c); BridgeTest.map(b,-3,true);
		// 4. Line
		c = new BridgeTest.Cfg(straight); c.width=3; c.pillarMode="ON"; c.pillarShape="LINE"; c.pillarDepth=6; c.pillarSpacing=10; b = BridgeTest.run("4. Line d6", c); BridgeTest.side(b,0); System.out.println("blocks below deck: "+(b.blocks.size()-63)+" (expect 3 pillars x 6 = 18)");
		// 5. Taper widening (base twice the top)
		c = new BridgeTest.Cfg(straight); c.width=3; c.pillarMode="ON"; c.pillarShape="TAPER"; c.pillarWidth=3; c.pillarDepth=8; c.pillarSpacing=40; c.pillarTaper=2f; b = BridgeTest.run("5. Taper 2.0 w3 d8 (base wider)", c); BridgeTest.cross(b,0);
		// 6. Taper narrowing
		c = new BridgeTest.Cfg(straight); c.width=3; c.pillarMode="ON"; c.pillarShape="TAPER"; c.pillarWidth=5; c.pillarDepth=8; c.pillarSpacing=40; c.pillarTaper=0.2f; b = BridgeTest.run("6. Taper 0.2 w5 d8 (narrowing)", c); BridgeTest.cross(b,0);
		// 6b. Taper even width 4, ratio 1 -> straight 4-wide column
		c = new BridgeTest.Cfg(straight); c.width=3; c.pillarMode="ON"; c.pillarShape="TAPER"; c.pillarWidth=4; c.pillarDepth=3; c.pillarSpacing=40; b = BridgeTest.run("6b. Taper 1.0 w4 (even width)", c); BridgeTest.map(b,-2,true);
		// 7. spacing > length -> 2 pillars
		c = new BridgeTest.Cfg(straight); c.width=3; c.pillarMode="ON"; c.pillarSpacing=50; c.pillarDepth=2; b = BridgeTest.run("7. spacing 50 > length 20", c); BridgeTest.side(b,0);
		// 8. everything on
		c = new BridgeTest.Cfg(straight); c.width=5; c.thickness=2; c.railMode="BOTH"; c.pillarMode="ON"; c.pillarSpacing=10; c.pillarDepth=4; b = BridgeTest.run("8. deck t2 + rails + posts + pillars", c); BridgeTest.side(b,0); BridgeTest.side(b,-2);
		// 9. L bend with pillars: pillar centres must lie on the deck top row
		c = new BridgeTest.Cfg(L); c.width=5; c.pillarMode="ON"; c.pillarSpacing=10; c.pillarDepth=3; c.pillarShape="LINE"; b = BridgeTest.run("9. L bend, line pillars spacing 10", c);
		int onDeck=0, total=0; for(String s: b.blocks){ String[] q=s.split(","); if(Integer.parseInt(q[1])==-1){ total++; if(b.has(Integer.parseInt(q[0]),0,Integer.parseInt(q[2]))) onDeck++; } }
		System.out.println("pillar tops under deck: "+onDeck+"/"+total+" (all should be under the deck)"); BridgeTest.map(b,-1,true);
		// 9b. diagonal bridge, square footprint aligned to travel direction
		c = new BridgeTest.Cfg(diag); c.width=1; c.pillarMode="ON"; c.pillarWidth=5; c.pillarDepth=1; c.pillarSpacing=100; b = BridgeTest.run("9b. diagonal, square w5 footprint (aligned to path)", c); BridgeTest.map(b,-1,true);
		// 10. degenerate
		c = new BridgeTest.Cfg(new int[][]{{3,3,3},{3,3,3}}); c.width=3; c.railMode="BOTH"; c.pillarMode="ON"; c.pillarDepth=3; b = BridgeTest.run("10. coincident, rails+pillars", c); System.out.println(b.blocks);
	}
}
