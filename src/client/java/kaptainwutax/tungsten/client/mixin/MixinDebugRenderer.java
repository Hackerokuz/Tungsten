	package kaptainwutax.tungsten.client.mixin;
	
	import static org.lwjgl.opengl.GL11.GL_BLEND;
	import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
	import static org.lwjgl.opengl.GL11.glDisable;
	import static org.lwjgl.opengl.GL11.glEnable;

	import java.util.ArrayList;
	import java.util.Collection;
	import java.util.Collections;
	import java.util.List;

	import com.mojang.blaze3d.vertex.BufferBuilder;
	import kaptainwutax.tungsten.client.render.PathRenderer;
    import net.minecraft.client.Minecraft;
    import net.minecraft.client.renderer.culling.Frustum;
    import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
    import net.minecraft.client.renderer.debug.DebugRenderer;
	import org.spongepowered.asm.mixin.Mixin;
    import org.spongepowered.asm.mixin.Shadow;
    import org.spongepowered.asm.mixin.gen.Accessor;
	import org.spongepowered.asm.mixin.injection.At;
	import org.spongepowered.asm.mixin.injection.Inject;
	import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

	import kaptainwutax.tungsten.render.Color;
	import kaptainwutax.tungsten.render.Cuboid;
	import kaptainwutax.tungsten.render.Renderer;
	
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
