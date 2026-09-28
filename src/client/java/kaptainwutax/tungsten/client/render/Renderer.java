package kaptainwutax.tungsten.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public abstract class Renderer {

    public abstract void render(BufferBuilder builder);

    public abstract BlockPos getPos();

    public Vec3 toVec3d(BlockPos pos) {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

}
