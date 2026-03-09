package kaptainwutax.tungsten;

import io.github.hackerokuz.FakePlayerAPIMod;
import io.github.hackerokuz.fakes.OurFakePlayer;
import kaptainwutax.tungsten.agent.Agent;
import kaptainwutax.tungsten.path.PathExecutor;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ServerSideTungstenMod implements DedicatedServerModInitializer {

	public OurFakePlayer player;
	
	public void onInitializeServer() {
		
		if (!FabricLoader.getInstance().isModLoaded("fake-player-api")) return;
		
		TungstenModDataContainer.EXECUTOR = new PathExecutor(false);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(CommandManager.literal("summonTungsten")
                    .executes(context -> {
                        return this.summonFakePlayer(context.getSource());
                    }));

            dispatcher.register(CommandManager.literal("stopTungsten")
                    .executes(context -> {
                    	
                    	if (TungstenModDataContainer.EXECUTOR.isRunning())
                    		TungstenModDataContainer.EXECUTOR.stop = true;
                		if (TungstenModDataContainer.PATHFINDER.active.get())
                    		TungstenModDataContainer.PATHFINDER.stop.set(true);
                		context.getSource().sendFeedback(() -> Text.of("Stopped!"), false);
                		return 1;
                    }));
            dispatcher.register(CommandManager.literal("come")
                    .executes(context -> {
                    	
                    	if (TungstenModDataContainer.EXECUTOR.isRunning() || TungstenModDataContainer.PATHFINDER.active.get()) {
                    		context.getSource().sendFeedback(() -> Text.of("Sorry, another process is running."), false);
                    		return 0;
                    	}
                    	
                    	BlockPos targetBlock = context.getSource().getPlayer().getBlockPos();
                    	
                    	Vec3d targetPos = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
                    	
                    	context.getSource().getPlayer().teleport(targetPos.x, targetPos.y, targetPos.z, true);
                    	
                    	TungstenModDataContainer.PATHFINDER.find(context.getSource().getWorld(), targetPos, player);
                		context.getSource().sendFeedback(() -> Text.of("Going to " + targetPos.toString()), false);
                        
                        return 1;
                    }));
        });
	}
	
	private int summonFakePlayer(ServerCommandSource source) {
		
		TungstenModDataContainer.world = source.getWorld();
		
		player = FakePlayerAPIMod.createFakePlayer(source.getWorld(), source.getServer(), source.getPlayer(), this::tick);

		
		
        TungstenModDataContainer.player = source.getPlayer();
		return 1;
	}
	
	private void tick() {
		Agent.INSTANCE = Agent.of(player);
		Agent.INSTANCE.tick(player.getEntityWorld());
		if (TungstenModDataContainer.EXECUTOR.getPath() != null) TungstenModDataContainer.EXECUTOR.tick(player);
	}
	
}
