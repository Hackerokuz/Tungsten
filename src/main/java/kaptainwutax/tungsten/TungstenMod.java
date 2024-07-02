package kaptainwutax.tungsten;

import kaptainwutax.tungsten.path.PathExecutor;
import kaptainwutax.tungsten.world.VoxelWorld;
import net.fabricmc.api.ModInitializer;
import kaptainwutax.tungsten.render.Renderer;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class TungstenMod implements ModInitializer {

	public static Collection<Renderer> RENDERERS = Collections.synchronizedCollection(new ArrayList<>());
	public static Collection<Renderer> TEST = Collections.synchronizedCollection(new ArrayList<>());
	public static Vec3d TARGET = new Vec3d(0.5D, 10.0D, 0.5D);
	public static PathExecutor EXECUTOR = new PathExecutor();
	public static VoxelWorld WORLD;

	@Override
	public void onInitialize() {

	}

}
