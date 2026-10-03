package kaptainwutax.tungsten.client.path.specialMoves.neo;

import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NeoJump {
	
	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;
		
		Direction jumpTowardsDirection = DirectionHelper.getHorizontalDirectionFromPos(nextBlockNode.previous.getPos(true), nextBlockNode.getPos(true));
		float jumpTowardsRotation = jumpTowardsDirection.getRotation().angle();
		Direction neoDirection = nextBlockNode.getNeoSide();
		float neoRotation = neoDirection.getRotation().angle();

		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
        double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new AgentInput(false, false, false, false, false, false, false, agent.getXRot(), desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        
        // Go forward to edge and jump
        boolean jump = false;
        int limit = 0;
//        desiredYaw = nudgeRotation(jumpTowardsRotation, 30);
	      if (neoRotation == 180 || neoRotation == 0)
	    	  desiredYaw = nudgeRotation(neoRotation, -35);
	      if (neoRotation == 270 || neoRotation == 90)
	    	  desiredYaw = nudgeRotation(neoRotation, 35);

        while (limit < 40 && jump == false && newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()-1) {
            AABB adjustedBox = newNode.agent.getBoundingBox().move(0, -0.5, 0).inflate(-0.04, 0, -0.04);
        	limit++;
        	Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(TungstenModDataContainer.world, adjustedBox));
//        	RenderHelper.renderNode(newNode);
            if (blockCollisions.findAny().isEmpty()) {
        		desiredYaw = nudgeRotation(jumpTowardsRotation, 5);
        		jump = true;
        	}
            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, jump, false, true, agent.getXRot(), desiredYaw),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
            if (jump) break;
        }
        

        limit = 0;
        while (limit < 40 && !newNode.agent.onGround() && newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()-1) {
        	limit++;
//        	RenderHelper.renderNode(newNode);
//            try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, false, false, true, agent.getXRot(),
            		(neoRotation == 270 || neoRotation == 90) ?
            			nudgeRotation(jumpTowardsRotation, distance < 2 ? 65 : 35)
        			:
        				nudgeRotation(jumpTowardsRotation, distance < 2 ? -65 : -35)

            		),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        }
            
        return newNode;
	}
	
	private static float nudgeRotation(float rotation, float nudgeAmount) {
		return DirectionHelper.calcYawFromRotation(rotation + nudgeAmount);
	}
	
}

