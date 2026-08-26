package dev.client.api.injection;

import dev.client.api.injection.accessor.IBossBarHudAccessor;
import dev.client.modules.core.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarMixins implements IBossBarHudAccessor {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void onRender(DrawContext context, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("BossBar")) ci.cancel();
    }
}