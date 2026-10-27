import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.FocusFilter.Part;
import brentmaas.buildguide.common.shape.IslandGeometry.*;
// Build focus, the pure core: the Island's parts (each block in exactly one, the union is the island, the
// geometry unchanged: Wall 1 to 3, the 4 Outlines, without spikes, attached and segmented), the filter (part x
// layer, Hidden, All), the threshold, the selectors saved at the end of the Island (old islands: All, All) and
// "Focus from" in buildguide.cfg.
public class FocusTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static Params params(Outline o, int wall, int spikes, BreakMode b){
		Params p = new Params(); p.outline = o; p.wall = wall; p.spikes = spikes; p.breakMode = b; p.seed = 7; p.spikeLength = 14; p.pieces = 3; p.gap = 2; return p;
	}
	static List<long[]> cells(Params p, IslandGeometry.Parts[] got) throws Exception {
		List<long[]> out = new ArrayList<>();
		IslandGeometry.enumerate(p, parts -> got[0] = parts, (x, y, z) -> out.add(new long[]{x, y, z}));
		return out;
	}

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FocusTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);

		System.out.println("-- the Island's parts");
		int cases = 0, badOrder = 0, badSpike = 0, badCap = 0, emptyCap = 0, emptyBody = 0, spikesWithout = 0, spikesMissing = 0;
		for(Outline o: Outline.values()) for(int wall = 1; wall <= 3; wall++) for(int mode = 0; mode < 3; mode++){
			Params p = params(o, wall, mode == 0 ? 0 : 12, mode == 2 ? BreakMode.SEGMENTED : BreakMode.ATTACHED);
			IslandGeometry.Parts[] got = new IslandGeometry.Parts[1];
			List<long[]> withParts = cells(p, got);
			List<long[]> plain = new ArrayList<>(); IslandGeometry.enumerate(p, (x, y, z) -> plain.add(new long[]{x, y, z}));
			// The same blocks in the same order with or without the parts callback (the GOLDENs see the second)
			boolean same = withParts.size() == plain.size(); for(int i = 0; same && i < plain.size(); i++) same = Arrays.equals(withParts.get(i), plain.get(i));
			if(!same || got[0] == null) badOrder++;
			// The body alone (no spikes): its own bottom per column, computed independently of Parts
			Params body = params(o, wall, 0, BreakMode.ATTACHED); int h = IslandGeometry.halfBox(body) + wall, n = 2 * h + 1;
			int[] bottom = IslandGeometry.columns(body, wall);
			Map<Part, Integer> count = new EnumMap<>(Part.class);
			for(long[] c: withParts){
				int x = (int) c[0], y = (int) c[1], z = (int) c[2];
				Part part = got[0].partOf(x, y, z);
				count.merge(part, 1, Integer::sum);
				int b = bottom[(x + h) * n + (z + h)];
				boolean below = b < 0 || y < -b;
				if((part == Part.SPIKES) != below) badSpike++;
				if(part == Part.CAP && !(y <= 0 && y >= -(wall - 1))) badCap++;
				if(part == Part.BODY && y >= -(wall - 1)) badCap++;
			}
			int sum = 0; for(int v: count.values()) sum += v;
			if(sum != withParts.size() || count.containsKey(Part.ALL) || count.containsKey(Part.HIDDEN)) badOrder++;
			if(count.getOrDefault(Part.CAP, 0) == 0) emptyCap++;
			if(count.getOrDefault(Part.BODY, 0) == 0 && body.depth >= wall) emptyBody++;
			if(mode == 0 && count.getOrDefault(Part.SPIKES, 0) > 0) spikesWithout++;
			if(mode > 0 && count.getOrDefault(Part.SPIKES, 0) == 0) spikesMissing++;
			// Segmented: every loose piece is Spikes
			if(mode == 2) for(int[] c: IslandGeometry.looseCells(p)) if(got[0].partOf(c[0], c[1], c[2]) != Part.SPIKES) badSpike++;
			cases++;
		}
		check(cases == 36 && badOrder == 0, cases + " islands (4 Outlines x Wall 1 to 3 x no spikes, attached, segmented): the same blocks in the same order with the parts, each block in exactly one of Cap, Body, Spikes, their union all of them");
		check(badSpike == 0, "Spikes = below the body's own bottom in that column (checked against the island without spikes), loose pieces included");
		check(badCap == 0, "Cap = the top Wall rows (y -(Wall-1) to 0), Body = the rest of the shell above the body's bottom");
		check(emptyCap == 0 && emptyBody == 0 && spikesWithout == 0 && spikesMissing == 0, "every island has a Cap and a Body; Spikes only, and always, with spikes");
		Params thin = params(Outline.CIRCLE, 3, 0, BreakMode.ATTACHED); thin.depth = 0;
		IslandGeometry.Parts[] tg = new IslandGeometry.Parts[1]; List<long[]> tc = cells(thin, tg); boolean allCap = true; for(long[] c: tc) allCap &= tg[0].partOf((int) c[0], (int) c[1], (int) c[2]) == Part.CAP;
		check(allCap && !tc.isEmpty(), "Depth 0: the whole island is its cap");

		System.out.println("-- the filter");
		FocusFilter capL2 = new FocusFilter(Part.CAP, 2);
		check(capL2.accepts(Part.CAP, 2) && !capL2.accepts(Part.CAP, 1) && !capL2.accepts(Part.BODY, 2), "Cap x Layer 2: only blocks that are both (the intersection)");
		FocusFilter hidden = new FocusFilter(Part.HIDDEN, 0); boolean none = true; for(Part p: Part.values()) for(int l = 0; l <= 4; l++) none &= !hidden.accepts(p, l);
		check(none, "Hidden: nothing");
		FocusFilter all = new FocusFilter(Part.ALL, 0); boolean every = true; for(Part p: Part.values()) for(int l = 0; l <= 4; l++) every &= all.accepts(p, l);
		check(every && !FocusFilter.isActive(Part.ALL, 0, 0, 1000000), "All, All: everything, and never an active focus (no filtering at all)");
		check(new FocusFilter(Part.ALL, 3).accepts(Part.SPIKES, 3) && !new FocusFilter(Part.ALL, 3).accepts(Part.SPIKES, 4), "All x Layer 3: every part of layer 3");
		check(new FocusFilter(Part.SPIKES, 9).layer == 0 && new FocusFilter(null, 0).part == Part.ALL, "an out-of-range layer or no part counts as All");
		check(FocusFilter.nextPart(Part.ALL, true) == Part.CAP && FocusFilter.nextPart(Part.HIDDEN, true) == Part.ALL && FocusFilter.nextPart(Part.ALL, false) == Part.HIDDEN && FocusFilter.nextPart(Part.HIDDEN, false) == Part.ALL, "Part button: All, Cap, Body, Spikes, Hidden; without parts All, Hidden");
		check(FocusFilter.nextLayer(0) == 1 && FocusFilter.nextLayer(4) == 5 && FocusFilter.nextLayer(5) == 0, "Layer button: All, 1, 2, 3, 4, 5, All");
		check(FocusFilter.nextLayer(2, 3) == 3 && FocusFilter.nextLayer(3, 3) == 0 && FocusFilter.nextLayer(0, 2) == 1 && FocusFilter.nextLayer(2, 2) == 0, "over the layers a shape uses: 3 layers All, 1, 2, 3, All; 2 layers All, 1, 2, All");

		System.out.println("-- the threshold");
		check(!FocusFilter.isActive(Part.CAP, 0, 400, 399) && FocusFilter.isActive(Part.CAP, 0, 400, 400) && FocusFilter.isActive(Part.CAP, 0, 400, 107000), "below N no focus, at N and above it applies (N = 400, the default)");
		check(FocusFilter.isActive(Part.BODY, 0, 0, 1) && FocusFilter.isActive(Part.HIDDEN, 0, 0, 0), "N = 0: always");
		check(FocusFilter.defaultThreshold == 400 && Shape.focusThreshold() == 400, "default 400 (and without a config): with Part and Layer on All the focus does nothing anyway");

		System.out.println("-- the Island's selectors");
		ShapeIsland island = new ShapeIsland();
		String save = island.toPersistence();
		// The focus stays the 43rd and 44th value; the strata's 20 follow it (2026-10-27)
		String[] sv = save.split(",", -1);
		check(island.properties.size() == 64 && sv[42].equals("ALL") && sv[43].equals("0"), "Focus part and Focus layer after Block 4, All and All by default (values 43 and 44 of 64)");
		Method store = Shape.class.getDeclaredMethod("storeFocus", Part.class, int.class); store.setAccessible(true);
		store.invoke(island, Part.SPIKES, 3);
		String focused = island.toPersistence();
		ShapeIsland back = new ShapeIsland(); back.restorePersistence(focused);
		check(!back.error && back.getFocusPart() == Part.SPIKES && back.getFocusLayer() == 3 && String.join(",", Arrays.copyOfRange(focused.split(",", -1), 42, 44)).equals("SPIKES,3"), "saved and restored (Spikes, Layer 3)");
		ShapeIsland old = new ShapeIsland(); old.restorePersistence(save.substring(0, save.length() - ",ALL,0".length()));
		check(!old.error && old.getFocusPart() == Part.ALL && old.getFocusLayer() == 0 && old.toPersistence().equals(save), "an island saved before the focus (42 values) loads with All, All and saves the same text");
		boolean hiddenRows = true; for(Property<?> p: island.getGuiProperties()) hiddenRows &= p != island.properties.get(42) && p != island.properties.get(43);
		check(hiddenRows, "not panel rows (the preview's buttons set them)");
		ShapeCuboid cube = new ShapeCuboid();
		check(!cube.hasParts() && cube.partOf(1, 2, 3) == FocusFilter.WHOLE && cube.getFocusPart() == Part.ALL, "other shapes: no parts (one part, IFocusParts default), focus All");

		System.out.println("-- Focus from in buildguide.cfg");
		File dir = Files.createTempDirectory("bgfocus").toFile(); File cfg = new File(dir, "buildguide.cfg");
		Config fresh = new Config(dir);
		check(fresh.focusFrom.value == 400 && Files.readAllLines(cfg.toPath()).contains("focusFrom = 400"), "a new buildguide.cfg: focusFrom = 400, written last");
		List<String> lines = new ArrayList<>(Files.readAllLines(cfg.toPath())); lines.replaceAll(l -> l.startsWith("focusFrom") ? "focusFrom = 5000" : l); Files.write(cfg.toPath(), lines);
		check(new Config(dir).focusFrom.value == 5000, "a saved value is read back");
		lines.replaceAll(l -> l.startsWith("focusFrom") ? "focusFrom = lots" : l); Files.write(cfg.toPath(), lines);
		check(new Config(dir).focusFrom.value == 400, "an unreadable value keeps the default");
		lines.replaceAll(l -> l.startsWith("focusFrom") ? "focusFrom = -5" : l); Files.write(cfg.toPath(), lines);
		check(new Config(dir).focusFrom.value == 0, "a negative one counts as 0 (always)");
		lines.removeIf(l -> l.startsWith("focusFrom")); Files.write(cfg.toPath(), lines);
		Config older = new Config(dir);
		check(older.focusFrom.value == 400 && Files.readAllLines(cfg.toPath()).contains("focusFrom = 400"), "a buildguide.cfg from before the focus: the default, and the key is added");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
