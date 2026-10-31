package kaptainwutax.tungsten.client.helpers;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathFinder;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class NodeCostCalculator {


    public static void updateNode(Level world, @NonNull Node child) {
        updateNode(world, child, TungstenModDataContainer.PATHFINDER.TARGET);
    }

    public static void updateNode(Level world, @NonNull Node child, Vec3 realTarget) {
        Vec3 childPos = child.agent.getPos();

        double collisionScore = NodeCostCalculator.calculateNodeCost(child.agent);
        double tentativeCost = child.cost;
        if (world.getBlockState(child.agent.blockPosition()).getBlock() instanceof WebBlock) {
            collisionScore += 20000;
        }
        if (world.getBlockState(child.agent.blockPosition()).getBlock() instanceof SweetBerryBushBlock) {
            collisionScore += 20000;
        }

        double estimatedCostToGoal = computeHeuristic(childPos, realTarget) * 6;

        tentativeCost += collisionScore;

        child.cost = tentativeCost;
        child.estimatedCostToGoal = estimatedCostToGoal;
        child.combinedCost = tentativeCost + estimatedCostToGoal;
    }

    public static double calculateNodeCost(AgentEntity agent) {

        Level world = TungstenModDataContainer.world;

        double addNodeCost = 1.08; // Magic number makes pathfinder go FAST. DO NOT TOUCH

//        if (agent.isInWater()) {
//            addNodeCost += 0.02;
//            if (!agent.isSwimming()) {
//                addNodeCost += 0.05;
//            }
//            if (agent.isUnderWater()) {
//                addNodeCost += 0.4;
//            }
//        }

        if (agent.fallDistance > 0.3) {
            addNodeCost += 200.5;
        }
        if (agent.hurtMarked || agent.invulnerableTime > 0) {
            addNodeCost += 200.5;
        }
        if (agent.onGround()) {
            addNodeCost += 10.4;
        }

        if (agent.onClimbable()) addNodeCost += 12.8;

        addNodeCost -= agent.getDeltaMovement().lengthSqr();
//        addNodeCost += Math.abs(agent.getDeltaMovement().x()) * -1;
//        addNodeCost += Math.abs(agent.getDeltaMovement().y()) * -1;
//        addNodeCost += Math.abs(agent.getDeltaMovement().z()) * -1;
        if (agent.horizontalCollision) {
            addNodeCost += 20.0004;
        }

        if (agent.isInLava()) addNodeCost += 2e6;

        if (agent.snapshot().sneaking) {
            addNodeCost += 3;
        }


        return addNodeCost; //+ Math.abs(agent.yaw- this.agent.yaw) * 5;
    }

    public static double computeHeuristic(Vec3 position, Vec3 realTarget) {
        double xzMultiplier = 1;

        if (PathFinder.blockPath.isEmpty()) {
            double realTargetDist = DistanceCalculator.getEuclideanDistance(position, realTarget);
            return (realTargetDist) * 40;
        }

        Vec3 waypoint = PathFinder.blockPath.get().get(PathFinder.NEXT_CLOSEST_BLOCKNODE_IDX.get()).getPos(true);
        Vec3 previousWaypoint = PathFinder.blockPath.get().get(PathFinder.NEXT_CLOSEST_BLOCKNODE_IDX.get()-1).getPos(true);
        double dx = (waypoint.x - position.x)*xzMultiplier;
        double dy = (waypoint.y - position.y);
        double dz = (waypoint.z - position.z)*xzMultiplier;
        double distanceToWaypoint = Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.82;

        double distanceToLine = DistanceCalculator.distanceFromPointToLine(previousWaypoint, waypoint, position);

		double realTargetDist = DistanceCalculator.getEuclideanDistance(position, realTarget);
//        double percentComplete = PathFinder.blockPath.map(blockNodes -> ((double) PathFinder.NEXT_CLOSEST_BLOCKNODE_IDX.get() / blockNodes.size()) * 100).orElse(0.0);

        return
                (distanceToLine * 2
//                + (100 - percentComplete)
//                + distanceToWaypoint
	    		+ (realTargetDist) * 4
                );
    }

    /** Cost of the move that produced this node. Mutates cost; safe to call once per node. */
    public static double accumulateCost(Level world, Node child) {
        double collisionScore = calculateNodeCost(child.agent);
        if (world.getBlockState(child.agent.blockPosition()).getBlock() instanceof WebBlock) collisionScore += 20000;
        if (world.getBlockState(child.agent.blockPosition()).getBlock() instanceof SweetBerryBushBlock) collisionScore += 20000;
        child.cost = child.inheritedCost + collisionScore;
        // inheritedCost is immutable
        return child.cost;
    }
    /** Heuristic only — no cost mutation, so it is idempotent and can be called at insertion. */
    public static void applyHeuristic(Level world, Node child, Vec3 realTarget) {
        child.estimatedCostToGoal = computeHeuristic(child.agent.getPos(), realTarget) * 6;
        child.combinedCost = child.cost + child.estimatedCostToGoal;
    }

}
