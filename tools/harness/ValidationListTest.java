import java.util.*;
import brentmaas.buildguide.common.screen.*;
import brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable;
import brentmaas.buildguide.common.screen.widget.ISelectorList;
import brentmaas.buildguide.common.shape.*;
// E2: the error list extracted from ValidationScreen. Pure rows, rebuild debounce, click/highlight.
// Titles are compared by key and values (toString needs the loader's screen handler)
public class ValidationListTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static long now=1000;
	static String k(Translatable t){ return t.getTranslationKey(); }
	static String v(Translatable t){ return t.getValues().length>0 ? String.valueOf(t.getValues()[0]) : ""; }
	static class FakeList implements ISelectorList { int l,r,t,b,h; List<Translatable> titles; ISelectorListCallback cb; int sets=0;
		public void setEntries(List<Translatable> ts){ titles=ts; sets++; } public void setVisibility(boolean v){} public void setYPosition(int y){} }
	public static void main(String[] a){
		// No shape / not validated: the two messages that exist today, no headers
		ValidationListComponent.Entries none=ValidationListComponent.buildEntries(null,0,0,0);
		check(none.titles.size()==1 && k(none.titles.get(0)).equals("screen.buildguide.novalidation") && none.positions.get(0)==-1 && none.version==-1, "no shape: only 'No shape'");
		ShapeBridge s=new ShapeBridge(); ValidationState st=s.getValidationState();
		ValidationListComponent.Entries nv=ValidationListComponent.buildEntries(s,0,0,0);
		check(nv.titles.size()==1 && k(nv.titles.get(0)).equals("screen.buildguide.notvalidated"), "not validated: only 'Not validated yet', no error headers");
		// Validated, no errors: the three headers with their counts (no 'No errors' line: that is E6)
		List<Long> exp=new ArrayList<>(); for(int x=0;x<6;x++) exp.add(LocalPos.pack(x,0,0));
		st.beginScan(exp); for(long p: exp) st.setStatus(p,ValidationState.OK,null); st.endScan();
		ValidationListComponent.Entries clean=ValidationListComponent.buildEntries(s,100,64,-200);
		check(clean.titles.size()==3 && k(clean.titles.get(0)).equals("screen.buildguide.errors.structure") && v(clean.titles.get(0)).equals("0") && k(clean.titles.get(1)).equals("screen.buildguide.errors.ignored") && v(clean.titles.get(1)).equals("0") && k(clean.titles.get(2)).equals("screen.buildguide.errors.missing") && v(clean.titles.get(2)).equals("0"), "validated, no errors: Structure errors (0), Ignored (0), Missing (0)");
		// Errors, ignored, missing
		st.setStatus(LocalPos.pack(1,0,0),ValidationState.MISSING,null);
		st.setStatus(LocalPos.pack(2,0,0),ValidationState.MISSING,null);
		st.setStatus(LocalPos.pack(4,0,0),ValidationState.IGNORED,"Scaffolding");
		st.updateBlock(LocalPos.pack(5,1,0),false,true,false,"Stone");
		st.updateBlock(LocalPos.pack(0,1,0),false,true,false,null);
		ValidationListComponent.Entries e=ValidationListComponent.buildEntries(s,100,64,-200);
		List<String> keys=new ArrayList<>(); for(Translatable t: e.titles) keys.add(k(t));
		check(e.titles.size()==6, "2 error rows + 1 ignored row + 3 headers ("+e.titles.size()+")");
		check(keys.get(0).equals("screen.buildguide.errors.structure") && v(e.titles.get(0)).equals("2") && keys.get(3).equals("screen.buildguide.errors.ignored") && v(e.titles.get(3)).equals("1") && keys.get(5).equals("screen.buildguide.errors.missing"), "order: Structure errors (2), then Ignored (1), Missing last");
		check(v(e.titles.get(5)).equals("2"), "Missing counts only real missing (2), not the ignored one ("+v(e.titles.get(5))+")");
		check(keys.get(1).equals("  [100, 65, -200] ? (d=1.0)") && keys.get(2).equals("  [105, 65, -200] Stone (d=1.0)"), "error rows: world coords (local + origin), sorted by x, '?' without a name, distance ("+keys.get(1)+" | "+keys.get(2)+")");
		check(keys.get(4).equals("  [104, 64, -200] Scaffolding"), "ignored row: world coords and block name, no distance");
		check(e.positions.equals(Arrays.asList(-1L,LocalPos.pack(0,1,0),LocalPos.pack(5,1,0),-1L,LocalPos.pack(4,0,0),-1L)), "positions parallel to the rows, -1 for headers");
		// Glue: factory gets left, right, top, bottom; row height 12
		FakeList fl=new FakeList();
		ValidationListComponent c=new ValidationListComponent(()->now,(l,r,t,b,h,ts,cur,cb)->{ fl.l=l; fl.r=r; fl.t=t; fl.b=b; fl.h=h; fl.titles=ts; fl.cb=cb; return fl; });
		c.init(5,50,475,265,s,100,64,-200);
		check(fl.l==5 && fl.r==475 && fl.t==50 && fl.b==265 && fl.h==12 && fl.titles.size()==6, "init: rectangle (5,50)-(475,265) passed as left 5, right 475, top 50, bottom 265, rows 12 px");
		// Debounce: the first update rebuilds at once (like the screen did), then at most every 100 ms
		c.update(s,100,64,-200); check(fl.sets==1, "first update after init rebuilds right away");
		c.update(s,100,64,-200); check(fl.sets==1, "nothing changed: no rebuild");
		now+=50; st.updateBlock(LocalPos.pack(5,1,0),true,false,false,null);
		c.update(s,100,64,-200); check(fl.sets==1, "state changed 50 ms after the last rebuild: waits");
		now+=60; c.update(s,100,64,-200); check(fl.sets==2 && v(fl.titles.get(0)).equals("1"), "after 100 ms: rebuilt, Structure errors (1)");
		ShapeBridge s2=new ShapeBridge(); now+=200; c.update(s2,0,0,0);
		check(fl.sets==3 && k(fl.titles.get(0)).equals("screen.buildguide.notvalidated"), "shape switch: rebuilt for the other shape");
		// Click / highlight
		c.update(s,100,64,-200); now+=200; c.update(s,100,64,-200);
		check(fl.titles.size()==5, "rows for the next checks: 1 error, 1 ignored, 3 headers");
		fl.cb.run(1); check(st.getHighlightedPos()==LocalPos.pack(0,1,0), "click on an error row highlights its position");
		fl.cb.run(1); check(st.getHighlightedPos()==-1, "click on the highlighted row again clears it");
		fl.cb.run(3); check(st.getHighlightedPos()==LocalPos.pack(4,0,0), "click on an ignored row highlights it");
		fl.cb.run(0); check(st.getHighlightedPos()==-1, "click on a header clears the highlight");
		fl.cb.run(99); fl.cb.run(-1); check(st.getHighlightedPos()==-1, "out-of-range index: ignored");
	}
}
