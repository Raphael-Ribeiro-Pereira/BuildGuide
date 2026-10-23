import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.PreviewController;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.SmartFillPlan.Group;
// Fill (2026-10-16, wip/fill; smart fill 2026-10-22, wip/smartfill). FillPlan.cells: what Area 3 could target
// (shown, not excluded, loaded, empty, not inside the player; inside the build height). SmartFillPlan: build
// order section by section and bottom up, groups by the colour the world draws (at most 8), a mix spread evenly
// and the same block first for the same cell, the first block the player has, what the stock covers. SmartFill:
// the plan's refusals (another kind of world, generating, nothing), the choice fitted to what is carried and
// remembered by colour, the batches (32 a tick, one in flight), a group out of blocks, Stop, a stopped fill's
// late answer. Shape.cubeRgb. The one filter: the preview shows what the world's Part and Layer show.
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

	static List<String> chat = new ArrayList<>();
	static String held = "minecraft:stone"; static boolean singleplayer = true; static World world = new World();
	// The loader's side of a fill: what the player carries, the batches it got, and whether it answers at once
	static Map<String, Integer> stock = new HashMap<>(); static List<String[][]> batches = new ArrayList<>(); static List<int[]> batchCells = new ArrayList<>();
	static boolean answerNow = true; static Consumer<byte[]> pending = null; static byte[] pendingCodes = null;
	static byte[] place(String[][] candidates){
		byte[] codes = new byte[candidates.length];
		for(int i = 0; i < candidates.length; i++){
			String b = SmartFillPlan.firstAvailable(candidates[i], id -> stock.getOrDefault(id, 0) > 0);
			if(b == null){ codes[i] = SmartFill.NO_BLOCKS; continue; }
			stock.merge(b, -1, Integer::sum); codes[i] = SmartFill.PLACED;
		}
		return codes;
	}
	static ShapeSet setOf(Shape s) throws Exception {
		Field setField = Shape.class.getDeclaredField("shapeSet"); setField.setAccessible(true); ShapeSet set = (ShapeSet) setField.get(s);
		// The set is made without its constructor (OriginScanTest.newSet): no exclusion boxes, as a new set has none enabled
		Field boxesField = ShapeSet.class.getDeclaredField("exclusionBoxes"); boxesField.setAccessible(true); boxesField.set(set, Array.newInstance(boxesField.getType().getComponentType(), 0));
		return set;
	}
	@SuppressWarnings("unchecked") static void set(Shape s, String f, Object v) throws Exception { Field x = s.getClass().getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	static void runToEnd(){ for(int i = 0; i < 100000 && SmartFill.isRunning(); i++) SmartFill.tick(); }

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

		System.out.println("-- SmartFillPlan: build order, groups, mix, stock");
		List<Long> sphere = new ArrayList<>(); for(int x = -20; x <= 20; x++) for(int y = -20; y <= 20; y++) for(int z = -20; z <= 20; z++) if(x * x + y * y + z * z <= 400) sphere.add(LocalPos.pack(x, y, z));
		Collections.shuffle(sphere, new Random(3));
		SmartFillPlan.sortForBuilding(sphere, 7, 64, -3);
		boolean bandsUp = true, sectionsTogether = true, upInSection = true; Set<String> done = new HashSet<>(); String lastSection = null; int lastBand = Integer.MIN_VALUE, lastY = 0;
		for(long p: sphere){
			int x = 7 + LocalPos.unpackX(p), y = 64 + LocalPos.unpackY(p), z = -3 + LocalPos.unpackZ(p);
			String section = Math.floorDiv(x, 16) + "," + Math.floorDiv(y, 16) + "," + Math.floorDiv(z, 16);
			if(Math.floorDiv(y, 16) < lastBand) bandsUp = false;
			lastBand = Math.floorDiv(y, 16);
			if(!section.equals(lastSection)){ if(!done.add(section)) sectionsTogether = false; lastSection = section; lastY = y; }
			else { if(y < lastY) upInSection = false; lastY = y; }
		}
		check(bandsUp && sectionsTogether && upInSection, "a shuffled sphere (" + sphere.size() + " cells) sorted: section by section (each once, " + done.size() + " sections), bands of 16 bottom up, bottom up in each section");
		List<Long> row = new ArrayList<>(); for(int x = 0; x < 30; x++) row.add(LocalPos.pack(x, 0, 0));
		int[] groupOf = new int[row.size()];
		List<Group> two = SmartFillPlan.groups(row, p -> LocalPos.unpackX(p) % 3 == 0 ? 0x5B8DD9 : 0x123456, groupOf);
		check(two.size() == 2 && two.get(0).rgb == 0x5B8DD9 && two.get(0).cells.size() == 10 && two.get(1).cells.size() == 20 && groupOf[0] == 0 && groupOf[1] == 1 && groupOf[3] == 0, "two colours: two groups in the order met, every cell's group");
		List<Group> many = SmartFillPlan.groups(row, p -> LocalPos.unpackX(p), null);
		check(many.size() == SmartFillPlan.maxGroups && many.get(SmartFillPlan.maxGroups - 1).cells.size() == 30 - (SmartFillPlan.maxGroups - 1), "30 colours: 8 groups, the last takes the rest (" + many.get(7).cells.size() + " cells)");
		List<String> mix = Arrays.asList("a", "b", "c"); Map<String, Integer> firsts = new HashMap<>(); boolean same = true;
		for(long p: sphere){ String[] c = SmartFillPlan.candidates(p, mix); firsts.merge(c[0], 1, Integer::sum); if(!Arrays.equals(c, SmartFillPlan.candidates(p, mix)) || new HashSet<>(Arrays.asList(c)).size() != 3) same = false; }
		int minShare = Collections.min(firsts.values()), maxShare = Collections.max(firsts.values());
		check(same && firsts.size() == 3 && maxShare - minShare < sphere.size() / 30, "a mix of 3: every cell tries all three, the same order each time, each first for about a third (" + firsts + ")");
		check(SmartFillPlan.candidates(1L, new ArrayList<>()).length == 0 && SmartFillPlan.candidates(5L, Arrays.asList("x")).length == 1, "no block: no candidate; one block: that one");
		check("b".equals(SmartFillPlan.firstAvailable(new String[]{"a", "b", "c"}, id -> !id.equals("a"))) && SmartFillPlan.firstAvailable(new String[]{"a"}, id -> false) == null, "the first one the player has; none: null");
		List<Group> share = SmartFillPlan.groups(row, p -> LocalPos.unpackX(p) < 20 ? 1 : 2, null);
		share.get(0).blocks.add("stone"); share.get(1).blocks.add("stone"); share.get(1).blocks.add("dirt");
		Map<String, Integer> have = new HashMap<>(); have.put("stone", 25); have.put("dirt", 3);
		int[] cov = SmartFillPlan.coverage(share, have);
		check(cov[0] == 20 && cov[1] == 8, "coverage: 25 stone for 20 + 10 cells, the second group also has 3 dirt: 20 and 8 (" + Arrays.toString(cov) + ")");

		System.out.println("-- the colour of a cube (Shape.cubeRgb)");
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
			case "worldProbe": return world;
			case "canSmartFill": return singleplayer;
			case "carriedBlocks": return new HashMap<>(stock);
			case "placeBlocks": {
				int[] xyz = (int[]) x[0]; String[][] cand = (String[][]) x[1]; @SuppressWarnings("unchecked") Consumer<byte[]> doneCb = (Consumer<byte[]>) x[2];
				batches.add(cand); batchCells.add(xyz);
				byte[] codes = place(cand);
				if(answerNow) doneCb.accept(codes); else { pending = doneCb; pendingCodes = codes; }
				return true;
			}
			default: return m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null;
			}
		});
		ShapePolygonBuilding capsule = FocusWorldTest.place(new ShapePolygonBuilding());
		set(capsule, "propertyRelief", 2); capsule.update(); FocusWorldTest.settle(capsule);
		int blue = 0, plain = 0; for(long p: capsule.getExpectedBlocks()){ int rgb = capsule.cubeRgb(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)); if(rgb == ShapePolygonBuilding.reliefRgb) blue++; else if(rgb == 0) plain++; }
		check(blue > 0 && plain > 0 && blue + plain == capsule.getExpectedBlocks().size(), "the capsule: the relief blue (" + blue + "), the rest the shape's colour (" + plain + ")");
		ShapeCuboid cuboid = FocusWorldTest.place(new ShapeCuboid());
		for(String f: new String[]{"propertyX", "propertyY", "propertyZ"}) set(cuboid, f, 8); // more than one batch
		cuboid.update(); FocusWorldTest.settle(cuboid);
		boolean allShape = true; for(long p: cuboid.getExpectedBlocks()) if(cuboid.cubeRgb(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) != 0) allShape = false;
		check(allShape, "a cuboid: every cube the shape's colour");

		System.out.println("-- SmartFill: the plan");
		ShapeSet capsuleSet = setOf(capsule), cuboidSet = setOf(cuboid);
		singleplayer = false;
		check("screen.buildguide.smartfill.singleplayer".equals(SmartFill.prepare(capsuleSet).message), "not a world this game runs: the panel says singleplayer only");
		singleplayer = true;
		capsule.lock.lock(); SmartFill.Plan[] busy = new SmartFill.Plan[1]; Thread t = new Thread(() -> busy[0] = SmartFill.prepare(capsuleSet)); t.start(); t.join(); capsule.lock.unlock();
		check("screen.buildguide.fill.generating".equals(busy[0].message), "the shape generating (its lock taken): try again");
		SmartFill.Plan plan = SmartFill.prepare(capsuleSet);
		check(plan.message == null && plan.cells.size() == capsule.getExpectedBlocks().size() && plan.groups.size() == 2 && plan.groups.get(0).cells.size() + plan.groups.get(1).cells.size() == plan.cells.size(), "the capsule in an empty world: every cell, two colours (" + plan.groups.get(0).cells.size() + " + " + plan.groups.get(1).cells.size() + ")");
		int reliefGroup = plan.groups.get(0).rgb == ShapePolygonBuilding.reliefRgb ? 0 : 1, wallGroup = 1 - reliefGroup;
		stock.clear(); stock.put("minecraft:stone", 100000); stock.put("minecraft:andesite", 50);
		SmartFill.fitToStock(plan, stock, held);
		check(plan.groups.get(0).blocks.equals(Arrays.asList("minecraft:stone")) && plan.groups.get(1).blocks.equals(Arrays.asList("minecraft:stone")), "nothing chosen yet: each colour takes the block in hand, which is carried");
		SmartFill.toggle(plan, reliefGroup, "minecraft:andesite"); SmartFill.toggle(plan, reliefGroup, "minecraft:stone");
		check(plan.groups.get(reliefGroup).blocks.equals(Arrays.asList("minecraft:andesite")), "a click adds a block, another takes the stone out: the relief in andesite");
		SmartFill.toggle(plan, wallGroup, "minecraft:andesite");
		check(plan.groups.get(wallGroup).blocks.size() == 2, "two blocks: a mix");
		SmartFill.Plan again = SmartFill.prepare(capsuleSet);
		check(again.groups.get(reliefGroup).blocks.equals(Arrays.asList("minecraft:andesite")) && again.groups.get(wallGroup).blocks.size() == 2, "opened again: the choices come back by colour");
		Map<String, Integer> noAndesite = new HashMap<>(); noAndesite.put("minecraft:stone", 5);
		SmartFill.fitToStock(again, noAndesite, "minecraft:dirt");
		check(again.groups.get(reliefGroup).blocks.isEmpty() && again.groups.get(wallGroup).blocks.equals(Arrays.asList("minecraft:stone")), "andesite no longer carried: out of both, and the block in hand (dirt) is not carried: the relief has none");
		SmartFill.clear(plan, wallGroup);
		check(plan.groups.get(wallGroup).blocks.isEmpty(), "Clear empties a colour");

		System.out.println("-- SmartFill: running");
		chat.clear(); batches.clear(); batchCells.clear();
		boolean started = SmartFill.start(plan);
		check(started && SmartFill.isRunning() && chat.get(0).contains("smartfill.start[" + plan.groups.get(reliefGroup).cells.size() + "]"), "Start with only the relief chosen: only its cells go (" + chat + ")");
		check(SmartFill.title().equals("screen.buildguide.smartfill.stop[0%]"), "the button reads Stop 0%");
		SmartFill.tick();
		boolean onlyAndesite = true; for(String[] c: batches.get(0)) if(!Arrays.equals(c, new String[]{"minecraft:andesite"})) onlyAndesite = false;
		check(batches.size() == 1 && batches.get(0).length == Math.min(SmartFill.blocksPerTick, plan.groups.get(reliefGroup).cells.size()) && onlyAndesite, "one tick: one batch of at most " + SmartFill.blocksPerTick + ", andesite each");
		runToEnd();
		int reliefCells = plan.groups.get(reliefGroup).cells.size();
		check(!SmartFill.isRunning() && stock.get("minecraft:andesite") == 0 && chat.get(chat.size() - 1).contains("smartfill.done[50, " + reliefCells + ", 0, 0, " + (reliefCells - 50) + "]"), "50 andesite for " + reliefCells + " cells: 50 placed, the rest without blocks left, and the end says so: " + chat.get(chat.size() - 1));
		int sentAfterOut = 0; for(String[][] b: batches) sentAfterOut += b.length;
		check(sentAfterOut < reliefCells, "once the colour ran out its other cells are not sent (" + sentAfterOut + " sent of " + reliefCells + ")");
		int firstRelief = 0; while(plan.groupOf[firstRelief] != reliefGroup) firstRelief++;
		long firstLocal = plan.cells.get(firstRelief); int[] first = batchCells.get(0);
		check(first[0] == capsuleSet.getOriginX() + LocalPos.unpackX(firstLocal) && first[1] == capsuleSet.getOriginY() + LocalPos.unpackY(firstLocal) && first[2] == capsuleSet.getOriginZ() + LocalPos.unpackZ(firstLocal), "the cells go in world coordinates, in build order");
		System.out.println("-- SmartFill: a mix, an answer that comes later, Stop, a late answer");
		stock.put("minecraft:andesite", 100000); stock.put("minecraft:stone", 100000);
		SmartFill.Plan both = SmartFill.prepare(cuboidSet); SmartFill.fitToStock(both, stock, held); SmartFill.toggle(both, 0, "minecraft:andesite");
		batches.clear(); chat.clear(); answerNow = false;
		SmartFill.start(both); SmartFill.tick(); SmartFill.tick(); SmartFill.tick();
		check(batches.size() == 1, "while the batch waits for its answer, no other is sent");
		pending.accept(pendingCodes); SmartFill.tick();
		check(batches.size() == 2, "its answer read: the next batch goes");
		Map<String, Integer> firstBlocks = new HashMap<>(); for(String[] c: batches.get(0)) firstBlocks.merge(c[0], 1, Integer::sum);
		check(firstBlocks.size() == 2, "a mix: both blocks come first in the batch (" + firstBlocks + ")");
		Consumer<byte[]> late = pending; byte[] lateCodes = pendingCodes;
		SmartFill.stop();
		check(!SmartFill.isRunning() && chat.get(chat.size() - 1).contains("smartfill.stopped["), "Stop: the chat says how far (" + chat.get(chat.size() - 1) + ")");
		batches.clear();
		SmartFill.start(both); SmartFill.tick(); Consumer<byte[]> mine = pending; byte[] mineCodes = pendingCodes;
		late.accept(lateCodes); SmartFill.tick();
		check(batches.size() == 1, "a new fill: the answer the stopped one left behind is not taken for its own batch's");
		mine.accept(mineCodes); SmartFill.tick();
		check(batches.size() == 2, "its own answer is");
		answerNow = true; pending.accept(pendingCodes); // the batch still waiting, then the rest at once
		runToEnd();
		check(!SmartFill.isRunning() && chat.get(chat.size() - 1).contains("smartfill.done[" + both.cells.size() + ", " + both.cells.size() + ", 0, 0, 0]"), "the cuboid filled: " + chat.get(chat.size() - 1));
		SmartFill.Plan none = SmartFill.prepare(cuboidSet); SmartFill.clear(none, 0); chat.clear();
		check(!SmartFill.start(none) && chat.get(0).contains("smartfill.noblocks"), "no colour has a block: nothing starts, the chat says so");

		System.out.println("-- one filter: the preview shows what the world shows");
		BuildGuide.config.focusFrom.value = 0;
		PreviewController preview = new PreviewController(() -> 0L);
		PreviewModel all = preview.update(capsule);
		check(all != null && all.focusMask == null, "Part All: no mask, every block");
		capsule.setFocus(FocusFilter.Part.ROOF, FocusFilter.LAYER_ALL); FocusWorldTest.settle(capsule);
		PreviewModel roof = preview.update(capsule);
		int shown = 0, roofBlocks = 0; for(int i = 0; i < roof.positions.length; i++){ if(roof.shows(i)) shown++; long p = roof.positions[i]; if(capsule.partOf(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) == FocusFilter.Part.ROOF) roofBlocks++; }
		check(roof.focusMask != null && shown == roofBlocks && shown > 0 && shown < roof.positions.length && "focus".equals(PreviewModel.meshChange(all, roof)), "Part Roof: the preview shows the roof only (" + shown + " of " + roof.positions.length + "), and its mesh is made again");
		check(preview.update(capsule) == roof, "the next frame: the same view, nothing made again");
		SmartFill.Plan roofPlan = SmartFill.prepare(capsuleSet);
		check(roofPlan.cells.size() == roofBlocks, "and Fill fills the roof only (" + roofPlan.cells.size() + ")");
		capsule.setFocus(FocusFilter.Part.ALL, FocusFilter.LAYER_ALL); FocusWorldTest.settle(capsule);
		check(preview.update(capsule).focusMask == null, "Part All again: every block");
		boolean noStatusFilter = false; try { ShapeScreen.class.getDeclaredField("buttonFilter"); } catch(NoSuchFieldException e){ noStatusFilter = true; }
		check(noStatusFilter, "the Shape tab has no status filter button any more: Part and Layer are the one filter");

		System.out.println("-- texts");
		String lang = new String(Files.readAllBytes(new File("common/resources/assets/buildguide/lang/en_us.json").toPath()), "UTF-8");
		boolean all2 = true; for(String key: new String[]{"title", "colours", "carried", "none", "mix", "enough", "nocarried", "hint", "clear", "back", "go", "singleplayer", "noinventory", "noblocks", "stop", "start", "stopped", "done"}) if(!lang.contains("\"screen.buildguide.smartfill." + key + "\"")) { all2 = false; System.out.println("   missing " + key); }
		check(all2 && !lang.contains("fill.nocommands") && !lang.contains("fill.confirm"), "every smart fill text is there, the /fill ones are gone");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
