package kaptainwutax.tungsten.mixin;

import com.mojang.authlib.GameProfile;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.PathFinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.text.Text;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayerEntity {

	private Thread patfinderThread = null;

	public MixinClientPlayerEntity(ClientWorld world, GameProfile profile, @Nullable PlayerPublicKey publicKey) {
		super(world, profile, publicKey);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void start(CallbackInfo ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			TungstenMod.EXECUTOR.tick((ClientPlayerEntity)(Object)this, MinecraftClient.getInstance().options);
		}

		if(!this.getAbilities().flying) {
			Agent.INSTANCE = Agent.of((ClientPlayerEntity)(Object)this);
			Agent.INSTANCE.tick(this.world);
		}

		if(TungstenMod.runKeyBinding.isPressed() && !PathFinder.active) {
			PathFinder.find(this.world, TungstenMod.TARGET);
		}
		if(TungstenMod.runBlockSearchKeyBinding.isPressed() && !PathFinder.active) {
			PathFinder.find(world, TungstenMod.TARGET);
		}
		if (TungstenMod.pauseKeyBinding.isPressed() && PathFinder.thread != null && PathFinder.thread.isAlive()) {
			PathFinder.thread.interrupt();
			TungstenMod.RENDERERS.clear();
			TungstenMod.TEST.clear();
		}
		
		if (TungstenMod.createGoalKeyBinding.isPressed()) {
			TungstenMod.TARGET = MinecraftClient.getInstance().player.getPos();
		}
	}

	@Inject(method = "tick", at = @At(value = "RETURN"))
	public void end(CallbackInfo ci) {
		if(!this.getAbilities().flying && Agent.INSTANCE != null) {
			Agent.INSTANCE.compare((ClientPlayerEntity)(Object)this, false);
		}
	}

	@Inject(method="getPitch", at=@At("RETURN"), cancellable = true)
	public void getPitch(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getPitch(tickDelta));
		}
	}

	@Inject(method="getYaw", at=@At("RETURN"), cancellable = true)
	public void getYaw(float tickDelta, CallbackInfoReturnable<Float> ci) {
		if(TungstenMod.EXECUTOR.isRunning()) {
			ci.setReturnValue(super.getYaw(tickDelta));
		}
	}

}
