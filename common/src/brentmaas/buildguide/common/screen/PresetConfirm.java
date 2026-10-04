package brentmaas.buildguide.common.screen;

import java.util.function.LongSupplier;

/**
 * Two-click confirmation of the preset menu (E8). A destructive press (Load, Clear, Save over an
 * occupied slot) only arms its button, which then reads "Replace?" / "Clear?" / "Overwrite?"; a
 * second press of the same button runs it. Pressing any other button, or timeoutMillis passing,
 * disarms. One armed button at a time. The clock is injectable (PreviewController pattern) so the
 * harness tests the timeout without sleeping.
 */
public class PresetConfirm {
	public enum Action {SAVE, LOAD, CLEAR}
	public static final long timeoutMillis = 3000;
	
	private final LongSupplier clock;
	private int armedSlot = -1;
	private Action armedAction = null;
	private long armedAt = 0;
	
	public PresetConfirm() {
		this(System::currentTimeMillis);
	}
	
	public PresetConfirm(LongSupplier clock) {
		this.clock = clock;
	}
	
	// A press of (slot, action); needsConfirm: the action would replace or delete something. Returns
	// true when the action must run now
	public boolean press(int slot, Action action, boolean needsConfirm) {
		boolean wasArmed = isArmed(slot, action);
		disarm();
		if(!needsConfirm || wasArmed) return true;
		armedSlot = slot;
		armedAction = action;
		armedAt = clock.getAsLong();
		return false;
	}
	
	public boolean isArmed(int slot, Action action) {
		if(armedAction != null && clock.getAsLong() - armedAt >= timeoutMillis) disarm();
		return armedSlot == slot && armedAction == action;
	}
	
	public void disarm() {
		armedSlot = -1;
		armedAction = null;
	}
}
