package brentmaas.buildguide.common.shape;

import brentmaas.buildguide.common.shape.ShapeSet.Origin;

public interface IShapeHandler {
	public IShapeBuffer newBuffer();
	
	public Origin getPlayerPosition();
	
	// Palette per layer (4D): a typed block id checked against the loader's registry, normalised ("stone" ->
	// "minecraft:stone"), or null when it is not a block (unknown, or air). Default: nothing is a block
	public default String normalizeBlockId(String typed) {
		return null;
	}
	
	// The id of the block in the player's main hand, or null (empty hand, an item that is not a block). Default: none
	public default String heldBlockId() {
		return null;
	}
}
