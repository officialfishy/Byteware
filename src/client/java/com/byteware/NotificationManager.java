package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NotificationManager {

    private static final class Note {
        final String text;
        final long expiresAtMs;
        Note(String text, long expiresAtMs) {
            this.text = text;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final List<Note> notes = new ArrayList<>();

    private NotificationManager() {}

    public static void push(String text) {
        long now = System.currentTimeMillis();
        notes.add(new Note(text, now + 1500)); // 1.5s
    }

    public static void render(DrawContext ctx) {
        if (notes.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        long now = System.currentTimeMillis();

        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            if (it.next().expiresAtMs <= now) it.remove();
        }
        if (notes.isEmpty()) return;

        int margin = 8;
        int y = mc.getWindow().getScaledHeight() - margin;

        for (int i = notes.size() - 1; i >= 0; i--) {
            String s = notes.get(i).text;

            int w = mc.textRenderer.getWidth(s) + 12;
            int h = 18;

            int x = mc.getWindow().getScaledWidth() - margin - w;
            y -= h;

            ctx.fill(x, y, x + w, y + h, 0xAA000000);
            ctx.drawTextWithShadow(mc.textRenderer, s, x + 6, y + 5, 0xFFFFFF);

            y -= 6;
        }
    }
}
