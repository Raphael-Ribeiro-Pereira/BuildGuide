package brentmaas.buildguide.common.screen.widget;

public interface ISelectorList extends IWidget {
	// Replace all entries (keeps the scroll position when possible); index in the callback follows the new list
	public void setEntries(java.util.List<brentmaas.buildguide.common.screen.AbstractScreenHandler.Translatable> titles);
	
	public static interface IEntry {
		
	}
	
	public interface ISelectorListCallback {
		public void run(int selected);
	}
}
