	package kaptainwutax.tungsten.client.mixin;


	import java.util.ArrayList;
	import java.util.List;

	import kaptainwutax.tungsten.client.render.PathRenderer;
    import net.minecraft.client.Minecraft;
    import net.minecraft.client.renderer.debug.DebugRenderer;
	import org.spongepowered.asm.mixin.Mixin;
    import org.spongepowered.asm.mixin.Shadow;
	import org.spongepowered.asm.mixin.injection.At;
	import org.spongepowered.asm.mixin.injection.Inject;
	import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
	
	@Mixin(DebugRenderer.class)
	public class MixinDebugRenderer {

		@Shadow
		private final List<DebugRenderer.SimpleDebugRenderer> renderers = new ArrayList<>();

		@Inject(method = "refreshRendererList", at = @At("RETURN"))
		public void refreshRendererList(CallbackInfo ci) {

			Minecraft minecraft = Minecraft.getInstance();
			this.renderers.add(new PathRenderer(minecraft));
		}

	
	}
