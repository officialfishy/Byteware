package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoTotemModule implements Module {
    private boolean enabled = false;
    private int keyCode = -1;
    private int tickCooldown = 0;

    @Override public String getName() { return "AutoTotem"; }

    @Override
    public ModuleGroup getGroup() {
        // CHANGE THIS to a group that exists in your ModuleGroup enum
        return ModuleGroup.COMBAT;
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    @Override
    public void onTick(MinecraftClient client) {
        if (!enabled) return;

        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return;

        if (player.getAbilities().creativeMode) return;
        if (client.currentScreen != null) return;

        // already has totem
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        int invIndex = findTotemInventoryIndex(player);
        if (invIndex == -1) return;

        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) return;

        if (!handler.getCursorStack().isEmpty()) return;

        int fromSlot = toHandlerSlot(invIndex);
        int offhandSlot = 45;

        int syncId = handler.syncId;

        client.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(syncId, offhandSlot, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, player);

        tickCooldown = 4;
    }

    private static int findTotemInventoryIndex(ClientPlayerEntity player) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static int toHandlerSlot(int invIndex) {
        if (invIndex >= 0 && invIndex <= 8) return 36 + invIndex; // hotbar
        return invIndex; // 9..35 main inventory
    }
}
