package dev.client.api.nullcry.render.core.renderers.core.handflame;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

/**
 * Захват кадра «до руки» / «с рукой» (цвет + глубина) для режима «Пламя» у
 * {@link dev.client.modules.core.render.ShaderHand}. Копирует основной фреймбуфер
 * в собственные текстуры на этапах {@code renderHand} HEAD/TAIL — отсюда строится
 * маска силуэта руки ({@code mask_diff}). Перенесено из проекта w3y.soul.
 */
public final class PostEffectContext {

    public static final int PING_PONG_SLOTS = 4;

    private final MinecraftClient client = MinecraftClient.getInstance();

    private GpuTexture sceneBeforeTex, sceneAfterTex;
    private GpuTextureView sceneBeforeView, sceneAfterView;

    private GpuTexture depthBeforeTex, depthAfterTex;
    private GpuTextureView depthBeforeView, depthAfterView;

    private GpuTexture maskTex;
    private GpuTextureView maskView;

    private final GpuTexture[] pingPongTex = new GpuTexture[PING_PONG_SLOTS];
    private final GpuTextureView[] pingPongView = new GpuTextureView[PING_PONG_SLOTS];

    private int width = 0;
    private int height = 0;
    private boolean sceneCaptured = false;
    private boolean afterCaptured = false;

    public PostEffectContext() { }

    public int width()  { return width; }
    public int height() { return height; }

    public GpuTextureView sceneBefore() { return sceneBeforeView; }
    public GpuTextureView sceneAfter()  { return sceneAfterView; }
    public GpuTextureView depthBefore() { return depthBeforeView; }
    public GpuTextureView depthAfter()  { return depthAfterView; }
    public GpuTextureView mask()        { return maskView; }

    public GpuTextureView pingPongView(int slot) { return pingPongView[slot]; }

    public boolean isSceneCaptured() { return sceneCaptured; }
    public boolean isAfterCaptured() { return afterCaptured; }

    public boolean captureBefore() {
        if (sceneCaptured) return true;
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return false;

        ensureAllocated(fb.textureWidth, fb.textureHeight);

        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        enc.copyTextureToTexture(fb.getColorAttachment(), sceneBeforeTex, 0, 0, 0, 0, 0, width, height);
        if (fb.getDepthAttachment() != null) {
            enc.copyTextureToTexture(fb.getDepthAttachment(), depthBeforeTex, 0, 0, 0, 0, 0, width, height);
        }
        sceneCaptured = true;
        afterCaptured = false;
        return true;
    }

    public boolean captureAfter() {
        if (!sceneCaptured) return false;
        if (afterCaptured) return true;
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return false;

        ensureAllocated(fb.textureWidth, fb.textureHeight);

        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        enc.copyTextureToTexture(fb.getColorAttachment(), sceneAfterTex, 0, 0, 0, 0, 0, width, height);
        if (fb.getDepthAttachment() != null) {
            enc.copyTextureToTexture(fb.getDepthAttachment(), depthAfterTex, 0, 0, 0, 0, 0, width, height);
        }
        afterCaptured = true;
        return true;
    }

    public void endFrame() {
        sceneCaptured = false;
        afterCaptured = false;
    }

    public void close() {
        releaseAll();
        width = 0;
        height = 0;
    }

    private void ensureAllocated(int w, int h) {
        if (w == width && h == height && sceneBeforeTex != null) return;
        releaseAll();

        sceneBeforeTex  = createColor("lynxwave:hand_flame_scene_before", w, h);
        sceneBeforeView = RenderSystem.getDevice().createTextureView(sceneBeforeTex);
        sceneAfterTex   = createColor("lynxwave:hand_flame_scene_after", w, h);
        sceneAfterView  = RenderSystem.getDevice().createTextureView(sceneAfterTex);

        depthBeforeTex  = createDepth("lynxwave:hand_flame_depth_before", w, h);
        depthBeforeView = RenderSystem.getDevice().createTextureView(depthBeforeTex);
        depthAfterTex   = createDepth("lynxwave:hand_flame_depth_after", w, h);
        depthAfterView  = RenderSystem.getDevice().createTextureView(depthAfterTex);

        maskTex  = createColor("lynxwave:hand_flame_mask", w, h);
        maskView = RenderSystem.getDevice().createTextureView(maskTex);

        for (int i = 0; i < PING_PONG_SLOTS; i++) {
            pingPongTex[i]  = createColor("lynxwave:hand_flame_pingpong_" + i, w, h);
            pingPongView[i] = RenderSystem.getDevice().createTextureView(pingPongTex[i]);
        }

        width = w;
        height = h;
    }

    private static GpuTexture createColor(String label, int w, int h) {
        return RenderSystem.getDevice().createTexture(
                () -> label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, w, h, 1, 1);
    }

    private static GpuTexture createDepth(String label, int w, int h) {
        return RenderSystem.getDevice().createTexture(
                () -> label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, w, h, 1, 1);
    }

    private void releaseAll() {
        sceneBeforeView = closeView(sceneBeforeView);
        sceneBeforeTex  = closeTex(sceneBeforeTex);
        sceneAfterView  = closeView(sceneAfterView);
        sceneAfterTex   = closeTex(sceneAfterTex);

        depthBeforeView = closeView(depthBeforeView);
        depthBeforeTex  = closeTex(depthBeforeTex);
        depthAfterView = closeView(depthAfterView);
        depthAfterTex  = closeTex(depthAfterTex);

        maskView = closeView(maskView);
        maskTex  = closeTex(maskTex);

        for (int i = 0; i < PING_PONG_SLOTS; i++) {
            pingPongView[i] = closeView(pingPongView[i]);
            pingPongTex[i]  = closeTex(pingPongTex[i]);
        }
    }

    private static GpuTexture closeTex(GpuTexture t) {
        if (t != null) t.close();
        return null;
    }

    private static GpuTextureView closeView(GpuTextureView v) {
        if (v != null) v.close();
        return null;
    }
}
