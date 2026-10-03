package kaptainwutax.tungsten.client.mixin;

import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.sim.AgentSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import kaptainwutax.tungsten.client.TungstenModDataContainer;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {

	@Shadow @Nullable public ClientLevel level;
	@Final
    @Shadow @Nullable public GameRenderer gameRenderer;

	@Inject(at = @At("HEAD"), method = "tick")
	private void tick(CallbackInfo info) {
		if (gameRenderer != TungstenModDataContainer.gameRenderer) {
	        TungstenModDataContainer.gameRenderer = this.gameRenderer;
		}
		if (Minecraft.getInstance().player != TungstenModDataContainer.player) {
	        TungstenModDataContainer.player = Minecraft.getInstance().player;
		}
		if(this.level == null) {
			TungstenClient.WORLD = null;
			TungstenModDataContainer.world = null;
		} else if(TungstenClient.WORLD == null) {
			TungstenClient.WORLD = this.level;
		} else if(TungstenClient.WORLD != this.level) {
			TungstenClient.WORLD = this.level;
		}
		if(TungstenModDataContainer.world == null) {
			TungstenModDataContainer.world = this.level;
		} else if(TungstenModDataContainer.world != this.level) {
			TungstenModDataContainer.world = this.level;
		}
	}

}
