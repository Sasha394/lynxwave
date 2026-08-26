package dev.client.api.nullcry.render.core.renderers.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.render.core.FramebufferCapture;
import dev.client.api.nullcry.render.core.GuiQuadData;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import dev.client.api.nullcry.render.core.PixelSnap;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.IRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public record BuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float Smoothness,
        float blurRadius
) implements IRenderer {
    private static final FramebufferCapture CAPTURE = new FramebufferCapture("lynxwave-blur-source");
    private static final int CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    public static void resetCaptureState() {
        CAPTURE.reset();
    }

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        if (this.blurRadius <= 0.0f) {
            return;
        }

        x = PixelSnap.position(x);
        y = PixelSnap.position(y);
        float width = PixelSnap.length(this.size.width());
        float height = PixelSnap.length(this.size.height());
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        int alphaMask = 0;
        alphaMask |= (this.color.color1() >>> 24) & 0xFF;
        alphaMask |= (this.color.color2() >>> 24) & 0xFF;
        alphaMask |= (this.color.color3() >>> 24) & 0xFF;
        alphaMask |= (this.color.color4() >>> 24) & 0xFF;
        if (alphaMask == 0) {
            return;
        }

        float radius = GuiQuadData.maxRadius(this.radius);
        float smoothness = Math.max(0.75f, this.Smoothness);

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.BLUR);
        GuiQuadData.vertex(builder, matrix, x, y, z, 0.0f, 0.0f, this.color.color1(), 0.0f, 0.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x, y + height, z, 0.0f, 1.0f, this.color.color2(), 0.0f, 1.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x + width, y + height, z, 1.0f, 1.0f, this.color.color3(), 1.0f, 1.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x + width, y, z, 1.0f, 0.0f, this.color.color4(), 1.0f, 0.0f, radius, smoothness);

        GpuBuffer config = createConfig(width, height, smoothness);
        RenderCoreCompat.drawWithFramebufferSampler(
                LynxRenderPipelines.BLUR,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform("BlurConfig", config)
                },
                CAPTURE
        );
    }

    private GpuBuffer createConfig(float width, float height, float smoothness) {
        QuadRadiusState radius = this.radius != null ? this.radius : QuadRadiusState.NO_ROUND;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        Window window = client.getWindow();
        float scale = Math.max(1.0f, (float) window.getScaleFactor());
        float inputWidth = framebuffer != null && framebuffer.textureWidth > 0 ? framebuffer.textureWidth : window.getFramebufferWidth();
        float inputHeight = framebuffer != null && framebuffer.textureHeight > 0 ? framebuffer.textureHeight : window.getFramebufferHeight();
        float quality = Math.max(0.0f, this.blurRadius * scale);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(Math.max(1.0f, inputWidth), Math.max(1.0f, inputHeight), quality, smoothness)
                    .putVec4(width, height, 0.0f, 0.0f)
                    .putVec4(
                            radius.radius1(),
                            radius.radius2(),
                            radius.radius3(),
                            radius.radius4()
                    );
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave blur config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }
}
