package dev.client.api.nullcry.render.core.renderers.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.client.api.nullcry.render.core.GuiQuadData;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import dev.client.api.nullcry.render.core.PixelSnap;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.api.nullcry.render.core.RoundedRectUniforms;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.IRenderer;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public record BuiltRectangle(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float Smoothness
    ) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        x = PixelSnap.position(x);
        y = PixelSnap.position(y);
        float width = PixelSnap.length(this.size.width());
        float height = PixelSnap.length(this.size.height());
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        float radius = GuiQuadData.maxRadius(this.radius);
        float smoothness = this.Smoothness;

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.ROUNDED_RECT);
        GuiQuadData.vertex(builder, matrix, x, y, z, 0.0f, 0.0f, this.color.color1(), 0.0f, 0.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x, y + height, z, 0.0f, 1.0f, this.color.color2(), 0.0f, 1.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x + width, y + height, z, 1.0f, 1.0f, this.color.color3(), 1.0f, 1.0f, radius, smoothness);
        GuiQuadData.vertex(builder, matrix, x + width, y, z, 1.0f, 0.0f, this.color.color4(), 1.0f, 0.0f, radius, smoothness);

        GpuBuffer config = RoundedRectUniforms.rounded(width, height, this.radius, smoothness);
        RenderCoreCompat.draw(
                LynxRenderPipelines.ROUNDED_RECT,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform(RoundedRectUniforms.ROUNDED_CONFIG, config)
                }
        );
    }
}
