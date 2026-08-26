package dev.client.api.nullcry.helper.entity.world;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.KillAura;
import lombok.experimental.UtilityClass;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

@UtilityClass
public class SlotHelper implements ClientApi {

    public int getItemSlot(Item item) {
        if (mc.player == null) return -1;
        int slot = -1;

        for (ItemStack stack : dev.client.api.compat.InventoryCompat.armorItems(mc.player)) {
            if (stack.getItem() == item) return -2;
        }

        for (int i = 0; i < 36; ++i) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) {
                slot = i;
                break;
            }
        }

        if (slot < 9 && slot != -1) {
            slot += 36;
        }

        return slot;
    }

    public int getItemInventoryOrHotBar(Item item, boolean hotbar) {
        int firstSlot = hotbar ? 0 : 9;
        int lastSlot = hotbar ? 9 : 36;
        int finalSlot = -1;
        for (int i = firstSlot; i < lastSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                finalSlot = i;
            }
        }

        return finalSlot;
    }

    public int getItemHotBar(boolean inHotBar) {
        int firstSlot = inHotBar ? 0 : 9;
        int lastSlot = inHotBar ? 9 : 36;
        int finalSlot = -1;
        for (int i = firstSlot; i < lastSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TORCH) {
                continue;
            }

            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem || mc.player.getInventory().getStack(i).getItem() == Items.WATER_BUCKET) {
                finalSlot = i;
            }
        }

        return finalSlot;
    }

    public int getItemInInventory(Item item) {
        int finalSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                finalSlot = i;
            }
        }

        return finalSlot;
    }

    public int getAxeInInventory(boolean hotbar) {
        if (mc.player == null) return -1;

        int firstSlot = hotbar ? 0 : 9;
        int lastSlot = hotbar ? 9 : 36;

        for (int i = firstSlot; i < lastSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    public static int getAxe() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    public void hotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() == item) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(i));
                if (KillAura.INSTANCE.getTarget() != null) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
                }
                mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
                break;
            }
        }
    }

}