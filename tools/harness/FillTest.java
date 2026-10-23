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
// late answer. Shape.cubeRgb. The one filter: the preview shows what the world's Part and Layer show. Water
// (2026-10-22): the farm's holes validate as built with a water source in them (a water block or a waterlogged
// one), Fill puts water in them (also into a dry slab), from a stock of WATER; and nothing goes to the chat: the
// Shape tab shows SmartFill.status().

public class FillTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// A world: solid, replaceable and air cells by position, an unloaded region and a build height
	// water: flowing water (replaceable, not a source); sources: water blocks; slabs: dry, can take water; wetSlabs: waterlogged
	static class World implements IBlockProbe {
		final Set<String> solid = new HashSet<>(), water = new HashSet<>(), sources = new HashSet<>(), slabs = new HashSet<>(), wetSlabs = new HashSet<>(); int unloadedFromX = Integer.MAX_VALUE, minY = -64, maxY = 319;
		static String k(int x, int y, int z){ return x + "," + y + "," + z; }
		public boolean isLoaded(int x, int y, int z){ return x < unloadedFromX; }
		public int flags(int x, int y, int z){ String p = k(x, y, z); if(solid.contains(p)) return FLAG_SOLID; if(slabs.contains(p)) return FLAG_SOLID | FLAG_WATERLOGGABLE; if(wetSlabs.contains(p)) return FLAG_SOLID | FLAG_WATER; if(sources.contains(p)) return FLAG_REPLACEABLE | FLAG_WATER; if(water.contains(p)) return FLAG_REPLACEABLE; return FLAG_AIR; }
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
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FillTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> { if(m.getName().contains("Chat")) chat.add(String.valueOf(x[0])); return null; });
		check(Arrays.stream(ILogHandler.class.getMethods()).noneMatch(m -> m.getName().toLowerCase().contains("chat")), "the log handler has no way to the chat any more");
		long[] now = {1000}; SmartFill.clock = () -> now[0];
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
		check(started && SmartFill.isRunning() && SmartFill.status().equals("screen.buildguide.smartfill.running[0%, 0, " + plan.groups.get(reliefGroup).cells.size() + "]"), "Start with only the relief chosen: only its cells go; the Shape tab reads " + SmartFill.status());
		check(SmartFill.title().equals("screen.buildguide.smartfill.stop[0%]"), "the button reads Stop 0%");
		SmartFill.tick();
		boolean onlyAndesite = true; for(String[] c: batches.get(0)) if(!Arrays.equals(c, new String[]{"minecraft:andesite"})) onlyAndesite = false;
		check(batches.size() == 1 && batches.get(0).length == Math.min(SmartFill.blocksPerTick, plan.groups.get(reliefGroup).cells.size()) && onlyAndesite, "one tick: one batch of at most " + SmartFill.blocksPerTick + ", andesite each");
		runToEnd();
		int reliefCells = plan.groups.get(reliefGroup).cells.size();
		check(!SmartFill.isRunning() && stock.get("minecraft:andesite") == 0 && SmartFill.status().startsWith("screen.buildguide.smartfill.done[50, " + reliefCells + ", ") && SmartFill.status().contains("smartfill.short[" + (reliefCells - 50) + "]") && !SmartFill.status().contains("taken"), "50 andesite for " + reliefCells + " cells: 50 placed, the rest ran out, only that said: " + SmartFill.status());
		now[0] += SmartFill.resultMillis;
		check(SmartFill.status() == null && SmartFill.lastResult() != null && chat.isEmpty(), "20 s later the Shape tab says nothing more; nothing ever went to the chat");
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
		check(!SmartFill.isRunning() && SmartFill.status().startsWith("screen.buildguide.smartfill.stopped["), "Stop: the Shape tab says how far (" + SmartFill.status() + ")");
		batches.clear();
		SmartFill.start(both); SmartFill.tick(); Consumer<byte[]> mine = pending; byte[] mineCodes = pendingCodes;
		late.accept(lateCodes); SmartFill.tick();
		check(batches.size() == 1, "a new fill: the answer the stopped one left behind is not taken for its own batch's");
		mine.accept(mineCodes); SmartFill.tick();
		check(batches.size() == 2, "its own answer is");
		answerNow = true; pending.accept(pendingCodes); // the batch still waiting, then the rest at once
		runToEnd();
		check(!SmartFill.isRunning() && SmartFill.status().equals("screen.buildguide.smartfill.done[" + both.cells.size() + ", " + both.cells.size() + ", ]"), "the cuboid filled: " + SmartFill.status());
		SmartFill.Plan none = SmartFill.prepare(cuboidSet); SmartFill.clear(none, 0);
		check(!SmartFill.start(none) && !SmartFill.isRunning() && chat.isEmpty(), "no colour has a block: nothing starts (the panel says so)");

		System.out.println("-- water: the farm's holes");
		ShapeFarmingLand farm = FocusWorldTest.place(new ShapeFarmingLand());
		set(farm, "propertyCrop", ShapeFarmingLand.Crop.SUGAR_CANE); set(farm, "propertyWidthX", 9); set(farm, "propertyWidthZ", 9);
		farm.update(); FocusWorldTest.settle(farm);
		java.util.function.LongPredicate wet = farm.waterTest();
		List<Long> holes = new ArrayList<>(); boolean sameAsParts = true;
		for(long p: farm.getExpectedBlocks()){ boolean isHole = farm.partOf(LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p)) == FocusFilter.Part.WATER; if(isHole) holes.add(p); if(wet.test(p) != isHole) sameAsParts = false; }
		check(holes.size() >= 4 && sameAsParts && new ShapeCuboid().waterTest() == null, "the farm wants water exactly in its water cells (" + holes.size() + "); other shapes want none");
		check(LayerPalette.classify(null, wet, LocalPos.unpackX(holes.get(0)), 0, LocalPos.unpackZ(holes.get(0)), IBlockProbe.FLAG_REPLACEABLE | IBlockProbe.FLAG_WATER, LayerPalette.NONE) == ValidationState.OK
			&& LayerPalette.classify(null, wet, LocalPos.unpackX(holes.get(0)), 0, LocalPos.unpackZ(holes.get(0)), IBlockProbe.FLAG_SOLID | IBlockProbe.FLAG_WATER, LayerPalette.NONE) == ValidationState.OK
			&& LayerPalette.classify(null, wet, LocalPos.unpackX(holes.get(0)), 0, LocalPos.unpackZ(holes.get(0)), IBlockProbe.FLAG_SOLID, LayerPalette.NONE) == ValidationState.MISSING
			&& LayerPalette.classify(null, wet, LocalPos.unpackX(holes.get(0)), 0, LocalPos.unpackZ(holes.get(0)), IBlockProbe.FLAG_AIR, LayerPalette.NONE) == ValidationState.MISSING, "a hole: built with a water block or a waterlogged slab; missing while dry, also under dirt");
		long edge = -1; for(long p: farm.getExpectedBlocks()) if(!wet.test(p)){ edge = p; break; }
		check(LayerPalette.classify(null, wet, LocalPos.unpackX(edge), LocalPos.unpackY(edge), LocalPos.unpackZ(edge), IBlockProbe.FLAG_SOLID, LayerPalette.NONE) == ValidationState.OK && LayerPalette.classify(null, wet, LocalPos.unpackX(edge), LocalPos.unpackY(edge), LocalPos.unpackZ(edge), IBlockProbe.FLAG_REPLACEABLE | IBlockProbe.FLAG_WATER, LayerPalette.NONE) == ValidationState.MISSING, "the edge: a solid block as before; water there is still missing");
		World pond = new World(); long h0 = holes.get(0), h1 = holes.get(1), h2 = holes.get(2), h3 = holes.get(3);
		pond.sources.add(World.k(LocalPos.unpackX(h0), 64, LocalPos.unpackZ(h0))); pond.slabs.add(World.k(LocalPos.unpackX(h1), 64, LocalPos.unpackZ(h1))); pond.solid.add(World.k(LocalPos.unpackX(h2), 64, LocalPos.unpackZ(h2))); pond.wetSlabs.add(World.k(LocalPos.unpackX(h3), 64, LocalPos.unpackZ(h3)));
		for(long p: farm.getExpectedBlocks()) if(!wet.test(p)) pond.solid.add(World.k(LocalPos.unpackX(p), 64 + LocalPos.unpackY(p), LocalPos.unpackZ(p)));
		ValidationState vs = new ValidationState(); vs.setWater(wet); vs.requestScan(0); vs.consumeScanRequest();
		SliceScan scan = SliceScan.prepare(vs, new ArrayList<>(farm.getExpectedBlocks()), 0, 64, 0, 1, vs.getScanEpoch(), null, new ArrayList<>(), false); scan.step(pond, 0, () -> 0L); scan.publish(pond);
		check(vs.getStatus(h0) == ValidationState.OK && vs.getStatus(h3) == ValidationState.OK && vs.getStatus(h1) == ValidationState.MISSING && vs.getStatus(h2) == ValidationState.MISSING && vs.getMissing() == holes.size() - 2 && vs.getStatus(edge) == ValidationState.OK, "a scan: the water block and the wet slab built, the dry slab and the dirt missing, every other hole missing, the edge built (missing " + vs.getMissing() + ")");
		vs.updateBlock(h1, false, true, false, true, LayerPalette.NONE, "Oak Slab");
		check(vs.getStatus(h1) == ValidationState.OK, "the slab waterlogged (a block event with water): built");
		vs.updateBlock(h0, true, false, false, false, LayerPalette.NONE, null);
		check(vs.getStatus(h0) == ValidationState.MISSING, "the water picked up: missing again");
		List<Long> waterCells = FillPlan.cells(farm.getExpectedBlocks(), 0, 64, 0, new ArrayList<>(), null, pond, new double[]{LocalPos.unpackX(h1) + 0.2, 64, LocalPos.unpackZ(h1) + 0.2, LocalPos.unpackX(h1) + 0.8, 65.8, LocalPos.unpackZ(h1) + 0.8}, wet);
		check(waterCells.size() == holes.size() - 3 && waterCells.contains(h1) && !waterCells.contains(h0) && !waterCells.contains(h2) && !waterCells.contains(h3), "Fill's holes: the dry ones and the dry slab (even with the player in it), not the water, the wet slab or the dirt (" + waterCells.size() + ")");
		List<Group> wetGroups = SmartFillPlan.groups(new ArrayList<>(farm.getExpectedBlocks()), p -> 0x3F76E4, wet, null);
		check(wetGroups.size() == 2 && wetGroups.stream().filter(g -> g.water).count() == 1 && wetGroups.stream().filter(g -> g.water).findFirst().get().cells.size() == holes.size(), "the same colour with and without water: two groups, the water one of its own");

		System.out.println("-- water: Fill");
		world = pond; ShapeSet farmSet = setOf(farm);
		SmartFill.Plan farmPlan = SmartFill.prepare(farmSet);
		Group waterGroup = farmPlan.groups.stream().filter(g -> g.water).findFirst().orElse(null);
		check(farmPlan.message == null && waterGroup != null && waterGroup.cells.size() == holes.size() - 3 && waterGroup.blocks.equals(Arrays.asList(SmartFill.WATER)), "the farm's plan: a water group (" + (waterGroup == null ? 0 : waterGroup.cells.size()) + " holes) filled with water");
		int wi = farmPlan.groups.indexOf(waterGroup);
		SmartFill.toggle(farmPlan, wi, "minecraft:stone"); SmartFill.clear(farmPlan, wi); SmartFill.fitToStock(farmPlan, new HashMap<>(), held);
		check(waterGroup.blocks.equals(Arrays.asList(SmartFill.WATER)), "no block can be chosen for it, Clear and the stock leave the water");
		stock.clear(); stock.put(SmartFill.WATER, 2); batches.clear();
		for(Group g: farmPlan.groups) if(!g.water) SmartFill.clear(farmPlan, farmPlan.groups.indexOf(g));
		SmartFill.start(farmPlan); runToEnd();
		boolean onlyWater = true; for(String[][] b: batches) for(String[] c: b) if(!Arrays.equals(c, new String[]{SmartFill.WATER})) onlyWater = false;
		check(onlyWater && stock.get(SmartFill.WATER) == 0 && SmartFill.lastResult().startsWith("screen.buildguide.smartfill.done[2, " + waterGroup.cells.size() + ", ") && SmartFill.lastResult().contains("short[" + (waterGroup.cells.size() - 2) + "]"), "two buckets' worth for " + waterGroup.cells.size() + " holes: each asks for water, 2 filled, the rest ran out: " + SmartFill.lastResult());
		world = new World();

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
		boolean all2 = true; for(String key: new String[]{"title", "colours", "carried", "none", "mix", "enough", "nocarried", "hint", "clear", "back", "go", "singleplayer", "noinventory", "noblocks", "stop", "running", "stopped", "done", "taken", "unloaded", "short", "water", "waterendless", "watercarried", "nowater", "nowaterhint"}) if(!lang.contains("\"screen.buildguide.smartfill." + key + "\"")) { all2 = false; System.out.println("   missing " + key); }
		check(lang.contains("\"screen.buildguide.preset.notwritten\"") && !lang.contains("\"screen.buildguide.smartfill.start\""), "the preset's problem is a text of the screen; the chat's start line is gone");
		check(all2 && !lang.contains("fill.nocommands") && !lang.contains("fill.confirm"), "every smart fill text is there, the /fill ones are gone");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
