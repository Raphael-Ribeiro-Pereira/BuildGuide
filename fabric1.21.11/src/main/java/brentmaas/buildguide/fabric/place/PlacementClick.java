package brentmaas.buildguide.fabric.place;

import java.util.Locale;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.shape.GuidelinePicker;

/**
 * Area 3: a right click fills the guideline cell aimed at. This class holds the short diagnostic
 * log (at most maxLogLines lines in latest.log, then a notice): when a target appears, when the
 * click's hitResult is replaced and when a click is discarded.
 */
public final class PlacementClick {
	public static final int maxLogLines = 50;
	private static int logged = 0;
	private static boolean hadTarget = false;

	private PlacementClick() {}

	// Called once per frame with the published target: logs only when a target appears after none
	public static void noteTarget(GuidelinePicker.Target target) {
		boolean has = target != null;
		if(has && !hadTarget) log(String.format(Locale.ROOT, "target [%d, %d, %d] at %.2f blocks", target.x, target.y, target.z, target.distance));
		hadTarget = has;
	}

	static void log(String message) {
		if(logged >= maxLogLines) return;
		++logged;
		String line = "[Build Guide] place: " + message;
		if(logged == maxLogLines) line += " (diagnostic limit of " + maxLogLines + " lines reached, no more place lines)";
		BuildGuide.logHandler.debugOrHigher(line);
	}
}
