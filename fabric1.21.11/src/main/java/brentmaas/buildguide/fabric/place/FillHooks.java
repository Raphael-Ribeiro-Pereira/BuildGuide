package brentmaas.buildguide.fabric.place;

import brentmaas.buildguide.common.FillTask;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Fill (FillTask) in the client: its commands go out on every client tick, and while a fill runs the server's
 * answers to its /fill commands are counted and kept out of the chat (a large shape would print thousands of
 * "Successfully filled" lines). An answer is recognised by its translation key (commands.fill.*), also when it
 * comes as an error (red: the key in the first sibling); every other message passes, errors included.
 */
public final class FillHooks {
	private static final String fillKeys = "commands.fill.", success = "commands.fill.success";

	private FillHooks() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if(mc.player == null) FillTask.stop(); // left the world
			FillTask.tick();
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !consume(message));
	}

	// Whether the message is an answer to the running fill (counted, kept out of the chat)
	static boolean consume(Component message) {
		if(!FillTask.isRunning()) return false;
		TranslatableContents answer = fillAnswer(message);
		if(answer == null) return false;
		boolean ok = answer.getKey().equals(success);
		return FillTask.answer(ok, ok ? count(answer) : 0);
	}

	private static TranslatableContents fillAnswer(Component message) {
		if(message.getContents() instanceof TranslatableContents t && t.getKey().startsWith(fillKeys)) return t;
		for(Component sibling: message.getSiblings()) {
			if(sibling.getContents() instanceof TranslatableContents t && t.getKey().startsWith(fillKeys)) return t;
		}
		return null;
	}

	// "Successfully filled %s block(s)": the number
	private static int count(TranslatableContents answer) {
		Object[] args = answer.getArgs();
		if(args.length == 0) return 0;
		String text = args[0] instanceof Component c ? c.getString() : String.valueOf(args[0]);
		try {
			return Integer.parseInt(text.trim());
		}catch(NumberFormatException e) {
			return 0;
		}
	}
}
