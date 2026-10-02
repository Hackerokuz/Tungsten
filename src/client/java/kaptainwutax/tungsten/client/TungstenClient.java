package kaptainwutax.tungsten.client;

import com.mojang.blaze3d.platform.InputConstants;
import kaptainwutax.tungsten.Tungsten;
import kaptainwutax.tungsten.client.commandsystem.CommandExecutor;
import kaptainwutax.tungsten.client.path.PathExecutor;
import kaptainwutax.tungsten.world.VoxelWorld;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TungstenClient implements ClientModInitializer {

    public static final String MOD_ID = "tungsten";
    //    public static final ModMetadata MOD_META;
    public static final String NAME;

    public static Minecraft mc = null;
    public static LocalPlayer player = null;
    public static ClientLevel world = null;
    public static Vec3 TARGET = new Vec3(0.5D, 10.0D, 0.5D);
    public static clickModeEnum clickMode = clickModeEnum.PLACE_GOAL;
    public static final Logger LOG;
    public static VoxelWorld WORLD;
    public static KeyMapping pauseKeyBinding;
    public static KeyMapping runKeyBinding;
    public static KeyMapping runBlockSearchKeyBinding;
    public static KeyMapping createGoalKeyBinding;
    private static CommandExecutor _commandExecutor;
    public static boolean renderPositonBoxes = true;


    static {
        // MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
        NAME = "Tungsten";
        // DEV_BUILD = MOD_META.getCustomValue(TungstenMod.MOD_ID + ":devbuild").getAsString();
        LOG = LoggerFactory.getLogger(NAME);

    }


    @Override
    public void onInitializeClient() {
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

        _commandExecutor = new CommandExecutor(this);

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

                Camera camera = mc.gameRenderer.getMainCamera();
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

                    Tungsten.TARGET = new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);


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

    /**
     * Executes commands
     */
    public static CommandExecutor getCommandExecutor() {
        return _commandExecutor;
    }

    public enum clickModeEnum {
        OFF,
        PLACE_GOAL,
        GOTO
    }
}
