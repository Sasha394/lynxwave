package dev.client.api.compat;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class InventoryCompat {
    private InventoryCompat() {
    }

    public static List<ItemStack> armorItems(PlayerEntity player) {
        return List.of(
                player.getEquippedStack(EquipmentSlot.FEET),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.HEAD)
        );
    }

    public static ItemStack getArmorStack(PlayerInventory inventory, int index) {
        return switch (index) {
            case 0 -> inventory.player.getEquippedStack(EquipmentSlot.FEET);
            case 1 -> inventory.player.getEquippedStack(EquipmentSlot.LEGS);
            case 2 -> inventory.player.getEquippedStack(EquipmentSlot.CHEST);
            case 3 -> inventory.player.getEquippedStack(EquipmentSlot.HEAD);
            default -> ItemStack.EMPTY;
        };
    }
}
