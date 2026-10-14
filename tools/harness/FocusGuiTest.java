import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.ShapeLayout;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.FocusFilter.Part;
// Build focus, the panel: the Part and Layer buttons' texts fit their buttons (the vanilla font's widths, from
// en_us.json), the focus row fits the preview panel at the heights 270 and 360 (the 3D area below it keeps its
// room), and the preview's corner note reads "Focus: ..." while the world shows a focus and "Focus off: < N
// blocks" while one is selected below the threshold.
public class FocusGuiTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final Map<String, String> lang = new HashMap<>();
	static String t(String k, Object... v){ String s = lang.getOrDefault(k, k); return v.length == 0 ? s : String.format(s, v); }

	public static void main(String[] a) throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		Matcher m = Pattern.compile("\"([^\"]+)\": \"([^\"]*)\"").matcher(json); while(m.find()) lang.put(m.group(1), m.group(2));
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable x){ return null; } public void showNone(){} public String translate(String k){ return t(k); } public String translate(String k, Object... v){ return t(k, v); } };
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FocusGuiTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, x, y) -> null);

		System.out.println("-- the button texts");
		int widest = 0; String widestText = "";
		for(Part p: Part.values()){ String s = t("screen.buildguide.focus.part", t(p.translationKey)); int w = LabelWidthTest.width(s); if(w > widest){ widest = w; widestText = s; } }
		check(!lang.containsKey("screen.buildguide.focus.part") ? false : widest <= 76 && widest <= ShapeScreen.partWidth - 4, "every \"Part: ...\" fits 76 px and its " + ShapeScreen.partWidth + "-px button (widest \"" + widestText + "\", " + widest + " px)");
		int widestLayer = 0;
		for(int l = 0; l <= 4; l++) widestLayer = Math.max(widestLayer, LabelWidthTest.width(t("screen.buildguide.focus.layer", l == 0 ? t("screen.buildguide.focus.layer.all") : "" + l)));
		check(lang.containsKey("screen.buildguide.focus.layer") && widestLayer <= ShapeScreen.layerWidth - 4, "every \"Layer: ...\" fits its " + ShapeScreen.layerWidth + "-px button (" + widestLayer + " px)");
		String longest = t("screen.buildguide.focus.on", t("screen.buildguide.focus.part.spikes") + ", " + t("screen.buildguide.focus.layern", "4"), "278000", "278000");
		check(LabelWidthTest.width(longest) <= ShapeLayout.minWidth - ShapeScreen.rightX - 6, "the longest note fits the preview at the minimum width: \"" + longest + "\" (" + LabelWidthTest.width(longest) + " px)");
		check(lang.containsKey("config.buildguide.focusFrom") && lang.containsKey("config.buildguide.focusFromComment") && lang.containsKey("screen.buildguide.focus.off"), "Configuration and the notes have their texts");

		System.out.println("-- the focus row at 270 and 360");
		for(int h: new int[]{270, 360}){
			ShapeLayout l = new ShapeLayout(h);
			int rowBottom = ShapeScreen.focusRowY + 16, areaTop = ShapeScreen.previewTop + ShapeScreen.previewControlsHeight + ShapeScreen.focusRowHeight, areaBottom = l.previewBottom - ShapeScreen.previewHintHeight;
			int right = ShapeScreen.rightX + 2 + ShapeScreen.partWidth + 2 + ShapeScreen.layerWidth;
			check(ShapeScreen.focusRowY >= ShapeScreen.previewTop + 2 + 16 && rowBottom <= areaTop && right <= ShapeLayout.minWidth - 2, h + ": the row (y " + ShapeScreen.focusRowY + ".." + rowBottom + ", x to " + right + ") is under the controls, above the 3D area, inside the panel");
			check(areaBottom - areaTop >= 80, h + ": the 3D area keeps " + (areaBottom - areaTop) + " px of height");
		}

		System.out.println("-- the preview's note");
		File dir = Files.createTempDirectory("bgfocusg").toFile(); BuildGuide.config = new Config(dir); BuildGuide.config.focusFrom.value = 0;
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(FocusGuiTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, x, y) -> x.getName().equals("newBuffer") ? new FocusWorldTest.RecBuffer() : x.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : x.isDefault() ? InvocationHandler.invokeDefault(p, x, y) : null);
		TimingLog.sink = line -> {};
		ShapeIsland island = FocusWorldTest.place(new ShapeIsland()); FocusWorldTest.setProp(island, "propertySpikes", 10); FocusWorldTest.setProp(island, "propertyLayers", true);
		island.update(); FocusWorldTest.settle(island); int total = island.getNumberOfBlocks();
		check(ShapeScreen.focusNotice(island) == null, "All, All: no note");
		island.setFocus(Part.BODY, 2); FocusWorldTest.settle(island);
		check(island.getWorldBlocks() > 0 && ("Focus: Body, Layer 2: " + island.getWorldBlocks() + " of " + total + " blocks").equals(ShapeScreen.focusNotice(island)), "\"" + ShapeScreen.focusNotice(island) + "\"");
		island.setFocus(Part.ALL, 3); FocusWorldTest.settle(island);
		check(("Focus: Layer 3: " + island.getWorldBlocks() + " of " + total + " blocks").equals(ShapeScreen.focusNotice(island)), "\"" + ShapeScreen.focusNotice(island) + "\"");
		island.setFocus(Part.HIDDEN, 3); FocusWorldTest.settle(island);
		check(("Focus: Hidden: 0 of " + total + " blocks").equals(ShapeScreen.focusNotice(island)), "\"" + ShapeScreen.focusNotice(island) + "\"");
		BuildGuide.config.focusFrom.value = total + 1; island.refreshFocus(); FocusWorldTest.settle(island);
		check(("Focus off: < " + (total + 1) + " blocks").equals(ShapeScreen.focusNotice(island)) && island.getWorldFocus() == null, "below the threshold: \"" + ShapeScreen.focusNotice(island) + "\" and the world shows everything (refreshFocus, as Configuration's Set does)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
