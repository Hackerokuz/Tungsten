package kaptainwutax.tungsten;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.resources.Identifier;

public class Tungsten implements DedicatedServerModInitializer {
    public static final String MOD_ID = "tungsten";

    @Override
    public void onInitializeServer() {
    }
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
