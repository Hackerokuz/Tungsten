package kaptainwutax.tungsten.path.specialMoves;

import java.util.stream.Stream;

import com.google.common.collect.Streams;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.TungstenModRenderContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.AgentChecker;
import kaptainwutax.tungsten.helpers.BlockStateChecker;
import kaptainwutax.tungsten.helpers.DirectionHelper;
import kaptainwutax.tungsten.helpers.DistanceCalculator;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.PathInput;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;
public class SwimmingMove {

	public static Node generateMove(Node parent, BlockNode nextBlockNode) {
		double cost = 0.02;
		WorldView world = TungstenModDataContainer.world;
		Agent agent = parent.agent;
		float desiredYaw = (float) DirectionHelper.calcYawFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		float desiredPitch = (float) DirectionHelper.calcPitchFromVec3d(agent.getPos(), nextBlockNode.getPos(true));
		double distance = DistanceCalculator.getHorizontalEuclideanDistance(agent.getPos(), nextBlockNode.getPos(true));
	    Node newNode = new Node(parent, world, new PathInput(true, false, false, true, false, false, true, -30f, desiredYaw + 45),
	    				new Color(0, 255, 150), parent.cost + (parent.agent.swimming ? 0 : 0.05) +  0.0001);
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
		while (i < 28 && distance > 0.2 && !newNode.agent.horizontalCollision) {
			if (newNode.agent.isSubmergedInWater) {
				newNode = new Node(newNode, world, new PathInput(true, false, false, true, i % 20 == 0, false, true, -30f, desiredYaw + 45f),
						new Color(0, 255, 150), newNode.cost + (newNode.agent.swimming ? 0 : 0.05) + cost);
			} else {
				newNode = new Node(newNode, world, new PathInput(true, false, false, true, newNode.agent.getPos().y > newNode.agent.getEyeY() + 0.1 , false, true, -30f, desiredYaw + 45f),
						new Color(0, 255, 150), newNode.cost + (newNode.agent.swimming ? 0 : 0.05) + cost);
				if (newNode.agent.velY > 0.045) {
					newNode = new Node(newNode, world, new PathInput(true, false, false, true, false, true, true, -30f, desiredYaw + 45f),
							new Color(0, 255, 150), newNode.cost + (newNode.agent.swimming ? 0 : 0.05) + cost);
					newNode = new Node(newNode, world, new PathInput(true, false, false, true, false, true, true, -30f, desiredYaw + 45f),
							new Color(0, 255, 150), newNode.cost + (newNode.agent.swimming ? 0 : 0.05) + cost);
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
