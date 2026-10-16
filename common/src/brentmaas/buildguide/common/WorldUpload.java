package brentmaas.buildguide.common;

import brentmaas.buildguide.common.shape.IShapeBuffer;

/**
 * Live apply over several frames: once the WorldUpdateGate lets a new world buffer through, it is sent to the
 * GPU a part per frame (IShapeBuffer.endPart, at most budgetNanos per frame, at least one section) while the
 * world keeps drawing the old one; it replaces the old one only when its last part is sent, so the world never
 * shows half a shape. A buffer the shape no longer holds (a newer generation or focus replaced it) is dropped:
 * its parts already on the GPU are closed. A buffer whose parts are all sent in one call (every buffer but the
 * sectioned one) is applied in that frame, as before. Render thread only.
 */
public final class WorldUpload {
	public static final long budgetNanos = 3000000;

	private IShapeBuffer buffer = null;
	private String reason = null;
	private long sendNanos = 0, maxFrameNanos = 0;
	private int frames = 0;

	// Whether this buffer is the one being sent
	public boolean isSending(IShapeBuffer b) {
		return buffer != null && buffer == b;
	}

	// The buffer being sent, if the shape no longer holds it: its parts on the GPU go. Returns whether one was dropped
	public boolean dropIfNot(IShapeBuffer held) {
		if(buffer == null || buffer == held) return false;
		buffer.close();
		clear();
		return true;
	}

	// A new buffer to send; reason: what let it through (WorldUpdateGate.reason)
	public void start(IShapeBuffer b, String reason) {
		buffer = b;
		this.reason = reason;
		sendNanos = 0;
		maxFrameNanos = 0;
		frames = 0;
	}

	// One frame of sending took this long
	public void frame(long nanos) {
		sendNanos += nanos;
		if(nanos > maxFrameNanos) maxFrameNanos = nanos;
		++frames;
	}

	// The last part is sent: the buffer is the world's now
	public void clear() {
		buffer = null;
	}

	public String getReason() {
		return reason;
	}

	public long getSendNanos() {
		return sendNanos;
	}

	public long getMaxFrameNanos() {
		return maxFrameNanos;
	}

	public int getFrames() {
		return frames;
	}
}
