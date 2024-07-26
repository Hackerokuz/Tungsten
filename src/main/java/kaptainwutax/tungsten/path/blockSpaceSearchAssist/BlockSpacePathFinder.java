package kaptainwutax.tungsten.path.blockSpaceSearchAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import baritone.api.pathing.movement.ActionCosts;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class BlockSpacePathFinder {
	
	public static boolean active = false;
	public static Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final BlockNode[] bestSoFar = new BlockNode[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	protected static final double MIN_DIST_PATH = 5;
	
	
	public static void find(WorldView world, Vec3d target) {
		if(active)return;
		active = true;

		thread = new Thread(() -> {
			try {
				search(world, target);
			} catch(Exception e) {
				e.printStackTrace();
			}

			active = false;
		});
		thread.start();
	}
	
	public static List<BlockNode> search(WorldView world, Vec3d target) {
		Goal goal = new Goal((int) target.x, (int) target.y, (int) target.z);
		boolean failing = true;
		
		TungstenMod.RENDERERS.clear();

		ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
		player.sendMessage(Text.literal("Searchin..."));
		
		double startTime = System.currentTimeMillis();
		

		BlockNode start = new BlockNode(player.getBlockPos(), goal);
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.cost;
            bestSoFar[i] = start;
        }

		BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
		Set<BlockNode> closed = new HashSet<>();
		openSet.insert(start);
		target = target.subtract(0.5, 0, 0.5);
		while(!openSet.isEmpty()) {
			TungstenMod.RENDERERS.clear();
			BlockNode next = openSet.removeLowest();
			
			if (closed.contains(next)) continue;
			
			closed.add(next);
			if(TungstenMod.pauseKeyBinding.isPressed()) break;
			if(next.getPos().squaredDistanceTo(target) <= 0.4D && !failing && next.wasCleared(world, next.previous.getBlockPos(), next.getBlockPos())) {
				TungstenMod.RENDERERS.clear();
				TungstenMod.TEST.clear();
				BlockNode n = next;
				List<BlockNode> path = new ArrayList<>();

				player.sendMessage(Text.literal("FOUND IT"));
				while(n.previous != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.RED));
	                TungstenMod.RENDERERS.add(new Cuboid(n.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
					n = n.previous;
				}

				path.add(n);
				Collections.reverse(path);
				return path;
			}
			
			if(TungstenMod.RENDERERS.size() > 9000) {
				TungstenMod.RENDERERS.clear();
			}
			 renderPathSoFar(next);

			 TungstenMod.RENDERERS.add(new Cuboid(next.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.RED));

			
			for(BlockNode child : next.getChildren(world, goal)) {
				if (closed.contains(child)) continue;
				

				updateNode(next, child, target);
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                failing = updateBestSoFar(child, bestHeuristicSoFar, target);
                TungstenMod.RENDERERS.add(new Cuboid(child.getPos(), new Vec3d(1.0D, 1.0D, 1.0D), Color.BLUE));
//				try {
//                Thread.sleep(50);
//            } catch (InterruptedException ignored) {}
			}
		}
		TungstenMod.TEST.clear();
		if (openSet.isEmpty()) player.sendMessage(Text.literal("Ran out of nodes"));
		return null;
	}
	
	private static double computeHeuristic(Vec3d position, Vec3d target) {
	    double dx = position.x - target.x;
	    double dy = (position.y - target.y) * 8;
	    double dz = position.z - target.z;
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz)) * 30;
	}
	
	private static void updateNode(BlockNode current, BlockNode child, Vec3d target) {
	    Vec3d childPos = child.getPos();

	    double tentativeCost = current.cost + ActionCosts.WALK_ONE_BLOCK_COST; // Assuming uniform cost for each step
	    

	    double estimatedCostToGoal = computeHeuristic(childPos, target);

	    child.previous = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static boolean updateBestSoFar(BlockNode child, double[] bestHeuristicSoFar, Vec3d target) {
		boolean failing = false;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
	        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
	            bestHeuristicSoFar[i] = heuristic;
	            bestSoFar[i] = child;
	            if (failing && getDistFromStartSq(child, target) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
	        }
	    }
	    return failing;
	}
	
	protected static double getDistFromStartSq(BlockNode n, Vec3d target) {
        double xDiff = n.getPos().x - target.x;
        double yDiff = n.getPos().y - target.y;
        double zDiff = n.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }
	
	private static void renderPathSoFar(BlockNode n) {
		int i = 0;
		while(n.previous != null) {
			TungstenMod.RENDERERS.add(new Line(new Vec3d(n.previous.x + 0.5, n.previous.y + 0.1, n.previous.z + 0.5), new Vec3d(n.x + 0.5, n.y + 0.1, n.z + 0.5), Color.WHITE));
			i++;
			n = n.previous;
		}
	}

}
