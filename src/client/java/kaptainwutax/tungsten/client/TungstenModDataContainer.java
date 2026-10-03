package kaptainwutax.tungsten.client;

import kaptainwutax.tungsten.client.path.PathExecutor;
import kaptainwutax.tungsten.client.path.PathFinder;
import kaptainwutax.tungsten.client.sim.AgentSimulator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

public class TungstenModDataContainer {
	public static LocalPlayer player;
    public static final boolean LOG_DEBUG_DATA = false;
    public static PathExecutor EXECUTOR;
	public static PathFinder PATHFINDER = new PathFinder();
	public static Level world;
    public static boolean ignoreFallDamage = true;
    public static net.minecraft.client.renderer.GameRenderer gameRenderer = null;
}
