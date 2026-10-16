package brentmaas.buildguide.common;

import java.util.Locale;
import java.util.function.Consumer;

import brentmaas.buildguide.common.shape.GpuMemory;
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
	public static final int maxRenderLines = 120;
	public static final String GENERATION = "generation", WORLD_BUFFER = "world-buffer", SCAN = "scan", PREVIEW_SNAPSHOT = "preview-snapshot", PREVIEW_REBUILD = "preview-rebuild", RENDER = "render";

	private static int written = 0, renderWritten = 0;
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

	/**
	 * The preview's mesh made again (performance step 2): ms= is what the render thread spent (sending it to
	 * the GPU), build= the worker thread's time writing it, wait= from the request until it was on screen, then
	 * blocks, reason (generation, view, validation, slice, filter, colour, layers) and native=. Written when
	 * ms + build pass the threshold.
	 */
	public static synchronized void recordPreview(long renderMillis, long buildMillis, long waitMillis, int blocks, String reason) {
		if(renderMillis + buildMillis <= thresholdMillis || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + PREVIEW_REBUILD + " ms=" + renderMillis + " build=" + buildMillis + " wait=" + waitMillis + " blocks=" + blocks + " reason=" + reason + " native=" + VertexMemory.liveMegabytes();
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
		recordScan(totalMillis, slices, maxSliceMillis, blocks, startMillis, publishMillis, -1);
	}

	// With the preparation on the worker thread (performance step 2), which costs the frames nothing: " prep=" last
	public static synchronized void recordScan(long totalMillis, int slices, long maxSliceMillis, int blocks, long startMillis, long publishMillis, long prepMillis) {
		if(totalMillis + Math.max(0, startMillis) + Math.max(0, publishMillis) <= thresholdMillis || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + SCAN + " ms=" + totalMillis + " slices=" + slices + " maxslice=" + maxSliceMillis + " blocks=" + blocks;
		if(startMillis >= 0) line += " start=" + startMillis;
		if(publishMillis >= 0) line += " publish=" + publishMillis;
		if(prepMillis >= 0) line += " prep=" + prepMillis;
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
		recordWorld(reason, endNanos, closeNanos, otherNanos, blocks, -1);
	}

	// With the build focus: focused = the cubes the buffer holds, as " focus=<focused>/<blocks>" (-1: not shown)
	public static synchronized void recordWorld(String reason, long endNanos, long closeNanos, long otherNanos, int blocks, int focused) {
		recordWorld(reason, endNanos, closeNanos, otherNanos, blocks, focused, 0, 1, endNanos);
	}

	/**
	 * With the world buffer in sections (SectionedBuffer, sent a part per frame by WorldUpload): world-end is the
	 * sending time over all its frames, then " sections=<n> frames=<frames> maxframe=<longest frame of sending, ms>
	 * gpu=<GPU memory of the mod's buffers, MiB>". Without sections (0) the line is the one above.
	 */
	public static synchronized void recordWorld(String reason, long endNanos, long closeNanos, long otherNanos, int blocks, int focused, int sections, int frames, long maxFrameNanos) {
		long totalNanos = endNanos + closeNanos + otherNanos;
		if(totalNanos <= worldThresholdMillis * 1000000 || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + WORLD_BUFFER + " ms=" + Math.round(totalNanos / 1e6) + " blocks=" + blocks + " reason=" + reason
				+ " world-end=" + tenths(endNanos) + " world-close=" + tenths(closeNanos) + " world-other=" + tenths(otherNanos) + " native=" + VertexMemory.liveMegabytes()
				+ (focused < 0 ? "" : " focus=" + focused + "/" + blocks)
				+ (sections <= 0 ? "" : " sections=" + sections + " frames=" + frames + " maxframe=" + tenths(maxFrameNanos) + " gpu=" + GpuMemory.liveMegabytes());
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	/**
	 * The steady cost while shapes are drawn (RenderStats, every 30 s): "phase=render frames=<n> avg=<ms per frame,
	 * two decimals> max=<longest frame, ms> sections=<drawn>/<total> gpu=<MiB> native=<MiB>". Its own limit of
	 * maxRenderLines (an hour), so it never uses up the lines of the other phases.
	 */
	public static synchronized void recordRender(int frames, long avgNanos, long maxNanos, int drawnSections, int totalSections) {
		if(renderWritten >= maxRenderLines) return;
		++renderWritten;
		String line = "[Build Guide] timing: phase=" + RENDER + " frames=" + frames + " avg=" + String.format(Locale.ROOT, "%.2f", avgNanos / 1e6) + " max=" + tenths(maxNanos)
				+ " sections=" + drawnSections + "/" + totalSections + " gpu=" + GpuMemory.liveMegabytes() + " native=" + VertexMemory.liveMegabytes();
		if(renderWritten == maxRenderLines) line += " (limit of " + maxRenderLines + " render lines reached, no more this session)";
		sink.accept(line);
	}

	// Milliseconds with one decimal, the same in every locale
	private static String tenths(long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
	}

	// Offline harness only
	static synchronized void reset() {
		written = 0;
		renderWritten = 0;
	}
}
