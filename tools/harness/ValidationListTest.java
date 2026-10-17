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
	static List<String> keys(ValidationListComponent.Entries e){ return titleKeys(e.titles); }
	static List<String> titleKeys(List<Translatable> ts){ List<String> l=new ArrayList<>(); for(Translatable t: ts) l.add(k(t)); return l; }

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
		// Performance step 3: init's rows are current, so the first update does not build them again (opening the
		// screen built and filled them three times before)
		c.update(s); check(fl.sets==0, "first update after init: nothing changed since init, no rebuild");
		c.update(s); check(fl.sets==0, "nothing changed: no rebuild");
		now+=50; st.updateBlock(LocalPos.pack(5,1,0),true,false,false,null);
		c.update(s); check(fl.sets==0, "state changed 50 ms after init (the last build): waits");
		now+=60; c.update(s); check(fl.sets==1 && fl.titles.size()==1, "after 100 ms: rebuilt, 1 error left");
		ShapeBridge s2=new ShapeBridge(); now+=200; c.update(s2);
		check(fl.sets==2 && k(fl.titles.get(0)).equals("screen.buildguide.notvalidated"), "shape switch: rebuilt for the other shape");
		FakeList fl2=new FakeList(); ValidationListComponent c2=new ValidationListComponent(()->now,(l,r,t,b,h,ts,cur,cb)->{ fl2.titles=ts; fl2.cb=cb; return fl2; });
		c2.selectCategory(Category.MISSING);
		check(fl2.sets==0 && c2.getCategory()==Category.MISSING, "selectCategory: only the tab, nothing built (the screen sets it before init)");
		c2.init(192,193,480,250,s);
		check(fl2.sets==0 && keys(ValidationListComponent.buildEntries(s,Category.MISSING)).equals(titleKeys(fl2.titles)), "init builds the selected tab's rows once");
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

		System.out.println("-- at most maxRows rows (performance step 3)");
		ShapeBridge big=new ShapeBridge(); ValidationState bs=big.getValidationState(); List<Long> many=new ArrayList<>();
		for(int x=0;x<60;x++) for(int z=0;z<50;z++) many.add(LocalPos.pack(x,0,z));
		Collections.shuffle(many, new Random(5)); bs.beginScan(many,0,0,0); for(long p: many) bs.setStatus(p,ValidationState.MISSING,null);
		List<ValidationState.NearBlock> nb=new ArrayList<>(); for(int x=0;x<60;x++) for(int z=0;z<50;z++) nb.add(new ValidationState.NearBlock(LocalPos.pack(x,1,z),"Dirt",1.4142135f)); bs.setNearBlocks(nb); bs.endScan();
		ValidationListComponent.Entries bm=ValidationListComponent.buildEntries(big,Category.MISSING);
		check(bm.titles.size()==ValidationListComponent.maxRows+1 && ValidationListComponent.maxRows==1000, "3000 missing: 1000 rows and one more row ("+bm.titles.size()+")");
		check(k(bm.titles.get(0)).equals("[0, 0, 0]") && k(bm.titles.get(999)).equals("[19, 0, 49]") && bm.positions.get(999)==LocalPos.pack(19,0,49), "the first 1000 by position (x, then y, then z): "+k(bm.titles.get(999)));
		Translatable more=bm.titles.get(1000);
		check(k(more).equals("screen.buildguide.errors.more") && Arrays.equals(more.getValues(), new Object[]{"2000"}) && bm.positions.get(1000)==-1, "the last row: how many more (2000), no position (a click clears the highlight)");
		ValidationListComponent.Entries be=ValidationListComponent.buildEntries(big,Category.ERRORS);
		check(be.titles.size()==1001 && k(be.titles.get(0)).equals("[0, 1, 0] Dirt (d=1.4)") && k(be.titles.get(1000)).equals("screen.buildguide.errors.more"), "3000 structure errors: 1000 rows with the distance, then how many more: "+k(be.titles.get(0)));
		ValidationListComponent.Entries small=ValidationListComponent.buildEntries(s,Category.MISSING);
		check(!titleKeys(small.titles).contains("screen.buildguide.errors.more"), "under the limit: no extra row");
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}
}
