import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.property.PropertyFloat;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.*;
import brentmaas.buildguide.common.shape.*;
// Optional step of PropertyFloat's -/+ buttons (Island block A): Island's 0..1 properties move by
// 0.05 and stay in [0, 1] without floating-point residue; an existing shape's float still moves by 1.
// The widget handler is PresetTest's fake, extended to keep the buttons' actions and the field's text.
public class FloatStepTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	static final List<Object[]> buttons = new ArrayList<>(); // {label, action}
	static class Widgets extends PresetTest.FakeWidgets {
		@Override public IButton createButton(int x,int y,int w,int h,Translatable t,IButton.IPressable p){ buttons.add(new Object[]{t.getTranslationKey(), p}); return super.createButton(x, y, w, h, t, p); }
		@Override public ITextField createTextField(int x,int y,int w,int h,String v){
			String[] text = {v};
			return (ITextField) Proxy.newProxyInstance(FloatStepTest.class.getClassLoader(), new Class<?>[]{ITextField.class}, (p,m,a)->{
				if(m.getName().equals("setTextValue")){ text[0] = (String) a[0]; return null; }
				if(m.getName().equals("getTextValue")) return text[0];
				Class<?> r = m.getReturnType(); if(r == boolean.class) return false; if(r == int.class) return 0; return null; });
		}
	}

	// The -/+ actions and the text field of one property (its widgets are made on first access)
	static IButton.IPressable minus, plus;
	static ITextField field;
	static void bind(PropertyFloat p){
		buttons.clear(); List<IWidget> ws = p.getWidgetList();
		minus = null; plus = null; for(Object[] b: buttons){ if("-".equals(b[0])) minus = (IButton.IPressable) b[1]; if("+".equals(b[0])) plus = (IButton.IPressable) b[1]; }
		field = null; for(IWidget w: ws) if(w instanceof ITextField) field = (ITextField) w;
	}
	// Regeneration is not under test: update() only counts (the shapes' own -/+ handlers still run)
	static int updates = 0;
	static class TestIsland extends ShapeIsland { @Override public void update(){ updates++; } }
	static class TestCone extends ShapeCone { @Override public void update(){ updates++; } }
	static PropertyFloat prop(Object shape, String name) throws Exception { Field f = shape.getClass().getSuperclass().getDeclaredField(name); f.setAccessible(true); return (PropertyFloat) f.get(shape); }
	static void press(IButton.IPressable b, int n){ for(int i = 0; i < n; i++) b.onPress(); }

	public static void main(String[] a) throws Exception {
		BuildGuide.widgetHandler = new Widgets();
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(FloatStepTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p,m,x)->null);

		System.out.println("-- Island: step 0.05, held in [0, 1]");
		ShapeIsland island = new TestIsland();
		for(String name: new String[]{"propertyRoundness", "propertyEdgeAmplitude", "propertySharpness", "propertyRoughness"}){
			PropertyFloat p = prop(island, name); bind(p);
			p.value = 0.0f; press(plus, 3);
			check(p.value == 0.15f && "0.15".equals(field.getTextValue()), name + ": 0 + 3 x 0.05 = " + p.value + ", field \"" + field.getTextValue() + "\"");
			press(minus, 1);
			check(p.value == 0.1f && "0.1".equals(field.getTextValue()), name + ": - once = " + p.value);
			press(minus, 5);
			check(p.value == 0.0f && "0.0".equals(field.getTextValue()), name + ": stops at 0 (" + p.value + ")");
			p.value = 0.9f; press(plus, 5);
			check(p.value == 1.0f && "1.0".equals(field.getTextValue()), name + ": stops at 1 (" + p.value + ")");
			p.value = 0.0f; boolean clean = true; String seen = "";
			for(int i = 1; i <= 20; i++){ press(plus, 1); String want = new java.math.BigDecimal("0.05").multiply(new java.math.BigDecimal(i)).stripTrailingZeros().toPlainString(); if(!want.contains(".")) want += ".0"; clean &= want.equals(field.getTextValue()); if(!want.equals(field.getTextValue())) seen += " " + field.getTextValue(); }
			check(clean, name + ": 0.05 .. 1.0 in 20 presses, every value reads as a clean decimal" + seen);
		}

		System.out.println("-- existing shapes: floats still move by 1");
		ShapeCone cone = new TestCone();
		for(String name: new String[]{"propertyHeight", "propertyTopRadius"}){
			PropertyFloat p = prop(cone, name); bind(p);
			p.value = 2.5f; press(plus, 1);
			check(p.value == 3.5f && "3.5".equals(field.getTextValue()), "Cone " + name + ": 2.5 + = " + p.value + " (not snapped to a grid)");
			press(minus, 3);
			check(p.value == 0.5f, "Cone " + name + ": - three times = " + p.value);
			press(minus, 2);
			check(p.value == -1.5f, "Cone " + name + ": no clamp, goes negative as before (" + p.value + ")");
		}
		PropertyFloat legacy = new PropertyFloat(0.1f, new Translatable("x"), null); bind(legacy);
		press(plus, 1);
		check(legacy.value == 0.1f + 1, "PropertyFloat without a step: 0.1 + = 0.1f + 1 exactly as before (" + legacy.value + ")");
		check(updates > 0, "every press regenerates through update() (" + updates + " calls)");
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
	}
}
