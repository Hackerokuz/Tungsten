package kaptainwutax.tungsten.client.helpers;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathFinder;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class NodeCostCalculator {


    public static void updateNode(Level world, @NonNull Node child, Vec3 target) {
        updateNode(world, child, target, TungstenModDataContainer.PATHFINDER.TARGET);
    }

    public static void updateNode(Level world, @NonNull Node child, Vec3 target, Vec3 realTarget) {
        Vec3 childPos = child.agent.getPos();

        double collisionScore = NodeCostCalculator.calculateNodeCost(child.agent);
        double tentativeCost = child.cost;
        if (world.getBlockState(child.agent.blockPosition()).getBlock() instanceof WebBlock) {
            collisionScore += 20000;
        }

        double estimatedCostToGoal = computeHeuristic(childPos, child.agent.onGround(), target, realTarget) * 6;

        tentativeCost += collisionScore;

        child.cost = tentativeCost;
        child.estimatedCostToGoal = estimatedCostToGoal;
        child.combinedCost = tentativeCost + estimatedCostToGoal;
    }

    public static double calculateNodeCost(AgentEntity agent) {

        Level world = TungstenModDataContainer.world;

        double addNodeCost = 1.08; // Magic number makes pathfinder go FAST. DO NOT TOUCH

        if (agent.isInWater()) {
            addNodeCost += 0.02;
            if (!agent.isSwimming()) {
                addNodeCost += 0.05;
            }
            if (agent.isUnderWater()) {
                addNodeCost += 0.4;
            }
        }

        if (agent.isDamaged) {
            addNodeCost += 8;
        }
        if (agent.onGround()) {
            addNodeCost += 2.4;
        }

        if (agent.onClimbable()) addNodeCost += 12.8;

        if (Math.abs(agent.velX) < 0.01 && Math.abs(agent.velY) < 0.01 && Math.abs(agent.velZ) < 0.01) {
            addNodeCost += 15;
        }
        addNodeCost += Math.abs(agent.velX) * -1;
        addNodeCost += Math.abs(agent.velY) * -1;
        addNodeCost += Math.abs(agent.velZ) * -1;
        if (agent.horizontalCollision) {
            addNodeCost += 20.0004;
        }

        if (agent.isInLava()) addNodeCost += 2e6;

        if (agent.snapshot().sneaking) {
            addNodeCost += 3;
        }


        return addNodeCost; //+ Math.abs(agent.yaw- this.agent.yaw) * 5;
    }

    public static double computeHeuristic(Vec3 position, boolean onGround, Vec3 target, Vec3 realTarget) {
        double xzMultiplier = 1;
        double dx = (target.x - position.x)*xzMultiplier;
        double dy = (target.y - position.y);
        double dz = (target.z - position.z)*xzMultiplier;


		double realTargetDist = DistanceCalculator.getEuclideanDistance(position, realTarget);
        double percentComplete = PathFinder.blockPath.map(blockNodes -> ((double) PathFinder.NEXT_CLOSEST_BLOCKNODE_IDX.get() / blockNodes.size()) * 100).orElse(0.0);
        return
                (Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.82 + (100 - percentComplete)
	    		+ (realTargetDist) * 1.005
                );
    }


}
