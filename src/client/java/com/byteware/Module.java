package com.byteware;

import net.minecraft.client.MinecraftClient;

public interface Module {
    String getName();
    ModuleGroup getGroup();

    boolean isEnabled();
    void setEnabled(boolean enabled);

    // GLFW key code (GLFW.GLFW_KEY_*). UNKNOWN means none.
    int getKeyCode();
    void setKeyCode(int keyCode);

    default void toggle() {
        setEnabled(!isEnabled());
    }

    default void onTick(MinecraftClient client) {
        // optional per-module logic
    }
}
