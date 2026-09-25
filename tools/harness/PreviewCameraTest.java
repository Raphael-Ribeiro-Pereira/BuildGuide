import brentmaas.buildguide.common.shape.*;
// Step 0 phase 5: orbit camera rules (drag, clamp, zoom, reset). The mesh never depends on it:
// PreviewRenderer rebuilds only when the PreviewModel instance changes, and the camera is not part of it
public class PreviewCameraTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static boolean near(double a,double b){ return Math.abs(a-b)<1e-4; }
	public static void main(String[] a){
		PreviewCamera c=new PreviewCamera();
		c.rotate(20,0); check(near(c.yaw,55) && near(c.pitch,30), "drag 20 px right: yaw +10 (0.5 deg/px), pitch unchanged");
		c.rotate(0,10); check(near(c.pitch,35), "drag 10 px down: pitch +5 (more of the top)");
		c.rotate(0,1000); check(near(c.pitch,89), "pitch clamped at +89 ("+c.pitch+")");
		c.rotate(0,-5000); check(near(c.pitch,-89), "pitch clamped at -89 ("+c.pitch+")");
		c.reset(); c.rotate(-120,0); check(near(c.yaw,345), "yaw wraps below 0 into [0,360) ("+c.yaw+")");
		c.rotate(60,0); check(near(c.yaw,15), "yaw wraps above 360 ("+c.yaw+")");
		c.reset(); c.zoomBy(1); check(near(c.zoom,1.1), "one notch: zoom x1.1");
		c.zoomBy(-1); check(near(c.zoom,1.0), "back one notch: zoom 1 (multiplicative, symmetric)");
		c.zoomBy(100); check(near(c.zoom,8), "zoom max 8 ("+c.zoom+")");
		c.zoomBy(-500); check(near(c.zoom,0.25), "zoom min 0.25 ("+c.zoom+")");
		c.rotate(33,-17); c.reset(); check(c.yaw==45 && c.pitch==30 && c.zoom==1, "reset: yaw 45, pitch 30, zoom 1");
		// Zoom only scales the fit, never the geometry
		PreviewModel m=PreviewModel.of(java.util.Arrays.asList(LocalPos.pack(0,0,0),LocalPos.pack(9,0,0)));
		check(near(PreviewCamera.fitScale(m,300,300,8)/PreviewCamera.fitScale(m,300,300,1),8), "zoom 8 = 8x the fit scale");
	}
}
