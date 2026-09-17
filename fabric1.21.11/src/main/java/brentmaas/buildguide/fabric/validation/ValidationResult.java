package brentmaas.buildguide.fabric.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
	public final int blocksOk;
	public final int blocksMissing;
	public final int blocksWrong;
	public final List<NearBlock> nearBlocks;
	
	public ValidationResult(int ok, int missing, int wrong, List<NearBlock> near) {
		blocksOk = ok;
		blocksMissing = missing;
		blocksWrong = wrong;
		nearBlocks = near != null ? near : new ArrayList<NearBlock>();
	}
}
