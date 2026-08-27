package dev.client.api.nullcry.uiClient.draggables.core.music;

import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.render.ColorUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class LyricParticle3D {
    private final String text;
    private final Vec3d basePosition;
    private final long spawnTimeMs;
    private final long durationMs;

    public LyricParticle3D(String text, Vec3d basePosition, long spawnTimeMs, long durationMs) {
        this.text = text;
        this.basePosition = basePosition;
        this.spawnTimeMs = spawnTimeMs;
        this.durationMs = durationMs;
    }

    public Vec3d getBasePosition() {
        return basePosition;
    }

    public boolean isDead(long nowMs) {
        return nowMs - spawnTimeMs >= durationMs;
    }

    public void render(MatrixStack matrices, Camera camera, int themeColor, long nowMs) {
        long elapsed = nowMs - spawnTimeMs;
        if (elapsed < 0 || elapsed > durationMs || text.isBlank()) return;

        float progress = MathHelper.clamp((float) elapsed / durationMs, 0f, 1f);
        float alpha;
        float animationScale;
        double yOffset;
        if (progress < 0.15f) {
            float t = easeOutCubic(progress / 0.15f);
            alpha = t;
            animationScale = 0.93f + 0.07f * easeOutBack(progress / 0.15f);
            yOffset = -0.06 * (1f - t);
        } else if (progress < 0.80f) {
            alpha = 1f;
            animationScale = 1f;
            yOffset = 0;
        } else {
            float t = (progress - 0.80f) / 0.20f;
            alpha = 1f - t * t * t;
            animationScale = 1f - 0.03f * t;
            yOffset = 0.04 * t * t * t;
        }
        if (alpha <= 0.001f) return;

        Vec3d cameraPos = camera.getCameraPos();
        matrices.push();
        matrices.translate(
                basePosition.x - cameraPos.x,
                basePosition.y + yOffset - cameraPos.y,
                basePosition.z - cameraPos.z
        );
        matrices.multiply(camera.getRotation());
        float scale = 0.035f * animationScale;
        matrices.scale(scale, -scale, scale);

        MinecraftClient client = MinecraftClient.getInstance();
        float width = client.textRenderer.getWidth(text);
        int color = ColorUtils.setAlpha(themeColor, MathHelper.clamp((int) (alpha * 255f), 0, 255));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        client.textRenderer.draw(
                text,
                -width / 2f,
                -4f,
                color,
                false,
                matrices.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                15728880
        );
        consumers.draw();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }
}
