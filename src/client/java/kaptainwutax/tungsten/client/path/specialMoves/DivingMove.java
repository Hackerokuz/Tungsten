package kaptainwutax.tungsten.client.path.specialMoves;


import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathInput;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.world.WorldView;

public class DivingMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		WorldView world = TungstenModDataContainer.world;
		Agent agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		float desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, parent.input == null ? new PathInput(false, false, false, false, false, false, false, desiredPitch, desiredYaw) : parent.input,
	    				new Color(0, 0, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		int limit = 0;
		double heightDiff = DistanceCalculator.getJumpHeight(newNode.agent.getPos().y, nextBlockNode.getPos(true).y);
		if (distance < 2.8 && distance > 0.8) {
            limit = 0;
			while (heightDiff > 0.8 && limit < 20 && newNode.agent.touchingWater) {
	        	RenderHelper.renderNode(newNode);
	        	try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				heightDiff = DistanceCalculator.getJumpHeight(newNode.agent.getPos().y, nextBlockNode.getPos(true).y);
	    		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
				desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
	            newNode = new Node(newNode, world, new PathInput(true, false, false, false, false, false, true, desiredPitch, desiredYaw),
	            		new Color(0, 0, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	            limit++;
			}
			return newNode;
		}
		if (distance < 0.8) {
			if (heightDiff > 0) {
	            limit = 0;
				while (heightDiff > 0.8 && limit < 80 && newNode.agent.touchingWater && !newNode.agent.verticalCollision) {
		        	RenderHelper.renderNode(newNode);
		        	try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					heightDiff = DistanceCalculator.getJumpHeight(newNode.agent.getPos().y, nextBlockNode.getPos(true).y);
		            newNode = new Node(newNode, world, new PathInput(false, false, false, false, true, false, false, desiredPitch, desiredYaw),
		            		new Color(0, 0, 150), newNode.cost);
					NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		            limit++;
				}
			} else if (heightDiff < 0) {
	            limit = 0;
				while (heightDiff < 0 && limit < 80 && newNode.agent.touchingWater && !newNode.agent.verticalCollision) {
		        	RenderHelper.renderNode(newNode);
		        	try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					heightDiff = DistanceCalculator.getJumpHeight(newNode.agent.getPos().y, nextBlockNode.getPos(true).y);
		            newNode = new Node(newNode, world, new PathInput(false, false, false, false, false, true, false, desiredPitch, desiredYaw),
		            		new Color(0, 0, 150), newNode.cost);
		            limit++;
				}
			}
			return newNode;
		}
        // Run forward to the node
		while (distance > 0.5 && limit < 150 && newNode.agent.touchingWater && !newNode.agent.horizontalCollision && !newNode.agent.verticalCollision) {
//        	RenderHelper.renderNode(newNode);
//        	try {
//				Thread.sleep(5);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        	limit++;
    		distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
    		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
    		desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
            newNode = new Node(newNode, world, new PathInput(true, false, false, true, false, false, true, desiredPitch, desiredYaw + 45),
            		new Color(0, 0, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
            
        }
            
        return newNode;
	}

}
