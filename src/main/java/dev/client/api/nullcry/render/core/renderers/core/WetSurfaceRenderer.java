package dev.client.api.nullcry.render.core.renderers.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

public final class WetSurfaceRenderer {
    private static final WetSurfaceRenderer INSTANCE = new WetSurfaceRenderer();
    private static final int DATA_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4()
            .putMat4f().putMat4f().putMat4f().putMat4f()
            .get();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/wet_surface"))
                    .withVertexShader(Identifier.of("lynxwave", "core/post/wet_surface"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/post/wet_surface"))
                    .withSampler("SceneSampler")
                    .withSampler("DepthSampler")
                    .withUniform("WetSurfaceData", UniformType.UNIFORM_BUFFER)
                    .build()
    );

    private SimpleFramebuffer sceneSource;

    private WetSurfaceRenderer() {
    }

    public static WetSurfaceRenderer getInstance() {
        return INSTANCE;
    }

    public void reset() {
        // Kept for the module lifecycle; GPU resources are reused between toggles.
    }

    public void apply(Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix, Parameters parameters) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (camera == null || viewMatrix == null || projectionMatrix == null || parameters == null
                || client.world == null || client.player == null || !isUsable(main)
                || main.getDepthAttachment() == null || main.getDepthAttachmentView() == null) {
            return;
        }

        sceneSource = ensureFramebuffer(sceneSource, main);
        copyColor(main, sceneSource);

        Matrix4f inverseProjection = new Matrix4f(projectionMatrix).invert();
        Matrix4f inverseView = new Matrix4f(viewMatrix).invert();
        Vec3d cameraPos = camera.getCameraPos();
        inverseView.m30((float) cameraPos.x);
        inverseView.m31((float) cameraPos.y);
        inverseView.m32((float) cameraPos.z);

        try (GpuBuffer data = createData(main, viewMatrix, projectionMatrix, inverseProjection, inverseView, parameters)) {
            render(main, data);
        }
    }

    private static void render(Framebuffer target, GpuBuffer data) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        WetSurfaceRenderer renderer = INSTANCE;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "lynxwave wet surface",
                target.getColorAttachmentView(),
                OptionalInt.empty()
        )) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WetSurfaceData", data);
            pass.bindTexture("SceneSampler", renderer.sceneSource.getColorAttachmentView(), sampler);
            pass.bindTexture("DepthSampler", target.getDepthAttachmentView(), sampler);
            pass.draw(0, 3);
        }
    }

    private static GpuBuffer createData(
            Framebuffer framebuffer,
            Matrix4f viewMatrix,
            Matrix4f projectionMatrix,
            Matrix4f inverseProjection,
            Matrix4f inverseView,
            Parameters parameters
    ) {
        float width = Math.max(1.0f, framebuffer.textureWidth);
        float height = Math.max(1.0f, framebuffer.textureHeight);
        float time = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, DATA_SIZE)
                    .putVec4(width, height, time, parameters.ripples ? 1.0f : 0.0f)
                    .putVec4(
                            clamp(parameters.wetness, 0.0f, 2.0f),
                            clamp(parameters.puddleCoverage, 0.0f, 1.0f),
                            clamp(parameters.reflectionStrength, 0.0f, 2.5f),
                            clamp(parameters.maxDistance, 4.0f, 128.0f)
                    )
                    .putVec4(
                            clamp(parameters.rippleStrength, 0.0f, 2.0f),
                            clamp(parameters.rainAmount, 0.0f, 2.0f),
                            Math.max(4, Math.min(16, parameters.reflectionSteps)),
                            0.0f
                    )
                    .putMat4f(viewMatrix)
                    .putMat4f(projectionMatrix)
                    .putMat4f(inverseProjection)
                    .putMat4f(inverseView);
            return RenderSystem.getDevice().createBuffer(
                    () -> "lynxwave wet surface data",
                    GpuBuffer.USAGE_UNIFORM,
                    builder.get()
            );
        }
    }

    private static SimpleFramebuffer ensureFramebuffer(SimpleFramebuffer framebuffer, Framebuffer reference) {
        if (framebuffer == null) {
            return new SimpleFramebuffer("lynxwave-wet-surface-source", reference.textureWidth, reference.textureHeight, false);
        }
        if (framebuffer.textureWidth != reference.textureWidth || framebuffer.textureHeight != reference.textureHeight) {
            framebuffer.resize(reference.textureWidth, reference.textureHeight);
        }
        return framebuffer;
    }

    private static void copyColor(Framebuffer source, Framebuffer target) {
        GpuTexture sourceColor = source.getColorAttachment();
        GpuTexture targetColor = target.getColorAttachment();
        if (sourceColor == null || targetColor == null || sourceColor.isClosed() || targetColor.isClosed()) {
            return;
        }
        int width = Math.min(source.textureWidth, target.textureWidth);
        int height = Math.min(source.textureHeight, target.textureHeight);
        if (width <= 0 || height <= 0
                || (sourceColor.usage() & GpuTexture.USAGE_COPY_SRC) == 0
                || (targetColor.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(sourceColor, targetColor, 0, 0, 0, 0, 0, width, height);
    }

    private static boolean isUsable(Framebuffer framebuffer) {
        return framebuffer != null
                && framebuffer.getColorAttachment() != null
                && framebuffer.getColorAttachmentView() != null
                && framebuffer.textureWidth > 1
                && framebuffer.textureHeight > 1;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    public static final class Parameters {
        public float wetness = 1.0f;
        public float puddleCoverage = 0.82f;
        public float reflectionStrength = 1.25f;
        public float maxDistance = 56.0f;
        public float rippleStrength = 0.8f;
        public float rainAmount = 1.0f;
        public int reflectionSteps = 10;
        public boolean ripples = true;
    }
}
