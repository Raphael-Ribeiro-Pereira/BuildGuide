import java.util.*;
import brentmaas.buildguide.common.shape.*;
// Step 0 phase 4: preview colours by status, from a real ValidationState, through the real PreviewMesh
public class PreviewColourTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static class RecBuf implements IShapeBuffer { int r,g,b; List<double[]> v=new ArrayList<>(); List<Integer> c=new ArrayList<>();
		public void setColour(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;} public void pushVertex(double x,double y,double z){ v.add(new double[]{x,y,z}); c.add((r<<16)|(g<<8)|b); } public void end(){} public void close(){} }
	static final float[] k={0.6f,0.5f,0.8f,0.6f,1.0f,0.8f};
	static int shade(int rgb,float f){ return (Math.round(((rgb>>16)&255)*f)<<16)|(Math.round(((rgb>>8)&255)*f)<<8)|Math.round((rgb&255)*f); }
	// Base colour of cube n = its top face (factor 1.0, vertices 16..19)
	static int top(RecBuf b,int n){ return b.c.get(n*24+16); }
	static String hex(int c){ return String.format("%06X",c); }
	// Cube n sits on block pos (min corner = pos + inset)
	static boolean at(RecBuf b,int n,long pos){ double[] p=b.v.get(n*24); double in=(1-PreviewMesh.cubeSize)/2; return Math.abs(p[0]-LocalPos.unpackX(pos)-in)<1e-9 && Math.abs(p[1]-LocalPos.unpackY(pos)-in)<1e-9 && Math.abs(p[2]-LocalPos.unpackZ(pos)-in)<1e-9; }

	public static void main(String[] a){
		long pOk=LocalPos.pack(0,0,0), pMissing=LocalPos.pack(1,0,0), pTorch=LocalPos.pack(2,0,0), pIgnored=LocalPos.pack(3,0,0), pExcluded=LocalPos.pack(4,0,0);
		List<Long> exp=Arrays.asList(pOk,pMissing,pTorch,pIgnored,pExcluded);
		// Not validated: every cube white, no errors
		PreviewModel geo=PreviewModel.of(exp);
		ValidationState s=new ValidationState();
		PreviewModel m0=geo.withValidation(s);
		RecBuf b0=new RecBuf(); PreviewMesh.fill(b0,m0);
		boolean allWhite=m0.status==null && m0.errors.length==0 && b0.v.size()==5*24; for(int n=0;n<5;n++) if(top(b0,n)!=PreviewColours.WHITE) allWhite=false;
		check(allWhite, "not validated: status null, 5 white cubes, no errors");
		// Validated like RenderHandler does it: exclusion boxes first, then the scan
		s.setExclusionBoxes(Collections.singletonList(new int[]{4,0,0,4,0,0}));
		s.beginScan(exp);
		s.setStatus(pOk,ValidationState.OK,null); s.setStatus(pMissing,ValidationState.MISSING,null); s.setStatus(pTorch,ValidationState.MISSING,null); s.setStatus(pIgnored,ValidationState.IGNORED,"Scaffolding");
		s.endScan();
		s.updateBlock(LocalPos.pack(1,1,0),false,true,false,"Stone"); // structure error on top of the missing one
		check(s.getStatus(pExcluded)==ValidationState.UNKNOWN && s.isValidated(), "excluded position reads UNKNOWN on a validated state");
		PreviewModel m=geo.withValidation(s);
		RecBuf b=new RecBuf(); PreviewMesh.fill(b,m);
		check(m.positions==geo.positions, "withValidation shares the positions array (geometry not copied)");
		check(b.v.size()==(5+1)*24, "5 shape cubes + 1 error cube ("+b.v.size()/24+")");
		check(top(b,0)==PreviewColours.OK, "OK -> green "+hex(top(b,0)));
		check(top(b,1)==PreviewColours.MISSING, "MISSING (air) -> blue-grey "+hex(top(b,1)));
		check(top(b,2)==PreviewColours.MISSING, "MISSING (torch on the guideline) -> blue-grey");
		check(top(b,3)==PreviewColours.IGNORED, "IGNORED -> yellow "+hex(top(b,3)));
		check(top(b,4)==PreviewColours.WHITE && top(b,4)!=PreviewColours.MISSING, "excluded (UNKNOWN, validated) -> white "+hex(top(b,4))+", not the MISSING blue-grey");
		check(top(b,5)==PreviewColours.ERROR && at(b,5,LocalPos.pack(1,1,0)), "structure error -> red cube at its own position, after the shape cubes");
		// Shading on top of every colour, face by face
		boolean shaded=true; for(int i=0;i<b.c.size();i++){ int n=i/24, base=top(b,n); if(b.c.get(i)!=shade(base,k[(i/4)%6])) shaded=false; }
		check(shaded, "face shading applied over every colour (6 cubes x 6 faces)");
		// The colour ambiguity the blue-grey avoids: a white N/S side must differ from the MISSING top
		check(shade(PreviewColours.WHITE,0.8f)!=PreviewColours.MISSING && shade(PreviewColours.WHITE,0.6f)!=PreviewColours.MISSING, "white side faces never equal the MISSING colour");
		// Refresh rule: version read at snapshot, a change makes a newer one, no change keeps it
		check(m.stateVersion==s.getVersion(), "snapshot carries the state version");
		s.updateBlock(pMissing,false,true,false,null);
		check(s.getVersion()!=m.stateVersion, "a block change bumps the version -> the screen will refresh");
		PreviewModel m2=geo.withValidation(s); RecBuf b2=new RecBuf(); PreviewMesh.fill(b2,m2);
		check(top(b2,1)==PreviewColours.OK && m2!=m, "refreshed snapshot: placed block now green, new instance (renderer rebuilds)");
		long v=s.getVersion(); s.updateBlock(pMissing,false,true,false,null);
		check(s.getVersion()==v, "same block again: no version bump -> no refresh");
		// Cost on a sphere r=50 shell, all validated
		List<Long> sphere=new ArrayList<>(); for(int x=-50;x<=50;x++) for(int y=-50;y<=50;y++) for(int z=-50;z<=50;z++){ double d=Math.sqrt(x*x+y*y+z*z); if(d<50&&d>=49) sphere.add(LocalPos.pack(x,y,z)); }
		ValidationState big=new ValidationState(); big.beginScan(sphere); int i=0; for(long p: sphere) big.setStatus(p,(i++%3==0)?ValidationState.OK:ValidationState.MISSING,null); big.endScan();
		PreviewModel bg=PreviewModel.of(sphere); bg.withValidation(big);
		long t0=System.nanoTime(); for(int r=0;r<10;r++) bg.withValidation(big); double msSnap=(System.nanoTime()-t0)/1e6/10;
		PreviewModel bm=bg.withValidation(big); IShapeBuffer noop=new IShapeBuffer(){ public void setColour(int r,int g,int b,int a){} public void pushVertex(double x,double y,double z){} public void end(){} public void close(){} };
		t0=System.nanoTime(); for(int r=0;r<10;r++) PreviewMesh.fill(noop,bm); double msFill=(System.nanoTime()-t0)/1e6/10;
		System.out.printf("   sphere r=50 (%d blocks): colour snapshot (getStatus x N, synchronized) %.2f ms, mesh fill (no-op buffer) %.2f ms%n", sphere.size(), msSnap, msFill);
	}
}
