import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class OverlayTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static class ColourBuf implements IShapeBuffer { int r,g,b; Map<String,Integer> cubesByColour=new LinkedHashMap<>(); int verts=0; String last=null; List<String> order=new ArrayList<>();
		public void setColour(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;} public void pushVertex(double x,double y,double z){ if(verts%24==0){String k=r+","+g+","+b; cubesByColour.merge(k,1,Integer::sum); order.add(k);} verts++; } public void end(){} public void close(){} }
	static final String RED="255,60,60", YELLOW="255,220,40", ORANGE="255,140,30", WHITE="255,255,255";
	public static void main(String[] a){
		ValidationState s=new ValidationState(); List<Long> exp=new ArrayList<>(); for(int x=0;x<10;x++) exp.add(LocalPos.pack(x,0,0));
		long v0=s.getVersion(); s.beginScan(exp); check(s.getVersion()>v0, "beginScan bumps version");
		for(long p: exp) s.setStatus(p, ValidationState.MISSING, null); s.endScan(); long v1=s.getVersion();
		s.setStatus(LocalPos.pack(3,0,0), ValidationState.IGNORED, "Scaffolding"); check(s.getVersion()==v1+1, "status change bumps once");
		s.setStatus(LocalPos.pack(3,0,0), ValidationState.IGNORED, "Scaffolding"); check(s.getVersion()==v1+1, "same status again: no bump");
		s.setStatus(LocalPos.pack(5,0,0), ValidationState.IGNORED, "Scaffolding"); s.setStatus(LocalPos.pack(1,0,0), ValidationState.IGNORED, "Scaffolding");
		check(s.getPositions(ValidationState.IGNORED).equals(Arrays.asList(LocalPos.pack(3,0,0),LocalPos.pack(5,0,0),LocalPos.pack(1,0,0))), "IGNORED index in insertion order (stable)");
		s.setStatus(LocalPos.pack(5,0,0), ValidationState.OK, null); check(s.getPositions(ValidationState.IGNORED).size()==2 && s.getIgnored()==2, "fix removes from index and counter");
		s.updateBlock(LocalPos.pack(0,1,0), false, true, false, "Log"); check(s.getNearCount()==1, "structure error added");
		ColourBuf b=new ColourBuf(); ValidationOverlay.build(b, s, new ShapeSet.Origin(0,0,0));
		check(b.cubesByColour.getOrDefault(YELLOW,0)==2 && b.cubesByColour.getOrDefault(RED,0)==1 && !b.cubesByColour.containsKey(ORANGE) && b.cubesByColour.size()==2, "overlay: 2 yellow ignored, 1 red error, no orange ("+b.cubesByColour+")");
		s.setHighlightedPos(LocalPos.pack(3,0,0)); b=new ColourBuf(); ValidationOverlay.build(b, s, new ShapeSet.Origin(0,0,0));
		check(b.cubesByColour.getOrDefault(WHITE,0)==1 && b.cubesByColour.getOrDefault(YELLOW,0)==1 && b.order.get(b.order.size()-1).equals(WHITE), "highlighted row drawn white, last, replacing its own cube");
		// cap: 6000 ignored, player at far end -> only 4000 nearest
		ValidationState big=new ValidationState(); List<Long> e2=new ArrayList<>(); for(int x=0;x<6000;x++) e2.add(LocalPos.pack(x,0,0)); big.beginScan(e2); for(long p: e2) big.setStatus(p, ValidationState.IGNORED, "X"); big.endScan();
		ColourBuf b2=new ColourBuf(); long t0=System.nanoTime(); ValidationOverlay.build(b2, big, new ShapeSet.Origin(5999,0,0)); double ms=(System.nanoTime()-t0)/1e6;
		check(b2.verts/24==4000, "cap 4000 cubes ("+b2.verts/24+")");
		System.out.printf("overlay build with 6000 entries (sort by distance + 4000 cubes): %.2f ms; getPositions(IGNORED) on 6000: %.3f ms%n", ms, timeGet(big));
		long v=big.getVersion(); big.invalidate(); check(!ValidationOverlay.hasContent(big) && big.getVersion()>v, "invalidate: no content, version bumped");
	}
	static double timeGet(ValidationState s){ long t=System.nanoTime(); for(int i=0;i<10;i++) s.getPositions(ValidationState.IGNORED); return (System.nanoTime()-t)/1e6/10; }
}
