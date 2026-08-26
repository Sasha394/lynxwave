package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AirPlace extends Module {

    public AirPlace() {
        super("AirPlace", ModuleCategory.Misc, "Позволяет ставить блоки в воздухе");
    }

    private final TimerUtil placeTimer = new TimerUtil();
    int cps = 5;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.options.useKey.isPressed()) {

            if (placeTimer.isReached(1000 / cps)) {
                ItemStack heldItem = mc.player.getStackInHand(Hand.MAIN_HAND);

                if (!heldItem.isEmpty() && Block.getBlockFromItem(heldItem.getItem()) != Blocks.AIR) {
                    BlockPos targetPos = getTargetBlockPos();

                    if (targetPos != null && mc.world.isAir(targetPos)) {
                        placeBlock(targetPos);
                    }
                }
                placeTimer.reset();
            }
        }
    }

    private BlockPos getTargetBlockPos() {
        Vec3d lookVec = mc.player.getRotationVector();
        Vec3d targetVec = dev.client.api.compat.EntityCompat.pos(mc.player).add(lookVec.multiply(5.0D));
        BlockPos targetPos = BlockPos.ofFloored(targetVec);
        if (mc.world.isAir(targetPos)) {
            return targetPos;
        }
        return null;
    }

    private void placeBlock(BlockPos pos) {
        BlockHitResult blockRayTraceResult = new BlockHitResult(Vec3d.ZERO, Direction.UP, pos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockRayTraceResult);

        if (result == ActionResult.SUCCESS) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}