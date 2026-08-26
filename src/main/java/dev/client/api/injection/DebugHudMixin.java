package dev.client.api.injection;

import dev.client.modules.core.combat.killaura.rotation.RotationController;
import net.minecraft.client.gui.hud.debug.PlayerPositionDebugHudEntry;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerPositionDebugHudEntry.class)
public abstract class DebugHudMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float redirectYaw(Entity entity) {
        return RotationController.INSTANCE.getRotation().getYaw();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F"))
    private float redirectPitch(Entity entity) {
        return RotationController.INSTANCE.getRotation().getPitch();
    }
}
