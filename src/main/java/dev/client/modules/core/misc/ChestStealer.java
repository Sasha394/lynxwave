package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChestStealer extends Module {

    public ChestStealer() {
        super("ChestStealer", ModuleCategory.Misc, "Автоматически забирает ценные предметы с контейнеров");
    }

    CheckBox random = new CheckBox("Рандом", () -> true).defaultValue(false).register(this);
    CheckBox closeScreen = new CheckBox("Закрывать после лута", () -> true).defaultValue(false).register(this);
    Slider delay = new Slider("Задержка", () -> true).set(1f, 100f, 1f).defaultValue(50f).register(this);

    private final TimerUtil timer = new TimerUtil();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            Inventory lowerChestInventory = container.getInventory();
            boolean allLooted = true;

            List<Integer> slotIndices = new ArrayList<>();
            for (int i = 0; i < lowerChestInventory.size(); ++i) {
                slotIndices.add(i);
            }

            if (random.getEnabled()) {
                Collections.shuffle(slotIndices);
            }

            for (int index : slotIndices) {
                if (shouldMoveItem(container, index)) {
                    allLooted = false;
                    if (delay.getValue() == 0.0f) {
                        moveItem(container, index, lowerChestInventory.size());
                    } else {
                        if (timer.isReached(delay.getValue().longValue())) {
                            mc.interactionManager.clickSlot(container.syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
                            timer.reset();
                        }
                    }
                }
            }

            if (allLooted) {
                if (closeScreen.getEnabled()) {
                    mc.player.closeScreen();
                }
                toggle();
            }
        }
    }

    private boolean shouldMoveItem(GenericContainerScreenHandler container, int index) {
        ItemStack itemStack = container.getInventory().getStack(index);
        if (itemStack.isEmpty()) return false;

        return itemStack.getItem() != Items.LIME_STAINED_GLASS_PANE && itemStack.getItem() != Items.RED_STAINED_GLASS_PANE;
    }

    private void moveItem(GenericContainerScreenHandler container, int index, int multi) {
        for (int i = 0; i < multi; i++) {
            mc.interactionManager.clickSlot(container.syncId, index + i, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }
}