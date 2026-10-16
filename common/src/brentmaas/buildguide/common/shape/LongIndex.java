package brentmaas.buildguide.common.shape;

/**
 * A set of packed positions (LocalPos, never negative) that also numbers them: each key added gets the next
 * index, 0, 1, 2... Open addressing with linear probing over primitive arrays, so neither adding nor looking up
 * allocates (the scan asks it thousands of times per layer). Not thread safe; fixed capacity.
 */
public final class LongIndex {
	private static final long EMPTY = -1;
	private final long[] keys;
	private final int[] values;
	private final int mask;
	private int size = 0;

	// Room for at least `expected` keys at a load of at most one half
	public LongIndex(int expected) {
		int capacity = Integer.highestOneBit(Math.max(4, expected) * 2 - 1) << 1;
		keys = new long[capacity];
		values = new int[capacity];
		java.util.Arrays.fill(keys, EMPTY);
		mask = capacity - 1;
	}

	private int slot(long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h ^ (h >>> 32)) & mask;
	}

	// Adds the key and returns its index; a key already there keeps its index
	public int add(long key) {
		int i = slot(key);
		while(keys[i] != EMPTY) {
			if(keys[i] == key) return values[i];
			i = (i + 1) & mask;
		}
		if(size * 2 >= keys.length) throw new IllegalStateException("LongIndex full");
		keys[i] = key;
		values[i] = size;
		return size++;
	}

	// The key's index, or -1
	public int indexOf(long key) {
		int i = slot(key);
		while(keys[i] != EMPTY) {
			if(keys[i] == key) return values[i];
			i = (i + 1) & mask;
		}
		return -1;
	}

	public boolean contains(long key) {
		return indexOf(key) >= 0;
	}

	public int size() {
		return size;
	}
}
