package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.world.level.Level;

public class ClimbALadderMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;
	    Node newNode = new Node(parent, world, new AgentInput(false, false, false, false, false, false, false, agent.getXRot(), agent.getXRot()),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	    
        int limit = 0;
        while (limit < 8 && Math.abs(newNode.agent.getPos().y - nextBlockNode.getPos(true).y) > 0.2) {
        	limit++;
//        	RenderHelper.renderNode(newNode);
//        	try {
//				Thread.sleep(4);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}

            newNode = new Node(newNode, world, new AgentInput(false, false, false, false, true, false, false, agent.getXRot(), agent.getXRot()),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        }
        
        return newNode;
	}
}
