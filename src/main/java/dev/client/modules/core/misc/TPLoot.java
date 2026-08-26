package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.ModeElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

public class TPLoot extends Module {
    public static TPLoot INSTANCE;

    private final ModeElement lootEnd = new ModeElement("После лута", () -> true)
            .set("Ничего", "/hub", "/spawn", "home", "Своя")
            .applyDefault("Ничего")
            .register(this);

    private final Input homeCommand = new Input("Команда", () -> lootEnd.isSelected("home"))
            .applyDefault("/home home")
            .register(this);

    private final Input customCommand = new Input("Команда", () -> lootEnd.isSelected("Своя"))
            .applyDefault("/test")
            .register(this);

    private final TimerUtil timerUtil = new TimerUtil();
    private boolean awaitingLoot;

    public TPLoot() {
        super("TPLoot", ModuleCategory.Misc, "Телепортирует на место, где лежат предметы");
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getStack();
            if (!isValuable(stack)) continue;

            BlockPos blockBelow = itemEntity.getBlockPos().down();
            if (!mc.world.getBlockState(blockBelow).isAir()
                    && itemEntity.getY() - blockBelow.getY() <= 1.0) {
                double x = itemEntity.getX() + 0.5;
                double y = itemEntity.getY();
                double z = itemEntity.getZ() + 0.5;

                sendPacket(new PlayerMoveC2SPacket.Full(
                        x,
                        y,
                        z,
                        mc.player.getYaw(),
                        mc.player.getPitch(),
                        false,
                        mc.player.horizontalCollision
                ));

                awaitingLoot = true;
            }
        }

        if (awaitingLoot && timerUtil.hasTimeElapsed(100, false)) {
            if (hasValuableInHotbar() || hasValuableInInventory()) {
                switch (lootEnd.getValue()) {
                    case "/hub" -> mc.player.networkHandler.sendChatMessage("/hub");
                    case "/spawn" -> mc.player.networkHandler.sendChatMessage("/spawn");
                    case "home" -> mc.player.networkHandler.sendChatMessage(homeCommand.getValue());
                    case "Своя" -> mc.player.networkHandler.sendChatMessage(customCommand.getValue());
                    default -> {
                    }
                }
                awaitingLoot = false;
                timerUtil.reset();
            }
        }
    }

    private boolean hasValuableInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (isValuable(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private boolean hasValuableInInventory() {
        for (int i = 9; i < 36; i++) {
            if (isValuable(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private boolean isValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return PlayerUtil.isSword(stack)
                || item instanceof PlayerHeadItem
                || PlayerUtil.isArmor(stack)
                || item == Items.TOTEM_OF_UNDYING
                || item == Items.ENDER_PEARL
                || item == Items.END_CRYSTAL
                || item == Items.FIREWORK_ROCKET
                || item == Items.ELYTRA
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.CLAY_BALL
                || item == Items.MAGMA_CREAM
                || item == Items.CRYING_OBSIDIAN;
    }
}
