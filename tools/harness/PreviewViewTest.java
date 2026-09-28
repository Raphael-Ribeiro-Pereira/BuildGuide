import java.util.*;
import brentmaas.buildguide.common.screen.PreviewController;
import brentmaas.buildguide.common.shape.*;
// GUI redesign E6: preview filter and slice, through the real PreviewMesh. Cubes are counted by
// colour on a model with one position of every status plus one structure error; the slice runs on
// a solid sphere. Also: the controller keeps the viewed model as one instance until something changes.
public class PreviewViewTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static class RecBuf implements IShapeBuffer { int r,g,b; List<Integer> c=new ArrayList<>();
		public void setColour(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;} public void pushVertex(double x,double y,double z){ c.add((r<<16)|(g<<8)|b); } public void end(){} public void close(){} }
	// Cubes drawn, and their base colours (top face, factor 1.0)
	static List<Integer> cubes(PreviewModel m){ RecBuf b=new RecBuf(); PreviewMesh.fill(b,m); List<Integer> out=new ArrayList<>(); for(int n=0;n<b.c.size()/24;n++) out.add(b.c.get(n*24+16)); return out; }
	static int count(List<Integer> l,int colour){ int n=0; for(int c: l) if(c==colour) ++n; return n; }

	public static void main(String[] a) throws Exception {
		long pOk=LocalPos.pack(0,0,0), pOk2=LocalPos.pack(0,1,0), pMissing=LocalPos.pack(1,0,0), pIgnored=LocalPos.pack(3,0,0), pExcluded=LocalPos.pack(4,0,0);
		List<Long> exp=Arrays.asList(pOk,pOk2,pMissing,pIgnored,pExcluded);
		ValidationState s=new ValidationState();
		s.setExclusionBoxes(Collections.singletonList(new int[]{4,0,0,4,0,0}));
		s.beginScan(exp);
		s.setStatus(pOk,ValidationState.OK,null); s.setStatus(pOk2,ValidationState.OK,null); s.setStatus(pMissing,ValidationState.MISSING,null); s.setStatus(pIgnored,ValidationState.IGNORED,"Scaffolding");
		s.endScan();
		s.updateBlock(LocalPos.pack(1,1,0),false,true,false,"Stone"); // one structure error
		PreviewModel m=PreviewModel.of(exp).withValidation(s);

		System.out.println("-- filters (2 built, 1 missing, 1 ignored, 1 unvalidated, 1 error)");
		List<Integer> all=cubes(m);
		check(all.size()==6, "ALL: 5 shape cubes + 1 error, as before E6 ("+all.size()+")");
		check(cubes(m.withView(PreviewFilter.ALL,PreviewModel.SLICE_OFF,0)).equals(all), "ALL through withView: same cubes, same colours");
		List<Integer> err=cubes(m.withView(PreviewFilter.ERRORS,PreviewModel.SLICE_OFF,0));
		check(err.size()==1 && err.get(0)==PreviewColours.ERROR, "ERRORS: only the red error cube");
		List<Integer> mis=cubes(m.withView(PreviewFilter.MISSING,PreviewModel.SLICE_OFF,0));
		check(mis.size()==1 && mis.get(0)==PreviewColours.MISSING, "MISSING: only blue-grey");
		List<Integer> built=cubes(m.withView(PreviewFilter.BUILT,PreviewModel.SLICE_OFF,0));
		check(built.size()==2 && count(built,PreviewColours.OK)==2, "BUILT: only the 2 green");
		List<Integer> unv=cubes(m.withView(PreviewFilter.UNVALIDATED,PreviewModel.SLICE_OFF,0));
		check(unv.size()==1 && unv.get(0)==PreviewColours.WHITE, "UNVALIDATED: only white (the excluded position)");
		List<Integer> fresh=cubes(PreviewModel.of(exp).withView(PreviewFilter.UNVALIDATED,PreviewModel.SLICE_OFF,0));
		check(fresh.size()==5, "UNVALIDATED on a never-validated shape: every position");
		PreviewModel v=m.withView(PreviewFilter.BUILT,PreviewModel.SLICE_OFF,0);
		check(v!=m && v.positions==m.positions && v.minX==m.minX && v.maxY==m.maxY && v.radius()==m.radius(), "withView: new instance, shared positions, same framing");
		check(m.withValidation(s).withView(PreviewFilter.MISSING,1,0).withValidation(s).filter==PreviewFilter.MISSING, "withValidation keeps the view");

		System.out.println("-- slice on a solid sphere r=5");
		Set<Long> sphere=new LinkedHashSet<>(); int r=5; int equator=0;
		for(int x=-r;x<=r;x++) for(int y=-r;y<=r;y++) for(int z=-r;z<=r;z++) if(x*x+y*y+z*z<=r*r){ sphere.add(LocalPos.pack(x,y,z)); if(y==0) ++equator; }
		PreviewModel sp=PreviewModel.of(sphere);
		check(cubes(sp.withView(PreviewFilter.ALL,1,0)).size()==equator, "slice Y=0: only the equator plane ("+equator+" of "+sphere.size()+")");
		check(cubes(sp.withView(PreviewFilter.ALL,0,5)).size()==1, "slice X=5: the single pole block");
		check(cubes(sp.withView(PreviewFilter.ALL,2,9)).size()==0, "slice outside the bounds: nothing");
		check(sp.min(1)==-5 && sp.max(1)==5, "bounds per axis for the slider range");
		check(cubes(m.withView(PreviewFilter.ALL,1,1)).size()==2, "slice applies to errors too: y=1 holds 1 shape cube + 1 error");

		System.out.println("-- empty view (e6-fix2: the + crash)");
		ValidationState clean=new ValidationState(); clean.beginScan(exp); for(long p: exp) clean.setStatus(p,ValidationState.OK,null); clean.endScan();
		PreviewModel noErr=PreviewModel.of(exp).withValidation(clean).withView(PreviewFilter.ERRORS,PreviewModel.SLICE_OFF,0);
		check(noErr.errors.length==0 && cubes(noErr).size()==0, "ERRORS on a shape without errors: zero cubes pushed (the null MeshData)");
		check(!noErr.isEmpty() && noErr.isViewEmpty(), "that view: model not empty, view empty -> message, no mesh");
		check(sp.withView(PreviewFilter.ALL,2,9).isViewEmpty(), "slice on an empty layer: view empty");
		check(!m.isViewEmpty() && !m.withView(PreviewFilter.ERRORS,PreviewModel.SLICE_OFF,0).isViewEmpty() && !sp.withView(PreviewFilter.ALL,1,0).isViewEmpty(), "views with cubes (all, the one error, a full layer): not empty");
		check(PreviewModel.of(exp).withView(PreviewFilter.MISSING,PreviewModel.SLICE_OFF,0).isViewEmpty(),"MISSING on a never-validated shape: view empty (nothing is missing yet)");
		check(noErr.withView(PreviewFilter.ALL,PreviewModel.SLICE_OFF,0).isViewEmpty()==false && noErr.isViewEmpty(), "the flag belongs to each view instance, not the shared data");

		System.out.println("-- controller view cache");
		long[] now={0}; PreviewController c=new PreviewController(()->now[0]);
		ShapeBridge shape=new ShapeBridge();
		generate(shape,LocalPos.pack(0,0,0),LocalPos.pack(0,1,0));
		PreviewModel base=c.update(shape);
		check(base!=null && c.update(shape)==base && base.filter==PreviewFilter.ALL, "default view: the base model itself, same instance every frame");
		c.setFilter(PreviewFilter.MISSING);
		PreviewModel v1=c.update(shape);
		check(v1!=base && v1.filter==PreviewFilter.MISSING && c.update(shape)==v1, "filter set: a new instance once, then the same one every frame (no mesh rebuild per frame)");
		c.setSlice(1,1);
		PreviewModel v2=c.update(shape);
		check(v2!=v1 && v2.sliceAxis==1 && v2.sliceValue==1 && c.update(shape)==v2, "slice set: new instance once, then stable");
		c.setFilter(PreviewFilter.ALL); c.setSlice(PreviewModel.SLICE_OFF,0);
		check(c.update(shape)==base, "back to All / Off: the base model again");
		check(c.getModel()==base, "getModel: the unfiltered model (slider range)");
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}

	static void generate(Shape s, long... blocks) throws Exception {
		java.lang.reflect.Method finish=Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		java.lang.reflect.Field fe=Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true);
		s.lock.lock(); try { @SuppressWarnings("unchecked") Set<Long> e=(Set<Long>)fe.get(s); e.clear(); for(long b: blocks) e.add(b); s.ready=false; s.error=false; finish.invoke(s); } finally { s.lock.unlock(); }
	}
}
