package com.byteware;

import org.lwjgl.glfw.GLFW;

public final class EspModule implements Module {
    private boolean enabled = false;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;

    // submodules
    private boolean players = true;
    private boolean hostiles = true;
    private boolean passives = false;

    // colors (ARGB)
    private int playersColor = 0xFFFFFFFF;   // white
    private int hostilesColor = 0xFFFF5555;  // red-ish
    private int passivesColor = 0xFF55FF55;  // green-ish

    @Override public String getName() { return "ESP"; }
    @Override public ModuleGroup getGroup() { return ModuleGroup.RENDER; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public boolean playersEnabled() { return players; }
    public void setPlayers(boolean v) { players = v; }

    public boolean hostilesEnabled() { return hostiles; }
    public void setHostiles(boolean v) { hostiles = v; }

    public boolean passivesEnabled() { return passives; }
    public void setPassives(boolean v) { passives = v; }

    public int getPlayersColor() { return playersColor; }
    public void setPlayersColor(int c) { playersColor = c; }

    public int getHostilesColor() { return hostilesColor; }
    public void setHostilesColor(int c) { hostilesColor = c; }

    public int getPassivesColor() { return passivesColor; }
    public void setPassivesColor(int c) { passivesColor = c; }

    // simple palette cycling
    private static final int[] PALETTE = new int[] {
            0xFFFFFFFF, // white
            0xFF55FFFF, // aqua
            0xFFAAAAFF, // light purple
            0xFFFFFF55, // yellow
            0xFFFFAA55, // orange
            0xFFFF5555, // red
            0xFF55FF55, // green
            0xFFAAAAAA  // gray
    };

    public int cycleColor(int current) {
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) { idx = i; break; }
        }
        idx = (idx + 1) % PALETTE.length;
        return PALETTE[idx];
    }
}
