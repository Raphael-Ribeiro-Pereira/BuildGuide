import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.SliceScan;
// Scan speed (Configuration): saved in buildguide.cfg like the World update mode, after every existing
// key; an older file without it gets the default (Normal) appended; an unknown value keeps the current
// one. The labels it adds fit their places.
public class ScanSpeedConfigTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static List<String> keys(File f) throws Exception { List<String> k = new ArrayList<>(); for(String l: Files.readAllLines(f.toPath())) if(!l.startsWith("#") && l.contains(" = ")) k.add(l.substring(0, l.indexOf(" = "))); return k; }

	public static void main(String[] a) throws Exception {
		BuildGuide.screenHandler = new AbstractScreenHandler(){
			public IScreenWrapper createWrapper(Translatable title){ return null; }
			public void showNone(){}
			public String translate(String key){ return key; }
			public String translate(String key, Object... values){ return key; }
		};
		BuildGuide.logHandler = (ILogHandler) java.lang.reflect.Proxy.newProxyInstance(ScanSpeedConfigTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);

		File dir = Files.createTempDirectory("bgscan").toFile(); File cfg = new File(dir, "buildguide.cfg");
		Config fresh = new Config(dir);
		check(fresh.scanSpeed.value == SliceScan.Speed.NORMAL, "a new buildguide.cfg: Scan speed Normal (the default)");
		check(keys(cfg).equals(Arrays.asList("asyncEnabled", "shapeListRandomColorsDefaultEnabled", "persistenceEnabled", "ignoredBlocks", "worldUpdateMode", "scanSpeed")), "keys in their old order, scanSpeed last: " + keys(cfg));

		Files.write(cfg.toPath(), Arrays.asList("asyncEnabled = false", "shapeListRandomColorsDefaultEnabled = true", "persistenceEnabled = true", "ignoredBlocks = minecraft:dirt", "worldUpdateMode = LIVE"));
		Config older = new Config(dir);
		check(older.scanSpeed.value == SliceScan.Speed.NORMAL && older.worldUpdateMode.value.name().equals("LIVE") && !older.asyncEnabled.value && older.ignoredBlocks.value.equals("minecraft:dirt"), "a file from before (no scanSpeed): every saved value kept, Scan speed Normal");
		check(keys(cfg).get(keys(cfg).size() - 1).equals("scanSpeed") && keys(cfg).size() == 6, "and the file is rewritten with scanSpeed added at the end");

		for(SliceScan.Speed s: SliceScan.Speed.values()){
			older.scanSpeed.setValue(s); older.write(); Config back = new Config(dir);
			check(back.scanSpeed.value == s, s + ": saved and loaded by name");
		}
		String text = new String(Files.readAllBytes(cfg.toPath()), "UTF-8").replace("scanSpeed = SLOW", "scanSpeed = TURBO");
		Files.write(cfg.toPath(), text.getBytes("UTF-8"));
		check(new Config(dir).scanSpeed.value == SliceScan.Speed.NORMAL, "an unknown value (TURBO) keeps the default, no crash");

		System.out.println("-- labels");
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		Matcher label = Pattern.compile("\"config\\.buildguide\\.scanSpeed\": \"([^\"]*)\"").matcher(json); label.find();
		check(LabelWidthTest.width(label.group(1)) <= 480 - 370 - 2, "\"" + label.group(1) + "\": " + LabelWidthTest.width(label.group(1)) + " px from x 370 (the screen is 480 wide)");
		for(String s: new String[]{"instant", "fast", "normal", "slow"}){ Matcher m = Pattern.compile("\"screen\\.buildguide\\.scanspeed\\." + s + "\": \"([^\"]*)\"").matcher(json); m.find();
			check(LabelWidthTest.width(m.group(1)) <= 105 - 8, "button \"" + m.group(1) + "\": " + LabelWidthTest.width(m.group(1)) + " px in a 105 px button"); }
		Matcher scanning = Pattern.compile("\"screen\\.buildguide\\.scanning\": \"([^\"]*)\"").matcher(json); scanning.find();
		String shown = scanning.group(1).replace("%s", "100%");
		check(LabelWidthTest.width(shown) <= 140, "\"" + shown + "\": " + LabelWidthTest.width(shown) + " px in the preview corner (288 px wide)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
