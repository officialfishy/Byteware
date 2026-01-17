package com.byteware;

import org.lwjgl.glfw.GLFW;

public final class StorageEspModule implements Module {
    private boolean enabled = false;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;

    public enum Mode { BOX, GLOW }
    private Mode mode = Mode.BOX;

    // toggles
    private boolean chests = true;
    private boolean trappedChests = true;
    private boolean barrels = true;
    private boolean shulkers = true;
    private boolean enderChests = true;
    private boolean other = true; // furnaces, hoppers, droppers, dispensers, etc.

    // visuals
    private boolean tracers = false;
    private int fillOpacity = 40; // 0..255 used only for BOX fill
    private int outlineWidth = 2; // used only for GLOW (not true shader width but kept for UI consistency)
    private double fadeDistance = 6.0;

    // colors (ARGB)
    private int chestColor = 0xFFFFA000;        // orange
    private int trappedChestColor = 0xFFFF0000; // red
    private int barrelColor = 0xFFFFA000;       // orange
    private int shulkerColor = 0xFFFFA000;      // orange
    private int enderColor = 0xFF7800FF;        // purple
    private int otherColor = 0xFF8C8C8C;        // gray

    @Override public String getName() { return "StorageESP"; }
    @Override public ModuleGroup getGroup() { return ModuleGroup.RENDER; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public boolean chestsEnabled() { return chests; }
    public void setChests(boolean v) { chests = v; }

    public boolean trappedChestsEnabled() { return trappedChests; }
    public void setTrappedChests(boolean v) { trappedChests = v; }

    public boolean barrelsEnabled() { return barrels; }
    public void setBarrels(boolean v) { barrels = v; }

    public boolean shulkersEnabled() { return shulkers; }
    public void setShulkers(boolean v) { shulkers = v; }

    public boolean enderChestsEnabled() { return enderChests; }
    public void setEnderChests(boolean v) { enderChests = v; }

    public boolean otherEnabled() { return other; }
    public void setOther(boolean v) { other = v; }

    public boolean tracersEnabled() { return tracers; }
    public void setTracers(boolean v) { tracers = v; }

    public int getFillOpacity() { return fillOpacity; }
    public void setFillOpacity(int v) { fillOpacity = clamp(v, 0, 255); }

    public int getOutlineWidth() { return outlineWidth; }
    public void setOutlineWidth(int v) { outlineWidth = clamp(v, 1, 10); }

    public double getFadeDistance() { return fadeDistance; }
    public void setFadeDistance(double v) { fadeDistance = Math.max(0.0, v); }

    public int getChestColor() { return chestColor; }
    public void setChestColor(int c) { chestColor = c; }

    public int getTrappedChestColor() { return trappedChestColor; }
    public void setTrappedChestColor(int c) { trappedChestColor = c; }

    public int getBarrelColor() { return barrelColor; }
    public void setBarrelColor(int c) { barrelColor = c; }

    public int getShulkerColor() { return shulkerColor; }
    public void setShulkerColor(int c) { shulkerColor = c; }

    public int getEnderColor() { return enderColor; }
    public void setEnderColor(int c) { enderColor = c; }

    public int getOtherColor() { return otherColor; }
    public void setOtherColor(int c) { otherColor = c; }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
