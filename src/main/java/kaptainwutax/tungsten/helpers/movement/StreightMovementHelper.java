package kaptainwutax.tungsten.helpers.movement;

import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.MovementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class StreightMovementHelper {
	
	public static boolean isPossible(Level world, BlockPos startPos, BlockPos endPos) {
		return isPossible(world, startPos, endPos, false, false);
	}
	
	public static boolean isPossible(Level world, BlockPos startPos, BlockPos endPos, boolean shouldRender, boolean shouldSlow) {
		
		boolean isJumpingUp = endPos.getY() - startPos.getY() == 1;

    	int dx = startPos.getX() - endPos.getX();
    	int dz = startPos.getZ() - endPos.getZ();
    	double distance = Math.sqrt(dx * dx + dz * dz);
		boolean isJumpingOneBlock = distance == 1;
	    PathNavigator navigator = new PathNavigator(world, isJumpingUp, isJumpingOneBlock, shouldRender, shouldSlow);

	    return navigator.traversePath(startPos, endPos);
	}

	private static class PathNavigator {
	    private final Level world;
	    private final boolean isJumpingUp;
	    private final boolean isJumpingOneBlock;
	    private final boolean shouldRender;
	    private final boolean shouldSlow;

	    public PathNavigator(Level world, boolean isJumpingUp, boolean isJumpingOneBlock, boolean shouldRender, boolean shouldSlow) {
	        this.world = world;
	        this.isJumpingUp = isJumpingUp;
	        this.isJumpingOneBlock = isJumpingOneBlock;
	        this.shouldRender = shouldRender;
	        this.shouldSlow = shouldSlow;
	    }

	    public boolean traversePath(BlockPos startPos, BlockPos endPos) {
	        int x = startPos.getX();
	        int y = startPos.getY();
	        int z = startPos.getZ();
	        int endX = endPos.getX();
	        int endY = endPos.getY();
	        int endZ = endPos.getZ();

	        BlockPos.MutableBlockPos currPos = new BlockPos.MutableBlockPos();
	        TungstenModRenderContainer.TEST.clear(); // Clear visual markers
	        renderBlock(endPos, Color.BLUE);
	        
	        boolean isOneBlockAway = DistanceCalculator.getHorizontalEuclideanDistance(startPos, endPos) <= 1;
	        
	        if (isOneBlockAway) {
	        	Direction dir = DirectionHelper.getHorizontalDirectionFromPos(startPos.getCenter(), endPos.getCenter());
	        	int offsetX = dir.getOffsetX();
	        	int offsetZ = dir.getOffsetZ();

	            currPos.set(x, y + 2, z);
	            
	            if (!processStep(currPos)) {
		            currPos.set(x + offsetX, y, z + offsetZ);
		            if (!processStep(currPos)) {
		                return false; // Path obstructed
		            }
	            }
	        	
	        }

	        while (x != endX || y != endY || z != endZ) {
	            if (TungstenModDataContainer.PATHFINDER.stop.get()) return false;

	            currPos.set(x, y, z);

	            if (!processStep(currPos)) {
	                return false; // Path obstructed
	            }

	            z = moveCoordinate(z, endZ);
	            

	            currPos.set(x, y, z);

	            if (!processStep(currPos)) {
	                return false; // Path obstructed
	            }
	            
	            x = moveCoordinate(x, endX);

	            currPos.set(x, y, z);

	            if (!processStep(currPos)) {
	                return false; // Path obstructed
	            }
	            
	            y = moveCoordinate(y, endY);
	            
	        }
	        renderBlock(endPos, Color.BLUE);
	        slowDownIfNeeded();
	        return true; // Successfully navigated the path
	    }

	    private boolean processStep(BlockPos.MutableBlockPos position) {
	        if (MovementHelper.isObscured(world, position, isJumpingUp, isJumpingOneBlock)) {
	            renderBlock(position, Color.RED);
	            slowDownIfNeeded();
	            return false;
	        } else {
	            renderBlock(position, Color.WHITE);
	            return true;
	        }
	    }

	    private void renderBlock(BlockPos position, Color color) {
	        if (shouldRender) {
	        	TungstenModRenderContainer.TEST.add(new Cuboid(new Vec3(position.getX(), position.getY(), position.getZ()), new Vec3(1.0D, 1.0D, 1.0D), color));
	        	TungstenModRenderContainer.TEST.add(new Cuboid(new Vec3(position.getX(), position.getY() + 1, position.getZ()), new Vec3(1.0D, 1.0D, 1.0D), color));
	        }
	    }

	    private void slowDownIfNeeded() {
	        if (shouldSlow) {
	            try {
	                Thread.sleep(450);
	            } catch (InterruptedException ignored) {}
	        }
	    }

	    private int moveCoordinate(int current, int target) {
	        if (current < target) return current + 1;
	        if (current > target) return current - 1;
	        return current;
	    }
	}
}
