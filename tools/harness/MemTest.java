import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class MemTest {
	static long used(){ System.gc(); System.gc(); try{Thread.sleep(50);}catch(Exception e){} return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(); }
	static void measure(String label, int n) {
		long before = used();
		Set<Long> set = new HashSet<Long>();
		int side = (int)Math.ceil(Math.cbrt(n)); int c=0;
		outer: for(int x=0;x<side;x++) for(int y=0;y<side;y++) for(int z=0;z<side;z++){ set.add(LocalPos.pack(x,y,z)); if(++c>=n) break outer; }
		long afterSet = used();
		ValidationState vs = new ValidationState(); vs.beginScan(set); for(long p: set) vs.setStatus(p, ValidationState.MISSING, null); vs.endScan();
		long afterState = used();
		System.out.printf("%-28s expectedBlocks set: %6.2f MB (%3d B/entry)   ValidationState: %6.2f MB   vertex buffer (672 B/block, already paid): %6.1f MB%n", label, (afterSet-before)/1048576.0, (afterSet-before)/n, (afterState-afterSet)/1048576.0, n*672/1048576.0);
		if(set.size()+vs.getTotal()<0) System.out.println();
	}
	public static void main(String[] a){ measure("sphere r=50 (~31k)", 31400); measure("large shape (100k)", 100000); measure("huge (300k)", 300000); }
}
