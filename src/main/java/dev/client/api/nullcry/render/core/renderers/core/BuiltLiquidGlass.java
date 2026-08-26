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
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public record BuiltLiquidGlass(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float cornerSmoothness,
        float globalAlpha,
        float fresnelPower,
        int fresnelColor,
        float baseAlpha,
        boolean fresnelInvert,
        float fresnelMix,
        float distortStrength
) implements IRenderer {
    private static final FramebufferCapture CAPTURE = new FramebufferCapture("lynxwave-liquid-glass-source");
    private static final int CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    public static void resetCaptureState() {
        CAPTURE.reset();
    }

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
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

        float clampedGlobalAlpha = clamp(this.globalAlpha, 0.0f, 1.0f);
        if (clampedGlobalAlpha <= 0.0f) {
            return;
        }

        float radius = GuiQuadData.maxRadius(this.radius);
        float alpha = Math.max(0.0f, Math.min(1.0f, clampedGlobalAlpha * this.baseAlpha));

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.LIQUID_GLASS);
        GuiQuadData.vertex(builder, matrix, x, y, z, 0.0f, 0.0f, this.color.color1(), 0.0f, 0.0f, radius, alpha);
        GuiQuadData.vertex(builder, matrix, x, y + height, z, 0.0f, 1.0f, this.color.color2(), 0.0f, 1.0f, radius, alpha);
        GuiQuadData.vertex(builder, matrix, x + width, y + height, z, 1.0f, 1.0f, this.color.color3(), 1.0f, 1.0f, radius, alpha);
        GuiQuadData.vertex(builder, matrix, x + width, y, z, 1.0f, 0.0f, this.color.color4(), 1.0f, 0.0f, radius, alpha);

        GpuBuffer config = createConfig(width, height, clampedGlobalAlpha);
        RenderCoreCompat.drawWithFramebufferSampler(
                LynxRenderPipelines.LIQUID_GLASS,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform("LiquidGlassConfig", config)
                },
                CAPTURE
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private GpuBuffer createConfig(float width, float height, float clampedGlobalAlpha) {
        QuadRadiusState radius = this.radius != null ? this.radius : QuadRadiusState.NO_ROUND;
        float fresnelR = ((this.fresnelColor >> 16) & 0xFF) / 255.0f;
        float fresnelG = ((this.fresnelColor >> 8) & 0xFF) / 255.0f;
        float fresnelB = (this.fresnelColor & 0xFF) / 255.0f;
        float fresnelA = ((this.fresnelColor >>> 24) & 0xFF) / 255.0f;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(width, height, clamp(this.smoothness, 0.0f, 4.0f), clamp(this.cornerSmoothness, 0.5f, 20.0f))
                    .putVec4(clampedGlobalAlpha, Math.max(0.0f, this.fresnelPower), clamp(this.baseAlpha, 0.0f, 1.0f), this.fresnelInvert ? 1.0f : 0.0f)
                    .putVec4(fresnelR, fresnelG, fresnelB, fresnelA)
                    .putVec4(clamp(this.fresnelMix, 0.0f, 1.0f), this.distortStrength, 0.0f, 0.0f)
                    .putVec4(
                            Math.max(0.0f, radius.radius1()),
                            Math.max(0.0f, radius.radius2()),
                            Math.max(0.0f, radius.radius3()),
                            Math.max(0.0f, radius.radius4())
                    );
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave liquid glass config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }
}
