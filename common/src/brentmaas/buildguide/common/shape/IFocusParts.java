package brentmaas.buildguide.common.shape;

/**
 * Build focus: the parts a shape's blocks belong to (FocusFilter). Each block of a generation belongs to
 * exactly one part, computed from its position and what that generation kept, never stored per block. A
 * shape without parts keeps the defaults: one part, FocusFilter.WHOLE.
 */
public interface IFocusParts {
	// Whether the last generation's blocks have parts (the Part button then offers Cap, Body and Spikes)
	default boolean hasParts() {
		return false;
	}

	// The parts the Part button offers between All and Hidden (the Island's by default when it has parts)
	default FocusFilter.Part[] focusParts() {
		return hasParts() ? FocusFilter.islandParts : new FocusFilter.Part[0];
	}

	// The part of the last generation's block at this local position
	default FocusFilter.Part partOf(int x, int y, int z) {
		return FocusFilter.WHOLE;
	}
}
