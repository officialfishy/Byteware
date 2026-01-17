package com.byteware;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.EquipmentSlot;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class BytewareHud {

    private BytewareHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        renderWatermark(ctx, mc);
        renderCoords(ctx, mc, sh);
        renderArrayList(ctx, mc, sw);
        renderArmorHud(ctx, mc, sw, sh);
    }

    // ---------------- WATERMARK ----------------
    private static void renderWatermark(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, "Byteware", 6, 6, 0xFFFFFF);
    }

    // ---------------- COORDS ----------------
    private static void renderCoords(DrawContext ctx, MinecraftClient mc, int sh) {
        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        boolean isNether = mc.world.getRegistryKey() == World.NETHER;
        boolean isOverworld = mc.world.getRegistryKey() == World.OVERWORLD;

        String xStr = formatDimPair(x, isNether, isOverworld);
        String zStr = formatDimPair(z, isNether, isOverworld);

        String line = "XYZ: " + xStr + ", " + y + ", " + zStr;

        int pad = 4;
        int textH = mc.textRenderer.fontHeight;

        int drawX = pad;
        int drawY = sh - pad - textH;

        ctx.drawTextWithShadow(mc.textRenderer, line, drawX, drawY, 0xFFFFFF);
    }

    private static String formatDimPair(int value, boolean isNether, boolean isOverworld) {
        if (isNether) {
            int overworld = value * 8;
            return value + "(" + overworld + ")";
        }
        if (isOverworld) {
            int nether = (int) Math.floor(value / 8.0);
            return value + "(" + nether + ")";
        }
        return Integer.toString(value);
    }

    // ---------------- ARRAYLIST ----------------
    private static void renderArrayList(DrawContext ctx, MinecraftClient mc, int sw) {
        List<Module> enabled = ModuleManager.getAll().stream()
                .filter(Module::isEnabled)
                .sorted(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.getName())))
                .collect(Collectors.toList());

        int y = 6;

        for (Module m : enabled) {
            String name = m.getName();
            int w = mc.textRenderer.getWidth(name);

            int x = sw - w - 6;

            ctx.drawTextWithShadow(mc.textRenderer, name, x, y, 0xFFFFFF);
            y += mc.textRenderer.fontHeight + 2;
        }
    }

    // ---------------- ARMOR HUD ----------------
    private static void renderArmorHud(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        if (mc.player == null) return;

        int hotbarY = sh - 22;
        int armorY = hotbarY - 52; // moved slightly higher

        ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);
        ItemStack legs  = mc.player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack helm  = mc.player.getEquippedStack(EquipmentSlot.HEAD);

        ItemStack[] armor = new ItemStack[]{boots, legs, chest, helm};

        int icon = 16;
        int gap = 8;
        int totalW = (icon * 4) + (gap * 3);
        int startX = (sw / 2) - (totalW / 2);

        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (stack == null || stack.isEmpty()) continue;

            int x = startX + i * (icon + gap);

            // Icon
            ctx.drawItem(stack, x, armorY);

            // Durability under icon
            if (stack.isDamageable()) {
                int max = stack.getMaxDamage();
                int dmg = stack.getDamage();
                int left = Math.max(0, max - dmg);
                int pct = (int) Math.round((left / (double) max) * 100.0);

                String s = pct + "%";
                int tw = mc.textRenderer.getWidth(s);

                int tx = x + (icon / 2) - (tw / 2);
                int ty = armorY + icon + 2;

                ctx.fill(tx - 2, ty - 1, tx + tw + 2, ty + mc.textRenderer.fontHeight, 0x80000000);
                ctx.drawTextWithShadow(mc.textRenderer, s, tx, ty, 0xFFFFFF);
            }
        }
    }
}
