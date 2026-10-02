package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.client.helpers.BlockStateChecker;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathInput;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public class SprintJumpMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		WorldView world = TungstenModDataContainer.world;
		Agent agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
		double closestDistance = Double.MAX_VALUE;
	    Node newNode = new Node(parent, world, new PathInput(false, false, false, false, false, false, false, parent.agent.pitch, desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		int limit = 0;
		Node lastHigheastNodeSinceGround = null;
        // Run forward to the node
//		TungstenMod.RENDERERS.clear();
		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
		if (distance < 0.8) return newNode;
		float pitch = (float) (0.6 - Math.random());
		while (distance > 0.95 && limit < 500 && newNode.agent.posY >= world.getBottomY() && !newNode.agent.horizontalCollision && !newNode.agent.isInLava() || (distance <= 0.3 && !newNode.agent.onGround) && limit < 500) {
			if (newNode.agent.touchingWater) {
				newNode.cost += 0.2;
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

			if (newNode.agent.onGround || lastHigheastNodeSinceGround == null || lastHigheastNodeSinceGround.agent.getPos().y < newNode.agent.getPos().y) {
				lastHigheastNodeSinceGround = newNode;
			} else if ((!TungstenModDataContainer.ignoreFallDamage
					&& !BlockStateChecker.isAnyWater(world.getBlockState(newNode.agent.getLandingPos(world))))
					&& DistanceCalculator.getJumpHeight(lastHigheastNodeSinceGround.agent.getPos().y, newNode.agent.getPos().y) < -2.7
					|| !TungstenModDataContainer.ignoreFallDamage && newNode.agent.isDamaged) {
				newNode = new Node(newNode, world, new PathInput(true, false, false, false, true, false, true, pitch, desiredYaw),
	            		new Color(24, 17, 222), newNode.cost * 2e5);
				break;
			}
			
        	limit++;
    		distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
            newNode = new Node(newNode, world, new PathInput(true, false, false, false, newNode.agent.onGround, false, true, pitch, desiredYaw),
            		new Color(147, 17, 222), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
			newNode.combinedCost = newNode.combinedCost * 0.001;
        	if (closestDistance > distance) {
        		closestDistance = distance;
        	} else {
        		break;
        	}
            
        }

		while (!newNode.agent.onGround && newNode.parent != null) {
			newNode = newNode.parent;
		}
            
        return newNode;
	}

}
