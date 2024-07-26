package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.List;

import baritone.api.pathing.movement.ActionCosts;
import baritone.api.utils.BetterBlockPos;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.WorldView;

public class BlockNode {
	
	/**
     * The position of this node
     */
    public final int x;
    public final int y;
    public final int z;
    
    /**
     * Cached, should always be equal to goal.heuristic(pos)
     */
    public double estimatedCostToGoal;

    /**
     * Total cost of getting from start to here
     * Mutable and changed by PathFinder
     */
    public double cost;

    /**
     * Should always be equal to estimatedCosttoGoal + cost
     * Mutable and changed by PathFinder
     */
    public double combinedCost;

    /**
     * In the graph search, what previous node contributed to the cost
     * Mutable and changed by PathFinder
     */
    public BlockNode previous;

    /**
     * Where is this node in the array flattenization of the binary heap? Needed for decrease-key operations.
     */
    public int heapPosition;
    
    public BlockNode(BlockPos pos, Goal goal) {
    	this.previous = null;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(pos.getX(), pos.getY(), pos.getZ());
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }
    
    public BlockNode(int x, int y, int z, Goal goal) {
        this.previous = null;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost) {
        this.previous = parent;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        if (Double.isNaN(estimatedCostToGoal)) {
            throw new IllegalStateException(goal + " calculated implausible heuristic");
        }
        this.heapPosition = -1;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    /**
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this class to function but they could be done better
     *
     * @return The hash code value for this {@link PathNode}
     */
    @Override
    public int hashCode() {
        return (int) BetterBlockPos.longHash(x, y, z);
    }
    
    public Vec3d getPos() {
    	return new Vec3d(x, y, z);
    }
    
    public BlockPos getBlockPos() {
    	return new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        // GOTTA GO FAST
        // ALL THESE CHECKS ARE FOR PEOPLE WHO WANT SLOW CODE
        // SKRT SKRT
        //if (obj == null || !(obj instanceof PathNode)) {
        //    return false;
        //}

        final BlockNode other = (BlockNode) obj;
        //return Objects.equals(this.pos, other.pos) && Objects.equals(this.goal, other.goal);

        return x == other.x && y == other.y && z == other.z;
    }
    
    public List<BlockNode> getChildren(WorldView world, Goal goal) {
		BlockNode n = this.previous;
		
		List<BlockNode> nodes = getNodesIn2DCircule(6, this, goal);
		nodes.removeIf((child) -> {
			if(world.getBlockState(child.getBlockPos().down()).isAir()) return true;
			if(world.getBlockState(child.getBlockPos()).isOf(Blocks.LAVA)) return true;
//			if(world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock) return true;
			if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof SlabBlock
					&& world.getBlockState(child.getBlockPos().down()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM) return true;
			if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof LilyPadBlock) return true;
			if(world.getBlockState(child.getBlockPos().down()).getBlock() instanceof CarpetBlock) return true;
			if(child.y - y > 1) return true;
			
			VoxelShape blockShape = world.getBlockState(child.getBlockPos().down()).getCollisionShape(world, child.getBlockPos().down());
			VoxelShape previousBlockShape = world.getBlockState(getBlockPos().down()).getCollisionShape(world, getBlockPos().down());
			
			if (!blockShape.isEmpty() && 
					blockShape.getBoundingBox().maxY > 1.3
					&& !previousBlockShape.isEmpty()
					&& previousBlockShape.getBoundingBox().maxY < 1.3
					&& previousBlockShape.getBoundingBox().maxY > 0.5
					&& y - child.y < 1
					&& getPos().distanceTo(child.getPos()) > 4) return true;
			
			
			if (getPos().distanceTo(child.getPos()) > 5 && y - child.y < 3) return true;
			if (!wasCleared(world, getBlockPos(), child.getBlockPos())) {
	            return true;
	        }
			if(world.getBlockState(child.getBlockPos()).getBlock() instanceof SlabBlock
					&& world.getBlockState(child.getBlockPos()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM
					&& y - child.y < 0
					&& !(world.getBlockState(getBlockPos().down()).getBlock() instanceof SlabBlock
					&& world.getBlockState(getBlockPos().down()).get(Properties.SLAB_TYPE) == SlabType.BOTTOM)
					) return true;
			
			if(world.getBlockState(child.getBlockPos()).getBlock() instanceof StairsBlock) return true;
			
			return !hasBiggerCollisionShapeThanAbove(world, child.getBlockPos().down());
		});
		
		return nodes;
		
    }
    
    public boolean wasCleared(WorldView world, BlockPos start, BlockPos end) { 
		int x1 = start.getX();
	    int y1 = start.getY();
	    int z1 = start.getZ();
	
	    int x2 = end.getX();
	    int y2 = end.getY();
	    int z2 = end.getZ();
	    
	    int thickness = 1;
		TungstenMod.TEST.clear();
		TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.GREEN));
		TungstenMod.TEST.add(new Cuboid(new Vec3d(x2, y2, z2), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
	
	    // Swap coordinates if necessary to make sure x1 <= x2, y1 <= y2, and z1 <= z2
//	    if (x1 > x2) {
//	        int temp = x1;
//	        x1 = x2;
//	        x2 = temp;
//	    }
//	    if (y1 > y2) {
//	        int temp = y1;
//	        y1 = y2;
//	        y2 = temp;
//	    }
//	    if (z1 > z2) {
//	        int temp = z1;
//	        z1 = z2;
//	        z2 = temp;
//	    }
	
	    int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);

        int sx = x1 < x2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        BlockPos.Mutable currPos = new BlockPos.Mutable();
        int err = dx - dz;
        while (true) {

        	for (int y = 1; y < 3; y++) {
        		
//        		for (int i = -thickness / 2; i <= thickness / 2; i++) {
//        			int ax = x1;
//        			int az = z1;
//                    if (dx > dz) {
//                        az += i;
//                    } else {
//                        ax += i;
//                    }

        		currPos.set(x1, y1 + y, z1);
	    			if (
	    					world.getBlockState(currPos).isFullCube(world, currPos) || 
	    					hasBiggerCollisionShapeThanAbove(world, currPos) 
	    					&& !(world.getBlockState(currPos).getBlock() instanceof SlabBlock)
	    					) {
	    				TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1 + y, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));
//	        			try {
//	        				Thread.sleep(50);
//	        			} catch (InterruptedException ignored) {}
//	    			if (world.isAir(new BlockPos(ax, y1 + y, az))) {
	        			
	    				return false;
	    			} else {
	    				TungstenMod.TEST.add(new Cuboid(new Vec3d(x1, y1 + y, z1), new Vec3d(1.0D, 1.0D, 1.0D), Color.WHITE));
	    			}
//                }
			}

            if (x1 == x2 && z1 == z2) {
                break;
            }

            int e2 = 2 * err;

            if (e2 > -dz) {
                err -= dz;
                x1 += sx;
            }

            if (e2 < dx) {
                err += dx;
                z1 += sz;
            }
        }

		return true;
	}
    
    public static boolean hasBiggerCollisionShapeThanAbove(WorldView world, BlockPos pos) {
        // Get the block states of the block at pos and the two blocks above it
        BlockState blockState = world.getBlockState(pos);
        BlockState aboveBlockState1 = world.getBlockState(pos.up(1));
        BlockState aboveBlockState2 = world.getBlockState(pos.up(2));

        // Get the collision shapes of these blocks
        VoxelShape blockShape = blockState.getCollisionShape(world, pos);
        VoxelShape aboveBlockShape1 = aboveBlockState1.getCollisionShape(world, pos.up(1));
        VoxelShape aboveBlockShape2 = aboveBlockState2.getCollisionShape(world, pos.up(2));
        
        // Calculate the volume of the collision shapes
        double blockVolume = getShapeVolume(blockShape);
        double aboveBlockVolume1 = getShapeVolume(aboveBlockShape1);
        double aboveBlockVolume2 = getShapeVolume(aboveBlockShape2);

        // Compare the volumes
        return blockVolume > aboveBlockVolume1 && blockVolume > aboveBlockVolume2;
    }
    
    private static double getShapeVolume(VoxelShape shape) {
        // Iterate over the shape's bounding boxes and sum their volumes
        return shape.getBoundingBoxes().stream()
                .mapToDouble(box -> (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ))
                .sum();
    }
    
    private List<BlockNode> getNodesIn2DCircule(int d, BlockNode parent, Goal goal) {
    	List<BlockNode> nodes = new ArrayList<>();
    	for (int id = 1; id <= d; id++) {
	        int px = id;
	        int pz = 0;
	        int dx = -1, dz = 1;
	        int n = id * 4;
	        for( int i = 0; i < n; i++ ) {
	            if( px == id && dx > 0 ) dx = -1;
	            else if( px == -id && dx < 0 ) dx = 1;
	            if( pz == id && dz > 0 ) dz = -1;
	            else if( pz == -id && dz < 0 ) dz = 1;
	            px += dx;
	            pz += dz;
	            for( int py = -3; py < 3; py++ ) {
		            BlockNode newNode = new BlockNode(this.x + px + (py < 0 ? py : 0), this.y + py, this.z + pz + (py < 0 ? py : 0), goal, this, ActionCosts.WALK_ONE_BLOCK_COST);
		            nodes.add(newNode);
	            }
	        }
		}
        
        return nodes;
    }

}
