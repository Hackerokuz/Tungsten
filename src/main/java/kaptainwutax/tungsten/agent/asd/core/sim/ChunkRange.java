package kaptainwutax.tungsten.agent.core.sim;

public final class ChunkRange {

    // Returns {cx1, cz1, cx2, cz2}: inclusive chunk bounds covering a 1-block margin around (x,z).
    public static int[] around(double x, double z) {
        return new int[]{
            ((int) Math.floor(x - 1.0)) >> 4,
            ((int) Math.floor(z - 1.0)) >> 4,
            ((int) Math.floor(x + 1.0)) >> 4,
            ((int) Math.floor(z + 1.0)) >> 4,
        };
    }
}
