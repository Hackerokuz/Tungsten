package kaptainwutax.tungsten.client.path.specialMoves;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.TungstenModRenderContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.client.helpers.DirectionHelper;
import kaptainwutax.tungsten.client.helpers.DistanceCalculator;
import kaptainwutax.tungsten.client.helpers.NodeCostCalculator;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.Node;
import kaptainwutax.tungsten.client.path.PathInput;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;
public class SwimmingMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		WorldView world = TungstenModDataContainer.world;
		Agent agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		float desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new PathInput(true, false, false, true, false, false, true, -30f, desiredYaw + 45),
	    				new Color(0, 255, 150), parent.cost);
		NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
		newNode.combinedCost = newNode.combinedCost * 0.0004;
		int limit = 0;
		double closestDistance = Double.MAX_VALUE;
//		int eyeBlockPos = (int) newNode.agent.getPos().subtract(0.05D, -0.4f - 0.05D, 0.05D).getY();
//		BlockPos.Mutable bP = new BlockPos.Mutable(newNode.agent.getBlockPos().getX(), eyeBlockPos, newNode.agent.getBlockPos().getZ());
//		boolean isWater = BlockStateChecker.isAnyWater(TungstenModDataContainer.world.getBlockState(bP));
//		while (isWater) {
//			bP.move(0, 1, 0);
//			isWater = BlockStateChecker.isAnyWater(TungstenModDataContainer.world.getBlockState(bP));
//		}
//		eyeBlockPos = bP.getY()-1;
		int i = 0;
		RenderHelper.clearRenderers();
		while (i < 28 && distance > 0.2 && !newNode.agent.horizontalCollision && newNode.agent.posY >= world.getBottomY()) {
			if (newNode.agent.isSubmergedInWater) {
				newNode = new Node(newNode, world, new PathInput(true, false, false, true, i % 20 == 0, false, true, -30f, desiredYaw + 45f),
						new Color(0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
				newNode.combinedCost = newNode.combinedCost * 0.0004;
			} else {
				newNode = new Node(newNode, world, new PathInput(true, false, false, true, newNode.agent.getPos().y > newNode.agent.getEyeY() + 0.1 , false, true, -30f, desiredYaw + 45f),
						new Color(0, 255, 150), newNode.cost);
				NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
				newNode.combinedCost = newNode.combinedCost * 0.0004;
				if (newNode.agent.velY > 0.045) {
					newNode = new Node(newNode, world, new PathInput(true, false, false, true, false, true, true, -30f, desiredYaw + 45f),
							new Color(0, 255, 150), newNode.cost);
					NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
					newNode.combinedCost = newNode.combinedCost * 0.0004;
					newNode = new Node(newNode, world, new PathInput(true, false, false, true, false, true, true, -30f, desiredYaw + 45f),
							new Color(0, 255, 150), newNode.cost);
					NodeCostCalculator.updateNode(world, newNode, nextBlockNode.getPos(true));
					newNode.combinedCost = newNode.combinedCost * 0.0004;
                }
			}
			distance = DistanceCalculator.getHorizontalEuclideanDistance(newNode.agent.getPos(), nextBlockNode.getPos(true));

			if (closestDistance > distance) {
				closestDistance = distance;
			} else {
				break;
			}
			i++;
		}
            
        return newNode;
	}

}
