import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class IncrementalTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	public static void main(String[] a) {
		ValidationState s = new ValidationState();
		List<Long> exp = new ArrayList<>(); for(int x=0;x<10;x++) for(int z=0;z<10;z++) exp.add(LocalPos.pack(x,0,z));
		check(!s.isInRange(0,0,0), "not validated -> out of range");
		s.beginScan(exp); for(long p: exp) s.setStatus(p, ValidationState.MISSING, null); s.endScan();
		check(s.getMissing()==100 && s.getOk()==0, "scan: all missing");
		check(s.isInRange(-2,-2,-2) && s.isInRange(11,2,11) && !s.isInRange(12,0,0) && !s.isInRange(0,3,0), "bounds expanded by 2");
		// place expected block (solid): missing -> ok
		s.updateBlock(LocalPos.pack(3,0,3), false, true, false, null); check(s.getOk()==1 && s.getMissing()==99, "place expected -> ok");
		// break it: ok -> missing
		s.updateBlock(LocalPos.pack(3,0,3), true, false, false, null); check(s.getOk()==0 && s.getMissing()==100, "break expected -> missing");
		// wrong block (non-solid, e.g. torch) on expected
		s.updateBlock(LocalPos.pack(3,0,3), false, false, false, "Torch"); check(s.getMissing()==100 && s.getOk()==0 && s.getNearCount()==0, "non-solid on expected (torch) -> missing, no error");
		// near: solid block 1 above the slab -> near
		s.updateBlock(LocalPos.pack(5,1,5), false, true, false, null); check(s.getNearCount()==1, "solid next to shape -> near");
		// far solid block (y=3, distance 3) -> not near, but in range? y=3 > maxY+2 -> out of range in practice; call directly:
		s.updateBlock(LocalPos.pack(5,3,5), false, true, false, null); check(s.getNearCount()==1, "solid at distance 3 -> not near");
		// break the near block -> removed
		s.updateBlock(LocalPos.pack(5,1,5), true, false, false, null); check(s.getNearCount()==0, "break near block -> removed");
		// invalidate -> updates ignored
		s.invalidate(); s.updateBlock(LocalPos.pack(3,0,3), false, true, false, null); check(s.getOk()==0 && s.getTotal()==0, "after invalidate updates are ignored");
		// timing: near check (125 lookups) on a 50k-position state
		ValidationState big = new ValidationState(); List<Long> bigExp = new ArrayList<>();
		for(int x=0;x<50;x++) for(int y=0;y<20;y++) for(int z=0;z<50;z++) bigExp.add(LocalPos.pack(x,y,z));
		big.beginScan(bigExp); big.endScan();
		int n = 200000; long t0 = System.nanoTime();
		for(int i=0;i<n;i++) big.updateBlock(LocalPos.pack(25, 21, 25 + (i & 7)), false, true, false, null); // outside, near check every time
		double nsNear = (System.nanoTime()-t0)/(double)n;
		t0 = System.nanoTime();
		for(int i=0;i<n;i++) big.updateBlock(LocalPos.pack(25, 5, 25), (i&1)==0, (i&1)==1, false, null); // expected position, transitions
		double nsExp = (System.nanoTime()-t0)/(double)n;
		t0 = System.nanoTime(); int hits=0;
		for(int i=0;i<n;i++) if(big.isInRange(100+(i&3), 0, 0)) hits++;
		double nsRange = (System.nanoTime()-t0)/(double)n;
		System.out.printf("50k-position state: near event %.2f us, expected-position event %.2f us, out-of-range pre-filter %.3f us (%d hits)%n", nsNear/1000, nsExp/1000, nsRange/1000, hits);
	}
}
