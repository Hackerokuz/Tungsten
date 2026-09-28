package de.legoshi.parkourcalc.core.imgui;

import imgui.ImGuiIO;

@FunctionalInterface
public interface RenderInterface {

    void render(final ImGuiIO io);

    /** Display-only render while the main UI is closed. Default no-op; overlays that pin panels override this. */
    default void renderDetached(ImGuiIO io) {}

}
