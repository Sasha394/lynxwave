package dev.client.api.injection;

import dev.client.modules.core.render.Cosmetic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraEntityModel.class)
public abstract class ElytraEntityModelMixins {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lynx$wingsSetAngles(BipedEntityRenderState state, CallbackInfo ci) {
        Cosmetic cosmetic = Cosmetic.INSTANCE;
        if (cosmetic == null || !cosmetic.isWingsActive()) return;
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !lynx$isLocalPlayer(playerState, client)) return;

        double output = Cosmetic.animationWings.getOutput().doubleValue();
        this.leftWing.originY = state.isInSneakingPose ? 3.0F : 0.0F;
        this.leftWing.pitch = (float) ((double) state.leftWingPitch - (0.2 + 0.6 * output));
        this.leftWing.roll = state.leftWingRoll - 1.4F;
        this.leftWing.yaw = state.leftWingYaw - 0.1F;
        this.rightWing.yaw = -this.leftWing.yaw;
        this.rightWing.originY = this.leftWing.originY;
        this.rightWing.pitch = this.leftWing.pitch;
        this.rightWing.roll = -this.leftWing.roll;
        ci.cancel();
    }

    @Unique
    private static boolean lynx$isLocalPlayer(PlayerEntityRenderState playerState, MinecraftClient client) {
        if (playerState.id == client.player.getId()) {
            return true;
        }
        return playerState.playerName != null
                && playerState.playerName.getString().equalsIgnoreCase(client.player.getNameForScoreboard());
    }
}
