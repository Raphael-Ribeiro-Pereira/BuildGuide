public class BaseSetTest { public static void main(String[] a) throws Exception {
	BridgeTest.Cfg c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0},{20,0,20}}); c.width=9; c.railMode="CONTINUOUS";
	brentmaas.buildguide.common.shape.ShapeBridge b = new brentmaas.buildguide.common.shape.ShapeBridge();
	BridgeTest.Buf buf = BridgeTest.run("L w9 rails (Step 2: 466)", c);
	c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0}}); c.width=5; c.railMode="BOTH"; c.railElevation=2; BridgeTest.run("Both elevation 2 (Step 2: 159)", c);
	c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0}}); c.width=5; c.thickness=2; c.railMode="BOTH"; c.pillarMode="ON"; c.pillarSpacing=10; c.pillarDepth=4; BridgeTest.run("deck+rails+posts+pillars (Step 3: 360)", c);
}}
