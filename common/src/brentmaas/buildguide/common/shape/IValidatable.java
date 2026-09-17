package brentmaas.buildguide.common.shape;

import java.util.Set;

// A shape that can be checked against the world by the loader's render handler
public interface IValidatable {
	// Local (origin-relative) block positions packed with LocalPos.pack
	public Set<Long> getExpectedBlocks();
	
	// Returns true exactly once per validation request
	public boolean consumeValidateRequest();
}
