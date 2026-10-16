package brentmaas.buildguide.fabric.shape;

import java.util.Locale;
import java.util.Optional;

import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.common.shape.IShapeBuffer;
import brentmaas.buildguide.common.shape.IShapeHandler;
import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;
import brentmaas.buildguide.fabric.validation.WorldProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.permissions.Permissions;
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

	// Fill (FillTask): the client world, as the scan reads it
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

	// /fill needs the game master level (2): cheats on in singleplayer or on a LAN world, or op on a server
	@Override
	public boolean mayRunCommands() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	@Override
	public boolean sendCommand(String command) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if(connection == null) return false;
		connection.sendCommand(command);
		return true;
	}
}