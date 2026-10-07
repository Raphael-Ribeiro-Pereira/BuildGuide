import java.nio.*;
import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.IslandGeometry.*;
// Timing probe, not an assert test (like ScanCostTest): what the render thread does in the frame a new
// shape enters the world, measured offline on islands of 35k, 122k and 177k blocks. The scan's start
// (RenderHandler.startScan: world positions, tracked list, SliceScan), its publish, one memcpy of the
// vertex data (the least a driver copies in glBufferStorage) and the growth of vanilla's shared QUADS
// index buffer (twice the indices, through a lambda). 5 runs each, so JIT warm-up shows.
public class WorldCost {
	static long asLong(int x, int y, int z){ return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF); } // BlockPos.asLong layout
	static int ux(long l){ return (int)(l >> 38); } static int uy(long l){ return (int)(l << 52 >> 52); } static int uz(long l){ return (int)(l << 26 >> 38); }
	static Params island(int w, int wall, int depth){ Params p = new Params(); p.outline = Outline.SQUARE; p.roundness = 0; p.widthX = w; p.widthZ = w; p.wall = wall; p.depth = depth; p.profile = Profile.BOWL; p.roughness = 0.3; p.edgeAmplitude = 0; p.seed = 8; return p; }
	public static void main(String[] a) throws Exception {
		Params worst = island(121, 3, 80); worst.spikes = 64; worst.spikeMode = SpikeMode.FILL; worst.spikeLength = 40; worst.spikeBase = 8; worst.taper = 0.5; worst.breakMode = BreakMode.SEGMENTED; worst.pieces = 4; worst.gap = 1;
		Object[][] cases = {{"square 81, wall 2, depth 50", island(81, 2, 50)}, {"square 121, wall 3, depth 80", island(121, 3, 80)}, {"worst: + 64 spikes, segmented", worst}};
		for(Object[] c: cases){
			List<Long> shape = new ArrayList<>(); IslandGeometry.enumerate((Params) c[1], (x, y, z) -> shape.add(LocalPos.pack(x, y, z)));
			Set<Long> expected = new HashSet<>(shape);
			int n = expected.size(); System.out.println("== " + c[0] + ": " + n + " blocks, " + (n * 24 * 16 / 1048576) + " MB of vertices (24 x 16 B per block)");
			for(int rep = 0; rep < 5; rep++){
				long t0 = System.nanoTime();
				// RenderHandler.startScan: world positions, then the tracked list, then the SliceScan
				ValidationState state = new ValidationState(); int ox = 100, oy = 64, oz = -200;
				Set<Long> world = new HashSet<Long>();
				for(long l: expected){ int lx = LocalPos.unpackX(l), ly = LocalPos.unpackY(l), lz = LocalPos.unpackZ(l); if(state.isExcluded(lx, ly, lz)) continue; world.add(asLong(ox + lx, oy + ly, oz + lz)); }
				List<Long> tracked = new ArrayList<Long>(world.size());
				for(long wl: world) tracked.add(LocalPos.pack(ux(wl) - ox, uy(wl) - oy, uz(wl) - oz));
				long t1 = System.nanoTime();
				SliceScan scan = new SliceScan(state, expected, tracked, ox, oy, oz, 1);
				long t2 = System.nanoTime();
				// Upload proxy: one memcpy of the vertex data (the driver copies at least this much in glBufferStorage)
				ByteBuffer src = ByteBuffer.allocateDirect(n * 384), dst = ByteBuffer.allocateDirect(n * 384);
				long t3 = System.nanoTime(); dst.put(src); long t4 = System.nanoTime();
				// Vanilla's shared QUADS index buffer when it grows: 2 x the needed indices, 4 per 6, through a lambda
				int indices = n * 24 / 4 * 6 * 2; ByteBuffer ib = ByteBuffer.allocateDirect(indices * 4).order(ByteOrder.nativeOrder());
				java.util.function.IntConsumer put = ib::putInt;
				long t5 = System.nanoTime(); for(int m = 0; m < indices; m += 6){ int v = m * 4 / 6; put.accept(v); put.accept(v + 1); put.accept(v + 2); put.accept(v + 2); put.accept(v + 3); put.accept(v); } long t6 = System.nanoTime();
				System.out.printf("  run %d: startScan sets %5.1f ms + SliceScan %5.1f ms = %5.1f ms | memcpy %5.1f ms | index growth %5.1f ms%n", rep + 1, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t2 - t0) / 1e6, (t4 - t3) / 1e6, (t6 - t5) / 1e6);
				IBlockProbe air = new IBlockProbe(){ public boolean isLoaded(int x, int y, int z){ return true; } public int flags(int x, int y, int z){ return FLAG_AIR; } public String name(int x, int y, int z){ return "air"; } };
				while(!scan.step(air, 0, System::nanoTime));
				long t7 = System.nanoTime(); scan.publish(air); long t8 = System.nanoTime();
				System.out.printf("         publish (all missing) %5.1f ms; layer steps %d ms in total%n", (t8 - t7) / 1e6, scan.getTotalMillis());
			}
		}
	}
}
