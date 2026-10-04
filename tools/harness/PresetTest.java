import brentmaas.buildguide.common.PresetStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Proxy;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.screen.BaseScreen;
// E8: preset slots. [PARE-2]: the file store (slots, format, Windows-safe replace, bad lines),
// free-text encoding and the entry parser. [PARE-3]: the instance name in the world save (ShapeSet
// persistence). ShapeSet preset round-trips and the unknown shape type come with [PARE-5]
public class PresetTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static String read(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), "UTF-8"); }

	// Inert widgets: property setters touch their widgets, which the harness never had. Every call returns a default
	static <W> W inert(Class<W> c){ return c.cast(Proxy.newProxyInstance(PresetTest.class.getClassLoader(), new Class<?>[]{c}, (p,m,x)->{ Class<?> r=m.getReturnType(); if(r==boolean.class) return false; if(r==int.class) return 0; if(r==double.class) return 0.0; if(r==float.class) return 0f; if(r==long.class) return 0L; if(r==String.class) return ""; return null; })); }
	static class FakeWidgets extends brentmaas.buildguide.common.screen.widget.AbstractWidgetHandler {
		public brentmaas.buildguide.common.screen.widget.IButton createButton(int x,int y,int w,int h,brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable t,brentmaas.buildguide.common.screen.widget.IButton.IPressable p){ return inert(brentmaas.buildguide.common.screen.widget.IButton.class); }
		public brentmaas.buildguide.common.screen.widget.ITextField createTextField(int x,int y,int w,int h,String v){ return inert(brentmaas.buildguide.common.screen.widget.ITextField.class); }
		public brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton createCheckbox(int x,int y,int w,int h,brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable t,boolean c,boolean d,brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton.IPressable p){ return inert(brentmaas.buildguide.common.screen.widget.ICheckboxRunnableButton.class); }
		public brentmaas.buildguide.common.screen.widget.ISlider createSlider(int x,int y,int w,int h,brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable n,double a,double b,double v){ return inert(brentmaas.buildguide.common.screen.widget.ISlider.class); }
		public brentmaas.buildguide.common.screen.widget.IShapeList createShapelist(int l,int r,int t,int b,int s,Runnable u){ return inert(brentmaas.buildguide.common.screen.widget.IShapeList.class); }
		public brentmaas.buildguide.common.screen.widget.ISelectorList createSelectorList(int l,int r,int t,int b,int s,List<brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable> ts,int c,brentmaas.buildguide.common.screen.widget.ISelectorList.ISelectorListCallback cb){ return inert(brentmaas.buildguide.common.screen.widget.ISelectorList.class); }
	}

	static long now=1000;
	static void confirmation(){
		System.out.println("-- two-click confirmation (injected clock)");
		brentmaas.buildguide.common.screen.PresetConfirm c=new brentmaas.buildguide.common.screen.PresetConfirm(()->now);
		brentmaas.buildguide.common.screen.PresetConfirm.Action L=brentmaas.buildguide.common.screen.PresetConfirm.Action.LOAD, C=brentmaas.buildguide.common.screen.PresetConfirm.Action.CLEAR, S=brentmaas.buildguide.common.screen.PresetConfirm.Action.SAVE;
		check(c.press(0,S,false) && !c.isArmed(0,S), "Save on an empty slot: runs at once, nothing armed");
		check(!c.press(0,S,true) && c.isArmed(0,S), "Save on an occupied slot: first click only arms (\"Overwrite?\")");
		now+=1000; check(c.press(0,S,true) && !c.isArmed(0,S), "second click within 3 s: runs, disarmed");
		check(!c.press(1,L,true) && c.isArmed(1,L), "Load: first click arms (\"Replace?\")");
		check(!c.press(1,C,true) && !c.isArmed(1,L) && c.isArmed(1,C), "another button: Load disarmed, Clear armed instead (one at a time)");
		check(!c.press(2,C,true) && !c.isArmed(1,C) && c.isArmed(2,C), "same action on another slot: moves the arming, does not run");
		now+=2999; check(c.isArmed(2,C), "2999 ms later: still armed");
		now+=1; check(!c.isArmed(2,C), "3000 ms: disarmed by itself (label back to \"Clear\")");
		check(!c.press(2,C,true), "click after the timeout: arms again, does not run");
		now+=3500; check(!c.press(2,C,true) && c.isArmed(2,C), "late second click (3.5 s): treated as a first click");
		c.disarm(); check(!c.isArmed(2,C), "disarm clears");
	}

	static void presetString(){
		System.out.println("-- preset data (ShapeSet.toPresetString) and slot label parts");
		ShapeSet s=newSet(); s.setOrigin(-7,70,300); s.setName("Minha; ponte");
		s.getExclusionBox(0).enabled=true; s.getExclusionBox(0).maxX=4;
		String d=s.toPresetString("Mundo Sobreviv00eancia 2");
		Map<String,String> e=PresetStore.entries(d);
		check(new ArrayList<>(e.keySet()).equals(Arrays.asList("index","origin","exclusions",ShapeCuboid.class.getName(),"name","world")), "keys: type, origin, exclusions, shape values, name, world "+e.keySet());
		check(e.get("origin").equals("-7,70,300") && e.get("exclusions").startsWith("1,0,0,0,4,0,0"), "origin and exclusion boxes");
		check(!d.contains("shapeColour") && !d.contains("visible") && !d.contains("CubeSize"), "colours, visibility and cube sizes left out (the set's look)");
		check(d.indexOf(10)<0, "one line (fits a slot)");
		String[] p=PresetStore.describe(d);
		check(ShapeCuboid.class.getName().equals(p[0]) && "Minha; ponte".equals(p[1]) && "Mundo Sobreviv00eancia 2".equals(p[2]), "label parts: type class, name, world");
		s.setName(null); String[] q=PresetStore.describe(s.toPresetString("W"));
		check(q[1]==null && "W".equals(q[2]), "no name typed: name part null (label shows type - world)");
		String[] u=PresetStore.describe("index=0;origin=0,0,0;com.example.FutureShape=1,2;world=X;");
		check("com.example.FutureShape".equals(u[0]) && ShapeRegistry.getShapeId(u[0])<0, "shape class this jar does not know: reported as is, registry says -1 (label \"?\")");
		check(PresetStore.describe("")[0]==null && PresetStore.describe("").length==3, "empty data: no class, no crash");
	}

	@SuppressWarnings("unchecked")
	static void loading(){
		System.out.println("-- loading a preset (ShapeSet.applyPresetValues; applyPreset adds the one update())");
		ShapeSet a=newSet(); Shape ac=a.getShapeIfAvailable(0);
		for(brentmaas.buildguide.common.property.Property<?> p: ac.properties) if(p.value instanceof Integer){ ((brentmaas.buildguide.common.property.Property<Integer>)p).value=(Integer)p.value+3; break; }
		a.setName("Ponte; do vale"); a.setOrigin(5,6,7); a.getExclusionBox(1).enabled=true; a.getExclusionBox(1).minY=-2;
		String d=a.toPresetString("W");
		String sphereData=new ShapeSphere().toPersistence();
		ShapeSet b=new ShapeSet(1); b.resetOrigin(); Shape sphere=b.getShapeIfAvailable(1);
		check(b.getShapeIfAvailable(0)==null, "target set has only a Sphere: no Cuboid instance yet");
		BaseScreen.shouldUpdatePersistence=false;
		check(b.applyPresetValues(d)==null, "load: no problem reported");
		check(b.getIndex()==0 && b.getShapeIfAvailable(0)!=null, "type switched to Cuboid, instance created (with its defaults) because the set had none");
		check(b.getShapeIfAvailable(0).toPersistence().equals(ac.toPersistence()), "shape values equal the saved ones: "+ac.toPersistence());
		check(b.getOriginX()==5 && b.getOriginY()==6 && b.getOriginZ()==7 && "Ponte; do vale".equals(b.getName()), "origin (5, 6, 7) and name restored");
		check(b.getExclusionBox(1).enabled && b.getExclusionBox(1).minY==-2 && !b.getExclusionBox(0).enabled, "exclusion boxes replaced by the preset's");
		check(b.getShapeIfAvailable(1)==sphere, "the Sphere instance is still in the set (reachable from the type dropdown)");
		check(BaseScreen.shouldUpdatePersistence, "shouldUpdatePersistence set: the world save writes the loaded shape");
		check(b.getShapeIfAvailable(0).getValidationState().isScanRequested(), "scan requested for the loaded shape");
		ShapeSet c=newSet(); Shape cube=c.getShapeIfAvailable(0); c.applyPresetValues(d);
		check(c.getShapeIfAvailable(0)==cube && cube.toPersistence().equals(ac.toPersistence()), "a set that already has a Cuboid: that instance is reused, values replaced");

		System.out.println("-- refused loads leave the set untouched");
		ShapeSet f=newSet(); f.setName("keep"); String before=f.toPersistence();
		check("screen.buildguide.preset.unknowntype".equals(f.applyPresetValues("index=0;origin=1,2,3;com.example.FutureShape=1,2;name=x;world=Y;")) && f.toPersistence().equals(before), "unknown shape type: refused with a message key, nothing changed");
		check("screen.buildguide.preset.invalid".equals(f.applyPresetValues("origin=1,2;"+ShapeCuboid.class.getName()+"=1;")) && f.toPersistence().equals(before), "damaged origin: refused, nothing changed");
		check("screen.buildguide.preset.invalid".equals(f.applyPresetValues("origin=1,2,3;exclusions=1,a,0,0,0,0,0;"+ShapeCuboid.class.getName()+"=1;")) && f.toPersistence().equals(before), "damaged exclusions: refused before anything is applied");

		System.out.println("-- older preset with fewer values: full reset, protected points included");
		String def=new ShapeSpline().toPersistence();
		ShapeSet s=new ShapeSet(2); s.resetOrigin(); Shape sp=s.getShapeIfAvailable(2);
		for(brentmaas.buildguide.common.property.Property<?> p: sp.properties) if(p.value instanceof Integer) ((brentmaas.buildguide.common.property.Property<Integer>)p).value=(Integer)p.value+7;
		check(!sp.toPersistence().equals(def), "Spline changed everywhere, points and point count included");
		String[] dv=def.split(",");
		check(s.applyPresetValues("index=2;origin=0,0,0;"+ShapeSpline.class.getName()+"="+dv[0]+","+dv[1]+";")==null, "preset with only 2 values loads");
		String got=sp.toPersistence(); String defNoCount=def.substring(0, def.lastIndexOf(','));
		check(got.startsWith(defNoCount+","), "every value back to its default, protected control points included: "+got);
		check(got.endsWith(",5"), "Point count 5 (maxPoints): the Spline's own rule for data older than the count field (all 5 points were used)");
		String[] full=def.split(","); full[full.length-1]="3";
		check(s.applyPresetValues("index=2;origin=0,0,0;"+ShapeSpline.class.getName()+"="+String.join(",",full)+";")==null && sp.toPersistence().endsWith(",3"), "preset that has the count: count restored as saved (3)");
		check(s.getShapeIfAvailable(2)==sp, "same Spline instance");
	}

	static ShapeSet newSet(){ ShapeSet s=new ShapeSet(0); s.resetOrigin(); return s; }

	static void nameInWorldSave(){
		System.out.println("-- instance name in the world save (ShapeSet persistence)");
		ShapeRegistry.registerShape(ShapeCuboid.class, "shape.buildguide.cuboid");
		ShapeRegistry.registerShape(ShapeSphere.class, "shape.buildguide.sphere");
		ShapeRegistry.registerShape(ShapeSpline.class, "shape.buildguide.spline");
		BuildGuide.widgetHandler=new FakeWidgets();
		BuildGuide.shapeHandler=(IShapeHandler)Proxy.newProxyInstance(PresetTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p,m,x)->m.getName().equals("getPlayerPosition")?new ShapeSet.Origin(10,64,-5):null);
		ShapeSet s=newSet();
		String plain=s.toPersistence();
		check(s.getName()==null && !plain.contains("name="), "no name typed: no name= entry (same text as before E8)");
		List<String> keys=new ArrayList<>(PresetStore.entries(plain).keySet());
		check(keys.subList(0,8).equals(Arrays.asList("index","origin","visible","shapeColour","originColour","shapeCubeSize","originCubeSize","exclusions")) && keys.get(8).equals(ShapeCuboid.class.getName()), "old keys in their old order, then the shape: "+keys);
		String name="Ponte; norte=1 100%";
		s.setName("  "+name+"  ");
		String withName=s.toPersistence();
		List<String> k2=new ArrayList<>(PresetStore.entries(withName).keySet());
		check(withName.startsWith(plain) && k2.get(k2.size()-1).equals("name"), "name= appended after everything else (old text unchanged in front)");
		ShapeSet r=newSet(); r.restorePersistence(withName);
		check(name.equals(r.getName()), "round trip: \""+r.getName()+"\" (trimmed, ; = % survive)");
		ShapeSet old=newSet(); old.restorePersistence(plain);
		check(old.getName()==null, "save without name= (older jar): default name");
		check(ShapeRegistry.getShapeId("name")<0, "older jar reading name=: not a shape class, its else branch skips it");
		ShapeSet e=newSet(); e.restorePersistence(plain+"name=%20%20;"); check(e.getName()==null, "blank name= loads as the default");
		s.setName(""); check(s.getName()==null && !s.toPersistence().contains("name="), "clearing the name removes the entry");
	}

	public static void main(String[] a) throws Exception {
		File dir=Files.createTempDirectory("bgpresets").toFile(); File f=new File(dir, PresetStore.fileName);
		System.out.println("-- store");
		PresetStore s=new PresetStore(dir);
		check(!f.exists() && s.isEmpty(0) && s.isEmpty(1) && s.isEmpty(2), "no file: three empty slots, nothing written");
		s.set(1, "index=2;origin=1,2,3;");
		check(f.exists() && read(f).equals("slot2=index=2;origin=1,2,3;\n"), "save slot 2: one line 'slot2=...', empty slots not written");
		s.set(0, "index=0;"); s.set(1, "index=5;");
		check(read(f).equals("slot1=index=0;\nslot2=index=5;\n"), "overwrite slot 2 with the file already there (Windows replace), slots in order");
		check(!new File(dir, PresetStore.fileName+".tmp").exists(), "no temporary file left behind");
		PresetStore r=new PresetStore(dir);
		check("index=0;".equals(r.get(0)) && "index=5;".equals(r.get(1)) && r.isEmpty(2), "reload from the file: same slots");
		r.clear(0);
		check(r.isEmpty(0) && read(f).equals("slot2=index=5;\n") && new PresetStore(dir).isEmpty(0), "clear slot 1: gone from memory and from the file");
		boolean threw=false; try { r.set(2, "a\nslot1=x"); } catch(IllegalArgumentException e){ threw=true; }
		check(threw && r.isEmpty(2), "multi-line data refused (cannot inject another slot)");
		threw=false; try { r.set(2, ""); } catch(IllegalArgumentException e){ threw=true; }
		check(threw, "empty data refused (empty = cleared)");

		System.out.println("-- bad lines");
		Files.write(f.toPath(), "garbage\nslot=x\nslotX=y\nslot0=z\nslot4=w\nslot3=\n# comment\nslot3=index=1;\nslot1=ok;\n".getBytes("UTF-8"));
		PresetStore b=new PresetStore(dir);
		check("ok;".equals(b.get(0)) && b.isEmpty(1) && "index=1;".equals(b.get(2)), "malformed, out-of-range and empty lines skipped; valid ones load");
		String[] sl=new String[3]; PresetStore.parseLine("slot2=a=b;c=d", sl);
		check("a=b;c=d".equals(sl[1]), "only the first '=' separates the slot from its data");

		System.out.println("-- encoding and entries");
		String name="Ponte; lado=norte 100% ç\nfim";
		String e=PresetStore.encode(name);
		check(e.indexOf(';')<0 && e.indexOf('=')<0 && e.indexOf('\n')<0, "encoded name has no ';', '=' or line break: "+e);
		check(PresetStore.decode(e).equals(name), "decode(encode(name)) == name (UTF-8)");
		check(PresetStore.decode("100%").equals("100%"), "malformed escape: kept as is, no exception");
		Map<String,String> m=PresetStore.entries("index=3;origin=1,-2,3;name="+e+";junk;brentmaas.X=1,2,3");
		check(new ArrayList<>(m.keySet()).equals(Arrays.asList("index","origin","name","brentmaas.X")), "entries in order, entry without '=' skipped");
		check(PresetStore.decode(m.get("name")).equals(name) && m.get("origin").equals("1,-2,3"), "values intact");
		check(PresetStore.entries(null).isEmpty(), "null data: no entries");

		System.out.println("-- in memory (folder null)");
		PresetStore mem=new PresetStore(null); mem.set(0,"x;"); check("x;".equals(mem.get(0)), "works without a file");
		for(File x: dir.listFiles()) x.delete(); dir.delete();

		nameInWorldSave();
		confirmation();
		presetString();
		loading();
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}
}
