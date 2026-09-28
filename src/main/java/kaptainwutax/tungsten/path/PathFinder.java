package kaptainwutax.tungsten.path;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.AtomicDoubleArray;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.helpers.*;
import kaptainwutax.tungsten.helpers.blockPath.BlockPosShifter;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.Goal;
import kaptainwutax.tungsten.path.calculators.BinaryHeapOpenSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class PathFinder {

	
	ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	public AtomicBoolean active = new AtomicBoolean(false);
	public AtomicBoolean stop = new AtomicBoolean(false);
	public Thread thread = null;
	private final Set<Integer> closed = Collections.synchronizedSet(new HashSet<>());
	private final AtomicDoubleArray bestHeuristicSoFar = new AtomicDoubleArray(COEFFICIENTS.length);
	private BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
	protected static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
	protected static final AtomicReferenceArray<Node> bestSoFar = new AtomicReferenceArray<Node>(COEFFICIENTS.length);
	private static final double minimumImprovement = -4.2e6;
	public static Optional<List<BlockNode>> blockPath = Optional.empty();
	protected static final double MIN_DIST_PATH = 1.8;
	public static AtomicInteger NEXT_CLOSEST_BLOCKNODE_IDX = new AtomicInteger(1);
	protected static AtomicInteger numNodesConsidered = new AtomicInteger(0);
	private static final long timeoutLimit = 550L;

	private long startTime;
	private Node start;

	public Vec3 TARGET = new Vec3(0.5D, 10.0D, 0.5D);

	synchronized public void find(Level world, Vec3 target, Player player) {
		find(world, target, player, Optional.empty());
	}

    synchronized public void find(Level world, Vec3 target, Player player, Optional<List<BlockNode>> blockPath) {

        if(active.get() || thread != null)return;
        active.set(true);
        stop.set(false);
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
	
	private boolean checkForFallDamage(Node n, Level world) {
		if (this.stop.get()) return false;
		if (TungstenModDataContainer.ignoreFallDamage) return false;
		if (BlockStateChecker.isAnyWater(world.getBlockState(n.agent.getBlockPos()))) return false;
		if (n.parent == null) return false;
		if (Thread.currentThread().isInterrupted()) return false;
		Node prev = null;
		do {
			if (Thread.currentThread().isInterrupted()) return false;
			if (stop.get()) break;
			if (prev == null) {
				prev = n.parent;
			} else {
				prev = prev.parent;
			}
			double currFallDist = DistanceCalculator.getJumpHeight(prev.agent.getPos().y, n.agent.getPos().y);
			if (currFallDist < -3 || prev.agent.isDamaged || n.agent.isDamaged) {
				return true;
			}
		} while (!prev.agent.onGround && !prev.agent.touchingWater);

		if (prev == null) return false;

        return DistanceCalculator.getJumpHeight(prev.agent.getPos().y, n.agent.getPos().y) < -3 || prev.agent.isDamaged || n.agent.isDamaged;
    }

	private void search(Level world, Vec3 target, Player player) {
		search(world, null, target, player);
	}

	private void search(Level world, Node start, Vec3 target, Player player) {
		search(world, start, target, player, 0);
	}

	private void search(Level world, Node start, Vec3 target, Player player, int failedAttempts) {
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
	
	        Node next = TungstenModDataContainer.PATHFINDER.openSet.removeLowest();
	        
            // Search for a path without fall damage
            if (checkForFallDamage(next, TungstenModDataContainer.world)) {
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

//	        if ((numNodesConsidered.get() & (timeCheckInterval - 1)) == 0) {
	            if (handleTimeout(startTime, primaryTimeoutTime, next, target, TungstenModDataContainer.PATHFINDER.start, player, closed)) {
	            	primaryTimeoutTime = System.currentTimeMillis() + timeoutLimit;
	                continue;
	            }
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
				new Thread(() -> RenderHelper.renderPathSoFar(next)).start();
	        }

	        failing = processNodeChildren(TungstenModDataContainer.world, next, target, TungstenModDataContainer.PATHFINDER.start.agent.getPos(), PathFinder.blockPath, TungstenModDataContainer.PATHFINDER.openSet, closed);
	        numNodesConsidered.set(numNodesConsidered.get()+1);
	        if (updateNextClosestBlockNodeIDX(PathFinder.blockPath.get(), next, closed, TungstenModDataContainer.world)) {
	        	primaryTimeoutTime = System.currentTimeMillis() + timeoutLimit;
				failedAttempts = 0;
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
			if (failedAttempts < 2 && TungstenModDataContainer.EXECUTOR.path != null) {
				RenderHelper.clearRenderers();
				closed.clear();
				PathFinder.blockPath = Optional.empty();
				Node lastNode = TungstenModDataContainer.EXECUTOR.path.getLast();

				search(world, lastNode, target, player, failedAttempts+1);
				return;
			}
			Debug.logMessage("Ran out of nodes!");
	    }
	    RenderHelper.clearRenderers();
		closed.clear();
		PathFinder.blockPath = Optional.empty();
	}
	protected static Optional<List<Node>> bestSoFar(boolean logInfo, int numNodes, Node startNode, Vec3d realTarget) {
        if (startNode == null) {
            return Optional.empty();
        }
        double bestDist = 0;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
			if (TungstenModDataContainer.PATHFINDER.stop.get()) break;
            if (bestSoFar.get(i) == null || bestSoFar.get(i).parent == null) {
                continue;
            }
            double dist = DistanceCalculator.getEuclideanDistance(startNode.agent.getPos(), bestSoFar.get(i).agent.getPos());
            if (dist > bestDist) {
                bestDist = dist;
            }
            if (bestDist > MIN_DIST_PATH * MIN_DIST_PATH) { // square the comparison since distFromStartSq is squared
//                if (logInfo) {
//                    if (COEFFICIENTS[i] >= 3) {
//                        System.out.println("Warning: cost coefficient is greater than three! Probably means that");
//                        System.out.println("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
//                        System.out.println("But I'm going to do it anyway, because yolo");
//                    }
//                    System.out.println("Path goes for " + Math.sqrt(dist) + " blocks");
//                }

                Node n = bestSoFar.get(i);
//                if (!n.agent.onGround && !n.agent.touchingWater && !n.agent.isClimbing(TungstenModDataContainer.world)) continue;
                List<Node> path = new ArrayList<>();
				while(n.parent != null) {
					if (TungstenModDataContainer.PATHFINDER.stop.get()) break;
					path.add(n);
					n = n.parent;
				}

				path.add(n);
				Collections.reverse(path);
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }
	
	private void clearParentsForBestSoFar(Node node) {
		for (int i = 0; i < COEFFICIENTS.length; i++) {
			bestSoFar.set(i, null);
		}
	}

	private boolean shouldSkipChild(Node child, Vec3d target, WorldView world) {
	    return child.agent.touchingWater && shouldSkipNode(child, target, world);
	}
	
	private boolean shouldSkipNode(Node node, Vec3d target, WorldView world) {
//	    BlockNode bN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
//	    BlockNode lBN = blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
//	    boolean isBottomSlab = BlockStateChecker.isBottomSlab(TungstenMod.mc.world.getBlockState(bN.getBlockPos().down()));
//	    Vec3d agentPos = node.agent.getPos();
//	    Vec3d parentAgentPos = node.parent == null ? null : node.parent.agent.getPos();
//	    if (!isBottomSlab && !node.agent.onGround && agentPos.y < bN.y && lBN != null && lBN.y <= bN.y && parentAgentPos != null && parentAgentPos.y > agentPos.y) {
//	    	return true;
//	    }
	    return shouldNodeBeSkipped(node, target, closed, true,
	       PathFinder.blockPath.isPresent()
                    && (
				PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingJump() ||
	           PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingLongJump(world) ||
	           PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingNeo() ||
	           PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get() - 1).isDoingCornerJump()
	        ),
	       PathFinder.blockPath.isPresent() && PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).isDoingNeo()
	    );
	}
	
	private static boolean shouldNodeBeSkipped(Node n, Vec3d target, Set<Integer> closed, boolean addToClosed, boolean isDoingLongJump, boolean shouldAddYaw) {

		int hashCode = n.hashCode(1, shouldAddYaw);
	    Vec3d agentPos = n.agent.getPos();
	    double distanceToTarget = agentPos.distanceTo(target);

	    // Determine scaling factors based on conditions
	    double xScale, yScale, zScale;
	    if (distanceToTarget < 1.0 /* || n.agent.isSubmergedInWater || n.agent.isClimbing(MinecraftClient.getInstance().world) */) {
	        xScale = 1e3;
	        yScale = 1e3;
	        zScale = 1e3;
	    } else if (isDoingLongJump) {
	        xScale = 10;
	        yScale = 1e2;
	        zScale = 10;
	    } else if (n.agent.isClimbing(TungstenModDataContainer.world)) {
	        xScale = 10;
	        yScale = 1e4;
	        zScale = 10;
	    } else if (n.agent.touchingWater) {
	        xScale = 1e3;
	        yScale = 1e2;
	        zScale = 1e3;
	    } else {
	        xScale = 1e2;
	        yScale = 1e2;
	        zScale = 1e2;
	    }

	    // Compute scaled position with hashCode offset
	    int nodeHash = computeScaledPosition(agentPos, hashCode, xScale, yScale, zScale);

	    // Check if the position is in the closed set
	    if (closed.contains(nodeHash)) {
//			RenderHelper.renderNode(n);
//            try {
//                Thread.sleep(2);
//            } catch (InterruptedException e) {
////                throw new RuntimeException(e);
//            }
            return true;
	    }

	    // Optionally add the position to the closed set
	    if (addToClosed) {
	        closed.add(nodeHash);
	    }

	    return false;
	}
	
	private static int computeScaledPosition(Vec3 pos, int hashCode, double xScale, double yScale, double zScale) {
	    return new Vec3d(
	        Math.round(pos.x * xScale),
	        Math.round(pos.y * yScale),
	        Math.round(pos.z * zScale)
	    ).hashCode() + hashCode;
	}
	
	private static double computeHeuristic(Vec3 position, boolean onGround, Vec3 target, Vec3 realTarget) {
	    return NodeCostCalculator.computeHeuristic(position, onGround, target, realTarget);
	}

	public static void updateNode(Level world, Node current, @NonNull Node child, Vec3 target, Vec3 realTarget, List<BlockNode> blockPath, Set<Integer> closed) {
		NodeCostCalculator.updateNode(world, child, target, realTarget);
	}
	
	private static int findClosestPositionIDX(Level world, BlockPos current, List<BlockNode> positions) {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("The list of positions must not be null or empty.");
        }

        int closestIDX = NEXT_CLOSEST_BLOCKNODE_IDX.get();
        BlockNode currentNode = positions.get(closestIDX);
        BlockNode closest = positions.get(closestIDX);
        double minDistance = current.getSquaredDistance(closest.getPos(true, world))/* + Math.abs(closest.y - current.getY()) * 160*/;
        int maxLoop = positions.size();
        for (int i = closestIDX+1; i < maxLoop; i++) {
        	BlockNode position = positions.get(i);
//			if (i % 5 != 0) {
//        		continue;
//        	}
            double distance = current.getSquaredDistance(position.getPos(true, world))/* + Math.abs(position.y - current.getY()) * 160*/;
//            if ( distance < 1 && closestIDX < i-1) continue;
            if (distance < minDistance/* && (heightDiff <= 0 || isCurrentNodeLadder || isClosestNodeLadder)*/) {
                minDistance = distance;
                closest = position;
                closestIDX = i;
            }
		}
        return closestIDX;
    }
	
	private static boolean updateBestSoFar(Node child, Vec3d start, AtomicDoubleArray bestHeuristicSoFar) {
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

	private static double getDistFromStartSq(Node n, Vec3d start) {
		double xDiff = start.x - n.agent.getPos().x;
		double yDiff = start.x - n.agent.getPos().y;
		double zDiff = start.x - n.agent.getPos().z;
		return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
	}


	private Node initializeStartNode(Node node, Vec3 target) {
        Node start = new Node(null,  Agent.of(node.agent, node.agent.input.toPathInput()), new Color(255, 255, 255), 0);
        start.agent.tick(TungstenModDataContainer.world);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target, TARGET);
        return start;
    }

	
	private Node initializeStartNode(PlayerEntity player, Vec3d target) {
        Node start = new Node(null, Agent.of(player), new Color(255, 255, 255), 0);
        start.combinedCost = computeHeuristic(start.agent.getPos(), start.agent.onGround, target, TARGET);
        return start;
    }

    private Optional<List<BlockNode>> findBlockPath(WorldView world, Vec3d target, PlayerEntity player) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, target, player);
    }
    
    private Optional<List<BlockNode>> findBlockPath(WorldView world, BlockNode start, Vec3d target, PlayerEntity player) {
        return kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(world, start, target, player);
    }

    private void initializeBestHeuristics(Node start) {
    	AtomicDoubleArray bestHeuristicSoFar = new AtomicDoubleArray(COEFFICIENTS.length);
        for (int i = 0; i < bestHeuristicSoFar.length(); i++) {
            bestHeuristicSoFar.set(i, start.combinedCost / COEFFICIENTS[i]);
            bestSoFar.set(i, start);
			this.bestHeuristicSoFar.set(i, start.combinedCost / COEFFICIENTS[i]);
        }
	}
    
    private boolean isPathComplete(Node node, Vec3d target, boolean failing, WorldView world) {
    	if (BlockStateChecker.isAnyWater(world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ()))))
    		return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
    	if (world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ())).getBlock() instanceof LadderBlock)
    		return node.agent.getPos().squaredDistanceTo(target) <= 0.9D;
        return node.agent.getPos().squaredDistanceTo(target) <= 0.2D;
    }

    private boolean tryExecutePath(Node node, Vec3d target, double minVelocity) {
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
        		TungstenModDataContainer.world.getBlockState(new BlockPos((int) target.getX(), (int) target.getY(), (int) target.getZ())).getBlock() instanceof LadderBlock) {
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
            RenderHelper.renderNodeConnection(node, node.parent);
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
            TungstenModDataContainer.EXECUTOR.blockPath =PathFinder.blockPath.orElseGet(null);
        } else {        	
        	TungstenModDataContainer.EXECUTOR.setPath(path);
            TungstenModDataContainer.EXECUTOR.blockPath =PathFinder.blockPath.orElseGet(null);
        }
		long endTime = System.currentTimeMillis();
		long elapsedTime = endTime - startTime;
		long minutes = (elapsedTime / 1000) / 60;
        long seconds = (elapsedTime / 1000) % 60;
        long milliseconds = elapsedTime % 1000;
        
        Debug.logMessage("Time taken to find path: " + minutes + " minutes, " + seconds + " seconds, " + milliseconds + " milliseconds");
    }

    private boolean shouldResetSearch(int numNodesConsidered, Optional<List<BlockNode>> blockPath, Node next, Vec3d target) {
        return (numNodesConsidered & (8 - 1)) == 0 &&
				PathFinder.blockPath.isPresent() &&
               	NEXT_CLOSEST_BLOCKNODE_IDX.get() > PathFinder.blockPath.get().size() - 10 &&
			   	!TungstenModDataContainer.EXECUTOR.isRunning() &&
				PathFinder.blockPath.get().getLast().getPos().squaredDistanceTo(next.agent.getPos()) < 3.0D &&
				PathFinder.blockPath.get().getLast().getPos().squaredDistanceTo(target) > 1.0D;// &&
//             	AgentChecker.isAgentStationary(next.agent, 0.08);
    }

    private Optional<List<BlockNode>> resetSearch(Node next, WorldView world, Optional<List<BlockNode>> blockPath, Vec3d target, PlayerEntity player) {
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

    private boolean handleTimeout(long startTime, long primaryTimeoutTime, Node next, Vec3d target, Node start, PlayerEntity player, Set<Integer> closed) {
        long now = System.currentTimeMillis();
        if (now < primaryTimeoutTime) return false;
        Optional<List<Node>> result = PathFinder.bestSoFar(true, 0, start, TungstenModDataContainer.PATHFINDER.TARGET);

		  if (result.isEmpty() // || result.get().size() < 46
//				  || !(result.get().getLast().agent.onGround && result.get().getLast().agent.touchingWater)
				  || result.get().getLast().agent.isClimbing(TungstenModDataContainer.world)
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
    
    private static boolean setCurrentPath(Vec3d target, Node start, PlayerEntity player) {
        Optional<List<Node>> result = PathFinder.bestSoFar(true, 0, start, TungstenModDataContainer.PATHFINDER.TARGET);

        if (result.isEmpty()) {
            return false;
        }
        Node newStart = null;
        if (result.get().getLast() != null) {
        	newStart = TungstenModDataContainer.PATHFINDER.initializeStartNode(result.get().getLast(), target);
        } else if (result.get().get(result.get().size()-2) != null) {
        	newStart = TungstenModDataContainer.PATHFINDER.initializeStartNode(result.get().get(result.get().size()-2), target);
        }
        if (newStart == null || !newStart.agent.onGround && !newStart.agent.touchingWater && !newStart.agent.isClimbing(TungstenModDataContainer.world)) return false;
		if (PathFinder.blockPath.isEmpty()
				|| newStart.agent.getBlockPos().getY() != PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getBlockPos().getY()) {
			return false;
		}
//        if (TungstenModDataContainer.EXECUTOR.getPath() != null && TungstenModDataContainer.EXECUTOR.getPath().getLast().hashCode(1, true) == result.get().getLast().hashCode(1, true)) return false;
//        if (TungstenModDataContainer.EXECUTOR.getPath() != null && TungstenModDataContainer.EXECUTOR.getPath().getFirst().hashCode(1, true) == result.get().getFirst().hashCode(1, true)) return false;
        TungstenModDataContainer.EXECUTOR.addPath(result.get());
        TungstenModDataContainer.EXECUTOR.blockPath =PathFinder.blockPath.orElseGet(null);
//        RenderHelper.renderPathCurrentlyExecuted();
        for (int i = 0; i < COEFFICIENTS.length; i++) {
	        PathFinder.bestSoFar.set(i, null);
		}
        TungstenModDataContainer.PATHFINDER.clearParentsForBestSoFar(newStart);
        TungstenModDataContainer.PATHFINDER.closed.clear();
        TungstenModDataContainer.PATHFINDER.initializeBestHeuristics(newStart);
        TungstenModDataContainer.PATHFINDER.openSet = new BinaryHeapOpenSet();
        TungstenModDataContainer.PATHFINDER.openSet.insert(newStart);
        TungstenModDataContainer.PATHFINDER.start = newStart;
        numNodesConsidered.set(0);
		if (PathFinder.blockPath.isPresent() && NEXT_CLOSEST_BLOCKNODE_IDX.get() == (PathFinder.blockPath.get().size()-1) && newStart.agent.getPos().distanceTo(PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getPos(true)) < 6) {
			while (TungstenModDataContainer.EXECUTOR.isRunning() && TungstenModDataContainer.EXECUTOR.getPercentComplete() < 65) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
			PathFinder.blockPath = kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder.search(TungstenModDataContainer.world, new BlockNode(newStart.agent.getBlockPos(), new Goal((int) target.x, (int) target.y, (int) target.z), player, TungstenModDataContainer.world), target, player);
            PathFinder.blockPath.ifPresent(blockNodes -> RenderHelper.renderBlockPath(blockNodes, 1));
			NEXT_CLOSEST_BLOCKNODE_IDX.set(1);
		}
//        try {
//			Thread.sleep(150);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//        RenderHelper.clearRenderers();
//        Node finalNewStart = newStart;
//        (new Runnable() {
//			
//			@Override
//			public void run() {
//				// TODO Auto-generated method stub
//		        TungstenModDataContainer.PATHFINDER.search(TungstenModDataContainer.world, finalNewStart, target, player);
//				
//			}
//		}).run();
        return true;
    }
    
    private boolean filterChildren(Node child, BlockNode lastBlockNode, BlockNode nextBlockNode, boolean isSmallBlock, WorldView world) {
    	boolean isLadder = nextBlockNode.getBlockState(world).getBlock() instanceof LadderBlock;
    	boolean isLadderBelow = world.getBlockState(nextBlockNode.getBlockPos().down()).getBlock() instanceof LadderBlock;
    	if (isLadder || isLadderBelow) return child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 3.6);
//    	double distB = DistanceCalculator.getHorizontalEuclideanDistance(lastBlockNode.getPos(true), nextBlockNode.getPos(true));
    	
//    	if (distB > 6 || child.agent.isClimbing(TungstenModDataContainer.world)) return  child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY() - 0.8);

//    	if (nextBlockNode.isDoingNeo())
//    		return child.agent.getBlockPos().getY() != nextBlockNode.getBlockPos().getY();

    	if (nextBlockNode.isDoingLongJump(world)) return child.agent.getBlockPos().getY() < nextBlockNode.getBlockPos().getY()-1;

    	if (isSmallBlock) return child.agent.getPos().getY() < (nextBlockNode.getPos(true).getY()-1);


        return shouldSkipNode(child, TARGET, world);
    }

    private boolean processNodeChildren(WorldView world, Node parent, Vec3d target, Vec3d start, Optional<List<BlockNode>> blockPath,
            BinaryHeapOpenSet openSet, Set<Integer> closed) {
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
	        double closestBlockVolume = BlockShapeChecker.getShapeVolume(nextBlockNode.getBlockPos().down(), world);
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
                            if (stop.get()) return null;
                            if (Thread.currentThread().isInterrupted()) return null;

                            // Check if this child is too close to any already accepted child
                            for (Node other : validChildren) {
                                if (Thread.currentThread().isInterrupted()) return null;
                                double distance = other.agent.getPos().distanceTo(child.agent.getPos());

                                boolean bothClimbing = other.agent.isClimbing(world) && child.agent.isClimbing(world);
                                boolean bothNotClimbing = !other.agent.isClimbing(world) && !child.agent.isClimbing(world);

                                if ((bothNotClimbing && distance < 0.03) || (bothClimbing && distance < 0.03) || (isSmallBlock && distance < 0.2)) {
                                    return null; // too close to existing child
                                }
                            }

                            boolean skip = filterChildren(child, lastBlockNode, nextBlockNode, isSmallBlock, world);

                            if (skip || checkForFallDamage(child, world)) {
                                return null;
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

				        boolean bothClimbing = other.agent.isClimbing(world) && child.agent.isClimbing(world);
				        boolean bothNotClimbing = !other.agent.isClimbing(world) && !child.agent.isClimbing(world);

				        if ((bothNotClimbing && distance < 0.03) || (bothClimbing && distance < 0.03) || (isSmallBlock && distance < 0.2)) {
				            return null; // too close to existing child
				        }
				    }
					
					boolean skip = filterChildren(child, lastBlockNode, nextBlockNode, isSmallBlock, world);

					if (skip || checkForFallDamage(child, world)) {
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
						Thread.sleep(1);
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
                            updateNode(world, parent, child,PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getPos(true), TARGET,PathFinder.blockPath.get(), closed);

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
						updateNode(world, parent, child,PathFinder.blockPath.get().get(NEXT_CLOSEST_BLOCKNODE_IDX.get()).getPos(true), TARGET,PathFinder.blockPath.get(), closed);

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
						Thread.sleep(1);
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
    
    private boolean updateNextClosestBlockNodeIDX(List<BlockNode> blockPath, Node node, Set<Integer> closed, WorldView world) {
    	if (blockPath == null) return false;

    	if (NEXT_CLOSEST_BLOCKNODE_IDX.get()+1 >= blockPath.size()) return false;
    	BlockNode lastClosestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()-1);
    	BlockNode closestPos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get());
    	BlockNode nextNodePos = blockPath.get(NEXT_CLOSEST_BLOCKNODE_IDX.get()+1);
    	
    	boolean isRunningLongDist = lastClosestPos.getPos(true).distanceTo(closestPos.getPos(true)) > 7;

    	Vec3d nodePos = node.agent.getPos();
//    	if (!node.agent.onGround && !node.agent.touchingWater && !node.agent.isClimbing(world)) return false;
    	
//    	boolean isNextNodeAbove = nextNodePos.getBlockPos().getY() > closestPos.getBlockPos().getY() && (nextNodePos.getBlockPos().getY() - closestPos.getBlockPos().getY()) > 1.5 && node.agent.onGround;
//    	boolean isNextNodeBelow = nextNodePos.getBlockPos().getY() < closestPos.getBlockPos().getY();
    	
    	BlockPos nodeBlockPos = new BlockPos(node.agent.blockX, node.agent.blockY, node.agent.blockZ);
    	int closestPosIDX = findClosestPositionIDX(world, nodeBlockPos, blockPath);
//    	BlockNode newClosestPos = blockPath.get(closestPosIDX);
        BlockState state = world.getBlockState(closestPos.getBlockPos());
        BlockState stateBelow = world.getBlockState(closestPos.getBlockPos().down());
        double closestBlockBelowHeight = BlockShapeChecker.getBlockHeight(closestPos.getBlockPos().down(), world);
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
        boolean isBelowGlassPane = (stateBelow.getBlock() instanceof PaneBlock) || (stateBelow.getBlock() instanceof StainedGlassPaneBlock);
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
        
        boolean validWaterProximity = isWater && nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos, world), 0.9, 1.8);
        // Agent state conditions
        boolean agentOnGroundOrClimbingOrOnTallBlock = node.agent.onGround || node.agent.isClimbing(world) || isBelowLadder || isLadder || isBlockBelowTall;

        // Ladder-specific conditions
        boolean validLadderProximity = (isLadder || isBelowLadder || isVine) && nodePos.isWithinRangeOf(BlockPosShifter.getPosOnLadder(closestPos, world), 1.95, 1.7);
        
        // Tall block position conditions. Things like fences and walls
        boolean validTallBlockProximity = isBlockBelowTall 
            && nodePos.isWithinRangeOf(closestPos.getPos(true), 0.8, 0.58);

        boolean validBottomSlabProximity = isBottomSlab && distanceToClosestPos < 0.99
                && heightDiff < 2;
        
        
        boolean validClosedTrapDoorProximity = isBelowClosedTrapDoor && nodePos.isWithinRangeOf(closestPos.getPos(true), 0.88, 2.2);
        
        boolean isBlockAboveSolid = BlockShapeChecker.getShapeVolume(nodeBlockPos.up(2), world) > 0;

		// Check for solid block above and distance constraints
		boolean solidBlockAboveCheck = isBlockAboveSolid && distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.85);

		// Check for no solid block above and nested conditions
		boolean noSolidBlockAboveCheck = !isBlockAboveSolid && (
				// Condition 1: Distance check with height difference constraints
				(distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.45) && heightDiff < 1.08 && heightDiff > 0)

				// Condition 2: On ground height checks
				|| (node.agent.onGround && heightDiff < 1.0 && distanceToClosestPos < (isRunningLongDist ? 1.80 : 1.45))

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
        	if (setCurrentPath(TARGET, this.start, TungstenModDataContainer.player)) {
                if (closestPosIDX+1 < blockPath.size()) {
                    NEXT_CLOSEST_BLOCKNODE_IDX.set(closestPosIDX+1);
					new Thread(() -> RenderHelper.renderBlockPath(blockPath, NEXT_CLOSEST_BLOCKNODE_IDX.get())).start();
                    closed.clear();
                }
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
	
}
