package dev.client.api.nullcry.render.core.renderers.core.handflame;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import dev.client.api.nullcry.render.core.LynxRenderPipelines;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class MaskDiffPipeline {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0f, 0f, 0f);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 16;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    public void createMask(GpuTextureView target, GpuTextureView before, GpuTextureView after, GpuTextureView depthBefore, GpuTextureView depthAfter, int w, int h) {
        if (target == null || before == null || after == null || depthBefore == null || depthAfter == null) return;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        prepareUniformData(w, h);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        uploadUniform(encoder);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
            RenderSystem.getModelViewMatrix(),
            COLOR_MODULATOR,
            MODEL_OFFSET,
            TEXTURE_MATRIX
        );

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass renderPass = encoder.createRenderPass(() -> "lynxwave:mask_diff", target, OptionalInt.of(0x00000000))) {
            renderPass.setPipeline(LynxRenderPipelines.MASK_DIFF);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("BeforeSampler", before, sampler);
            renderPass.bindTexture("AfterSampler", after, sampler);
            renderPass.bindTexture("DepthBeforeSampler", depthBefore, sampler);
            renderPass.bindTexture("DepthAfterSampler", depthAfter, sampler);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("MaskData", uniformBuffer);
            renderPass.draw(0, 6);
        }
    }

    private void ensureInitialized() {
        if (initialized) return;

        dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);
        dummyVertexBuffer = createDummyVertexBuffer("lynxwave:mask_diff_dummy_vertex");
        initialized = true;
    }

    private void prepareUniformData(int w, int h) {
        dataBuffer.clear();
        dataBuffer.putFloat(w);
        dataBuffer.putFloat(h);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    private void uploadUniform(CommandEncoder encoder) {
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "lynxwave:mask_diff_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
            );
        }
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
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
