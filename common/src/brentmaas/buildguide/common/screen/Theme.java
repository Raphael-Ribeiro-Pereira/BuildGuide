package brentmaas.buildguide.common.screen;

// The look of the new interface (2026-10-27): flat widgets drawn by the mod instead of Minecraft's stone buttons,
// in the colours of the Oficina prototype. ARGB; the loaders' widgets and the screens read them from here, so
// 1.21.11 and 26.3 look the same
public class Theme {
	public static final int BACKGROUND = 0xF00F1114;
	public static final int PANEL = 0xF016191E;
	public static final int SURFACE = 0xFF1C2026;
	public static final int CONTROL = 0xFF23272E;
	public static final int CONTROL_HOVER = 0xFF2C323B;
	public static final int CONTROL_DISABLED = 0xFF181B20;
	public static final int SELECTED = 0xFF1F2A36;
	public static final int BORDER = 0xFF2C323B;
	public static final int BORDER_HOVER = 0xFF6F7784;
	public static final int FIELD = 0xFF0C0D10;
	public static final int ACCENT = 0xFF7CC4FF;
	public static final int ACCENT_DIM = 0xFF3B6FB0;
	public static final int OK = 0xFF5EC27A;
	public static final int TEXT = 0xFFE6E8EC;
	public static final int TEXT_SOFT = 0xFFC9CED6;
	public static final int TEXT_MUTED = 0xFF9AA1AD;
	public static final int TEXT_FAINT = 0xFF6F7784;
	public static final int SELECTED_HOVER = 0xFF2A3442;
	public static final int ACCENT_HOVER = 0xFFB3DCFF;
	public static final int ON_ACCENT = 0xFF0C0D10;
	public static final int WARN = 0xFFFF8C1A;
	public static final int ERROR = 0xFFFF5A5A;
	
	// RGB only, for the draw calls that take a colour without alpha
	public static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}
}
