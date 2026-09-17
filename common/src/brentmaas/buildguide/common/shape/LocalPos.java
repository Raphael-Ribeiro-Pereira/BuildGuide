package brentmaas.buildguide.common.shape;

// Packs a local block position into a long: 21 signed bits per axis
public class LocalPos {
	public static long pack(int x, int y, int z) {
		return ((x & 0x1FFFFFL) << 42) | ((y & 0x1FFFFFL) << 21) | (z & 0x1FFFFFL);
	}
	
	public static int unpackX(long key) {
		return signExtend21((int) ((key >> 42) & 0x1FFFFFL));
	}
	
	public static int unpackY(long key) {
		return signExtend21((int) ((key >> 21) & 0x1FFFFFL));
	}
	
	public static int unpackZ(long key) {
		return signExtend21((int) (key & 0x1FFFFFL));
	}
	
	private static int signExtend21(int v) {
		return (v << 11) >> 11;
	}
}
