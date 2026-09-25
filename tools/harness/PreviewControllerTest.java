import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.screen.*;
import brentmaas.buildguide.common.shape.*;
// E1: PreviewController refresh rules (manual clock, real shapes, finishGeneration as update()'s
// finally calls it), shared camera and input routing
public class PreviewControllerTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static long now=1000;
	static Method finish; static Field fe;
	@SuppressWarnings("unchecked")
	static void generate(Shape s, boolean error, long... blocks) throws Exception {
		s.lock.lock(); try { Set<Long> e=(Set<Long>)fe.get(s); e.clear(); for(long b: blocks) e.add(b); s.ready=false; s.error=error; finish.invoke(s); } finally { s.lock.unlock(); }
	}
	public static void main(String[] a) throws Exception {
		finish=Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		fe=Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true);
		PreviewController c=new PreviewController(()->now);
		check(c.update(null)==null, "no shape: nothing to draw");
		ShapeBridge s=new ShapeBridge();
		check(c.update(s)==null, "shape never generated (not ready): null -> 'Generating...'");
		generate(s,false,LocalPos.pack(0,0,0));
		PreviewModel m1=c.update(s);
		check(m1!=null && m1.generation==1, "first generation: snapshot at once");
		check(c.update(s)==m1, "nothing changed: same model instance (renderer keeps mesh and texture)");
		// Throttle: a new generation 10 ms after the last snapshot waits
		now+=10; generate(s,false,LocalPos.pack(0,0,0),LocalPos.pack(1,0,0));
		check(c.update(s)==m1, "generation 2 after 10 ms: throttled, old model stays");
		now+=240; PreviewModel m2=c.update(s);
		check(m2!=m1 && m2.generation==2 && m2.positions.length==2, "250 ms after the last snapshot: generation 2 taken");
		// Burst of generations: at most one snapshot per 250 ms, and the last one always lands
		long burstStart=now; int snapshots=0; PreviewModel last=m2;
		for(int i=0;i<20;i++){ now+=20; generate(s,false,LocalPos.pack(0,0,0),LocalPos.pack(i,1,0)); PreviewModel m=c.update(s); if(m!=last){ snapshots++; last=m; } }
		check(snapshots<=2, "burst of 20 generations in 400 ms: "+snapshots+" snapshots (<= 2)");
		now+=250; PreviewModel settled=c.update(s);
		check(settled.generation==s.getGeneration(), "after the burst the last generation lands (model "+settled.generation+" = shape "+s.getGeneration()+")");
		// A failed generation does not move the generation: no refresh
		now+=1000; generate(s,true,LocalPos.pack(9,9,9));
		check(c.update(s)==settled, "failed/cancelled generation: model unchanged (partial blocks never shown)");
		// Still generating while a newer one is pending: previous model stays on screen
		now+=1000; s.lock.lock(); s.ready=false; s.lock.unlock();
		check(c.update(s)==settled, "shape generating: previous model stays, no flash to 'Generating...'");
		generate(s,false,LocalPos.pack(5,5,5));
		PreviewModel afterGen=c.update(s);
		check(afterGen!=settled && afterGen.generation==s.getGeneration(), "generation finished: taken on the next frame (throttle window long past)");
		// Shape change: immediate snapshot, camera kept
		c.camera.yaw=100; c.camera.zoom=2;
		ShapeBridge s2=new ShapeBridge(); generate(s2,false,LocalPos.pack(7,7,7));
		now+=1; PreviewModel other=c.update(s2);
		check(other!=null && other.positions[0]==LocalPos.pack(7,7,7), "other shape: snapshot at once, ignoring the throttle");
		check(c.camera.yaw==100 && c.camera.zoom==2, "other shape: camera kept (zoom is relative to the fit)");
		// Colours: 100 ms debounce, geometry shared
		ValidationState vs=s2.getValidationState(); List<Long> exp=Arrays.asList(LocalPos.pack(7,7,7));
		now+=1000; c.update(s2);
		vs.beginScan(exp); vs.setStatus(LocalPos.pack(7,7,7),ValidationState.OK,null); vs.endScan();
		PreviewModel beforeColour=c.update(s2);
		now+=50; vs.setStatus(LocalPos.pack(7,7,7),ValidationState.MISSING,null);
		check(c.update(s2)==beforeColour, "colour change 50 ms after the last refresh: waits");
		now+=60; PreviewModel coloured=c.update(s2);
		check(coloured!=beforeColour && coloured.status!=null && coloured.status[0]==ValidationState.MISSING && coloured.positions==beforeColour.positions, "after 100 ms: new colours, same positions array");
		// Input
		check(!c.mouseClicked(false,BaseScreen.MOUSE_LEFT,false) && !c.isDragging(), "click outside the area: not handled, no drag");
		check(!c.mouseDragged(10,0), "drag without a start in the area: ignored");
		check(c.mouseClicked(true,BaseScreen.MOUSE_LEFT,false) && c.isDragging(), "left click in the area: drag starts");
		float y0=c.camera.yaw; c.mouseDragged(10,0); check(c.camera.yaw==(y0+5)%360, "drag rotates the shared camera");
		c.mouseReleased(); check(!c.isDragging() && !c.mouseDragged(10,0), "release ends the drag");
		c.mouseClicked(true,BaseScreen.MOUSE_LEFT,false); c.attach(); check(!c.isDragging(), "attach (another view takes over) drops a pending drag");
		check(c.mouseScrolled(true,1) && Math.abs(c.camera.zoom-2.2f)<1e-4 && !c.mouseScrolled(false,1), "scroll zooms only in the area");
		check(c.mouseClicked(true,BaseScreen.MOUSE_MIDDLE,false) && c.camera.yaw==45 && c.camera.zoom==1, "middle button resets");
		c.camera.yaw=10; check(c.mouseClicked(true,BaseScreen.MOUSE_LEFT,true) && c.camera.yaw==45 && !c.isDragging(), "double click resets, no drag");
		// Shared camera across views: a second view attaching sees the same camera
		c.camera.pitch=12; c.attach(); check(c.camera.pitch==12, "camera survives a view change (D5)");
	}
}
