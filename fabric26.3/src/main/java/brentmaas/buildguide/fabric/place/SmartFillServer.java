package brentmaas.buildguide.fabric.place;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.SmartFill;
import brentmaas.buildguide.common.shape.SmartFillPlan;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Smart fill (SmartFill) on the server this game runs (singleplayer, or the host of a LAN world): the blocks the
 * player carries are counted and taken, and placed, on the server thread, as the player's own blocks. Nothing
 * here runs on another kind of server, which would need this mod too.
 *
 * Where blocks come from, in the order they are taken: the inventory (hotbar, main slots, off hand), the
 * Traveler's Backpack worn on the back, Traveler's Backpacks carried, then the shulker boxes carried. A shulker
 * box or a backpack is never a block to place itself. The backpack is read without depending on its mod: the
 * worn one through its own AttachmentUtils.getBackpackWrapper(player).getStorage() (a vanilla Container that
 * saves itself), a carried one through its item component, by reflection; without the mod, or if it changes,
 * that source is skipped.
 *
 * Water (2026-10-22), a bucket's worth per cell, as a player emptying a water bucket there (BucketItem's own
 * emptyContents: into a dry slab, stairs or leaves, over grass, evaporating where water does). Where it comes
 * from, in order: nothing is taken while the player stands in water that refills itself (an endless pool: the
 * backpack draws on it); else the worn backpack's tanks, any item with a fluid storage (Fabric's transfer API:
 * water buckets, tanks), water buckets in shulker boxes, and last the water block the player stands in.
 */
public final class SmartFillServer {
	private static final String backpackUtils = "com.tiviacz.travelersbackpack.attachment.AttachmentUtils", backpackTanks = "com.tiviacz.travelersbackpack.inventory.upgrades.tanks.TanksUpgrade";
	private static final Identifier backpackContents = Identifier.fromNamespaceAndPath("travelersbackpack", "backpack_container");
	private static final long countTimeoutMillis = 2000;
	private static boolean backpackWarned = false;

	private SmartFillServer() {}

	// One stack the player carries, wherever it is, read again each time, and how one item is taken from it or the
	// stack changed for another (a water bucket emptied into a bucket)
	private interface Slot {
		ItemStack stack();

		void takeOne();

		void replace(ItemStack with);
	}

	public static boolean available() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.level != null && mc.hasSingleplayerServer();
	}

	// Counted on the server thread; the client waits for it (the panel opening), at most countTimeoutMillis
	public static Map<String, Integer> carriedBlocks() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		Player client = Minecraft.getInstance().player;
		if(server == null || client == null) return null;
		UUID id = client.getUUID();
		try {
			return server.submit(() -> {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if(player == null) return null;
				Map<String, Integer> out = count(player);
				int water = waterCount(player.level() instanceof ServerLevel level ? level : null, player);
				if(water > 0) out.put(SmartFill.WATER, water);
				return out;
			}).get(countTimeoutMillis, TimeUnit.MILLISECONDS);
		}catch(Exception e) {
			BuildGuide.logHandler.error("Smart fill: the inventory could not be read: " + e);
			return null;
		}
	}

	public static boolean placeBlocks(int[] xyz, String[][] candidates, Consumer<byte[]> done) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		Minecraft mc = Minecraft.getInstance();
		if(server == null || mc.player == null || mc.level == null) return false;
		UUID id = mc.player.getUUID();
		ResourceKey<Level> dimension = mc.level.dimension();
		server.execute(() -> {
			byte[] codes = new byte[candidates.length];
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			ServerLevel level = server.getLevel(dimension);
			if(player == null || level == null) {
				java.util.Arrays.fill(codes, SmartFill.UNLOADED);
			}else {
				List<Slot> slots = slots(player);
				BlockPos last = null, lastWater = null;
				BlockState lastState = null;
				for(int i = 0;i < candidates.length;++i) {
					BlockPos pos = new BlockPos(xyz[3 * i], xyz[3 * i + 1], xyz[3 * i + 2]);
					if(candidates[i].length == 1 && SmartFill.WATER.equals(candidates[i][0])) {
						codes[i] = placeWater(level, player, pos);
						if(codes[i] == SmartFill.PLACED) lastWater = pos;
						continue;
					}
					BlockState[] placed = new BlockState[1];
					codes[i] = place(level, player, slots, pos, candidates[i], placed);
					if(codes[i] == SmartFill.PLACED) {
						last = pos;
						lastState = placed[0];
					}
				}
				// One sound per batch, as a player placing the last of them would make (emptyContents gave the water
				// none: its sound goes to everyone but the player, who is the one emptying the bucket)
				if(last != null) {
					SoundType sound = lastState.getSoundType();
					level.playSound(null, last, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				}
				if(lastWater != null) level.playSound(null, lastWater, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			done.accept(codes);
		});
		return true;
	}

	private static byte place(ServerLevel level, ServerPlayer player, List<Slot> slots, BlockPos pos, String[] candidates, BlockState[] placed) {
		if(!level.isLoaded(pos)) return SmartFill.UNLOADED;
		if(!level.getBlockState(pos).canBeReplaced()) return SmartFill.TAKEN;
		boolean creative = player.hasInfiniteMaterials();
		String id = SmartFillPlan.firstAvailable(candidates, block -> creative || find(slots, block) != null);
		if(id == null) return SmartFill.NO_BLOCKS;
		Block block = blockOf(id);
		if(block == null) return SmartFill.NO_BLOCKS;
		Slot slot = creative ? null : find(slots, id);
		ItemStack item = slot != null ? slot.stack().copyWithCount(1) : new ItemStack(block);
		// As if the player clicked the top of the block below: stairs and the like face the way they look
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, item, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		// null: the block says it cannot go there (a door without room above it)
		BlockState state = block.getStateForPlacement(context);
		if(state == null || !state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, CollisionContext.empty())) return SmartFill.TAKEN;
		if(!level.setBlock(pos, state, Block.UPDATE_ALL)) return SmartFill.TAKEN;
		// As BlockItem.place does: a door's upper half, a bed's head, a tall flower's top
		block.setPlacedBy(level, pos, state, player, item);
		if(slot != null) slot.takeOne();
		placed[0] = state;
		return SmartFill.PLACED;
	}

	// Water: a bucket's worth, unless the cell has water already or cannot take it (a solid block)
	private static byte placeWater(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if(!level.isLoaded(pos)) return SmartFill.UNLOADED;
		BlockState current = level.getBlockState(pos);
		if(current.getFluidState().isSourceOfType(Fluids.WATER)) return SmartFill.TAKEN;
		boolean takesIn = current.getBlock() instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, current, Fluids.WATER);
		if(!current.isAir() && !current.canBeReplaced(Fluids.WATER) && !takesIn) return SmartFill.TAKEN;
		if(!player.hasInfiniteMaterials() && waterCount(level, player) <= 0) return SmartFill.NO_BLOCKS;
		if(!((BucketItem) Items.WATER_BUCKET).emptyContents(player, level, pos, null)) return SmartFill.TAKEN;
		if(!player.hasInfiniteMaterials()) takeWater(level, player);
		return SmartFill.PLACED;
	}

	// How many buckets' worth of water the player has; Integer.MAX_VALUE while they stand in an endless pool
	private static int waterCount(ServerLevel level, ServerPlayer player) {
		if(level != null && inEndlessWater(level, player)) return Integer.MAX_VALUE;
		long droplets = 0;
		for(Storage<FluidVariant> storage: waterStorages(player)) droplets += StorageUtil.simulateExtract(storage, FluidVariant.of(Fluids.WATER), Long.MAX_VALUE, null);
		long buckets = droplets / FluidConstants.BUCKET;
		for(Slot slot: shulkerSlots(player)) if(slot.stack().is(Items.WATER_BUCKET)) ++buckets;
		if(level != null && level.getFluidState(player.blockPosition()).isSourceOfType(Fluids.WATER)) ++buckets;
		return (int) Math.min(Integer.MAX_VALUE - 1, buckets);
	}

	// One bucket's worth, from the first place that has it (none from an endless pool)
	private static boolean takeWater(ServerLevel level, ServerPlayer player) {
		if(inEndlessWater(level, player)) return true;
		for(Storage<FluidVariant> storage: waterStorages(player)) {
			try(Transaction transaction = Transaction.openOuter()) {
				if(storage.extract(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction) == FluidConstants.BUCKET) {
					transaction.commit();
					return true;
				}
			}
		}
		for(Slot slot: shulkerSlots(player)) {
			if(slot.stack().is(Items.WATER_BUCKET)) {
				slot.replace(new ItemStack(Items.BUCKET));
				return true;
			}
		}
		BlockPos feet = player.blockPosition();
		BlockState state = level.getBlockState(feet);
		return state.getFluidState().isSourceOfType(Fluids.WATER) && state.getBlock() instanceof BucketPickup pickup && !pickup.pickupBlock(player, level, feet, state).isEmpty();
	}

	// The player's feet or head in a water block that refills itself: two water sources beside it, and water or a
	// solid block under it (vanilla turns such a block back into a source)
	private static boolean inEndlessWater(ServerLevel level, ServerPlayer player) {
		return endlessWater(level, player.blockPosition()) || endlessWater(level, BlockPos.containing(player.getEyePosition()));
	}

	private static boolean endlessWater(ServerLevel level, BlockPos pos) {
		if(!level.getFluidState(pos).isSourceOfType(Fluids.WATER)) return false;
		int sources = 0;
		for(Direction side: Direction.Plane.HORIZONTAL) if(level.getFluidState(pos.relative(side)).isSourceOfType(Fluids.WATER)) ++sources;
		BlockState below = level.getBlockState(pos.below());
		return sources >= 2 && (below.isSolid() || below.getFluidState().isSourceOfType(Fluids.WATER));
	}

	// What holds water, in the order it is emptied: the worn backpack's tanks, then every carried item with a fluid
	// storage (a water bucket, a tank of another mod)
	private static List<Storage<FluidVariant>> waterStorages(ServerPlayer player) {
		List<Storage<FluidVariant>> out = new ArrayList<Storage<FluidVariant>>(wornTanks(player));
		for(SingleSlotStorage<ItemVariant> slot: PlayerInventoryStorage.of(player).getSlots()) {
			if(slot.isResourceBlank()) continue;
			Storage<FluidVariant> storage = ContainerItemContext.ofPlayerSlot(player, slot).find(FluidStorage.ITEM);
			if(storage != null) out.add(storage);
		}
		return out;
	}

	private static Slot find(List<Slot> slots, String id) {
		for(Slot slot: slots) {
			ItemStack stack = slot.stack();
			if(!stack.isEmpty() && id.equals(placeableId(stack))) return slot;
		}
		return null;
	}

	private static Map<String, Integer> count(ServerPlayer player) {
		Map<String, Integer> out = new LinkedHashMap<String, Integer>();
		for(Slot slot: slots(player)) {
			ItemStack stack = slot.stack();
			String id = stack.isEmpty() ? null : placeableId(stack);
			if(id != null) out.merge(id, stack.getCount(), Integer::sum);
		}
		return out;
	}

	// The block an item places, or null: not a block item, air, or a container (a shulker box, a backpack)
	private static String placeableId(ItemStack stack) {
		if(!(stack.getItem() instanceof BlockItem item)) return null;
		Block block = item.getBlock();
		if(block instanceof ShulkerBoxBlock || block.defaultBlockState().isAir() || isBackpack(stack)) return null;
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	private static Block blockOf(String id) {
		Identifier key = Identifier.tryParse(id);
		if(key == null) return null;
		return BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
	}

	// Every stack the player carries, in the order blocks are taken from them
	private static List<Slot> slots(ServerPlayer player) {
		List<Slot> out = new ArrayList<Slot>();
		Inventory inventory = player.getInventory();
		int main = inventory.getNonEquipmentItems().size();
		for(int i = 0;i < main;++i) out.add(containerSlot(inventory, i));
		out.add(containerSlot(inventory, Inventory.SLOT_OFFHAND));
		Container worn = wornBackpack(player);
		if(worn != null) {
			for(int i = 0;i < worn.getContainerSize();++i) out.add(containerSlot(worn, i));
		}
		for(ItemStack holder: holders(player)) {
			if(isBackpack(holder)) addBackpackSlots(out, holder);
		}
		out.addAll(shulkerSlots(player));
		return out;
	}

	private static List<ItemStack> holders(ServerPlayer player) {
		List<ItemStack> holders = new ArrayList<ItemStack>(player.getInventory().getNonEquipmentItems());
		holders.add(player.getOffhandItem());
		return holders;
	}

	// The slots of the shulker boxes carried (any item with a container component)
	private static List<Slot> shulkerSlots(ServerPlayer player) {
		List<Slot> out = new ArrayList<Slot>();
		for(ItemStack holder: holders(player)) {
			ItemContainerContents contents = holder.get(DataComponents.CONTAINER);
			if(contents == null) continue;
			int size = contents.size();
			for(int i = 0;i < size;++i) out.add(shulkerSlot(holder, i));
		}
		return out;
	}

	private static Slot containerSlot(Container container, int index) {
		return new Slot() {
			public ItemStack stack() {
				return container.getItem(index);
			}

			public void takeOne() {
				container.removeItem(index, 1);
				container.setChanged();
			}

			public void replace(ItemStack with) {
				container.setItem(index, with);
				container.setChanged();
			}
		};
	}

	// A slot of a shulker box (or any item with a container component): the component is replaced on each take
	private static Slot shulkerSlot(ItemStack holder, int index) {
		return new Slot() {
			public ItemStack stack() {
				List<ItemStack> items = items(holder);
				return index < items.size() ? items.get(index) : ItemStack.EMPTY;
			}

			public void takeOne() {
				ItemStack less = stack().copy();
				less.shrink(1);
				replace(less);
			}

			public void replace(ItemStack with) {
				List<ItemStack> items = new ArrayList<ItemStack>();
				for(ItemStack s: items(holder)) items.add(s.copy());
				if(index >= items.size()) return;
				items.set(index, with);
				holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			}
		};
	}

	private static List<ItemStack> items(ItemStack holder) {
		ItemContainerContents contents = holder.get(DataComponents.CONTAINER);
		return contents == null ? new ArrayList<ItemStack>() : contents.itemCopies().toList();
	}

	// Traveler's Backpack, by reflection (no compile dependency on it)

	private static DataComponentType<?> backpackType() {
		return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(backpackContents).orElse(null);
	}

	private static boolean isBackpack(ItemStack stack) {
		DataComponentType<?> type = backpackType();
		return type != null && stack.has(type);
	}

	// The worn backpack's BackpackWrapper, null when none is worn or the mod is not there
	private static Object wornWrapper(ServerPlayer player) {
		try {
			Class<?> utils = Class.forName(backpackUtils);
			if(!(Boolean) utils.getMethod("isWearingBackpack", Player.class).invoke(null, player)) return null;
			return utils.getMethod("getBackpackWrapper", Player.class).invoke(null, player);
		}catch(ClassNotFoundException e) {
			return null; // the mod is not there
		}catch(Throwable e) {
			warnBackpack(e);
			return null;
		}
	}

	private static Container wornBackpack(ServerPlayer player) {
		Object wrapper = wornWrapper(player);
		if(wrapper == null) return null;
		try {
			Object storage = wrapper.getClass().getMethod("getStorage").invoke(wrapper);
			return storage instanceof Container container ? container : null;
		}catch(Throwable e) {
			warnBackpack(e);
			return null;
		}
	}

	// The worn backpack's two tanks, when it has the Tanks upgrade (each a transfer API storage of its own)
	@SuppressWarnings("unchecked")
	private static List<Storage<FluidVariant>> wornTanks(ServerPlayer player) {
		List<Storage<FluidVariant>> out = new ArrayList<Storage<FluidVariant>>();
		Object wrapper = wornWrapper(player);
		if(wrapper == null) return out;
		try {
			Class<?> tanks = Class.forName(backpackTanks);
			Optional<?> upgrade = (Optional<?>) wrapper.getClass().getMethod("getUpgrade", Class.class).invoke(wrapper, tanks);
			if(upgrade.isEmpty()) return out;
			for(String side: new String[] {"getLeftTank", "getRightTank"}) {
				Object tank = tanks.getMethod(side).invoke(upgrade.get());
				if(tank instanceof Storage<?> storage) out.add((Storage<FluidVariant>) storage);
			}
		}catch(Throwable e) {
			warnBackpack(e);
		}
		return out;
	}

	// A carried backpack: its component's list of stacks; a take puts a new component made from a changed copy
	private static void addBackpackSlots(List<Slot> out, ItemStack holder) {
		List<ItemStack> items = backpackItems(holder);
		for(int i = 0;i < items.size();++i) {
			final int index = i;
			out.add(new Slot() {
				public ItemStack stack() {
					List<ItemStack> now = backpackItems(holder);
					return index < now.size() ? now.get(index) : ItemStack.EMPTY;
				}

				public void takeOne() {
					ItemStack less = stack().copy();
					less.shrink(1);
					replace(less);
				}

				@SuppressWarnings("unchecked")
				public void replace(ItemStack with) {
					try {
						DataComponentType<Object> type = (DataComponentType<Object>) backpackType();
						Object contents = holder.get(type);
						List<ItemStack> copy = new ArrayList<ItemStack>();
						for(ItemStack s: backpackItems(holder)) copy.add(s.copy());
						if(index >= copy.size()) return;
						copy.set(index, with);
						Method fromItems = contents.getClass().getMethod("fromItems", int.class, List.class);
						holder.set(type, fromItems.invoke(null, copy.size(), copy));
					}catch(Throwable e) {
						warnBackpack(e);
					}
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	private static List<ItemStack> backpackItems(ItemStack holder) {
		try {
			DataComponentType<?> type = backpackType();
			Object contents = type == null ? null : holder.get(type);
			if(contents == null) return new ArrayList<ItemStack>();
			return (List<ItemStack>) contents.getClass().getMethod("getItems").invoke(contents);
		}catch(Throwable e) {
			warnBackpack(e);
			return new ArrayList<ItemStack>();
		}
	}

	private static void warnBackpack(Throwable e) {
		if(backpackWarned) return;
		backpackWarned = true;
		BuildGuide.logHandler.error("Smart fill: Traveler's Backpack could not be read, its blocks are left out: " + e);
	}
}
