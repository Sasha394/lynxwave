package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.other.NoPushEvent;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockMixins {

    @Inject(method = "shouldBlockVision", at = @At("HEAD"), cancellable = true)
    public void onShouldBlockVision(BlockView world, BlockPos pos, CallbackInfoReturnable<Boolean> infoReturnable) {
        NoPushEvent noPushEvent = new NoPushEvent(NoPushEvent.CollisionEnum.Blocks);
        EventManager.call(noPushEvent);

        if (noPushEvent.isCancelled()) {
            infoReturnable.setReturnValue(false);
        }
    }
}