package com.byteware;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.Map;

public final class StorageEspRenderer {
    private StorageEspRenderer() {}

    private static final double RANGE = 256.0;

    public static void init() {
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;

            StorageEspModule mod = findStorageModule();
            if (mod == null || !mod.isEnabled()) return;

            MatrixStack matrices = ctx.matrixStack();
            VertexConsumerProvider consumers = ctx.consumers();
            if (matrices == null || consumers == null) return;

            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            render(mc, matrices, consumers, tickDelta, mod);
        });
    }

    private static StorageEspModule findStorageModule() {
        for (Module m : ModuleManager.getAll()) {
            if (m instanceof StorageEspModule sm) return sm;
        }
        return null;
    }

    private static void render(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers,
                               float tickDelta, StorageEspModule mod) {

        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        // scan chunks around player (no world.blockEntities / no getLoadedChunksIterable)
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int rangeChunks = (int) Math.ceil(RANGE / 16.0);

        // GLOW provider (Minecraft outline pipeline)
        OutlineVertexConsumerProvider outlineProvider = null;
        BlockEntityRenderDispatcher beDispatcher = null;

        if (mod.getMode() == StorageEspModule.Mode.GLOW) {
            outlineProvider = mc.getBufferBuilders().getOutlineVertexConsumers();
            beDispatcher = mc.getBlockEntityRenderDispatcher();
        }

        // BOX mode = draw through walls
        if (mod.getMode() == StorageEspModule.Mode.BOX) {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        for (int cx = pcx - rangeChunks; cx <= pcx + rangeChunks; cx++) {
            for (int cz = pcz - rangeChunks; cz <= pcz + rangeChunks; cz++) {
                Object chunk = getClientWorldChunk(mc, cx, cz);
                if (chunk == null) continue;

                Map<BlockPos, BlockEntity> bes = getChunkBlockEntities(chunk);
                if (bes == null || bes.isEmpty()) continue;

                for (BlockEntity be : bes.values()) {
                    if (be == null) continue;

                    BlockPos pos = be.getPos();
                    if (pos == null) continue;

                    // range check
                    double dx = (pos.getX() + 0.5) - cam.x;
                    double dy = (pos.getY() + 0.5) - cam.y;
                    double dz = (pos.getZ() + 0.5) - cam.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > RANGE * RANGE) continue;

                    int argb = classifyColor(be, mod);
                    if (argb == 0) continue;

                    // Meteor-like fade
                    double fade = 1.0;
                    double fd = mod.getFadeDistance();
                    if (fd > 0) {
                        double fdSq = fd * fd;
                        if (distSq <= fdSq * fdSq) fade = Math.sqrt(distSq) / fdSq;
                        if (fade < 0.075) continue;
                    }

                    // apply fade to alpha
                    argb = applyFadeAlpha(argb, fade);

                    if (mod.getMode() == StorageEspModule.Mode.BOX) {
                        // box around the block
                        Box box = new Box(pos).offset(-cam.x, -cam.y, -cam.z);

                        // filled + lines look like Meteor "Both"
                        drawFilledBox(matrices, consumers, box, argb, mod.getFillOpacity());
                        drawBoxLines(matrices, consumers, box.expand(0.01), argb);

                        // optional tracer
                        if (mod.tracersEnabled()) {
                            drawTracer(matrices, consumers, cam, pos, argb);
                        }
                    } else {
                        // GLOW: render the block entity with Minecraft outline provider
                        // NOTE: this is the closest "spectral-ish" outline without Meteor’s shader system.
                        if (outlineProvider != null && beDispatcher != null) {
                            int r = (argb >>> 16) & 0xFF;
                            int g = (argb >>> 8) & 0xFF;
                            int b = (argb) & 0xFF;
                            int a = (argb >>> 24) & 0xFF;

                            outlineProvider.setColor(r, g, b, a);

                            matrices.push();
                            matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                            // render BE into outline provider
                            beDispatcher.render(be, tickDelta, matrices, outlineProvider);

                            matrices.pop();
                        }
                    }
                }
            }
        }

        if (mod.getMode() == StorageEspModule.Mode.GLOW && outlineProvider != null) {
            // flush outline rendering
            outlineProvider.draw();
        }

        if (mod.getMode() == StorageEspModule.Mode.BOX) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    // --- classification ---

    private static int classifyColor(BlockEntity be, StorageEspModule mod) {
        if (be instanceof TrappedChestBlockEntity) return mod.trappedChestsEnabled() ? mod.getTrappedChestColor() : 0;
        if (be instanceof ChestBlockEntity) return mod.chestsEnabled() ? mod.getChestColor() : 0;
        if (be instanceof BarrelBlockEntity) return mod.barrelsEnabled() ? mod.getBarrelColor() : 0;
        if (be instanceof ShulkerBoxBlockEntity) return mod.shulkersEnabled() ? mod.getShulkerColor() : 0;
        if (be instanceof EnderChestBlockEntity) return mod.enderChestsEnabled() ? mod.getEnderColor() : 0;

        if (be instanceof AbstractFurnaceBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof DispenserBlockEntity
                || be instanceof DropperBlockEntity
                || be instanceof BrewingStandBlockEntity
                || be instanceof DecoratedPotBlockEntity
                || be instanceof ChiseledBookshelfBlockEntity
                || be instanceof CrafterBlockEntity) {
            return mod.otherEnabled() ? mod.getOtherColor() : 0;
        }

        return 0;
    }

    private static int applyFadeAlpha(int argb, double fade) {
        int a = (argb >>> 24) & 0xFF;
        int na = (int) Math.max(0, Math.min(255, a * fade));
        return (argb & 0x00FFFFFF) | (na << 24);
    }

    // --- chunk access (reflection for mapping safety) ---

    private static Object getClientWorldChunk(MinecraftClient mc, int cx, int cz) {
        Object cm = mc.world.getChunkManager();

        try {
            Method m = cm.getClass().getMethod("getWorldChunk", int.class, int.class);
            return m.invoke(cm, cx, cz);
        } catch (Throwable ignored) {}

        try {
            Method m = cm.getClass().getMethod("getChunk", int.class, int.class);
            return m.invoke(cm, cx, cz);
        } catch (Throwable ignored) {}

        try {
            for (Method m : cm.getClass().getMethods()) {
                if (!m.getName().equals("getChunk")) continue;
                if (m.getParameterCount() != 4) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p[0] == int.class && p[1] == int.class && p[3] == boolean.class) {
                    return m.invoke(cm, cx, cz, null, false);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, BlockEntity> getChunkBlockEntities(Object chunk) {
        try {
            Method m = chunk.getClass().getMethod("getBlockEntities");
            Object out = m.invoke(chunk);
            if (out instanceof Map<?, ?> map) return (Map<BlockPos, BlockEntity>) map;
        } catch (Throwable ignored) {}
        return null;
    }

    // --- render helpers (lines + fill + tracer) ---

    private static void drawTracer(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, BlockPos pos, int argb) {
        // from camera to block center (in view space we draw around 0,0,0 with cam offset already applied)
        double x2 = (pos.getX() + 0.5) - cam.x;
        double y2 = (pos.getY() + 0.5) - cam.y;
        double z2 = (pos.getZ() + 0.5) - cam.z;

        drawLine(matrices, consumers.getBuffer(RenderLayer.getLines()), 0, 0, 0, x2, y2, z2, argb);
    }

    private static void drawFilledBox(MatrixStack matrices, VertexConsumerProvider consumers, Box b, int argb, int fillOpacity) {
        // simple translucent fill via lines buffer isn't perfect; we’ll approximate by drawing lots of “faces” lines would be messy.
        // keep it lightweight: only do fill if opacity > 0 by drawing a slightly denser box using RenderLayer.getLines() is not real fill.
        // If you want REAL fill, we can add a proper quad layer next.
        if (fillOpacity <= 0) return;

        int a = (int) Math.max(0, Math.min(255, fillOpacity));
        int fillArgb = (argb & 0x00FFFFFF) | (a << 24);

        // “fake fill”: inner box lines (gives a shaded look)
        drawBoxLines(matrices, consumers, b.expand(-0.02), fillArgb);
    }

    private static void drawBoxLines(MatrixStack matrices, VertexConsumerProvider consumers, Box b, int argb) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        double x1 = b.minX, y1 = b.minY, z1 = b.minZ;
        double x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;

        // bottom
        drawLine(matrices, vc, x1, y1, z1, x2, y1, z1, argb);
        drawLine(matrices, vc, x2, y1, z1, x2, y1, z2, argb);
        drawLine(matrices, vc, x2, y1, z2, x1, y1, z2, argb);
        drawLine(matrices, vc, x1, y1, z2, x1, y1, z1, argb);

        // top
        drawLine(matrices, vc, x1, y2, z1, x2, y2, z1, argb);
        drawLine(matrices, vc, x2, y2, z1, x2, y2, z2, argb);
        drawLine(matrices, vc, x2, y2, z2, x1, y2, z2, argb);
        drawLine(matrices, vc, x1, y2, z2, x1, y2, z1, argb);

        // verticals
        drawLine(matrices, vc, x1, y1, z1, x1, y2, z1, argb);
        drawLine(matrices, vc, x2, y1, z1, x2, y2, z1, argb);
        drawLine(matrices, vc, x2, y1, z2, x2, y2, z2, argb);
        drawLine(matrices, vc, x1, y1, z2, x1, y2, z2, argb);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer vc,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int argb) {

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMat = entry.getPositionMatrix();

        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        float nx = 0f, ny = 1f, nz = 0f;

        vc.vertex(posMat, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
        endVertex(vc);

        vc.vertex(posMat, (float) x2, (float) y2, (float) z2)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
        endVertex(vc);
    }

    private static void endVertex(VertexConsumer vc) {
        try {
            Method m = vc.getClass().getMethod("next");
            m.invoke(vc);
            return;
        } catch (Throwable ignored) {}

        try {
            Method m = vc.getClass().getMethod("endVertex");
            m.invoke(vc);
        } catch (Throwable ignored) {}
    }
}
