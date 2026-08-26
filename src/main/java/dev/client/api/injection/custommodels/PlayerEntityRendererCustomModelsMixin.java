package dev.client.api.injection.custommodels;

import dev.client.modules.core.render.Cosmetic;
import dev.client.modules.core.render.custommodels.ICustomPlayerModelState;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererCustomModelsMixin<AvatarlikeEntity extends PlayerLikeEntity & ClientPlayerLikeEntity> {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void lynxwave$updateCustomModelState(AvatarlikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        Cosmetic cosmetic = Cosmetic.INSTANCE;
        boolean enabled = cosmetic != null && cosmetic.shouldApplyCustomModelTo(player);
        ((ICustomPlayerModelState) state).lynxwave$setCustomModel(enabled, enabled ? cosmetic.getCustomModelName() : "");
    }
}
