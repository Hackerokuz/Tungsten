package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.render.Color;
import kaptainwutax.tungsten.render.Cuboid;
import kaptainwutax.tungsten.render.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class PathFinder {

	public static boolean active = false;
	public static Thread thread = null;
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final Node[] bestSoFar = new Node[COEFFICIENTS.length];
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

	private static void search(WorldView world, Vec3d target) {
		boolean failing = true;
		TungstenMod.RENDERERS.clear();

		ClientPlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
		
		double startTime = System.currentTimeMillis();
		

		Node start = new Node(null, Agent.of(player), null, 0);
		start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target);
		
		double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
		for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = start.heuristic;
            bestSoFar[i] = start;
        }

		BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
		Set<Vec3d> closed = new HashSet<>();
		openSet.insert(start);
		while(!openSet.isEmpty()) {
			TungstenMod.RENDERERS.clear();
			Node next = openSet.removeLowest();
			if (shouldNodeBeSkiped(next, target, closed, true)) continue;

			
			if(MinecraftClient.getInstance().options.socialInteractionsKey.isPressed()) break;
			double minVel = 0.2;
			if(next.agent.getPos().squaredDistanceTo(target) <= 0.4D && !failing /*|| !failing && (startTime + 5000) - System.currentTimeMillis() <= 0*/) {
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
				if (path.get(path.size()-1).agent.velX < minVel && path.get(path.size()-1).agent.velX > -minVel && path.get(path.size()-1).agent.velZ < minVel && path.get(path.size()-1).agent.velZ > -minVel) {					
					TungstenMod.EXECUTOR.setPath(path);
					break;
				}
			} /* else if (previous != null && next.agent.getPos().squaredDistanceTo(target) > previous.agent.getPos().squaredDistanceTo(target)) continue; */
			if(TungstenMod.RENDERERS.size() > 9000) {
				TungstenMod.RENDERERS.clear();
			}
			 renderPathSoFar(next);

			 TungstenMod.RENDERERS.add(new Cuboid(next.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), Color.RED));
			 
//			 try {
//                 Thread.sleep(600);
//             } catch (InterruptedException ignored) {}
			 
			for(Node child : next.getChildren(world, target)) {
				if (shouldNodeBeSkiped(child, target, closed)) continue;
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
//				if (next.agent.getPos().distanceTo(child.agent.getPos()) < 0.2) continue;
				updateNode(next, child, target);
				
                if (child.isOpen()) {
                    openSet.update(child);
                } else {
                    openSet.insert(child);//dont double count, dont insert into open set if it's already there
                }
                
                failing = updateBestSoFar(child, bestHeuristicSoFar, target);

		        
//				open.add(child);

//				TungstenMod.RENDERERS.add(new Line(child.agent.getPos(), child.parent.agent.getPos(), child.color));
				TungstenMod.RENDERERS.add(new Cuboid(child.agent.getPos().subtract(0.05D, 0.05D, 0.05D), new Vec3d(0.1D, 0.1D, 0.1D), child.color));
			}
		}
	}
	
	private static boolean shouldNodeBeSkiped(Node n, Vec3d target, Set<Vec3d> closed) {
		return shouldNodeBeSkiped(n, target, closed, false);
	}
	
	private static boolean shouldNodeBeSkiped(Node n, Vec3d target, Set<Vec3d> closed, boolean addToClosed) {
		if (n.agent.getPos().distanceTo(target) < 2.0) {
			if(closed.contains(new Vec3d(Math.round(n.agent.getPos().x*1000), Math.round(n.agent.getPos().y * 1000), Math.round(n.agent.getPos().z*1000)))) return true;
			if (addToClosed) closed.add(new Vec3d(Math.round(n.agent.getPos().x*1000), Math.round(n.agent.getPos().y * 1000), Math.round(n.agent.getPos().z*1000)));
		} else if(closed.contains(new Vec3d(Math.round(n.agent.getPos().x*10), Math.round(n.agent.getPos().y * 10), Math.round(n.agent.getPos().z*10)))) return true;
		if (addToClosed) closed.add(new Vec3d(Math.round(n.agent.getPos().x*10), Math.round(n.agent.getPos().y * 10), Math.round(n.agent.getPos().z*10)));
		
		return false;
	}
	
	private static double computeHeuristic(Vec3d position, boolean onGround, Vec3d target) {
	    double dx = position.x - target.x;
	    double dy = (position.y - target.y);
//	    if (onGround || dy < 1.6 && dy > -1.6) dy = 0;
	    
	    double dz = position.z - target.z;
	    return (Math.sqrt(dx * dx + dy * dy + dz * dz)) * 33.563;
	}
	
	private static void updateNode(Node current, Node child, Vec3d target) {
	    Vec3d childPos = child.agent.getPos();

	    double collisionScore = 0;
	    double tentativeCost = current.cost + 1; // Assuming uniform cost for each step
	    if (child.agent.horizontalCollision) {
	        collisionScore += 25 + (Math.abs(current.agent.velZ - child.agent.velZ) + Math.abs(current.agent.velX - child.agent.velX)) * 120;
	    }

	    double estimatedCostToGoal = computeHeuristic(childPos, child.agent.onGround, target) + collisionScore;

	    child.parent = current;
	    child.cost = tentativeCost;
	    child.estimatedCostToGoal = estimatedCostToGoal;
	    child.combinedCost = tentativeCost + estimatedCostToGoal;
	}
	
	private static boolean updateBestSoFar(Node child, double[] bestHeuristicSoFar, Vec3d target) {
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
	
	protected static double getDistFromStartSq(Node n, Vec3d target) {
        double xDiff = n.agent.getPos().x - target.x;
        double yDiff = n.agent.getPos().y - target.y;
        double zDiff = n.agent.getPos().z - target.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }

	public static double calcYawFromVec3d(Vec3d orig, Vec3d dest) {
        double[] delta = {orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
        double yaw = Math.atan2(delta[0], -delta[2]);
        return yaw * 180.0 / Math.PI;
    }
	
	private static Direction getHorizontalDirectionFromYaw(double yaw) {
        yaw %= 360.0F;
        if (yaw < 0) {
            yaw += 360.0F;
        }

        if ((yaw >= 45 && yaw < 135) || (yaw >= -315 && yaw < -225)) {
            return Direction.WEST;
        } else if ((yaw >= 135 && yaw < 225) || (yaw >= -225 && yaw < -135)) {
            return Direction.NORTH;
        } else if ((yaw >= 225 && yaw < 315) || (yaw >= -135 && yaw < -45)) {
            return Direction.EAST;
        } else {
            return Direction.SOUTH;
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
