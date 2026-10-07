package brentmaas.buildguide.common.shape;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Native vertex memory held by the loader's shape buffers: the CPU copy of the vertices, written during a
 * generation and freed once they are on the GPU or the generation is discarded. For the diagnostic native=
 * in the timing log, which must stay level while shapes are edited.
 *
 * Each buffer opens an Account. The account follows its builder's capacity with vanilla's growth rule
 * (ByteBufferBuilder: when a write passes the capacity it grows by the capacity, at most 2 MiB at a time, or
 * straight to what is needed) and gives it back exactly once, on release. Only java.*: no net.minecraft.
 */
public final class VertexMemory {
	public static final long maxGrowth = 2097152;
	private static final AtomicLong live = new AtomicLong(), opened = new AtomicLong(), released = new AtomicLong();

	private VertexMemory() {}

	// Vanilla's capacity after a write that needs `needed` bytes in all
	public static long grownCapacity(long capacity, long needed) {
		if(needed <= capacity) return capacity;
		return Math.max(needed, capacity + Math.min(capacity, maxGrowth));
	}

	public static Account open(long initialCapacity) {
		return new Account(initialCapacity);
	}

	// Bytes held by the accounts not released yet
	public static long liveBytes() {
		return live.get();
	}

	public static long openAccounts() {
		return opened.get() - released.get();
	}

	// liveBytes in MiB with one decimal, the same in every locale
	public static String liveMegabytes() {
		return String.format(Locale.ROOT, "%.1f", live.get() / 1048576.0);
	}

	/**
	 * One builder's memory. write() follows the bytes the builder reserves; an account is written by one thread
	 * at a time (the generation, or the render thread for its own buffers) and the shape's lock orders the
	 * hand-over. release() gives the capacity back once and says whether this call did it. A write after the
	 * release throws, so a loader that checks first never writes into freed memory.
	 */
	public static final class Account {
		private long capacity, written = 0;
		private volatile boolean isReleased = false;

		private Account(long initialCapacity) {
			capacity = initialCapacity;
			live.addAndGet(initialCapacity);
			opened.incrementAndGet();
		}

		public void write(int bytes) {
			if(isReleased) throw new IllegalStateException("vertex data written after it was released");
			written += bytes;
			if(written > capacity) {
				long next = grownCapacity(capacity, written);
				live.addAndGet(next - capacity);
				capacity = next;
			}
		}

		public synchronized boolean release() {
			if(isReleased) return false;
			isReleased = true;
			live.addAndGet(-capacity);
			released.incrementAndGet();
			return true;
		}

		public boolean isReleased() {
			return isReleased;
		}

		public long getCapacity() {
			return capacity;
		}

		public long getWritten() {
			return written;
		}
	}
}
