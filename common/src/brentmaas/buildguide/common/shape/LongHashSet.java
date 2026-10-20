package brentmaas.buildguide.common.shape;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The shape's expected blocks (performance step 4): a set of packed positions on primitive arrays instead of a
 * HashSet of boxed Longs. Adding and asking allocate nothing (a HashSet made a Long and a node per block, every
 * generation), the positions keep the order they were added in, so iterating walks one array, and
 * toLongArray is a single copy (the preview snapshot and the scan copy were a walk over 50 000 nodes on the
 * render thread). It still is a Set<Long> for the readers that want one (boxed, as slow as before).
 *
 * Open addressing with linear probing, at most half full. Packed positions are never negative (LocalPos), so -1
 * marks a free slot; a -1 added anyway is kept apart. No removal but clear(). Not thread safe: the shape's lock
 * guards it, as it guarded the HashSet.
 */
public final class LongHashSet extends AbstractSet<Long> {
	private static final long FREE = -1;
	private static final int minCapacity = 16;
	private long[] table;
	private int mask;
	// The positions in the order they were added
	private long[] items = new long[minCapacity];
	private int size = 0;
	private boolean hasFree = false;

	public LongHashSet() {
		allocate(minCapacity * 2);
	}

	private void allocate(int capacity) {
		table = new long[capacity];
		Arrays.fill(table, FREE);
		mask = capacity - 1;
	}

	private int slot(long key) {
		long h = key * 0x9E3779B97F4A7C15L;
		return (int) (h ^ (h >>> 32)) & mask;
	}

	// Adds the position; false when it is in already
	public boolean add(long key) {
		if(key == FREE) {
			if(hasFree) return false;
			hasFree = true;
			append(key);
			return true;
		}
		int i = slot(key);
		while(table[i] != FREE) {
			if(table[i] == key) return false;
			i = (i + 1) & mask;
		}
		table[i] = key;
		append(key);
		if(size * 2 > table.length) rehash(table.length * 2);
		return true;
	}

	private void append(long key) {
		if(size == items.length) items = Arrays.copyOf(items, size * 2);
		items[size++] = key;
	}

	private void rehash(int capacity) {
		allocate(capacity);
		for(int k = 0;k < size;++k) {
			long key = items[k];
			if(key == FREE) continue;
			int i = slot(key);
			while(table[i] != FREE) i = (i + 1) & mask;
			table[i] = key;
		}
	}

	public boolean contains(long key) {
		if(key == FREE) return hasFree;
		int i = slot(key);
		while(table[i] != FREE) {
			if(table[i] == key) return true;
			i = (i + 1) & mask;
		}
		return false;
	}

	// The k-th position added (0 <= k < size())
	public long get(int k) {
		if(k < 0 || k >= size) throw new IndexOutOfBoundsException(k);
		return items[k];
	}

	// Every position, in the order they were added
	public long[] toLongArray() {
		return Arrays.copyOf(items, size);
	}

	@Override
	public boolean add(Long key) {
		return add(key.longValue());
	}

	@Override
	public boolean contains(Object o) {
		return o instanceof Long && contains(((Long) o).longValue());
	}

	@Override
	public int size() {
		return size;
	}

	// Empty again. The arrays are kept for the next generation when they were well used (no new garbage per
	// generation), made small again after a big shape became a small one
	@Override
	public void clear() {
		if(size * 8 < table.length && table.length > minCapacity * 2) {
			allocate(minCapacity * 2);
			items = new long[minCapacity];
		}else {
			Arrays.fill(table, FREE);
		}
		size = 0;
		hasFree = false;
	}

	@Override
	public Iterator<Long> iterator() {
		return new Iterator<Long>() {
			private int next = 0;

			@Override
			public boolean hasNext() {
				return next < size;
			}

			@Override
			public Long next() {
				if(next >= size) throw new NoSuchElementException();
				return items[next++];
			}
		};
	}
}
