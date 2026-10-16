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

	// Fill (FillTask): the client world as a probe (null without one), the player's box in world coordinates
	// {minX, minY, minZ, maxX, maxY, maxZ} (null: none), whether the player may run /fill (cheats or op), and one
	// command sent to the server as if typed, without the slash (false: no connection). Defaults: no world
	public default IBlockProbe worldProbe() {
		return null;
	}

	public default double[] playerBox() {
		return null;
	}

	public default boolean mayRunCommands() {
		return false;
	}

	public default boolean sendCommand(String command) {
		return false;
	}
}
