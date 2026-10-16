package brentmaas.buildguide.fabric.shape;

import java.util.Locale;
import java.util.Optional;

import brentmaas.buildguide.common.shape.IShapeBuffer;
import brentmaas.buildguide.common.shape.IShapeHandler;
import brentmaas.buildguide.common.shape.SectionedBuffer;
import brentmaas.buildguide.common.shape.ShapeSet.Origin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
}