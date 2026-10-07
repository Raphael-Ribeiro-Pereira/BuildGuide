package brentmaas.buildguide.common;

import java.util.function.Consumer;

/**
 * Diagnostic timing lines for the phases of a shape change, so where a freeze comes from shows in
 * numbers in latest.log: "[Build Guide] timing: phase=... ms=... blocks=...". Only phases of more
 * than thresholdMillis are written, at most maxLines per session (then one notice). Thread safe:
 * generation runs on the executor, the other phases on the render thread.
 */
public final class TimingLog {
	public static final long thresholdMillis = 8;
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
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	// The scan in Y layers (SliceScan): its total time, the frames it was spread over and the longest one
	public static synchronized void recordScan(long totalMillis, int slices, long maxSliceMillis, int blocks) {
		if(totalMillis <= thresholdMillis || written >= maxLines) return;
		++written;
		String line = "[Build Guide] timing: phase=" + SCAN + " ms=" + totalMillis + " slices=" + slices + " maxslice=" + maxSliceMillis + " blocks=" + blocks;
		if(written == maxLines) line += " (limit of " + maxLines + " timing lines reached, no more this session)";
		sink.accept(line);
	}

	// Offline harness only
	static synchronized void reset() {
		written = 0;
	}
}
