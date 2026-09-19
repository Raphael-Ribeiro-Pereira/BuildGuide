package brentmaas.buildguide.fabric.validation;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.State;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.ShapeSet;
import brentmaas.buildguide.common.shape.ValidationState;
import brentmaas.buildguide.fabric.RenderHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps validated shapes up to date without a rescan. Called from MixinClientLevel for every
 * block the client sets (server updates, local prediction, destruction), on the client main
 * thread. The cheap checks come first: only validated shapes, only positions inside the
 * shape's expanded bounding box; then one synchronized map lookup per shape.
 */
public class IncrementalValidator {
	public static void onBlockChanged(BlockPos pos, BlockState blockState) {
		if(BuildGuide.stateManager == null) return;
		State state = BuildGuide.stateManager.getState();
		if(state == null || state.shapeSets == null) return;
		
		boolean air = blockState.isAir();
		boolean solid = !air && blockState.blocksMotion();
		boolean ignored = !air && RenderHandler.isIgnored(blockState);
		String name = null;
		for(ShapeSet set: state.shapeSets) {
			if(!set.isShapeAvailable(set.getIndex())) continue; // never instantiate a shape from a block event
			ValidationState vs = set.getShape().getValidationState(); // every Shape is IValidatable
			int lx = pos.getX() - set.getOriginX(), ly = pos.getY() - set.getOriginY(), lz = pos.getZ() - set.getOriginZ();
			if(!vs.isInRange(lx, ly, lz)) continue;
			if(name == null && !air && (!solid || ignored)) name = blockState.getBlock().getName().getString();
			vs.updateBlock(LocalPos.pack(lx, ly, lz), air, solid, ignored, name);
		}
	}
}
