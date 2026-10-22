import brentmaas.buildguide.common.screen.ShapeLayout;
import brentmaas.buildguide.common.screen.ShapeScreen;
import brentmaas.buildguide.common.screen.BaseScreen;
// GUI redesign E7: vertical bands of the Shape tab from the GUI height. 2026-10-22: the list of positions moved
// to Visualisation ("Find blocks"), so the preview reaches down to one status line (2-px progress, then the
// missing count) right above the bottom row, and gets all the extra height. The bottom row, the legend and the
// accordion are the E6 ones at 270 and follow the bottom edge above it.
public class ShapeLayoutTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static String s(ShapeLayout l){ return "preview ..."+l.previewBottom+", progress "+l.progressTop+", status text "+l.statusTextY+", bottom row "+l.bottomRowY+", legend "+l.legendY+", accordion .."+l.accordionBottom; }

	public static void main(String[] a){
		System.out.println("-- 270: the minimum");
		ShapeLayout m=new ShapeLayout(270);
		System.out.println("   "+s(m));
		check(m.previewBottom==236 && m.progressTop==236, "preview 40..236 (it ended at 175 with the list under it), progress line from 236");
		check(m.statusTextY==241 && m.statusTextY+9<=m.bottomRowY, "status text at 241, inside its line above the bottom row");
		check(m.bottomRowY==250, "Validate / Reset at y 250 (E6)");
		check(m.legendY==256, "legend at y 256 (E6)");
		check(m.accordionBottom==248 && m.accordionHeight()==206 && ShapeScreen.accordionTop==42, "accordion 42..248 = 206 px (E6)");

		System.out.println("-- 360: GUI scale 3 on 1080p");
		ShapeLayout t=new ShapeLayout(360);
		System.out.println("   "+s(t));
		check(t.bottomRowY==340 && t.legendY==346, "bottom row and legend follow the bottom edge (h - 20)");
		check(t.previewBottom==236+90 && t.statusTextY==t.previewBottom+5, "the preview gets all 90 extra px, the status line stays right under it");
		check(t.accordionHeight()==296, "accordion 42..338 = 296 px");

		System.out.println("-- 540: GUI scale 2 on 1080p");
		ShapeLayout u=new ShapeLayout(540);
		System.out.println("   "+s(u));
		check(u.bottomRowY==520 && u.previewBottom==506 && u.previewBottom<u.statusTextY && u.statusTextY<u.bottomRowY, "bands keep their order and grow");

		System.out.println("-- below the minimum (D1 shows a message instead; the preview never shrinks under the minimum's)");
		ShapeLayout b=new ShapeLayout(240);
		check(b.previewBottom==236, "no negative extra: the preview keeps its size at 270");
		System.out.println("-- D1: minimum check (GUI pixels = window / scale, rounded up)");
		check(BaseScreen.fitsMinimum(480,270), "1920x1080 at scale 4 (Auto): 480x270 fits, exactly the minimum");
		check(BaseScreen.fitsMinimum(640,360) && BaseScreen.fitsMinimum(960,540), "scale 3 and 2 on 1080p fit");
		check(!BaseScreen.fitsMinimum(427,240), "1280x720 at scale 3 (Auto): 427x240 -> message");
		check(!BaseScreen.fitsMinimum(479,270) && !BaseScreen.fitsMinimum(480,269), "one pixel short in either direction -> message");
		System.out.println(fails==0 ? "ALL OK" : fails+" FAILED");
		System.exit(fails==0 ? 0 : 1);
	}
}
