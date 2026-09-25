import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class OverlayCost {
	static class NoopBuf implements IShapeBuffer { long n=0; public void setColour(int r,int g,int b,int a){} public void pushVertex(double x,double y,double z){n++;} public void end(){} public void close(){} }
	static void cube(IShapeBuffer b,double x,double y,double z,double s){ // same 24 vertices as Shape.addCube
		b.pushVertex(x,y,z);b.pushVertex(x,y,z+s);b.pushVertex(x,y+s,z+s);b.pushVertex(x,y+s,z); b.pushVertex(x,y,z);b.pushVertex(x+s,y,z);b.pushVertex(x+s,y,z+s);b.pushVertex(x,y,z+s);
		b.pushVertex(x,y,z);b.pushVertex(x,y+s,z);b.pushVertex(x+s,y+s,z);b.pushVertex(x+s,y,z); b.pushVertex(x+s,y,z);b.pushVertex(x+s,y+s,z);b.pushVertex(x+s,y+s,z+s);b.pushVertex(x+s,y,z+s);
		b.pushVertex(x,y+s,z);b.pushVertex(x,y+s,z+s);b.pushVertex(x+s,y+s,z+s);b.pushVertex(x+s,y+s,z); b.pushVertex(x,y,z+s);b.pushVertex(x+s,y,z+s);b.pushVertex(x+s,y+s,z+s);b.pushVertex(x,y+s,z+s); }
	public static void main(String[] a){
		ValidationState s=new ValidationState(); List<Long> exp=new ArrayList<>(); for(int x=0;x<100;x++)for(int y=0;y<5;y++)for(int z=0;z<100;z++) exp.add(LocalPos.pack(x,y,z));
		s.beginScan(exp); int i=0; for(long p: exp){ s.setStatus(p, (i%10==0)?ValidationState.IGNORED:ValidationState.OK, "Stone"); i++; } s.endScan(); // 5000 ignored of 50k
		for(int rep=0;rep<3;rep++){ long t0=System.nanoTime(); List<Long> wrong=s.getPositions(ValidationState.IGNORED); long t1=System.nanoTime(); NoopBuf b=new NoopBuf(); for(long p: wrong) cube(b, LocalPos.unpackX(p)+0.2, LocalPos.unpackY(p)+0.2, LocalPos.unpackZ(p)+0.2, 0.6); long t2=System.nanoTime();
			System.out.printf("5000 ignored of 50k: getPositions %.2f ms (scans the whole map), cube pushes %.2f ms (%d vertices)%n",(t1-t0)/1e6,(t2-t1)/1e6,b.n); }
	}
}
