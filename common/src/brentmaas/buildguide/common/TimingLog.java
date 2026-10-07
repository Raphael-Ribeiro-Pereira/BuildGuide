package brentmaas.buildguide.common;

import java.util.Locale;
import java.util.function.Consumer;

import brentmaas.buildguide.common.shape.VertexMemory;

/**
 * Diagnostic timing lines for the phases of a shape change, so where a freeze comes from shows in
 * numbers in latest.log: "[Build Guide] timing: phase=... ms=... blocks=...". Only phases of more
 * than thresholdMillis are written, at most maxLines per session (then one notice). Thread safe:
 * generation runs on the executor, the other phases on the render thread. The world-buffer and
 * preview-rebuild lines end with native=: the vertex memory still held (VertexMemory), in MiB, which
 * must stay level while shapes are edited.
 */
public final class TimingLog {
	public static final long thresholdMillis = 8;
	// The world update (recordWorld) has its own, lower threshold: it was never seen above 8 ms
	public static final long worldThresholdMillis = 4;
	public static final int maxLines = 200;
	public static final String GENERATION = "generation", WORLD_BUFFER = "world-buffer", SCAN = "scan", PREVIEW_SNAPSHOT = "preview-snapshot", PREVIEW_REBUILD = "preview-rebuild";

	private static int written = 0;
	// Where lines go; the log handler by default (replaceable for the offline harness)
	public static Consumer<String> sink = line -> BuildGuide.logHandler.debugOrHigher(line);

	private TimingLog() {}

	public static void record(String phase, long millis, int blocks) {
		record(phase, millis, blocks, null);
	}
	
	// With what caused the phase (the preview rebuild: generation, view, validation, slice or filter)
	public static synchronized void record(String phase, long millis, int blocks, String reason) {
		if(millis <= thresholdMillis || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + phase + " ms=" + millis + " blocks=" + blocks + (reason == null ? "" : " reason=" + reason);
		if(PREVIEW_REBUILD.equals(phase)) line += " native=" + VertexMemory.liveMegabytes();
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	// The scan in Y layers (SliceScan): its total time, the frames it was spread over and the longest one
	public static synchronized void recordScan(long totalMillis, int slices, long maxSliceMillis, int blocks) {
		recordScan(totalMillis, slices, maxSliceMillis, blocks, -1, -1);
	}

	// With the two steps outside the layers, each in a single frame: preparing the scan (the expected positions
	// in world coordinates and the SliceScan) and publishing its result. Negative: not measured, left out
	public static synchronized void recordScan(long totalMillis, int slices, long maxSliceMillis, int blocks, long startMillis, long publishMillis) {
		if(totalMillis + Math.max(0, startMillis) + Math.max(0, publishMillis) <= thresholdMillis || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + SCAN + " ms=" + totalMillis + " slices=" + slices + " maxslice=" + maxSliceMillis + " blocks=" + blocks;
		if(startMillis >= 0) line += " start=" + startMillis;
		if(publishMillis >= 0) line += " publish=" + publishMillis;
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	/**
	 * The frame in which a new buffer replaces the one drawn in the world (live apply), split in three:
	 * world-end (the buffer's end(): the vertices and, when it grows, the shared index buffer sent to the
	 * GPU), world-close (the old buffer discarded) and world-other (the rest of the mod's work for that shape
	 * in the same frame: drawing, the error overlay, the scan's start, the safety net, the Area 3 target).
	 * ms is the sum. reason: what let the update through (WorldUpdateGate.reason). Threshold: 4 ms.
	 */
	public static synchronized void recordWorld(String reason, long endNanos, long closeNanos, long otherNanos, int blocks) {
		long totalNanos = endNanos + closeNanos + otherNanos;
		if(totalNanos <= worldThresholdMillis * 1000000 || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + WORLD_BUFFER + " ms=" + Math.round(totalNanos / 1e6) + " blocks=" + blocks + " reason=" + reason
				+ " world-end=" + tenths(endNanos) + " world-close=" + tenths(closeNanos) + " world-other=" + tenths(otherNanos) + " native=" + VertexMemory.liveMegabytes();
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	// Milliseconds with one decimal, the same in every locale
	private static String tenths(long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
	}

	// Offline harness only
	static synchronized void reset() {
		written = 0;
	}
}
