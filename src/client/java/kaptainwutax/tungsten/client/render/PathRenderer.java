package kaptainwutax.tungsten.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModRenderContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class PathRenderer implements DebugRenderer.SimpleDebugRenderer {

    private final Minecraft minecraft;

    // Maximum number of renderers to draw at once to prevent performance issues
    private static final int MAX_RENDERERS_PER_CATEGORY = 500;


    public PathRenderer(final Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double cameraX, double cameraY, double cameraZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks) {


        Cuboid goal = new Cuboid(TungstenClient.TARGET.subtract(0.5D, 0D, 0.5D), new Vec3(1.0D, 2.0D, 1.0D), Color.GREEN);
        goal.render();


        // Batch render each collection with culling and limiting
        if (!TungstenModRenderContainer.RUNNING_PATH_RENDERER.isEmpty())
            renderCollection(TungstenModRenderContainer.RUNNING_PATH_RENDERER, frustum, cameraX, cameraY, cameraZ);

        if (!TungstenModRenderContainer.BLOCK_PATH_RENDERER.isEmpty())
            renderCollection(TungstenModRenderContainer.BLOCK_PATH_RENDERER, frustum, cameraX, cameraY, cameraZ);

        if (!TungstenModRenderContainer.RENDERERS.isEmpty())
            renderCollection(TungstenModRenderContainer.RENDERERS, frustum, cameraX, cameraY, cameraZ);

        if (!TungstenModRenderContainer.TEST.isEmpty())
            renderCollection(TungstenModRenderContainer.TEST, frustum, cameraX, cameraY, cameraZ);

        if (!TungstenModRenderContainer.ERROR.isEmpty())
            renderCollection(TungstenModRenderContainer.ERROR, frustum, cameraX, cameraY, cameraZ);


    }
    private static void renderCollection(Collection<Renderer> renderers, Frustum frustum,
                                         double cameraX, double cameraY, double cameraZ) {
        int count = 0;
        //		Vec3d target = new Vec3d(cameraX, cameraY, cameraZ);
        List<Renderer> sortedRenderers = new ArrayList<>(renderers);
        //		sortedRenderers.sort(Comparator.comparingDouble(obj -> obj.toVec3d(obj.getPos()).distanceTo(TungstenMod.mc.player.getPos())));
        Collections.reverse(sortedRenderers);
        try {
            for (Renderer r : sortedRenderers) {
                if (count >= MAX_RENDERERS_PER_CATEGORY) {
                    break; // Limit the number of renderers to prevent lag
                }

                try {
                    // Skip rendering for objects outside the view frustum
                    if (r.getPos() != null) {
                        if (!frustum.isVisible(new AABB(r.getPos().getX() - 3, r.getPos().getY() - 3, r.getPos().getZ() - 3,
                                r.getPos().getX() + 3, r.getPos().getY() + 3, r.getPos().getZ() + 3))) {
                            continue;
                        }
                    }
                    r.render();
//						RenderLayer.getDebugLineStrip(2).draw(builder.end());
                    count++;
                } catch (Exception e) {
                    // Log the exception rather than silently ignoring it
                    TungstenClient.LOG.debug("Error rendering object: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Log the exception rather than silently ignoring it
            TungstenClient.LOG.debug("Error rendering object: " + e.getMessage());
        }
    }

    private static void render(Renderer r) {
        try {
            r.render();
//				RenderLayer.getDebugLineStrip(2).draw(builder.end());
        } catch (Exception e) {
            // Ignored
        }
    }
}
