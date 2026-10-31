package kaptainwutax.tungsten.client.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.AtomicDoubleArray;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.TungstenModRenderContainer;
import kaptainwutax.tungsten.client.helpers.*;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockSpacePathFinder;
import kaptainwutax.tungsten.client.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.Goal;
import kaptainwutax.tungsten.client.path.calculators.BinaryHeapOpenSet;
import kaptainwutax.tungsten.client.path.specialMoves.SprintJumpMove;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import kaptainwutax.tungsten.client.sim.AgentSimulator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class PathFinder {

	/** Closed-set quantum, horizontal. 1/16 block — finer than one tick of any movement. */
	private static final double QUANTUM_XZ = 1.0 / 16.0;
	/** Closed-set quantum, vertical. 1/16 block — jumps move ~0.10/tick and stay distinct. */
	private static final double QUANTUM_Y = 1.0 / 16.0;

	private static final long FLAG_ON_GROUND = 1L;
	private static final long FLAG_ON_CLIMBABLE = 2L;
	private static final long FLAG_IN_WATER = 4L;
	
	ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	public AtomicBoolean active = new AtomicBoolean(false);
	public AtomicBoolean stop = new AtomicBoolean(false);
	public Thread thread = null;
	private AgentSimulator sim;
	private final Set<Long> closed = ConcurrentHashMap.newKeySet();
	private final AtomicDoubleArray bestHeuristicSoFar = new AtomicDoubleArray(COEFFICIENTS.length);
	private BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final AtomicReferenceArray<Node> bestSoFar = new AtomicReferenceArray<Node>(COEFFICIENTS.length);
	private static final double minimumImprovement = -1e-6;
	public static Optional<List<BlockNode>> blockPath = Optional.empty();
	protected static final double MIN_DIST_PATH = 1.8;
	public static AtomicInteger NEXT_CLOSEST_BLOCKNODE_IDX = new AtomicInteger(1);
	protected static AtomicInteger numNodesConsidered = new AtomicInteger(0);
	private static final long timeoutLimit = 550L;
	private long nextRenderAt = 0;

	private long startTime;
	private Node start;

	public Vec3 TARGET = new Vec3(0.5D, 10.0D, 0.5D);

	synchronized public void find(Level world, Vec3 target, LocalPlayer player) {
		find(world, target, player, Optional.empty());
	}

    synchronized public void find(Level world, Vec3 target, LocalPlayer player, Optional<List<BlockNode>> blockPath) {

        if(active.get() || thread != null)return;
        active.set(true);
        stop.set(false);
		sim = AgentSimulator.fromLocalPlayer(TungstenModDataContainer.player, TungstenModDataContainer.player.getDeltaMovement());
        TARGET = target;
        PathFinder.blockPath = blockPath;
        numNodesConsidered.set(0);
        this.start = null;

        thread = new Thread(() -> {
            try {
                while (!player.onGround() && !player.isInWater()) {
                    if (stop.get()) break;
                    try {
                        Thread.sleep(500);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
//                blockPath.ifPresent(blockNodes -> NEXT_CLOSEST_BLOCKNODE_IDX.set(findClosestPositionIDX(world, player.getBlockPos(), blockNodes)));
                search(world, target, player);
            } catch(Exception e) {
                e.printStackTrace();
            }

            active.set(false);
            this.thread = null;
            closed.clear();
            PathFinder.blockPath = Optional.empty();
            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

        });
        thread.setName("PathFinder");
        thread.setPriority(4);
        startTime = System.currentTimeMillis();
        thread.start();
    }

	private void search(Level world, Vec3 target, LocalPlayer player) {
		search(world, null, target, player);
	}

	private void search(Level world, Node start, Vec3 target, LocalPlayer player) {
		search(world, start, target, player, 0);
	}

	private void search(Level world, Node start, Vec3 target, LocalPlayer player, int failedAttempts) {
	    boolean failing = true;
	    TungstenModRenderContainer.RENDERERS.clear();

	    long startTime = System.currentTimeMillis();
	    long primaryTimeoutTime = startTime + timeoutLimit;
		numNodesConsidered.set(0);
	    int timeCheckInterval = 1 << 3;
	    double minVelocity = BlockStateChecker.isAnyWater(world.getBlockState(new BlockPos((int) target.x(), (int) target.y(), (int) target.z()))) ? 0.2 :  0.07;

	    if (player.position().distanceTo(target) < 1.0) {
	        Debug.logMessage("Already at target location!");
	        return;
	    }
	    if (start == null) {
		    	start = initializeStartNode(player, target);
		    	this.start = start;
	    }
	    if (PathFinder.blockPath.isEmpty()) {
			BlockNode nextBlockNode = new BlockNode(new BlockPos((int) TungstenClient.TARGET.x, (int) TungstenClient.TARGET.y, (int) TungstenClient.TARGET.z), new Goal((int) TungstenClient.TARGET.x, (int) TungstenClient.TARGET.y, (int) TungstenClient.TARGET.z), TungstenModDataContainer.player, world);
			Node sprintJumpMove = SprintJumpMove.generateMove(start, nextBlockNode);
			if (sprintJumpMove.agent.position().distanceTo(TungstenClient.TARGET) < 0.5) {
				Node n = new Node(sprintJumpMove, world, new AgentInput(false, false, false, false, false, true, false, sprintJumpMove.agent.getXRot(), sprintJumpMove.agent.getYRot()), Color.RED, sprintJumpMove.cost);
				NodeCostCalculator.accumulateCost(world, n);
				while (!n.agent.onGround()) {
					n = new Node(n, world, new AgentInput(false, false, false, false, false, true, false, n.agent.getXRot(), n.agent.getYRot()), Color.RED, n.cost);
					NodeCostCalculator.accumulateCost(world, n);
				}

				PathFinder.blockPath = findBlockPath(world, target, player);
				executePath(constructPath(n));
				return;
			}

		    Optional<List<BlockNode>> blockPath = findBlockPath(world, target, player);
		    if (blockPath.isPresent()) {
	        	RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
	        	PathFinder.blockPath = blockPath;
	    	    NEXT_CLOSEST_BLOCKNODE_IDX.set(1);

				Debug.logMessage("Searching for inputs!");
	        }
	    }
	    if (PathFinder.blockPath.isEmpty() || PathFinder.blockPath.get().isEmpty()) {
	    	Debug.logWarning("Failed! No block path");
	    	return;
	    }
	
	    initializeBestHeuristics(this.start);
		TungstenModDataContainer.PATHFINDER.openSet = new BinaryHeapOpenSet();
		TungstenModDataContainer.PATHFINDER.openSet.insert(this.start);
	    closed.clear();

	    while (!TungstenModDataContainer.PATHFINDER.openSet.isEmpty()) {

//	        if ((numNodesConsidered.get() & (timeCheckInterval - 1)) == 0) {
			if (handleTimeout(primaryTimeoutTime, target, TungstenModDataContainer.PATHFINDER.start, player, closed)) {
				primaryTimeoutTime = System.currentTimeMillis() + timeoutLimit;

				continue;
			}

			Node next = TungstenModDataContainer.PATHFINDER.openSet.removeLowest();
//	        }

            if (closed.size() > 3e5) {
				closed.clear();
			}

			if (PathFinder.blockPath.isEmpty() || PathFinder.blockPath.get().isEmpty()) {
				Debug.logWarning("Failed! No block path");
				return;
			}
			if (stop.get()) {
				RenderHelper.clearRenderers();
				break;
			}

			if (PathFinder.blockPath.isPresent() && TungstenModRenderContainer.BLOCK_PATH_RENDERER.isEmpty()) {
				new Thread(() -> RenderHelper.renderBlockPath(PathFinder.blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get())).start();
			}
	        
            // Search for a path without fall damage
            if (next.fallDamage) {
            	continue;
            }
	
	        if (isPathComplete(next, TungstenModDataContainer.PATHFINDER.TARGET, failing, TungstenModDataContainer.world)) {
	            if (tryExecutePath(next, target, minVelocity)) {
	            	TungstenModRenderContainer.RENDERERS.clear();
	            	TungstenModRenderContainer.TEST.clear();
	    			closed.clear();
	    			PathFinder.blockPath = Optional.empty();
	                return;
	            }
	        }
	
//	        if (shouldResetSearch(numNodesConsidered.get(), blockPath, next, target)) {
//	        	TungstenModDataContainer.EXECUTOR.cb = () -> {
//		        	blockPath = resetSearch(next, world, blockPath, target, player);
//	        	};
//	            openSet = new BinaryHeapOpenSet();
//	            this.start = initializeStartNode(next, target);
//	            openSet.insert(this.start);
//	            while (TungstenModDataContainer.EXECUTOR.isRunning()) {
//                    if (stop.get()) break;
//					try {
//						Thread.sleep(500);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
//	            continue;
//	        }

			if (PathFinder.blockPath.isPresent() && NEXT_CLOSEST_BLOCKNODE_IDX.get() == (PathFinder.blockPath.get().size()-1) && PathFinder.blockPath.get().getLast().getPos(true, world).distanceTo(target) > 5) {
				while (TungstenModDataContainer.EXECUTOR.isRunning() && TungstenModDataContainer.EXECUTOR.getPercentComplete() < 65) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
				}
			}
	        
	        if (numNodesConsidered.get() % 20 == 0) {
				maybeRender(next);
	        }

	        failing = processNodeChildren(TungstenModDataContainer.world, next, target, TungstenModDataContainer.PATHFINDER.start.agent.getPos(), PathFinder.blockPath, TungstenModDataContainer.PATHFINDER.openSet, closed);
	        numNodesConsidered.set(numNodesConsidered.get()+1);
	        if (updateNextClosestBlockNodeIDX(PathFinder.blockPath.get(), next, closed, TungstenModDataContainer.world)) {
//	        	primaryTimeoutTime = System.currentTimeMillis() + timeoutLimit;
				failedAttempts = 0;
				initializeBestHeuristics(next);   // the node that just reached the new waypoint
	        }
//        	if (numNodesConsidered % 5 == 0 && updateNextClosestBlockNodeIDX(blockPath.get(), next, closed)) {
//        		List<Node> path = constructPath(next);
//                TungstenModDataContainer.EXECUTOR.addPath(path);
//                Node n = path.getLast();
//                clearParentsForBestSoFar(n);
//                start = initializeStartNode(n, target);
//    			closed.clear();
//    			bestHeuristicSoFar = initializeBestHeuristics(start);
//    		    openSet = new BinaryHeapOpenSet();
//    		    openSet.insert(start);
//        	}
	        
//	        try {
//				Thread.sleep(250);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}

			if (TungstenModDataContainer.PATHFINDER.openSet.isEmpty()) {
				RenderHelper.clearRenderers();
				RenderHelper.renderBlockPath(PathFinder.blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
				RenderHelper.renderPathSoFar(next);
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	    }
	
	    if (stop.get()) {
	        Debug.logMessage("stopped!");
	        stop.set(false);
	    } else if (TungstenModDataContainer.PATHFINDER.openSet.isEmpty()) {
//			if (failedAttempts < 2 && TungstenModDataContainer.EXECUTOR.path != null) {
//				RenderHelper.clearRenderers();
//				closed.clear();
//				PathFinder.blockPath = Optional.empty();
//				Node lastNode = TungstenModDataContainer.EXECUTOR.path.getLast();
//
//				search(world, lastNode, target, player, failedAttempts+1);
//				return;
//			}
			Debug.logMessage("Ran out of nodes!");
	    }
	    RenderHelper.clearRenderers();
		closed.clear();
		PathFinder.blockPath = Optional.empty();
	}

	protected static Optional<List<Node>> bestSoFar(boolean logInfo, int numNodes, Node startNode, Vec3 realTarget) {
		if (startNode == null) {
			return Optional.empty();
		}
		// Pick the record that made the most progress, and only if the executor can
		// actually take it. The old loop returned the FIRST coefficient whose running max
		// crossed the threshold — not the best node, and not even the node holding that
		// max.
		Node best = null;
		double bestH = Double.MAX_VALUE;
		for (int i = 0; i < COEFFICIENTS.length; i++) {
			if (TungstenModDataContainer.PATHFINDER.stop.get()) break;
			Node cand = bestSoFar.get(i);
			if (cand == null || cand.parent == null) continue;
			if (!isUsableFallback(cand)) continue;
			double h = cand.estimatedCostToGoal;
			if (h < bestH) { bestH = h; best = cand; }
		}
		if (best == null) return  Optional.empty();
		// UNITS: getEuclideanDistance returns a plain distance, so this compares a plain
		// distance against a plain threshold. The old code compared it against
		// MIN_DIST_PATH * MIN_DIST_PATH and so fired at 3.24 blocks instead of 1.8.
		double moved = DistanceCalculator.getEuclideanDistance(startNode.agent.getPos(), best.agent.getPos());
		if (moved < MIN_DIST_PATH) return Optional.empty();

		List<Node> path = new ArrayList<>();
		Node n = best;
		while (n.parent != null) {
			if (TungstenModDataContainer.PATHFINDER.stop.get()) break;
			path.add(n);
			n = n.parent;
		}
		path.add(n);
		Collections.reverse(path);
		return Optional.of(path);
	}

	/**
	 * A fallback is only worth emitting if the executor can take it. These are the same
	 * conditions setCurrentPath() applies — the point of this helper is that the selection
	 * and the consumer agree, so the fallback is not picked and then thrown away.
	 */
	private static boolean isUsableFallback(Node n) {
		if (!n.agent.onGround() && !n.agent.isInWater() && !n.agent.onClimbable()) return false;
		if (PathFinder.blockPath.isEmpty()) return true;
		int idx = Math.min(NEXT_CLOSEST_BLOCKNODE_IDX.get(), PathFinder.blockPath.get().size() - 1);
		int waypointY = PathFinder.blockPath.get().get(idx).getBlockPos().getY();
		return Math.abs(n.agent.blockPosition().getY() - waypointY) <= 1;
	}

	private void clearParentsForBestSoFar(Node node) {
		for (int i = 0; i < COEFFICIENTS.length; i++) {
			bestSoFar.set(i, null);
		}
	}

	private boolean shouldSkipChild(Node child, Vec3 target, Level world) {
	    return child.agent.isInWater() && shouldSkipNode(child, target, world);
	}
	
	private boolean shouldSkipNode(Node node, Vec3 target, Level world) {
//	    BlockNode bN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
//	    BlockNode lBN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
//	    boolean isBottomSlab = BlockStateChecker.isBottomSlab(TungstenMod.mc.world.getBlockState(bN.getBlockPos().down()));
//	    Vec3d agentPos = node.agent.getPos();
//	    Vec3d parentAgentPos = node.parent == null ? null : node.parent.agent.getPos();
//	    if (!isBottomSlab && !node.agent.onGround && agentPos.y < bN.y && lBN != null && lBN.y <= bN.y && parentAgentPos != null && parentAgentPos.y > agentPos.y) {
//	    	return true;
//	    }
	    return shouldNodeBeSkipped(node, target, closed, true);
	}

	private static long stateKey(Node n) {
		Vec3 pos = n.agent.getPos();
		long qx = Math.round(pos.x * 10);
		long qy = Math.round(pos.y * 10);
		long qz = Math.round(pos.z * 10);
		long flags = 0;
		if (n.agent.onGround()) flags |= FLAG_ON_GROUND;
		if (n.agent.onClimbable()) flags |= FLAG_ON_CLIMBABLE;
		if (n.agent.isInWater()) flags |= FLAG_IN_WATER;

		// Distinct odd multipliers, then one avalanche pass: the low bits of the result
		// are usable as a table index, and a 64-bit key cannot silently collide the way a
		// 32-bit Vec3.hashCode() + inputHash did.
		return mix(qx * 0x9E3779B97F4A7C15L + qy * 0xC2B2AE3D27D4EB4FL + qz * 0x165667B19E3779F9L + flags * 0x27D4EB2F165667C5L);
	}
	/** SplitMix64 finalizer. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
}

	private static boolean shouldNodeBeSkipped(Node n, Vec3 target, Set<Long> closed, boolean addToClosed) {
		long key = stateKey(n);
		if (addToClosed) {
			return !closed.add(key); // atomic claim, one lookup
		}

		return closed.contains(key);
	}
	private static int computeScaledPosition(Vec3 pos, int hashCode, double xScale, double yScale, double zScale) {
	    return new Vec3(
	        Math.round(pos.x * xScale),
	        Math.round(pos.y * yScale),
	        Math.round(pos.z * zScale)
	    ).hashCode() + hashCode;
	}
	
	private static double computeHeuristic(Vec3 position, Vec3 realTarget) {
	    return NodeCostCalculator.computeHeuristic(position, realTarget);
	}

	public static void updateNode(Level world, @NonNull Node child, Vec3 realTarget, List<BlockNode> blockPath, Set<Integer> closed) {
		NodeCostCalculator.updateNode(world, child, realTarget);
	}
	
	private static int findClosestPositionIDX(Level world, BlockPos current, List<BlockNode> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("The list of positions must not be null or empty.");
        }

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX.get();
        BlockNode currentNode = positions.get(closestIDX);
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.distToCenterSqr(closest.getPos(true, world))/* + Math.abs(closest.y - current.getY()) * 160*/;
        int maxLoop = positions.size();
        for (int i = closestIDX+1; i < maxLoop; i++) {
        	BlockNode position = positions.get(i);
//			if (i % 5 != 0) {
//        		continue;
//        	}
            double distance = current.distToCenterSqr(position.getPos(true, world))/* + Math.abs(position.y - current.getY()) * 160*/;
//            if ( distance < 1 && closestIDX < i-1) continue;
            if (distance < minDistance/* && (heightDiff <= 0 || isCurrentNodeLadder || isClosestNodeLadder)*/) {
                minDistance = distance;
                closest = position;
                closestIDX = i;
            }
		}
        return closestIDX;
    }
	
	private static boolean updateBestSoFar(Node child, Vec3 start, AtomicDoubleArray bestHeuristicSoFar) {
		boolean failing = true;
	    for (int i = 0; i < COEFFICIENTS.length; i++) {
	        double heuristic = child.estimatedCostToGoal + child.cost / COEFFICIENTS[i];
	        if (bestHeuristicSoFar.get(i) - heuristic > minimumImprovement) {
	            bestHeuristicSoFar.set(i, heuristic);
	            bestSoFar.set(i, child);
	            if (failing && getDistFromStartSq(child, start) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
	        }
	    }
	    return failing;
	}

	private static double getDistFromStartSq(Node n, Vec3 start) {
		double xDiff = start.x - n.agent.getPos().x;
		double yDiff = start.y - n.agent.getPos().y;
		double zDiff = start.z - n.agent.getPos().z;
		return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
	}


	private Node initializeStartNode(Node node, Vec3 target) {
        Node start = new Node(null,  AgentEntity.cloneFrom(node.agent, node.agent.snapshot()), new Color(255, 255, 255), 0);
		start.estimatedCostToGoal = computeHeuristic(start.agent.position(), TARGET);
		start.combinedCost = start.estimatedCostToGoal + start.cost;
        return start;
    }

	
	private Node initializeStartNode(LocalPlayer player, Vec3 target) {
        Node start = new Node(null, AgentSimulator.fromLocalPlayer(player).getAgent(), new Color(255, 255, 255), 0);
		start.estimatedCostToGoal = computeHeuristic(start.agent.position(), TARGET);
		start.combinedCost = start.estimatedCostToGoal + start.cost;
        return start;
    }

    private Optional<List<BlockNode>> findBlockPath(Level world, Vec3 target, LocalPlayer player) {
        return BlockSpacePathFinder.search(world, target, player);
    }
    
    private Optional<List<BlockNode>> findBlockPath(Level world, BlockNode start, Vec3 target, LocalPlayer player) {
        return BlockSpacePathFinder.search(world, start, target, player);
    }

	private void initializeBestHeuristics(Node start) {
		double h = computeHeuristic(start.agent.getPos(), TARGET);
		for (int i = 0; i < COEFFICIENTS.length; i++) {
			this.bestHeuristicSoFar.set(i, h);
			bestSoFar.set(i, start);
		}
	}
    
    private boolean isPathComplete(Node node, Vec3 target, boolean failing, Level world) {
    	if (BlockStateChecker.isAnyWater(world.getBlockState(new BlockPos((int) target.x(), (int) target.y(), (int) target.z()))))
    		return node.agent.getPos().distanceToSqr(target) <= 0.9D;
    	if (world.getBlockState(new BlockPos((int) target.x(), (int) target.y(), (int) target.z())).getBlock() instanceof LadderBlock)
    		return node.agent.getPos().distanceToSqr(target) <= 0.9D;
        return node.agent.getPos().distanceToSqr(target) <= 0.2D;
    }

    private boolean tryExecutePath(Node node, Vec3 target, double minVelocity) {
    	TungstenModRenderContainer.TEST.clear();
    	RenderHelper.renderPathSoFar(node);
//    	while (TungstenModDataContainer.EXECUTOR.isRunning()) {
//    		try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//    	}




        if (AgentChecker.isAgentStationary(node.agent, minVelocity) ||
        		TungstenModDataContainer.world.getBlockState(new BlockPos((int) target.x(), (int) target.y(), (int) target.z())).getBlock() instanceof LadderBlock) {

			List<Node> path = constructPath(node);
            executePath(path);
            return true;
        }
        return false;
    }

    private List<Node> constructPath(Node node) {
        List<Node> path = new ArrayList<>();
        TungstenModRenderContainer.RUNNING_PATH_RENDERER.clear();
        while (node.parent != null) {
            path.add(node);
//            RenderHelper.renderNodeConnection(node, node.parent);
            node = node.parent;
        }
        path.add(node);
        Collections.reverse(path);
        return path;
    }

    private void executePath(List<Node> path) {
        TungstenModDataContainer.EXECUTOR.cb = () -> {
            Debug.logMessage("Finished!");
            RenderHelper.clearRenderers();
        };
        if (TungstenModDataContainer.EXECUTOR.isRunning()) {
            TungstenModDataContainer.EXECUTOR.addPath(path);
            TungstenModDataContainer.EXECUTOR.blockPath = PathFinder.blockPath.orElseGet(null);
        } else {        	
        	TungstenModDataContainer.EXECUTOR.setPath(path);
            TungstenModDataContainer.EXECUTOR.blockPath = PathFinder.blockPath.orElseGet(null);
        }
		long endTime = System.currentTimeMillis();
		long elapsedTime = endTime - startTime;
		long minutes = (elapsedTime / 1000) / 60;
        long seconds = (elapsedTime / 1000) % 60;
        long milliseconds = elapsedTime % 1000;
        
        Debug.logMessage("Time taken to find path: " + minutes + " minutes, " + seconds + " seconds, " + milliseconds + " milliseconds");
    }

    private boolean shouldResetSearch(int numNodesConsidered, Optional<List<BlockNode>> blockPath, Node next, Vec3 target) {
        return (numNodesConsidered & (8 - 1)) == 0 &&
				PathFinder.blockPath.isPresent() &&
               	NEXT_CLOSEST_BLOCKNODE_IDX.get() > PathFinder.blockPath.get().size() - 10 &&
			   	!TungstenModDataContainer.EXECUTOR.isRunning() &&
				PathFinder.blockPath.get().getLast().getPos().distanceToSqr(next.agent.getPos()) < 3.0D &&
				PathFinder.blockPath.get().getLast().getPos().distanceToSqr(target) > 1.0D;// &&
//             	AgentChecker.isAgentStationary(next.agent, 0.08);
    }

    private Optional<List<BlockNode>> resetSearch(Node next, Level world, Optional<List<BlockNode>> blockPath, Vec3 target, LocalPlayer player) {
    	BlockNode lastNode = PathFinder.blockPath.get().getLast();
    	lastNode.previous = null;
       PathFinder.blockPath = findBlockPath(world, lastNode, target, player);
        if (PathFinder.blockPath.isPresent()) {
            List<Node> path = constructPath(next);
            TungstenModDataContainer.EXECUTOR.setPath(path);
            TungstenModDataContainer.EXECUTOR.blockPath =PathFinder.blockPath.orElseGet(null);
            NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
        	RenderHelper.renderBlockPath(PathFinder.blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
        	return PathFinder.blockPath;
        }
        Debug.logWarning("Failed!");
        stop.set(true);
        return Optional.empty();
    }

    private boolean handleTimeout(long primaryTimeoutTime, Vec3 target, Node start, LocalPlayer player, Set<Long> closed) {
        long now = System.currentTimeMillis();
        if (now < primaryTimeoutTime) return false;
        Optional<List<Node>> result = PathFinder.bestSoFar(true, 0, start, TungstenModDataContainer.PATHFINDER.TARGET);


		  if (result.isEmpty() // || result.get().size() < 46
//				  || !(result.get().getLast().agent.onGround && result.get().getLast().agent.touchingWater)
				  || result.get().getLast().agent.onClimbable()
				  || result.get().getLast().agent.getPos().distanceTo(result.get().getFirst().agent.getPos()) < 2.5
		  ) {
			  return false;
		  }
//        if (player.getPos().distanceTo(result.get().getFirst().agent.getPos()) < 1 && next.agent.getPos().distanceTo(target) > 1) {
	    if (setCurrentPath(target, start, player)) {
	    	Debug.logMessage("Time ran out!");
			if (closed.size() > 5e3) closed.clear();
		    return true;
	    }
//        }
        return false;
    }

	private boolean setCurrentPath(Vec3 target, Node start, LocalPlayer player) {
		Optional<List<Node>> result = PathFinder.bestSoFar(true, 0, start, TungstenModDataContainer.PATHFINDER.TARGET);
		if (result.isEmpty()) {
			return false;
		}
		this.sim = new AgentSimulator(result.get().getLast().agent);
		Node newStart = TungstenModDataContainer.PATHFINDER.initializeStartNode(result.get().getLast(), target);
		if (newStart == null) return false;

		if (!newStart.agent.onGround() && !newStart.agent.isInWater() && !newStart.agent.onClimbable()) return false;
//		if (PathFinder.blockPath.isEmpty()
//				|| Math.abs(newStart.agent.blockPosition().getY()
//				- PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getBlockPos().getY()) > 1) {
//			return false;
//		}

		// HAND THE PATH TO THE EXECUTOR — it walks this while the search plans the next leg.
		TungstenModDataContainer.EXECUTOR.addPath(result.get());
		TungstenModDataContainer.EXECUTOR.blockPath = PathFinder.blockPath.orElseGet(null);

		// RE-ROOT THE SEARCH.
		//
		//  (a) At the END of the path just emitted (newStart). The next emission chains
		//      onto this one, so the executor walks P1 then P2. Correct IF the executor
		//      queues rather than replaces.
		//
		//  (b) At the AGENT'S ACTUAL POSITION. Correct if the executor replaces the running
		//      path, or if you want the search to always describe where the player really
		//      is. Costs a re-plan of the part already walked.
		Node root = newStart;

		TungstenModDataContainer.PATHFINDER.closed.clear();
		TungstenModDataContainer.PATHFINDER.initializeBestHeuristics(root);
		TungstenModDataContainer.PATHFINDER.openSet = new BinaryHeapOpenSet();
		TungstenModDataContainer.PATHFINDER.openSet.insert(root);
		TungstenModDataContainer.PATHFINDER.start = root;
		numNodesConsidered.set(0);
		if (PathFinder.blockPath.isPresent() && NEXT_CLOSEST_BLOCKNODE_IDX.get() == (PathFinder.blockPath.get().size()-1) && newStart.agent.getPos().distanceTo(PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getPos(true)) < 6) {
			while (TungstenModDataContainer.EXECUTOR.isRunning() && TungstenModDataContainer.EXECUTOR.getPercentComplete() < 65) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
			PathFinder.blockPath = BlockSpacePathFinder.search(TungstenModDataContainer.world, new BlockNode(newStart.agent.blockPosition(), new Goal((int) target.x, (int) target.y, (int) target.z), player, TungstenModDataContainer.world), target, player);
			PathFinder.blockPath.ifPresent(blockNodes -> RenderHelper.renderBlockPath(blockNodes, 1));
			NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
		}
		return true;
	}

    private boolean filterChildren(Node child, BlockNode lastBlockNode, BlockNode nextBlockNode, boolean isSmallBlock, Level world) {
//    	boolean isLadder = nextBlockNode.getBlockState(world).getBlock() instanceof LadderBlock;
//    	boolean isLadderBelow = world.getBlockState(nextBlockNode.getBlockPos().below()).getBlock() instanceof LadderBlock;
//    	if (isLadder || isLadderBelow) return child.agent.getPos().y() < (nextBlockNode.getPos(true).y() - 3.6);
////    	double distB = DistanceCalculator.getHorizontalEuclideanDistance(lastBlockNode.getPos(true), nextBlockNode.getPos(true));
//
////    	if (distB > 6 || child.agent.isClimbing(TungstenModDataContainer.world)) return  child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 0.8);
//
////    	if (nextBlockNode.isDoingNeo())
////    		return child.agent.getBlockPos().getY() != nextBlockNode.getBlockPos().getY();
//
//    	if (nextBlockNode.isDoingLongJump(world)) return child.agent.blockPosition().getY() < nextBlockNode.getBlockPos().getY()-1;
//
//    	if (isSmallBlock) return child.agent.getPos().y() < (nextBlockNode.getPos(true).y()-1);
//

        return shouldSkipNode(child, TARGET, world);
    }

    private boolean processNodeChildren(Level world, Node parent, Vec3 target, Vec3 start, Optional<List<BlockNode>> blockPath,
										BinaryHeapOpenSet openSet, Set<Long> closed) {
//		long startTime2 = System.currentTimeMillis();
			AtomicBoolean failing = new AtomicBoolean(true);
			if (PathFinder.blockPath.isEmpty()) return false;
			List<Node> children = parent.getChildren(world, target,PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()), openSet.size() < 4);
			if (children.isEmpty()) return false;
			
//			Debug.logMessage("All children");
//			for (Node node : children) {
//				if (stop.get()) return false;
//		    	if (Thread.currentThread().isInterrupted()) return false;
//		        RenderHelper.renderNode(node);
//			}
//			try {
//				Thread.sleep(500);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
			Queue<Node> validChildren = new ConcurrentLinkedQueue<>();

			BlockNode lastBlockNode =PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
			BlockNode nextBlockNode =PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
	        double closestBlockVolume = BlockShapeChecker.getShapeVolume(nextBlockNode.getBlockPos().below(), world);
	        boolean isSmallBlock = closestBlockVolume > 0 && closestBlockVolume < 1;
			
			List<Callable<Void>> tasks = new ArrayList<>();


//		List<Node> nodesC = children.stream().toList();
//		for (Node n : nodesC) {
//			if (TungstenModDataContainer.PATHFINDER.stop.get()) break;
//			RenderHelper.renderPathSoFar(n);
//			try {
//				Thread.sleep(50);
//			} catch (InterruptedException e) {
//				throw new RuntimeException(e);
//			}
//		}
			
			if (children.size() > 5) {
				Node[][] chunks = ArrayChunkSplitter.splitArrayIntoChunksOfX(children.toArray(new Node[0]), children.size()/5);

                for (Node[] nodes : chunks) {
                    tasks.add(() -> {
                        for (Node child : nodes) {
                            if (stop.get()) continue;
                            if (Thread.currentThread().isInterrupted()) continue;

                            // Check if this child is too close to any already accepted child
							mainLoop:
                            for (Node other : validChildren) {
                                if (Thread.currentThread().isInterrupted()) return null;
                                double distance = other.agent.getPos().distanceTo(child.agent.getPos());

                                boolean bothClimbing = other.agent.onClimbable() && child.agent.onClimbable();
                                boolean bothNotClimbing = !other.agent.onClimbable() && !child.agent.onClimbable();

                                if ((bothNotClimbing && distance < 0.03) || (bothClimbing && distance < 0.03) || (isSmallBlock && distance < 0.2)) {
                                    return null; // too close to existing child
                                }
                            }

                            boolean skip = filterChildren(child, lastBlockNode, nextBlockNode, isSmallBlock, world);

                            if (skip || child.fallDamage) {
								continue;
                            }

                            validChildren.add(child);
                        }
                        return null;
                    });
                }
				
			} else {
				tasks = children.stream().map(child -> (Callable<Void>) () -> {
					if (stop.get()) return null;
			    	if (Thread.currentThread().isInterrupted()) return null;
					
					// Check if this child is too close to any already accepted child
				    for (Node other : validChildren) {
				    	if (Thread.currentThread().isInterrupted()) return null;
				        double distance = other.agent.getPos().distanceTo(child.agent.getPos());

				        boolean bothClimbing = other.agent.onClimbable() && child.agent.onClimbable();
				        boolean bothNotClimbing = !other.agent.onClimbable() && !child.agent.onClimbable();

				        if ((bothNotClimbing && distance < 0.03) || (bothClimbing && distance < 0.03) || (isSmallBlock && distance < 0.2)) {
				            return null; // too close to existing child
				        }
				    }
					
					boolean skip = filterChildren(child, lastBlockNode, nextBlockNode, isSmallBlock, world);

					if (skip || child.fallDamage) {
						return null;
					}
					
					validChildren.add(child);
					return null;
				}).collect(Collectors.toList());
			}
			
//			for (Iterator iterator = tasks.iterator(); iterator.hasNext();) {
//				Callable<Void> callable = (Callable<Void>) iterator.next();
//				try {
//					callable.call();
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
			
			try {
				List<Future<Void>> futures = executor.invokeAll(tasks);
				
				for (Future<Void> future : futures) {
					if (!future.isDone()) {
						Thread.sleep(41);
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			List<Callable<Void>> processingTasks = new ArrayList<>();

			if (validChildren.size() > 25) {
				Node[][] chunks = ArrayChunkSplitter.splitArrayIntoChunksOfX(validChildren.toArray(new Node[0]), children.size()/25);

                for (Node[] nodes : chunks) {
					processingTasks.add(() -> {
                        for (Node child : nodes) {
                            if (stop.get()) return null;
                            if (Thread.currentThread().isInterrupted()) return null;
							NodeCostCalculator.applyHeuristic(world, child, nextBlockNode.getPos(true));
                            synchronized (openSet) {
                                if (child.isOpen()) {
                                    openSet.update(child);
                                } else {
                                    openSet.insert(child);
                                }
                            }

                            synchronized (bestHeuristicSoFar) {
								failing.set(updateBestSoFar(child, start, bestHeuristicSoFar));
                            }
                        }
                        return null;
                    });
                }

			} else {

				processingTasks = validChildren.stream()
				    .map(child -> (Callable<Void>) () -> {
						if (stop.get()) return null;
				    	if (Thread.currentThread().isInterrupted()) return null;
						NodeCostCalculator.applyHeuristic(world, child, nextBlockNode.getPos(true));
				        synchronized (openSet) {
				            if (child.isOpen()) {
				                openSet.update(child);
				            } else {
				                openSet.insert(child);
				            }
				        }

				        synchronized (bestHeuristicSoFar) {
							failing.set(updateBestSoFar(child, start, bestHeuristicSoFar));
				        }

				        // Optional: render node for debugging
//				         RenderHelper.renderNode(child);

				        return null;
				    })
				    .collect(Collectors.toList());

			}

//			for (Iterator iterator = processingTasks.iterator(); iterator.hasNext();) {
//				Callable<Void> callable = (Callable<Void>) iterator.next();
//				try {
//					callable.call();
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
			
		    try {
				List<Future<Void>> futures = executor.invokeAll(processingTasks);
				
				for (Future<Void> future : futures) {
					if (!future.isDone()) {
						Thread.sleep(81);
					}
				}
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			Debug.logMessage("Rendering all children to be considered");
//			Node lowestNode = null;
//			RenderHelper.clearRenderers();
//			RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
//			RenderHelper.renderPathSoFar(parent);
//			for (Node node : validChildren) {
//				if (stop.get()) return false;
//				if (Thread.currentThread().isInterrupted()) return false;
//				if (lowestNode == null || node.combinedCost < lowestNode.combinedCost) {
//					lowestNode = node;
////					Debug.logMessage(" " + node.combinedCost);
//					RenderHelper.renderNode(node);
////					try {
////						Thread.sleep(250);
////					} catch (InterruptedException e) {
////						// TODO Auto-generated catch block
////						e.printStackTrace();
////					}
//				}
//			}
//			try {
//				Thread.sleep(250);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			RenderHelper.clearRenderers();
//			RenderHelper.renderBlockPath(blockPath.get(), NEXT_CLOSEST_BLOCKNODE_IDX.get());
//			if (lowestNode != null) {
//				Debug.logMessage("Cheapest " + lowestNode.combinedCost);
//				RenderHelper.renderPathSoFar(lowestNode);
//			}
//			try {
//				Thread.sleep(500);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
				
//			for (Node child : validChildren) {
//				updateNode(world, parent, child, target, blockPath.get(), closed);
//				
//				if (child.isOpen()) {
//					openSet.update(child);
//				} else {
//					openSet.insert(child);
//				}
//				
//				// Update best so far
//				if (updateBestSoFar(child, bestHeuristicSoFar, target)) {
//					failing.set(false);
//				}
//				
//				// Optionally render or handle visual updates here
//				// RenderHelper.renderNode(child);
//			}
		    
//		    RenderHelper.clearRenderers();
//
//			Debug.logMessage("Valid children");
//			for (Node node : validChildren) {
//				if (stop.get()) return false;
//		    	if (Thread.currentThread().isInterrupted()) return false;
//		        RenderHelper.renderNode(node);
//			}
//			try {
//				Thread.sleep(20);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			long took = System.currentTimeMillis() - startTime2;
//			if (took > 10) {
//				Debug.logMessage(took + "ms");
//				Debug.logMessage("children: " + children.size() + " validChildren " + validChildren.size());
//				if (validChildren.isEmpty()) {
//					for (Node node : children) {
//						if (stop.get()) return false;
//						if (Thread.currentThread().isInterrupted()) return false;
//						RenderHelper.renderNode(node);
//					}
//					try {
//						Thread.sleep(1250);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
//				if (validChildren.size() > 10) {
//					for (Node node : children) {
//						if (stop.get()) return false;
//						if (Thread.currentThread().isInterrupted()) return false;
//						RenderHelper.renderNode(node);
//					}
//					try {
//						Thread.sleep(10250);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
//			}
			return failing.get();
		}
    
    private boolean updateNextClosestBlockNodeIDX(List<BlockNode> blockPath, Node node, Set<Long> closed, Level world) {
    	if (blockPath == null) return false;

		if (NEXT_CLOSEST_BLOCKNODE_IDX.get()+1 >= blockPath.size()) return false;
    	BlockNode lastClosestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
    	BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
    	BlockNode nextNodePos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()+1);

		if (closestPos.getPos(true).distanceTo(node.agent.position()) > 4) return false;

    	boolean isRunningLongDist = lastClosestPos.getPos(true).distanceTo(closestPos.getPos(true)) > 7;

    	Vec3 nodePos = node.agent.getPos();
//    	if (!node.agent.onGround && !node.agent.touchingWater && !node.agent.isClimbing(world)) return false;
    	
//    	boolean isNextNodeAbove = nextNodePos.getBlockPos().getY() > closestPos.getBlockPos().getY() && (nextNodePos.getBlockPos().getY() - closestPos.getBlockPos().getY()) > 1.5 && node.agent.onGround;
//    	boolean isNextNodeBelow = nextNodePos.getBlockPos().getY() < closestPos.getBlockPos().getY();
    	
    	BlockPos nodeBlockPos = new BlockPos(node.agent.getBlockX(), node.agent.getBlockY(), node.agent.getBlockZ());
    	int closestPosIDX = findClosestPositionIDX(world, nodeBlockPos, blockPath);
//    	BlockNode newClosestPos = blockPath.get(closestPosIDX);
        BlockState state = world.getBlockState(closestPos.getBlockPos());
        BlockState stateBelow = world.getBlockState(closestPos.getBlockPos().below());
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(closestPos.getBlockPos().below(), world);
        double closestBlockVolume = BlockShapeChecker.getShapeVolume(closestPos.getBlockPos(), world);
        double distanceToClosestPos = nodePos.distanceTo(closestPos.getPos(true));
        double heightDiff = closestPos.getJumpHeight(Math.ceil(nodePos.y), closestPos.y);

		if (heightDiff < 0 || heightDiff > 0.98) return false;

        boolean isWater = BlockStateChecker.isAnyWater(state);
        boolean isLadder = state.getBlock() instanceof LadderBlock;
        boolean isCarpet = state.getBlock() instanceof CarpetBlock;
        boolean isVine = state.getBlock() instanceof VineBlock;
        boolean isConnected = BlockStateChecker.isConnected(nodeBlockPos, world);
        boolean isBelowLadder = stateBelow.getBlock() instanceof LadderBlock;
        boolean isBottomSlab = BlockStateChecker.isBottomSlab(state);
        boolean isBelowClosedTrapDoor= BlockStateChecker.isClosedBottomTrapdoor(stateBelow);
        boolean isBelowGlassPane = (stateBelow.getBlock() instanceof StainedGlassPaneBlock) || (stateBelow.getBlock() instanceof StainedGlassPaneBlock);
        boolean isBlockBelowTall = closestBlockBelowHeight > 1.3;
        


//    	if (!isLadder && !isCarpet) {
//	    	if (closestPos.getPos(true).y - nodePos.y > 0.6 || !nodePos.isWithinRangeOf(closestPos.getPos(true), (isRunningLongDist ? 2.80 : 1.95), (isRunningLongDist ? 1.20 : 1.20)))  {
//	    		return false;
//	    	}
//
//	    	Node p = node.parent;
//	    	for (int i = 0; i < 4; i++) {
//	    		if (p != null && closestPos.getPos(true).y <= p.agent.getPos().y &&  !p.agent.getPos().isWithinRangeOf(closestPos.getPos(true), (isRunningLongDist ? 2.80 : 1.95), (isRunningLongDist ? 1.20 : 1.80))) return false;
//			}
//    	}
        
        boolean validWaterProximity = isWater && nodePos.closerThan(BlockPosShifter.getPosOnLadder(closestPos, world), 0.9, 1.8);
        // Agent state conditions
        boolean agentOnGroundOrClimbingOrOnTallBlock = node.agent.onGround() || node.agent.onClimbable() || isBelowLadder || isLadder || isBlockBelowTall;

        // Ladder-specific conditions
        boolean validLadderProximity = (isLadder || isBelowLadder || isVine) && nodePos.closerThan(BlockPosShifter.getPosOnLadder(closestPos, world), 1.95, 1.7);
        
        // Tall block position conditions. Things like fences and walls
        boolean validTallBlockProximity = isBlockBelowTall 
            && nodePos.closerThan(closestPos.getPos(true), 0.8, 0.58);

        boolean validBottomSlabProximity = isBottomSlab && distanceToClosestPos < 0.99
                && heightDiff < 2;
        
        
        boolean validClosedTrapDoorProximity = isBelowClosedTrapDoor && nodePos.closerThan(closestPos.getPos(true), 0.88, 2.2);
        
        boolean isBlockAboveSolid = BlockShapeChecker.getShapeVolume(nodeBlockPos.above(2), world) > 0;

		// Check for solid block above and distance constraints
		boolean solidBlockAboveCheck = isBlockAboveSolid && distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.85);

		// Check for no solid block above and nested conditions
		boolean noSolidBlockAboveCheck = !isBlockAboveSolid && (
				// Condition 1: Distance check with height difference constraints
				(distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.45) && heightDiff < 1.08 && heightDiff > 0)

				// Condition 2: On ground height checks
				|| (node.agent.onGround() && heightDiff < 1.0 && distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.45))

				// Condition 3: Carpet check with height tolerance
				|| (isCarpet && heightDiff < 1 && distanceToClosestPos < 2)
		);

		boolean validStandardProximity = solidBlockAboveCheck || noSolidBlockAboveCheck;

        // Glass pane conditions
        boolean validGlassPaneProximity = isBelowGlassPane && distanceToClosestPos < 0.5;
        
        // Block volume conditions
        boolean validSmallBlockProximity = !isBelowGlassPane && closestBlockVolume > 0 && closestBlockVolume < 1 && distanceToClosestPos < 0.7;
        
//        for (int j = 0; j < blockPath.size(); j++) {
//			if (j >= closestPosIDX) {
//	        	RenderHelper.renderBlockPath(blockPath, j);
//				try {
//					Thread.sleep(200);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//		}
		if (validStandardProximity) {

//			if (setCurrentPath(TARGET, this.start, TungstenModDataContainer.player)) {

			if (closestPosIDX+1 < blockPath.size()) {
				NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX+1);
				new Thread(() -> RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX.get())).start();
				closed.clear();
			}
			return true;
//			}
		}
        if (validLadderProximity) {
			if (closestPosIDX+1 < blockPath.size()) {
				NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX+1);
				closed.clear();
				return true;
			}
        }
    	if (closestPosIDX +1 < blockPath.size()
    			&&  heightDiff <= 1.4
    			&& ( validWaterProximity || !isConnected
//    			&& BlockNode.wasCleared(world, nodeBlockPos, blockPath.get(closestPosIDX+1).getBlockPos())
				&& agentOnGroundOrClimbingOrOnTallBlock
    			&& (
	    			validTallBlockProximity
		    		|| validGlassPaneProximity
		    		|| validSmallBlockProximity
		    		|| validBottomSlabProximity
		    		|| validClosedTrapDoorProximity
	    		)
//			    && (child.agent.getBlockPos().getY() == blockPath.get(closestPosIDX).getBlockPos().getY())
    			)
    			) {

                boolean isNeo = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingNeo();

    			if (!isNeo || setCurrentPath(TARGET, this.start, TungstenModDataContainer.player)) {
                    if (closestPosIDX+1 < blockPath.size()) {
                        NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX+1);
						new Thread(() -> RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX.get())).start();
                        closed.clear();
                    }
    				return true;
    			}
//	    		try {
//					Thread.sleep(150);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
    	}
    	return false;
    }

	public  AgentSimulator getSimCopyForAllOf(Node mainN) {
		if (mainN == null) return sim.copy();
		List<Node> path = constructPath(mainN);
		AgentSimulator simCopy = sim.copy();
		for (Node n : path) {
			simCopy.simulate(n.input);
		}

		return simCopy;
	}
	public  AgentSimulator getSimCopy() {
		return sim.copy();
	}

	private void maybeRender(Node next) {
		long now = System.currentTimeMillis();
		if (now < nextRenderAt ) return;
		nextRenderAt = now + 50; // 20 Hz
		executor.execute(() -> RenderHelper.renderPathSoFar(next));
	}
	
}
