package brentmaas.buildguide.fabric.validation;

import brentmaas.buildguide.common.shape.IBlockProbe;
import brentmaas.buildguide.fabric.RenderHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** IBlockProbe over the client world, for StateReconciler and SliceScan (render thread only: one reused mutable position). */
public class WorldProbe implements IBlockProbe {
	private final ClientLevel world;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	
	public WorldProbe(ClientLevel world) {
		this.world = world;
	}
	
	@Override
	public boolean isLoaded(int x, int y, int z) {
		return world.hasChunkAt(pos.set(x, y, z));
	}
	
	// The same classification as the scan (RenderHandler.validateShape) and IncrementalValidator
	@Override
	public int flags(int x, int y, int z) {
		BlockState state = world.getBlockState(pos.set(x, y, z));
		if(state.isAir()) return FLAG_AIR;
		int flags = 0;
		if(state.blocksMotion()) flags |= FLAG_SOLID;
		if(RenderHandler.isIgnored(state)) flags |= FLAG_IGNORED;
		if(state.canBeReplaced()) flags |= FLAG_REPLACEABLE; // water, tall grass: a placed block takes their place (area 3)
		return flags;
	}
	
	// The structure-error sweep (SliceScan): air, solid, and the ignored test only for solid blocks, as the
	// scan always short-circuited it (the registry lookup is the expensive part)
	@Override
	public int nearFlags(int x, int y, int z) {
		BlockState state = world.getBlockState(pos.set(x, y, z));
		if(state.isAir()) return FLAG_AIR;
		if(!state.blocksMotion()) return 0;
		return RenderHandler.isIgnored(state) ? FLAG_SOLID | FLAG_IGNORED : FLAG_SOLID;
	}
	
	@Override
	public String name(int x, int y, int z) {
		return world.getBlockState(pos.set(x, y, z)).getBlock().getName().getString();
	}
}
