package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.screen.Screen;

public final class FramebufferCapture {
    private final String name;
    private SimpleFramebuffer framebuffer;
    private Screen screen;
    private long frameId = -1L;
    private int width = -1;
    private int height = -1;
    private boolean dirty = true;

    public FramebufferCapture(String name) {
        this.name = name;
    }

    public void reset() {
        this.screen = null;
        this.frameId = -1L;
        this.width = -1;
        this.height = -1;
        this.dirty = true;
    }

    public GpuTextureView getColorView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }

        Framebuffer source = client.getFramebuffer();
        if (!isUsable(source)) {
            return null;
        }

        this.framebuffer = ensureFramebuffer(this.framebuffer, source);
        if (this.framebuffer == null || !isUsable(this.framebuffer)) {
            return null;
        }

        Screen currentScreen = client.currentScreen;
        long currentFrameId = RenderCoreCompat.getCaptureFrameId();
        boolean sizeChanged = this.width != source.textureWidth || this.height != source.textureHeight;
        boolean needsCapture = this.dirty || sizeChanged || this.screen != currentScreen || this.frameId != currentFrameId;

        if (needsCapture) {
            if (!copyColor(source, this.framebuffer)) {
                return null;
            }
            this.width = source.textureWidth;
            this.height = source.textureHeight;
            this.screen = currentScreen;
            this.frameId = currentFrameId;
            this.dirty = false;
        }

        return this.framebuffer.getColorAttachmentView();
    }

    private SimpleFramebuffer ensureFramebuffer(SimpleFramebuffer current, Framebuffer source) {
        if (current == null) {
            return new SimpleFramebuffer(this.name, source.textureWidth, source.textureHeight, false);
        }
        if (current.textureWidth != source.textureWidth || current.textureHeight != source.textureHeight) {
            current.resize(source.textureWidth, source.textureHeight);
            this.dirty = true;
        }
        return current;
    }

    private static boolean isUsable(Framebuffer framebuffer) {
        return framebuffer != null
                && framebuffer.getColorAttachment() != null
                && framebuffer.getColorAttachmentView() != null
                && framebuffer.textureWidth > 0
                && framebuffer.textureHeight > 0
                && !framebuffer.getColorAttachment().isClosed();
    }

    private static boolean copyColor(Framebuffer source, Framebuffer target) {
        GpuTexture sourceColor = source.getColorAttachment();
        GpuTexture targetColor = target.getColorAttachment();
        if (sourceColor == null || targetColor == null || sourceColor.isClosed() || targetColor.isClosed()) {
            return false;
        }
        if ((sourceColor.usage() & GpuTexture.USAGE_COPY_SRC) == 0 || (targetColor.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            return false;
        }

        int copyWidth = Math.min(source.textureWidth, target.textureWidth);
        int copyHeight = Math.min(source.textureHeight, target.textureHeight);
        if (copyWidth <= 0 || copyHeight <= 0) {
            return false;
        }

        RenderSystem.assertOnRenderThread();
        RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(sourceColor, targetColor, 0, 0, 0, 0, 0, copyWidth, copyHeight);
        return true;
    }
}
