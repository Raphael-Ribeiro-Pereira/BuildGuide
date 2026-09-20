import java.util.*;
import brentmaas.buildguide.common.shape.*;
/** Offline cost of the scan's hash work (world reads excluded): classify pass + near pass over the bounding box. */
public class ScanCostTest {
	static void measure(String label, Set<Long> expected, Set<Long> solidWorld) {
		int minX=1<<30,minY=1<<30,minZ=1<<30,maxX=-(1<<30),maxY=-(1<<30),maxZ=-(1<<30);
		for(long p: expected){int x=LocalPos.unpackX(p),y=LocalPos.unpackY(p),z=LocalPos.unpackZ(p);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
		long cells=(long)(maxX-minX+5)*(maxY-minY+5)*(maxZ-minZ+5);
		ValidationState s = new ValidationState();
		long t0=System.nanoTime();
		s.beginScan(expected);
		for(long p: expected) s.setStatus(p, solidWorld.contains(p)?ValidationState.OK:ValidationState.MISSING, null);
		long t1=System.nanoTime();
		int near=0, solidChecked=0;
		for(int x=minX-2;x<=maxX+2;x++) for(int y=minY-2;y<=maxY+2;y++) for(int z=minZ-2;z<=maxZ+2;z++){
			long p=LocalPos.pack(x,y,z); if(expected.contains(p)) continue; if(!solidWorld.contains(p)) continue; solidChecked++;
			double best=9; for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++){int d2=dx*dx+dy*dy+dz*dz; if(d2==0||d2>4)continue; if(expected.contains(LocalPos.pack(x+dx,y+dy,z+dz))){double d=Math.sqrt(d2); if(d<best)best=d;}}
			if(best<=2) near++;
		}
		long t2=System.nanoTime();
		System.out.printf("%-42s expected=%6d bbox=%8d cells  classify=%5.1f ms  near-pass=%6.1f ms (solid cells checked %d, near %d)%n", label, expected.size(), cells, (t1-t0)/1e6, (t2-t1)/1e6, solidChecked, near);
	}
	public static void main(String[] a) {
		// Cone r=20 h=40 hollow, in the air (nothing built): ~classify only
		Set<Long> cone=new HashSet<>(); for(int y=0;y<40;y++){double r=20.0*(1-y/40.0); for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){double d=Math.sqrt(x*x+z*z); if(Math.abs(d-r)<=0.5) cone.add(LocalPos.pack(x,y,z));}}
		measure("cone r20 h40, empty world", cone, new HashSet<>());
		// same cone, half built
		Set<Long> half=new HashSet<>(); int i=0; for(long p: cone) if((i++&1)==0) half.add(p);
		measure("cone r20 h40, half built", cone, half);
		// bridge 160 long x 7 wide over flat ground 6 below the deck (ground fills the bbox bottom)
		Set<Long> bridge=new HashSet<>(); for(int x=0;x<160;x++) for(int z=-3;z<=3;z++) bridge.add(LocalPos.pack(x,0,z));
		for(int x=0;x<160;x+=12) for(int y=-1;y>=-6;y--) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) bridge.add(LocalPos.pack(x+dx,y,z(dz)));
		Set<Long> ground=new HashSet<>(); for(int x=-2;x<162;x++) for(int z=-5;z<=5;z++) for(int y=-8;y<=-6;y++) ground.add(LocalPos.pack(x,y,z));
		measure("bridge 160x7 + pillars, ground in bbox", bridge, ground);
		// big solid cube 50x20x50 (50k) on ground
		Set<Long> cube=new HashSet<>(); for(int x=0;x<50;x++)for(int y=0;y<20;y++)for(int z=0;z<50;z++) cube.add(LocalPos.pack(x,y,z));
		Set<Long> g2=new HashSet<>(); for(int x=-2;x<52;x++)for(int z=-2;z<52;z++)for(int y=-2;y<0;y++) g2.add(LocalPos.pack(x,y,z));
		measure("solid 50x20x50 (50k) on ground", cube, g2);
	}
	static int z(int dz){ return dz; }
}
