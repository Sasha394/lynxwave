package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.player.EnchantmentUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import net.minecraft.block.AirBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AutoTool extends Module {
    public static AutoTool INSTANCE;

    public AutoTool() {
        super("AutoTool", ModuleCategory.Player, "Автоматически берет лучший предмет для ломания блоков");
    }

    CheckBox swapBack = new CheckBox("Возвращать обратно", () -> true).defaultValue(true).register(this);
    CheckBox saveItem = new CheckBox("Сохранять предмет", () -> true).defaultValue(true).register(this);
    CheckBox silent = new CheckBox("Пакетный", () -> true).defaultValue(false).register(this);
    CheckBox echestSilk = new CheckBox("Искать только шелковое касание", () -> true).defaultValue(true).register(this);

    public static int itemIndex;
    private boolean swap;
    private long swapDelay;
    private final List<Integer> lastItem = new ArrayList<>();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!(mc.crosshairTarget instanceof BlockHitResult)) return;
        BlockHitResult result = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = result.getBlockPos();
        if (mc.world.getBlockState(pos).isAir())
            return;

        if (getTool(pos) != -1 && mc.options.attackKey.isPressed()) {
            lastItem.add(mc.player.getInventory().getSelectedSlot());

            if (silent.getEnabled()) sendPacket(new UpdateSelectedSlotC2SPacket(getTool(pos)));
            else mc.player.getInventory().setSelectedSlot(getTool(pos));

            itemIndex = getTool(pos);
            swap = true;

            swapDelay = System.currentTimeMillis();
        } else if (swap && !lastItem.isEmpty() && System.currentTimeMillis() >= swapDelay + 300 && swapBack.getEnabled()) {
            if (silent.getEnabled())
                sendPacket(new UpdateSelectedSlotC2SPacket(lastItem.get(0)));
            else mc.player.getInventory().setSelectedSlot(lastItem.get(0));

            itemIndex = lastItem.get(0);
            lastItem.clear();
            swap = false;
        }
    }

    public static int getTool(final BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != ItemStack.EMPTY) {
                if (!(mc.player.getInventory().getStack(i).getMaxDamage() - mc.player.getInventory().getStack(i).getDamage() > 10) && AutoTool.INSTANCE.saveItem.getEnabled())
                    continue;

                final float digSpeed = EnchantmentUtil.getLevel(Enchantments.EFFICIENCY, stack);
                final float destroySpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));

                if (mc.world.getBlockState(pos).getBlock() instanceof AirBlock) return -1;
                if (mc.world.getBlockState(pos).getBlock() instanceof EnderChestBlock && AutoTool.INSTANCE.echestSilk.getEnabled()) {
                    if (EnchantmentUtil.getLevel(Enchantments.SILK_TOUCH, stack) > 0 && digSpeed + destroySpeed > CurrentFastest) {
                        CurrentFastest = digSpeed + destroySpeed;
                        index = i;
                    }
                } else if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }
}