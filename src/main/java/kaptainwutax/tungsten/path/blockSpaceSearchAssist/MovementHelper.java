package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import static kaptainwutax.tungsten.path.blockSpaceSearchAssist.Ternary.*;

import net.minecraft.block.AirBlock;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.SlabType;

public class MovementHelper {

	static Ternary fullyPassableBlockState(BlockState state) {
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
                || block instanceof TrapdoorBlock
                || block instanceof EndPortalBlock
                || block instanceof SkullBlock
                || block instanceof ShulkerBoxBlock) {
            return NO;
        }
        return YES;
    }
	
	 static Ternary canWalkOnBlockState(BlockState state) {
	        Block block = state.getBlock();
	        if (isBlockNormalCube(state) && block != Blocks.MAGMA_BLOCK && block != Blocks.BUBBLE_COLUMN && block != Blocks.HONEY_BLOCK) {
	            return YES;
	        }
	        if (block instanceof AzaleaBlock) {
	            return YES;
	        }
	        if (block == Blocks.LADDER || block == Blocks.VINE) { // TODO reconsider this
	            return YES;
	        }
	        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
	            return YES;
	        }
	        if (block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
	            return YES;
	        }
	        if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
	            return YES;
	        }
	        if (block instanceof StairsBlock) {
	            return YES;
	        }
	        if (block instanceof SlabBlock) {
	            return YES;
	        }
	        return NO;
	    }
	 
	 static boolean isBlockNormalCube(BlockState state) {
	        Block block = state.getBlock();
	        if (block instanceof ScaffoldingBlock
	                || block instanceof ShulkerBoxBlock
	                || block instanceof PointedDripstoneBlock
	                || block instanceof AmethystClusterBlock) {
	            return false;
	        }
	        try {
	            return Block.isShapeFullCube(state.getCollisionShape(null, null));
	        } catch (Exception ignored) {
	            // if we can't get the collision shape, assume it's bad and add to blocksToAvoid
	        }
	        return false;
	    }
	 
	 static boolean isLava(BlockState state) {
	        return state.isOf(Blocks.LAVA);
	    }
	
}
