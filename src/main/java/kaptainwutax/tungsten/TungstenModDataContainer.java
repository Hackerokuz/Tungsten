package kaptainwutax.tungsten;

import kaptainwutax.tungsten.path.PathExecutor;
import kaptainwutax.tungsten.path.PathFinder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TungstenModDataContainer {
	public static Player player;
    public static final boolean LOG_DEBUG_DATA = false;
    public static PathExecutor EXECUTOR;
	public static PathFinder PATHFINDER = new PathFinder();
	public static Level world;
    public static boolean ignoreFallDamage = true;
    public static net.minecraft.client.renderer.GameRenderer gameRenderer = null;
}
