import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.property.Property;
import brentmaas.buildguide.common.property.PropertyBlockId;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.screen.widget.ITextField;
import brentmaas.buildguide.common.shape.*;
// Palette per layer (4D), the Island side: four block ids after Cut 3 %, URL-encoded in the save, empty by
// default (older islands load unchanged); typed ids are checked and normalised by the loader (a fake registry
// here), an invalid one is not applied; the Hand button with a fake provider (a block, no block); the palette
// travels with the generation, like the layers.
public class PaletteIslandTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static class TestIsland extends ShapeIsland { int updates = 0; @Override public void update(){ updates++; } }
	@SuppressWarnings("unchecked") static void setProp(ShapeIsland s, String f, Object v) throws Exception { Field x = ShapeIsland.class.getDeclaredField(f); x.setAccessible(true); ((Property<Object>) x.get(s)).value = v; }
	static PropertyBlockId block(ShapeIsland s, int i) throws Exception { Field x = ShapeIsland.class.getDeclaredField("propertyBlock" + i); x.setAccessible(true); return (PropertyBlockId) x.get(s); }

	// A text field that remembers its text and colour
	static final List<Field2> fields = new ArrayList<>();
	static class Field2 implements ITextField {
		String text = ""; int colour = 0xFFFFFF; Runnable enter; Field2(){ fields.add(this); }
		public void setTextValue(String t){ text = t; } public void setTextColour(int c){ colour = c; } public String getTextValue(){ return text; }
		public void setOnEnter(Runnable r){ enter = r; } public void setYPosition(int y){} public void setVisibility(boolean v){}
	}
	static class Widgets extends PresetTest.FakeWidgets { @Override public ITextField createTextField(int x, int y, int w, int h, String v){ return new Field2(); } }
	// A fake registry: these blocks exist (air too, which is no block for a palette)
	static final Set<String> REGISTRY = new HashSet<>(Arrays.asList("minecraft:stone", "minecraft:dirt", "minecraft:cobblestone", "minecraft:air", "create:andesite_casing"));
	static String held = null; static final List<String> chat = new ArrayList<>();

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new Widgets();
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(PaletteIslandTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> { if(m.getName().equals("sendChatMessage")) chat.add((String) x[0]); return null; });
		BuildGuide.shapeHandler = new IShapeHandler(){
			public IShapeBuffer newBuffer(){ return null; }
			public ShapeSet.Origin getPlayerPosition(){ return new ShapeSet.Origin(0, 0, 0); }
			@Override public String normalizeBlockId(String typed){ String t = typed.trim().toLowerCase(Locale.ROOT); if(!t.contains(":")) t = "minecraft:" + t; return REGISTRY.contains(t) && !t.equals("minecraft:air") ? t : null; }
			@Override public String heldBlockId(){ return held; }
		};

		System.out.println("-- persistence");
		TestIsland fresh = new TestIsland();
		String save = fresh.toPersistence();
		check(fresh.properties.size() == 44 && save.endsWith(",false,25,50,75,,,,,ALL,0"), "44 persisted properties: Block 1 to Block 4 after Cut 3 %, empty by default, then the focus (" + save.substring(save.length() - 26) + ")");
		String old = save.substring(0, save.length() - ",,,,,ALL,0".length());
		check(old.split(",").length == 38, "an island saved before the palette (4C): 38 values");
		TestIsland loaded = new TestIsland(); loaded.restorePersistence(old);
		check(!loaded.error && loaded.toPersistence().equals(save) && block(loaded, 1).value.isEmpty() && block(loaded, 4).value.isEmpty(), "it loads with the four ids empty and saves the same text");
		TestIsland withIds = new TestIsland(); setProp(withIds, "propertyLayers", true);
		block(withIds, 1).apply("minecraft:stone"); block(withIds, 3).apply("create:andesite_casing");
		String encoded = withIds.toPersistence();
		check(encoded.endsWith(",minecraft%3Astone,,create%3Aandesite_casing,,ALL,0"), "ids are URL-encoded in the save (':' -> %3A), like the instance name: " + encoded.substring(encoded.length() - 52));
		TestIsland back = new TestIsland(); back.restorePersistence(encoded);
		check(!back.error && block(back, 1).value.equals("minecraft:stone") && block(back, 2).value.isEmpty() && block(back, 3).value.equals("create:andesite_casing") && back.toPersistence().equals(encoded), "and come back exactly (a mod's namespace too)");
		check(!encoded.replace("%3A", "").contains(":") && encoded.split(";").length == 1 && !encoded.contains("="), "nothing in the save can break its ',' ';' '=' separators");

		System.out.println("-- typed ids and the Hand button");
		TestIsland s = new TestIsland(); PropertyBlockId b1 = block(s, 1); fields.clear(); b1.getWidgetList(); Field2 field = fields.get(0);
		check(b1.apply("stone") && b1.value.equals("minecraft:stone") && field.text.equals("minecraft:stone") && s.updates == 1, "\"stone\" is normalised to \"minecraft:stone\", shown in the field, one regeneration");
		check(b1.apply("  Minecraft:Dirt ") && b1.value.equals("minecraft:dirt"), "case and spaces do not matter (\"  Minecraft:Dirt \")");
		check(!b1.apply("notablock") && b1.value.equals("minecraft:dirt") && field.colour == 0xFF0000 && s.updates == 2, "an id that is no block: the field turns red, nothing applied, no regeneration");
		check(!b1.apply("air") && b1.value.equals("minecraft:dirt"), "air is not a block for a palette");
		check(b1.apply("") && b1.value.isEmpty() && field.colour == 0xFFFFFF && s.updates == 3, "an empty field clears the layer's block (any solid again)");
		field.text = "cobblestone"; field.enter.run();
		check(b1.value.equals("minecraft:cobblestone"), "Enter (and live apply after a pause, which runs the same action) applies the typed id");
		held = "minecraft:stone"; chat.clear();
		check(b1.fromHand() && b1.value.equals("minecraft:stone") && chat.isEmpty(), "Hand with a block in the main hand: its id is applied");
		held = null;
		check(!b1.fromHand() && b1.value.equals("minecraft:stone") && chat.isEmpty() && field.text.equals("screen.buildguide.holdablock") && field.colour == 0xFF0000, "Hand with no block (empty hand, or an item that is not a block): \"Hold a block\" in red in the field, nothing in the chat, nothing applied");

		System.out.println("-- the palette travels with the generation");
		TestIsland g = new TestIsland(); IslandLayersTest.set(g); block(g, 2).apply("dirt");
		IslandLayersTest.generate(g);
		check(g.getPalette() == null && g.getLayers() == null, "Layers off: no palette (the validation of before)");
		setProp(g, "propertyLayers", true); IslandLayersTest.generate(g);
		check(Arrays.equals(g.getPalette(), new String[]{"", "minecraft:dirt", "", ""}) && g.getLayers() != null, "Layers on: the generation's palette, one id per layer (\"\" = any solid)");
		block(g, 2).apply("stone");
		check(g.getPalette()[1].equals("minecraft:dirt"), "a typed id changes the palette only with the next generation (which rescans)");
		Shape cube = new ShapeCuboid();
		check(cube.getPalette() == null, "other shapes: no palette (Shape default)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
