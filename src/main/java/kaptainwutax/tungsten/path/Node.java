package kaptainwutax.tungsten.path;

import java.util.ArrayList;
import java.util.List;

import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.render.Color;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

public class Node {

	public Node parent;
	public Agent agent;
	public PathInput input;
	public double cost;
	public double heuristic;
	public double estimatedCostToGoal = 0;
	public int heapPosition;
	public double combinedCost;
	public Color color;

	public Node(Node parent, Agent agent, Color color, double pathCost) {
		this.parent = parent;
		this.agent = agent;
		this.color = color;
		this.cost = pathCost;
		this.heuristic = 0;
		this.heapPosition = -1;
	}

	public Node(Node parent, WorldView world, PathInput input, Color color, double pathCost) {
		this.parent = parent;
		this.agent = Agent.of(parent.agent, input).tick(world);
		this.input = input;
		this.color = color;
		this.cost = pathCost;
		this.heuristic = 0;
		this.heapPosition = -1;
	}
	
	 public boolean isOpen() {
	        return heapPosition != -1;
    }

	public List<Node> getChildren(WorldView world, Vec3d target) {
		Node n = this.parent;
//		boolean mismatch = false;
//		int i;
//
//
//		for(i = 0; i < 4 && n != null; i++) {
//			if(n.agent.blockX != this.agent.blockX || n.agent.blockY != this.agent.blockY || n.agent.blockZ != this.agent.blockZ) {
//				mismatch = true;
//				break;
//			}
//
//			n = n.parent;
//		}
		if(n != null && n.agent.isInLava()) return new ArrayList<>();

//		if(!mismatch && i == 5) {
//			return new ArrayList<>();
//		}

		if(this.agent.onGround || this.agent.touchingWater) {
			List<Node> nodes = new ArrayList<>();
			// float[] pitchValues = {0.0f, 45.0f, 90.0f}; // Example pitch values
//        	float[] yawValues = {-135.0f, -90.0f, -67.5f, -45.0f, -22.5f, 0.0f, 22.5f, 45.0f, 67.5f, 90.0f, 135.0f, 180.0f}; // Example yaw values

			for (boolean forward : new boolean[]{true, false}) {
//				for (boolean back : new boolean[]{true, false}) {
					for (boolean right : new boolean[]{true, false}) {
						for (boolean left : new boolean[]{true, false}) {
								for (boolean sneak : new boolean[]{true, false}) {
										// for (float pitch : pitchValues) {
//											for (float yaw : yawValues) {
										for (float yaw = -180.0f; yaw < 180.0f; yaw += 22.5) {
											for (boolean sprint : new boolean[]{true, false}) {
												for (boolean jump : new boolean[]{true, false}) {
													Node newNode = new Node(this, world, new PathInput(forward, false, right, left, jump, sneak, sprint, agent.pitch, yaw), new Color(0, 255, 0), this.cost + (jump ? sneak ? 4 :0.5 : sneak ? 4 : 2));
													nodes.add(newNode);
//											}
											}
										 }
									}
//								}
							}
						}
					}
//				}
			}

			return nodes;
			
			// return new Node[] {
			// 	new Node(this, world, new PathInput(true, false, false, false, true,
			// 		false, true, this.agent.pitch, this.agent.yaw), new Color(0, 255, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw), new Color(255, 0, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw + 90.0F), new Color(255, 255, 0), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, this.agent.yaw - 90.0F), new Color(255, 0, 255), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, (-1 * (this.agent.yaw)) + 90.0F), new Color(255, 0, 25), this.pathCost + 1),
			// 	new Node(this, world, new PathInput(true, false, false, false, false,
			// 		false, true, this.agent.pitch, (-1 * (this.agent.yaw)) - 90.0F), new Color(25, 0, 255), this.pathCost + 1),
			// };
		} else {
			List<Node> nodes = new ArrayList<Node>();
			nodes.add(new Node(this, world, new PathInput(true, false, false, false, false,
			false, true, this.agent.pitch, this.agent.yaw), new Color(0, 255, 255), this.cost + 1));
			return nodes;
		}
	}

}
