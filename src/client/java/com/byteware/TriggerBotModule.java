package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class TriggerBotModule implements Module {
    private boolean enabled = false;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;

    // tiny cooldown so it doesn't spam packets every tick
    private int tickCooldown = 0;

    @Override
    public String getName() {
        return "TriggerBot";
    }

    @Override
    public ModuleGroup getGroup() {
        return ModuleGroup.COMBAT;
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
        if (!enabled) return;

        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return;

        // don’t attack while in GUIs
        if (client.currentScreen != null) return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        Entity target = ((EntityHitResult) hit).getEntity();

        // basic safety checks
        if (target == null || target == player) return;
        if (!(target instanceof LivingEntity living)) return;
        if (!living.isAlive()) return;

        // only hit when vanilla cooldown is ready (prevents weird spam)
        if (player.getAttackCooldownProgress(0.0f) < 1.0f) return;

        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        tickCooldown = 2; // feels snappy but not dumb
    }
}
