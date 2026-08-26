package dev.client.api.nullcry.render.core.renderers.core.handflame;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gl.GpuSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class KawaseBlurPipeline {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0f, 0f, 0f);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int MAX_ITERATIONS = 8;
    private static final int BUFFER_SIZE = 16;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private GpuTexture[] downTextures;
    private GpuTextureView[] downViews;
    private GpuTexture[] upTextures;
    private GpuTextureView[] upViews;
    private int[] downWidths;
    private int[] downHeights;
    private int[] upWidths;
    private int[] upHeights;
    private GpuTexture finalTexture;
    private GpuTextureView finalView;
    private int lastWidth;
    private int lastHeight;
    private boolean initialized;

    public GpuTextureView blur(GpuTexture sourceTexture, GpuTextureView sourceView, int w, int h, int iterations, float offset) {
        if (sourceTexture == null || sourceView == null || w <= 0 || h <= 0) return null;

        ensureInitialized();
        ensureFramebuffers(w, h);

        iterations = Math.min(Math.max(iterations, 1), MAX_ITERATIONS);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTextureView currentSource = sourceView;
        int currentWidth = w;
        int currentHeight = h;

        for (int i = 0; i < iterations; i++) {
            uploadPass(
                encoder,
                LynxRenderPipelines.KAWASE_DOWN,
                currentSource,
                downViews[i],
                currentWidth,
                currentHeight,
                offset,
                "lynxwave:kawase_down_" + i
            );
            currentSource = downViews[i];
            currentWidth = downWidths[i];
            currentHeight = downHeights[i];
        }

        for (int i = iterations - 1; i >= 0; i--) {
            uploadPass(
                encoder,
                LynxRenderPipelines.KAWASE_UP,
                currentSource,
                upViews[i],
                currentWidth,
                currentHeight,
                offset,
                "lynxwave:kawase_up_" + i
            );
            currentSource = upViews[i];
            currentWidth = upWidths[i];
            currentHeight = upHeights[i];
        }

        uploadPass(
            encoder,
            LynxRenderPipelines.KAWASE_UP,
            currentSource,
            finalView,
            currentWidth,
            currentHeight,
            offset,
            "lynxwave:kawase_final"
        );

        return finalView;
    }

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);
        dummyVertexBuffer = createDummyVertexBuffer("lynxwave:kawase_dummy_vertex");
        downTextures = new GpuTexture[MAX_ITERATIONS];
        downViews = new GpuTextureView[MAX_ITERATIONS];
        upTextures = new GpuTexture[MAX_ITERATIONS];
        upViews = new GpuTextureView[MAX_ITERATIONS];
        downWidths = new int[MAX_ITERATIONS];
        downHeights = new int[MAX_ITERATIONS];
        upWidths = new int[MAX_ITERATIONS];
        upHeights = new int[MAX_ITERATIONS];
        initialized = true;
    }

    private void ensureFramebuffers(int w, int h) {
        if (w == lastWidth && h == lastHeight && finalTexture != null) return;

        cleanupFramebuffers();

        finalTexture = RenderSystem.getDevice().createTexture(
            () -> "lynxwave:kawase_final",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            w,
            h,
            1,
            1
        );
        finalView = RenderSystem.getDevice().createTextureView(finalTexture);

        int currentWidth = w;
        int currentHeight = h;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            currentWidth = Math.max(1, currentWidth / 2);
            currentHeight = Math.max(1, currentHeight / 2);

            final int index = i;
            final int passWidth = currentWidth;
            final int passHeight = currentHeight;

            downWidths[i] = passWidth;
            downHeights[i] = passHeight;
            upWidths[i] = passWidth;
            upHeights[i] = passHeight;

            downTextures[i] = RenderSystem.getDevice().createTexture(
                () -> "lynxwave:kawase_down_" + index,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                passWidth,
                passHeight,
                1,
                1
            );
            downViews[i] = RenderSystem.getDevice().createTextureView(downTextures[i]);

            upTextures[i] = RenderSystem.getDevice().createTexture(
                () -> "lynxwave:kawase_up_" + index,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                passWidth,
                passHeight,
                1,
                1
            );
            upViews[i] = RenderSystem.getDevice().createTextureView(upTextures[i]);
        }

        lastWidth = w;
        lastHeight = h;
    }

    private void uploadPass(CommandEncoder encoder, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, GpuTextureView source, GpuTextureView target, int w, int h, float offset, String name) {
        if (source == null || target == null) return;

        prepareUniformData(w, h, offset);
        uploadUniform(encoder);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
            RenderSystem.getModelViewMatrix(),
            COLOR_MODULATOR,
            MODEL_OFFSET,
            TEXTURE_MATRIX
        );

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass renderPass = encoder.createRenderPass(() -> name, target, OptionalInt.of(0x00000000))) {
            renderPass.setPipeline(pipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", source, sampler);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("KawaseData", uniformBuffer);
            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(int w, int h, float offset) {
        dataBuffer.clear();
        dataBuffer.putFloat(w);
        dataBuffer.putFloat(h);
        dataBuffer.putFloat(offset);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    private void uploadUniform(CommandEncoder encoder) {
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "lynxwave:kawase_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
            );
        }
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
    }

    private void cleanupFramebuffers() {
        if (finalView != null) {
            finalView.close();
            finalView = null;
        }
        if (finalTexture != null) {
            finalTexture.close();
            finalTexture = null;
        }
        if (downViews != null) {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                if (downViews[i] != null) {
                    downViews[i].close();
                    downViews[i] = null;
                }
                if (downTextures[i] != null) {
                    downTextures[i].close();
                    downTextures[i] = null;
                }
                if (upViews[i] != null) {
                    upViews[i].close();
                    upViews[i] = null;
                }
                if (upTextures[i] != null) {
                    upTextures[i].close();
                    upTextures[i] = null;
                }
            }
        }
        lastWidth = 0;
        lastHeight = 0;
    }

    private static GpuBuffer createDummyVertexBuffer(String name) {
        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        try {
            dummyData.putInt(0);
            dummyData.flip();
            return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, dummyData);
        } finally {
            MemoryUtil.memFree(dummyData);
        }
    }

    public void close() {
        cleanupFramebuffers();
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}
