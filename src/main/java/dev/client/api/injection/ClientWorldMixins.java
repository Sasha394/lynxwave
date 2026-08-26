package dev.client.api.injection;

import dev.client.modules.core.misc.TotemSound;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixins {

    @Unique
    private static boolean lynxwave$replacingTotem = false;

    @Inject(method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZJ)V",
            at = @At("HEAD"), cancellable = true)
    private void onPlaySound(double x, double y, double z, SoundEvent sound, SoundCategory category,
                             float volume, float pitch, boolean useDistance, long seed, CallbackInfo ci) {
        if (lynxwave$replacingTotem) return;
        if (sound != SoundEvents.ITEM_TOTEM_USE) return;

        TotemSound module = TotemSound.INSTANCE;
        if (module == null || !module.shouldReplace()) return;

        SoundEvent replacement = module.replacementEvent();
        if (replacement == SoundEvents.ITEM_TOTEM_USE) return;

        ci.cancel();
        lynxwave$replacingTotem = true;
        try {
            ((ClientWorld) (Object) this).playSoundClient(x, y, z, replacement, category, module.replacementVolume(), module.replacementPitch(), useDistance);
        } finally {
            lynxwave$replacingTotem = false;
        }
    }
}
