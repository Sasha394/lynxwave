package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.render.ColorUtils;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class HitEffect extends Module implements ClientApi {

    private final List<WaveEffect> waves = new ArrayList<>();

    private final CheckBox syncWithTheme = new CheckBox("Синхрон с темой", () -> true).defaultValue(true).register(this);
    private final ModeElement colorMode = new ModeElement("Цвет удара", () -> !syncWithTheme.getEnabled()).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    private final ColorPicker hitColor = new ColorPicker("Цвет удара", () -> !syncWithTheme.getEnabled() && colorMode.isSelected("Свой")).set(-1).defaultValue(-1).register(this);
    private final ModeElement totemColorMode = new ModeElement("Цвет тотема", () -> !syncWithTheme.getEnabled()).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    private final ColorPicker totemColor = new ColorPicker("Цвет тотема", () -> !syncWithTheme.getEnabled() && totemColorMode.isSelected("Свой")).set(new Color(255, 215, 0).getRGB()).defaultValue(new Color(255, 215, 0).getRGB()).register(this);

    private static final int MAX_RADIUS = 15;
    private static final long WAVE_DURATION_MS = 1200L;

    public HitEffect() {
        super("HitEffect", ModuleCategory.Render, "Волновой эффект при ударе");
    }

    @Subscribe
    public void onAttack(PlayerAttackEvent event) {
        if (mc.player == null || mc.world == null || event.getEntity() == null) return;
        Vec3d pos = dev.client.api.compat.EntityCompat.pos(event.getEntity());
        int color = getHitColor();
        waves.add(new WaveEffect(pos, color));
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return;
        if (packet.getEntity(mc.world) != mc.player) return;
        Vec3d pos = dev.client.api.compat.EntityCompat.pos(mc.player);
        int color = getTotemColor();
        waves.add(new WaveEffect(pos, color));
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (waves.isEmpty()) return;

        MatrixStack ms = event.getMatrices();
        Vec3d cameraPos = mc.getEntityRenderDispatcher().camera.getCameraPos();

        ms.push();
        ms.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(RenderSystem.SRC_ALPHA, RenderSystem.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        Matrix4f matrix = ms.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        BufferBuilder fillBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (WaveEffect wave : waves) {
            wave.renderFill(fillBuffer, matrix);
        }
        var fillBuilt = fillBuffer.endNullable();
        if (fillBuilt != null) RenderCompat.draw(fillBuilt);

        BufferBuilder linesBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (WaveEffect wave : waves) {
            wave.renderOutline(linesBuffer, matrix);
        }
        var linesBuilt = linesBuffer.endNullable();
        if (linesBuilt != null) RenderCompat.draw(linesBuilt);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();

        waves.removeIf(w -> w.getProgress() >= 1.0f);
    }

    @Override
    public void onDisabled() {
        waves.clear();
    }

    private int getHitColor() {
        if (syncWithTheme.getEnabled() && Interface.INSTANCE != null) return Interface.INSTANCE.getMainColor();
        return colorMode.isSelected("Свой") ? hitColor.getColorRGBA() : (Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFFFFF);
    }

    private int getTotemColor() {
        if (syncWithTheme.getEnabled() && Interface.INSTANCE != null) return Interface.INSTANCE.getMainColor();
        return totemColorMode.isSelected("Свой") ? totemColor.getColorRGBA() : (Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFD700);
    }

    private static final class BlockOutline {
        final BlockPos pos;
        final double distance;

        BlockOutline(BlockPos pos, double distance) {
            this.pos = pos;
            this.distance = distance;
        }
    }

    private final class WaveEffect {
        final Vec3d centerPos;
        final int color;
        final long startTime = System.currentTimeMillis();
        final List<BlockOutline> blocks = new ArrayList<>();

        WaveEffect(Vec3d pos, int color) {
            this.centerPos = pos;
            this.color = color;
            BlockPos centerBlock = BlockPos.ofFloored(pos);

            for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
                for (int z = -MAX_RADIUS; z <= MAX_RADIUS; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance > MAX_RADIUS || distance < 1.0) continue;

                    BlockPos found = null;
                    for (int y = 3; y >= -10; y--) {
                        BlockPos check = centerBlock.add(x, y, z);
                        if (mc.world != null && !mc.world.getBlockState(check).isAir()) {
                            found = check;
                            break;
                        }
                    }
                    if (found != null) blocks.add(new BlockOutline(found, distance));
                }
            }
        }

        float getProgress() {
            return Math.min(1f, (System.currentTimeMillis() - startTime) / (float) WAVE_DURATION_MS);
        }

        void renderFill(BufferBuilder builder, Matrix4f matrix) {
            float progress = getProgress();
            float currentRadius = progress * MAX_RADIUS;
            float waveThickness = 1.0f;

            for (BlockOutline block : blocks) {
                float distDiff = Math.abs((float) block.distance - currentRadius);
                if (distDiff > waveThickness) continue;

                float alpha = 1.0f - distDiff / waveThickness;
                alpha = (float) Math.pow(alpha, 0.5);
                float fadeOut = 1.0f;
                if (progress > 0.7f) fadeOut = 1.0f - (progress - 0.7f) / 0.3f;
                alpha *= fadeOut;
                if (alpha <= 0.05f) continue;

                float scale = progress > 0.7f ? (0.5f + 0.5f * fadeOut) : 1.0f;
                int fillColor = ColorUtils.setAlpha(color, (int) (alpha * 40) & 0xFF);
                drawBoxQuads(builder, matrix, block.pos, scale, fillColor);
            }
        }

        void renderOutline(BufferBuilder builder, Matrix4f matrix) {
            float progress = getProgress();
            float currentRadius = progress * MAX_RADIUS;
            float waveThickness = 1.0f;

            for (BlockOutline block : blocks) {
                float distDiff = Math.abs((float) block.distance - currentRadius);
                if (distDiff > waveThickness) continue;

                float alpha = 1.0f - distDiff / waveThickness;
                alpha = (float) Math.pow(alpha, 0.5);
                float fadeOut = 1.0f;
                if (progress > 0.7f) fadeOut = 1.0f - (progress - 0.7f) / 0.3f;
                alpha *= fadeOut;
                if (alpha <= 0.05f) continue;

                float scale = progress > 0.7f ? (0.5f + 0.5f * fadeOut) : 1.0f;
                int lineColor = ColorUtils.setAlpha(color, (int) (alpha * 255) & 0xFF);
                drawBoxLines(builder, matrix, block.pos, scale, lineColor);
            }
        }

        private void drawBoxQuads(BufferBuilder builder, Matrix4f matrix, BlockPos pos, float scale, int color) {
            double half = 0.5 * scale;
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            float r = ColorUtils.redf(color);
            float g = ColorUtils.greenf(color);
            float b = ColorUtils.bluef(color);
            float a = ColorUtils.alphaf(color);

            // bottom
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
            // top
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            // north
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            // south
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
            // west
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x - half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            // east
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z - half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y + half), (float) (z + half)).color(r, g, b, a);
            builder.vertex(matrix, (float) (x + half), (float) (y - half), (float) (z + half)).color(r, g, b, a);
        }

        private void drawBoxLines(BufferBuilder builder, Matrix4f matrix, BlockPos pos, float scale, int color) {
            double half = 0.5 * scale;
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            float r = ColorUtils.redf(color);
            float g = ColorUtils.greenf(color);
            float b = ColorUtils.bluef(color);
            float a = ColorUtils.alphaf(color);

            float x1 = (float) (x - half);
            float x2 = (float) (x + half);
            float y1 = (float) (y - half);
            float y2 = (float) (y + half);
            float z1 = (float) (z - half);
            float z2 = (float) (z + half);

            line(builder, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
            line(builder, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
            line(builder, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
            line(builder, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
            line(builder, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
            line(builder, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
            line(builder, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
            line(builder, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
            line(builder, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
            line(builder, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
            line(builder, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
            line(builder, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
        }

        private void line(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
            builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            builder.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        }
    }
}
