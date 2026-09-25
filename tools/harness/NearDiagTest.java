import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// Etapa 2.5 Part 0: are near blocks detected (scan and incremental) and drawn visibly?
// Wall x 0..9, y 0..4, z 0 (all built). Candidate solid blocks placed around it.
public class NearDiagTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static final String[] names = {"face d=1", "straight d=2", "diagonal d=1.41", "diagonal d=2.83", "straight d=3"};
	static final int[][] cand = {{5,2,1}, {5,2,2}, {10,2,1}, {11,2,2}, {5,2,3}};
	static final boolean[] expectNear = {true, true, true, false, false};
	static List<Long> wall(){ List<Long> e=new ArrayList<>(); for(int x=0;x<10;x++) for(int y=0;y<5;y++) e.add(LocalPos.pack(x,y,0)); return e; }

	// Copy of fabric RenderHandler.validateShape's near pass (world = set of solid positions)
	static List<NearBlock> scanNear(Set<Long> expected, Set<Long> solid){
		int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
		for(long p: expected){ int x=LocalPos.unpackX(p),y=LocalPos.unpackY(p),z=LocalPos.unpackZ(p); minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z); }
		List<NearBlock> near=new ArrayList<>();
		for(int x=minX-2;x<=maxX+2;++x) for(int y=minY-2;y<=maxY+2;++y) for(int z=minZ-2;z<=maxZ+2;++z){
			if(expected.contains(LocalPos.pack(x,y,z))) continue;
			if(!solid.contains(LocalPos.pack(x,y,z))) continue;
			double best=Double.MAX_VALUE;
			for(int dx=-2;dx<=2;++dx) for(int dy=-2;dy<=2;++dy) for(int dz=-2;dz<=2;++dz){ int d2=dx*dx+dy*dy+dz*dz; if(d2==0||d2>4) continue; if(expected.contains(LocalPos.pack(x+dx,y+dy,z+dz))){ double d=Math.sqrt(d2); if(d<best) best=d; } }
			if(best<=2.0) near.add(new NearBlock(LocalPos.pack(x,y,z),"Stone",(float)best));
		}
		return near;
	}

	// Records every cube's bounding box and colour
	static class BoxBuf implements IShapeBuffer { int r,g,b; List<double[]> boxes=new ArrayList<>(); List<String> colours=new ArrayList<>(); int v=0; double[] cur;
		public void setColour(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;}
		public void pushVertex(double x,double y,double z){ if(v%24==0){ cur=new double[]{x,y,z,x,y,z}; boxes.add(cur); colours.add(r+","+g+","+b);} cur[0]=Math.min(cur[0],x);cur[1]=Math.min(cur[1],y);cur[2]=Math.min(cur[2],z);cur[3]=Math.max(cur[3],x);cur[4]=Math.max(cur[4],y);cur[5]=Math.max(cur[5],z); v++; }
		public void end(){} public void close(){} }

	public static void main(String[] a){
		List<Long> exp=wall(); Set<Long> expSet=new HashSet<>(exp);
		System.out.println("-- scan path (mirror of RenderHandler near pass), one candidate at a time");
		for(int i=0;i<cand.length;i++){
			Set<Long> solid=new HashSet<>(exp); long p=LocalPos.pack(cand[i][0],cand[i][1],cand[i][2]); solid.add(p);
			List<NearBlock> n=scanNear(expSet, solid);
			boolean found=n.size()==1 && n.get(0).localPos==p;
			check(found==expectNear[i], "scan "+names[i]+": "+(found?"near d="+n.get(0).distance:"not near"));
		}
		System.out.println("-- incremental path (real ValidationState.updateBlock), placed after the scan");
		for(int i=0;i<cand.length;i++){
			ValidationState s=new ValidationState(); s.beginScan(exp); for(long e: exp) s.setStatus(e, ValidationState.OK, null); s.endScan();
			long p=LocalPos.pack(cand[i][0],cand[i][1],cand[i][2]);
			boolean inRange=s.isInRange(cand[i][0],cand[i][1],cand[i][2]);
			s.updateBlock(p,false,true,false,"Stone");
			boolean found=s.getNearCount()==1;
			check(found==expectNear[i], "incremental "+names[i]+": "+(found?"near d="+s.getNearBlocks().get(0).distance:"not near")+" (isInRange "+inRange+")");
		}
		System.out.println("-- overlay: state with every detectable candidate as near (via scan), built by the real ValidationOverlay");
		ValidationState s=new ValidationState(); s.beginScan(exp); for(long e: exp) s.setStatus(e, ValidationState.OK, null);
		Set<Long> solid=new HashSet<>(exp); for(int[] c: cand) solid.add(LocalPos.pack(c[0],c[1],c[2]));
		s.setNearBlocks(scanNear(expSet, solid)); s.endScan();
		s.setStatus(LocalPos.pack(2,2,0), ValidationState.IGNORED, "Scaffolding");
		check(ValidationOverlay.hasContent(s), "hasContent with errors + 1 ignored");
		BoxBuf b=new BoxBuf(); ValidationOverlay.build(b, s, new ShapeSet.Origin(0,0,0));
		int orange=0, orangeShell=0;
		for(int i=0;i<b.boxes.size();i++){
			double[] bx=b.boxes.get(i);
			System.out.printf("   cube %-12s [%.2f..%.2f, %.2f..%.2f, %.2f..%.2f] %s%n", b.colours.get(i), bx[0],bx[3],bx[1],bx[4],bx[2],bx[5], kind(bx));
			if(b.colours.get(i).equals("255,60,60")){ orange++; if(kind(bx).startsWith("shell")) orangeShell++; }
		}
		check(orange==3, "overlay emits a red cube per structure error ("+orange+")");
		check(orangeShell==3, "red error cubes are shells around their solid block, visible under depth test ("+orangeShell+"/"+orange+")");
		check(kind(b.boxes.get(0)).startsWith("inner"), "ignored (scaffolding, see-through) keeps the inner 0.7 cube");
		s.setHighlightedPos(LocalPos.pack(5,2,1)); b=new BoxBuf(); ValidationOverlay.build(b, s, new ShapeSet.Origin(0,0,0));
		int last=b.boxes.size()-1;
		check(b.colours.get(last).equals("255,255,255") && kind(b.boxes.get(last)).startsWith("shell"), "highlighted near block: white shell, drawn last");
		s.setHighlightedPos(LocalPos.pack(2,2,0)); b=new BoxBuf(); ValidationOverlay.build(b, s, new ShapeSet.Origin(0,0,0));
		last=b.boxes.size()-1;
		check(b.colours.get(last).equals("255,255,255") && kind(b.boxes.get(last)).startsWith("inner"), "highlighted shape position: white inner cube");
	}
	// inner = strictly inside its block cell (hidden if the block is opaque); shell = encloses the whole cell
	static String kind(double[] bx){
		int cx=(int)Math.floor(bx[0]+0.5), cy=(int)Math.floor(bx[1]+0.5), cz=(int)Math.floor(bx[2]+0.5);
		if(bx[0]<cx && bx[1]<cy && bx[2]<cz && bx[3]>cx+1 && bx[4]>cy+1 && bx[5]>cz+1) return "shell (encloses the block)";
		cx=(int)Math.floor(bx[0]); cy=(int)Math.floor(bx[1]); cz=(int)Math.floor(bx[2]);
		if(bx[0]>cx && bx[1]>cy && bx[2]>cz && bx[3]<cx+1 && bx[4]<cy+1 && bx[5]<cz+1) return "inner (strictly inside the cell)";
		return "other";
	}
}
