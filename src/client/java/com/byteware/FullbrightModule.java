package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FullbrightModule implements Module {

    private boolean enabled = false;
    private int keyCode = -1; // NONE

    private boolean bytewareApplied = false;

    @Override
    public String getName() {
        return "Fullbright";
    }

    @Override
    public ModuleGroup getGroup() {
        return ModuleGroup.RENDER;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int getKeyCode() {
        return keyCode;
    }

    @Override
    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        if (enabled) {
            // Night vision = Meteor-style "world lit up" without gamma hacks
            client.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    400,   // 20s
                    0,
                    true,  // ambient
                    false, // showParticles
                    false  // showIcon
            ));
            bytewareApplied = true;
        } else {
            if (bytewareApplied && client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            bytewareApplied = false;
        }
    }
}
