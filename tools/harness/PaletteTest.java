import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// Palette per layer (4D), core: the one classification rule (LayerPalette.classify) and every path that uses
// it. The truth table of Raphael's decisions; no palette = the rule of before; the scan in layers equal to the
// instantaneous one and to an independent reference in random worlds with palettes, structure errors and
// exclusions; blocks changed mid-scan; block events equal to a full rescan; the safety net; the counters;
// and the probe is asked for ids and names only where needed (nothing per block on the hot path).
public class PaletteTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final int AIR = IBlockProbe.FLAG_AIR, SOLID = IBlockProbe.FLAG_SOLID, IGN = IBlockProbe.FLAG_IGNORED, NONE = LayerPalette.NONE;
	static final byte OK = ValidationState.OK, MISSING = ValidationState.MISSING, IGNORED = ValidationState.IGNORED, WL = ValidationState.WRONG_LAYER, WB = ValidationState.WRONG_BLOCK;
	// Block ids by name, as the loader's registry would give them
	static final Map<String, Integer> IDS = new HashMap<>();
	static { String[] n = {"Stone", "Dirt", "Cobblestone", "Oak Planks", "Torch", "Scaffolding", "Ladder", "Grass", "Glass"}; for(int i = 0; i < n.length; i++) IDS.put(n[i], 10 + i); }
	static int id(String name){ return IDS.get(name); }

	// SliceScanTest's fake world, plus ids from the block names and a count of the id and name reads
	static class IdWorld extends SliceScanTest.World {
		long idReads = 0, nameReads = 0;
		@Override public int blockId(int x, int y, int z){ idReads++; String n = names.get(LocalPos.pack(x, y, z)); return n == null ? NONE : IDS.get(n); }
		@Override public String name(int x, int y, int z){ nameReads++; return super.name(x, y, z); }
		IdWorld copyIds(){ IdWorld w = new IdWorld(); w.flags.putAll(flags); w.names.putAll(names); return w; }
	}
	// The decisions written out directly, independent of LayerPalette
	static byte reference(int[] chosen, ShapeLayers layers, int y, int flags, int blockId){
		if((flags & AIR) != 0) return MISSING;
		int want = chosen[layers.layerOf(y) - 1];
		boolean inPalette = false; for(int c: chosen) if(c != NONE && c == blockId) inPalette = true;
		if(want != NONE && blockId == want) return OK;
		if(want != NONE && inPalette) return WL;
		if((flags & IGN) != 0) return IGNORED;
		if((flags & SOLID) == 0) return MISSING;
		return want != NONE ? WB : OK;
	}
	static String snapshot(ValidationState s){
		StringBuilder b = new StringBuilder(SliceScanTest.snapshot(s));
		b.append('|').append(s.getWrongLayer()).append('/').append(s.getWrongBlock());
		for(byte st: new byte[]{WL, WB}) b.append('|').append(s.getPositions(st));
		for(byte st: new byte[]{IGNORED, WL, WB}) for(long p: s.getPositions(st)) b.append(';').append(s.getBlockName(p));
		return b.toString();
	}
	static String scan(SliceScanTest.Case c, IdWorld w, LayerPalette palette, long budget){
		ValidationState s = SliceScanTest.fresh(c); s.setPalette(palette); s.consumeScanRequest();
		SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job.step(w, budget, () -> SliceScanTest.nanos)); job.publish(w); return snapshot(s);
	}
	static ValidationState scanState(SliceScanTest.Case c, IdWorld w, LayerPalette palette){
		ValidationState s = SliceScanTest.fresh(c); s.setPalette(palette); s.consumeScanRequest();
		SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job.step(w, 0, () -> SliceScanTest.nanos)); job.publish(w); return s;
	}
	// A random case with more block kinds on the shape, and a palette over its depth
	static IdWorld paletteWorld(SliceScanTest.Case c, Random r){
		IdWorld w = c.world instanceof IdWorld ? (IdWorld) c.world : new IdWorld(); if(w != c.world){ w.flags.putAll(c.world.flags); w.names.putAll(c.world.names); }
		String[] solids = {"Stone", "Dirt", "Cobblestone", "Oak Planks", "Glass"};
		for(long p: c.shape){ int x = LocalPos.unpackX(p) + c.ox, y = LocalPos.unpackY(p) + c.oy, z = LocalPos.unpackZ(p) + c.oz; if("Stone".equals(w.names.get(LocalPos.pack(x, y, z))) && r.nextInt(3) > 0) w.set(x, y, z, SOLID, solids[r.nextInt(solids.length)]); }
		return w;
	}
	static LayerPalette randomPalette(SliceScanTest.Case c, Random r){
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE; for(long p: c.shape){ minY = Math.min(minY, LocalPos.unpackY(p)); maxY = Math.max(maxY, LocalPos.unpackY(p)); }
		int[] cut = ShapeLayers.normalize(5 + 5 * r.nextInt(17), 5 + 5 * r.nextInt(17), 5 + 5 * r.nextInt(17), 0);
		String[] pick = {null, "Stone", "Dirt", "Cobblestone", "Oak Planks", "Torch"};
		int[] ids = new int[4]; for(int i = 0; i < 4; i++){ String n = pick[r.nextInt(pick.length)]; ids[i] = n == null ? NONE : id(n); }
		return new LayerPalette(new ShapeLayers(maxY, maxY - minY, cut[0], cut[1], cut[2]), ids);
	}

	public static void main(String[] a) throws Exception {
		System.out.println("-- the truth table (Depth 20, cuts 25/50/75; Stone on layers 1 and 4, Dirt on 2, layer 3 any)");
		ShapeLayers layers = new ShapeLayers(0, 20, 25, 50, 75);
		LayerPalette pal = new LayerPalette(layers, new int[]{id("Stone"), id("Dirt"), NONE, id("Stone")});
		int L1 = 0, L2 = -6, L3 = -12, L4 = -18;
		Object[][] table = {
			{"air on layer 1", L1, AIR, NONE, MISSING},
			{"layer 1, its block (Stone)", L1, SOLID, id("Stone"), OK},
			{"layer 1, the block of layer 2 (Dirt)", L1, SOLID, id("Dirt"), WL},
			{"layer 1, a solid block in no palette (Oak Planks)", L1, SOLID, id("Oak Planks"), WB},
			{"layer 1, a non-solid block in no palette (Torch)", L1, 0, id("Torch"), MISSING},
			{"layer 1, an ignored type (Scaffolding)", L1, SOLID | IGN, id("Scaffolding"), IGNORED},
			{"layer 2, its block (Dirt)", L2, SOLID, id("Dirt"), OK},
			{"layer 2, Stone (chosen for layers 1 and 4)", L2, SOLID, id("Stone"), WL},
			{"layer 3 (no block chosen), Dirt: any solid is fine, no orange", L3, SOLID, id("Dirt"), OK},
			{"layer 3, Oak Planks", L3, SOLID, id("Oak Planks"), OK},
			{"layer 3, a torch", L3, 0, id("Torch"), MISSING},
			{"layer 3, air", L3, AIR, NONE, MISSING},
			{"layer 4, Stone (also layer 1's): its own layer wins", L4, SOLID, id("Stone"), OK},
			{"layer 4, Dirt", L4, SOLID, id("Dirt"), WL},
			{"below Depth (a spike, layer 4), Stone", -35, SOLID, id("Stone"), OK},
		};
		for(Object[] row: table){ byte got = LayerPalette.classify(pal, (Integer) row[1], (Integer) row[2], (Integer) row[3]); check(got == (Byte) row[4], row[0] + ": " + got); }
		LayerPalette soft = new LayerPalette(layers, new int[]{id("Torch"), NONE, NONE, NONE});
		check(LayerPalette.classify(soft, L1, 0, id("Torch")) == OK, "a chosen block that is not solid (Torch on layer 1) is right there");
		boolean same = true;
		for(int f: new int[]{AIR, 0, SOLID, IGN, SOLID | IGN, AIR | IGN}) for(int y: new int[]{0, -7, -30}) for(int bid: new int[]{NONE, id("Stone"), id("Dirt")}){
			byte old = (f & AIR) != 0 ? MISSING : (f & IGN) != 0 ? IGNORED : (f & SOLID) != 0 ? OK : MISSING;
			same &= LayerPalette.classify(null, y, f, bid) == old && LayerPalette.classify(new LayerPalette(layers, new int[]{NONE, NONE, NONE, NONE}), y, f, bid) == old;
		}
		check(same, "no palette, or a palette with no block chosen: exactly the rule of before (ignored -> IGNORED, solid -> OK, else MISSING)");
		check(ValidationState.WRONG_LAYER == 5 && ValidationState.WRONG_BLOCK == 6 && ValidationState.IGNORED == 4, "WRONG_LAYER 5 and WRONG_BLOCK 6 after IGNORED 4 (3 stays reserved; statuses are never persisted)");

		System.out.println("-- scan in layers = instantaneous = the reference, 40 random worlds");
		int equal = 0, ref = 0, total = 0, sawWL = 0, sawWB = 0;
		for(long seed = 1; seed <= 40; seed++){
			SliceScanTest.Case c = SliceScanTest.randomCase(seed); Random r = new Random(seed * 31); IdWorld w = paletteWorld(c, r); c.world = w; LayerPalette p = randomPalette(c, r);
			String instant = scan(c, w, p, 0), sliced = scan(c, w, p, 1), budget = scan(c, w, p, 3000);
			if(instant.equals(sliced) && instant.equals(budget)) equal++;
			ValidationState s = scanState(c, w, p); boolean match = true; int[] chosen = new int[4]; for(int i = 0; i < 4; i++) chosen[i] = p.idOf(i + 1);
			for(long pos: c.tracked){ int x = LocalPos.unpackX(pos) + c.ox, y = LocalPos.unpackY(pos) + c.oy, z = LocalPos.unpackZ(pos) + c.oz; Integer f = w.flags.get(LocalPos.pack(x, y, z)); String n = w.names.get(LocalPos.pack(x, y, z));
				match &= s.getStatus(pos) == reference(chosen, p.layers, LocalPos.unpackY(pos), f == null ? AIR : f, n == null ? NONE : id(n)); }
			if(match) ref++; total++; if(s.getWrongLayer() > 0) sawWL++; if(s.getWrongBlock() > 0) sawWB++;
		}
		check(equal == total, "budget 1 ns (one layer a frame), about 3 layers a frame and Instant: identical states (" + equal + " of " + total + ")");
		check(ref == total && sawWL > 10 && sawWB > 10, "every expected position as the reference decides (" + ref + " of " + total + "; wrong layer in " + sawWL + ", wrong block in " + sawWB + " worlds)");

		System.out.println("-- blocks changed mid-scan, block events, the safety net");
		int mid = 0, inc = 0, net = 0; total = 0;
		for(long seed = 100; seed < 130; seed++){
			SliceScanTest.Case c = SliceScanTest.randomCase(seed); Random r = new Random(seed); IdWorld w = paletteWorld(c, r); c.world = w; LayerPalette p = randomPalette(c, r);
			ValidationState s = SliceScanTest.fresh(c); s.setPalette(p); s.consumeScanRequest(); SliceScan job = new SliceScan(s, c.shape, c.tracked, c.ox, c.oy, c.oz, 1);
			String[] kinds = {"Stone", "Dirt", "Cobblestone", "Oak Planks", "Torch"}; List<Long> changed = new ArrayList<>();
			int steps = 0; while(!job.step(w, 1, () -> SliceScanTest.nanos)){ if(++steps % 3 == 0) for(int i = 0; i < 6; i++){ long pos = c.shape.get(r.nextInt(c.shape.size())); int x = LocalPos.unpackX(pos) + c.ox, y = LocalPos.unpackY(pos) + c.oy, z = LocalPos.unpackZ(pos) + c.oz;
				String k = kinds[r.nextInt(kinds.length)]; if(r.nextInt(5) == 0) w.set(x, y, z, AIR, null); else w.set(x, y, z, k.equals("Torch") ? 0 : SOLID, k); job.markDirty(x, y, z); } }
			job.publish(w);
			if(snapshot(s).equals(scan(c, w, p, 0))) mid++;
			// Block events after the scan, as IncrementalValidator passes them, then a full rescan of the same world
			for(int i = 0; i < 40; i++){ long pos = c.tracked.get(r.nextInt(c.tracked.size())); int x = LocalPos.unpackX(pos) + c.ox, y = LocalPos.unpackY(pos) + c.oy, z = LocalPos.unpackZ(pos) + c.oz;
				String k = kinds[r.nextInt(kinds.length)]; boolean air = r.nextInt(6) == 0; if(air) w.set(x, y, z, AIR, null); else w.set(x, y, z, k.equals("Torch") ? 0 : SOLID, k);
				s.updateBlock(pos, air, !air && !k.equals("Torch"), false, air ? NONE : id(k), air ? null : k); }
			ValidationState full = scanState(c, w, p); boolean same2 = true;
			for(long pos: c.tracked) same2 &= s.getStatus(pos) == full.getStatus(pos) && Objects.equals(s.getBlockName(pos), full.getBlockName(pos));
			same2 &= s.getOk() == full.getOk() && s.getMissing() == full.getMissing() && s.getWrongLayer() == full.getWrongLayer() && s.getWrongBlock() == full.getWrongBlock();
			if(same2) inc++;
			// The safety net: statuses made wrong behind the state's back are put right by the same rule
			for(int i = 0; i < 20; i++){ long pos = c.tracked.get(r.nextInt(c.tracked.size())); s.setStatus(pos, s.getStatus(pos) == OK ? MISSING : OK, null); }
			StateReconciler.run(s, w, 1000000, null); boolean same3 = true; for(long pos: c.tracked) same3 &= s.getStatus(pos) == full.getStatus(pos);
			if(same3) net++; total++;
		}
		check(mid == total, "blocks changed (and marked) during the scan: the result is the instantaneous scan of the final world (" + mid + " of " + total + ")");
		check(inc == total, "40 block events after a scan, with ids: the same statuses, names and counters as a full rescan (" + inc + " of " + total + ")");
		check(net == total, "the safety net restores corrupted statuses with the same rule (" + net + " of " + total + ")");

		System.out.println("-- counters, and what the probe is asked");
		SliceScanTest.Case c = SliceScanTest.randomCase(7); Random r = new Random(7); IdWorld w = paletteWorld(c, r); c.world = w; LayerPalette p = randomPalette(c, r);
		ValidationState s = scanState(c, w, p);
		int byStatus = 0; for(byte st: new byte[]{OK, MISSING, IGNORED, WL, WB}) byStatus += s.getPositions(st).size();
		check(s.getOk() + s.getMissing() + s.getWrongLayer() + s.getWrongBlock() == s.getTotal() && byStatus == s.getTotal(), "ok + missing (ignored included) + wrong layer + wrong block = total (" + s.getOk() + " + " + s.getMissing() + " + " + s.getWrongLayer() + " + " + s.getWrongBlock() + " = " + s.getTotal() + ")");
		check(s.getErrorCount() == s.getNearCount() + s.getWrongBlock(), "errors (red) = structure errors around the shape (" + s.getNearCount() + ") + wrong blocks on it (" + s.getWrongBlock() + ")");
		IdWorld counted = w.copyIds(); ValidationState t = SliceScanTest.fresh(c); t.setPalette(p); t.consumeScanRequest();
		SliceScan job = new SliceScan(t, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job.step(counted, 0, () -> SliceScanTest.nanos)); job.publish(counted);
		int needsId = 0; for(long pos: c.tracked) if(p.needsId(LocalPos.unpackY(pos))) needsId++;
		int named = t.getIgnored() + t.getWrongLayer() + t.getWrongBlock();
		check(counted.idReads == needsId, "ids read only on positions whose layer has a block (" + counted.idReads + " of " + c.tracked.size() + " expected positions): an int each, no string");
		check(counted.nameReads == named + t.getNearCount(), "names read only for what is listed (" + counted.nameReads + " = " + named + " ignored/wrong + " + t.getNearCount() + " structure errors)");
		IdWorld plain = w.copyIds(); ValidationState u = SliceScanTest.fresh(c); u.consumeScanRequest();
		SliceScan job2 = new SliceScan(u, c.shape, c.tracked, c.ox, c.oy, c.oz, 1); while(!job2.step(plain, 0, () -> SliceScanTest.nanos)); job2.publish(plain);
		check(plain.idReads == 0 && u.getWrongLayer() == 0 && u.getWrongBlock() == 0, "without a palette: no id read at all, no wrong statuses");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
