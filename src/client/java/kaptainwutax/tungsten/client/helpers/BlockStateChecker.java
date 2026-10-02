package kaptainwutax.tungsten.client.helpers;

import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.Ternary;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;

import static kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.Ternary.NO;
import static kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.Ternary.YES;


/**
 * Helper class to easily check block state.
 */
public class BlockStateChecker {
	
	public static Ternary fullyPassableBlockState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AirBlock) { // early return for most common case
            return YES;
        }
        // exceptions - blocks that are isPassable true, but we can't actually jump through
        if (block instanceof FireBlock
                || block == Blocks.TRIPWIRE
                || block == Blocks.COBWEB
                || block == Blocks.VINE
                || block == Blocks.LADDER
                || block == Blocks.COCOA
                || block instanceof AzaleaBlock
                || block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || !state.getFluidState().isEmpty()
                || block instanceof TrapDoorBlock
                || block instanceof EndPortalBlock
                || block instanceof SkullBlock
                || block instanceof ShulkerBoxBlock
				|| block instanceof LeavesBlock
				|| block instanceof MangroveRootsBlock) {
            return NO;
        }
		if (state.isPathfindable(PathComputationType.LAND)) {
			return YES;
		} else {
			return NO;
		}
    }
	
	/**
     * Checks if a block is connected to another of its type.
     * 
     * @param pos Position of the block
     * @return true if a block is connected to another of its type. Example: fence to fence
     */
	public static boolean isConnected(BlockPos pos, Level world) {
	    BlockState state = world.getBlockState(pos);
	    Block block = state.getBlock();

	    // Check for Fence connections
	    if (block instanceof FenceBlock) {
	        return isFenceConnected(state, world, pos);
	    }

	    // Check for Wall connections
	    if (block instanceof WallBlock) {
	        return isWallConnected(state);
	    }

	    // Check for Glass Pane connections
	    if (block instanceof StainedGlassPaneBlock) {
	        return isGlassPaneConnected(state, world, pos);
	    }

	    return false;
	}

	/**
     * Checks if a block is connected to another of its type.
     * 
     * @param state BlockState for this block
     * @param world World View
     * @param pos Position of the block
     * @return true if a block is connected to another of its type. Example: fence to fence
     */
	public static boolean isFenceConnected(BlockState state, Level world, BlockPos pos) {
	    return state.getValue(FenceBlock.NORTH) && isFence(world, pos.north())
	        || state.getValue(FenceBlock.SOUTH) && isFence(world, pos.south())
	        || state.getValue(FenceBlock.EAST) && isFence(world, pos.east())
	        || state.getValue(FenceBlock.WEST) && isFence(world, pos.west());
	}

	public static boolean isFence(Level world, BlockPos pos) {
	    Block block = world.getBlockState(pos).getBlock();
	    return block instanceof FenceBlock;
	}

	/**
     * Checks if a wall block is connected to anything.
     * 
     * @param state BlockState for this block
     * @return true if wall block is connected to anything.
     */
	public static boolean isWallConnected(BlockState state) {
	    return state.getValue(WallBlock.NORTH) != WallSide.NONE
	        || state.getValue(WallBlock.SOUTH) != WallSide.NONE
	        || state.getValue(WallBlock.EAST) != WallSide.NONE
	        || state.getValue(WallBlock.WEST) != WallSide.NONE;
	}

	public static boolean isGlassPaneConnected(BlockState state, Level world, BlockPos pos) {
	    return state.getValue(StainedGlassPaneBlock.NORTH)
	        || state.getValue(StainedGlassPaneBlock.SOUTH)
	        || state.getValue(StainedGlassPaneBlock.EAST)
	        || state.getValue(StainedGlassPaneBlock.WEST);
	}

	public static boolean isPane(Level world, BlockPos pos) {
	    Block block = world.getBlockState(pos).getBlock();
	    return block instanceof StainedGlassPaneBlock;
	}
	
	public static boolean isSlab(Level world, BlockPos pos) {
	    return world.getBlockState(pos).getBlock() instanceof SlabBlock;
	}

	/**
     * Checks if a block is a double slab.
     * 
     * @param world
     * @param pos Position of the block
     * @return true if a block is a double slab.
     */
	public static boolean isDoubleSlab(Level world, BlockPos pos) {
	    BlockState state = world.getBlockState(pos);
	    Block block = state.getBlock();
	    return block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
	}
	
	public static boolean isTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock;
    }
	
	public static boolean isTrapdoor(Block block) {
        return block instanceof TrapDoorBlock;
    }

    // Helper method to check if the block is an open trapdoor
	public static boolean isOpenTrapdoor(BlockState state) {
        return isTrapdoor(state) && state.getValue(TrapDoorBlock.OPEN);
    }
	
	public static boolean isClosedBottomTrapdoor(BlockState state) {
        return isTrapdoor(state) && state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM && !state.getValue(TrapDoorBlock.OPEN);
    }

    // Helper method to check if the block is a bottom slab
	public static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }
	
    // Helper method to check if the block is a top slab
	public static boolean isTopSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }
	
    // Helper method to check if the block contains water
	public static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER) || state.getFluidState().is(Fluids.WATER);
    }
	
    // Helper method to check if the block is flowing water
	public static boolean isFlowingWater(BlockState state) {
        return state.getFluidState().is(Fluids.FLOWING_WATER);
    }
	
	// Helper method to check if the block is water. Either flowing or source
	public static boolean isAnyWater(BlockState state) {
        return isWater(state) || isFlowingWater(state);
    }
	

    // Helper method to check if the block is water logged
	public static boolean isWaterLogged(BlockState state) {
        return state.getProperties().contains(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    // Helper method to check if the block is lava
	static boolean isLava(BlockState state) {
        return state.is(Blocks.LAVA) || state.getFluidState().is(Fluids.LAVA);
    }

    // Helper method to check if the block is flowing lava
	static boolean isFlowingLava(BlockState state) {
        return state.getFluidState().is(Fluids.FLOWING_LAVA);
    }

	// Helper method to check if the block is lava. Either flowing or source
	static boolean isAnyLava(BlockState state) {
        return isLava(state) || isFlowingLava(state);
    }


}
