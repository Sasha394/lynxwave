package dev.client.api.nullcry.render.core.renderers.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import dev.client.api.nullcry.render.core.PixelSnap;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.api.nullcry.render.core.msdf.core.MsdfFont;
import dev.client.api.nullcry.render.core.providers.ColorProvider;
import dev.client.api.nullcry.render.core.renderers.IRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.List;

public record BuiltText(
        MsdfFont font,
        String text,
        float size,
        float thickness,
        int color,
        float Smoothness,
        float spacing,
        int outlineColor,
        float outlineThickness,
        List<Run> runs
) implements IRenderer {
    private static final int CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        if (this.font == null) {
            return;
        }

        if (!hasRenderableGlyphs()) {
            return;
        }

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.MSDF_FONT);

        float penX = PixelSnap.position(x);
        float penY = PixelSnap.position(y + this.font.getMetrics().baselineHeight() * this.size);
        float pxRange = (this.thickness + this.outlineThickness * 0.5f) * 0.5f * this.size;

        if (this.runs != null && !this.runs.isEmpty()) {
            float lineAdvance = this.font.getLineAdvance(this.size);
            for (Run run : this.runs) {
                if (run == null || run.s == null) continue;
                String runText = run.s;
                int offset = 0;
                while (offset <= runText.length()) {
                    int newlineIndex = runText.indexOf('\n', offset);
                    String segment = (newlineIndex == -1) ? runText.substring(offset) : runText.substring(offset, newlineIndex);
                    if (!segment.isEmpty()) {
                        this.font.applyGlyphs(matrix, builder, segment, this.size, pxRange, this.spacing, penX, penY, z, run.rgba);
                        penX += this.font.getWidth(segment, this.size, this.thickness, this.outlineThickness, this.spacing);
                    }

                    if (newlineIndex == -1) {
                        break;
                    }

                    penX = PixelSnap.position(x);
                    penY = PixelSnap.position(penY + lineAdvance);
                    offset = newlineIndex + 1;
                }
            }
        } else if (this.text != null) {
            this.font.applyGlyphs(matrix, builder, this.text, this.size, pxRange, this.spacing, penX, penY, z, this.color);
        }

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer != null) {
            GpuBuffer config = createConfig();
            RenderCoreCompat.draw(
                    LynxRenderPipelines.MSDF_FONT,
                    builtBuffer,
                    new RenderCoreCompat.UniformBinding[] {
                            RenderCoreCompat.uniform("MsdfConfig", config)
                    },
                    RenderCoreCompat.binding(RenderCoreCompat.SAMPLER0, this.font.getTextureView(), this.font.getSampler())
            );
        }
    }

    public void renderExact(Matrix4f matrix, float x, float y) {
        renderExactInternal(matrix, x, y, 0f, null, null);
    }

    public void renderHorizontalGradientExact(Matrix4f matrix, float x, float y, int startColor, int endColor) {
        renderExactInternal(matrix, x, y, 0f, startColor, endColor);
    }

    private void renderExactInternal(Matrix4f matrix, float x, float y, float z, Integer gradientStart, Integer gradientEnd) {
        if (this.font == null || !hasRenderableGlyphs()) return;
        if (this.runs != null && !this.runs.isEmpty()) {
            render(matrix, x, y, z);
            return;
        }
        if (this.text == null) return;

        BufferBuilder builder = RenderCoreCompat.begin(LynxRenderPipelines.MSDF_FONT);
        float penX = PixelSnap.position(x);
        float penY = PixelSnap.position(y + this.font.getMetrics().baselineHeight() * this.size);
        if (gradientStart != null && gradientEnd != null) {
            this.font.applyGlyphsHorizontalGradient(
                    matrix, builder, this.text, this.size, 0f, this.spacing,
                    penX, penY, z, gradientStart, gradientEnd
            );
        } else {
            this.font.applyGlyphs(matrix, builder, this.text, this.size, 0f, this.spacing, penX, penY, z, this.color);
        }

        BuiltBuffer builtBuffer = builder.endNullable();
        if (builtBuffer == null) return;
        GpuBuffer config = createConfig();
        RenderCoreCompat.draw(
                LynxRenderPipelines.MSDF_FONT,
                builtBuffer,
                new RenderCoreCompat.UniformBinding[] {
                        RenderCoreCompat.uniform("MsdfConfig", config)
                },
                RenderCoreCompat.binding(RenderCoreCompat.SAMPLER0, this.font.getTextureView(), this.font.getSampler())
        );
    }

    private GpuBuffer createConfig() {
        float[] outline = ColorProvider.normalize(this.outlineColor);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(
                            Math.max(1.0f, this.font.getAtlas().range()),
                            this.thickness,
                            Math.max(0.001f, this.Smoothness),
                            Math.max(0.0f, this.outlineThickness)
                    )
                    .putVec4(outline[0], outline[1], outline[2], outline[3]);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave msdf config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private boolean hasRenderableGlyphs() {
        if (this.text != null) {
            if (this.text.isEmpty()) return false;
            for (int i = 0; i < this.text.length(); i++) {
                char ch = this.text.charAt(i);
                if (!Character.isWhitespace(ch)) return true;
            }
            return false;
        }

        if (this.runs != null) {
            for (Run run : this.runs) {
                if (run == null) continue;
                String value = run.s();
                if (value == null || value.isEmpty()) continue;
                boolean hasGlyph = value.chars().anyMatch(ch -> ch != '\n' && !Character.isWhitespace(ch));
                if (hasGlyph) {
                    return true;
                }
            }
        }

        return false;
    }

    public record Run(String s, int rgba) {
    }

    public float measureWidth() {
        if (this.font == null) return 0f;

        if (this.runs != null && !this.runs.isEmpty()) {
            float currentLineWidth = 0f;
            float maxWidth = 0f;

            for (Run run : this.runs) {
                if (run == null || run.s == null || run.s.isEmpty()) continue;

                String runText = run.s;
                int offset = 0;
                while (offset <= runText.length()) {
                    int newlineIndex = runText.indexOf('\n', offset);
                    String segment = (newlineIndex == -1)
                            ? runText.substring(offset)
                            : runText.substring(offset, newlineIndex);

                    if (!segment.isEmpty()) {
                        currentLineWidth += this.font.getWidth(segment, this.size, this.thickness, this.outlineThickness, this.spacing);
                    }

                    if (newlineIndex == -1) {
                        break;
                    }

                    maxWidth = Math.max(maxWidth, currentLineWidth);
                    currentLineWidth = 0f;
                    offset = newlineIndex + 1;
                }
            }

            return Math.max(maxWidth, currentLineWidth);
        }

        if (this.text != null) {
            return this.font.getWidth(this.text, this.size, this.thickness, this.outlineThickness, this.spacing);
        }

        return 0f;
    }
}
