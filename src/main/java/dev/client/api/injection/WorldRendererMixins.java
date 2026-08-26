package dev.client.api.injection;

import dev.client.modules.core.render.BlockHighLight;
import dev.client.modules.core.render.motionblur.MotionBlur;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    protected abstract void renderMain(FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4f positionMatrix, GpuBufferSlice fogBuffer, boolean renderBlockOutline, WorldRenderState worldRenderState, RenderTickCounter renderTickCounter, Profiler profiler);

    @Unique private Matrix4f lynxwave$prevModelView = new Matrix4f();
    @Unique private Matrix4f lynxwave$prevProjection = new Matrix4f();
    @Unique private Vector3f lynxwave$prevCameraPos = new Vector3f();

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderMain(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/render/state/WorldRenderState;Lnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/util/profiler/Profiler;)V"))
    private void onRender(WorldRenderer instance, FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4f positionMatrix, GpuBufferSlice fogBuffer, boolean renderBlockOutline, WorldRenderState worldRenderState, RenderTickCounter renderTickCounter, Profiler profiler) {
        this.renderMain(frameGraphBuilder, frustum, positionMatrix, fogBuffer, !BlockHighLight.INSTANCE.isEnabled(), worldRenderState, renderTickCounter, profiler);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void lynxwave$setMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        dev.client.api.compat.RenderSystem.setProjectionMatrixSnapshot(projectionMatrix);
        if (MotionBlur.INSTANCE == null || !MotionBlur.INSTANCE.isEnabled()) return;
        MotionBlur.INSTANCE.shader.setFrameMotionBlur(
                positionMatrix,
                lynxwave$prevModelView,
                basicProjectionMatrix,
                lynxwave$prevProjection,
                new Vector3f(
                        (float) (camera.getCameraPos().x % 30000f),
                        (float) (camera.getCameraPos().y % 30000f),
                        (float) (camera.getCameraPos().z % 30000f)
                ),
                lynxwave$prevCameraPos
        );
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lynxwave$setOldMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (MotionBlur.INSTANCE == null || !MotionBlur.INSTANCE.isEnabled()) return;
        lynxwave$prevModelView = new Matrix4f(positionMatrix);
        lynxwave$prevProjection = new Matrix4f(basicProjectionMatrix);
        lynxwave$prevCameraPos = new Vector3f(
                (float) (camera.getCameraPos().x % 30000f),
                (float) (camera.getCameraPos().y % 30000f),
                (float) (camera.getCameraPos().z % 30000f)
        );
    }
}
