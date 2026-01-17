package com.byteware;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EspRenderer {
    private EspRenderer() {}

    private static final double RANGE = 256.0;

    private static final String TEAM_PLAYERS  = "byteware_esp_players";
    private static final String TEAM_HOSTILES = "byteware_esp_hostiles";
    private static final String TEAM_PASSIVES = "byteware_esp_passives";

    // We store UUIDs of entities we want to glow
    private static final Set<UUID> SHOULD_GLOW = new HashSet<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EspRenderer::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll(client));
        System.out.println("[Byteware] EspRenderer init OK");
    }

    // Called by the Mixin
    public static boolean shouldForceGlow(Entity e) {
        if (e == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return false;

        EspModule esp = findEspModule();
        if (esp == null || !esp.isEnabled()) return false;

        return SHOULD_GLOW.contains(e.getUuid());
    }

    private static void onTick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) return;

        EspModule esp = findEspModule();
        if (esp == null || !esp.isEnabled()) {
            clearAll(mc);
            return;
        }

        Scoreboard sb = mc.world.getScoreboard();
        ensureTeam(sb, TEAM_PLAYERS,  argbToFormatting(esp.getPlayersColor()));
        ensureTeam(sb, TEAM_HOSTILES, argbToFormatting(esp.getHostilesColor()));
        ensureTeam(sb, TEAM_PASSIVES, argbToFormatting(esp.getPassivesColor()));

        Box area = mc.player.getBoundingBox().expand(RANGE);

        List<Entity> entities = mc.world.getOtherEntities(
            mc.player,
            area,
            e -> e instanceof LivingEntity
        );

        // rebuild set each tick
        SHOULD_GLOW.clear();

        for (Entity e : entities) {
            if (!(e instanceof LivingEntity)) continue;
            if (e == mc.player) continue;

            String teamName;

            if (e instanceof PlayerEntity) {
                if (!esp.playersEnabled()) continue;
                teamName = TEAM_PLAYERS;
            } else if (e instanceof HostileEntity || e instanceof net.minecraft.entity.mob.Monster) {
                if (!esp.hostilesEnabled()) continue;
                teamName = TEAM_HOSTILES;
            } else if (e instanceof PassiveEntity) {
                if (!esp.passivesEnabled()) continue;
                teamName = TEAM_PASSIVES;
            } else {
                continue;
            }

            // mark for glow
            SHOULD_GLOW.add(e.getUuid());

            // also put into team so the outline has color
            addToTeamSafe(sb, scoreHolder(e), teamName);
        }
    }

    private static EspModule findEspModule() {
        for (Module m : ModuleManager.getAll()) {
            if (m instanceof EspModule em) return em;
        }
        return null;
    }

    // --- Scoreboard / teams ---

    private static void ensureTeam(Scoreboard sb, String name, Formatting color) {
        Team t = sb.getTeam(name);
        if (t == null) t = sb.addTeam(name);

        t.setColor(color);
        t.setFriendlyFireAllowed(true);
        t.setShowFriendlyInvisibles(false);
        t.setNameTagVisibilityRule(Team.VisibilityRule.NEVER);
        t.setDeathMessageVisibilityRule(Team.VisibilityRule.NEVER);
    }

    private static void addToTeamSafe(Scoreboard sb, String holder, String teamName) {
        Team desired = sb.getTeam(teamName);
        if (desired == null) return;

        Team current = sb.getScoreHolderTeam(holder);
        if (current == desired) return;

        // If they're already on some other team, remove safely first
        if (current != null) {
            try {
                sb.removeScoreHolderFromTeam(holder, current);
            } catch (Throwable ignored) {}
        }

        try {
            sb.addScoreHolderToTeam(holder, desired);
        } catch (Throwable ignored) {}
    }

    private static String scoreHolder(Entity e) {
        // Players should use their scoreboard name (so teams work reliably)
        if (e instanceof PlayerEntity p) return p.getNameForScoreboard();

        // Mobs: UUID string is stable + unique
        return e.getUuidAsString();
    }

    // --- Cleanup ---

    private static void clearAll(MinecraftClient mc) {
        SHOULD_GLOW.clear();

        if (mc == null || mc.world == null) return;

        Scoreboard sb = mc.world.getScoreboard();

        // We don't know which holders were in teams, but removing non-members safely is fine
        // (and avoids the crash you had earlier).
        // We can just delete the teams entirely.
        tryRemoveTeam(sb, TEAM_PLAYERS);
        tryRemoveTeam(sb, TEAM_HOSTILES);
        tryRemoveTeam(sb, TEAM_PASSIVES);
    }

    private static void tryRemoveTeam(Scoreboard sb, String name) {
        Team t = sb.getTeam(name);
        if (t == null) return;
        try {
            sb.removeTeam(t);
        } catch (Throwable ignored) {}
    }

    // --- Color mapping (glow outline uses Formatting colors) ---

    private static Formatting argbToFormatting(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        if (r > 220 && g > 220 && b > 220) return Formatting.WHITE;
        if (r > 220 && g > 220) return Formatting.YELLOW;
        if (r > 220 && b > 220) return Formatting.LIGHT_PURPLE;
        if (g > 220 && b > 220) return Formatting.AQUA;
        if (r > 220) return Formatting.RED;
        if (g > 220) return Formatting.GREEN;
        if (b > 220) return Formatting.BLUE;

        if (r > 160 && g > 160) return Formatting.GOLD;
        if (r > 160 && b > 160) return Formatting.DARK_PURPLE;
        if (g > 160 && b > 160) return Formatting.DARK_AQUA;

        if (r > 100) return Formatting.DARK_RED;
        if (g > 100) return Formatting.DARK_GREEN;
        if (b > 100) return Formatting.DARK_BLUE;

        return Formatting.GRAY;
    }
}
