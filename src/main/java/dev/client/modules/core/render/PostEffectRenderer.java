package dev.client.modules.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

public final class PostEffectRenderer {
    private static final int MOTION_CONFIG_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .putInt()
            .putInt()
            .putFloat()
            .putInt()
            .get();
    private static final int MOTION_MATRICES_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec3()
            .putVec3()
            .get();
    private static final int SATURATION_CONFIG_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private static final RenderPipeline MOTION_BLUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/motion_blur"))
                    .withVertexShader(Identifier.ofVanilla("core/screenquad"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/post/motionblur/motion_blur"))
                    .withSampler("MainSampler")
                    .withSampler("MainDepthSampler")
                    .withUniform("MotionBlurConfig", UniformType.UNIFORM_BUFFER)
                    .withUniform("MotionBlurMatrices", UniformType.UNIFORM_BUFFER)
                    .build()
    );

    private static final RenderPipeline SATURATION = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/post/saturation"))
                    .withVertexShader(Identifier.ofVanilla("core/saturation"))
                    .withFragmentShader(Identifier.ofVanilla("core/saturation"))
                    .withSampler("InSampler")
                    .withUniform("SaturationConfig", UniformType.UNIFORM_BUFFER)
                    .build()
    );

    private static SimpleFramebuffer motionSource;
    private static SimpleFramebuffer saturationSource;

    private PostEffectRenderer() {
    }

    public static void init() {
        // Forces static pipeline registration during module setup.
    }

    public static void renderMotionBlur(
            float blendFactor,
            int samples,
            int halfSamples,
            float inverseSamples,
            int blurAlgorithm,
            Matrix4fc mvInverse,
            Matrix4fc projInverse,
            Matrix4fc prevModelView,
            Matrix4fc prevProjection,
            Vector3fc cameraPos,
            Vector3fc prevCameraPos
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (!isUsable(main) || main.getDepthAttachment() == null) {
            return;
        }

        motionSource = ensureFramebuffer(motionSource, main, true, "lynxwave-motion-source");
        copyFramebuffer(main, motionSource, true);

        try (GpuBuffer config = createMotionConfig(main.textureWidth, main.textureHeight, blendFactor, samples, halfSamples, inverseSamples, blurAlgorithm);
             GpuBuffer matrices = createMotionMatrices(mvInverse, projInverse, prevModelView, prevProjection, cameraPos, prevCameraPos)) {
            renderFullscreen(MOTION_BLUR, main, new UniformBinding("MotionBlurConfig", config), new UniformBinding("MotionBlurMatrices", matrices),
                    new TextureBinding("MainSampler", motionSource.getColorAttachmentView()),
                    new TextureBinding("MainDepthSampler", motionSource.getDepthAttachmentView()));
        }
    }

    public static void renderSaturation(float saturation) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (!isUsable(main)) {
            return;
        }

        saturationSource = ensureFramebuffer(saturationSource, main, false, "lynxwave-saturation-source");
        copyFramebuffer(main, saturationSource, false);

        try (GpuBuffer config = createSaturationConfig(saturation)) {
            renderFullscreen(SATURATION, main, new UniformBinding("SaturationConfig", config),
                    new TextureBinding("InSampler", saturationSource.getColorAttachmentView()));
        }
    }

    private static boolean isUsable(Framebuffer framebuffer) {
        return framebuffer != null
                && framebuffer.getColorAttachment() != null
                && framebuffer.getColorAttachmentView() != null
                && framebuffer.textureWidth > 0
                && framebuffer.textureHeight > 0;
    }

    private static SimpleFramebuffer ensureFramebuffer(SimpleFramebuffer framebuffer, Framebuffer reference, boolean depth, String name) {
        if (framebuffer == null || framebuffer.useDepthAttachment != depth) {
            if (framebuffer != null) {
                framebuffer.delete();
            }
            return new SimpleFramebuffer(name, reference.textureWidth, reference.textureHeight, depth);
        }
        if (framebuffer.textureWidth != reference.textureWidth || framebuffer.textureHeight != reference.textureHeight) {
            framebuffer.resize(reference.textureWidth, reference.textureHeight);
        }
        return framebuffer;
    }

    private static void copyFramebuffer(Framebuffer source, Framebuffer target, boolean depth) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(source.getColorAttachment(), target.getColorAttachment(), 0, 0, 0, 0, 0, source.textureWidth, source.textureHeight);
        if (depth && source.getDepthAttachment() != null && target.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(source.getDepthAttachment(), target.getDepthAttachment(), 0, 0, 0, 0, 0, source.textureWidth, source.textureHeight);
        }
    }

    private static GpuBuffer createMotionConfig(int width, int height, float blendFactor, int samples, int halfSamples, float inverseSamples, int blurAlgorithm) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, MOTION_CONFIG_SIZE)
                    .putVec2(width, height)
                    .putFloat(blendFactor)
                    .putInt(samples)
                    .putInt(halfSamples)
                    .putFloat(inverseSamples)
                    .putInt(blurAlgorithm);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave motion blur config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private static GpuBuffer createMotionMatrices(
            Matrix4fc mvInverse,
            Matrix4fc projInverse,
            Matrix4fc prevModelView,
            Matrix4fc prevProjection,
            Vector3fc cameraPos,
            Vector3fc prevCameraPos
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, MOTION_MATRICES_SIZE)
                    .putMat4f(mvInverse)
                    .putMat4f(projInverse)
                    .putMat4f(prevModelView)
                    .putMat4f(prevProjection)
                    .putVec3(cameraPos)
                    .putVec3(prevCameraPos);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave motion blur matrices", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private static GpuBuffer createSaturationConfig(float saturation) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, SATURATION_CONFIG_SIZE)
                    .putVec4(saturation, 0.0f, 0.0f, 0.0f);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave saturation config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private static void renderFullscreen(RenderPipeline pipeline, Framebuffer target, UniformBinding uniform0, TextureBinding texture0) {
        renderFullscreen(pipeline, target, uniform0, null, texture0, null);
    }

    private static void renderFullscreen(RenderPipeline pipeline, Framebuffer target, UniformBinding uniform0, UniformBinding uniform1, TextureBinding texture0, TextureBinding texture1) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                pipeline::toString,
                target.getColorAttachmentView(),
                OptionalInt.empty()
        )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            bindUniform(pass, uniform0);
            bindUniform(pass, uniform1);
            bindTexture(pass, texture0, sampler);
            bindTexture(pass, texture1, sampler);
            pass.draw(0, 3);
        }
    }

    private static void bindUniform(RenderPass pass, UniformBinding uniform) {
        if (uniform != null && uniform.buffer != null) {
            pass.setUniform(uniform.name, uniform.buffer);
        }
    }

    private static void bindTexture(RenderPass pass, TextureBinding texture, GpuSampler sampler) {
        if (texture != null && texture.view != null) {
            pass.bindTexture(texture.name, texture.view, sampler);
        }
    }

    private record UniformBinding(String name, GpuBuffer buffer) {
    }

    private record TextureBinding(String name, com.mojang.blaze3d.textures.GpuTextureView view) {
    }
}
