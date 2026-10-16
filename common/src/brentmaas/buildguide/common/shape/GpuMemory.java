package brentmaas.buildguide.common.shape;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GPU memory held by the loader's vertex buffers (the world's sections, the error overlay, the preview, the
 * Area 3 outline), for the diagnostic gpu= in the timing log: what an upload adds, its close() gives back. The
 * shared index buffer is vanilla's and not counted. Only java.*: no net.minecraft.
 */
public final class GpuMemory {
	private static final AtomicLong live = new AtomicLong(), buffers = new AtomicLong();

	private GpuMemory() {}

	public static void allocated(long bytes) {
		live.addAndGet(bytes);
		buffers.incrementAndGet();
	}

	public static void freed(long bytes) {
		live.addAndGet(-bytes);
		buffers.decrementAndGet();
	}

	public static long liveBytes() {
		return live.get();
	}

	public static long liveBuffers() {
		return buffers.get();
	}

	// liveBytes in MiB with one decimal, the same in every locale
	public static String liveMegabytes() {
		return String.format(Locale.ROOT, "%.1f", live.get() / 1048576.0);
	}
}
