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

	// Fill (SmartFill): the client world as a probe (null without one) and the player's box in world coordinates
	// {minX, minY, minZ, maxX, maxY, maxZ} (null: none). Defaults: no world
	public default IBlockProbe worldProbe() {
		return null;
	}

	public default double[] playerBox() {
		return null;
	}
	// Placement "At the crosshair block" (2026-10-28): {x, y, z} of the block the crosshair is on, null when it
	// points at no block. Default: none
	public default int[] crosshairBlock() {
		return null;
	}
	// See in the world (2026-10-28): the map colour (ARGB, shaded by height like a map) of the top block of each
	// column of a width x depth grid from x0, z0, one every step blocks, row by row along x; 0 where the chunk is not loaded. Null: no world
	public default int[] topColours(int x0, int z0, int width, int depth, int step) {
		return null;
	}

	// Smart fill (2026-10-22): whether this game runs the world itself (singleplayer, or the host of a LAN world),
	// the only place blocks can be taken out of what the player carries and placed as in survival
	public default boolean canSmartFill() {
		return false;
	}

	// The blocks the player carries, by block id, counting what the shulker boxes and backpacks among them hold;
	// null when they cannot be read. Never the boxes or backpacks themselves
	public default java.util.Map<String, Integer> carriedBlocks() {
		return null;
	}

	// A block's name to show; the id when it is unknown
	public default String blockName(String blockId) {
		return blockId;
	}

	/**
	 * Places one block per cell (world x, y, z triples): the first of the cell's candidates the player still has,
	 * taken out of what they carry (none in creative). done gets one SmartFill result per cell, on the thread that
	 * placed them. False when nothing could be sent (no world of this game).
	 */
	public default boolean placeBlocks(int[] xyz, String[][] candidates, java.util.function.Consumer<byte[]> done) {
		return false;
	}
}
