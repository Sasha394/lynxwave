package dev.client.modules.core.render.particles;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class RichStyleParticleRenderer {

    private static final Identifier GLOW_TEXTURE = Identifier.of("minecraft", "textures/particle/glow.png");
    private static final Identifier GLOW_TEXTURE_SECONDARY = Identifier.of("minecraft", "textures/particle/damage.png");

    private RichStyleParticleRenderer() {}

    public static RenderLayer getQuadsLayer() {
        return WorldParticleLayers.POSITION_COLOR_QUADS;
    }

    public static RenderLayer getLinesLayer() {
        return WorldParticleLayers.POSITION_COLOR_DEBUG_LINES;
    }

    public static RenderLayer getGlowLayer() {
        return RenderLayers.entityTranslucent(GLOW_TEXTURE);
    }

    public static RenderLayer getGlowLayerSecondary() {
        return RenderLayers.entityTranslucent(GLOW_TEXTURE_SECONDARY);
    }

    public static void drawCube(VertexConsumer b, Matrix4f m, int color, float s) {
        float h = s / 2.0F;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int bl = color & 0xFF;
        int a = color >> 24 & 0xFF;

        b.vertex(m, -h, h, -h).color(r, g, bl, a);
        b.vertex(m, -h, h, h).color(r, g, bl, a);
        b.vertex(m, h, h, h).color(r, g, bl, a);
        b.vertex(m, h, h, -h).color(r, g, bl, a);

        b.vertex(m, -h, -h, -h).color(r, g, bl, a);
        b.vertex(m, h, -h, -h).color(r, g, bl, a);
        b.vertex(m, h, -h, h).color(r, g, bl, a);
        b.vertex(m, -h, -h, h).color(r, g, bl, a);

        b.vertex(m, -h, h, h).color(r, g, bl, a);
        b.vertex(m, -h, -h, h).color(r, g, bl, a);
        b.vertex(m, h, -h, h).color(r, g, bl, a);
        b.vertex(m, h, h, h).color(r, g, bl, a);

        b.vertex(m, -h, h, -h).color(r, g, bl, a);
        b.vertex(m, h, h, -h).color(r, g, bl, a);
        b.vertex(m, h, -h, -h).color(r, g, bl, a);
        b.vertex(m, -h, -h, -h).color(r, g, bl, a);

        b.vertex(m, -h, h, -h).color(r, g, bl, a);
        b.vertex(m, -h, -h, -h).color(r, g, bl, a);
        b.vertex(m, -h, -h, h).color(r, g, bl, a);
        b.vertex(m, -h, h, h).color(r, g, bl, a);

        b.vertex(m, h, h, -h).color(r, g, bl, a);
        b.vertex(m, h, h, h).color(r, g, bl, a);
        b.vertex(m, h, -h, h).color(r, g, bl, a);
        b.vertex(m, h, -h, -h).color(r, g, bl, a);
    }

    public static void drawLines(VertexConsumer b, Matrix4f m, int c, float s) {
        float h = s / 2.0F;
        int r = c >> 16 & 0xFF;
        int g = c >> 8 & 0xFF;
        int bl = c & 0xFF;
        int a = c >> 24 & 0xFF;

        line(b, m, -h, -h, -h, h, -h, -h, r, g, bl, a);
        line(b, m, h, -h, -h, h, -h, h, r, g, bl, a);
        line(b, m, h, -h, h, -h, -h, h, r, g, bl, a);
        line(b, m, -h, -h, h, -h, -h, -h, r, g, bl, a);
        line(b, m, -h, h, -h, h, h, -h, r, g, bl, a);
        line(b, m, h, h, -h, h, h, h, r, g, bl, a);
        line(b, m, h, h, h, -h, h, h, r, g, bl, a);
        line(b, m, -h, h, h, -h, h, -h, r, g, bl, a);
        line(b, m, -h, -h, -h, -h, h, -h, r, g, bl, a);
        line(b, m, h, -h, -h, h, h, -h, r, g, bl, a);
        line(b, m, h, -h, h, h, h, h, r, g, bl, a);
        line(b, m, -h, -h, h, -h, h, h, r, g, bl, a);
    }

    private static void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int bl, int a) {
        // Слой POSITION_COLOR_DEBUG_LINES использует формат POSITION_COLOR (DEBUG_LINES).
        b.vertex(m, x1, y1, z1).color(r, g, bl, a);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a);
    }

    public static void drawGlow(VertexConsumer buffer, Matrix4f matrix, int color, int alpha, float size) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float half = size / 2.0f;
        // Слой WORLD_PARTICLES_GLOW использует формат POSITION_TEXTURE_COLOR.
        buffer.vertex(matrix, -half, -half, 0).texture(0, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, -half, half, 0).texture(0, 1).color(r, g, b, alpha);
        buffer.vertex(matrix, half, half, 0).texture(1, 1).color(r, g, b, alpha);
        buffer.vertex(matrix, half, -half, 0).texture(1, 0).color(r, g, b, alpha);
    }
}
