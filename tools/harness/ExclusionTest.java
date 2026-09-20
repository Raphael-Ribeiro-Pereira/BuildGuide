import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class ExclusionTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	public static void main(String[] a) {
		ValidationState s = new ValidationState();
		List<Long> exp = new ArrayList<>(); for(int x=0;x<10;x++) for(int z=0;z<10;z++) exp.add(LocalPos.pack(x,0,z)); // 100-block slab
		// exclusion box over x 0..4 set BEFORE scan
		s.setExclusionBoxes(Arrays.asList(new int[]{0,-5,0,4,5,9}));
		s.beginScan(exp); for(long p: exp) if(!s.isExcluded(LocalPos.unpackX(p),0,LocalPos.unpackZ(p))) s.setStatus(p, ValidationState.MISSING, null); s.endScan();
		check(s.getTotal()==50 && s.getMissing()==50, "box before scan: total 50 (excluded half is not expected)");
		check(s.getStatus(LocalPos.pack(2,0,2))==ValidationState.UNKNOWN, "excluded position untracked");
		s.updateBlock(LocalPos.pack(2,0,2), false, true, false, null); check(s.getOk()==0, "event in excluded expected position ignored");
		s.updateBlock(LocalPos.pack(2,1,2), false, true, false, null); check(s.getNearCount()==0, "solid block in excluded region never near");
		// ignored type on expected position -> IGNORED, counted as missing, listed
		s.updateBlock(LocalPos.pack(7,0,7), false, true, true, "Scaffolding");
		check(s.getIgnored()==1 && s.getMissing()==50 && s.getOk()==0, "ignored type on expected: ignored 1, still missing 50");
		check(s.getPositions(ValidationState.IGNORED).size()==1 && "Scaffolding".equals(s.getWrongBlockName(LocalPos.pack(7,0,7))), "IGNORED listed with block name");
		s.updateBlock(LocalPos.pack(7,0,7), false, true, false, null); check(s.getIgnored()==0 && s.getOk()==1 && s.getMissing()==49, "replace ignored with real block -> ok");
		// ignored type near the shape -> never near
		s.updateBlock(LocalPos.pack(7,1,7), false, true, true, "Scaffolding"); check(s.getNearCount()==0, "ignored type next to shape: not near");
		s.updateBlock(LocalPos.pack(7,1,7), false, true, false, "Stone"); check(s.getNearCount()==1, "real block next to shape: near");
		// box added AFTER scan: immediate exclude of tracked positions and near blocks
		s.setExclusionBoxes(Arrays.asList(new int[]{0,-5,0,4,5,9}, new int[]{5,-5,5,9,5,9}));
		check(s.getTotal()==25 && s.getMissing()<=25 && s.getNearCount()==0, "box added after scan: total 25, near inside box removed ("+s.getTotal()+", near "+s.getNearCount()+")");
		check(s.getOk()==0, "ok block at (7,0,7) fell inside the new box and left the counters");
		// timing: bridge over ground with and without a box covering the ground (scan hash work)
		Set<Long> bridge=new HashSet<>(); for(int x=0;x<160;x++) for(int z=-3;z<=3;z++) bridge.add(LocalPos.pack(x,0,z));
		for(int x=0;x<160;x+=12) for(int y=-1;y>=-6;y--) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) bridge.add(LocalPos.pack(x+dx,y,dz));
		Set<Long> ground=new HashSet<>(); for(int x=-2;x<162;x++) for(int z=-5;z<=5;z++) for(int y=-8;y<=-6;y++) ground.add(LocalPos.pack(x,y,z));
		for(int pass=0;pass<2;pass++){
			ValidationState vs = new ValidationState();
			if(pass==1) vs.setExclusionBoxes(Arrays.asList(new int[]{-5,-9,-6,165,-6,6})); // the ground slab (y -8..-6)
			long t0=System.nanoTime(); vs.beginScan(bridge); for(long p: bridge) if(vs.getStatus(p)!=ValidationState.UNKNOWN||!vs.isExcluded(LocalPos.unpackX(p),LocalPos.unpackY(p),LocalPos.unpackZ(p))) vs.setStatus(p, ground.contains(p)?ValidationState.OK:ValidationState.MISSING, null);
			int checked=0; for(int x=-2;x<=161;x++) for(int y=-10;y<=2;y++) for(int z=-5;z<=5;z++){ long p=LocalPos.pack(x,y,z); if(bridge.contains(p)) continue; if(vs.isExcluded(x,y,z)) continue; if(!ground.contains(p)) continue; checked++;
				for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++){int d2=dx*dx+dy*dy+dz*dz; if(d2==0||d2>4)continue; vs.getStatus(LocalPos.pack(x+dx,y+dy,z+dz));} }
			System.out.printf("bridge over ground, %s: total %d, near-pass %.1f ms (solid cells checked %d)%n", pass==0?"no box":"ground box", vs.getTotal(), (System.nanoTime()-t0)/1e6, checked);
		}
	}
}
