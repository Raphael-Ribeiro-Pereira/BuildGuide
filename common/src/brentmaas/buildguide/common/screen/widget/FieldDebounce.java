package brentmaas.buildguide.common.screen.widget;

import java.util.function.LongSupplier;

/**
 * Live apply for a text field: the field's own apply action (its Enter handler) runs by itself
 * delayMillis after the last keystroke, or at once when the field loses focus. Only text the user
 * typed counts: a change made by code (setTextValue) is the new reference and never fires. Text equal
 * to the last applied text does not fire either, so typing a value back does not regenerate.
 * Invalid values are the apply action's business (red field, nothing applied), as with Enter.
 *
 * The loader's text field calls the on* methods and asks poll() once per frame. Pure, with an
 * injectable clock.
 */
public class FieldDebounce {
	public static final long delayMillis = 500;

	private final LongSupplier clock;
	private String appliedText = "";
	private boolean pending = false;
	private long lastEdit = 0;

	public FieldDebounce(LongSupplier clock) {
		this.clock = clock;
	}

	// Text set by code: it becomes the reference, and anything pending is dropped
	public void onProgrammaticSet(String text) {
		appliedText = text == null ? "" : text;
		pending = false;
	}

	// Text changed by the user (typing, pasting, deleting)
	public void onUserEdit(String text) {
		lastEdit = clock.getAsLong();
		pending = !appliedText.equals(text == null ? "" : text);
	}

	// The field lost focus: true when the apply action must run now
	public boolean onFocusLost(String text) {
		return take(text);
	}

	// Once per frame: true when the apply action must run now (delayMillis after the last keystroke)
	public boolean poll(String text) {
		if(!pending || clock.getAsLong() - lastEdit < delayMillis) return false;
		return take(text);
	}

	// The apply action ran by another way (Enter, or a point row committing its three fields)
	public void markApplied(String text) {
		appliedText = text == null ? "" : text;
		pending = false;
	}

	public boolean isPending() {
		return pending;
	}

	private boolean take(String text) {
		String t = text == null ? "" : text;
		boolean fire = pending && !appliedText.equals(t);
		pending = false;
		if(fire) appliedText = t;
		return fire;
	}
}
