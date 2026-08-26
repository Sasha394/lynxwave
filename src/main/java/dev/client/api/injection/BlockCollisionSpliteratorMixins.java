package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.other.CollisionEvent;
import dev.client.modules.core.player.NoWeb;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlockCollisionSpliterator.class, priority = 800)
public abstract class BlockCollisionSpliteratorMixins {

    @Redirect(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState onComputeNext(BlockView instance, BlockPos blockPos) {
        if (!NoWeb.INSTANCE.isEnabled()) return instance.getBlockState(blockPos);
        CollisionEvent event = new CollisionEvent(instance.getBlockState(blockPos), blockPos);
        EventManager.call(event);
        return event.getState();
    }
}

