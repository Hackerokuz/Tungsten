package kaptainwutax.tungsten.path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;

import java.util.List;

import kaptainwutax.tungsten.TungstenMod;

public class PathExecutor {

    protected List<Node> path;
    protected int tick = 0;
    protected boolean allowedFlying = false;

    public PathExecutor() {
    	try {
        	this.allowedFlying = MinecraftClient.getInstance().player.getAbilities().allowFlying;
		} catch (Exception e) {
			this.allowedFlying = true;
		}
	}

	public void setPath(List<Node> path) {
    	this.path = path;
    	this.tick = 0;
	}

	public boolean isRunning() {
        return this.path != null && this.tick <= this.path.size();
    }

    public void tick(ClientPlayerEntity player, GameOptions options) {
    	player.getAbilities().allowFlying = false;
    	if(TungstenMod.pauseKeyBinding.isPressed()) {
    		this.tick = this.path.size();
		    options.forwardKey.setPressed(false);
		    options.backKey.setPressed(false);
		    options.leftKey.setPressed(false);
		    options.rightKey.setPressed(false);
		    options.jumpKey.setPressed(false);
		    options.sneakKey.setPressed(false);
		    options.sprintKey.setPressed(false);
		    player.getAbilities().allowFlying = true;
    		return;
    	}
    	if(this.tick == this.path.size()) {
		    options.forwardKey.setPressed(false);
		    options.backKey.setPressed(false);
		    options.leftKey.setPressed(false);
		    options.rightKey.setPressed(false);
		    options.jumpKey.setPressed(false);
		    options.sneakKey.setPressed(false);
		    options.sprintKey.setPressed(false);
		    player.getAbilities().allowFlying = true;
	    } else {
		    Node node = this.path.get(this.tick);

		    if(this.tick != 0) {
			    this.path.get(this.tick - 1).agent.compare(player, true);
		    }

		    if(node.input != null) {
			    if (player.isCreative()) player.stopFallFlying();
			    options.forwardKey.setPressed(node.input.forward);
			    options.backKey.setPressed(node.input.back);
			    options.leftKey.setPressed(node.input.left);
			    options.rightKey.setPressed(node.input.right);
			    options.jumpKey.setPressed(node.input.jump);
			    options.sneakKey.setPressed(node.input.sneak);
			    options.sprintKey.setPressed(node.input.sprint);
			    player.prevYaw = player.getYaw();
			    player.prevPitch = player.getPitch();
			    player.setYaw(node.input.yaw);
			    player.setPitch(node.input.pitch);
		    }
	    }

	    this.tick++;
    }

}
