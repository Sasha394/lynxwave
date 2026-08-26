package dev.client.api.injection;

import dev.client.modules.core.render.NoRender;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.SubmittableBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixins {

    @Inject(method = "addToBatch", at = @At("HEAD"), cancellable = true)
    private void onAddToBatch(SubmittableBatch batch, Frustum frustum, Camera camera, float tickProgress, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Particles")) ci.cancel();
    }
}
