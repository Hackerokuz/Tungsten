package kaptainwutax.tungsten.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.blockSpaceSearchAssist.BlockSpacePathFinder;

@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayer {

	public MixinClientPlayerEntity(ClientLevel world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void start(CallbackInfo ci) {
		if(TungstenModDataContainer.EXECUTOR.isRunning()) {
			TungstenModDataContainer.EXECUTOR.tick((LocalPlayer)(Object)this, Minecraft.getInstance().options);
		}

		if(!this.getAbilities().flying) {
			Agent.INSTANCE = Agent.of((LocalPlayer)(Object)this, Minecraft.getInstance().options);
			Agent.INSTANCE.tick(this.level());
		}

		if(Tungsten.runKeyBinding.isPressed() && !TungstenModDataContainer.PATHFINDER.active.get() && !TungstenModDataContainer.EXECUTOR.isRunning()) {
			TungstenModDataContainer.PATHFINDER.find(this.getEntityWorld(), TungstenMod.TARGET, TungstenMod.mc.player);
		}
		if(TungstenMod.runBlockSearchKeyBinding.isPressed() && !TungstenModDataContainer.PATHFINDER.active.get()) {
			BlockSpacePathFinder.find(getEntityWorld(), TungstenMod.TARGET, TungstenMod.mc.player);
		}
		if (TungstenMod.pauseKeyBinding.isPressed()) {
			try {
				
	        	if((TungstenModDataContainer.PATHFINDER.active.get() || TungstenModDataContainer.EXECUTOR.isRunning())) {
	        		TungstenModDataContainer.PATHFINDER.stop.set(true);
	        		TungstenModDataContainer.EXECUTOR.stop = true;
					Debug.logMessage("Stopped!");
	    		} else {
					Debug.logMessage("Nothing to stop.");
	    		}

	
			} catch (Exception e) {
				// TODO: handle exception
			}
		}

		if (TungstenMod.pauseKeyBinding.isPressed()) {
			TungstenModDataContainer.PATHFINDER.stop.set(true);
		}
		if (TungstenMod.createGoalKeyBinding.isPressed()) {
			BlockPos cameraBlockPos = TungstenMod.mc.gameRenderer.getCamera().getBlockPos();
			TungstenMod.TARGET = new Vec3d(cameraBlockPos.getX() + 0.5, cameraBlockPos.getY() - 1, cameraBlockPos.getZ() + 0.5);
		}
	}

	@Inject(method = "tick", at = @At(value = "RETURN"))
	public void end(CallbackInfo ci) {
		if (TungstenModDataContainer.EXECUTOR.isRunning() && TungstenModDataContainer.EXECUTOR.getCurrentTick() > 0) {
			TungstenModDataContainer.EXECUTOR.getPath().get(TungstenModDataContainer.EXECUTOR.getCurrentTick() - 1).agent.compare((ClientPlayerEntity)(Object)this, ((ClientPlayerEntity)(Object)this).input.playerInput, true);
		} else if(!this.getAbilities().flying && Agent.INSTANCE != null) {
			Agent.INSTANCE.compare((ClientPlayerEntity)(Object)this, ((ClientPlayerEntity)(Object)this).input.playerInput, false);
		}
	}

	@Inject(method="getPitch", at=@At("RETURN"), cancellable = true)
	public void getPitch(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenModDataContainer.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getPitch(tickDelta));
		}
	}

	@Inject(method="getYaw", at=@At("RETURN"), cancellable = true)
	public void getYaw(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenModDataContainer.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getYaw(tickDelta));
		}
	}

}
