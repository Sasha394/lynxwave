package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdVClip implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;
    final MinecraftClient mc;

    @Override
    public List<String> parametersCommand() {
        return List.of("");
    }

    @Override
    public void execute(Parameters parameters) {
        final BlockPos playerPos = mc.player.getBlockPos();
        float yOffset;

        String direction = parameters.asString(0).orElseThrow(() -> new CommandException(Formatting.GRAY + "Необходимо указать направление (up/down) или расстояние."));

        switch (direction) {
            case "up" -> yOffset = findOffset(playerPos, true);
            case "down" -> yOffset = findOffset(playerPos, false);
            default -> yOffset = parseOffset(direction);
        }

        if (yOffset != 0) {
            int elytraSlot = getElytraSlot();
            boolean hasElytra = elytraSlot != -1 && elytraSlot != -2;

            if (hasElytra) {
                switchElytra(elytraSlot, true);
            }

            teleport(yOffset, hasElytra);

            if (hasElytra) {
                switchElytra(elytraSlot, false);
            }
        } else {
            logger.log(Formatting.RED + "Не удалось выполнить телепортацию.");
        }
    }

    @Override
    public String name() {
        return "vclip";
    }

    @Override
    public String description() {
        return "Телепортирует игрока вверх или вниз по вертикали";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "vclip up ➜ Телепортация вверх до ближайшего свободного пространства",
                prefix.get() + "vclip down ➜ Телепортация вниз до ближайшего свободного пространства",
                prefix.get() + "vclip <distance> ➜ Телепортация на указанное расстояние",
                "Пример: " + Formatting.RED + prefix.get() + "vclip 10"
        );
    }

    private float findOffset(BlockPos playerPos, boolean toUp) {
        int startY = toUp ? 3 : -1;
        int endY = toUp ? 255 : -255;
        int step = toUp ? 1 : -1;

        for (int i = startY; i != endY; i += step) {
            BlockPos targetPos = playerPos.add(0, i, 0);

            if (mc.world.getBlockState(targetPos) == Blocks.AIR.getDefaultState()) {
                return i + (toUp ? 1 : -1);
            }

            if (mc.world.getBlockState(targetPos) == Blocks.BEDROCK.getDefaultState() && !toUp) {
                logger.log(Formatting.RED + "Телепортация под землю невозможна.");
                return 0;
            }
        }
        return 0;
    }

    private void teleport(float yOffset, boolean elytra) {
        if (elytra) {
            for (int i = 0; i < 2; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
            }
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + yOffset, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + yOffset, mc.player.getZ());
            String blockUnit = (yOffset > 1) ? "блока" : "блок";
            logger.log(Formatting.GRAY + "Попытка телепортироваться с элитрой...");
            logger.log(String.format("Вы были успешно телепортированы на %.1f %s по вертикали", yOffset, blockUnit));
            return;
        }
        int packetsCount = calculatePacketsCount(yOffset);
        for (int i = 0; i < packetsCount; i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround(), mc.player.horizontalCollision));
        }
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + yOffset, mc.player.getZ(), false, mc.player.horizontalCollision));
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + yOffset, mc.player.getZ());
        String blockUnit = (yOffset > 1) ? "блока" : "блок";
        logger.log(String.format("Вы были успешно телепортированы на %.1f %s по вертикали", yOffset, blockUnit));
    }

    private float parseOffset(String distance) {
        if (NumberUtils.isNumber(distance)) {
            return Float.parseFloat(distance);
        }
        logger.log(Formatting.RED + distance + Formatting.GRAY + " не является числом!");
        return 0;
    }

    private int calculatePacketsCount(float yOffset) {
        return Math.max((int) (yOffset / 1000), 3);
    }

    private void switchElytra(int elytraSlot, boolean equip) {
        final int chestplateSlot = 6;
        if (equip) {
            mc.interactionManager.clickSlot(0, elytraSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, chestplateSlot, 0, SlotActionType.PICKUP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, chestplateSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, elytraSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private int getElytraSlot() {
        for (ItemStack stack : dev.client.api.compat.InventoryCompat.armorItems(mc.player)) {
            if (stack.getItem() == Items.ELYTRA) {
                return -2;
            }
        }
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.ELYTRA) {
                slot = i;
                break;
            }
        }
        if (slot < 9 && slot != -1) {
            slot = slot + 36;
        }
        return slot;
    }
}
