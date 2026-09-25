import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class OverlayCost2 {
	static class NoopBuf implements IShapeBuffer { long n=0; public void setColour(int r,int g,int b,int a){} public void pushVertex(double x,double y,double z){n++;} public void end(){} public void close(){} }
	public static void main(String[] a){
		ValidationState big=new ValidationState(); List<Long> e2=new ArrayList<>(); for(int x=0;x<6000;x++) e2.add(LocalPos.pack(x,0,0)); big.beginScan(e2); for(long p: e2) big.setStatus(p, ValidationState.IGNORED, "X"); big.endScan();
		ValidationState small=new ValidationState(); List<Long> e3=new ArrayList<>(); for(int x=0;x<3000;x++) e3.add(LocalPos.pack(x,0,0)); small.beginScan(e3); for(long p: e3) small.setStatus(p, ValidationState.IGNORED, "X"); small.endScan();
		for(int rep=0;rep<6;rep++){ NoopBuf b=new NoopBuf(); long t0=System.nanoTime(); ValidationOverlay.build(b, big, new ShapeSet.Origin(5999,0,0)); long t1=System.nanoTime(); NoopBuf c=new NoopBuf(); ValidationOverlay.build(c, small, new ShapeSet.Origin(0,0,0)); long t2=System.nanoTime();
			if(rep>=3) System.out.printf("over cap (6000 wrong, sort + 4000 cubes): %.2f ms | under cap (3000 wrong, no sort): %.2f ms%n",(t1-t0)/1e6,(t2-t1)/1e6); }
	}
}
