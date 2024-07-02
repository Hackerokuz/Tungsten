package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class PathFinder {

	public static boolean active = false;
	public static Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final Node[] bestSoFar = new Node[COEFFICIENTS.length];
	private static final double minimumImprovement = 0.21;
	
	
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

	private static void search(WorldView world, Vec3d target) {
		TungstenMod.RENDERERS.clear();

		ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
		
		double startTime = System.currentTimeMillis();
		

		Node start = new Node(null, Agent.of(player), null, 0);
		start.combinedCost = computeHeuristic(start.agent.getPos(), target);
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.heuristic;
            bestSoFar[i] = start;
        }

		Queue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(o -> o.heuristic));
		BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
		Set<Vec3d> closed = new HashSet<>();
		openSet.insert(start);
		open.add(start);
		while(!openSet.isEmpty()) {
			Node next = openSet.removeLowest();
//			closed.add(next.agent.getPos());
			if(closed.size() > 1000000)break;
			if(MinecraftClient.getInstance().options.socialInteractionsKey.isPressed()) break;
			System.out.println(startTime - System.currentTimeMillis());
			if(next.agent.getPos().squaredDistanceTo(target) <= 0.5D /*|| (startTime + 3000) - System.currentTimeMillis() <= 0*/) {
				TungstenMod.RENDERERS.clear();
				Node n = next;
				List<Node> path = new ArrayList<>();

				while(n.parent != null) {
					path.add(n);
					TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
					TungstenMod.RENDERERS.add(new Cuboid(n.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), n.color));
					n = n.parent;
				}

				path.add(n);
				Collections.reverse(path);
				double minVel = 0.04;
				if (path.get(path.size()-1).agent.velX < minVel && path.get(path.size()-1).agent.velX > -minVel && path.get(path.size()-1).agent.velZ < minVel && path.get(path.size()-1).agent.velZ > -minVel) {					
					TungstenMod.EXECUTOR.setPath(path);
					break;
				}
			} /* else if (previous != null && next.agent.getPos().squaredDistanceTo(target) > previous.agent.getPos().squaredDistanceTo(target)) continue; */
			if(TungstenMod.RENDERERS.size() > 9000) {
				TungstenMod.RENDERERS.clear();
			}
			 renderPathSoFar(next);
			 
			for(Node child : next.getChildren(world)) {
//				if(closed.contains(child.agent.getPos()))continue;
				
				// DUMB HEURISTIC CALC
//				child.heuristic = child.pathCost / child.agent.getPos().distanceTo(start.agent.getPos()) * child.agent.getPos().distanceTo(target);

				// NOT SO DUMB HEURISTIC CALC
//				double heuristic = 20.0D * child.agent.getPos().distanceTo(target);
//				
//				if (child.agent.horizontalCollision) {
//		            //massive collision punish
//		            double d = 25+ (Math.abs(next.agent.velZ-child.agent.velY)+Math.abs(next.agent.velX-child.agent.velX))*120;
//		            heuristic += d;
//		        }
//				
//				child.heuristic = heuristic;
				
				// AStar? HEURISTIC CALC

				updateNode(next, child, target);
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                updateBestSoFar(child, bestHeuristicSoFar);

		        
//				open.add(child);

//				TungstenMod.RENDERERS.add(new Line(child.agent.getPos(), child.parent.agent.getPos(), child.color));
				TungstenMod.RENDERERS.add(new Cuboid(child.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), child.color));
			}
		}
	}
	
	private static double computeHeuristic(Vec3d position, Vec3d target) {
	    double dx = position.x - target.x;
	    double dy = position.y - target.y;
	    double dz = position.z - target.z;
	    return Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
	}
	
	private static void updateNode(Node current, Node child, Vec3d target) {
	    Vec3d childPos = child.agent.getPos();

	    double collisionScore = 0;
	    double tentativeCost = current.cost + 1; // Assuming uniform cost for each step
	    if (child.agent.horizontalCollision) {
	        collisionScore += 25 + (Math.abs(current.agent.velZ - child.agent.velZ) + Math.abs(current.agent.velX - child.agent.velX)) * 120;
	        tentativeCost -= 30;
	    }

	    double estimatedCostToGoal = computeHeuristic(childPos, target) + collisionScore;

	    child.parent = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static void updateBestSoFar(Node child, double[] bestHeuristicSoFar) {
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
	        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
	            bestHeuristicSoFar[i] = heuristic;
	            bestSoFar[i] = child;
	        }
	    }
	}

	private static void renderPathSoFar(Node n) {
		int i = 0;
		while(n.parent != null) {
			TungstenMod.RENDERERS.add(new Line(n.agent.getPos(), n.parent.agent.getPos(), n.color));
			i++;
			n = n.parent;
		}
	}
	
}
