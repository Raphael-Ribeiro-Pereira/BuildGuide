import brentmaas.buildguide.common.screen.ShapeLayout;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.BaseScreen;
// GUI redesign E7: vertical bands of the Shape tab from the GUI height. At the minimum height they
// must be exactly the E6 constants (the proof that 480 x 270 looks the same); above it the bottom row
// follows the bottom edge and the extra height goes 60% to the preview, 40% to the list.
public class ShapeLayoutTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static String s(ShapeLayout l){ return "preview ..."+l.previewBottom+", progress "+l.progressTop+", tabs "+l.tabsTop+".."+l.tabsBottom+", list .."+l.listBottom+", bottom row "+l.bottomRowY+", legend "+l.legendY+", accordion .."+l.accordionBottom; }

	public static void main(String[] a){
		System.out.println("-- 270: the E6 layout");
		ShapeLayout m=new ShapeLayout(270);
		System.out.println("   "+s(m));
		check(m.previewBottom==175 && m.progressTop==175, "preview 40..175, progress line from 175 (E6)");
		check(m.tabsTop==177 && m.tabsBottom==193, "tabs 177..193 (E6)");
		check(m.listBottom==250, "list ends at 250 (E6)");
		check(m.bottomRowY==250, "Validate / Reset at y 250 (E6)");
		check(m.legendY==256, "legend at y 256 (E6)");
		check(m.accordionBottom==248 && m.accordionHeight()==206 && ShapeScreen.accordionTop==42, "accordion 42..248 = 206 px (E6)");

		System.out.println("-- 360: GUI scale 3 on 1080p");
		ShapeLayout t=new ShapeLayout(360);
		System.out.println("   "+s(t));
		check(t.bottomRowY==340 && t.legendY==346 && t.listBottom==340, "bottom row, legend and list end follow the bottom edge (h - 20)");
		check(t.previewBottom==175+54, "preview gets 60% of the 90 extra px (54)");
		check(t.listBottom-t.tabsBottom==(250-193)+36, "list gets the other 40% (36)");
		check(t.tabsTop==t.previewBottom+2 && t.tabsBottom==t.tabsTop+16, "progress 2 px and tabs 16 px under the preview, as in E6");
		check(t.accordionHeight()==296, "accordion 42..338 = 296 px");

		System.out.println("-- 540: GUI scale 2 on 1080p");
		ShapeLayout u=new ShapeLayout(540);
		System.out.println("   "+s(u));
		check(u.bottomRowY==520 && u.previewBottom==175+162 && u.listBottom-u.tabsBottom>=57, "bands keep their order and grow");

		System.out.println("-- below the minimum (D1 shows a message instead; bands never shrink under E6)");
		ShapeLayout b=new ShapeLayout(240);
		check(b.previewBottom==175 && b.tabsBottom==193, "no negative extra: the preview keeps its E6 size");
		System.out.println("-- D1: minimum check (GUI pixels = window / scale, rounded up)");
		check(BaseScreen.fitsMinimum(480,270), "1920x1080 at scale 4 (Auto): 480x270 fits, exactly the minimum");
		check(BaseScreen.fitsMinimum(640,360) && BaseScreen.fitsMinimum(960,540), "scale 3 and 2 on 1080p fit");
		check(!BaseScreen.fitsMinimum(427,240), "1280x720 at scale 3 (Auto): 427x240 -> message");
		check(!BaseScreen.fitsMinimum(479,270) && !BaseScreen.fitsMinimum(480,269), "one pixel short in either direction -> message");
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
	}
}
