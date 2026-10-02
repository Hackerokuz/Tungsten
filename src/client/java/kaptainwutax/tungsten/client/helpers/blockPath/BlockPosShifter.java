package kaptainwutax.tungsten.client.helpers.blockPath;

import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BlockPosShifter {
	
	/**
     * Returns the position shifted closer to the ladder.
     * 
     * @param blockNode node to be shifted
     * @return node's position shifted closer to the ladder.
     */
	public static Vec3 getPosOnLadder(BlockNode blockNode, Level world) {
		BlockState blockState = world.getBlockState(blockNode.getBlockPos());
		BlockState blockBelowState = world.getBlockState(blockNode.getBlockPos().below());
		Vec3 currPos = blockNode.getPos().add(0.5, 0, 0.5);
		if (!(blockState.getBlock() instanceof LadderBlock) && !(blockBelowState.getBlock() instanceof LadderBlock)) {
			return currPos;
		}
		
		double insetAmount = 0.4;
		
		if (blockBelowState.getBlock() instanceof LadderBlock && !(blockState.getBlock() instanceof LadderBlock)) {
			Direction ladderFacingDir = blockBelowState.getValue(LadderBlock.FACING);
			
			currPos = currPos.relative(ladderFacingDir.getOpposite(), insetAmount).add(0, 0.3, 0);
			
			return currPos;
		}
		
		Direction ladderFacingDir = blockState.getValue(LadderBlock.FACING);
		
		currPos = currPos.relative(ladderFacingDir.getOpposite(), insetAmount).add(0, 0.6, 0);
		
		return currPos;
	}
	
	/**
     * Returns the position shifted for a NEO jump.
     * 
     * @param blockNode node to be shifted
     * @return node's position shifted for a NEO jump.
     */
	public static Vec3 shiftForStraightNeo(BlockNode blockNode, Direction dir) {
		if (dir == Direction.DOWN || dir == Direction.UP) {
			throw new IllegalArgumentException("Only horizontal directions may be passed!");
		}
		Vec3 currPos = new Vec3(blockNode.getBlockPos().getX() + 0.5, blockNode.getBlockPos().getY(), blockNode.getBlockPos().getZ() + 0.5).relative(dir, 0.55);
		
		return currPos;
	}

}
