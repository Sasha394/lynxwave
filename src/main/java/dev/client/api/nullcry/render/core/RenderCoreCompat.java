package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class RenderCoreCompat {
    public static final String SAMPLER0 = "Sampler0";
    public static final String SAMPLER1 = "Sampler1";
    public static final String SAMPLER2 = "Sampler2";

    private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
    private static final Matrix4f GUI_MODEL_VIEW = new Matrix4f().setTranslation(0.0f, 0.0f, -11000.0f);
    private static final List<DeferredGuiDraw> DEFERRED_GUI_DRAWS = new ArrayList<>();
    private static final List<DeferredGuiDraw> POST_GUI_DRAWS = new ArrayList<>();
    private static ProjectionMatrix2 guiProjection;
    private static boolean deferGuiDraws;
    private static boolean postGuiDraws;
    private static long captureFrameId;

    private RenderCoreCompat() {
    }

    public static net.minecraft.client.render.BufferBuilder begin(RenderLayer layer) {
        return Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
    }

    public static net.minecraft.client.render.BufferBuilder begin(RenderPipeline pipeline) {
        return Tessellator.getInstance().begin(pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
    }

    public static void draw(RenderLayer layer, BuiltBuffer buffer) {
        if (buffer == null) {
            return;
        }

        if (postGuiDraws) {
            POST_GUI_DRAWS.add(new QueuedLayerDraw(layer, buffer, captureScissor(), captureGuiProjection()));
            return;
        }

        if (deferGuiDraws) {
            DEFERRED_GUI_DRAWS.add(new QueuedLayerDraw(layer, buffer, captureScissor(), captureGuiProjection()));
            return;
        }

        layer.draw(buffer);
    }

    public static void draw(BuiltBuffer buffer) {
        if (buffer == null) {
            return;
        }

        if (buffer.getDrawParameters().format() == VertexFormats.POSITION_TEXTURE_COLOR) {
            drawTextured(RenderPipelines.GUI_TEXTURED, buffer, null, null);
            return;
        }

        draw(RenderPipelines.DEBUG_QUADS, buffer);
    }

    public static void draw(RenderPipeline pipeline, BuiltBuffer buffer) {
        draw(pipeline, buffer, (TextureBinding[]) null);
    }

    public static void drawTextured(RenderPipeline pipeline, BuiltBuffer buffer, GpuTextureView textureView, GpuSampler sampler) {
        draw(pipeline, buffer, binding(SAMPLER0, textureView, sampler));
    }

    public static void draw(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, TextureBinding... textures) {
        drawInternal(pipeline, buffer, uniforms, textures);
    }

    public static TextureBinding binding(String samplerName, GpuTextureView textureView, GpuSampler sampler) {
        return new TextureBinding(samplerName, textureView, sampler);
    }

    public static UniformBinding uniform(String uniformName, GpuBuffer buffer) {
        return new UniformBinding(uniformName, buffer, true);
    }

    public static void draw(RenderPipeline pipeline, BuiltBuffer buffer, TextureBinding... textures) {
        drawInternal(pipeline, buffer, null, textures);
    }

    public static void drawWithFramebufferSampler(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, FramebufferCapture capture) {
        drawWithFramebufferSampler(pipeline, buffer, uniforms, capture, SAMPLER0);
    }

    public static void drawWithFramebufferSampler(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, FramebufferCapture capture, String samplerName) {
        if (buffer == null) {
            closeOwnedUniforms(uniforms);
            return;
        }

        boolean guiPipeline = usesGuiTransform(pipeline);
        ScissorSnapshot scissor = captureScissor();
        GuiProjectionSnapshot guiProjection = guiPipeline ? captureGuiProjection() : null;

        if (postGuiDraws && shouldDeferDuringGuiExtraction(pipeline)) {
            POST_GUI_DRAWS.add(new QueuedFramebufferPipelineDraw(
                    pipeline,
                    buffer,
                    uniforms != null ? uniforms.clone() : null,
                    capture,
                    samplerName,
                    scissor,
                    guiProjection,
                    guiPipeline
            ));
            return;
        }

        if (deferGuiDraws && shouldDeferDuringGuiExtraction(pipeline)) {
            DEFERRED_GUI_DRAWS.add(new QueuedFramebufferPipelineDraw(
                    pipeline,
                    buffer,
                    uniforms != null ? uniforms.clone() : null,
                    capture,
                    samplerName,
                    scissor,
                    guiProjection,
                    guiPipeline
            ));
            return;
        }

        GpuBufferSlice previousProjection = null;
        ProjectionType previousProjectionType = null;
        if (guiPipeline) {
            previousProjection = RenderSystem.getProjectionMatrixBuffer();
            previousProjectionType = RenderSystem.getProjectionType();
            setupGuiProjection(guiProjection);
        }

        try {
            drawImmediateWithFramebufferSampler(pipeline, buffer, uniforms, capture, samplerName, scissor, guiPipeline);
        } finally {
            if (guiPipeline && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    private static void drawInternal(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, TextureBinding... textures) {
        if (buffer == null) {
            closeOwnedUniforms(uniforms);
            return;
        }

        boolean guiPipeline = usesGuiTransform(pipeline);
        ScissorSnapshot scissor = captureScissor();
        GuiProjectionSnapshot guiProjection = guiPipeline ? captureGuiProjection() : null;

        if (postGuiDraws && shouldDeferDuringGuiExtraction(pipeline)) {
            POST_GUI_DRAWS.add(new QueuedPipelineDraw(
                    pipeline,
                    buffer,
                    uniforms != null ? uniforms.clone() : null,
                    textures != null ? textures.clone() : null,
                    scissor,
                    guiProjection,
                    true
            ));
            return;
        }

        if (deferGuiDraws && shouldDeferDuringGuiExtraction(pipeline)) {
            DEFERRED_GUI_DRAWS.add(new QueuedPipelineDraw(
                    pipeline,
                    buffer,
                    uniforms != null ? uniforms.clone() : null,
                    textures != null ? textures.clone() : null,
                    scissor,
                    guiProjection,
                    true
            ));
            return;
        }

        GpuBufferSlice previousProjection = null;
        ProjectionType previousProjectionType = null;
        if (guiPipeline) {
            previousProjection = RenderSystem.getProjectionMatrixBuffer();
            previousProjectionType = RenderSystem.getProjectionType();
            setupGuiProjection(guiProjection);
        }

        try {
            drawImmediate(pipeline, buffer, uniforms, textures, scissor, guiPipeline);
        } finally {
            if (guiPipeline && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    public static void beginGuiExtraction() {
        captureFrameId++;
        clearDeferredGuiDraws();
        clearPostGuiDraws();
        deferGuiDraws = true;
        postGuiDraws = false;
    }

    public static long getCaptureFrameId() {
        return captureFrameId;
    }

    public static void withImmediateGuiDraws(Runnable draw) {
        if (draw == null) {
            return;
        }

        boolean previousDeferState = deferGuiDraws;
        deferGuiDraws = false;
        try {
            draw.run();
        } finally {
            deferGuiDraws = previousDeferState;
        }
    }

    public static void withPostGuiDraws(Runnable draw) {
        if (draw == null) {
            return;
        }

        boolean previousPostState = postGuiDraws;
        postGuiDraws = true;
        try {
            draw.run();
        } finally {
            postGuiDraws = previousPostState;
        }
    }

    public static void flushGuiExtraction() {
        deferGuiDraws = false;

        List<DeferredGuiDraw> draws = new ArrayList<>(DEFERRED_GUI_DRAWS);
        DEFERRED_GUI_DRAWS.clear();
        if (draws.isEmpty()) {
            return;
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        try {
            for (DeferredGuiDraw draw : draws) {
                draw.draw();
            }
        } finally {
            for (DeferredGuiDraw draw : draws) {
                draw.close();
            }
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
        }
    }

    public static void flushPostGuiDraws() {
        postGuiDraws = false;

        List<DeferredGuiDraw> draws = new ArrayList<>(POST_GUI_DRAWS);
        POST_GUI_DRAWS.clear();
        if (draws.isEmpty()) {
            return;
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        try {
            for (DeferredGuiDraw draw : draws) {
                draw.draw();
            }
        } finally {
            for (DeferredGuiDraw draw : draws) {
                draw.close();
            }
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
        }
    }

    public static void cancelGuiExtraction() {
        deferGuiDraws = false;
        postGuiDraws = false;
        clearDeferredGuiDraws();
        clearPostGuiDraws();
    }

    private static void drawImmediate(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, TextureBinding[] textures, ScissorSnapshot scissor, boolean guiTransform) {
        try (buffer) {
            GpuBuffer vertexBuffer = pipeline.getVertexFormat().uploadImmediateVertexBuffer(buffer.getBuffer());
            BuiltBuffer.DrawParameters drawParameters = buffer.getDrawParameters();
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;

            if (buffer.getSortedBuffer() == null) {
                RenderSystem.ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(drawParameters.mode());
                indexBuffer = sequentialBuffer.getIndexBuffer(drawParameters.indexCount());
                indexType = sequentialBuffer.getIndexType();
            } else {
                indexBuffer = pipeline.getVertexFormat().uploadImmediateIndexBuffer(buffer.getSortedBuffer());
                indexType = drawParameters.indexType();
            }

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : framebuffer.getColorAttachmentView();
            GpuTextureView depthView = framebuffer.useDepthAttachment
                    ? (RenderSystem.outputDepthTextureOverride != null
                    ? RenderSystem.outputDepthTextureOverride
                    : framebuffer.getDepthAttachmentView())
                    : null;

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                    guiTransform ? GUI_MODEL_VIEW : RenderSystem.getModelViewMatrix(),
                    new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                    new Vector3f(),
                    IDENTITY_TEXTURE_MATRIX
            );

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    pipeline::toString,
                    colorView,
                    OptionalInt.empty(),
                    depthView,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);

                if (scissor.enabled()) {
                    pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                }

                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                if (uniforms != null) {
                    for (UniformBinding uniform : uniforms) {
                        if (uniform != null && uniform.buffer != null) {
                            pass.setUniform(uniform.uniformName, uniform.buffer);
                        }
                    }
                }
                pass.setVertexBuffer(0, vertexBuffer);

                if (textures != null) {
                    for (TextureBinding texture : textures) {
                        if (texture != null && texture.textureView != null) {
                            pass.bindTexture(texture.samplerName, texture.textureView, resolveSampler(texture.sampler));
                        }
                    }
                }

                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
            }
        } finally {
            closeOwnedUniforms(uniforms);
        }
    }

    private static void drawImmediateWithFramebufferSampler(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, FramebufferCapture capture, String samplerName, ScissorSnapshot scissor, boolean guiTransform) {
        GpuTextureView textureView = capture != null ? capture.getColorView() : null;
        if (textureView == null) {
            try (buffer) {
                // Close the buffer without drawing when the source framebuffer cannot be sampled.
            } finally {
                closeOwnedUniforms(uniforms);
            }
            return;
        }

        drawImmediate(pipeline, buffer, uniforms, new TextureBinding[] {
                binding(samplerName != null ? samplerName : SAMPLER0, textureView, null)
        }, scissor, guiTransform);
    }

    private static GpuSampler resolveSampler(GpuSampler sampler) {
        return sampler != null ? sampler : RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
    }

    private static GuiProjectionSnapshot captureGuiProjection() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        int scale = Math.max(1, window.getScaleFactor());
        float width = Math.max(1.0f, (float) window.getScaledWidth());
        float height = Math.max(1.0f, (float) window.getScaledHeight());
        if (width <= 1.0f && window.getFramebufferWidth() > 0) {
            width = (float) window.getFramebufferWidth() / (float) scale;
        }
        if (height <= 1.0f && window.getFramebufferHeight() > 0) {
            height = (float) window.getFramebufferHeight() / (float) scale;
        }
        return new GuiProjectionSnapshot(width, height);
    }

    private static void setupGuiProjection(GuiProjectionSnapshot snapshot) {
        GuiProjectionSnapshot resolved = snapshot != null ? snapshot : captureGuiProjection();
        RenderSystem.setProjectionMatrix(getGuiProjection().set(resolved.width(), resolved.height()), ProjectionType.ORTHOGRAPHIC);
    }

    private static ProjectionMatrix2 getGuiProjection() {
        if (guiProjection == null) {
            guiProjection = new ProjectionMatrix2("lynxwave_gui", 1000.0f, 11000.0f, true);
        }
        return guiProjection;
    }

    private static ScissorSnapshot captureScissor() {
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        return new ScissorSnapshot(scissor.isEnabled(), scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
    }

    private static void clearDeferredGuiDraws() {
        for (DeferredGuiDraw draw : DEFERRED_GUI_DRAWS) {
            draw.close();
        }
        DEFERRED_GUI_DRAWS.clear();
    }

    private static void clearPostGuiDraws() {
        for (DeferredGuiDraw draw : POST_GUI_DRAWS) {
            draw.close();
        }
        POST_GUI_DRAWS.clear();
    }

    private static void closeOwnedUniforms(UniformBinding[] uniforms) {
        if (uniforms == null) {
            return;
        }
        for (UniformBinding uniform : uniforms) {
            if (uniform != null && uniform.closeAfterDraw && uniform.buffer != null && !uniform.buffer.isClosed()) {
                uniform.buffer.close();
            }
        }
    }

    private static boolean usesGuiTransform(RenderPipeline pipeline) {
        if (pipeline != null) {
            var location = pipeline.getLocation();
            if ("lynxwave".equals(location.getNamespace())
                    && location.getPath().startsWith("pipeline/gui/")) {
                return true;
            }
        }

        return pipeline == RenderPipelines.GUI
                || pipeline == RenderPipelines.GUI_INVERT
                || pipeline == RenderPipelines.GUI_TEXT_HIGHLIGHT
                || pipeline == RenderPipelines.GUI_TEXTURED
                || pipeline == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
                || pipeline == RenderPipelines.GUI_OPAQUE_TEX_BG
                || pipeline == RenderPipelines.GUI_NAUSEA_OVERLAY
                || pipeline == RenderPipelines.BLOCK_SCREEN_EFFECT
                || pipeline == RenderPipelines.FIRE_SCREEN_EFFECT
                || pipeline == RenderPipelines.VIGNETTE
                || pipeline == RenderPipelines.CROSSHAIR
                || pipeline == RenderPipelines.MOJANG_LOGO
                || pipeline == RenderPipelines.DEBUG_QUADS;
    }

    private static boolean shouldDeferDuringGuiExtraction(RenderPipeline pipeline) {
        if (pipeline == RenderPipelines.CROSSHAIR) {
            return false;
        }
        if (usesGuiTransform(pipeline)) {
            return true;
        }
        if (pipeline == null) {
            return false;
        }

        var location = pipeline.getLocation();
        return "lynxwave".equals(location.getNamespace())
                && (location.getPath().startsWith("position_color_")
                || location.getPath().startsWith("position_texture_color_"));
    }

    private static void applyScissor(ScissorSnapshot scissor) {
        if (scissor.enabled()) {
            RenderSystem.enableScissorForRenderTypeDraws(scissor.x(), scissor.y(), scissor.width(), scissor.height());
        } else {
            RenderSystem.disableScissorForRenderTypeDraws();
        }
    }

    private interface DeferredGuiDraw extends AutoCloseable {
        void draw();

        @Override
        void close();
    }

    private static final class QueuedPipelineDraw implements DeferredGuiDraw {
        private final RenderPipeline pipeline;
        private final BuiltBuffer buffer;
        private final UniformBinding[] uniforms;
        private final TextureBinding[] textures;
        private final ScissorSnapshot scissor;
        private final GuiProjectionSnapshot projection;
        private final boolean guiTransform;
        private boolean closed;

        private QueuedPipelineDraw(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, TextureBinding[] textures, ScissorSnapshot scissor, GuiProjectionSnapshot projection, boolean guiTransform) {
            this.pipeline = pipeline;
            this.buffer = buffer;
            this.uniforms = uniforms;
            this.textures = textures;
            this.scissor = scissor;
            this.projection = projection;
            this.guiTransform = guiTransform;
        }

        @Override
        public void draw() {
            if (this.closed) {
                return;
            }

            GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
            ProjectionType previousProjectionType = RenderSystem.getProjectionType();
            try {
                if (this.guiTransform) {
                    setupGuiProjection(this.projection);
                }
                drawImmediate(this.pipeline, this.buffer, this.uniforms, this.textures, this.scissor, this.guiTransform);
            } finally {
                this.closed = true;
                if (this.guiTransform && previousProjectionType != null) {
                    RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
                }
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.buffer.close();
            closeOwnedUniforms(this.uniforms);
        }
    }

    private static final class QueuedFramebufferPipelineDraw implements DeferredGuiDraw {
        private final RenderPipeline pipeline;
        private final BuiltBuffer buffer;
        private final UniformBinding[] uniforms;
        private final FramebufferCapture capture;
        private final String samplerName;
        private final ScissorSnapshot scissor;
        private final GuiProjectionSnapshot projection;
        private final boolean guiTransform;
        private boolean closed;

        private QueuedFramebufferPipelineDraw(RenderPipeline pipeline, BuiltBuffer buffer, UniformBinding[] uniforms, FramebufferCapture capture, String samplerName, ScissorSnapshot scissor, GuiProjectionSnapshot projection, boolean guiTransform) {
            this.pipeline = pipeline;
            this.buffer = buffer;
            this.uniforms = uniforms;
            this.capture = capture;
            this.samplerName = samplerName;
            this.scissor = scissor;
            this.projection = projection;
            this.guiTransform = guiTransform;
        }

        @Override
        public void draw() {
            if (this.closed) {
                return;
            }

            GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
            ProjectionType previousProjectionType = RenderSystem.getProjectionType();
            try {
                if (this.guiTransform) {
                    setupGuiProjection(this.projection);
                }
                drawImmediateWithFramebufferSampler(this.pipeline, this.buffer, this.uniforms, this.capture, this.samplerName, this.scissor, this.guiTransform);
            } finally {
                this.closed = true;
                if (this.guiTransform && previousProjectionType != null) {
                    RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
                }
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.buffer.close();
            closeOwnedUniforms(this.uniforms);
        }
    }

    private static final class QueuedLayerDraw implements DeferredGuiDraw {
        private final RenderLayer layer;
        private final BuiltBuffer buffer;
        private final ScissorSnapshot scissor;
        private final GuiProjectionSnapshot projection;
        private boolean closed;

        private QueuedLayerDraw(RenderLayer layer, BuiltBuffer buffer, ScissorSnapshot scissor, GuiProjectionSnapshot projection) {
            this.layer = layer;
            this.buffer = buffer;
            this.scissor = scissor;
            this.projection = projection;
        }

        @Override
        public void draw() {
            if (this.closed) {
                return;
            }

            GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
            ProjectionType previousProjectionType = RenderSystem.getProjectionType();
            ScissorSnapshot previousScissor = captureScissor();
            try {
                setupGuiProjection(this.projection);
                applyScissor(this.scissor);
                this.layer.draw(this.buffer);
            } catch (Throwable throwable) {
                this.buffer.close();
                throw throwable;
            } finally {
                this.closed = true;
                applyScissor(previousScissor);
                if (previousProjectionType != null) {
                    RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
                }
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.buffer.close();
        }
    }

    private record ScissorSnapshot(boolean enabled, int x, int y, int width, int height) {
    }

    private record GuiProjectionSnapshot(float width, float height) {
    }

    public record TextureBinding(String samplerName, GpuTextureView textureView, GpuSampler sampler) {
    }

    public record UniformBinding(String uniformName, GpuBuffer buffer, boolean closeAfterDraw) {
        public UniformBinding(String uniformName, GpuBuffer buffer) {
            this(uniformName, buffer, false);
        }
    }
}
