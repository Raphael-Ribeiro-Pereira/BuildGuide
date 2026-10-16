package brentmaas.buildguide.common;

import java.util.function.LongSupplier;

/**
 * The mod's steady cost in the world, for the timing log (TimingLog.recordRender): while shapes are drawn, one
 * "phase=render" line every periodMillis with the frames of the period, the mod's time per frame on the render
 * thread (average and longest), the world sections drawn of those the shapes have (per frame, on average) and
 * the GPU and native memory the mod's buffers hold. A JFR recording shows the CPU too; the sections drawn and
 * the GPU memory it cannot see. Render thread only; the clock can be replaced for the offline harness.
 */
public final class RenderStats {
	public static final long periodMillis = 30000;
	public static LongSupplier clock = System::currentTimeMillis;

	private static long periodStart = -1;
	private static int frames = 0;
	private static long nanos = 0, maxNanos = 0, drawn = 0, total = 0;

	private RenderStats() {}

	// One shape set's world sections in a frame: how many were drawn of how many it has
	public static void sections(int drawnSections, int totalSections) {
		drawn += drawnSections;
		total += totalSections;
	}

	// The end of a frame in which the mod drew: its time in that frame. Writes the line when the period is over
	public static void frame(long frameNanos) {
		long now = clock.getAsLong();
		if(periodStart < 0) periodStart = now;
		++frames;
		nanos += frameNanos;
		if(frameNanos > maxNanos) maxNanos = frameNanos;
		if(now - periodStart < periodMillis) return;
		TimingLog.recordRender(frames, nanos / frames, maxNanos, (int) Math.round((double) drawn / frames), (int) Math.round((double) total / frames));
		reset();
		periodStart = now;
	}

	// Offline harness, and the start of each period
	static void reset() {
		periodStart = -1;
		frames = 0;
		nanos = 0;
		maxNanos = 0;
		drawn = 0;
		total = 0;
	}
}
