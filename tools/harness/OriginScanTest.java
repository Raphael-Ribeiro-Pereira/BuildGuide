import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.common.shape.ShapeBridge;
import brentmaas.buildguide.common.screen.ValidationListComponent;
import brentmaas.buildguide.common.screen.ValidationListComponent.Category;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;
import java.lang.reflect.*;
import java.util.*;
// P4: every local position in ValidationState is relative to the origin of the scan, not to the
// current origin, and scan requests are debounced by one clock (regeneration and origin changes).
// ShapeSet and State build without the loader: Unsafe instances with only the fields the origin methods use
public class OriginScanTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long w(ValidationState s, long local){ return ((long)(s.getScanOriginX()+LocalPos.unpackX(local))<<40) ^ ((long)(s.getScanOriginY()+LocalPos.unpackY(local))<<20) ^ (s.getScanOriginZ()+LocalPos.unpackZ(local)); }

	static boolean pending(Shape s){ return s.getValidationState().isScanRequested(); }
	static void clear(Shape... ss){ for(Shape s: ss) s.getValidationState().consumeScanRequest(); }
	static Object alloc(Class<?> c){ try { Field f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true); Object u=f.get(null); return u.getClass().getMethod("allocateInstance", Class.class).invoke(u, c); } catch(Exception e){ throw new RuntimeException(e); } }
	static ShapeSet newSet(Shape[] shapes, int x, int y, int z){ try { ShapeSet s=(ShapeSet)alloc(ShapeSet.class); s.shapes=shapes; Field o=ShapeSet.class.getDeclaredField("origin"); o.setAccessible(true); o.set(s, new ShapeSet.Origin(x,y,z)); return s; } catch(Exception e){ throw new RuntimeException(e); } }

	public static void main(String[] a){
		System.out.println("-- scan origin");
		List<Long> exp=new ArrayList<>(); for(int x=0;x<5;x++) exp.add(LocalPos.pack(x,0,0));
		ValidationState s=new ValidationState();
		s.beginScan(exp, 100, 64, -200);
		check(s.getScanOriginX()==100 && s.getScanOriginY()==64 && s.getScanOriginZ()==-200, "beginScan records the origin (100, 64, -200)");
		for(long p: exp) s.setStatus(p, p==LocalPos.pack(1,0,0)?ValidationState.IGNORED:ValidationState.OK, "Scaffolding");
		s.setNearBlocks(Arrays.asList(new ValidationState.NearBlock(LocalPos.pack(2,1,0),"Stone",1f)));
		s.setHighlightedPos(LocalPos.pack(2,1,0));
		s.endScan();
		long nearW=w(s,s.getNearBlocks().get(0).localPos), ignW=w(s,s.getPositions(ValidationState.IGNORED).get(0));
		check(nearW==w(s,LocalPos.pack(2,1,0)) && s.getScanOriginX()+2==102, "near block at world (102, 65, -200)");
		s.requestScan(1000); // origin moved: only a request, the results stay in the scan frame
		check(s.getScanOriginX()==100 && w(s,s.getNearBlocks().get(0).localPos)==nearW && w(s,s.getPositions(ValidationState.IGNORED).get(0))==ignW, "scan request (origin moved) keeps near/ignored world positions");
		check(s.isValidated() && s.getHighlightedPos()==LocalPos.pack(2,1,0), "state stays validated and highlighted until the rescan");
		s.invalidate();
		check(s.getScanOriginX()==100, "invalidate (regeneration) keeps the last scan origin");
		s.beginScan(exp, 105, 64, -200);
		check(s.getScanOriginX()==105 && s.getNearCount()==0, "rescan at the new origin replaces origin and results");
		ValidationState d=new ValidationState(); d.beginScan(exp);
		check(d.getScanOriginX()==0 && d.getScanOriginY()==0 && d.getScanOriginZ()==0, "one-argument beginScan: origin (0, 0, 0)");

		System.out.println("-- overlay offset (scan origin - current origin)");
		ValidationState o=new ValidationState(); o.beginScan(exp, 100, 64, -200);
		int[] z=o.getScanOffset(100, 64, -200);
		check(z[0]==0 && z[1]==0 && z[2]==0, "origins equal: offset exactly {0, 0, 0}, no extra transform (overlay identical to before P4)");
		int[] m=o.getScanOffset(103, 60, -200);
		check(m[0]==-3 && m[1]==4 && m[2]==0, "origin moved by (+3, -4, 0): offset (-3, +4, 0) puts the cubes back where the scan saw them");
		check(103+m[0]+2==100+2 && 60+m[1]+1==64+1, "current origin + offset + local = scan origin + local (world position unchanged)");
		int[] n=o.getScanOffset(-5, 300, 7); check(n[0]==105 && n[1]==-236 && n[2]==-207, "any origin: integer offset, no rounding");

		System.out.println("-- error list (world coordinates from the scan origin)");
		ShapeBridge sb=new ShapeBridge(); ValidationState ls=sb.getValidationState();
		ls.beginScan(exp, 100, 64, -200); for(long p: exp) ls.setStatus(p, ValidationState.OK, null);
		ls.setNearBlocks(Arrays.asList(new ValidationState.NearBlock(LocalPos.pack(2,1,0),"Stone",1f))); ls.endScan();
		String before=ValidationListComponent.buildEntries(sb, Category.ERRORS).titles.get(0).getTranslationKey();
		check(before.startsWith("[102, 65, -200]"), "row at world (102, 65, -200): "+before);
		ls.requestScan(1000);
		check(ValidationListComponent.buildEntries(sb, Category.ERRORS).titles.get(0).getTranslationKey().equals(before), "origin moved (scan requested): row unchanged until the rescan");

		System.out.println("-- debounce (one clock for regeneration and origin changes)");
		ValidationState q=new ValidationState();
		check(!q.isScanDue(10_000, 300), "no request: never due");
		long t=5000; for(int i=0;i<5;i++){ q.requestScan(t); check(!q.isScanDue(t+100-1,300), "click "+(i+1)+" at "+t+": not due 99 ms later"); t+=100; }
		long last=t-100;
		check(!q.isScanDue(last+299,300), "299 ms after the last click: still waiting");
		check(q.isScanDue(last+300,300), "300 ms after the last click: due");
		int scans=0; for(long now=5000; now<=last+1000; now+=10) if(q.isScanDue(now,300) && q.consumeScanRequest()) scans++;
		check(scans==1, "5 clicks 100 ms apart -> exactly 1 scan (got "+scans+")");
		check(!q.isScanDue(last+5000,300) && !q.isScanRequested(), "consumed: nothing pending");
		System.out.println("-- ShapeSet: every way of moving the origin requests a scan");
		ShapeBridge b1=new ShapeBridge(), b2=new ShapeBridge();
		ShapeSet set=newSet(new Shape[]{b1,null,b2}, 10, 64, 10);
		check(!pending(b1) && !pending(b2), "fresh: nothing pending");
		set.setOriginX(11); check(pending(b1) && pending(b2), "setOriginX: both instantiated shapes of the set (null slot skipped)"); clear(b1,b2);
		set.setOriginY(65); check(pending(b1), "setOriginY"); clear(b1,b2);
		set.setOriginZ(9); check(pending(b1), "setOriginZ"); clear(b1,b2);
		set.setOrigin(0,0,0); check(pending(b1), "setOrigin (Enter in the fields)"); clear(b1,b2);
		set.shiftOrigin(1,0,0); check(pending(b1) && set.getOriginX()==1, "shiftOrigin (+ / - buttons, key binds)"); clear(b1,b2);
		set.setOrigin(1,0,0); set.setOriginX(1); set.shiftOrigin(0,0,0);
		check(!pending(b1) && !pending(b2), "same value (setOrigin, setOriginX, shift by 0): no scan request");
		BuildGuide.shapeHandler=(brentmaas.buildguide.common.shape.IShapeHandler)Proxy.newProxyInstance(OriginScanTest.class.getClassLoader(), new Class<?>[]{brentmaas.buildguide.common.shape.IShapeHandler.class}, (px,mth,args)->mth.getName().equals("getPlayerPosition")?new ShapeSet.Origin(50,70,-3):null);
		set.resetOrigin(); check(pending(b1) && set.getOriginX()==50, "resetOrigin (Set origin button, key): moves to the player, requests"); clear(b1,b2);
		set.resetOrigin(); check(!pending(b1), "resetOrigin when already at the player: no request");

		System.out.println("-- global origin (Shape list): State.shiftOrigins moves every set");
		ShapeBridge c1=new ShapeBridge(); ShapeSet set2=newSet(new Shape[]{c1}, 0, 0, 0);
		State st=(State)alloc(State.class); ArrayList<ShapeSet> sets=new ArrayList<>(Arrays.asList(set,set2)); st.shapeSets=sets;
		c1.getValidationState().beginScan(exp, 0, 0, 0); c1.getValidationState().setNearBlocks(Arrays.asList(new ValidationState.NearBlock(LocalPos.pack(2,1,0),"Stone",1f))); c1.getValidationState().endScan();
		st.shiftOrigins(4,0,0);
		check(pending(b1) && pending(b2) && pending(c1), "every shape of every set gets a request");
		check(set.getOriginX()==54 && set2.getOriginX()==4, "both origins moved by +4");
		ValidationState cv=c1.getValidationState();
		check(cv.getScanOriginX()+LocalPos.unpackX(cv.getNearBlocks().get(0).localPos)==2, "near block of the second set stays at world x 2 until the rescan");
		int[] co=cv.getScanOffset(set2.getOriginX(), set2.getOriginY(), set2.getOriginZ());
		check(co[0]==-4 && co[1]==0 && co[2]==0, "its overlay offset is (-4, 0, 0)");

		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}
}
