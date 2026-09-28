package kaptainwutax.tungsten.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class Cube extends Cuboid {

    public Cube() {
        this(BlockPos.ZERO, Color.WHITE);
    }

    public Cube(BlockPos pos) {
        this(pos, Color.WHITE);
    }

    public Cube(BlockPos pos, Color color) {
        super(new Vec3(pos.getX(), pos.getY(), pos.getZ()), new Vec3(1, 1, 1), color);
    }

}
