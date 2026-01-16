package com.byteware;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

public class BytewareClient implements ClientModInitializer {

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.byteware.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.byteware"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleManager.onTick(client);

            while (openMenuKey.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                if (mc.currentScreen instanceof BytewareScreen) mc.setScreen(null);
                else mc.setScreen(new BytewareScreen());
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            NotificationManager.render(drawContext);
        });
    }
}
