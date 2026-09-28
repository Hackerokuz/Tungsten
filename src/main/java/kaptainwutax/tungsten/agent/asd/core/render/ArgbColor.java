package de.legoshi.parkourcalc.core.render;

/** Allocation-free 0xAARRGGBB channel unpacking; called per vertex in the render hot path. */
public final class ArgbColor {

    public static float alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }

    public static float red(int argb) {
        return ((argb >>> 16) & 0xFF) / 255.0f;
    }

    public static float green(int argb) {
        return ((argb >>> 8) & 0xFF) / 255.0f;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255.0f;
    }
}
