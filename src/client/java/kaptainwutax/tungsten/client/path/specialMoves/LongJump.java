package kaptainwutax.tungsten.client.path.specialMoves;

import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.AgentChecker;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LongJump {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new AgentInput(false, false, false, false, false, false, false, agent.getXRot(), desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	    // Go back if we are too close to the edge to jump
	    if (distance > 4 && DistanceCalculator.getDistanceToEdge(newNode.agent) < 0.8 && AgentChecker.isAgentStationary(newNode.agent, 0.07)) {
	        for (int j = 0; j < 6; j++) {
	        	if (newNode.agent.horizontalCollision) break;
	            AABB adjustedBox = newNode.agent.getBoundingBox().move(0, -0.5, 0).inflate(-0.45, 0, -0.45);
	        	Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(TungstenModDataContainer.world, adjustedBox));
	        	if (j > 1 && blockCollisions.findAny().isEmpty()) break;
	        	desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
	            newNode = new Node(newNode, world, new AgentInput(false, true, false, false, false, false, false, agent.getXRot(), desiredYaw),
	            		new Color(0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	        }
	    }
        
        boolean jump = false;
        int limit = 0;
        double boxExpension = -0.001;
		if (distance > 1.0) {
			distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
	        // Go forward to edge and jump
	        while (limit < 10) {
	        	if (newNode.agent.horizontalCollision) break;
	            AABB adjustedBox = newNode.agent.getBoundingBox().move(0, -0.5, 0).inflate(boxExpension, 0, boxExpension);
	        	limit++;
	        	if (distance > 3) {
		        	Stream<VoxelShape> blockCollisions = Streams.stream(newNode.agent.getBlockCollisions(TungstenModDataContainer.world, adjustedBox));
		            if (blockCollisions.findAny().isEmpty()) jump = true;
	        	} else {
	        		if (DistanceCalculator.getDistanceToEdge(newNode.agent) < 0.6) jump = true;
	        	}
	            if (!newNode.agent.onGround()) break;
	
	    		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
	            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, jump, false, true, agent.getXRot(), desiredYaw),
	            		new Color(0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	        }
	        limit = 0;
    		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
	        while (limit < 22 && !newNode.agent.onGround() && newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()-1) {
	        	if (newNode.agent.horizontalCollision) break;
	            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, false, false, false, agent.getXRot(), desiredYaw),
	            		new Color(distance < 0.4 ? 180 : 0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	        	limit++;
	        }
            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, false, false, false, agent.getXRot(), desiredYaw),
            		new Color(distance < 0.4 ? 180 : 0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		} else {
			limit = 0;
	        // Run forward to the node
			while (distance > 0.2 && limit < 22) {
	        	if (newNode.agent.horizontalCollision) break;
	        	limit++;
	    		distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));
	            newNode = new Node(newNode, world, new AgentInput(true, false, false, false, jump, false, false, agent.getXRot(), desiredYaw),
	            		new Color(0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
	        }
		}
            
        return newNode;
	}

}
