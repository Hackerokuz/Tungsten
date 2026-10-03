package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.world.level.Level;

public class TurnACornerMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode, boolean reverse) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;

		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new AgentInput(false, false, false, false, false, false, false, agent.getXRot(), desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	    
	    boolean jump = false;
        int limit = 0;
        desiredYaw -= reverse ? -90f : 90f;
        while (limit < 4 && !newNode.agent.horizontalCollision) {
        	limit++;
        	RenderHelper.renderNode(newNode);
        	try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, jump, false, true, agent.getXRot(), desiredYaw),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        }
        limit = 0;
		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
        while (limit < 4 && !newNode.agent.horizontalCollision) {
        	limit++;
        	RenderHelper.renderNode(newNode);
        	try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, jump, false, true, agent.getXRot(), desiredYaw),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        }
        
        return newNode;
	}
}
