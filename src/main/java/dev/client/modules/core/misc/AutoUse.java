package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoUse extends Module {

    private final SelectElements useItems = new SelectElements("Использовать", () -> true).set("Еда", "Невидимость", "Гепл").applyDefault("Еда").register(this);
    private final CheckBox stopOnFullFood = new CheckBox("Отключать при полном голоде", () -> useItems.isSelected("Еда")).defaultValue(true).register(this);
    private final CheckBox useFoodThreshold = new CheckBox("Использовать порог еды", () -> useItems.isSelected("Еда")).defaultValue(true).register(this);
    private final Slider foodThreshold = new Slider("Порог еды", () -> useFoodThreshold.getEnabled() && useItems.isSelected("Еда")).set(1.0f, 20.0f, 1.0f).defaultValue(18.0f).register(this);
    private final Slider gappleThreshold = new Slider("Порог здоровья", () -> useItems.isSelected("Гепл")).set(1.0f, 20.0f, 0.5f).defaultValue(16.0f).register(this);
    private final CheckBox disableAfterEating = new CheckBox("Отключать после заедания", () -> useItems.isSelected("Еда")).defaultValue(false).register(this);
    private final CheckBox disableAfterDrinking = new CheckBox("Отключать после запивания", () -> useItems.isSelected("Невидимость")).defaultValue(false).register(this);

    private static final long MESSAGE_COOLDOWN = 2000L;

    private UseMode currentMode = UseMode.NONE;
    private Hand activeHand = Hand.MAIN_HAND;
    private boolean usingOffhand = false;
    private boolean swappedFromInventory = false;
    private boolean previousUseKeyState = false;
    private int previousSlot = -1;
    private int usedHotbarSlot = -1;
    private int swappedInventorySlot = -1;
    private long startedUsingAt = 0L;
    private long lastInteractionAt = 0L;
    private long lastFoodMessage = 0L;
    private long lastPotionMessage = 0L;

    public AutoUse() {
        super("AutoUse", ModuleCategory.Misc, "Автоматически использует еду, геплы и зелья невидимости");
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null || mc.player == null) {
            stopUsing(StopReason.CANCELLED, false);
            return;
        }

        if (currentMode != UseMode.NONE) {
            tickActiveUse();
            return;
        }

        if (useItems.isSelected("Еда")) {
            handleFoodLogic();
            if (currentMode != UseMode.NONE) {
                return;
            }
        }

        if (useItems.isSelected("Невидимость")) {
            handleInvisibilityLogic();
            if (currentMode != UseMode.NONE) {
                return;
            }
        }

        if (useItems.isSelected("Гепл")) {
            handleGappleLogic();
        }
    }

    private void handleFoodLogic() {
        if (mc.player.getHungerManager().getFoodLevel() >= 20) {
            if (stopOnFullFood.getEnabled() && useItems.getSelected().size() == 1) {
                toggle();
            }
            return;
        }

        if (!shouldEatFood()) {
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        ItemTarget target = findFood();
        if (target == null) {
            if (System.currentTimeMillis() - lastFoodMessage >= MESSAGE_COOLDOWN) {
                printClient("Еда не найдена");
                lastFoodMessage = System.currentTimeMillis();
            }
            if (useItems.getSelected().size() == 1) {
                toggle();
            }
            return;
        }

        if (prepareUse(target)) {
            startUsing(UseMode.FOOD);
        }
    }

    private void handleInvisibilityLogic() {
        if (mc.player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            if (disableAfterDrinking.getEnabled() && useItems.getSelected().size() == 1) {
                toggle();
            }
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        ItemTarget target = findInvisibilityPotion();
        if (target == null) {
            if (System.currentTimeMillis() - lastPotionMessage >= MESSAGE_COOLDOWN) {
                printClient("Нет зелья невидимости");
                lastPotionMessage = System.currentTimeMillis();
            }
            if (useItems.getSelected().size() == 1 && useItems.isSelected("Невидимость")) {
                toggle();
            }
            return;
        }

        if (prepareUse(target)) {
            startUsing(UseMode.INVISIBILITY);
        }
    }

    private void handleGappleLogic() {
        if (!shouldUseGapple()) {
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        ItemTarget target = findGapple();
        if (target == null) {
            return;
        }

        if (prepareUse(target)) {
            startUsing(UseMode.GAPPLE);
        }
    }

    private void tickActiveUse() {
        if (mc.player == null || mc.interactionManager == null) {
            stopUsing(StopReason.CANCELLED, false);
            return;
        }

        ItemStack activeStack = getActiveStack();

        switch (currentMode) {
            case FOOD -> {
                if (!shouldEatFood()) {
                    stopUsing(StopReason.COMPLETED, true);
                    return;
                }
                if (!isFoodStack(activeStack)) {
                    stopUsing(StopReason.FAILED, true);
                    return;
                }
            }
            case INVISIBILITY -> {
                if (mc.player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    stopUsing(StopReason.COMPLETED, true);
                    return;
                }
                if (!isInvisibilityPotion(activeStack) && !mc.player.isUsingItem()) {
                    stopUsing(StopReason.FAILED, true);
                    return;
                }
            }
            case GAPPLE -> {
                if (!shouldUseGapple()) {
                    stopUsing(StopReason.COMPLETED, true);
                    return;
                }
                if (!isGappleStack(activeStack) && !mc.player.isUsingItem()) {
                    stopUsing(StopReason.FAILED, true);
                    return;
                }
            }
        }

        ensureUsingActive(activeStack);

        if (!mc.player.isUsingItem() && System.currentTimeMillis() - startedUsingAt > 2000L) {
            stopUsing(StopReason.CANCELLED, true);
        }
    }

    private void ensureUsingActive(ItemStack stack) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        mc.options.useKey.setPressed(true);

        if (System.currentTimeMillis() - lastInteractionAt >= 120L) {
            if (!mc.player.isUsingItem() && isValidForMode(stack)) {
                mc.player.setCurrentHand(activeHand);
                mc.interactionManager.interactItem(mc.player, activeHand);
                lastInteractionAt = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldEatFood() {
        if (!useItems.isSelected("Еда")) {
            return false;
        }

        int hunger = mc.player.getHungerManager().getFoodLevel();
        if (stopOnFullFood.getEnabled() && hunger >= 20) {
            return false;
        }

        if (!useFoodThreshold.getEnabled()) {
            return hunger < 20;
        }

        return hunger <= foodThreshold.getValue().intValue();
    }

    private boolean shouldUseGapple() {
        if (!useItems.isSelected("Гепл") || mc.player == null) {
            return false;
        }

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health > gappleThreshold.getValue()) {
            return false;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.GOLDEN_APPLE))) {
            return false;
        }

        return findGapple() != null;
    }

    private boolean prepareUse(ItemTarget target) {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        previousUseKeyState = mc.options.useKey.isPressed();
        previousSlot = mc.player.getInventory().getSelectedSlot();
        usedHotbarSlot = previousSlot;
        if (!isValidHotbarSlot(usedHotbarSlot)) {
            return false;
        }
        swappedFromInventory = false;
        swappedInventorySlot = -1;
        usingOffhand = false;
        activeHand = Hand.MAIN_HAND;

        return switch (target.type()) {
            case MAIN_HAND -> true;
            case OFF_HAND -> {
                usingOffhand = true;
                activeHand = Hand.OFF_HAND;
                yield true;
            }
            case HOTBAR -> selectHotbar(target.slot());
            case INVENTORY -> moveInventoryItemToHand(target.slot());
        };
    }

    private void startUsing(UseMode mode) {
        currentMode = mode;
        startedUsingAt = System.currentTimeMillis();
        lastInteractionAt = 0L;
        ensureUsingActive(getActiveStack());
    }

    private boolean selectHotbar(int slot) {
        if (!isValidHotbarSlot(slot)) {
            return false;
        }

        usedHotbarSlot = slot;
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        return true;
    }

    private boolean moveInventoryItemToHand(int slot) {
        if (!isValidInventorySlot(slot) || !isValidHotbarSlot(usedHotbarSlot) || mc.player == null || mc.interactionManager == null) {
            return false;
        }

        var handler = mc.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }

        int handlerSlot = toHandlerSlot(slot);
        int handlerHotbar = toHandlerHotbarSlot(usedHotbarSlot);
        if (handlerSlot == -1 || handlerHotbar == -1) {
            return false;
        }

        try {
            mc.interactionManager.clickSlot(handler.syncId, handlerSlot, usedHotbarSlot, SlotActionType.SWAP, mc.player);
        } catch (Throwable ignored) {
            return false;
        }

        swappedFromInventory = true;
        swappedInventorySlot = slot;
        return true;
    }

    private void stopUsing(StopReason reason, boolean restore) {
        UseMode previousMode = currentMode;

        if (mc.options != null) {
            mc.options.useKey.setPressed(previousUseKeyState);
        }

        if (restore && mc.player != null) {
            if (swappedFromInventory) {
                restoreInventorySwap();
            }

            if (!usingOffhand && isValidHotbarSlot(previousSlot) && mc.player.getInventory().getSelectedSlot() != previousSlot) {
                mc.player.getInventory().setSelectedSlot(previousSlot);
                sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
        }

        resetState();

        if (reason == StopReason.COMPLETED && isEnabled()) {
            switch (previousMode) {
                case FOOD -> {
                    if (disableAfterEating.getEnabled()) {
                        toggle();
                    }
                }
                case INVISIBILITY -> {
                    if (disableAfterDrinking.getEnabled()) {
                        toggle();
                    }
                }
                case GAPPLE -> {
                }
                case NONE -> {
                }
            }
        }
    }

    private void restoreInventorySwap() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        var handler = mc.player.currentScreenHandler;
        if (handler == null) {
            return;
        }

        int handlerSlot = toHandlerSlot(swappedInventorySlot);
        int handlerHotbar = toHandlerHotbarSlot(usedHotbarSlot);
        if (handlerSlot == -1 || handlerHotbar == -1) {
            return;
        }

        try {
            mc.interactionManager.clickSlot(handler.syncId, handlerSlot, usedHotbarSlot, SlotActionType.SWAP, mc.player);
        } catch (Throwable ignored) {
        }
    }

    private void resetState() {
        currentMode = UseMode.NONE;
        activeHand = Hand.MAIN_HAND;
        usingOffhand = false;
        swappedFromInventory = false;
        previousSlot = -1;
        usedHotbarSlot = -1;
        swappedInventorySlot = -1;
        startedUsingAt = 0L;
        lastInteractionAt = 0L;
        previousUseKeyState = false;
    }

    private ItemStack getActiveStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        return usingOffhand ? mc.player.getOffHandStack() : mc.player.getMainHandStack();
    }

    private ItemTarget findFood() {
        ItemStack main = mc.player.getMainHandStack();
        if (isFoodStack(main)) {
            return new ItemTarget(ItemLocationType.MAIN_HAND, -1);
        }

        ItemStack off = mc.player.getOffHandStack();
        if (isFoodStack(off)) {
            return new ItemTarget(ItemLocationType.OFF_HAND, -1);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isFoodStack(stack)) {
                return new ItemTarget(ItemLocationType.HOTBAR, i);
            }
        }

        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isFoodStack(stack)) {
                return new ItemTarget(ItemLocationType.INVENTORY, i);
            }
        }

        return null;
    }

    private ItemTarget findGapple() {
        ItemStack main = mc.player.getMainHandStack();
        if (isGappleStack(main)) {
            return new ItemTarget(ItemLocationType.MAIN_HAND, -1);
        }

        ItemStack off = mc.player.getOffHandStack();
        if (isGappleStack(off)) {
            return new ItemTarget(ItemLocationType.OFF_HAND, -1);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isGappleStack(stack)) {
                return new ItemTarget(ItemLocationType.HOTBAR, i);
            }
        }

        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isGappleStack(stack)) {
                return new ItemTarget(ItemLocationType.INVENTORY, i);
            }
        }

        return null;
    }

    private ItemTarget findInvisibilityPotion() {
        ItemStack off = mc.player.getOffHandStack();
        if (isInvisibilityPotion(off)) {
            return new ItemTarget(ItemLocationType.OFF_HAND, -1);
        }

        ItemStack main = mc.player.getMainHandStack();
        if (isInvisibilityPotion(main)) {
            return new ItemTarget(ItemLocationType.MAIN_HAND, -1);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isInvisibilityPotion(stack)) {
                return new ItemTarget(ItemLocationType.HOTBAR, i);
            }
        }

        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isInvisibilityPotion(stack)) {
                return new ItemTarget(ItemLocationType.INVENTORY, i);
            }
        }

        return null;
    }

    private boolean isValidForMode(ItemStack stack) {
        return switch (currentMode) {
            case NONE -> false;
            case FOOD -> isFoodStack(stack);
            case INVISIBILITY -> isInvisibilityPotion(stack);
            case GAPPLE -> isGappleStack(stack);
        };
    }

    private boolean isFoodStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    private boolean isConsumable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.get(DataComponentTypes.CONSUMABLE) != null || stack.get(DataComponentTypes.FOOD) != null);
    }

    private boolean isGappleStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.GOLDEN_APPLE);
    }

    private static boolean isInvisibilityPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        PotionContentsComponent component = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (component == null) {
            return false;
        }

        return component.potion()
                .map(entry -> entry.matches(Potions.INVISIBILITY) || entry.matches(Potions.LONG_INVISIBILITY))
                .orElse(false);
    }

    private boolean isValidHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    private boolean isValidInventorySlot(int slot) {
        return slot >= 9 && slot < mc.player.getInventory().size();
    }

    private int toHandlerSlot(int invIndex) {
        if (invIndex >= 0 && invIndex <= 8) {
            return 36 + invIndex;
        }
        if (invIndex >= 9 && invIndex <= 35) {
            return invIndex;
        }
        return -1;
    }

    private int toHandlerHotbarSlot(int hotbarSlot) {
        if (isValidHotbarSlot(hotbarSlot)) {
            return 36 + hotbarSlot;
        }
        return -1;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        stopUsing(StopReason.CANCELLED, true);
    }

    private enum UseMode {
        NONE,
        FOOD,
        INVISIBILITY,
        GAPPLE
    }

    private enum StopReason {
        CANCELLED,
        COMPLETED,
        FAILED
    }

    private record ItemTarget(ItemLocationType type, int slot) {
    }

    private enum ItemLocationType {
        MAIN_HAND,
        OFF_HAND,
        HOTBAR,
        INVENTORY
    }
}
