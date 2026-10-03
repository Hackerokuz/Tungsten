package kaptainwutax.tungsten.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import kaptainwutax.tungsten.client.path.PathExecutor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TungstenClient implements ClientModInitializer {

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(TungstenClient.id("pipeline/debug_filled_box_through_walls"))
            .withDepthStencilState(Optional.empty())
            .build()
    );
    public static final String MOD_ID = "tungsten";
    //    public static final ModMetadata MOD_META;
    public static final String NAME;

    public static Minecraft mc = null;
    public static LocalPlayer player = null;
    public static ClientLevel world = null;
    public static Vec3 TARGET = new Vec3(0.5D, 10.0D, 0.5D);
    public static clickModeEnum clickMode = clickModeEnum.PLACE_GOAL;
    public static final Logger LOG;
    public static Level WORLD;
    public static KeyMapping pauseKeyBinding;
    public static KeyMapping runKeyBinding;
    public static KeyMapping runBlockSearchKeyBinding;
    public static KeyMapping createGoalKeyBinding;
    public static boolean renderPositonBoxes = true;
    private static final StagedVertexBuffer stagedBuffer = new StagedVertexBuffer(() -> "Waypoints Buffer", RenderType.SMALL_BUFFER_SIZE);


    static {
        // MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        NAME = "Tungsten";
        // DEV_BUILD = MOD_META.getCustomValue(TungstenMod.MOD_ID + ":devbuild").getAsString();
        LOG = LoggerFactory.getLogger(NAME);

    }


    @Override
    public void onInitializeClient() {
        RenderPipeline renderPipeline = TungstenClient.FILLED_THROUGH_WALLS;
        VertexFormat formatBinding = renderPipeline.getVertexFormatBinding(0);

        assert formatBinding != null;

        PrimitiveTopology primitive = renderPipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(formatBinding, primitive, primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);
        TungstenModDataContainer.EXECUTOR = new PathExecutor(true);
        pauseKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tungsten.pause", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_P, // The keycode of the key
                KeyMapping.Category.MISC // The translation key of the keybinding's category.
        ));
        runKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tungsten.run", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_G, // The keycode of the key
                KeyMapping.Category.MISC // The translation key of the keybinding's category.
        ));
        runBlockSearchKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tungsten.run_block_search", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_J, // The keycode of the key
                KeyMapping.Category.MISC // The translation key of the keybinding's category.
        ));
        createGoalKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tungsten.create_goal", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_H, // The keycode of the key
                KeyMapping.Category.MISC // The translation key of the keybinding's category.
        ));



        // Global minecraft client accessor
        mc = Minecraft.getInstance();
        TungstenModDataContainer.player = mc.player;
        TungstenModDataContainer.world = mc.level;
        TungstenModDataContainer.gameRenderer = mc.gameRenderer;

        initializeCommands();

        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            Runnable toRun = new Runnable() {
                public void run() {
                    if (!TungstenModRenderContainer.ERROR.isEmpty()) {
                        TungstenModRenderContainer.ERROR.clear();
                    }
                }
            };
            ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(toRun, 1, 15, TimeUnit.SECONDS);
        }

        ClientTickEvents.START_CLIENT_TICK.register((a) -> {

            boolean isRunning = TungstenModDataContainer.PATHFINDER.active.get() || TungstenModDataContainer.EXECUTOR.isRunning();
            if (!isRunning) {
                if (!TungstenModRenderContainer.BLOCK_PATH_RENDERER.isEmpty()) {
                    TungstenModRenderContainer.BLOCK_PATH_RENDERER.clear();
                }
                if (!TungstenModRenderContainer.RUNNING_PATH_RENDERER.isEmpty()) {
                    TungstenModRenderContainer.RUNNING_PATH_RENDERER.clear();
                }
                if (!TungstenModRenderContainer.RENDERERS.isEmpty()) {
                    TungstenModRenderContainer.RENDERERS.clear();
                }
                if (!TungstenModRenderContainer.TEST.isEmpty()) {
                    TungstenModRenderContainer.TEST.clear();
                }
            }
            if (clickMode != clickModeEnum.OFF && mc.options.keyUse.isDown() && !isRunning && mc.player != null) {

                Camera camera = mc.gameRenderer.mainCamera();
                Vec3 cameraPos = camera.position();

                // Calculate the direction the camera is looking based on its pitch and yaw, and extend this direction 210 units away from the camera position
                // 210 is used here as the maximum distance of 200 blocks
                // This is done to be able to set target while in freecam
                Vec3 direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot()).scale(250);
                Vec3 targetPos = cameraPos.add(direction);

                ClipContext context = new ClipContext(
                        cameraPos,   // start position of the ray
                        targetPos,   // end position of the ray
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        mc.player
                );

                BlockHitResult hitResult = mc.level.clip(context);

                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
                    Direction side = hitResult.getDirection();

                    if (mc.level.getBlockState(pos).useWithoutItem(mc.level, mc.player, hitResult) != InteractionResult.PASS)
                        return;

                    BlockState state = mc.level.getBlockState(pos);

                    VoxelShape shape = state.getCollisionShape(mc.level, pos);
                    if (shape.isEmpty()) shape = state.getShape(mc.level, pos);

                    double height = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);

                    TungstenClient.TARGET = new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);


                    if (clickMode == clickModeEnum.GOTO && !TungstenModDataContainer.PATHFINDER.active.get()) {
                        TungstenModDataContainer.PATHFINDER.find(mc.level, TARGET, mc.player);
                    }
                }
            }


        });
    }

    public static String getCommandPrefix() {
        return ";";
    }

    // List all command sources here.
    private void initializeCommands() {
        try {
            // This creates the commands. If you want any more commands feel free to initialize new command lists.
            new TungstenCommands(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum clickModeEnum {
        OFF,
        PLACE_GOAL,
        GOTO
    }
    public static void close() {
        stagedBuffer.close();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
