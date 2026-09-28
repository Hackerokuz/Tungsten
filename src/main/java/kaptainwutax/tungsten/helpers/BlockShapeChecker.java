package kaptainwutax.tungsten.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Helper class to easily get blocks shape data.
 */
public class BlockShapeChecker {
	
	/**
     * Calculates the height of a block at given position.
     * 
     * @param pos Position of the block
     * @return the height of a block at given position.
     */
	public static double getBlockHeight(BlockPos pos, Level world) {
		BlockState state = world.getBlockState(pos);
		
		if (state.isAir()) return 0;
		
		VoxelShape shape = state.getCollisionShape(world, pos);
		double height = shape.max(Direction.Axis.Y);
		
		return height;
	}
	
	/**
     * Calculates the volume in X and Z axis of a block at given position.
     * 
     * @param pos Position of the block
     * @return the volume of a block at given position.
     */
	public static double getShapeVolume(BlockPos pos, Level world) {
		BlockState state = world.getBlockState(pos);
    	return getShapeVolume(state, pos, world);
    }
	
	/**
     * Calculates the volume in X and Z axis of a block at given position.
     * 
     * @param pos Position of the block
     * @return the volume of a block at given position.
     */
	public static double getShapeVolume(BlockState state, BlockPos pos, Level world) {
		VoxelShape shape = state.getCollisionShape(world, pos);
//        if (shape.isEmpty()) shape = state.getOutlineShape(world, pos);
        
    	return getShapeVolume(shape);
    }
	
	/**
     * Calculates the volume in X and Z axis of a block at given position.
     * 
     * @param pos Position of the block
     * @return the volume of a block at given position.
     */
	public static double getShapeVolume(VoxelShape shape) {
    	
    	double maxX = shape.max(Direction.Axis.X);
    	double minX = shape.min(Direction.Axis.X);
    	double maxZ = shape.max(Direction.Axis.Z);
    	double minZ = shape.min(Direction.Axis.Z);
    	
    	double blockVolume = (maxX - minX) * (maxZ - minZ);
    	
    	if (Double.isInfinite(blockVolume))
        	blockVolume = 0;
    	
    	return blockVolume;
    }

    
    // Helper method to get blocks height
	public static double getBlockHeight(VoxelShape blockShape) {
        return blockShape.max(Direction.Axis.Y);
    }
    
	public static double getBlockHeight(Level world, BlockState state, BlockPos pos) {
    	VoxelShape blockShape = state.getCollisionShape(world, pos);
    	
    	return getBlockHeight(blockShape);
    }
    
    public static boolean hasBiggerCollisionShapeThanAbove(Level world, BlockPos pos) {
        // Get the block states of the block at pos and the two blocks above it
        BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock() instanceof LadderBlock) return false;
        
        // Calculate the volume of the collision shapes
        double blockVolume = BlockShapeChecker.getShapeVolume(pos, world);
        double aboveBlockVolume1 = BlockShapeChecker.getShapeVolume(pos.above(1), world);
        double aboveBlockVolume2 = BlockShapeChecker.getShapeVolume(pos.above(2), world);
        
        // Compare the volumes
        return blockVolume > aboveBlockVolume1 && blockVolume > aboveBlockVolume2;
    }
	 
   public static boolean isBlockNormalCube(BlockState state) {
	        Block block = state.getBlock();
	        if (block instanceof ScaffoldingBlock
	                || block instanceof ShulkerBoxBlock
	                || block instanceof PointedDripstoneBlock
	                || block instanceof AmethystClusterBlock) {
	            return false;
	        }
	        try {
	            return Block.isShapeFullBlock(state.getCollisionShape(null, null));
	        } catch (Exception ignored) {
	            // if we can't get the collision shape, assume it's bad and add to blocksToAvoid
	        }
	        return false;
    }
}
