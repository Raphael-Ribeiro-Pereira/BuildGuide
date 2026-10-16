import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.FillTask;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
// Fill with the block in hand (2026-10-16, wip/fill): the cells (what Area 3 could target: shown, not excluded,
// loaded, empty, not inside the player; inside the build height), the boxes (an exact cover, at most 32768
// blocks each, few commands for solid shapes), the command text, and FillTask: two clicks, the refusals (no
// block, no commands, generating, nothing to fill), 20 commands per tick, the answers counted and kept out of
// the chat, the summary, Stop, the confirmation running out.
public class FillTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// A world: solid, replaceable and air cells by position, an unloaded region and a build height
	static class World implements IBlockProbe {
		final Set<String> solid = new HashSet<>(), water = new HashSet<>(); int unloadedFromX = Integer.MAX_VALUE, minY = -64, maxY = 319;
		static String k(int x, int y, int z){ return x + "," + y + "," + z; }
		public boolean isLoaded(int x, int y, int z){ return x < unloadedFromX; }
		public int flags(int x, int y, int z){ String p = k(x, y, z); if(solid.contains(p)) return FLAG_SOLID; if(water.contains(p)) return FLAG_REPLACEABLE; return FLAG_AIR; }
		public String name(int x, int y, int z){ return "test"; }
		@Override public boolean isInBuildHeight(int y){ return y >= minY && y <= maxY; }
	}
	static Set<Long> cuboid(int x1, int y1, int z1, int x2, int y2, int z2){ Set<Long> s = new HashSet<>(); for(int x = x1; x <= x2; x++) for(int y = y1; y <= y2; y++) for(int z = z1; z <= z2; z++) s.add(LocalPos.pack(x, y, z)); return s; }
	// The boxes cover exactly the cells, without overlap, none above the limit
	static String cover(List<Long> cells, List<int[]> boxes, int max){
		Set<Long> want = new HashSet<>(cells), got = new HashSet<>();
		for(int[] b: boxes){
			if(FillPlan.volume(b) > max) return "a box of " + FillPlan.volume(b);
			for(int x = b[0]; x <= b[3]; x++) for(int y = b[1]; y <= b[4]; y++) for(int z = b[2]; z <= b[5]; z++) if(!got.add(LocalPos.pack(x, y, z))) return "overlap at " + x + "," + y + "," + z;
		}
		return got.equals(want) ? "" : "covers " + got.size() + " of " + want.size() + (got.containsAll(want) ? " (extra cells)" : " (missing cells)");
	}

	static List<String> chat = new ArrayList<>(), sentCommands = new ArrayList<>();
	static String held = "minecraft:stone"; static boolean mayRun = true, connected = true; static World world = new World(); static double[] player = null;

	@SuppressWarnings("unchecked")
	public static void main(String[] a) throws Exception {
		System.out.println("-- FillPlan.cells: what Area 3 could target, inside the build height");
		World w = new World();
		Set<Long> expected = cuboid(0, 0, 0, 3, 0, 3); // 16 cells, origin (100, 64, 100)
		w.solid.add(World.k(100, 64, 100)); w.water.add(World.k(101, 64, 100));
		List<Long> cells = FillPlan.cells(expected, 100, 64, 100, new ArrayList<>(), null, w, null);
		check(cells.size() == 15 && !cells.contains(LocalPos.pack(0, 0, 0)) && cells.contains(LocalPos.pack(1, 0, 0)), "a solid cell is skipped, water (replaceable) and air are filled: 15 of 16");
		List<int[]> ex = new ArrayList<>(); ex.add(new int[]{2, 0, 0, 3, 0, 3});
		check(FillPlan.cells(expected, 100, 64, 100, ex, null, w, null).size() == 7, "an exclusion box (local) takes its 8 cells out: 7");
		check(FillPlan.cells(expected, 100, 64, 100, new ArrayList<>(), p -> LocalPos.unpackZ(p) == 0, w, null).size() == 3, "the focus test: only z = 0, minus the solid one: 3");
		w.unloadedFromX = 102;
		check(FillPlan.cells(expected, 100, 64, 100, new ArrayList<>(), null, w, null).size() == 7, "an unloaded chunk (x >= 102) is never filled: 7");
		w.unloadedFromX = Integer.MAX_VALUE;
		check(FillPlan.cells(expected, 100, 64, 100, new ArrayList<>(), null, w, new double[]{101.2, 64, 101.2, 101.8, 65.8, 101.8}).size() == 14, "the cell the player stands in is skipped: 14");
		w.maxY = 63;
		check(FillPlan.cells(expected, 100, 64, 100, new ArrayList<>(), null, w, null).isEmpty(), "above the build height: nothing");
		w.maxY = 319;

		System.out.println("-- FillPlan.boxes: an exact cover, few commands");
		List<Long> solid = new ArrayList<>(cuboid(0, 0, 0, 9, 9, 9));
		List<int[]> boxes = FillPlan.boxes(solid, FillPlan.maxVolume);
		check(cover(solid, boxes, FillPlan.maxVolume).isEmpty() && boxes.size() == 1, "a 10^3 cuboid: one box (" + boxes.size() + ")");
		List<Long> big = new ArrayList<>(cuboid(0, 0, 0, 39, 39, 39));
		boxes = FillPlan.boxes(big, FillPlan.maxVolume);
		check(cover(big, boxes, FillPlan.maxVolume).isEmpty() && boxes.size() <= 4, "a 40^3 cuboid (64000 blocks): within the 32768 limit, " + boxes.size() + " boxes " + cover(big, boxes, FillPlan.maxVolume));
		List<Long> sphere = new ArrayList<>(); for(int x = -20; x <= 20; x++) for(int y = -20; y <= 20; y++) for(int z = -20; z <= 20; z++) if(x * x + y * y + z * z <= 400) sphere.add(LocalPos.pack(x, y, z));
		boxes = FillPlan.boxes(sphere, FillPlan.maxVolume);
		check(cover(sphere, boxes, FillPlan.maxVolume).isEmpty() && boxes.size() * 20 < sphere.size(), "a solid sphere r = 20 (" + sphere.size() + " blocks): " + boxes.size() + " boxes");
		Random r = new Random(7); List<Long> scattered = new ArrayList<>(); Set<Long> seen = new HashSet<>();
		for(int i = 0; i < 3000; i++){ long p = LocalPos.pack(r.nextInt(30) - 15, r.nextInt(12) - 6, r.nextInt(30) - 15); if(seen.add(p)) scattered.add(p); }
		boxes = FillPlan.boxes(scattered, FillPlan.maxVolume);
		check(cover(scattered, boxes, FillPlan.maxVolume).isEmpty(), "random cells (" + scattered.size() + "): still an exact cover, " + boxes.size() + " boxes");
		boxes = FillPlan.boxes(big, 100);
		check(cover(big, boxes, 100).isEmpty(), "a small limit (100): no box above it, still exact (" + boxes.size() + " boxes)");
		check(FillPlan.boxes(new ArrayList<>(), FillPlan.maxVolume).isEmpty(), "no cells: no box");
		check(FillPlan.command(new int[]{-2, 0, 3, 5, 1, 3}, 100, 64, -50, "minecraft:stone").equals("fill 98 64 -47 105 65 -47 minecraft:stone replace #minecraft:replaceable"), "the command: world coordinates, the block, replace only #minecraft:replaceable");

		System.out.println("-- FillTask: the button");
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k + Arrays.toString(v); } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FillTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> { if(m.getName().equals("sendChatMessage")) chat.add((String) x[0]); return null; });
		File dir = Files.createTempDirectory("bgfill").toFile(); BuildGuide.config = new Config(dir);
		TimingLog.sink = line -> {};
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(FillTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> {
			switch(m.getName()){
			case "newBuffer": return new FocusWorldTest.RecBuffer();
			case "getPlayerPosition": return new ShapeSet.Origin(0, 64, 0);
			case "heldBlockId": return held;
			case "mayRunCommands": return mayRun;
			case "worldProbe": return world;
			case "playerBox": return player;
			case "sendCommand": if(!connected) return false; sentCommands.add((String) x[0]); return true;
			default: return m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null;
			}
		});
		long[] now = {1000}; FillTask.clock = () -> now[0];
		ShapeCuboid shape = FocusWorldTest.place(new ShapeCuboid());
		shape.update(); FocusWorldTest.settle(shape);
		Field setField = Shape.class.getDeclaredField("shapeSet"); setField.setAccessible(true); ShapeSet set = (ShapeSet) setField.get(shape);
		// The set is made without its constructor (OriginScanTest.newSet): no exclusion boxes, as a new set has none enabled
		Field boxesField = ShapeSet.class.getDeclaredField("exclusionBoxes"); boxesField.setAccessible(true); boxesField.set(set, Array.newInstance(boxesField.getType().getComponentType(), 0));
		int total = shape.getNumberOfBlocks();
		check(total > 0 && FillTask.title().equals("screen.buildguide.fill"), "a cuboid of " + total + " blocks; the title: Fill");
		held = null; FillTask.click(set);
		check(chat.size() == 1 && chat.get(0).contains("screen.buildguide.holdablock") && !FillTask.isRunning(), "no block in hand: the chat says so, nothing runs");
		held = "minecraft:stone"; mayRun = false; chat.clear(); FillTask.click(set);
		check(chat.size() == 1 && chat.get(0).contains("fill.nocommands"), "no right to run commands: the chat says so");
		mayRun = true; chat.clear();
		shape.lock.lock(); Thread t = new Thread(() -> FillTask.click(set)); t.start(); t.join(); shape.lock.unlock();
		check(chat.size() == 1 && chat.get(0).contains("fill.generating"), "the shape generating (its lock taken): the chat says so");
		chat.clear(); FillTask.click(set);
		check(chat.isEmpty() && FillTask.title().equals("screen.buildguide.fill.confirm[" + total + "]") && sentCommands.isEmpty(), "first click: Fill " + total + "? and nothing sent");
		now[0] += FillTask.confirmMillis;
		check(FillTask.title().equals("screen.buildguide.fill"), "after 5 s the confirmation runs out: Fill again");
		FillTask.click(set); now[0] += 1000; FillTask.click(set);
		check(FillTask.isRunning() && FillTask.title().equals("screen.buildguide.fill.stop") && chat.size() == 1 && chat.get(0).startsWith("[Build Guide] screen.buildguide.fill.start[" + total + ", minecraft:stone, "), "a second click within 5 s starts: Stop, and the chat says how much: " + chat);
		int commands = Integer.parseInt(chat.get(0).replaceAll(".*, (\\d+)\\]$", "$1"));
		FillTask.tick();
		check(sentCommands.size() == Math.min(commands, FillTask.commandsPerTick), "one tick: at most 20 commands (" + sentCommands.size() + " of " + commands + ")");
		while(FillTask.isRunning() && sentCommands.size() < commands) FillTask.tick();
		check(sentCommands.size() == commands && sentCommands.get(0).startsWith("fill ") && sentCommands.get(0).endsWith(" minecraft:stone replace #minecraft:replaceable"), "every command sent: " + sentCommands.get(0));
		check(FillTask.isRunning(), "still running: waiting for the answers");
		int sum = 0; boolean belongs = true;
		for(int i = 0; i < commands; i++){ boolean ok = i > 0; int n = ok ? 10 : 0; sum += n; belongs &= FillTask.answer(ok, n); }
		check(belongs, "every answer while the fill runs belongs to it (kept out of the chat)");
		FillTask.tick();
		check(!FillTask.isRunning() && chat.size() == 2 && chat.get(1).contains("fill.done[" + sum + ", " + total + ", minecraft:stone, " + commands + ", 1]"), "every answer in: the summary (placed, of, block, commands, failed): " + chat.get(1));
		check(!FillTask.answer(true, 5), "an answer after the end is not the fill's: it reaches the chat");

		System.out.println("-- FillTask: Stop, no answers, connection lost, the focus, nothing to fill");
		// Every other cell solid: one box per cell, more commands than one tick sends
		ShapeCuboid large = FocusWorldTest.place(new ShapeCuboid());
		for(String f: new String[]{"propertyX", "propertyY", "propertyZ"}){ Field pf = ShapeCuboid.class.getDeclaredField(f); pf.setAccessible(true); ((brentmaas.buildguide.common.property.Property<Object>) pf.get(large)).value = 8; }
		large.update(); FocusWorldTest.settle(large);
		ShapeSet bigSet = (ShapeSet) setField.get(large); boxesField.set(bigSet, Array.newInstance(boxesField.getType().getComponentType(), 0));
		World checker = new World();
		for(long p: large.getExpectedBlocks()){ int x = LocalPos.unpackX(p), y = LocalPos.unpackY(p), z = LocalPos.unpackZ(p); if(((x + y + z) & 1) == 0) checker.solid.add(World.k(x, 64 + y, z)); }
		world = checker; chat.clear(); sentCommands.clear();
		FillTask.click(bigSet); FillTask.click(bigSet);
		int many = Integer.parseInt(chat.get(0).replaceAll(".*, (\\d+)\\]$", "$1"));
		FillTask.tick(); FillTask.click(bigSet);
		check(many > FillTask.commandsPerTick && !FillTask.isRunning() && chat.size() == 2 && chat.get(1).contains("fill.stopped[" + FillTask.commandsPerTick + ", " + many + "]") && sentCommands.size() == FillTask.commandsPerTick, "Stop after one tick: nothing more is sent, the chat says how far (" + chat.get(1) + ")");
		FillTask.tick();
		check(sentCommands.size() == FillTask.commandsPerTick, "and a tick after Stop sends nothing");
		world = new World(); chat.clear(); sentCommands.clear();
		FillTask.click(set); FillTask.click(set);
		while(FillTask.isRunning() && sentCommands.size() < commands) FillTask.tick();
		FillTask.tick();
		check(FillTask.isRunning(), "all sent, no answer yet: still waiting");
		now[0] += FillTask.answerMillis; FillTask.tick();
		check(!FillTask.isRunning() && chat.get(chat.size() - 1).contains("fill.sent[" + commands + ", " + total + ", minecraft:stone]"), "no answers at all (command feedback off): ends 5 s after the last command and says what was sent: " + chat.get(chat.size() - 1));
		chat.clear(); sentCommands.clear(); FillTask.click(set); FillTask.click(set); connected = false; FillTask.tick();
		check(!FillTask.isRunning() && sentCommands.isEmpty() && chat.get(chat.size() - 1).contains("fill.stopped[0, "), "the connection gone: the fill stops");
		connected = true;
		BuildGuide.config.focusFrom.value = 0;
		shape.setFocus(FocusFilter.Part.HIDDEN, 0); FocusWorldTest.settle(shape);
		chat.clear(); FillTask.click(set);
		check(chat.size() == 1 && chat.get(0).contains("fill.nothing"), "Part Hidden: the world shows nothing of the shape, so nothing to fill");
		shape.setFocus(FocusFilter.Part.ALL, 0); FocusWorldTest.settle(shape);
		for(long p: shape.getExpectedBlocks()) world.solid.add(World.k(LocalPos.unpackX(p), 64 + LocalPos.unpackY(p), LocalPos.unpackZ(p)));
		chat.clear(); FillTask.click(set);
		check(chat.size() == 1 && chat.get(0).contains("fill.nothing") && FillTask.title().equals("screen.buildguide.fill"), "every cell taken: nothing to fill, the chat says so");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
