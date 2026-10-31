package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.BlockStateChecker;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathFinder;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.world.level.Level;

public class SprintJumpMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;
		int NEXT_CLOSEST_BLOCKNODE_IDX = PathFinder.NEXT_CLOSEST_BLOCKNODE_IDX.get();
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
		double closestDistance = Double.MAX_VALUE;
	    Node newNode = new Node(parent, world, new AgentInput(true, false, false, false, false, false, true, parent.agent.getXRot(), desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.accumulateCost(world, newNode);
		int limit = 0;
		Node lastHigheastNodeSinceGround = null;
        // Run forward to the node
//		TungstenMod.RENDERERS.clear();
		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
		float pitch = (float) (0.6 - Math.random());
		while (limit < 500 && newNode.agent.getY() >= world.getMinY() && !newNode.agent.horizontalCollision && !newNode.agent.isInLava()) {
			if (newNode.agent.isInWater()) {
				newNode.cost += 0.002;
				break;
			}
//        	RenderHelper.renderNode(newNode);
//        	try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//        	if (newNode.agent.blockY < nextBlockNode.getBlockPos().getY()-1) break;

			if (newNode.agent.onGround() || lastHigheastNodeSinceGround == null || lastHigheastNodeSinceGround.agent.getPos().y < newNode.agent.getPos().y) {
				lastHigheastNodeSinceGround = newNode;
			} else if ((!TungstenModDataContainer.ignoreFallDamage
					&& !BlockStateChecker.isAnyWater(world.getBlockState(newNode.agent.getLandingPos(world))))
					&& DistanceCalculator.getJumpHeight(lastHigheastNodeSinceGround.agent.getPos().y, newNode.agent.getPos().y) < -2.7
					|| !TungstenModDataContainer.ignoreFallDamage && newNode.agent.hurtMarked) {
				newNode = new Node(newNode, world, new AgentInput(true, false, false, false, true, false, true, pitch, desiredYaw),
	            		new Color(24, 17, 222), newNode.cost * 2e5);
				NodeCostCalculator.accumulateCost(world, newNode);
				break;
			}
			
        	limit++;
    		distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, newNode.agent.onGround(), false, true, pitch, desiredYaw),
            		new Color(147, 17, 222), newNode.cost);
			NodeCostCalculator.accumulateCost(world, newNode);
//			newNode.combinedCost = newNode.combinedCost * 0.001;
        	if (closestDistance > distance) {
				desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
        		closestDistance = distance;
        	} else {
//				if (PathFinder.blockPath.isPresent()) {
//					if (PathFinder.blockPath.get().size()-1 == NEXT_CLOSEST_BLOCKNODE_IDX) {
//						break;
//					}
//					NEXT_CLOSEST_BLOCKNODE_IDX += 1;
//					nextBlockNode = PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX);
//					closestDistance = Double.MAX_VALUE;
//					desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
//				} else {
					break;
//				}
        	}
            
        }

//		while (!newNode.agent.onGround() && newNode.parent != null) {
//			newNode = newNode.parent;
//		}

        return newNode;
	}

}
