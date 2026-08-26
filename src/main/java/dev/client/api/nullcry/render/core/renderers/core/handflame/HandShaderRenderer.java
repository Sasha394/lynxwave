package dev.client.api.nullcry.render.core.renderers.core.handflame;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

/**
 * Оркестратор режима «Пламя» у {@link dev.client.modules.core.render.ShaderHand}.
 *
 * <p>Кадр «до руки» / «с рукой» (цвет + глубина) захватывается через
 * {@link PostEffectContext} на этапах {@code renderHand} HEAD/TAIL. Далее цепочка
 * проходов:
 * <ol>
 *     <li>{@code mask_diff} — маска силуэта руки (по разнице глубины, запасной путь —
 *         по разнице цвета);</li>
 *     <li>{@code hand_trail} — поле шлейфа с обратной связью (ping-pong, огонь
 *         поднимается/тянется за движением и взмахом);</li>
 *     <li>dual-Kawase blur — мягкий дым;</li>
 *     <li>{@code hand_fire} — композит «сцена + пламя» прямо на главный фреймбуфер.</li>
 * </ol>
 * Перенесено из проекта w3y.soul, адаптировано под рендер-API lynxwave.
 */
public class HandShaderRenderer {
    private static HandShaderRenderer instance;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final PostEffectContext ctx = new PostEffectContext();
    private HandFirePipeline firePipeline;
    private HandTrailPipeline handTrail;
    private KawaseBlurPipeline kawaseBlur;
    private MaskDiffPipeline maskDiff;

    private boolean initialized = false;
    private boolean enabled = false;
    private float intensity = 1.0f;
    private float radius = 8.0f;
    private float speed = 1.0f;
    private float flameHeight = 1.0f;
    private float trailBlurRadius = 1.55f;
    private float trailSoftness = 1.35f;
    private float trailFade = 0.84f;
    private float handBlurRadius = 1.45f;
    private float handSoftness = 1.25f;
    private float handFade = 0.68f;
    private float smoke = 0.55f;
    private float colorR = 0.6f, colorG = 0.2f, colorB = 1.0f, colorA = 1.0f;
    private float attackImpulse = 0.0f;
    private float slashImpulse = 0.0f;
    private float slashDirection = 1.0f;

    private long startTimeNanos = System.nanoTime();

    public static HandShaderRenderer getInstance() {
        if (instance == null) {
            instance = new HandShaderRenderer();
        }
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) return;
        closePipelines();
        firePipeline = new HandFirePipeline();
        handTrail = new HandTrailPipeline();
        kawaseBlur = new KawaseBlurPipeline();
        maskDiff = new MaskDiffPipeline();
        startTimeNanos = System.nanoTime();
        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            ensureInitialized();
        } else {
            attackImpulse = 0.0f;
            slashImpulse = 0.0f;
            ctx.endFrame();
            if (handTrail != null) handTrail.clear();
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setIntensity(float v) { this.intensity = v; }
    public void setRadius(float v) { this.radius = v; }
    public void setBlurRadius(float v) { this.trailBlurRadius = v; this.handBlurRadius = v; }
    public void setTrailBlurRadius(float v) { this.trailBlurRadius = v; }
    public void setHandBlurRadius(float v) { this.handBlurRadius = v; }
    public void setSpeed(float v) { this.speed = v; }
    public void setFlameHeight(float v) { this.flameHeight = v; }
    public void setSoftness(float v) { this.trailSoftness = v; this.handSoftness = v; }
    public void setTrailSoftness(float v) { this.trailSoftness = v; }
    public void setHandSoftness(float v) { this.handSoftness = v; }
    public void setTrailFade(float v) { this.trailFade = v; }
    public void setHandFade(float v) { this.handFade = v; }
    public void setSmoke(float v) { this.smoke = v; }
    public void pulseAttack() {
        this.attackImpulse = 1.0f;
        this.slashImpulse = 1.0f;
        this.slashDirection = -this.slashDirection;
    }
    public void setColor(float r, float g, float b, float a) {
        this.colorR = r; this.colorG = g; this.colorB = b; this.colorA = a;
    }

    public void captureSceneBeforeHands() {
        if (!enabled) return;
        ensureInitialized();
        ctx.captureBefore();
    }

    public void captureSceneAfterHands() {
        if (!enabled) return;
        ensureInitialized();
        ctx.captureAfter();
    }

    public boolean renderFireEffect() {
        if (!enabled || !ctx.isAfterCaptured()) return false;
        ensureInitialized();

        try {
            Framebuffer fb = client.getFramebuffer();
            if (fb == null || fb.getColorAttachment() == null) return false;

            int w = ctx.width();
            int h = ctx.height();

            maskDiff.createMask(ctx.mask(), ctx.sceneBefore(), ctx.sceneAfter(), ctx.depthBefore(), ctx.depthAfter(), w, h);

            float time = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0f;

            float swing = 0.0f;
            if (client.player != null && client.player.handSwinging) {
                swing = 1.0f - Math.min(1.0f, client.player.handSwingProgress);
            }

            float activity = Math.max(attackImpulse, swing);
            float slash = slashImpulse;
            attackImpulse *= handFade;
            slashImpulse *= trailFade;
            if (attackImpulse < 0.01f) attackImpulse = 0.0f;
            if (slashImpulse < 0.01f) slashImpulse = 0.0f;

            GpuTextureView trailView = handTrail.update(ctx.sceneAfter(), ctx.mask(), w, h, time, intensity, colorR, colorG, colorB, colorA, radius, speed, flameHeight, trailSoftness, trailBlurRadius, smoke, activity, slash, slashDirection, trailFade);

            if (trailView == null) return false;

            int blurIterations = handBlurRadius >= 2.15f ? 3 : (handBlurRadius >= 1.05f ? 2 : 1);
            GpuTextureView smokeView = kawaseBlur.blur(handTrail.texture(), trailView, w, h, blurIterations, Math.max(1.05f, handBlurRadius * 1.22f));

            if (smokeView == null) smokeView = trailView;

            firePipeline.composite(fb.getColorAttachmentView(), ctx.sceneAfter(), smokeView, ctx.mask(), w, h, time, intensity, colorR, colorG, colorB, colorA, radius, speed, flameHeight, handSoftness, handBlurRadius, smoke, activity);
            return true;
        } finally {
            ctx.endFrame();
        }
    }

    public void invalidate() {
        closePipelines();
        initialized = false;
    }

    public void close() {
        closePipelines();
        ctx.close();
        initialized = false;
    }

    private void closePipelines() {
        if (firePipeline != null) {
            firePipeline.close();
            firePipeline = null;
        }
        if (handTrail != null) {
            handTrail.close();
            handTrail = null;
        }
        if (kawaseBlur != null) {
            kawaseBlur.close();
            kawaseBlur = null;
        }
        if (maskDiff != null) {
            maskDiff.close();
            maskDiff = null;
        }
    }
}
