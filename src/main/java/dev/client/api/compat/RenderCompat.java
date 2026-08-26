package dev.client.api.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public final class RenderCompat {
    private static final Map<VertexFormat.DrawMode, RenderPipeline> POSITION_COLOR_PIPELINES =
            new EnumMap<>(VertexFormat.DrawMode.class);
    private static final Map<VertexFormat.DrawMode, RenderPipeline> POSITION_COLOR_NO_DEPTH_WRITE_PIPELINES =
            new EnumMap<>(VertexFormat.DrawMode.class);
    private static final Map<VertexFormat.DrawMode, RenderPipeline> POSITION_COLOR_ADDITIVE_NO_DEPTH_WRITE_PIPELINES =
            new EnumMap<>(VertexFormat.DrawMode.class);
    private static final Map<VertexFormat.DrawMode, RenderPipeline> POSITION_TEXTURE_COLOR_PIPELINES =
            new EnumMap<>(VertexFormat.DrawMode.class);

    private RenderCompat() {
    }

    public static void drawPositionColor(BuiltBuffer buffer) {
        if (buffer != null) {
            RenderCoreCompat.draw(pipelineFor(POSITION_COLOR_PIPELINES, false, buffer.getDrawParameters().mode()), buffer);
        }
    }

    public static void drawPositionColorNoDepthWrite(BuiltBuffer buffer) {
        if (buffer != null) {
            RenderCoreCompat.draw(pipelineForNoDepthWrite(POSITION_COLOR_NO_DEPTH_WRITE_PIPELINES, buffer.getDrawParameters().mode(), BlendFunction.TRANSLUCENT), buffer);
        }
    }

    public static void drawPositionColorAdditiveNoDepthWrite(BuiltBuffer buffer) {
        if (buffer != null) {
            // LIGHTNING = (SRC_ALPHA, ONE) — аддитив, масштабированный альфой, как
            // blendFunc(SRC_ALPHA, ONE) в 1.21.4. ADDITIVE = (ONE, ONE) игнорирует альфу
            // и пересвечивает почти белые слои (крылья превращались в белые пятна).
            RenderCoreCompat.draw(pipelineForNoDepthWrite(POSITION_COLOR_ADDITIVE_NO_DEPTH_WRITE_PIPELINES, buffer.getDrawParameters().mode(), BlendFunction.LIGHTNING), buffer);
        }
    }

    public static void drawPositionTextureColor(BuiltBuffer buffer) {
        if (buffer != null) {
            RenderCoreCompat.draw(pipelineFor(POSITION_TEXTURE_COLOR_PIPELINES, true, buffer.getDrawParameters().mode()), buffer);
        }
    }

    public static void draw(BuiltBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.getDrawParameters().format() == VertexFormats.POSITION_TEXTURE_COLOR) {
            drawPositionTextureColor(buffer);
        } else {
            drawPositionColor(buffer);
        }
    }

    public static void drawTextured(BuiltBuffer buffer, AbstractTexture texture) {
        if (buffer != null) {
            RenderCoreCompat.drawTextured(
                    LynxRenderPipelines.COMPAT_TEXTURE,
                    buffer,
                    texture != null ? texture.getGlTextureView() : null,
                    texture != null ? texture.getSampler() : null
            );
        }
    }

    private static RenderPipeline pipelineFor(Map<VertexFormat.DrawMode, RenderPipeline> cache, boolean textured, VertexFormat.DrawMode mode) {
        return cache.computeIfAbsent(mode, drawMode -> {
            return RenderPipelines.register(
                    RenderPipeline.builder(textured ? RenderPipelines.POSITION_TEX_COLOR_SNIPPET : RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of("lynxwave", (textured ? "position_texture_color_" : "position_color_") + drawMode.name().toLowerCase()))
                            .withVertexFormat(textured ? VertexFormats.POSITION_TEXTURE_COLOR : VertexFormats.POSITION_COLOR, drawMode)
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withCull(false)
                            .build()
            );
        });
    }

    private static RenderPipeline pipelineForNoDepthWrite(Map<VertexFormat.DrawMode, RenderPipeline> cache, VertexFormat.DrawMode mode, BlendFunction blendFunction) {
        return cache.computeIfAbsent(mode, drawMode -> {
            return RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of("lynxwave", pipelinePath(drawMode, blendFunction)))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, drawMode)
                            .withBlend(blendFunction)
                            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        });
    }

    private static String pipelinePath(VertexFormat.DrawMode drawMode, BlendFunction blendFunction) {
        String blend = (blendFunction == BlendFunction.ADDITIVE || blendFunction == BlendFunction.LIGHTNING) ? "_additive" : "";
        return "position_color_" + drawMode.name().toLowerCase() + blend + "_no_depth_write";
    }
}
