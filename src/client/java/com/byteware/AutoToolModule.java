package com.byteware;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoToolModule implements Module {
    private boolean enabled = false;
    private int keyCode = -1;

    // return-to-previous-slot logic
    private boolean lastAttackPressed = false;
    private int originalSlot = -1;
    private int lastSwitchedTo = -1;

    @Override public String getName() { return "AutoTool"; }
    @Override public ModuleGroup getGroup() { return ModuleGroup.PLAYER; }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    @Override
    public void onTick(MinecraftClient client) {
        if (!enabled) return;

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // don't mess with slots in GUIs
        if (client.currentScreen != null) return;

        boolean attackPressed = client.options.attackKey.isPressed();

        // rising edge: remember slot when user STARTS holding left click
        if (attackPressed && !lastAttackPressed) {
            originalSlot = player.getInventory().selectedSlot;
            lastSwitchedTo = -1;
        }

        // falling edge: user RELEASES left click -> return to original slot (if we swapped)
        if (!attackPressed && lastAttackPressed) {
            tryReturnSlot(player);
        }

        lastAttackPressed = attackPressed;

        if (!attackPressed) return;

        HitResult hr = client.crosshairTarget;
        if (hr == null) return;

        // If aiming at an entity -> switch to sword/axe
        if (hr.getType() == HitResult.Type.ENTITY) {
            int bestWeaponSlot = findBestWeaponSlot(player);
            if (bestWeaponSlot != -1) switchSlot(player, bestWeaponSlot);
            return;
        }

        // If aiming at a block -> switch to best mining tool
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockState state = client.world.getBlockState(bhr.getBlockPos());
            if (state.isAir()) return;

            int bestToolSlot = findBestToolSlot(player, state);
            if (bestToolSlot != -1) switchSlot(player, bestToolSlot);
        }
    }

    private void switchSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;

        int current = player.getInventory().selectedSlot;
        if (current == slot) return;

        player.getInventory().selectedSlot = slot;
        lastSwitchedTo = slot;
    }

    private void tryReturnSlot(ClientPlayerEntity player) {
        if (originalSlot < 0 || originalSlot > 8) return;

        // only return if we were the ones who changed the slot
        // (prevents fighting the user if they manually scrolled)
        if (lastSwitchedTo != -1 && player.getInventory().selectedSlot == lastSwitchedTo) {
            player.getInventory().selectedSlot = originalSlot;
        }

        originalSlot = -1;
        lastSwitchedTo = -1;
    }

    // ===== selection =====

    private static int findBestWeaponSlot(ClientPlayerEntity player) {
        // Priority: sword first, then axe
        int axeSlot = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof SwordItem) return slot;
            if (axeSlot == -1 && stack.getItem() instanceof AxeItem) axeSlot = slot;
        }

        return axeSlot;
    }

    private static int findBestToolSlot(ClientPlayerEntity player, BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);

            // only take real tools (hand-like is usually <= 1.0)
            if (speed <= 1.0f) continue;

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }
}
