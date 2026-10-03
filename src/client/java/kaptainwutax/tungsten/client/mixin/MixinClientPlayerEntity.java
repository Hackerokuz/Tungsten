package kaptainwutax.tungsten.client.mixin;

import kaptainwutax.tungsten.client.TungstenClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.path.blockSpaceSearchAssist.BlockSpacePathFinder;

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

		if(TungstenClient.runKeyBinding.isDown() && !TungstenModDataContainer.PATHFINDER.active.get() && !TungstenModDataContainer.EXECUTOR.isRunning()) {
			TungstenModDataContainer.PATHFINDER.find(this.level(), TungstenClient.TARGET, TungstenClient.mc.player);
		}
		if(TungstenClient.runBlockSearchKeyBinding.isDown() && !TungstenModDataContainer.PATHFINDER.active.get()) {
			BlockSpacePathFinder.find(level(), TungstenClient.TARGET, TungstenClient.mc.player);
		}
		if (TungstenClient.pauseKeyBinding.isDown()) {
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

		if (TungstenClient.pauseKeyBinding.isDown()) {
			TungstenModDataContainer.PATHFINDER.stop.set(true);
		}
		if (TungstenClient.createGoalKeyBinding.isDown()) {
			BlockPos cameraBlockPos = TungstenClient.mc.gameRenderer.mainCamera().blockPosition();
			TungstenClient.TARGET = new Vec3(cameraBlockPos.getX() + 0.5, cameraBlockPos.getY() - 1, cameraBlockPos.getZ() + 0.5);
		}
	}

}
