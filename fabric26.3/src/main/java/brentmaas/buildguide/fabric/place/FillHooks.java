package brentmaas.buildguide.fabric.place;

import brentmaas.buildguide.common.SmartFill;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fill (SmartFill) in the client: a batch goes out on every client tick, once the last one was answered
 * (SmartFillServer places it on the server thread); leaving the world stops it.
 */
public final class FillHooks {
	private FillHooks() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if(mc.player == null) SmartFill.stop(); // left the world
			SmartFill.tick();
		});
	}
}
