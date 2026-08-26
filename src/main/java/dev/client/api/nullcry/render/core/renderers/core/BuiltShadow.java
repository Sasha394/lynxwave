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
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

public record BuiltShadow(
        SizeState size,
        QuadColorState color,
        QuadRadiusState radiusState,
        float softness,
        float shadowRadius
) implements IRenderer {

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        x = PixelSnap.position(x);
        y = PixelSnap.position(y);
        float width = PixelSnap.length(this.size.width());
        float height = PixelSnap.length(this.size.height());
        float radius = GuiQuadData.maxRadius(this.radiusState);
        float effectRadius = PixelSnap.length(this.shadowRadius);
        if (width <= 0.0f || height <= 0.0f || effectRadius <= 0.0f) {
            return;
        }

        float sx = x - effectRadius;
        float sy = y - effectRadius;
        float sw = width + effectRadius * 2.0f;
        float sh = height + effectRadius * 2.0f;

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.SHADOW);
        GuiQuadData.vertex(builder, matrix, sx, sy, z, 0.0f, 0.0f, color.color1(), 0.0f, 0.0f, radius, effectRadius);
        GuiQuadData.vertex(builder, matrix, sx, sy + sh, z, 0.0f, 1.0f, color.color2(), 0.0f, 1.0f, radius, effectRadius);
        GuiQuadData.vertex(builder, matrix, sx + sw, sy + sh, z, 1.0f, 1.0f, color.color3(), 1.0f, 1.0f, radius, effectRadius);
        GuiQuadData.vertex(builder, matrix, sx + sw, sy, z, 1.0f, 0.0f, color.color4(), 1.0f, 0.0f, radius, effectRadius);

        GpuBuffer config = RoundedRectUniforms.effect(sw, sh, this.radiusState, this.softness, effectRadius);
        RenderCoreCompat.draw(
                LynxRenderPipelines.SHADOW,
                builder.end(),
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform(RoundedRectUniforms.EFFECT_CONFIG, config)
                }
        );
    }
}
