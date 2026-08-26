package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class LynxRenderPipelines {
    private static final String SAMPLER0 = "Sampler0";

    public static final RenderPipeline MSDF_FONT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/msdf_font"))
                    .withVertexShader(Identifier.of("lynxwave", "core/msdf_font"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/msdf_font"))
                    .withSampler(SAMPLER0)
                    .withUniform("MsdfConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline ROUNDED_RECT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/rectangle"))
                    .withVertexShader(Identifier.of("lynxwave", "core/rectangle"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/rectangle"))
                    .withUniform("RoundedRectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline TEXTURE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/texture"))
                    .withVertexShader(Identifier.of("lynxwave", "core/texture"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/texture"))
                    .withSampler(SAMPLER0)
                    .withUniform("RoundedRectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline COMPAT_TEXTURE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/compat/texture"))
                    .withVertexShader(Identifier.of("lynxwave", "core/compat_texture"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/compat_texture"))
                    .withSampler(SAMPLER0)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline HEAD_TEXTURE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/head_texture"))
                    .withVertexShader(Identifier.of("lynxwave", "core/head_texture"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/head_texture"))
                    .withSampler(SAMPLER0)
                    .withUniform("RoundedRectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline GRADIENT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/gradient"))
                    .withVertexShader(Identifier.of("lynxwave", "core/gradient"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/gradient"))
                    .withUniform("RoundedRectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline OUTLINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/outline"))
                    .withVertexShader(Identifier.of("lynxwave", "core/outline"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/outline"))
                    .withUniform("OutlineConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline GLOW = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/glow"))
                    .withVertexShader(Identifier.of("lynxwave", "core/glow"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/glow"))
                    .withUniform("EffectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline SHADOW = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/shadow"))
                    .withVertexShader(Identifier.of("lynxwave", "core/shadow"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/shadow"))
                    .withUniform("EffectConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline BLUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/blur"))
                    .withVertexShader(Identifier.of("lynxwave", "core/blur"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/blur"))
                    .withSampler(SAMPLER0)
                    .withUniform("BlurConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline LIQUID_GLASS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/liquid_glass"))
                    .withVertexShader(Identifier.of("lynxwave", "core/liquid_glass"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/liquid_glass"))
                    .withSampler(SAMPLER0)
                    .withUniform("LiquidGlassConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline HAND_HIGHLIGHT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/hand_highlight"))
                    .withVertexShader(Identifier.of("lynxwave", "core/hand_highlight"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/hand_highlight"))
                    .withSampler(SAMPLER0)
                    .withSampler("Sampler1")
                    .withUniform("HandHighlightConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public static final RenderPipeline HAND_BLUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/gui/hand_blur"))
                    .withVertexShader(Identifier.of("lynxwave", "core/hand_blur"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/hand_blur"))
                    .withSampler(SAMPLER0)
                    .withSampler("Sampler1")
                    .withUniform("HandBlurConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Грани 3D-куба частицы (формат POSITION_COLOR, шейдер core/position_color).
    public static final RenderPipeline WORLD_PARTICLE_QUADS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/world/particle_quads"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Рёбра 3D-куба. DrawMode.DEBUG_LINES рисует тонкие линии шейдером core/position_color
    // и не требует элемента LineWidth (в отличие от встроенного LINES/LINES_TRANSLUCENT).
    public static final RenderPipeline WORLD_PARTICLE_LINES = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/world/particle_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Аддитивное свечение мировых частиц (формат POSITION_TEXTURE_COLOR, шейдер
    // core/position_tex_color). Blend LIGHTNING = аддитивный (SRC_ALPHA, ONE) — чёрные
    // пиксели текстуры не затемняют сцену, частица "светится" как в версии 1.21.4.
    public static final RenderPipeline WORLD_PARTICLE_GLOW = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/world/particle_glow"))
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // --- Режим «Пламя» у ShaderHand (trail-пламя, перенесённое из w3y.soul) ---
    // Полноэкранные проходы без вершинных атрибутов: vsh строит quad через gl_VertexID,
    // формат VertexFormats.EMPTY + фиктивный вершинный буфер, draw(0, 6). REPLACE_BLEND
    // перезаписывает цель напрямую (промежуточные буферы и финальный композит сцены + пламени).
    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    public static final RenderPipeline HAND_FIRE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/hand_fire"))
                    .withVertexShader(Identifier.of("lynxwave", "core/hand_fire"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/hand_fire"))
                    .withUniform("HandFireData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("BlurSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    public static final RenderPipeline HAND_TRAIL = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/hand_trail"))
                    .withVertexShader(Identifier.of("lynxwave", "core/hand_trail"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/hand_trail"))
                    .withUniform("HandTrailData", UniformType.UNIFORM_BUFFER)
                    .withSampler("PrevTrailSampler")
                    .withSampler("SceneSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    public static final RenderPipeline MASK_DIFF = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/mask_diff"))
                    .withVertexShader(Identifier.of("lynxwave", "core/mask_diff"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/mask_diff"))
                    .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
                    .withSampler("BeforeSampler")
                    .withSampler("AfterSampler")
                    .withSampler("DepthBeforeSampler")
                    .withSampler("DepthAfterSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    public static final RenderPipeline KAWASE_DOWN = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/kawase_down"))
                    .withVertexShader(Identifier.of("lynxwave", "core/kawase_down"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/kawase_down"))
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    public static final RenderPipeline KAWASE_UP = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/kawase_up"))
                    .withVertexShader(Identifier.of("lynxwave", "core/kawase_up"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/kawase_up"))
                    .withUniform("KawaseData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .build()
    );

    private LynxRenderPipelines() {
    }
}
