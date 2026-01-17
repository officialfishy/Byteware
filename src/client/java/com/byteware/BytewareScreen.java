package com.byteware;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BytewareScreen extends Screen {

    // Layout
    private static final int PADDING = 8;
    private static final int GROUP_BTN_H = 18;
    private static final int GROUP_BTN_PAD_X = 10;

    private ModuleGroup selectedGroup = ModuleGroup.COMBAT;

    // Click targets
    private final List<GroupButton> groupButtons = new ArrayList<>();
    private final List<ModuleButton> moduleButtons = new ArrayList<>();

    // Popup state (anchored to module row)
    private Module openModule = null;
    private Rect openModuleRect = null;
    private boolean listeningForKey = false;

    private Rect popupRect = null;
    private Rect keybindRect = null;

    // AutoEat extra click zones (only when openModule is AutoEatModule)
    private Rect eatToFullRect = null;
    private Rect listModeRect = null;
    private Rect addHeldRect = null;
    private Rect removeHeldRect = null;
    private Rect clearListRect = null;

    // ESP extra click zones (only when openModule is EspModule)
    private Rect espPlayersRect = null;
    private Rect espPlayersColorRect = null;
    private Rect espHostilesRect = null;
    private Rect espHostilesColorRect = null;
    private Rect espPassivesRect = null;
    private Rect espPassivesColorRect = null;

    // --- basic rect ---
    private static final class Rect {
        final int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; }
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class GroupButton {
        final ModuleGroup group;
        final Rect r;
        GroupButton(ModuleGroup group, Rect r) { this.group = group; this.r = r; }
    }

    private static final class ModuleButton {
        final Module module;
        final Rect r;
        ModuleButton(Module module, Rect r) { this.module = module; this.r = r; }
    }

    public BytewareScreen() {
        super(Text.literal("Byteware!"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Brand (top-left)
        context.drawTextWithShadow(this.textRenderer, "Byteware", 6, 6, 0xFFFFFF);

        // Main panel
        int panelX = PADDING;
        int panelY = 18;
        int panelW = this.width - (PADDING * 2);
        int panelH = this.height - panelY - PADDING;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA000000);

        // ---- Group bar (left -> right) ----
        groupButtons.clear();

        int gx = panelX + 8;
        int gy = panelY + 6;

        for (ModuleGroup g : ModuleGroup.values()) {
            String label = g.name();
            int textW = this.textRenderer.getWidth(label);
            int btnW = textW + (GROUP_BTN_PAD_X * 2);

            boolean selected = (g == selectedGroup);

            int bg = selected ? 0x55FFFFFF : 0x22000000;
            int border = 0x33FFFFFF;

            context.fill(gx, gy, gx + btnW, gy + GROUP_BTN_H, bg);
            drawBorder(context, gx, gy, btnW, GROUP_BTN_H, border);

            int tx = gx + (btnW / 2) - (textW / 2);
            int ty = gy + (GROUP_BTN_H / 2) - (this.textRenderer.fontHeight / 2);
            context.drawTextWithShadow(this.textRenderer, label, tx, ty, 0xFFFFFF);

            groupButtons.add(new GroupButton(g, new Rect(gx, gy, btnW, GROUP_BTN_H)));
            gx += btnW + 6;
        }

        // Divider under group bar
        int dividerY = panelY + 6 + GROUP_BTN_H + 6;
        context.fill(panelX + 8, dividerY, panelX + panelW - 8, dividerY + 1, 0x33FFFFFF);

        // ---- Content area ----
        int contentX = panelX + 12;
        int contentY = dividerY + 10;

        context.drawTextWithShadow(this.textRenderer, selectedGroup.name(), contentX, contentY, 0xFFFFFF);

        List<Module> mods = ModuleManager.getByGroup(selectedGroup);

        moduleButtons.clear();

        int listY = contentY + 22;
        int rowH = 18;
        int rowW = Math.min(240, panelW - 24);

        if (mods.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No modules yet", contentX, listY, 0xAAAAAA);
        } else {
            for (Module m : mods) {
                int rowX = contentX;
                int rowY = listY;

                int bg = m.isEnabled() ? 0x5533FF33 : 0x22000000;
                int border = 0x33FFFFFF;

                context.fill(rowX, rowY, rowX + rowW, rowY + rowH, bg);
                drawBorder(context, rowX, rowY, rowW, rowH, border);

                // Left label
                String label = m.getName();
                context.drawTextWithShadow(this.textRenderer, label, rowX + 8, rowY + 5, 0xFFFFFF);

                // Right label: keybind (if set)
                String kb = keyName(m.getKeyCode());
                if (!"NONE".equals(kb)) {
                    int kbW = this.textRenderer.getWidth(kb);
                    context.drawTextWithShadow(this.textRenderer, kb, rowX + rowW - 8 - kbW, rowY + 5, 0xAAAAAA);
                }

                Rect r = new Rect(rowX, rowY, rowW, rowH);
                moduleButtons.add(new ModuleButton(m, r));

                listY += rowH + 6;
            }
        }

        // ---- Popup (anchored to module row) ----
        popupRect = null;
        keybindRect = null;

        eatToFullRect = null;
        listModeRect = null;
        addHeldRect = null;
        removeHeldRect = null;
        clearListRect = null;

        espPlayersRect = null;
        espPlayersColorRect = null;
        espHostilesRect = null;
        espHostilesColorRect = null;
        espPassivesRect = null;
        espPassivesColorRect = null;

        if (openModule != null && openModuleRect != null) {

            boolean isAutoEat = (openModule instanceof AutoEatModule);
            boolean isEsp = (openModule instanceof EspModule);

            // Popup sizing + spacing
            int popW = 210;
            int lineH = 14;         // distance between text baselines
            int topPad = 6;         // title y offset
            int headerH = 22;       // space from top to first line baseline
            int bottomPad = 14;     // footer space

            // rows inside popup (excluding title)
            int rows = 1; // keybind always
            if (isAutoEat) rows += 5; // EatToFull, ListMode, Add, Remove, Clear
            if (isEsp) rows += 6;     // Players, PlayersColor, Hostiles, HostilesColor, Passives, PassivesColor

            int popH = headerH + (rows * lineH) + bottomPad;

            int px = openModuleRect.x + openModuleRect.w + 8;
            int py = openModuleRect.y;

            // flip to left side if needed
            if (px + popW > this.width - 8) {
                px = openModuleRect.x - popW - 8;
            }

            // clamp vertically
            if (py + popH > this.height - 8) py = this.height - popH - 8;
            if (py < 8) py = 8;

            popupRect = new Rect(px, py, popW, popH);

            context.fill(px, py, px + popW, py + popH, 0xEE000000);
            drawBorder(context, px, py, popW, popH, 0x44FFFFFF);

            context.drawTextWithShadow(this.textRenderer, openModule.getName(), px + 8, py + topPad, 0xFFFFFF);

            int lineY = py + headerH; // first line baseline

            // ---- Keybind line ----
            String keyLine = listeningForKey
                    ? "Keybind: [press key]"
                    : "Keybind: " + keyName(openModule.getKeyCode());

            keybindRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
            drawHoverLine(context, keybindRect, mouseX, mouseY);
            context.drawTextWithShadow(this.textRenderer, keyLine, px + 8, lineY, 0xAAAAAA);

            // ---- AutoEat settings lines ----
            if (isAutoEat) {
                AutoEatModule ae = (AutoEatModule) openModule;

                lineY += lineH;
                String eatFull = "EatToFull: " + (ae.isEatToFull() ? "ON" : "OFF");
                eatToFullRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, eatToFullRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, eatFull, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String mode = "ListMode: " + ae.getListMode().name();
                listModeRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, listModeRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, mode, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String add = "Add held item";
                addHeldRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, addHeldRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, add, px + 8, lineY, 0x88FF88);

                lineY += lineH;
                String rem = "Remove held item";
                removeHeldRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, removeHeldRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, rem, px + 8, lineY, 0xFFAA88);

                lineY += lineH;
                String clr = "Clear list (" + ae.getFoodListView().size() + ")";
                clearListRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, clearListRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, clr, px + 8, lineY, 0xFF8888);
            }

            // ---- ESP settings lines ----
            if (isEsp) {
                EspModule em = (EspModule) openModule;

                lineY += lineH;
                String p = "Players: " + (em.playersEnabled() ? "ON" : "OFF");
                espPlayersRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espPlayersRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, p, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String pc = "Players Color: click";
                espPlayersColorRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espPlayersColorRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, pc, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String h = "Hostiles: " + (em.hostilesEnabled() ? "ON" : "OFF");
                espHostilesRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espHostilesRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, h, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String hc = "Hostiles Color: click";
                espHostilesColorRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espHostilesColorRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, hc, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String pa = "Passives: " + (em.passivesEnabled() ? "ON" : "OFF");
                espPassivesRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espPassivesRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, pa, px + 8, lineY, 0xAAAAAA);

                lineY += lineH;
                String pac = "Passives Color: click";
                espPassivesColorRect = new Rect(px + 6, lineY - 3, popW - 12, 14);
                drawHoverLine(context, espPassivesColorRect, mouseX, mouseY);
                context.drawTextWithShadow(this.textRenderer, pac, px + 8, lineY, 0xAAAAAA);
            }

            // footer
            context.drawTextWithShadow(this.textRenderer, "RMB: close", px + 8, py + popH - 12, 0x666666);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static void drawHoverLine(DrawContext context, Rect r, int mouseX, int mouseY) {
        if (r != null && r.hit(mouseX, mouseY)) {
            context.fill(r.x, r.y, r.x + r.w, r.y + r.h, 0x22FFFFFF);
        }
    }

    private static void drawBorder(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static String keyName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE";
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null && !name.isBlank()) return name.toUpperCase();
        return "KEY_" + keyCode;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // If popup open:
        if (openModule != null) {

            // RMB closes popup
            if (button == 1) {
                openModule = null;
                openModuleRect = null;
                listeningForKey = false;
                return true;
            }

            // LMB on popup options
            if (button == 0) {

                // Keybind line starts listening
                if (keybindRect != null && keybindRect.hit(mouseX, mouseY)) {
                    listeningForKey = true;
                    return true;
                }

                // AutoEat settings clicks
                if (openModule instanceof AutoEatModule ae) {

                    if (eatToFullRect != null && eatToFullRect.hit(mouseX, mouseY)) {
                        ae.setEatToFull(!ae.isEatToFull());
                        NotificationManager.push("AutoEat EatToFull: " + (ae.isEatToFull() ? "ON" : "OFF"));
                        return true;
                    }

                    if (listModeRect != null && listModeRect.hit(mouseX, mouseY)) {
                        ae.cycleListMode();
                        NotificationManager.push("AutoEat ListMode: " + ae.getListMode().name());
                        return true;
                    }

                    if (addHeldRect != null && addHeldRect.hit(mouseX, mouseY)) {
                        ItemStack held = heldStack();
                        if (held.isEmpty()) {
                            NotificationManager.push("Hold a food item first");
                        } else if (ae.addItemToListFromStack(held)) {
                            NotificationManager.push("Added: " + held.getName().getString());
                        } else {
                            NotificationManager.push("Already in list: " + held.getName().getString());
                        }
                        return true;
                    }

                    if (removeHeldRect != null && removeHeldRect.hit(mouseX, mouseY)) {
                        ItemStack held = heldStack();
                        if (held.isEmpty()) {
                            NotificationManager.push("Hold a food item first");
                        } else if (ae.removeItemFromListFromStack(held)) {
                            NotificationManager.push("Removed: " + held.getName().getString());
                        } else {
                            NotificationManager.push("Not in list: " + held.getName().getString());
                        }
                        return true;
                    }

                    if (clearListRect != null && clearListRect.hit(mouseX, mouseY)) {
                        ae.clearList();
                        NotificationManager.push("AutoEat list cleared");
                        return true;
                    }
                }

                // ESP settings clicks
                if (openModule instanceof EspModule em) {

                    if (espPlayersRect != null && espPlayersRect.hit(mouseX, mouseY)) {
                        em.setPlayers(!em.playersEnabled());
                        NotificationManager.push("ESP Players: " + (em.playersEnabled() ? "ON" : "OFF"));
                        return true;
                    }

                    if (espPlayersColorRect != null && espPlayersColorRect.hit(mouseX, mouseY)) {
                        em.setPlayersColor(em.cycleColor(em.getPlayersColor()));
                        NotificationManager.push("ESP Players Color changed");
                        return true;
                    }

                    if (espHostilesRect != null && espHostilesRect.hit(mouseX, mouseY)) {
                        em.setHostiles(!em.hostilesEnabled());
                        NotificationManager.push("ESP Hostiles: " + (em.hostilesEnabled() ? "ON" : "OFF"));
                        return true;
                    }

                    if (espHostilesColorRect != null && espHostilesColorRect.hit(mouseX, mouseY)) {
                        em.setHostilesColor(em.cycleColor(em.getHostilesColor()));
                        NotificationManager.push("ESP Hostiles Color changed");
                        return true;
                    }

                    if (espPassivesRect != null && espPassivesRect.hit(mouseX, mouseY)) {
                        em.setPassives(!em.passivesEnabled());
                        NotificationManager.push("ESP Passives: " + (em.passivesEnabled() ? "ON" : "OFF"));
                        return true;
                    }

                    if (espPassivesColorRect != null && espPassivesColorRect.hit(mouseX, mouseY)) {
                        em.setPassivesColor(em.cycleColor(em.getPassivesColor()));
                        NotificationManager.push("ESP Passives Color changed");
                        return true;
                    }
                }

                // Clicking outside popup closes it (nice UX)
                if (popupRect != null && !popupRect.hit(mouseX, mouseY)) {
                    openModule = null;
                    openModuleRect = null;
                    listeningForKey = false;
                    // do not return; user might be clicking something else
                }
            }
        }

        // Groups (LMB)
        if (button == 0) {
            for (GroupButton b : groupButtons) {
                if (b.r.hit(mouseX, mouseY)) {
                    selectedGroup = b.group;
                    openModule = null;
                    openModuleRect = null;
                    listeningForKey = false;
                    return true;
                }
            }
        }

        // Modules:
        // LMB = toggle
        if (button == 0) {
            for (ModuleButton b : moduleButtons) {
                if (b.r.hit(mouseX, mouseY)) {
                    ModuleManager.toggle(b.module);
                    return true;
                }
            }
        }

        // RMB = open settings popup (anchored)
        if (button == 1) {
            for (ModuleButton b : moduleButtons) {
                if (b.r.hit(mouseX, mouseY)) {
                    openModule = b.module;
                    openModuleRect = b.r;
                    listeningForKey = false;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static ItemStack heldStack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getMainHandStack();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (openModule != null && listeningForKey) {
            // ESC cancels
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningForKey = false;
                return true;
            }

            // Backspace/Delete clears
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                openModule.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                NotificationManager.push(openModule.getName() + " keybind: NONE");
                listeningForKey = false;
                return true;
            }

            // Set keybind
            openModule.setKeyCode(keyCode);
            NotificationManager.push(openModule.getName() + " keybind: " + keyName(keyCode));
            listeningForKey = false;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
