package kaptainwutax.tungsten.client.path;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.TungstenModRenderContainer;
import kaptainwutax.tungsten.client.helpers.render.RenderHelper;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockNode;
import kaptainwutax.tungsten.client.render.Color;
import kaptainwutax.tungsten.client.render.Cuboid;
import kaptainwutax.tungsten.client.sim.AgentEntity;
import kaptainwutax.tungsten.client.sim.AgentInput;
import kaptainwutax.tungsten.client.sim.AgentSimulator;
import kaptainwutax.tungsten.client.sim.AgentStatus;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class PathExecutor {

    protected List<Node> path;
    protected int tick = 0;
    protected boolean allowedFlying = false;
    public boolean stop = false;
    public Runnable cb = null;
    public long startTime;
    public List<BlockNode> blockPath = null;
    private boolean isClient;

    public PathExecutor(boolean isClient) {
    	this.isClient = isClient;
    	try {
    		this.startTime = System.currentTimeMillis();
			if (isClient)
	        	this.allowedFlying = TungstenClient.mc.player.getAbilities().flying;
		} catch (Exception e) {
			this.allowedFlying = true;
		}
	}

	public void setPath(List<Node> path) {
		this.cb = null;
		this.startTime = System.currentTimeMillis();
		if (isClient)
			this.allowedFlying = TungstenClient.mc.player.getAbilities().flying;
	    stop = false;

//		AgentSimulator sim = AgentSimulator.fromLocalPlayer(TungstenModDataContainer.player, TungstenModDataContainer.player.getDeltaMovement());
//		Node lastN = null;
//		for (int i = 0; i < path.size(); i++) {
//			Node n = path.get(i);
//            n.agentStatus = sim.simulate(n.input);
//			n.agent = sim.getAgent();
//			n.parent = lastN;
//			lastN = n;
//			path.set(i, n);
//		}

    	this.path = path;
    	this.tick = 0;
    	RenderHelper.renderPathCurrentlyExecuted();
	}
	
	public void addToPath(Node n) {
		this.path.add(n);
    	RenderHelper.renderPathCurrentlyExecuted();
	}
	
	public void addPath(List<Node> path) {
		if (stop) {
			setPath(path);
			return;
		}
		if (this.path == null) {
			setPath(path);
			return;
		}
		this.path.addAll(path);
    	RenderHelper.renderPathCurrentlyExecuted();
	}
	
	public List<Node> getPath() {
		return this.path;
	}

	public float getPercentComplete() {
		return (float) this.tick / this.path.size() * 100;
	}
	
	public Node getCurrentNode() {
		if (this.path == null) return null;
		if (this.tick >= this.path.size()) return this.path.get(this.path.size()-1);
		return this.path.get(this.tick);
	}
	

	public int getCurrentTick() {
		return this.tick;
	}


	public boolean isRunning() {
        return this.path != null && this.tick <= this.path.size();
    }


//    public void tick(Player player) {
//    	player.getAbilities().flying = false;
//    	if(stop) {
//    		this.tick = this.path.size();
//    		player.setPlayerInput(AgentInput.NONE);
//		    player.getAbilities().flying = allowedFlying;
//		    this.path = null;
//		    stop = false;
//    		return;
//    	}
//    	if(this.tick == this.path.size()) {
//    		long endTime = System.currentTimeMillis();
//    		long elapsedTime = endTime - startTime;
//    		long minutes = (elapsedTime / 1000) / 60;
//            long seconds = (elapsedTime / 1000) % 60;
//            long milliseconds = elapsedTime % 1000;
//
//            Debug.logMessage("Time taken to execute: " + minutes + " minutes, " + seconds + " seconds, " + milliseconds + " milliseconds");
//
//    		player.setPlayerInput(AgentInput.NONE);
//		    player.getAbilities().flying = allowedFlying;
//		    this.path = null;
//		    stop = false;
//			player.setVelocity(0, 0, 0);
//		    if (cb != null) {
//		    	cb.run();
//		    	cb = null;
//		    }
//	    } else {
//		    Node node = this.path.get(this.tick);
//
//		    if(this.tick != 0) {
//			    this.path.get(this.tick - 1).agent.compare(player, player.getPlayerInput(), true);
//		    }
//
//		    if(node.input != null) {
//			    player.setYaw(node.input.yaw);
//			    player.setPitch(node.input.pitch);
//			    if (player.isCreative()) player.stopGliding();
//
//	    		player.setPlayerInput(node.input.getPlayerInput());
//		    }
//	    }
//	    this.tick++;
//    }
    
    public void tick(LocalPlayer player, Options options) {
    	player.getAbilities().flying = false;
    	if(TungstenClient.pauseKeyBinding.isDown() || stop) {
    		this.tick = this.path.size();
    		player.input.keyPresses = AgentInput.NONE.toInput();
			options.keyUp.setDown(false);
			options.keyDown.setDown(false);
			options.keyLeft.setDown(false);
			options.keyRight.setDown(false);
			options.keyJump.setDown(false);
			options.keyShift.setDown(false);
			options.keySprint.setDown(false);
		    player.getAbilities().flying = allowedFlying;
		    this.path = null;
		    stop = false;
		    TungstenModRenderContainer.RUNNING_PATH_RENDERER.clear();
		    TungstenModRenderContainer.BLOCK_PATH_RENDERER.clear();
    		return;
    	}
    	if(this.tick == this.path.size()) {
    		long endTime = System.currentTimeMillis();
    		long elapsedTime = endTime - startTime;
    		long minutes = (elapsedTime / 1000) / 60;
            long seconds = (elapsedTime / 1000) % 60;
            long milliseconds = elapsedTime % 1000;
            
            Debug.logMessage("Time taken to execute: " + minutes + " minutes, " + seconds + " seconds, " + milliseconds + " milliseconds");
    		
		    options.keyUp.setDown(false);
		    options.keyDown.setDown(false);
		    options.keyLeft.setDown(false);
		    options.keyRight.setDown(false);
		    options.keyJump.setDown(false);
		    options.keyShift.setDown(false);
		    options.keySprint.setDown(false);
		    player.getAbilities().flying = allowedFlying;
		    this.path = null;
		    stop = false;
		    TungstenModRenderContainer.RUNNING_PATH_RENDERER.clear();
		    TungstenModRenderContainer.BLOCK_PATH_RENDERER.clear();
//			player.setVelocity(0, 0, 0);
		    if (cb != null) {
		    	cb.run();
		    	cb = null;
		    }
	    } else {
		    Node node = this.path.get(this.tick);

	    		options.keyUp.setDown(node.agent.input.keyPresses.forward());
			    options.keyDown.setDown(node.agent.input.keyPresses.backward());
			    options.keyLeft.setDown(node.agent.input.keyPresses.left());
			    options.keyRight.setDown(node.agent.input.keyPresses.right());
			    options.keyJump.setDown(node.agent.input.keyPresses.jump());
			    options.keyShift.setDown(node.agent.input.keyPresses.shift());
			    options.keySprint.setDown(node.agent.input.keyPresses.sprint());
				player.setSprinting(node.agent.isSprinting());
				player.setXRot(node.agent.getXRot());
				player.setYRot(node.agent.getYRot());


//		    if(this.tick != 0 && options != null) {
//			    this.path.get(this.tick - 1).agent.compare(player, optionsToPlayerInput(options), true);
//		    }
		    int idx = TungstenModRenderContainer.RUNNING_PATH_RENDERER.size()-1;
		    if (!TungstenModRenderContainer.RUNNING_PATH_RENDERER.isEmpty() && this.tick != 0) {
		    	try {
			    	TungstenModRenderContainer.RUNNING_PATH_RENDERER.remove(TungstenModRenderContainer.RUNNING_PATH_RENDERER.toArray()[idx]);
			    	if (TungstenClient.renderPositonBoxes && TungstenModRenderContainer.RUNNING_PATH_RENDERER.size() > 1) {
			    		TungstenModRenderContainer.RUNNING_PATH_RENDERER.remove(TungstenModRenderContainer.RUNNING_PATH_RENDERER.toArray()[idx-1]);
			    	}
				} catch (Exception e) {
					// TODO: handle exception
				}
		    }
				AgentSimulator sim = AgentSimulator.fromLocalPlayer(player, player.getDeltaMovement());
				TungstenModRenderContainer.ERROR.clear();
				RenderHelper.clearRenderers();
				double size = 0.01D;
				for (int i = this.tick; i < Math.min(this.tick+8, this.path.size()); i++) {
					Node n = this.path.get(i);
					AgentStatus agentStatus = sim.simulate(n.input);

					if (!n.agentStatus.position.equals(agentStatus.position)) {
						RenderHelper.renderNode(n, TungstenModRenderContainer.ERROR);
						TungstenModRenderContainer.ERROR.add(
								new Cuboid(
										agentStatus.position.subtract(size/2, size/2-0.02D, size/2), new Vec3(size, size, size),
										Color.RED
								)
						);
						stop = true;

						while (!this.blockPath.isEmpty() && !this.blockPath.getFirst().getPos().closerThan(TungstenModDataContainer.player.position(),5)) {
							this.blockPath.removeFirst();
						}
						new Thread(() -> {

							while (TungstenModDataContainer.PATHFINDER.active.get()) {
								if (!TungstenModDataContainer.PATHFINDER.stop.get()) {
									TungstenModDataContainer.PATHFINDER.stop.set(true);
								}
								try {
									Thread.sleep(2);
								} catch (InterruptedException e) {
//                                    throw new RuntimeException(e);
								}
							}
							TungstenModDataContainer.PATHFINDER.find(TungstenModDataContainer.world, TungstenClient.TARGET, TungstenModDataContainer.player, Optional.of(this.blockPath));

						}).start();

					}

				}
	    }
	    this.tick++;
    }
    
    
//    public static AgentInput optionsToPlayerInput(GameOptions options) {
//    	return new AgentInput(options.forwardKey.isPressed(), options.backKey.isPressed(), options.leftKey.isPressed(), options.rightKey.isPressed(), options.jumpKey.isPressed(), options.sneakKey.isPressed(), options.sprintKey.isPressed());
//    }

}
