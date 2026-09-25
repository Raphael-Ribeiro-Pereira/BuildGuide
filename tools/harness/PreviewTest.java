import java.util.*;
import brentmaas.buildguide.common.shape.*;
// Step 0 preview, the parts that live in common: face shading order, model snapshot bounds, camera fit
public class PreviewTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static class RecBuf implements IShapeBuffer { int r,g,b,a; List<double[]> v=new ArrayList<>(); List<int[]> c=new ArrayList<>(); boolean ended, closed;
		public void setColour(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;}
		public void pushVertex(double x,double y,double z){ v.add(new double[]{x,y,z}); c.add(new int[]{r,g,b,a}); }
		public void end(){ended=true;} public void close(){closed=true;} }
	public static void main(String[] args) throws Exception {
		// CubeMesh face order: FaceShadedBuffer assumes -X,-Y,-Z,+X,+Y,+Z, 4 vertices each
		RecBuf raw=new RecBuf(); CubeMesh.push(raw, 10, 20, 30, 1);
		String[] expect={"-X","-Y","-Z","+X","+Y","+Z"}; boolean orderOk=raw.v.size()==24;
		for(int f=0;f<6 && orderOk;f++){ String got=face(raw.v.subList(f*4,f*4+4), 10,20,30,1); if(!got.equals(expect[f])){ orderOk=false; System.out.println("   face "+f+" is "+got+", expected "+expect[f]); } }
		check(orderOk, "CubeMesh emits faces -X,-Y,-Z,+X,+Y,+Z (the order FaceShadedBuffer relies on)");
		// Shading per face, alpha untouched, two cubes in a row (counter wraps every 24 vertices)
		RecBuf out=new RecBuf(); FaceShadedBuffer fs=new FaceShadedBuffer(out); fs.setColour(200,100,50,255);
		CubeMesh.push(fs,0,0,0,1); CubeMesh.push(fs,1,0,0,1);
		float[] k={0.6f,0.5f,0.8f,0.6f,1.0f,0.8f}; boolean shadeOk=out.v.size()==48;
		for(int i=0;i<48 && shadeOk;i++){ float f=k[(i/4)%6]; int[] c=out.c.get(i); if(c[0]!=Math.round(200*f)||c[1]!=Math.round(100*f)||c[2]!=Math.round(50*f)||c[3]!=255) shadeOk=false; }
		check(shadeOk, "FaceShadedBuffer: top 1.0, N/S 0.8, E/W 0.6, bottom 0.5, alpha kept, across cubes");
		check(out.c.get(16)[0]==200 && out.c.get(4)[0]==100, "top face keeps the colour, bottom is half");
		fs.end(); fs.close(); check(out.ended && out.closed, "end/close delegate");
		// Model: copy, bounds, centre, radius
		Set<Long> live=new LinkedHashSet<>(); for(int x=-2;x<=3;x++) for(int y=0;y<4;y++) live.add(LocalPos.pack(x,y,-5));
		PreviewModel m=PreviewModel.of(live); live.clear();
		check(m.positions.length==24, "snapshot is a copy (clearing the live set does not touch it)");
		check(m.minX==-2&&m.maxX==3&&m.minY==0&&m.maxY==3&&m.minZ==-5&&m.maxZ==-5, "bounds");
		check(m.centreX()==1.0 && m.centreY()==2.0 && m.centreZ()==-4.5, "centre of the block box (blocks span x..x+1)");
		check(Math.abs(m.radius()-0.5*Math.sqrt(100+64+25))<1e-9, "framing radius = half the diagonal of the box grown by nearRadius (2) on every side");
		check(PreviewModel.of(new ArrayList<Long>()).isEmpty(), "empty model");
		// Camera fit: bounding sphere takes 90% of half the smaller side, zoom multiplies
		double s=PreviewCamera.fitScale(m, 400, 200, 1);
		check(Math.abs(s*m.radius()-90)<1e-9, "fit: radius x scale = 0.9 x 100 px ("+s*m.radius()+")");
		check(Math.abs(PreviewCamera.fitScale(m,400,200,2)-2*s)<1e-9, "zoom 2 doubles the scale");
		PreviewCamera cam=new PreviewCamera(); check(cam.yaw==45 && cam.pitch==30 && cam.zoom==1, "default camera yaw 45, pitch 30, zoom 1");
		// Sphere r=50 (hollow shell): snapshot size and cost
		List<Long> sphere=new ArrayList<>(); for(int x=-50;x<=50;x++) for(int y=-50;y<=50;y++) for(int z=-50;z<=50;z++){ double d=Math.sqrt(x*x+y*y+z*z); if(d<50&&d>=49) sphere.add(LocalPos.pack(x,y,z)); }
		long t0=System.nanoTime(); PreviewModel big=PreviewModel.of(sphere); double ms=(System.nanoTime()-t0)/1e6;
		RecBuf nb=new RecBuf(); FaceShadedBuffer fb=new FaceShadedBuffer(new IShapeBuffer(){ int n; public void setColour(int r,int g,int b,int a){} public void pushVertex(double x,double y,double z){n++;} public void end(){} public void close(){} });
		t0=System.nanoTime(); for(long p: big.positions) CubeMesh.push(fb, LocalPos.unpackX(p), LocalPos.unpackY(p), LocalPos.unpackZ(p), 0.92); double msMesh=(System.nanoTime()-t0)/1e6;
		System.out.printf("   sphere r=50 shell: %d blocks, snapshot %.1f ms, %d vertices, mesh walk (no-op buffer) %.1f ms%n", big.positions.length, ms, big.positions.length*24, msMesh);
	}
	static String face(List<double[]> q, double x, double y, double z, double s){
		for(int ax=0;ax<3;ax++){ double v0=q.get(0)[ax]; boolean same=true; for(double[] p: q) if(p[ax]!=v0) same=false; if(same){ double lo=ax==0?x:ax==1?y:z; return (v0==lo?"-":"+")+"XYZ".charAt(ax); } }
		return "?";
	}
}
