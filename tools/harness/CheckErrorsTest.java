import java.io.File;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.State;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.ValidationListComponent;
import brentmaas.buildguide.common.screen.ValidationListComponent.Category;
import brentmaas.buildguide.common.screen.VisualisationScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// Check errors (2026-10-22): a switch for the structure errors (solid blocks around a shape), off by default.
// The state drops them and a scan asked for or running goes stale; block events around the shape change
// nothing while it is off; a scan without them reads only the shape's positions, once each, with the same
// statuses; the Errors tab says why it is empty; State keeps the switch (persisted last) and hands it to every
// shape. And the Visualisation topics: Display and Find blocks fit beside each other.
public class CheckErrorsTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static int reads = 0;
	static class World implements IBlockProbe {
		final Set<Long> solid; World(Set<Long> s){ solid = s; }
		public boolean isLoaded(int x, int y, int z){ return true; }
		public int flags(int x, int y, int z){ reads++; return solid.contains(LocalPos.pack(x, y, z)) ? FLAG_SOLID : FLAG_AIR; }
		public String name(int x, int y, int z){ return "Stone"; }
	}
	static ValidationState validated(List<Long> exp, List<NearBlock> near){
		ValidationState s = new ValidationState(); s.beginScan(exp, 0, 0, 0); for(long p: exp) s.setStatus(p, ValidationState.MISSING, null); s.setNearBlocks(near); s.endScan(); return s;
	}

	public static void main(String[] a) throws Exception {
		System.out.println("-- the state");
		List<Long> exp = new ArrayList<>(); for(int x = 0; x < 6; x++) exp.add(LocalPos.pack(x, 0, 0));
		ValidationState s = validated(exp, Arrays.asList(new NearBlock(LocalPos.pack(2, 1, 0), "Stone", 1f)));
		check(s.isCheckErrors() && s.getNearCount() == 1 && s.getErrorCount() == 1, "on by default in a state (as before): one structure error");
		long v = s.getVersion(), epoch = s.getScanEpoch();
		s.setCheckErrors(false);
		check(!s.isCheckErrors() && s.getNearCount() == 0 && s.getErrorCount() == 0 && s.getVersion() > v, "off: the errors are dropped at once, the version moves (list, overlay and preview follow)");
		check(s.getMissing() == 6 && s.isValidated() && s.getScanEpoch() == epoch, "the shape's own totals stay; no scan was asked for or running, so none is asked for");
		s.updateBlock(LocalPos.pack(3, 1, 0), false, true, false, "Stone");
		check(s.getNearCount() == 0, "off: a solid block placed next to the shape is not an error");
		s.updateBlock(LocalPos.pack(3, 0, 0), false, true, false, "Stone");
		check(s.getOk() == 1, "off: the shape's own positions still follow the world");
		long v2 = s.getVersion(); s.setCheckErrors(false);
		check(s.getVersion() == v2, "the same setting again changes nothing");
		s.consumeScanRequest(); long e2 = s.getScanEpoch();
		s.setCheckErrors(true);
		check(s.isCheckErrors() && s.getScanEpoch() > e2 && s.consumeScanRequest(), "on again: a validated state asks for a scan to find them");
		s.updateBlock(LocalPos.pack(3, 1, 0), false, true, false, "Stone");
		check(s.getNearCount() == 1, "on: block events find errors again");
		ValidationState fresh = new ValidationState(); fresh.setCheckErrors(false);
		check(!fresh.consumeScanRequest(), "a state never scanned asks for nothing when the switch moves");
		ValidationState asked = new ValidationState(); asked.requestScan(0); long e3 = asked.getScanEpoch(); asked.setCheckErrors(false);
		check(asked.getScanEpoch() > e3, "a scan already asked for (maybe being prepared with the old setting) is made stale");

		System.out.println("-- a scan without structure errors");
		Random r = new Random(9); int same = 0, cases = 0; boolean onlyShape = true, noNear = true, layersOnly = true;
		for(int k = 0; k < 10; k++){
			IslandGeometry.Params p = new IslandGeometry.Params(); p.outline = IslandGeometry.Outline.ORGANIC; p.widthX = 15 + r.nextInt(25); p.widthZ = 15 + r.nextInt(25); p.depth = 5 + r.nextInt(8); p.wall = 1 + r.nextInt(2); p.seed = r.nextInt(500);
			Set<Long> shape = new LinkedHashSet<>(); IslandGeometry.enumerate(p, (x, y, z) -> shape.add(LocalPos.pack(x, y, z)));
			Set<Long> solid = new HashSet<>(); for(long q: shape) if(r.nextInt(3) == 0) solid.add(q);
			for(long q: shape) if(r.nextInt(5) == 0) solid.add(LocalPos.pack(LocalPos.unpackX(q), LocalPos.unpackY(q) + 1, LocalPos.unpackZ(q)));
			List<Long> list = new ArrayList<>(shape); World w = new World(solid);
			ValidationState on = new ValidationState(); on.requestScan(0); on.consumeScanRequest();
			SliceScan a1 = SliceScan.prepare(on, list, 0, 0, 0, 1, on.getScanEpoch(), null, new ArrayList<>(), true); a1.step(w, 0, () -> 0L); a1.publish(w);
			ValidationState off = new ValidationState(); off.requestScan(0); off.consumeScanRequest(); off.setCheckErrors(false);
			SliceScan a2 = SliceScan.prepare(off, list, 0, 0, 0, 1, off.getScanEpoch(), null, new ArrayList<>(), false);
			reads = 0; int frames = 0; boolean done = false; int lastPercent = -1; boolean rising = true;
			while(!done){ done = a2.step(w, 1, () -> 0L); frames++; if(a2.percent() < lastPercent) rising = false; lastPercent = a2.percent(); }
			if(reads != shape.size()) onlyShape = false;
			a2.publish(w);
			cases++;
			if(on.getOk() == off.getOk() && on.getMissing() == off.getMissing() && on.getPositions(ValidationState.OK).equals(off.getPositions(ValidationState.OK)) && rising && lastPercent == 100) same++;
			if(off.getNearCount() != 0 || on.getNearCount() == 0) noNear = false;
			int layers = 0; { int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE; for(long q: shape){ mn = Math.min(mn, LocalPos.unpackY(q)); mx = Math.max(mx, LocalPos.unpackY(q)); } layers = mx - mn + 1; }
			if(frames > layers + shape.size() / SliceScan.classifyChunk + 1) layersOnly = false;
		}
		check(same == cases, "10 random islands: the same ok and missing (and their lists) as with errors on, progress up to 100 % (" + same + " of " + cases + ")");
		check(onlyShape, "off: the world is read at the shape's positions only, once each (no box around it)");
		check(noNear, "off: no structure error; on: the same islands have some");
		check(layersOnly, "off: no layer above or below the shape, no row sweep: about one unit per layer");

		System.out.println("-- the Errors tab, and a scan made stale");
		ShapeBridge b = new ShapeBridge(); ValidationState bs = b.getValidationState(); bs.beginScan(exp, 0, 0, 0); for(long p: exp) bs.setStatus(p, ValidationState.OK, null); bs.endScan();
		check(ValidationListComponent.buildEntries(b, Category.ERRORS).titles.get(0).getTranslationKey().equals("screen.buildguide.errors.none"), "on, no errors: \"None\"");
		bs.setCheckErrors(false);
		check(ValidationListComponent.buildEntries(b, Category.ERRORS).titles.get(0).getTranslationKey().equals("screen.buildguide.errors.off"), "off: the row says they are not checked and where to turn them on");
		check(ValidationListComponent.buildEntries(b, Category.MISSING).titles.get(0).getTranslationKey().equals("screen.buildguide.errors.none"), "the other tabs as before");
		ValidationState running = validated(exp, new ArrayList<>()); running.requestScan(0); running.consumeScanRequest();
		SliceScan job = SliceScan.prepare(running, exp, 0, 0, 0, 7, running.getScanEpoch(), null, new ArrayList<>(), true); running.setActiveScan(job);
		running.setCheckErrors(false);
		check(job.isStale(running, 7), "a scan running when the switch moves is stale (the render handler starts another)");

		System.out.println("-- State: off by default, persisted last, handed to every shape");
		State st = new State();
		check(!st.isCheckErrors(), "off by default");
		ShapeBridge s1 = new ShapeBridge(); ShapeCuboid s2 = new ShapeCuboid();
		st.shapeSets.add(OriginScanTest.newSet(new Shape[]{s1, null, s2}, 0, 64, 0));
		st.setCheckErrors(true);
		check(s1.getValidationState().isCheckErrors() && s2.getValidationState().isCheckErrors(), "on: every shape that exists gets it");
		st.setCheckErrors(false);
		check(!s1.getValidationState().isCheckErrors() && !s2.getValidationState().isCheckErrors(), "off: every shape too");
		File dir = Files.createTempDirectory("bgcheck").toFile(); File f = new File(dir, "state.txt");
		State empty = new State(); empty.setCheckErrors(true); empty.savePersistence(f);
		List<String> lines = Files.readAllLines(f.toPath());
		check(lines.get(lines.size() - 1).equals("checkErrors=true"), "saved as the last line: " + lines);
		State back = new State(); back.loadPersistence(f);
		check(back.isCheckErrors(), "loaded back");
		Files.write(f.toPath(), Arrays.asList("enabled=true", "depthTest=true", "highlightErrors=true", "iShapeSet=0", "iShapeNew=0"));
		State old = new State(); old.loadPersistence(f);
		check(!old.isCheckErrors(), "an older save without the key: off");

		System.out.println("-- layout: the topics, and the preview down to the status line");
		check(275 + 120 < VisualisationScreen.topicX && VisualisationScreen.topicX + VisualisationScreen.topicWidth <= 480, "topic buttons in the free column right of the cube size block (x " + VisualisationScreen.topicX + ".." + (VisualisationScreen.topicX + VisualisationScreen.topicWidth) + ")");
		check(VisualisationScreen.findX2 < VisualisationScreen.topicX && VisualisationScreen.findTabsBottom <= VisualisationScreen.findListTop && 270 - 22 - VisualisationScreen.findListTop >= 15 * ValidationListComponent.rowHeight, "Find blocks: tabs then at least 15 rows left of the topic buttons, above the bottom bar at 270");
		check(new brentmaas.buildguide.common.screen.ShapeLayout(270).previewBottom - ShapeScreen.previewTop - ShapeScreen.previewControlsHeight - ShapeScreen.focusRowHeight - ShapeScreen.previewHintHeight >= 140, "the 3D area of the Shape tab at 270 is 140 px tall or more (it was 77)");

		System.out.println("-- texts");
		String lang = new String(Files.readAllBytes(new File("common/resources/assets/buildguide/lang/en_us.json").toPath()), "UTF-8");
		boolean all = true; for(String key: new String[]{"screen.buildguide.checkerrors", "screen.buildguide.topic.display", "screen.buildguide.topic.find", "screen.buildguide.find.hint", "screen.buildguide.errors.off", "screen.buildguide.status.missing", "screen.buildguide.status.built", "screen.buildguide.status.errors", "screen.buildguide.status.wronglayer", "screen.buildguide.status.notvalidated"}) if(!lang.contains("\"" + key + "\"")) { all = false; System.out.println("   missing " + key); }
		check(all, "every new text is in en_us.json");
		check(lang.contains("\"shape.buildguide.polygonbuilding\": \"Geometric capsule\""), "the capsule's name");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
