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

import java.awt.Color;

public class HandBlurRenderer {
    private static final int CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private Framebuffer withHandFbo;
    private Framebuffer beforeHandFbo;
    private final float blurRadius;
    private final float threshold;
    private final float[] tintColor;
    private final int screenW;
    private final int screenH;

    public HandBlurRenderer(Framebuffer withHandFbo, Framebuffer beforeHandFbo, float blurRadius, float threshold, float[] tintColor, int screenW, int screenH) {
        this.withHandFbo = withHandFbo;
        this.beforeHandFbo = beforeHandFbo;
        this.blurRadius = blurRadius;
        this.threshold = threshold;
        this.tintColor = tintColor;
        this.screenW = screenW;
        this.screenH = screenH;
    }

    public void render(Matrix4f matrix) {
        if (this.withHandFbo == null || this.withHandFbo.getColorAttachmentView() == null
                || this.beforeHandFbo == null || this.beforeHandFbo.getColorAttachmentView() == null) {
            return;
        }

        int white = new Color(255, 255, 255, 255).getRGB();
        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.HAND_BLUR);
        GuiQuadData.vertex(builder, matrix, 0.0F, (float) this.screenH, 0.0F, 0.0F, 1.0F, white, 0.0F, 1.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, white, 0.0F, 0.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, (float) this.screenW, 0.0F, 0.0F, 1.0F, 0.0F, white, 1.0F, 0.0F, 0.0F, 1.0F);
        GuiQuadData.vertex(builder, matrix, (float) this.screenW, (float) this.screenH, 0.0F, 1.0F, 1.0F, white, 1.0F, 1.0F, 0.0F, 1.0F);

        GpuBuffer config = createConfig();
        RenderCoreCompat.draw(
                LynxRenderPipelines.HAND_BLUR,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform("HandBlurConfig", config)
                },
                RenderCoreCompat.binding(RenderCoreCompat.SAMPLER0, this.withHandFbo.getColorAttachmentView(), null),
                RenderCoreCompat.binding(RenderCoreCompat.SAMPLER1, this.beforeHandFbo.getColorAttachmentView(), null)
        );
    }

    public void updateFramebuffers(Framebuffer withHand, Framebuffer beforeHand) {
        this.withHandFbo = withHand;
        this.beforeHandFbo = beforeHand;
    }

    public void updateColor(float r, float g, float b, float a) {
        this.tintColor[0] = r;
        this.tintColor[1] = g;
        this.tintColor[2] = b;
        this.tintColor[3] = a;
    }

    private GpuBuffer createConfig() {
        float r = this.tintColor != null && this.tintColor.length > 0 ? this.tintColor[0] : 1.0f;
        float g = this.tintColor != null && this.tintColor.length > 1 ? this.tintColor[1] : 1.0f;
        float b = this.tintColor != null && this.tintColor.length > 2 ? this.tintColor[2] : 1.0f;
        float a = this.tintColor != null && this.tintColor.length > 3 ? this.tintColor[3] : 1.0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(this.blurRadius, this.threshold, this.screenW, this.screenH)
                    .putVec4(r, g, b, a);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave hand blur config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }
}
