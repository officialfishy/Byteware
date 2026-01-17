package com.byteware;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Module, Boolean> prevDown = new HashMap<>();

    private ModuleManager() {}

    public static void init() {
        MODULES.clear();

        MODULES.add(new AutoSprintModule());
        MODULES.add(new FullbrightModule());
        MODULES.add(new AutoTotemModule());
        MODULES.add(new TriggerBotModule());
        MODULES.add(new AutoToolModule());
        MODULES.add(new AutoEatModule());
        MODULES.add(new EspModule());
        MODULES.add(new StorageEspModule());

        prevDown.clear();
        for (Module m : MODULES) prevDown.put(m, false);
    }

    public static List<Module> getByGroup(ModuleGroup group) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.getGroup() == group) out.add(m);
        return out;
    }

    public static List<Module> getAll() {
        return MODULES;
    }

    public static void toggle(Module m) {
        m.toggle();
        NotificationManager.push(m.getName() + (m.isEnabled() ? " ON" : " OFF"));
    }

    public static void onTick(MinecraftClient client) {
        if (client == null) return;

        // module logic
        for (Module m : MODULES) {
            if (m.isEnabled()) m.onTick(client);
        }

        // keybind toggles (edge trigger)
        long window = client.getWindow().getHandle();

        for (Module m : MODULES) {
            int key = m.getKeyCode();
            if (key == GLFW.GLFW_KEY_UNKNOWN) continue;

            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean wasDown = prevDown.getOrDefault(m, false);

            if (down && !wasDown) toggle(m);

            prevDown.put(m, down);
        }
    }
}
