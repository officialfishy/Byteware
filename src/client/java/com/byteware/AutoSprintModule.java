package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class AutoSprintModule implements Module {

    private boolean enabled = false;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;

    @Override public String getName() { return "AutoSprint"; }
    @Override public ModuleGroup getGroup() { return ModuleGroup.MOVEMENT; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    @Override
    public void onTick(MinecraftClient client) {
        if (client == null) return;
        if (client.currentScreen != null) return;

        ClientPlayerEntity p = client.player;
        if (p == null) return;

        if (p.isSneaking()) return;
        if (p.isUsingItem()) return;
        if (p.hasVehicle()) return;

        // Only sprint when moving forward (W)
        if (p.input != null && p.input.movementForward > 0) {
            p.setSprinting(true);
        }
    }
}
