package dev.client.api.nullcry.render.core.renderers.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.render.core.GuiQuadData;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public class HandHighlightRenderer {
    private static final int CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private final Framebuffer withHandFbo;
    private final Framebuffer beforeHandFbo;
    private final float time;
    private final float threshold;
    private final float opacity;
    private final float[] baseColor;
    private final int screenW;
    private final int screenH;
    private final String shaderName;

    public HandHighlightRenderer(Framebuffer withHandFbo, Framebuffer beforeHandFbo, float time, float threshold, float opacity, float[] baseColor, int screenW, int screenH) {
        this(withHandFbo, beforeHandFbo, time, threshold, opacity, baseColor, screenW, screenH, "hand_highlight");
    }

    public HandHighlightRenderer(Framebuffer withHandFbo, Framebuffer beforeHandFbo, float time, float threshold, float opacity, float[] baseColor, int screenW, int screenH, String shaderName) {
        this.withHandFbo = withHandFbo;
        this.beforeHandFbo = beforeHandFbo;
        this.time = time;
        this.threshold = threshold;
        this.opacity = opacity;
        this.baseColor = baseColor;
        this.screenW = screenW;
        this.screenH = screenH;
        this.shaderName = shaderName;
    }

    public void render(Matrix4f matrix) {
        if (this.withHandFbo == null || this.withHandFbo.getColorAttachmentView() == null
                || this.beforeHandFbo == null || this.beforeHandFbo.getColorAttachmentView() == null) {
            return;
        }

        int white = 0xFFFFFFFF;
        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.HAND_HIGHLIGHT);
        GuiQuadData.vertex(builder, matrix, 0.0F, (float) this.screenH, 0.0F, 0.0F, 1.0F, white, 0.0F, 1.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, white, 0.0F, 0.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, (float) this.screenW, 0.0F, 0.0F, 1.0F, 0.0F, white, 1.0F, 0.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, (float) this.screenW, (float) this.screenH, 0.0F, 1.0F, 1.0F, white, 1.0F, 1.0F, 0.0F, 1.0F);

        GpuBuffer config = createConfig();
        RenderCoreCompat.draw(
                LynxRenderPipelines.HAND_HIGHLIGHT,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform("HandHighlightConfig", config)
                },
                RenderCoreCompat.binding(RenderCoreCompat.SAMPLER0, this.withHandFbo.getColorAttachmentView(), null),
                RenderCoreCompat.binding(RenderCoreCompat.SAMPLER1, this.beforeHandFbo.getColorAttachmentView(), null)
        );
    }

    private GpuBuffer createConfig() {
        float r = this.baseColor != null && this.baseColor.length > 0 ? this.baseColor[0] : 1.0f;
        float g = this.baseColor != null && this.baseColor.length > 1 ? this.baseColor[1] : 1.0f;
        float b = this.baseColor != null && this.baseColor.length > 2 ? this.baseColor[2] : 1.0f;
        // Resolution для эффекта = пиксели фреймбуфера (как gl_FragCoord), а не
        // GUI-масштабированный размер — иначе эффект тайлится в scale раз.
        var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        float fbW = Math.max(1.0f, window.getFramebufferWidth());
        float fbH = Math.max(1.0f, window.getFramebufferHeight());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(this.time, this.threshold, this.opacity, modeId(this.shaderName))
                    .putVec4(r, g, b, 1.0f)
                    .putVec4(fbW, fbH, 0.0f, 0.0f);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave hand highlight config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private static int modeId(String name) {
        if ("hand_wave".equals(name)) return 2;
        if ("hand_stars2".equals(name)) return 3;
        if ("hand_bh1".equals(name)) return 4;
        if ("hand_bh2".equals(name)) return 5;
        if ("hand_bh3".equals(name)) return 6;
        if ("hand_bh4".equals(name)) return 7;
        if ("hand_bh5".equals(name)) return 8;
        if ("hand_bh6".equals(name)) return 9;
        if ("hand_bh7".equals(name)) return 10;
        if ("hand_bh8".equals(name)) return 11;
        if ("hand_bh_space".equals(name)) return 12;
        if ("hand_outline_right".equals(name)) return 13;
        if ("hand_outline_left".equals(name)) return 14;
        return 1;
    }
}
