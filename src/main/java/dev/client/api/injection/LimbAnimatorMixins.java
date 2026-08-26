package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.client.modules.core.render.NoRender;
import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LimbAnimator.class)
public abstract class LimbAnimatorMixins {

    @ModifyReturnValue(
            method = {
                    "getSpeed()F",
                    "getAmplitude(F)F",
                    "getAnimationProgress()F",
                    "getAnimationProgress(F)F"
            },
            at = @At("RETURN")
    )
    public float hideLimbs(float original) {
        return NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Limbs") ? 0 : original;
    }
}
