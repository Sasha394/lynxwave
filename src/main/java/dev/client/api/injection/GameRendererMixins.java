package dev.client.api.injection;


import com.llamalad7.mixinextras.sugar.Local;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.events.core.render.ShaderHandEvent;
import dev.client.api.nullcry.events.core.world.AspectRatioEvent;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.modules.core.combat.NoEntityTrace;
import dev.client.api.nullcry.render.core.renderers.core.handflame.HandShaderRenderer;
import dev.client.modules.core.render.CustomWorld;
import dev.client.modules.core.render.NoRender;
import dev.client.modules.core.render.PostEffectRenderer;
import dev.client.modules.core.render.Rain;
import dev.client.modules.core.render.ShaderHand;
import dev.client.modules.core.render.Zoom;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixins {
    @Shadow
    public abstract float getFarPlaneDistance();

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private SimpleFramebuffer lynx$beforeHandFbo = null;
    @Unique
    private SimpleFramebuffer lynx$withHandFbo = null;

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void lynx$beforeHand(float tickDelta, boolean sleeping, Matrix4f matrix4f, CallbackInfo ci) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (main.getColorAttachment() != null) {
            this.lynx$beforeHandFbo = lynx$ensureFbo(this.lynx$beforeHandFbo, main, "lynxwave-before-hand");
            lynx$copyGL(main, this.lynx$beforeHandFbo);
        }

        // Режим «Пламя» у ShaderHand: захват сцены (цвет + глубина) до отрисовки руки.
        HandShaderRenderer flame = HandShaderRenderer.getInstance();
        flame.setEnabled(ShaderHand.flameActive());
        flame.captureSceneBeforeHands();
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void lynx$afterHand(float tickDelta, boolean sleeping, Matrix4f matrix4f, CallbackInfo ci) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (main.getColorAttachment() != null && this.lynx$beforeHandFbo != null) {
            this.lynx$withHandFbo = lynx$ensureFbo(this.lynx$withHandFbo, main, "lynxwave-with-hand");
            lynx$copyGL(main, this.lynx$withHandFbo);
            EventManager.call(new ShaderHandEvent(this.lynx$beforeHandFbo, this.lynx$withHandFbo));
        }

        // Захват сцены с рукой (цвет + глубина) — вторая половина маски силуэта.
        HandShaderRenderer.getInstance().captureSceneAfterHands();
    }

    @Unique
    private static SimpleFramebuffer lynx$ensureFbo(SimpleFramebuffer fbo, Framebuffer ref, String name) {
        if (fbo == null) {
            return new SimpleFramebuffer(name, ref.textureWidth, ref.textureHeight, false);
        }
        if (fbo.textureWidth != ref.textureWidth || fbo.textureHeight != ref.textureHeight) {
            fbo.resize(ref.textureWidth, ref.textureHeight);
        }
        return fbo;
    }

    @Unique
    private static void lynx$copyGL(Framebuffer src, SimpleFramebuffer dst) {
        GpuTexture srcColor = src.getColorAttachment();
        GpuTexture dstColor = dst.getColorAttachment();
        if (srcColor == null || dstColor == null || srcColor.isClosed() || dstColor.isClosed()) {
            return;
        }
        if ((srcColor.usage() & GpuTexture.USAGE_COPY_SRC) == 0 || (dstColor.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            return;
        }

        int width = Math.min(src.textureWidth, dst.textureWidth);
        int height = Math.min(src.textureHeight, dst.textureHeight);
        if (width <= 0 || height <= 0) {
            return;
        }

        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        com.mojang.blaze3d.systems.RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToTexture(srcColor, dstColor, 0, 0, 0, 0, 0, width, height);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;clear()V",
                    shift = At.Shift.AFTER
            )
    )
    private void lynx$beginGuiExtraction(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RenderCoreCompat.beginGuiExtraction();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void lynx$flushGuiExtraction(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RenderCoreCompat.flushGuiExtraction();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void lynx$flushPostGuiDraws(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RenderCoreCompat.flushPostGuiDraws();
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRender3DCombined(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 1) Matrix4f viewMatrix) {
        {
            Camera camera = client.gameRenderer.getCamera();
            MatrixStack ms = new MatrixStack();
            RenderSystem.getModelViewStack().pushMatrix();
            try {
                RenderSystem.getModelViewStack().mul(ms.peek().getPositionMatrix());
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));

                ProjectionUtil.lastModMat.set(RenderSystem.getModelViewMatrix());
                ProjectionUtil.lastProjMat.set(RenderSystem.getProjectionMatrix());
                ProjectionUtil.lastWorldSpaceMatrix.set(ms.peek().getPositionMatrix());

                EventManager.call(new RenderEvent.Draw3D(ms, tickCounter));
            } finally {
                RenderSystem.getModelViewStack().popMatrix();
            }
        }

        {
            MatrixStack ms = new MatrixStack();
            ms.multiplyPositionMatrix(viewMatrix);
            ms.translate(client.getEntityRenderDispatcher().camera.getCameraPos().negate());
            Draw3DUtil.setLastProjMat(RenderSystem.getProjectionMatrix());
            Draw3DUtil.setLastWorldSpaceMatrix(ms.peek());
            Draw3DUtil.onDraw3D(new RenderEvent.Draw3D(ms, tickCounter));
        }

        Rain rain = Rain.INSTANCE;
        if (rain != null && rain.isEnabled()) {
            rain.renderWetSurfaceFrame(
                    client.gameRenderer.getCamera(),
                    viewMatrix,
                    RenderSystem.getProjectionMatrix()
            );
        }

        CustomWorld customWorld = CustomWorld.INSTANCE;
        if (customWorld != null && customWorld.isEnabled() && customWorld.mode.isSelected("Насыщенность")) {
            lynx$renderSaturation(customWorld.saturation.getValue());
        }
    }
    @Unique
    private void lynx$renderSaturation(float v) {
        PostEffectRenderer.renderSaturation(v);
    }

    @ModifyVariable(method = "getBasicProjectionMatrix", at = @At("HEAD"), argsOnly = true)
    private float zoomModifyFov(float fovDegrees) {
        if (Zoom.INSTANCE != null && Zoom.INSTANCE.isEnabled()) {
            float mult = Zoom.INSTANCE.getZoomMultiplier();
            if (mult > 0f) return fovDegrees / mult;
        }
        return fovDegrees;
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrixHook(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatioEvent aspectRatioEvent = new AspectRatioEvent();
        EventManager.call(aspectRatioEvent);
        if (aspectRatioEvent.isCancelled()) {
            Matrix4f matrix4f = new Matrix4f();
            matrix4f.perspective(fovDegrees * 0.01745329238474369F, aspectRatioEvent.getRatio(), 0.05f, getFarPlaneDistance());
            cir.setReturnValue(matrix4f);
        }
    }

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void onUpdateCrosshairTarget(float tickDelta, CallbackInfo ci) {
        if (NoEntityTrace.INSTANCE.isEnabled()) {
            Entity camera = client.getCameraEntity();
            if (camera == null || client.player == null) return;
            double range = Math.max(client.player.getBlockInteractionRange(), client.player.getEntityInteractionRange());
            HitResult blockHitResult = camera.raycast(range, tickDelta, false);
            client.crosshairTarget = blockHitResult;
            client.targetedEntity = null;
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    public void showFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Totem")) ci.cancel();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void tiltViewWhenHurt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("NoHurtCam")) ci.cancel();
    }

}
