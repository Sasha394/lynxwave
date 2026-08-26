package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.game.BlockPlaceEvent;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixins {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BlockItem;copyComponentsToBlockEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V", shift = At.Shift.AFTER))
    private void onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = context.getStack();
        EventManager.call(new BlockPlaceEvent(pos, state.getBlock(), stack));
    }
}
