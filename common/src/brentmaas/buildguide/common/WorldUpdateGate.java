package brentmaas.buildguide.common;

import java.util.function.LongSupplier;

/**
 * When the heavy world part of a shape change runs: uploading the regenerated shape buffer that is
 * drawn in the world, and the validation scan that follows it. Values and generation are never
 * delayed (the embedded preview keeps its own 250 ms throttle). Every change calls request(); a new
 * request replaces the pending one (the latest generation is the only one ever shown), so work never
 * piles up.
 *
 * Modes (Configuration, buildguide.cfg): LIVE applies as soon as the generation is ready; IDLE waits
 * idleMillis after the last request; ON_CLOSE waits until the menu closes. Closing the menu always
 * applies at once. Pure, with an injectable clock.
 */
public class WorldUpdateGate {
	public enum Mode{
		LIVE,
		IDLE,
		ON_CLOSE
	}

	public static final long idleMillis = 1000;

	private final LongSupplier clock;
	private boolean pending = false;
	private long lastRequest = 0;

	public WorldUpdateGate(LongSupplier clock) {
		this.clock = clock;
	}

	public void request() {
		pending = true;
		lastRequest = clock.getAsLong();
	}

	public boolean shouldApply(Mode mode, boolean menuOpen) {
		if(!pending) return false;
		if(!menuOpen) return true;
		switch(mode) {
		case LIVE:
			return true;
		case IDLE:
			return clock.getAsLong() - lastRequest >= idleMillis;
		default:
			return false;
		}
	}

	public void applied() {
		pending = false;
	}

	public boolean isPending() {
		return pending;
	}
}
