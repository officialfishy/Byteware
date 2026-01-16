package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AutoEatModule implements Module {

    public enum ListMode { OFF, WHITELIST, BLACKLIST }

    private boolean enabled = false;
    private int keyCode = -1;

    // Start eating when hunger <= this (only used when EatToFull is OFF)
    private final int eatAtHunger = 16;

    // Submodules / settings
    private boolean eatToFull = false;
    private ListMode listMode = ListMode.OFF;
    private final Set<String> foodList = new HashSet<>(); // stores item id strings (e.g. "minecraft:cooked_beef")

    // Eating state
    private boolean eating = false;
    private int prevHotbarSlot = -1;
    private int eatSlot = -1;

    private int cooldownTicks = 0;

    // Safety to prevent stuck eating
    private int startHunger = -1;
    private int eatTicks = 0;

    @Override public String getName() { return "AutoEat"; }
    @Override public ModuleGroup getGroup() { return ModuleGroup.PLAYER; }

    @Override public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) forceStop();
    }

    @Override public int getKeyCode() { return keyCode; }
    @Override public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    // ===== UI / RMB popup API =====
    public boolean isEatToFull() { return eatToFull; }
    public void setEatToFull(boolean v) { this.eatToFull = v; }

    public ListMode getListMode() { return listMode; }

    public void cycleListMode() {
        switch (listMode) {
            case OFF -> listMode = ListMode.WHITELIST;
            case WHITELIST -> listMode = ListMode.BLACKLIST;
            case BLACKLIST -> listMode = ListMode.OFF;
        }
    }

    public Set<String> getFoodListView() {
        return Collections.unmodifiableSet(foodList);
    }

    public boolean addItemToListFromStack(ItemStack stack) {
        if (!isFood(stack)) return false;
        String id = itemId(stack.getItem());
        return foodList.add(id);
    }

    public boolean removeItemFromListFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = itemId(stack.getItem());
        return foodList.remove(id);
    }

    public void clearList() {
        foodList.clear();
    }

    // ===== main loop =====
    @Override
    public void onTick(MinecraftClient client) {
        if (!enabled) return;
        if (cooldownTicks > 0) cooldownTicks--;

        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (player.getAbilities().creativeMode) { forceStop(); return; }
        if (client.currentScreen != null) { forceStop(); return; }

        // -------- while eating --------
        if (eating) {
            eatTicks++;

            int hungerNow = player.getHungerManager().getFoodLevel();

            // stop conditions (these prevent the "never stops" bug)
            if (!player.isUsingItem()) { stopEatingAndRestore(player); return; }
            if (!isFood(player.getMainHandStack())) { stopEatingAndRestore(player); return; }

            // If EatToFull ON -> stop at full hunger
            if (eatToFull) {
                if (hungerNow >= 20) { stopEatingAndRestore(player); return; }
            } else {
                // EatToFull OFF -> stop once we've improved enough
                if (hungerNow > eatAtHunger || hungerNow > startHunger) {
                    stopEatingAndRestore(player);
                    return;
                }
            }

            // safety timeout (lag/packet weirdness)
            if (eatTicks > 60) { stopEatingAndRestore(player); return; }

            // keep holding use while eating
            client.options.useKey.setPressed(true);
            return;
        }

        // -------- not eating yet --------
        int hunger = player.getHungerManager().getFoodLevel();
        if (hunger >= 20) return; // no need
        if (!eatToFull && hunger > eatAtHunger) return; // only trigger low hunger unless eatToFull

        // If current hand already good food -> just eat
        if (canEat(player) && isAllowedFood(player.getMainHandStack())) {
            startEating(client, player, player.getInventory().selectedSlot);
            return;
        }

        // Find best allowed food in hotbar
        int bestHotbar = findBestAllowedFoodInHotbar(player);
        if (bestHotbar != -1) {
            startEating(client, player, bestHotbar);
        }
    }

    private void startEating(MinecraftClient client, ClientPlayerEntity player, int hotbarSlot) {
        if (cooldownTicks > 0) return;
        if (hotbarSlot < 0 || hotbarSlot > 8) return;

        prevHotbarSlot = player.getInventory().selectedSlot;
        eatSlot = hotbarSlot;

        player.getInventory().selectedSlot = eatSlot;

        ItemStack main = player.getMainHandStack();
        if (!canEat(player) || !isAllowedFood(main)) {
            // revert if not valid
            player.getInventory().selectedSlot = prevHotbarSlot;
            prevHotbarSlot = -1;
            eatSlot = -1;
            eating = false;
            return;
        }

        startHunger = player.getHungerManager().getFoodLevel();
        eatTicks = 0;

        eating = true;

        client.options.useKey.setPressed(true);
        if (client.interactionManager != null) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        }

        cooldownTicks = 4;
    }

    private void stopEatingAndRestore(ClientPlayerEntity player) {
        eating = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.useKey.setPressed(false);

        // stop using to prevent "stuck eating" animation/key
        player.stopUsingItem();

        if (prevHotbarSlot >= 0 && prevHotbarSlot <= 8) {
            player.getInventory().selectedSlot = prevHotbarSlot;
        }

        prevHotbarSlot = -1;
        eatSlot = -1;
        startHunger = -1;
        eatTicks = 0;

        cooldownTicks = 4;
    }

    private void forceStop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            stopEatingAndRestore(mc.player);
        } else {
            eating = false;
            mc.options.useKey.setPressed(false);
            prevHotbarSlot = -1;
            eatSlot = -1;
            startHunger = -1;
            eatTicks = 0;
            cooldownTicks = 0;
        }
    }

    private static boolean canEat(ClientPlayerEntity player) {
        return player.getHungerManager().getFoodLevel() < 20;
    }

    private boolean isAllowedFood(ItemStack stack) {
        if (!isFood(stack)) return false;

        // apply whitelist/blacklist filter
        if (listMode == ListMode.OFF) return true;

        String id = itemId(stack.getItem());
        boolean inList = foodList.contains(id);

        return (listMode == ListMode.WHITELIST) ? inList : !inList;
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        return food != null;
    }

    private int findBestAllowedFoodInHotbar(ClientPlayerEntity player) {
        int bestSlot = -1;
        double bestScore = -1.0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!isAllowedFood(stack)) continue;

            double score = foodScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static double foodScore(ItemStack stack) {
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return -1.0;

        int nutrition = food.nutrition();
        float saturation = food.saturation();
        // simple “best food” ranking
        return nutrition * 10.0 + saturation;
    }

    private static String itemId(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id == null ? "minecraft:air" : id.toString();
    }
}
