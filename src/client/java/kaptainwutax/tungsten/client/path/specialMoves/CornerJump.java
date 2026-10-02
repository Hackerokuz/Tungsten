package kaptainwutax.tungsten.client.path.specialMoves;

import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathInput;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CornerJump {
	
	public static Node generateMove(Node parent, BlockNode nextBlockNode, boolean reverse) {
		Level world = TungstenModDataContainer.world;
		AgentEntity agent = parent.agent;

		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new PathInput(false, false, false, false, false, false, false, agent.pitch, desiredYaw),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));

	    // Go back
//        for (int j = 0; j < 5; j++) {
//            RenderHelper.renderNode(newNode);
//            Box adjustedBox = newNode.agent.box.offset(0, -0.5, 0).expand(-0.45, 0, -0.45);
//        	Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(TungstenMod.mc.world, adjustedBox));
//        	if (blockCollisions.findAny().isEmpty()) break;
//            newNode = new Node(newNode, world, new PathInput(false, true, false, false, false, false, false, agent.pitch, desiredYaw),
//            		new Color(0, 255, 150), newNode.cost + 5);
//        }
        
        // Go forward to edge and jump
        boolean jump = false;
        int limit = 0;
        desiredYaw += reverse ? -90f : 90f;
        while (limit < 18 && !jump && newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()-2) {
            AABB adjustedBox = newNode.agent.getBoundingBox().move(0, -0.5, 0).inflate(-0.04, 0, -0.04);
        	limit++;
        	Stream<VoxelShape> blockCollisions = Streams.stream(newNode.agent.getBlockCollisions(TungstenModDataContainer.world, adjustedBox));
//        	RenderHelper.renderNode(newNode, TungstenMod.TEST);
//            try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
            if (blockCollisions.findAny().isEmpty() || newNode.agent.horizontalCollision || limit > 10) {
        		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
        		jump = true;
        	}

            newNode = new Node(newNode, world, new PathInput(true, false, false, false, jump, false, true, agent.pitch, desiredYaw),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
            if (jump) break;
        }
        

        limit = 0;
        Direction dir = DirectionHelper.getHorizontalDirectionFromPos(nextBlockNode.previous.getPos(), nextBlockNode.getPos());
        Vec3d offsetVec = new Vec3d(0, 0, 0).offset(dir, 0.5);
        while (limit < 40 && newNode.agent.getPos().y > nextBlockNode.getBlockPos().getY()-1) {
            Box adjustedBox = newNode.agent.box.offset(offsetVec).expand(-0.001, 0, -0.001);
        	limit++;
        	Stream<VoxelShape> blockCollisions = Streams.stream(agent.getBlockCollisions(TungstenModDataContainer.world, adjustedBox));
//        	RenderHelper.renderNode(newNode, TungstenMod.TEST);
            if (blockCollisions.findAny().isEmpty()) {
//                try {
//    				Thread.sleep(50);
//    			} catch (InterruptedException e) {
//    				// TODO Auto-generated catch block
//    				e.printStackTrace();
//    			}
                if (newNode.agent.onGround)
        		desiredYaw = (float) DirectionHelper.calcYawFromVec3d(newNode.agent.getPos(), nextBlockNode.getPos(true));
        	}
            newNode = new Node(newNode, world, new PathInput(true, false, false, false, false, false, true, agent.pitch, desiredYaw),
            		new Color(0, 255, 150), newNode.cost);
			NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
        	limit++;
        	if (newNode.agent.getPos().isWithinRangeOf(nextBlockNode.getPos(true), 0.7, 0.8)) break;
        }
            
        return newNode;
	}
	
}
