package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class AutoArmor extends Module {

    public AutoArmor() {
        super("AutoArmor", ModuleCategory.Player, "Автоматически надевает лучшую броню с указанной задержкой");
    }

    Slider delay = new Slider("Задержка", () -> true).set(1f, 100f, 1).defaultValue(50f).register(this);

    private final TimerUtil timerUtil = new TimerUtil();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (MovingUtil.isMoving()) {
            return;
        }

        PlayerInventory inventoryPlayer = mc.player.getInventory();
        int[] bestIndexes = new int[4];
        int[] bestValues = new int[4];
        boolean foundBetterArmor = false;

        for (int i = 0; i < 4; ++i) {
            bestIndexes[i] = -1;
            ItemStack stack = dev.client.api.compat.InventoryCompat.getArmorStack(inventoryPlayer, i);

            if (!isItemValid(stack) || !PlayerUtil.isArmor(stack)) {
                continue;
            }

            bestValues[i] = calculateArmorValue(stack);
        }

        for (int i = 0; i < 36; ++i) {
            ItemStack stack = inventoryPlayer.getStack(i);

            if (!isItemValid(stack) || !PlayerUtil.isArmor(stack)) continue;

            int armorTypeIndex = PlayerUtil.getArmorInventoryIndex(stack);
            if (armorTypeIndex == -1) continue;

            int value = calculateArmorValue(stack);

            if (value > bestValues[armorTypeIndex]) {
                bestIndexes[armorTypeIndex] = i;
                bestValues[armorTypeIndex] = value;
                foundBetterArmor = true;
            }
        }

        if (!foundBetterArmor) {
            if (hasNoArmorInInventory(inventoryPlayer)) {
                printClient("Нет брони в инвентаре");
                toggle();
                return;
            }

            if (isFullyEquipped(inventoryPlayer)) {
                toggle();
                return;
            }
        }

        ArrayList<Integer> randomIndexes = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
        Collections.shuffle(randomIndexes);

        for (int index : randomIndexes) {
            int bestIndex = bestIndexes[index];

            if (bestIndex == -1 || (isItemValid(dev.client.api.compat.InventoryCompat.getArmorStack(inventoryPlayer, index)) && inventoryPlayer.getEmptySlot() == -1))
                continue;

            if (bestIndex < 9) {
                bestIndex += 36;
            }

            if (!this.timerUtil.isReached(this.delay.getValue().longValue())) break;

            ItemStack armorItemStack = dev.client.api.compat.InventoryCompat.getArmorStack(inventoryPlayer, index);

            if (isItemValid(armorItemStack)) {
                mc.interactionManager.clickSlot(0, 8 - index, 0, SlotActionType.QUICK_MOVE, mc.player);
            }

            mc.interactionManager.clickSlot(0, bestIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
            this.timerUtil.reset();
            break;
        }
    }

    private boolean isItemValid(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private boolean hasNoArmorInInventory(PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (PlayerUtil.isArmor(inventory.getStack(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isFullyEquipped(PlayerInventory inventory) {
        for (int i = 0; i < 4; i++) {
            if (dev.client.api.compat.InventoryCompat.getArmorStack(inventory, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int calculateArmorValue(final ItemStack stack) {
        double protection = PlayerUtil.getArmorProtection(stack);
        double toughness = PlayerUtil.getArmorToughness(stack);
        return (int) (protection * 5 + toughness * 2);
    }
}
