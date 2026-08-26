package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

// Порт rich.util.render.сliemtpipeline.ClientPipelines (ESP-слои TargetESP) под LynxWave.
// Шейдеры ванильные (core/position_tex_color, core/position_color), аддитив = LIGHTNING.
public final class LynxEspPipelines {

    private LynxEspPipelines() {}

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_romb"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> RenderLayer.of("lynx_esp_romb",
                    RenderSetup.builder(ROMB_ESP_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));

    public static final RenderPipeline GHOSTS_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_ghosts"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> GHOSTS_ESP =
            Util.memoize(texture -> RenderLayer.of("lynx_esp_ghosts",
                    RenderSetup.builder(GHOSTS_ESP_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));

    public static final RenderPipeline CHAIN_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_chain"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> CHAIN_ESP =
            Util.memoize(texture -> RenderLayer.of("lynx_esp_chain",
                    RenderSetup.builder(CHAIN_ESP_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));

    public static final RenderPipeline CRYSTAL_FILLED_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_crystal_filled"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer CRYSTAL_FILLED = RenderLayer.of("lynx_esp_crystal_filled",
            RenderSetup.builder(CRYSTAL_FILLED_PIPELINE)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build());

    public static final RenderPipeline CRYSTAL_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_crystal_glow"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer CRYSTAL_GLOW = RenderLayer.of("lynx_esp_crystal_glow",
            RenderSetup.builder(CRYSTAL_GLOW_PIPELINE)
                    .translucent()
                    .expectedBufferSize(4096)
                    .build());

    public static final RenderPipeline BLOOM_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_bloom"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> BLOOM_ESP =
            Util.memoize(texture -> RenderLayer.of("lynx_esp_bloom",
                    RenderSetup.builder(BLOOM_ESP_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(2048)
                            .build()));

    public static final RenderPipeline PENTAGRAM_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/esp_pentagram"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> PENTAGRAM_ESP =
            Util.memoize(texture -> RenderLayer.of("lynx_esp_pentagram",
                    RenderSetup.builder(PENTAGRAM_ESP_PIPELINE)
                            .texture("Sampler0", texture)
                            .translucent()
                            .expectedBufferSize(8192)
                            .build()));
}
