package kaptainwutax.tungsten.client.path;

import java.awt.*;
import java.util.*;
import java.util.List;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.TungstenModRenderContainer;
import kaptainwutax.tungsten.client.helpers.*;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.path.specialMoves.ClimbALadderMove;
import kaptainwutax.tungsten.client.path.specialMoves.CornerJump;
import kaptainwutax.tungsten.client.path.specialMoves.DivingMove;
import kaptainwutax.tungsten.client.path.specialMoves.EnterWaterAndSwimMove;
import kaptainwutax.tungsten.client.path.specialMoves.ExitWaterMove;
import kaptainwutax.tungsten.client.path.specialMoves.LongJump;
import kaptainwutax.tungsten.client.path.specialMoves.RunToNode;
import kaptainwutax.tungsten.client.path.specialMoves.SprintJumpMove;
import kaptainwutax.tungsten.client.path.specialMoves.SwimmingMove;
import kaptainwutax.tungsten.client.path.specialMoves.WalkToNode;
import kaptainwutax.tungsten.client.path.specialMoves.neo.NeoJump;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.render.Cuboid;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import kaptainwutax.tungsten.client.sim.AgentSimulator;
import kaptainwutax.tungsten.client.sim.AgentStatus;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class Node {

	public Node parent;
	public AgentEntity agent;
	public AgentStatus agentStatus;
	public AgentInput input;
	public double cost;
	public double estimatedCostToGoal = 0;
	public int heapPosition;
	public double combinedCost;
	public kaptainwutax.tungsten.client.render.Color color;
	public @NotNull AgentSimulator sim;

	public Node(Node parent, @NotNull AgentEntity agent, @NotNull kaptainwutax.tungsten.client.render.Color color, double pathCost) {
		this.parent = parent;
		this.agent = AgentEntity.cloneFrom(agent, agent.snapshot());
		this.agentStatus = this.agent.snapshot();
		this.color = color;
		this.cost = pathCost;
		this.combinedCost = 0;
		this.heapPosition = -1;
		if (parent == null) {
			this.sim = new AgentSimulator(this.agent);
		} else {
			this.sim = parent.sim.copy();
		}
	}

	public Node(@NotNull Node parent, @NotNull Level world, @NotNull AgentInput input, @NotNull kaptainwutax.tungsten.client.render.Color color, double pathCost) {
		this.parent = parent;
		this.sim = parent.sim.copy();
//		Node node = parent;
//		List<Node> path = new ArrayList<>();
//		while (node.parent != null) {
//			path.add(node);
//			node = node.parent;
//		}
//		path.add(node);
//		Collections.reverse(path);
//
//		AgentSimulator sim = AgentSimulator.fromLocalPlayer(TungstenModDataContainer.player, TungstenModDataContainer.player.getDeltaMovement());
//		TungstenModRenderContainer.ERROR.clear();
//        for (Node n : path) {
//            AgentStatus agentStatus = sim.simulate(n.input == null ? AgentInput.NONE : n.input);
//			if (!n.agentStatus.position.closerThan(agentStatus.position, 0.41)) {
//				n.agent = sim.getAgent();
//				n.agentStatus = agentStatus;
//				RenderHelper.renderNode(n);
//				TungstenModRenderContainer.ERROR.add(new Cuboid(agentStatus.position.subtract(0.01, 0, 0.01), new Vec3(0.02D, 0.06D, 0.02D), Color.WHITE));
//				try {
//					Thread.sleep(15);
//				} catch (InterruptedException e) {
////            throw new RuntimeException(e);
//				}
//			}
//        }
//
//		TungstenModRenderContainer.ERROR.clear();

        this.agentStatus = sim.simulate(input);
		this.agent = sim.getAgent();
		this.input = input;
		this.color = color;
		this.cost = pathCost;
		this.combinedCost = 0;
		this.heapPosition = -1;
	}
	
	 public boolean isOpen() {
	        return heapPosition != -1;
    }
	 
	 public int hashCode() {
		 return (int) hashCode(1, true);
	 }
	 
	 public int hashCode(int round, boolean shouldAddYaw) {
		 long result = 3241;
		 if (this.input != null) {
			 if (this.input.forward) result += "forward".hashCode();
			 if (this.input.back) result += "back".hashCode();
			 if (this.input.right) result += "right".hashCode();
			 if (this.input.left) result += "left".hashCode();
			 if (this.input.jump) result += "jump".hashCode();
			 if (this.input.sneak) result += "sneak".hashCode();
			 if (this.input.sprint) result += "sprint".hashCode();
//		    result = result + (Math.round(this.input.pitch));
		    if (shouldAddYaw) result = result + (Math.round(this.input.yaw / 45f));
//		    result = result + (Math.round(this.agent.velX*10));
//		    result = result + (Math.round(this.agent.velZ*10));
		 }
//	    if (round > 1) {
//		    result = 34L * result + Double.hashCode(roundToPrecision(this.agent.getPos().x, round));
//		    result = 87L * result + Double.hashCode(roundToPrecision(this.agent.getPos().y, round));
//		    result = 28L * result + Double.hashCode(roundToPrecision(this.agent.getPos().z, round));
//	    } else {
//		    result = 34L * result + Double.hashCode(this.agent.getPos().x);
//		    result = 87L * result + Double.hashCode(this.agent.getPos().y);
//		    result = 28L * result + Double.hashCode(this.agent.getPos().z);
//	    }
	    return (int) result;
    }

	public List<Node> getChildren(Level world, Vec3 target, BlockNode nextBlockNode) {
		return getChildren(world, target, nextBlockNode, false);
	}

	public List<Node> getChildren(Level world, Vec3 target, BlockNode nextBlockNode, boolean forceGenAllNodes) {
		if (!forceGenAllNodes && shouldSkipNodeGeneration(nextBlockNode)) {
	        return Collections.emptyList();
	    }

		double distance = DistanceCalculator.getEuclideanDistance(this.agent.position(), nextBlockNode.getPos(true));

	    List<Node> nodes = new ArrayList<>();


//	    if (!agent.isClimbing(world) && nextBlockNode.getBlockState(world).getBlock() instanceof LadderBlock) {
//	    	Node sprintJumpMove = JumpToLadderMove.generateMove(this, nextBlockNode);
//	    	boolean isSprintJumpMoveClose = sprintJumpMove.agent.getPos().distanceTo(nextBlockNode.getPos(true)) < 0.55;
//	    	if (isSprintJumpMoveClose) {
//		    	nodes.add(sprintJumpMove);
//	    		return nodes;
//	    	}
//	    }

	    if (DistanceCalculator.getHorizontalManhattanDistance(agent.position(), nextBlockNode.getPos(true)) <= 0.5 && nextBlockNode.getBlockState(world).getBlock() instanceof LadderBlock) {
	    	Node climbALadderMove = ClimbALadderMove.generateMove(this, nextBlockNode);
	    	boolean isClimbALadderMoveClose = Math.abs(climbALadderMove.agent.position().y - nextBlockNode.getPos(true).y) < 0.4;
	    	nodes.add(climbALadderMove);
	    	if (!forceGenAllNodes && isClimbALadderMoveClose) {
	    		return nodes;
	    	}
	    }


	    if (!agent.isInWater() && agent.onGround() && this.agent.canSprint()) {
	    	if (nextBlockNode.isDoingNeo()) {
	    		nodes.add(NeoJump.generateMove(this, nextBlockNode));
	    	}
		    if (nextBlockNode.isDoingLongJump(world) || world.getBlockState(nextBlockNode.getBlockPos()).getBlock() instanceof LadderBlock || nextBlockNode.previous != null && world.getBlockState(nextBlockNode.previous.getBlockPos()).getBlock() instanceof IceBlock) {
		    	nodes.add(LongJump.generateMove(this, nextBlockNode));
		    }
	    }

	    if (!agent.isInWater() && (BlockStateChecker.isAnyWater(nextBlockNode.getBlockState(world)) || BlockStateChecker.isAnyWater(nextBlockNode.previous.getBlockState(world)))) {
	    	Node enterWaterAndSwimMove = EnterWaterAndSwimMove.generateMove(this, nextBlockNode);
//	    	boolean isEnterWaterAndSwimMoveClose = enterWaterAndSwimMove.agent.getPos().distanceTo(nextBlockNode.getPos(true)) > 1.5;
	    	nodes.add(enterWaterAndSwimMove);
//	    	if (isEnterWaterAndSwimMoveClose) return nodes;
	    }

        if (agent.isInWater()) {
			if (!BlockStateChecker.isAnyWater(world.getBlockState(nextBlockNode.getBlockPos().above()))) nodes.add(SwimmingMove.generateMove(this, nextBlockNode));
            else  nodes.add(DivingMove.generateMove(this, nextBlockNode));
			if (!forceGenAllNodes) return nodes;
        }

		if (this.agent.canSprint()) {
			Node sprintJumpMove = SprintJumpMove.generateMove(this, nextBlockNode);
			boolean isSprintJumpMoveClose = sprintJumpMove.agent.position().distanceTo(nextBlockNode.getPos(true)) < 0.85;
//                if (!isSprintJumpMoveClose || forceGenAllNodes) {
				if (agent.onGround() || agent.isInWater() || agent.onClimbable()) {
					generateGroundOrWaterNodes(world, target, nextBlockNode, nodes);
				}  else {
					generateAirborneNodes(world, nextBlockNode, nodes);
				}
//
//                    sortNodesByYaw(nodes, target);
//                }
			nodes.add(sprintJumpMove);
			if (isSprintJumpMoveClose && !forceGenAllNodes) return nodes;
		} else {
			if (agent.onGround() || agent.isInWater() || agent.onClimbable()) {
				generateGroundOrWaterNodes(world, target, nextBlockNode, nodes);
			} else {
				generateAirborneNodes(world, nextBlockNode, nodes);
			}

//                sortNodesByYaw(nodes, target);
		}

	    if (agent.onGround()) {
	    	if (!world.getBlockState(agent.blockPosition().above(2)).isAir() && nextBlockNode.getPos(true).distanceTo(agent.position()) < 3) {
	//    		nodes.add(TurnACornerMove.generateMove(this, nextBlockNode, false));
	//    		nodes.add(TurnACornerMove.generateMove(this, nextBlockNode, true));
	    		nodes.add(CornerJump.generateMove(this, nextBlockNode, false));
	    		nodes.add(CornerJump.generateMove(this, nextBlockNode, true));
	    	}
	    }
	    if (agent.isInWater() && BlockShapeChecker.getShapeVolume(nextBlockNode.getBlockPos(), world) == 0 && !BlockStateChecker.isAnyWater(nextBlockNode.getBlockState(world))) {
	    	Node exitWaterMove = ExitWaterMove.generateMove(this, nextBlockNode);
//	    	boolean isExitWaterMoveClose = exitWaterMove.agent.getPos().distanceTo(nextBlockNode.getPos(true)) < 1.5;
	    	nodes.add(exitWaterMove);
//	    	if (isExitWaterMoveClose) return nodes;
	    }

	    if (!agent.isInWater() && !this.agent.canSprint() && distance > 12) {
	    	nodes.add(WalkToNode.generateMove(this, nextBlockNode));
	    }

	    if (!agent.isInWater() && this.agent.canSprint() && distance < 14) {
	    	nodes.add(RunToNode.generateMove(this, nextBlockNode));
	    }
    	if (agent.onGround() && !agent.onClimbable() && world.getBlockState(agent.blockPosition().below()).getBlock() instanceof LadderBlock) {
	    	nodes.add(LongJump.generateMove(this, nextBlockNode));
//    		nodes.add(CornerJump.generateMove(this, nextBlockNode));
    	}


		return  nodes.reversed();
	}

	
	private boolean shouldSkipNodeGeneration(BlockNode nextBlockNode) {
	    Node n = this.parent;
	    if (n != null && (n.agent.isInLava() || agent.isInLava() || (agent.fallDistance > 
	    this.agent.position().y - nextBlockNode.getBlockPos().getY()+2
	    && !agent.isInWater()
	    ))) {
	        return true;
	    }
	    return false;
	}


	private record ChildGenParams(boolean forward, boolean right, boolean left, boolean sneak,
	                              boolean sprint, boolean jump, float yaw) {}

	private void generateGroundOrWaterNodes(Level world, Vec3 target, BlockNode nextBlockNode, List<Node> nodes) {
	    boolean isDoingLongJump = nextBlockNode.isDoingLongJump(world) || nextBlockNode.isDoingNeo();
	    boolean isCloseToBlockNode = DistanceCalculator.getHorizontalEuclideanDistance(agent.position(), nextBlockNode.getPos(true)) < 1;
//	    boolean needToJump = agent.blockY < nextBlockNode.y;
    	BlockState state = world.getBlockState(nextBlockNode.getBlockPos());
	    
	    if (agent.onClimbable()
	    		&& state.getBlock() instanceof LadderBlock
	    		&& nextBlockNode.getBlockPos().getX() == agent.getBlockX()
	    		&& nextBlockNode.getBlockPos().getZ() == agent.getBlockZ()) {
	    	Direction dir = state.getValue(LadderBlock.FACING);
	    	double desiredYaw = DirectionHelper.calcYawFromVec3d(agent.position(), nextBlockNode.getPos(true).relative(dir.getOpposite(), 1)) /*+ MathHelper.roundToPrecision(Math.random(), 2) / 1000000*/;
	    	if (nextBlockNode.getBlockPos().getY() > agent.getBlockY()) {
				{ Node n = createNode(world, nextBlockNode, true, false, false, false, false, true, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode); if (n != null) nodes.add(n); }
				return;
	    	}
	    	if (nextBlockNode.getBlockPos().getY() < agent.getBlockY()) {
				{ Node n = createNode(world, nextBlockNode, true, false, false, false, false, false, (float) desiredYaw, isDoingLongJump, isCloseToBlockNode); if (n != null) nodes.add(n); }
				return;
	    	}
	    }
		// 1) Collect parameter combinations (cheap — no physics)
		List<ChildGenParams> params = new ArrayList<>();
	    float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.position(), nextBlockNode.getPos(true));
	    float a = 134.4f;
	    float fromYaw = -180.0f;
	    float toYaw = 180f;
//	    for (boolean forward : new boolean[]{true, false}) {
//	        for (boolean right : new boolean[]{true, false}) {
//	            for (boolean left : new boolean[]{true, false}) {
//	                for (boolean sneak : new boolean[]{false, true}) {
	                    for (float yaw = fromYaw; yaw < toYaw; yaw += 22.5f) {
	                        for (boolean sprint : new boolean[]{true, false}) {
	                        	if (!this.agent.canSprint() && sprint) continue;

	                            for (boolean jump : new boolean[]{true, false}) {
//	                            	if (!needToJump && jump) continue;
//	                            	if (isCloseToBlockNode && jump && nextBlockNode.getBlockPos().getY() == agent.blockY) continue;
									params.add(new ChildGenParams(true, false, false, false, sprint, jump, yaw));
	                            }
	                        }
	                    }
//	                }
//	            }
//	        }
//	    }

		// 2) Create nodes in parallel (expensive — Agent.tick per child)
		List<Node> created = params.parallelStream()
				.map(p -> createNode(world, nextBlockNode, p.forward, p.right, p.left, p.sneak, p.sprint, p.jump, p.yaw, isDoingLongJump, isCloseToBlockNode))
				.filter(Objects::nonNull)
				.toList();
		nodes.addAll(created);
	}

	private Node createNode(Level world, BlockNode nextBlockNode,
	                        boolean forward, boolean right, boolean left, boolean sneak, boolean sprint, boolean jump,
	                        float yaw, boolean isDoingLongJump, boolean isCloseToBlockNode) {
	    try {

            if (jump && sneak) return null;
	        Node newNode = new Node(this, world, new AgentInput(forward, false, right, left, jump, sneak, sprint, agent.getXRot(), yaw),
	                new kaptainwutax.tungsten.client.render.Color(sneak ? 220 : 0, 255, sneak ? 50 : 0), this.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	        if (newNode.agent.position().closerThan(nextBlockNode.getPos(true), 0.1, 0.4)) return null;
//	        double newNodeDistanceToBlockNode = Math.ceil(newNode.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);
//	        double parentNodeDistanceToBlockNode = Math.ceil(newNode.parent.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e5);
	        
//	        if (newNodeDistanceToBlockNode >= parentNodeDistanceToBlockNode) return;
	        
	        boolean isMoving = (forward || right || left);
	        if (newNode.agent.onClimbable()) jump = this.agent.blockPosition().getY() < nextBlockNode.getBlockPos().getY();

//	            if (!newNode.agent.touchingWater && !newNode.agent.onGround && sneak) return null;
//	            if (!newNode.agent.touchingWater && sneak && jump) return null;
//	            if (!newNode.agent.touchingWater && (sneak && sprint)) return null;
//	            if (!newNode.agent.touchingWater && sneak && (right || left) && forward) return null;
//	            if (!newNode.agent.touchingWater && sneak && Math.abs(newNode.parent.agent.yaw - newNode.agent.yaw) > 80) return null;
			if (newNode.agent.isInWater() && (sneak || jump) && newNode.agent.blockPosition().getY() == nextBlockNode.getBlockPos().getY()) return null;
			if (newNode.agent.isInWater() && jump && newNode.agent.blockPosition().getY() > nextBlockNode.getBlockPos().getY()) return null;
			if (!sneak) {
//	            	boolean isBelowClosedTrapDoor = BlockStateChecker.isClosedBottomTrapdoor(world.getBlockState(nextBlockNode.getBlockPos().down()));
//	        	    boolean shouldAllowWalkingOnLowerBlock = !world.getBlockState(agent.getBlockPos().up(2)).isAir() && nextBlockNode.getPos(true).distanceTo(agent.getPos()) < 3;
//	        	    double minY = isBelowClosedTrapDoor ? nextBlockNode.getPos(true).y - 1 : nextBlockNode.getBlockPos().getY() - (shouldAllowWalkingOnLowerBlock ? 1.3 : 0.3);
				for (int j = 0; j < ((!jump) && !newNode.agent.onClimbable() ? 2 : 10); j++) {
//		                if (newNode.agent.getPos().y <= minY && !newNode.agent.isClimbing(world) || !isMoving) break;
					if (!isMoving || newNode.agent.horizontalCollision) break;
					// TODO: Figure out how to check if near edge
//					AABB adjustedBox = newNode.agent.getBoundingBox().move(0, -0.5, 0).inflate(-0.001, 0, -0.001);
//					Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(world, adjustedBox));
//					if (blockCollisions.findAny().isEmpty() && isDoingLongJump) jump = true;
					newNode = new Node(newNode, world, new AgentInput(forward, false, right, left, jump, sneak, sprint, agent.getXRot(), yaw),
							jump ? new kaptainwutax.tungsten.client.render.Color(150, 55, 85) : new kaptainwutax.tungsten.client.render.Color(0, 255, sneak ? 50 : 0), this.cost);
					NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
					if (!isDoingLongJump && jump && j > 1) break;
					if (!newNode.agent.onGround() && !newNode.agent.onClimbable()) break;
				}
			}

			return newNode;
	    } catch (ConcurrentModificationException e) {
//	        try {
//	            Thread.sleep(2);
//	        } catch (InterruptedException ignored) {}
			return null;
	    }
    }

	private void generateAirborneNodes(Level world, BlockNode nextBlockNode, List<Node> nodes) {
	    try {
//	        for (float yaw = agent.yaw - 45; yaw < 180.0f; yaw += 22.5 + Math.random()) {
//	            for (boolean forward : new boolean[]{true, false}) {
//	                for (boolean right : new boolean[]{false, true}) {
	                    createAirborneNodes(world, nextBlockNode, nodes, true, false, agent.getXRot());
//	                }
//	            }
//	        }
	    } catch (ConcurrentModificationException e) {
//	        try {
//	            Thread.sleep(2);
//	        } catch (InterruptedException ignored) {}
	    }
	}

	private void createAirborneNodes(Level world, BlockNode nextBlockNode, List<Node> nodes, boolean forward, boolean right, float yaw) {
	    Node newNode = new Node(this, world, new AgentInput(forward, false, right, false, false, false, true, agent.getXRot(), yaw),
	            new kaptainwutax.tungsten.client.render.Color(0, 255, 255), this.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));

        
//        if (newNode.agent.getPos().isWithinRangeOf(nextBlockNode.getPos(true), 0.9, 0.4)) return;
//        double newNodeDistanceToBlockNode = Math.ceil(newNode.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e4);
//        double parentNodeDistanceToBlockNode = Math.ceil(newNode.parent.agent.getPos().distanceTo(nextBlockNode.getPos(true)) * 1e4);

//        if (newNodeDistanceToBlockNode >= parentNodeDistanceToBlockNode) return;
	    int i = 0;
	    boolean isBelowClosedTrapDoor = BlockStateChecker.isClosedBottomTrapdoor(world.getBlockState(nextBlockNode.getBlockPos().below()));
	    boolean shouldAllowWalkingOnLowerBlock = !world.getBlockState(agent.blockPosition().above(2)).isAir() && nextBlockNode.getPos(true).distanceTo(agent.position()) < 3;
	    double minY = isBelowClosedTrapDoor ? nextBlockNode.getPos(true).y - 1 : nextBlockNode.getBlockPos().getY() - (shouldAllowWalkingOnLowerBlock ? 1.4 : 0.4);
	    while (!newNode.agent.onGround() && !newNode.agent.onClimbable()
				&& newNode.agent.position().y() >= minY
	    		&& (TungstenModDataContainer.ignoreFallDamage || DistanceCalculator.getJumpHeight(agent.position().y(), newNode.agent.position().y()) > -3)) {
	    	if (i > 60) break;
	    	i++;
	        newNode = new Node(newNode, world, new AgentInput(forward, false, right, false, false, false, true, agent.getXRot(), yaw),
	                new kaptainwutax.tungsten.client.render.Color(0, 255, 255), this.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	    }
        newNode = new Node(newNode, world, new AgentInput(forward, false, right, false, false, false, true, agent.getXRot(), yaw),
                new kaptainwutax.tungsten.client.render.Color(0, 255, 255), this.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));

//        if (newNode.agent.getPos().distanceTo(this.agent.getPos()) < 1.05) return;
	    nodes.add(newNode);
	}
	
	private void sortNodesByYaw(List<Node> nodes, Vec3 target) {
	    double desiredYaw = DirectionHelper.calcYawFromVec3d(agent.position(), target);
	    nodes.sort((n1, n2) -> {
	        double diff1 = Math.abs(n1.agent.getXRot() - desiredYaw);
	        double diff2 = Math.abs(n2.agent.getXRot() - desiredYaw);
	        return Double.compare(diff1, diff2);
	    });
	}
}
