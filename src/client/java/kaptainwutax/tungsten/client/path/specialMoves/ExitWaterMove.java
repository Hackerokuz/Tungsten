package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.world.level.Level;

public class ExitWaterMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
	    if (!parent.agent.isInWater()) return parent;
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		float desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
		double closestDistance = Double.MAX_VALUE;
	    Node newNode = new Node(parent, world, new AgentInput(false, false, false, false, false, false, false, desiredPitch, desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		newNode.combinedCost = newNode.combinedCost * 0.0004;
		int limit = 0;
        // Run forward to the node
		while (distance > 0.2 && limit < 40) {
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
            newNode = new Node(newNode, world, new AgentInput(true, false, false, true, true, false, true, desiredPitch, desiredYaw + 45),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
			newNode.combinedCost = newNode.combinedCost * 0.0004;
            
        }
            
        return newNode;
	}

}
