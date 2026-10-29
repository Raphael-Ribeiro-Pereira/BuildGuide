package brentmaas.buildguide.fabric.shape;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.IShapeBuffer;
import brentmaas.buildguide.common.shape.IShapeHandler;
import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;
import brentmaas.buildguide.fabric.place.SmartFillServer;
import brentmaas.buildguide.fabric.validation.WorldProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class ShapeHandler implements IShapeHandler {
	// The world buffer of a shape (Shape is its only caller): in 16-block sections, each one a ShapeBuffer, so
	// RenderHandler draws only the sections in view and a new shape is sent a few sections per frame
	public IShapeBuffer newBuffer() {
		return new SectionedBuffer(ShapeBuffer::new);
	}
	
	public Origin getPlayerPosition() {
		Vec3 pos = Minecraft.getInstance().player.position();
		return new Origin((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
	}
	
	// Palette per layer (4D): "stone" and "Minecraft:Stone" become "minecraft:stone"; null unless it is a block (not air)
	@Override
	public String normalizeBlockId(String typed) {
		Identifier key = Identifier.tryParse(typed.trim().toLowerCase(Locale.ROOT));
		if(key == null) return null;
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(key);
		if(block.isEmpty() || block.get().defaultBlockState().isAir()) return null;
		return BuiltInRegistries.BLOCK.getKey(block.get()).toString();
	}
	
	// The block of the item in the main hand; null for an empty hand or an item that places no block
	@Override
	public String heldBlockId() {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null || !(player.getMainHandItem().getItem() instanceof BlockItem item)) return null;
		Block block = item.getBlock();
		return block.defaultBlockState().isAir() ? null : BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	// Placement "At the crosshair block": the block the crosshair is on, null when it points at no block
	@Override
	public int[] crosshairBlock() {
		HitResult hit = Minecraft.getInstance().hitResult;
		if(!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
		BlockPos pos = blockHit.getBlockPos();
		return new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	// See in the world: each column's top block in its map colour, brighter or darker than the column to its north
	// as a map shades slopes; 0 where the chunk is not loaded
	@Override
	public int[] topColours(int x0, int z0, int width, int depth, int step) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return null;
		int[] out = new int[width * depth];
		int[] height = new int[width * depth];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for(int dz = 0;dz < depth;++dz) {
			for(int dx = 0;dx < width;++dx) {
				int x = x0 + dx * step, z = z0 + dz * step, i = dz * width + dx;
				if(!level.hasChunk(x >> 4, z >> 4)) continue;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				pos.set(x, y, z);
				BlockState state = level.getBlockState(pos);
				height[i] = y;
				int colour = state.getMapColor(level, pos).col;
				int shade = dz == 0 || out[i - width] == 0 ? 220 : y > height[i - width] ? 255 : y < height[i - width] ? 180 : 220;
				int r = ((colour >> 16) & 0xFF) * shade / 255, g = ((colour >> 8) & 0xFF) * shade / 255, b = (colour & 0xFF) * shade / 255;
				out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
			}
		}
		return out;
	}

	// The Scan: the blocks of the box that can be seen (not air, with air on one of their six sides), with their
	// map colour; the box is read top down, so a full budget keeps the surface
	@Override
	public int[] scanBlocks(int x0, int yMin, int z0, int width, int yMax, int depth, int maxBlocks) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return null;
		int[] out = new int[4 * Math.max(0, maxBlocks)];
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
		for(int y = yMax;y >= yMin && n < maxBlocks;--y) {
			for(int dz = 0;dz < depth && n < maxBlocks;++dz) {
				for(int dx = 0;dx < width && n < maxBlocks;++dx) {
					int x = x0 + dx, z = z0 + dz;
					if(!level.hasChunk(x >> 4, z >> 4)) continue;
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if(state.isAir()) continue;
					boolean seen = false;
					for(int[] d: SIDES) {
						side.set(x + d[0], y + d[1], z + d[2]);
						if(level.getBlockState(side).isAir()) {
							seen = true;
							break;
						}
					}
					if(!seen) continue;
					out[4 * n] = x;
					out[4 * n + 1] = y;
					out[4 * n + 2] = z;
					int colour = state.getMapColor(level, pos).col;
					out[4 * n + 3] = colour == 0 ? 0x9AA1AD : colour; // a block without a map colour (glass): grey
					++n;
				}
			}
		}
		return java.util.Arrays.copyOf(out, 4 * n);
	}

	private static final int[][] SIDES = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

	// Fill (SmartFill): the client world, as the scan reads it
	@Override
	public IBlockProbe worldProbe() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? null : new WorldProbe(level);
	}

	@Override
	public double[] playerBox() {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null) return null;
		AABB box = player.getBoundingBox();
		return new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
	}

	// Smart fill: on the server this game runs (SmartFillServer)
	@Override
	public boolean canSmartFill() {
		return SmartFillServer.available();
	}

	@Override
	public Map<String, Integer> carriedBlocks() {
		return SmartFillServer.carriedBlocks();
	}

	@Override
	public String blockName(String blockId) {
		Identifier key = Identifier.tryParse(blockId);
		Optional<Block> block = key == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(key);
		return block.isEmpty() ? blockId : block.get().getName().getString();
	}

	@Override
	public boolean placeBlocks(int[] xyz, String[][] candidates, Consumer<byte[]> done) {
		return SmartFillServer.placeBlocks(xyz, candidates, done);
	}
}