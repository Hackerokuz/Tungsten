package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;

public class EnterWaterAndSwimMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		if (!parent.agent.touchingWater) {
			if (parent.agent.canSprint()) {
		    	Node sprintJumpMove = SprintJumpMove.generateMove(parent, nextBlockNode);
		    	if (sprintJumpMove.agent.touchingWater) {
                    return SwimmingMove.generateMove(sprintJumpMove, nextBlockNode);
		    	}
			} else {
		    	Node walkMove = WalkToNode.generateMove(parent, nextBlockNode);
		    	if (walkMove.agent.touchingWater) {
		    		Node swimmingMove = SwimmingMove.generateMove(walkMove, nextBlockNode);
		    		RenderHelper.renderPathSoFar(swimmingMove);
		    		return swimmingMove;
		    	}
			}
		}
		return parent;
	}
}
