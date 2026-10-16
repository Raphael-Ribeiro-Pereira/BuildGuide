package brentmaas.buildguide.common;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.LongSupplier;

import brentmaas.buildguide.common.shape.FillPlan;
import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;

/**
 * Fill with the block in hand (the Fill button of the shape screen): FillPlan finds the empty cells the world
 * shows and their boxes, then the /fill commands go out a few per client tick through the loader
 * (IShapeHandler.sendCommand), and the server's answers are counted (the loader keeps them out of the chat while
 * a fill expects them). One fill at a time; client thread only.
 *
 * The button takes two clicks: the first plans and shows "Fill N?", a second within confirmMillis starts (planned
 * again, the world may have changed); while a fill runs the button stops it. Nothing happens without a block in
 * the main hand, without the right to run commands (cheats or op) or while the shape is generating: the chat
 * says why.
 */
public final class FillTask {
	public static final int commandsPerTick = 20;
	public static final long confirmMillis = 5000;
	// After the last command, its answers are still waited for (and kept out of the chat) this long
	public static final long answerMillis = 5000;
	public static LongSupplier clock = System::currentTimeMillis;

	private static final ArrayDeque<String> queue = new ArrayDeque<String>();
	private static boolean running = false;
	private static String block = null;
	private static int cells = 0, commands = 0, sent = 0, answered = 0, placed = 0, failed = 0;
	private static long lastSentAt = 0;
	// The first click's plan: its cell count and when, -1 when not armed
	private static long armedAt = -1;
	private static int armedCells = 0;

	private FillTask() {}

	// The button's title now: "Fill", "Fill N?" (armed), "Stop" (running)
	public static String title() {
		if(running) return BuildGuide.screenHandler.translate("screen.buildguide.fill.stop");
		if(isArmed()) return BuildGuide.screenHandler.translate("screen.buildguide.fill.confirm", "" + armedCells);
		return BuildGuide.screenHandler.translate("screen.buildguide.fill");
	}

	private static boolean isArmed() {
		return armedAt >= 0 && clock.getAsLong() - armedAt < confirmMillis;
	}

	// The Fill button: stop a running fill, else plan and arm, else (armed) start
	public static void click(ShapeSet set) {
		if(running) {
			stop();
			return;
		}
		boolean confirm = isArmed();
		armedAt = -1;
		if(set == null || set.getShape() == null) return;
		String held = BuildGuide.shapeHandler.heldBlockId();
		if(held == null) {
			chat("screen.buildguide.holdablock");
			return;
		}
		if(!BuildGuide.shapeHandler.mayRunCommands()) {
			chat("screen.buildguide.fill.nocommands");
			return;
		}
		IBlockProbe probe = BuildGuide.shapeHandler.worldProbe();
		if(probe == null) return;
		Shape shape = set.getShape();
		if(!shape.lock.tryLock()) {
			chat("screen.buildguide.fill.generating");
			return;
		}
		List<Long> found;
		try {
			if(!shape.ready || shape.error) {
				chat("screen.buildguide.fill.generating");
				return;
			}
			found = FillPlan.cells(shape.getExpectedBlocks(), set.getOriginX(), set.getOriginY(), set.getOriginZ(), set.getActiveExclusionBoxes(), shape.worldFocusTest(), probe, BuildGuide.shapeHandler.playerBox());
		}finally {
			shape.lock.unlock();
		}
		if(found.isEmpty()) {
			chat("screen.buildguide.fill.nothing");
			return;
		}
		if(!confirm) {
			armedAt = clock.getAsLong();
			armedCells = found.size();
			return;
		}
		queue.clear();
		for(int[] box: FillPlan.boxes(found, FillPlan.maxVolume)) queue.add(FillPlan.command(box, set.getOriginX(), set.getOriginY(), set.getOriginZ(), held));
		running = true;
		block = held;
		cells = found.size();
		commands = queue.size();
		sent = 0;
		answered = 0;
		placed = 0;
		failed = 0;
		lastSentAt = clock.getAsLong();
		chat("screen.buildguide.fill.start", "" + cells, block, "" + commands);
	}

	// Every client tick (the loader): the next commands, then the end once every answer came or stopped coming
	public static void tick() {
		if(!running) return;
		for(int n = 0;n < commandsPerTick && !queue.isEmpty();++n) {
			if(!BuildGuide.shapeHandler.sendCommand(queue.peek())) { // no connection any more
				stop();
				return;
			}
			queue.poll();
			++sent;
			lastSentAt = clock.getAsLong();
		}
		if(queue.isEmpty() && (answered >= sent || clock.getAsLong() - lastSentAt >= answerMillis)) finish();
	}

	/**
	 * A server answer to a /fill: success with the blocks placed, or not (nothing was empty any more, or an
	 * error). Returns whether it belongs to the fill, i.e. stays out of the chat: only while one runs.
	 */
	public static boolean answer(boolean ok, int blocks) {
		if(!running) return false;
		++answered;
		if(ok) placed += blocks;
		else ++failed;
		return true;
	}

	public static boolean isRunning() {
		return running;
	}

	public static void stop() {
		if(!running) return;
		running = false;
		queue.clear();
		chat("screen.buildguide.fill.stopped", "" + sent, "" + commands);
	}

	// Without any answer (the world's sendCommandFeedback is off) only what was sent is known
	private static void finish() {
		running = false;
		if(answered == 0) chat("screen.buildguide.fill.sent", "" + commands, "" + cells, block);
		else chat("screen.buildguide.fill.done", "" + placed, "" + cells, block, "" + commands, "" + failed);
	}

	private static void chat(String key, Object... values) {
		BuildGuide.logHandler.sendChatMessage("[Build Guide] " + BuildGuide.screenHandler.translate(key, values));
	}

	// Offline harness only
	static void reset() {
		queue.clear();
		running = false;
		armedAt = -1;
	}
}
