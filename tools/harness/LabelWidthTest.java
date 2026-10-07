import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
// Island labels must fit the label column: Property.labelX 2 .. controlX 80, limit 76 px. Widths use
// the vanilla default font's advances (glyph width + 1 px spacing): 6 px for most ASCII, narrower for
// the characters listed below. Reads the values straight from en_us.json.
public class LabelWidthTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static final Map<Character, Integer> NARROW = new HashMap<>();
	static {
		for(char c: "!',.:;i|".toCharArray()) NARROW.put(c, 2);
		for(char c: "`l".toCharArray()) NARROW.put(c, 3);
		for(char c: " It[]".toCharArray()) NARROW.put(c, 4);
		for(char c: "\"()*<>fk{}".toCharArray()) NARROW.put(c, 5);
		NARROW.put('@', 7); NARROW.put('~', 7);
	}
	static int width(String s){ int w = 0; for(char c: s.toCharArray()) w += NARROW.getOrDefault(c, 6); return w; }

	public static void main(String[] a) throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("common/resources/assets/buildguide/lang/en_us.json")), "UTF-8");
		String[] keys = {"outline", "widthx", "widthz", "sides", "roundness", "islandrotation", "edgeamplitude", "edgescale", "wall", "depth", "profile", "sharpness", "roughness", "seed", "basepercent", "bodypercent", "spikes", "spikemode", "spikelength", "spikebase", "lengthvar", "spread", "jitter"};
		for(String k: keys){
			Matcher m = Pattern.compile("\"property\\.buildguide\\." + k + "\": \"([^\"]*)\"").matcher(json);
			if(!m.find()){ check(false, k + ": key missing"); continue; }
			int w = width(m.group(1));
			check(w <= 76, "\"" + m.group(1) + "\": " + w + " px (limit 76)");
		}
		// Thickness was unclear: the clearest candidate that fits wins (2026-10-06)
		for(String c: new String[]{"Shell (blocks)", "Shell thick.", "Hollow wall"}) System.out.println("   candidate \"" + c + "\": " + width(c) + " px");
		Matcher wall = Pattern.compile("\"property\\.buildguide\\.wall\": \"([^\"]*)\"").matcher(json);
		check(wall.find() && wall.group(1).equals("Shell (blocks)") && width(wall.group(1)) <= 76, "Wall label is \"Shell (blocks)\" (" + width("Shell (blocks)") + " px, fits 76)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
