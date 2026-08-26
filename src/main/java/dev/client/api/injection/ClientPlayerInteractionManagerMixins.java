package dev.client.api.injection;

import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.player.NoInteract;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixins {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        Block block = MinecraftClient.getInstance().world.getBlockState(hitResult.getBlockPos()).getBlock();
        if (NoInteract.INSTANCE.isEnabled() && (NoInteract.INSTANCE.mode.isSelected("Все блоки") || (NoInteract.INSTANCE.mode.isSelected("Кастом") && NoInteract.INSTANCE.filter.isSelected(NoInteract.getBlockName(block)))) && (KillAura.INSTANCE.isEnabled() || !NoInteract.INSTANCE.onlyKillAura.getEnabled())) {
            cir.setReturnValue(ActionResult.PASS);
        }

        if (NoInteract.INSTANCE.isEnabled() && NoInteract.INSTANCE.noPlaceBall.getEnabled() && MinecraftClient.getInstance().player != null && ((MinecraftClient.getInstance().player.getOffHandStack().getItem() == Items.PLAYER_HEAD && hand == Hand.OFF_HAND) || (MinecraftClient.getInstance().player.getMainHandStack().getItem() == Items.PLAYER_HEAD && hand == Hand.MAIN_HAND)))
            cir.setReturnValue(ActionResult.PASS);
    }
}
