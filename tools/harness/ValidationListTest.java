import java.util.*;
import brentmaas.buildguide.common.screen.*;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.ValidationListComponent.Category;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.*;
// E2: the error list extracted from ValidationScreen; E6: one tab (category) at a time, the combined
// list and the Validation tab are gone. Pure rows, counts, rebuild debounce, tab switch, click/highlight.
// Titles are compared by key and values (toString needs the loader's screen handler)
public class ValidationListTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static long now=1000;
	static String k(Translatable t){ return t.getTranslationKey(); }
	static class FakeList implements ISelectorList { int l,r,t,b,h; List<Translatable> titles; ISelectorListCallback cb; int sets=0;
		public void setEntries(List<Translatable> ts){ titles=ts; sets++; } public void setVisibility(boolean v){} public void setYPosition(int y){} }
	static List<String> keys(ValidationListComponent.Entries e){ List<String> l=new ArrayList<>(); for(Translatable t: e.titles) l.add(k(t)); return l; }

	public static void main(String[] a){
		System.out.println("-- messages");
		for(Category c: Category.values()){
			ValidationListComponent.Entries none=ValidationListComponent.buildEntries(null,c);
			check(none.titles.size()==1 && k(none.titles.get(0)).equals("screen.buildguide.novalidation") && none.positions.get(0)==-1 && none.version==-1, c+": no shape -> only 'No shape'");
		}
		ShapeBridge s=new ShapeBridge(); ValidationState st=s.getValidationState();
		check(keys(ValidationListComponent.buildEntries(s,Category.MISSING)).equals(Arrays.asList("screen.buildguide.notvalidated")), "not validated -> only 'Not validated yet'");
		check(ValidationListComponent.counts(null)==null && ValidationListComponent.counts(s)==null, "no counts without a shape or before validation");

		System.out.println("-- validated, clean");
		List<Long> exp=new ArrayList<>(); for(int x=0;x<6;x++) exp.add(LocalPos.pack(x,0,0));
		st.beginScan(exp,100,64,-200); for(long p: exp) st.setStatus(p,ValidationState.OK,null); st.endScan();
		int[] n0=ValidationListComponent.counts(s);
		check(n0[0]==0 && n0[1]==0 && n0[2]==0, "counts {0, 0, 0}");
		for(Category c: Category.values()){
			ValidationListComponent.Entries e=ValidationListComponent.buildEntries(s,c);
			check(keys(e).equals(Arrays.asList("screen.buildguide.errors.none")) && e.positions.get(0)==-1, c+": one 'None' row, no position");
		}

		System.out.println("-- errors, missing, ignored");
		st.setStatus(LocalPos.pack(1,0,0),ValidationState.MISSING,null);
		st.setStatus(LocalPos.pack(2,0,0),ValidationState.MISSING,null);
		st.setStatus(LocalPos.pack(4,0,0),ValidationState.IGNORED,"Scaffolding");
		st.updateBlock(LocalPos.pack(5,1,0),false,true,false,"Stone");
		st.updateBlock(LocalPos.pack(0,1,0),false,true,false,null);
		int[] n=ValidationListComponent.counts(s);
		check(n[0]==2 && n[1]==2 && n[2]==1, "counts {errors 2, missing 2, ignored 1}: missing without the ignored one");
		ValidationListComponent.Entries te=ValidationListComponent.buildEntries(s,Category.ERRORS);
		check(keys(te).equals(Arrays.asList("[100, 65, -200] ? (d=1.0)","[105, 65, -200] Stone (d=1.0)")), "Errors: world coords (local + origin), sorted by x, '?' without a name, distance; no header "+keys(te));
		check(te.positions.equals(Arrays.asList(LocalPos.pack(0,1,0),LocalPos.pack(5,1,0))), "Errors: positions parallel to the rows");
		ValidationListComponent.Entries tm=ValidationListComponent.buildEntries(s,Category.MISSING);
		check(keys(tm).equals(Arrays.asList("[101, 64, -200]","[102, 64, -200]")) && tm.positions.get(1)==LocalPos.pack(2,0,0), "Missing: one row per missing position, sorted, no name "+keys(tm));
		ValidationListComponent.Entries ti=ValidationListComponent.buildEntries(s,Category.IGNORED);
		check(keys(ti).equals(Arrays.asList("[104, 64, -200] Scaffolding")), "Ignored: world coords and block name, no distance");

		System.out.println("-- glue");
		FakeList fl=new FakeList();
		ValidationListComponent c=new ValidationListComponent(()->now,(l,r,t,b,h,ts,cur,cb)->{ fl.l=l; fl.r=r; fl.t=t; fl.b=b; fl.h=h; fl.titles=ts; fl.cb=cb; return fl; });
		check(c.getCategory()==Category.ERRORS, "default tab: Errors");
		c.init(192,193,480,250,s);
		check(fl.l==192 && fl.r==480 && fl.t==193 && fl.b==250 && fl.h==12 && fl.titles.size()==2, "init: rectangle (192,193)-(480,250) passed as left, right, top, bottom; rows 12 px; Errors rows");
		c.update(s); check(fl.sets==1, "first update after init rebuilds right away");
		c.update(s); check(fl.sets==1, "nothing changed: no rebuild");
		now+=50; st.updateBlock(LocalPos.pack(5,1,0),true,false,false,null);
		c.update(s); check(fl.sets==1, "state changed 50 ms after the last rebuild: waits");
		now+=60; c.update(s); check(fl.sets==2 && fl.titles.size()==1, "after 100 ms: rebuilt, 1 error left");
		ShapeBridge s2=new ShapeBridge(); now+=200; c.update(s2);
		check(fl.sets==3 && k(fl.titles.get(0)).equals("screen.buildguide.notvalidated"), "shape switch: rebuilt for the other shape");
		c.update(s); now+=200; c.update(s);
		fl.cb.run(0); check(st.getHighlightedPos()==LocalPos.pack(0,1,0), "click on an error row highlights its position");
		fl.cb.run(0); check(st.getHighlightedPos()==-1, "click on the highlighted row again clears it");
		int before=fl.sets; c.setCategory(Category.MISSING);
		check(fl.sets==before+1 && fl.titles.size()==2 && c.getCategory()==Category.MISSING, "setCategory: rebuilt at once with the tab's rows");
		fl.cb.run(0); check(st.getHighlightedPos()==LocalPos.pack(1,0,0), "click on a missing row highlights where the block goes");
		c.setCategory(Category.IGNORED); fl.cb.run(0); check(st.getHighlightedPos()==LocalPos.pack(4,0,0), "click on an ignored row highlights it");
		st.updateBlock(LocalPos.pack(0,1,0),true,false,false,null); c.setCategory(Category.ERRORS);
		fl.cb.run(0); check(st.getHighlightedPos()==-1, "click on 'None' (no position) clears the highlight, like a header did");
		c.setCategory(Category.IGNORED); fl.cb.run(0); fl.cb.run(99); fl.cb.run(-1); check(st.getHighlightedPos()==LocalPos.pack(4,0,0), "out-of-range index: ignored (the highlight stays)");
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}
}
