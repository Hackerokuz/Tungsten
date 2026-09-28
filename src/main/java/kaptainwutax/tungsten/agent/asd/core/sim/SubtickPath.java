package kaptainwutax.tungsten.agent.core.sim;

import java.util.List;

public final class SubtickPath {

    public static void appendMove(List<Vec3dCore> buf, double bx, double by, double bz, double cx, double cy, double cz, boolean xBeforeZ) {
        if (buf.isEmpty()) buf.add(new Vec3dCore(bx, by, bz));
        buf.add(new Vec3dCore(bx, by + cy, bz));
        if (xBeforeZ) {
            buf.add(new Vec3dCore(bx + cx, by + cy, bz));
            buf.add(new Vec3dCore(bx + cx, by + cy, bz + cz));
        } else {
            buf.add(new Vec3dCore(bx, by + cy, bz + cz));
            buf.add(new Vec3dCore(bx + cx, by + cy, bz + cz));
        }
    }
}
