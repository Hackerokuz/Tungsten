package kaptainwutax.tungsten.client.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.TungstenModRenderContainer;
import kaptainwutax.tungsten.client.helpers.BlockShapeChecker;
import kaptainwutax.tungsten.client.helpers.BlockStateChecker;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.client.helpers.movement.NeoMovementHelper;
import kaptainwutax.tungsten.client.helpers.movement.StreightMovementHelper;
import kaptainwutax.tungsten.client.path.calculators.ActionCosts;
import kaptainwutax.tungsten.world.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockNode {
	
	private final Player player;

	/**
	 * The position of this node
	 */
	public final int x;
	public final int y;
	public final int z;
	

	public Vec3 chachedPos = null;
	public Vec3 chachedWithOffsetPos = null;
	public BlockState chachedBlockState = null;
	public BlockPos chachedBlockPos = null;
	
	public Boolean isDoingLongJump = null;

	/**
	 * Cached, should always be equal to goal.heuristic(pos)
	 */
	public double estimatedCostToGoal;

	/**
	 * Total cost of getting from start to here Mutable and changed by PathFinder
	 */
	public double cost;

	/**
	 * Should always be equal to estimatedCosttoGoal + cost Mutable and changed by
	 * PathFinder
	 */
	public double combinedCost;

	/**
	 * In the graph search, what previous node contributed to the cost Mutable and
	 * changed by PathFinder
	 */
	public BlockNode previous;

	private boolean wasOnSlime;
	private boolean wasOnLadder;
	private boolean isDoingNeo = false;
	private Direction neoSide;
	private boolean isDoingCornerJump = false;
	private boolean isDoingJump = false;

	/**
	 * Where is this node in the array flattenization of the binary heap? Needed for
	 * decrease-key operations.
	 */
	public int heapPosition;

	public BlockNode(BlockPos pos, Goal goal, Player player, Level world) {
		this.player = player;
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
		this.wasOnSlime = world.getBlockState(pos.below()).getBlock() instanceof SlimeBlock;
		this.wasOnLadder = world.getBlockState(pos).getBlock() instanceof LadderBlock;
	}

	public BlockNode(int x, int y, int z, Goal goal, Player player) {
		this.player = player;
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
		this.wasOnSlime = player.level().getBlockState(new BlockPos(x, y - 1, z))
				.getBlock() instanceof SlimeBlock;
		this.wasOnLadder = player.level().getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
	}

	public BlockNode(int x, int y, int z, Goal goal, BlockNode parent, double cost, Player player) {
		this.player = player;
		this.previous = parent;
		this.wasOnSlime = player.level().getBlockState(new BlockPos(x, y - 1, z))
				.getBlock() instanceof SlimeBlock;
		this.wasOnLadder = player.level().getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
		this.cost = parent != null ? cost : ActionCosts.COST_INF;
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
	 * TODO: Possibly reimplement hashCode and equals. They are necessary for this
	 * class to function but they could be done better
	 *
	 * @return The hash code value for this {@link PathNode}
	 */
	@Override
	public int hashCode() {
		return (int) BetterBlockPos.longHash(x, y, z);
	}

	public Vec3 getPos() {
		return getPos(false, TungstenModDataContainer.world);
	}
	

	public Vec3 getPos(boolean shift) {
		return getPos(shift, TungstenModDataContainer.world);
	}
	
	public Vec3 getPos(Level world) {
		return getPos(false, world);
	}

	public Vec3 getPos(boolean shift, Level world) {
		if (!shift && chachedPos != null) return chachedPos;
		if (shift) {
			if (isDoingNeo && !(this.getBlockState(world).getBlock() instanceof LadderBlock)) {
				chachedWithOffsetPos = BlockPosShifter.shiftForStraightNeo(this, neoSide);
//				chachedWithOffsetPos = BlockPosShifter.getPosOnLadder(this, world);
				return chachedWithOffsetPos;
			}
			if (chachedWithOffsetPos != null) return chachedWithOffsetPos;
			if (isDoingNeo) {
				chachedWithOffsetPos = BlockPosShifter.shiftForStraightNeo(this, neoSide);
				return chachedWithOffsetPos;
			}
			if (BlockShapeChecker.getBlockHeight(this.getBlockPos().below(), world) > 1) {
				chachedWithOffsetPos = BlockPosShifter.getPosOnLadder(this, world);
				chachedWithOffsetPos = chachedWithOffsetPos.add(0, BlockShapeChecker.getBlockHeight(this.getBlockPos().below(), world)-1, 0);
				if (isDoingNeo) chachedWithOffsetPos = BlockPosShifter.shiftForStraightNeo(this, neoSide);
				return chachedWithOffsetPos;
			}
			if (BlockShapeChecker.getBlockHeight(this.getBlockPos(), world) > 0) {
				chachedWithOffsetPos = BlockPosShifter.getPosOnLadder(this, world);
				double blockVolume = BlockShapeChecker.getShapeVolume(this.getBlockPos(), world);
				chachedWithOffsetPos = chachedWithOffsetPos.add(0, BlockShapeChecker.getBlockHeight(this.getBlockPos(), world), 0);
				if (isDoingNeo) chachedWithOffsetPos = BlockPosShifter.shiftForStraightNeo(this, neoSide);
				return chachedWithOffsetPos;
			}
			chachedWithOffsetPos = BlockPosShifter.getPosOnLadder(this, world);
			if (isDoingNeo) chachedWithOffsetPos = BlockPosShifter.shiftForStraightNeo(this, neoSide);
			return chachedWithOffsetPos;
		}
		chachedPos = new Vec3(x, y, z);
		return chachedPos;
	}

	public boolean isDoingJump() {
		return isDoingJump;
	}

	public boolean isDoingLongJump(Level world) {
		if (isDoingLongJump != null) return isDoingLongJump;
		if (this.previous != null) {
			double distance = DistanceCalculator.getHorizontalEuclideanDistance(this.previous.getPos(world), this.getPos(world));
			if (distance >= 4 && distance < 6) {
				isDoingLongJump = true;
				return true;
			}
		}
		isDoingLongJump = false;
		return false;
	}

	public boolean isDoingNeo() {
		return this.isDoingNeo;
	}
	
	public Direction getNeoSide() {
		return this.neoSide;
	}

	public boolean isDoingCornerJump() {
		return this.isDoingCornerJump;
	}

	public BlockState getBlockState(Level world) {
		if (chachedBlockState != null) return chachedBlockState;
		chachedBlockState = world.getBlockState(getBlockPos());
		return chachedBlockState;
	}

	public BlockPos getBlockPos() {
		if (chachedBlockPos != null) return chachedBlockPos;
		chachedBlockPos = new BlockPos(x, y, z);
		return chachedBlockPos;
	}

	@Override
	public boolean equals(Object obj) {

		final BlockNode other = (BlockNode) obj;

		return x == other.x && y == other.y && z == other.z;
	}

	public List<BlockNode> getChildren(Level world, Goal goal, boolean generateDeep) {

		List<BlockNode> nodes = getNodesIn3DCircle(8, this, goal, generateDeep);
//		nodes.removeIf((child) -> {
//			return shouldRemoveNode(world, child);
//		});
		
		 List<BlockNode> filtered = nodes.parallelStream()
			        .filter(node -> !shouldRemoveNode(world, node))
			        .collect(Collectors.toList());
//		for (BlockNode n : nodes) {
//			RenderHelper.renderNode(n);
//		}
//		try {
//			Thread.sleep(500);
//		} catch (InterruptedException e) {
////            throw new RuntimeException(e);
//		}
//		RenderHelper.clearRenderers();
//		 for (BlockNode n : filtered) {
//			 RenderHelper.renderNode(n);
//		 }
//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
////            throw new RuntimeException(e);
//        }

        TungstenModRenderContainer.TEST.clear();

		return filtered;

	}

	public static boolean wasCleared(Level world, BlockPos start, BlockPos end) {
		return wasCleared(world, start, end, null, null);
	}

	public static boolean wasCleared(Level world, BlockPos start, BlockPos end, BlockNode startNode,
			BlockNode endNode) {

		TungstenModRenderContainer.TEST.clear();
		boolean shouldRender = false;
		boolean shouldSlow = false;
		

		boolean isStraightPossible = StreightMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
		
		if (isStraightPossible) return true;
		if (endNode == null) return false;
		
		// When running bot in normal environment instead of parkour you need to turn on Neo and Corner jump checks to avoid cases where it can get stuck
		boolean shouldCheckNeo = !start.closerToCenterThan(end.getCenter(), 1.2) && start.closerToCenterThan(end.getCenter(), 4.2);
		if (shouldCheckNeo) {
			Direction neoDirection = NeoMovementHelper.getNeoDirection(world, start, end, shouldRender, shouldSlow);
			if (neoDirection != null) {
				endNode.isDoingNeo = true;
				endNode.neoSide = neoDirection;
				endNode.isDoingCornerJump = false;
				return true;
			}
		}
		// FIXME: This causes a bug where bot thinks it can jump up when there is a block above it
//		boolean isCornerJumpPossible = CornerJumpMovementHelper.isPossible(world, start, end, shouldRender, shouldSlow);
//		if (isCornerJumpPossible) {
//			endNode.isDoingNeo = false;
//			endNode.isDoingCornerJump = true;
//			return true;
//		}

		return false;
	}

	private List<BlockNode> getNodesIn3DCircle(int d, BlockNode parent, Goal goal, boolean generateDeep) {
		ConcurrentLinkedQueue<BlockNode> nodes = new ConcurrentLinkedQueue<>();

		// TODO: Create a circle of nodes 1 block above current player pos and then move each node down until ground is found.

//	    double g = 32.656;
//	    double v_sprint = 5.8;
//
//	    double yMax = (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0)
//	        ? MovementHelper.getSlimeBounceHeight(parent.previous.y - parent.y) - 0.5
//	        : generateDeep ? 4 : 2;
//
//	    if (parent.wasOnSlime && parent.previous != null && parent.previous.y - parent.y < 0) {
//	    	TungstenModRenderContainer.BLOCK_PATH_RENDERER.add(new Cuboid(
//	                new Vec3d(parent.getBlockPos().getX(), parent.getBlockPos().getY(), parent.getBlockPos().getZ()),
//	                new Vec3d(0.2D, 0.2D, 0.2D), Color.GREEN));
//	        try {
//	            Thread.sleep(250); // Optional debug delay
//	        } catch (InterruptedException e) {
//	            e.printStackTrace();
//	        }
//	    }
//
//	    int distanceWanted = d;
//	    int finalYMax = (int) Math.ceil(yMax);
//
//        IntStream.range(generateDeep ? -64 : -4, finalYMax).parallel().forEach(py -> {
//            int localD;
//
//            if (py < -5) {
//                double t = Math.sqrt((2 * py * -1) / g);
//                localD = (int) Math.ceil(v_sprint * t);
//            } else {
//                localD = distanceWanted + 1;
//            }
//
//            // Center node
//			BlockNode nA = new BlockNode(this.x, this.y + py, this.z, goal, this, ActionCosts.WALK_ONE_BLOCK_COST, this.player);
//            nodes.add(nA);
//
//            for (int id = 0; id <= localD; id++) {
//                int px = id, pz = 0;
//                int dx = -1, dz = 1;
//                int n = id * 4;
//
//                for (int i = 0; i < n; i++) {
//                    if (px == id && dx > 0) dx = -1;
//                    else if (px == -id && dx < 0) dx = 1;
//
//                    if (pz == id && dz > 0) dz = -1;
//                    else if (pz == -id && dz < 0) dz = 1;
//
//                    px += dx;
//                    pz += dz;
//
//					double newNodeCost = ActionCosts.WALK_ONE_BLOCK_COST;
//
//					boolean isDoingJump = Math.abs(pz) > 1 || Math.abs(px) > 1;
//
//					if (isDoingJump) newNodeCost += 6.5;
//
//					if (py > 0) newNodeCost += 2;
//
//					BlockNode newNode = new BlockNode(this.x + px, this.y + py, this.z + pz, goal, this,
//							newNodeCost, this.player);
//					newNode.isDoingJump = isDoingJump;
//                    nodes.add(newNode);
//                }
//            }
//        });

		for (int pz = -6; pz < 6; pz++) {
			for (int px = -6; px < 6; px++) {

				boolean isDoingJump = Math.abs(pz) > 1 || Math.abs(px) > 1;

				BlockPos.MutableBlockPos currentNewNodeBlockPos = new BlockPos.MutableBlockPos(this.x + px, this.y + 1, this.z + pz);

				Level world = player.level();
				BlockState blockState = world.getBlockState(currentNewNodeBlockPos);
				boolean canStandOn = BlockShapeChecker.hasBiggerCollisionShapeThanAbove(world, currentNewNodeBlockPos.below()) || BlockStateChecker.isAnyWater(blockState);
//
				if (!canStandOn) {
					int idx = 0;
					while (!canStandOn && idx < 3) {
						currentNewNodeBlockPos.move(0, -1, 0);
						blockState = world.getBlockState(currentNewNodeBlockPos);
						canStandOn = BlockShapeChecker.hasBiggerCollisionShapeThanAbove(world, currentNewNodeBlockPos.below()) || BlockStateChecker.isAnyWater(blockState);
						idx++;
					}
				}
				if (canStandOn) {
					BlockNode newNode = new BlockNode(currentNewNodeBlockPos.getX(), currentNewNodeBlockPos.getY(), currentNewNodeBlockPos.getZ(), goal, this,
							this.cost, this.player);
					newNode.isDoingJump = isDoingJump;
                    nodes.add(newNode);
				}
			}
		}

		/*

		// ---

		// TRAVERSE_NORTH
		nodes.add(new BlockNode(this.x, this.y, this.z - 1, goal, this,
							1, this.player));

		// TRAVERSE_SOUTH
		nodes.add(new BlockNode(this.x, this.y, this.z + 1, goal, this,
				1, this.player));

		// TRAVERSE_EAST
		nodes.add(new BlockNode(this.x + 1, this.y, this.z, goal, this,
				1, this.player));

		// TRAVERSE_WEST
		nodes.add(new BlockNode(this.x - 1, this.y, this.z, goal, this,
				1, this.player));

		// ---

		// DESCEND_EAST
		nodes.add(new BlockNode(this.x + 1, this.y + 1, this.z, goal, this,
				1, this.player));

		// ASCEND_WEST
		nodes.add(new BlockNode(this.x - 1, this.y + 1, this.z, goal, this,
				1, this.player));

		// ASCEND_NORTH
		nodes.add(new BlockNode(this.x, this.y + 1, this.z - 1, goal, this,
				1, this.player));

		// ASCEND_SOUTH
		nodes.add(new BlockNode(this.x, this.y + 1, this.z + 1, goal, this,
				1, this.player));

		// ---

		// DESCEND_EAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z, goal, this,
				1, this.player));

		// DESCEND_WEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z, goal, this,
				1, this.player));

		// DESCEND_NORTH
		nodes.add(new BlockNode(this.x, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_SOUTH
		nodes.add(new BlockNode(this.x, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_EAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z, goal, this,
				1, this.player));

		// DESCEND_WEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z, goal, this,
				1, this.player));

		// ---

		// DESCEND_DIAGONAL_NORTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_NORTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_NORTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_NORTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_NORTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_NORTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y, this.z - 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// DESCEND_DIAGONAL_SOUTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y - 1, this.z + 1, goal, this,
				1, this.player));

		// ---

		// ASCEND_DIAGONAL_NORTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y + 1, this.z - 1, goal, this,
				1, this.player));

		// ASCEND_DIAGONAL_NORTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y + 1, this.z - 1, goal, this,
				1, this.player));

		// ASCEND_DIAGONAL_SOUTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y + 1, this.z + 1, goal, this,
				1, this.player));

		// ASCEND_DIAGONAL_SOUTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y + 1, this.z + 1, goal, this,
				1, this.player));

		// ---

		// DIAGONAL_NORTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y, this.z - 1, goal, this,
				1, this.player));

		// DIAGONAL_NORTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y, this.z - 1, goal, this,
				1, this.player));

		// DIAGONAL_SOUTHEAST
		nodes.add(new BlockNode(this.x + 1, this.y, this.z + 1, goal, this,
				1, this.player));

		// DIAGONAL_SOUTHWEST
		nodes.add(new BlockNode(this.x - 1, this.y, this.z + 1, goal, this,
				1, this.player));

		// ---

		// DOWNWARD
		nodes.add(new BlockNode(this.x, this.y - 1, this.z , goal, this,
				1, this.player));

		// UPWARD
		nodes.add(new BlockNode(this.x, this.y + 1, this.z, goal, this,
				1, this.player));

		*/

        return new ArrayList<>(nodes);
	}

	private boolean shouldRemoveNode(Level world, BlockNode child) {
		if (TungstenModDataContainer.PATHFINDER.stop.get())
			return true;

//		BlockState currentBlockState = world.getBlockState(getBlockPos());
//		BlockState currentBlockBelowState = world.getBlockState(getBlockPos().down());
//		BlockState childAboveState = world.getBlockState(child.getBlockPos().up());
		BlockState childState = world.getBlockState(child.getBlockPos());
//		BlockState childBelowState = world.getBlockState(child.getBlockPos().down());
//		Block currentBlock = currentBlockState.getBlock();
//		Block childBlock = childState.getBlock();
//		Block childBelowBlock = childBelowState.getBlock();
//		boolean isAboveChildSolid = BlockShapeChecker.getShapeVolume(child.getBlockPos().up(), world) > 0;
		boolean isAboveChildSolid2 = BlockShapeChecker.getShapeVolume(child.getBlockPos().above(2), world) > 0;
//
//		if (BlockStateChecker.isAnyWater(childState)) {
//
//			return MovementHelper.isObscured(world, child.getBlockPos(), false, false);
//		}
//
//
//		if (child.getPos().y > this.getPos().y && childBelowState.isAir())
//			return true;
//
		BlockState blockState = world.getBlockState(child.getBlockPos());
		boolean canStandOn = BlockShapeChecker.hasBiggerCollisionShapeThanAbove(world, child.getBlockPos().below()) || BlockStateChecker.isAnyWater(blockState);
//
		if (!canStandOn) return true;

//		// Collision shape and block exceptions
//		if (!BlockStateChecker.isBottomSlab(childState))
//		if (!canStandOn && (!(childBlock instanceof DaylightDetectorBlock) && !(childBlock instanceof CarpetBlock)
//				&& !(childBelowBlock instanceof SlabBlock) && !(childBelowBlock instanceof LanternBlock)
//				&& !(childBelowBlock == Blocks.SNOW))
////                || (childBelowBlock instanceof StairsBlock)
////                && !(childBelowBlock instanceof LanternBlock)
////                && !(childBelowBlock == Blocks.SNOW)
////                && getShapeVolume(childState.getCollisionShape(world, child.getBlockPos())) >= 1
//		) {
//			return true;
//		}
//
		if (isJumpImpossible(world, child))
			return true;
		
		// TODO: Fix bottom slab under fence thing
		if (!wasCleared(world, getBlockPos(), child.getBlockPos(), this, child)) {
			return true;
		}

		if (BlockStateChecker.isBottomSlab(childState) && isAboveChildSolid2)
			child.cost += 2;
		if (BlockStateChecker.isAnyWater(childState))
			child.cost += 2;
		
		return false;
	}
	
	/**
	 * Returns jump height.
	 * 
	 * @param from
	 * @param to
	 * @return positive is going up and negative is going down
	 */
	public double getJumpHeight(double from, double to) {
		
		double diff = to - from;
		
		// if `to` is higher then `from` return value should be positive
		if (to > from) {
			return diff > 0 ? diff : diff * -1;
		}
		return diff > 0 ? diff * -1 : diff;
	}

	private boolean isJumpImpossible(Level world, BlockNode child) {
		double heightDiff = getJumpHeight(this.getPos(true).y, child.getPos(true).y); // positive is going up and negative is going down
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(getPos(true, world), child.getPos(true, world));
		
		BlockState childBlockState = world.getBlockState(child.getBlockPos());
		Block childBlock = childBlockState.getBlock();
		BlockState belowChildBlockState = world.getBlockState(child.getBlockPos().below());
		BlockState currentBlockState = world.getBlockState(getBlockPos().below());
		Block belowChildBlock = belowChildBlockState.getBlock();
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(child.getBlockPos().below(), world);
		boolean isBlockBelowTall = closestBlockBelowHeight > 1.3;
		if (heightDiff > 1.4 && childBlock != Blocks.LADDER) return true;


//    	if (world.getBlockState(child.getBlockPos().down()).getBlock() instanceof TrapdoorBlock) {
//			System.out.println(!world.getBlockState(child.getBlockPos().down()).get(Properties.OPEN));
//    	}
		boolean isAboveChildSolid = BlockShapeChecker.getShapeVolume(child.getBlockPos().above(), world) > 0;
		boolean isAboveChildSolid2 = BlockShapeChecker.getShapeVolume(child.getBlockPos().above(2), world) > 0;
		boolean isAboveSolid2 = BlockShapeChecker.getShapeVolume(getBlockPos().above(2), world) > 0;
		if (isAboveSolid2 && distance > 3) {
			return true;
		}
		if (heightDiff > 0.6 && distance > 6.3 && childBlock != Blocks.LADDER) {
			return true;
		}

		VoxelShape blockShape = belowChildBlockState.getCollisionShape(world, child.getBlockPos().below());
		VoxelShape currentBlockShape = currentBlockState.getCollisionShape(world, getBlockPos().below());

		double childBlockHeight = BlockShapeChecker.getBlockHeight(blockShape);
		double currentBlockHeight = BlockShapeChecker.getBlockHeight(currentBlockShape);

		double blockHeightDiff = currentBlockHeight - childBlockHeight; // Negative values means currentBlockHeight is
																		// lower, and positive means currentBlockHeight
		if (player.getFoodData().getFoodLevel() < 6) {

			if (distance >= 4) return true;
		}

		if (BlockStateChecker.isBottomSlab(childBlockState) && isAboveChildSolid2  && distance > 2.3) {
			return true;
		}
		
		if (BlockStateChecker.isBottomSlab(currentBlockState) && childBlockHeight == 1 && heightDiff > 0.5) {
			return true;
		}
		

		if (BlockStateChecker.isAnyWater(currentBlockState)) {
			if (distance >= 2) return true;
			return false;
		}
		if (childBlockHeight == 1.5 && currentBlockHeight == 1.5 && heightDiff <= 1) {
			if (distance <= 4) return false;
		}

		if (isBlockBelowTall && heightDiff > 0.5) return true;
								
		// VoxelShape-based checks
		if (!Double.isInfinite(blockHeightDiff) && !Double.isNaN(blockHeightDiff)) {
			
			// Slab and ladder checks
			if (heightDiff <= 0 && (BlockStateChecker.isBottomSlab(belowChildBlockState)
					|| (!wasOnLadder && belowChildBlock instanceof LadderBlock)) && distance >= 4.5) {
				return true;
			}
			if (belowChildBlock instanceof SlabBlock && belowChildBlockState.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP
					&& !world.getBlockState(child.getBlockPos()).isAir()) {
				return true;
			}

			if (BlockStateChecker.isClosedBottomTrapdoor(belowChildBlockState)) {
				if (heightDiff <= 1 && distance <= 6.4) return false;
				if (heightDiff == 2 && distance <= 4.4) return false;
			}
			
			if (BlockStateChecker.isClosedBottomTrapdoor(currentBlockState)) {
				if (heightDiff <= 0 && distance <= 6.4) return false;
				if (heightDiff > 0 && BlockStateChecker.isTopSlab(belowChildBlockState)) return true;
			}
			
			if (blockHeightDiff != 0) {
				
				
				if (Math.abs(blockHeightDiff) > 0.5 && Math.abs(blockHeightDiff) <= 1.0) {
					if (heightDiff > 0 && (blockShape.min(Direction.Axis.Y) == 0.0 && currentBlockHeight <= 1.0))
						return true;
					if (heightDiff == 2 && distance <= 5.3)
						return false;
					if (heightDiff >= 0 && distance <= 5.3)
						return false;
				}
				
				if (Math.abs(blockHeightDiff) <= 0.5 && (blockShape.min(Direction.Axis.Y) == 0.0 && childBlockHeight == 1.0)) {
					if (heightDiff == 0 && distance <= 5.4)
						return false;
				}

				if (Math.abs(blockHeightDiff) <= 0.5 && (blockShape.min(Direction.Axis.Y) == 0.0 || childBlockHeight > 1.0)) {
					if (blockHeightDiff == 0.5 && heightDiff <= -1)
						return true;
					if (heightDiff == 0 && distance >= 4.4)
						return true;
					if (heightDiff <= 1 && distance <= 7.4)
						return false;
				}

				if (Math.abs(blockHeightDiff) >= 0.5 && (blockShape.min(Direction.Axis.Y) == 0.0 || childBlockHeight > 1.0))
					return true;
				
			}
		}

		if (!wasOnSlime || this.previous.y - this.y >= 0) {
			// Basic height and distance checks
			if (heightDiff >= 2)
				return true;
			if (heightDiff == 1 && distance > 5.24)
				return true;
			if (heightDiff == -1 && distance > 5.3)
				return true;
			if (heightDiff == -2 && distance > 6.2)
				return true;
			if (heightDiff == 1 && distance >= 4.5)
				return true;
			if ((heightDiff == 0) && distance >= 5.3)
				return true;
			if (heightDiff >= -3 && distance >= 6.3)
				return true;
			if (heightDiff < -2 && distance >= 6.3)
				return true;

			// Trapdoor checks
			if (heightDiff == 1 && BlockStateChecker.isOpenTrapdoor(belowChildBlockState) && distance > 5)
				return true;
			if (heightDiff <= -2 && BlockStateChecker.isOpenTrapdoor(belowChildBlockState) && distance > 6)
				return true;
		}

		// Large height drop
		if (heightDiff > -1 && distance >= 6)
			return true;

		// Bottom slab checks
		if (currentBlockHeight <= 0.5 && heightDiff > 0.5 && childBlockHeight > 0.5) {
			return true;
		}

		return false;
	}

}
