package dev.client.api.nullcry.helper.entity.world;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

@UtilityClass
public class ItemUseHelper implements ClientApi {

    private final long HOTBAR_SELECT_DELAY = 20L;
    private final long HOTBAR_RESTORE_DELAY = 40L;
    private final long INVENTORY_SWAP_INTERVAL = 20L;
    private final long INVENTORY_SELECT_TIMEOUT = 80L;
    private final long INVENTORY_POST_USE_DELAY = 30L;
    private final long INVENTORY_RESTORE_INTERVAL = 20L;
    private final long HW_LEGIT_SCREEN_HOLD_MS = 50L;

    private final int STAGE_IDLE = -1;
    private final int STAGE_WAIT_USE = 0;
    private final int STAGE_WAIT_RESTORE = 1;

    private int stage = STAGE_IDLE;
    private long stageTimer = 0L;
    private Hand pendingHand = Hand.MAIN_HAND;
    private boolean pendingSwing = false;
    private int previousSlot = -1;
    private int targetHotbarSlot = -1;

    private final int INV_STATE_IDLE = -1;
    private final int INV_STATE_SWAP_IN = 0;
    private final int INV_STATE_SELECT = 1;
    private final int INV_STATE_WAIT_ACK = 2;
    private final int INV_STATE_USE = 3;
    private final int INV_STATE_POST_USE = 4;
    private final int INV_STATE_RESELECT = 5;
    private final int INV_STATE_RESTORE = 6;
    private final int INV_STATE_SCREEN_OPEN = 7;

    private int invUseState = INV_STATE_IDLE;
    private long invUseStepAt = 0L;
    private boolean invSelectAck = false;
    private int invUseInvSlot = -1;
    private int invUseTempHotbar = -1;
    private int invUsePrevSelected = -1;
    private ItemStack invUseHotbarSnapshot = ItemStack.EMPTY;
    private boolean invUseScreenLegit = false;
    private boolean invUseOpenedScreen = false;
    private long hwLegitCloseAt = 0L;
    private boolean hwLegitOpened = false;

    public int spoofUseItem(int previousSlot, int hbSlot, int invSlot) {
        return spoofUseItem(previousSlot, hbSlot, invSlot, Hand.MAIN_HAND, true, true);
    }

    public int spoofUseItem(int previousSlotParam, int hbSlot, int invSlot, Hand hand, boolean swing) {
        return spoofUseItem(previousSlotParam, hbSlot, invSlot, hand, swing, true);
    }

    public int spoofUseItem(int previousSlotParam, int hbSlot, int invSlot, Hand hand, boolean swing, boolean legit) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return -1;
        }

        if (!legit) {
            resetAll();
            return spoofUseItemNonLegit(previousSlotParam, hbSlot, invSlot, hand, swing);
        }

        if (stage != STAGE_IDLE || invUseState != INV_STATE_IDLE) {
            return -1;
        }

        if (isValidHotbarSlot(hbSlot)) {
            return startHotbarUse(previousSlotParam, hbSlot, hand, swing);
        }

        if (invSlot != -1) {
            boolean screenLegit = ConnectionHelper.isHW();
            return startInventoryUse(previousSlotParam, invSlot, hand, swing, screenLegit);
        }

        return -1;
    }

    public boolean isUseInProgress() {
        return stage != STAGE_IDLE || invUseState != INV_STATE_IDLE;
    }

    public void useHand(Hand hand, boolean swing) {
        if (mc == null || mc.player == null) {
            return;
        }
        sendUsePacket(hand, swing);
    }

    public int spoofUseItemHwLegit(int previousSlotParam, int hbSlot, int invSlot, Hand hand, boolean swing) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return -1;
        }

        if (stage != STAGE_IDLE || invUseState != INV_STATE_IDLE) {
            return -1;
        }

        if (isValidHotbarSlot(hbSlot)) {
            return spoofUseItem(previousSlotParam, hbSlot, -1, hand, swing, true);
        }

        if (invSlot != -1) {
            return useFromInventoryScreen(previousSlotParam, invSlot, hand, swing);
        }

        return -1;
    }

    private int spoofUseItemNonLegit(int previousSlotParam, int hbSlot, int invSlot, Hand hand, boolean swing) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return -1;
        }

        var player = mc.player;
        var inventory = player.getInventory();
        int activeSlot = inventory.getSelectedSlot();

        if (isValidHotbarSlot(hbSlot)) {
            return useFromHotbarNonLegit(previousSlotParam, hbSlot, activeSlot, hand, swing);
        }

        if (invSlot != -1) {
            int handlerSlot = toHandlerSlot(invSlot);
            if (handlerSlot != -1 && isValidHotbarSlot(activeSlot)) {
                return useFromInventoryNonLegit(previousSlotParam, invSlot, handlerSlot, activeSlot, hand, swing);
            }
        }

        return -1;
    }

    private int useFromInventoryScreen(int previousSlotParam, int invSlot, Hand hand, boolean swing) {
        return startInventoryUse(previousSlotParam, invSlot, hand, swing, true);
    }

    public void handleUpdate() {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            resetAll();
            return;
        }

        long now = System.currentTimeMillis();
        handleHwLegitScreenClose(now);

        if (invUseState != INV_STATE_IDLE) {
            handleInventoryState(now);
            return;
        }

        if (stage == STAGE_IDLE) {
            return;
        }

        if (stage == STAGE_WAIT_USE) {
            if (now - stageTimer >= HOTBAR_SELECT_DELAY) {
                sendUsePacket(pendingHand, pendingSwing);
                stage = STAGE_WAIT_RESTORE;
                stageTimer = now;
            }
            return;
        }

        if (stage == STAGE_WAIT_RESTORE && now - stageTimer >= HOTBAR_RESTORE_DELAY) {
            if (isValidHotbarSlot(previousSlot) && previousSlot != targetHotbarSlot) {
                selectHotbarSlot(previousSlot);
            }
            resetStage();
        }
    }

    public void handlePacket(PacketEvent event) {
        if (event.isSend()) {
            return;
        }

        if (event.getPacket() instanceof UpdateSelectedSlotS2CPacket packet) {
            if (invUseTempHotbar == packet.slot()) {
                invSelectAck = true;
            }
        }
    }

    private int startHotbarUse(int previousSlotParam, int hbSlot, Hand hand, boolean swing) {
        if (!isValidHotbarSlot(hbSlot)) {
            return -1;
        }

        long now = System.currentTimeMillis();

        previousSlot = previousSlotParam;
        targetHotbarSlot = hbSlot;
        pendingHand = hand;
        pendingSwing = swing;
        stageTimer = now;
        stage = STAGE_WAIT_USE;

        selectHotbarSlot(hbSlot);
        return hbSlot;
    }

    private int startInventoryUse(int previousSlotParam, int invSlot, Hand hand, boolean swing, boolean screenLegit) {
        var player = mc.player;
        var inventory = player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();

        if (!isValidHotbarSlot(selectedSlot)) {
            return -1;
        }

        int handlerSlot = toHandlerSlot(invSlot);
        if (handlerSlot == -1) {
            return -1;
        }

        boolean openedScreen = false;
        if (screenLegit) {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
                return -1;
            }
            if (!(mc.currentScreen instanceof InventoryScreen)) {
                mc.setScreen(new InventoryScreen(player));
                openedScreen = true;
            }
            var handler = player.currentScreenHandler;
            if (handler == null || !handler.getCursorStack().isEmpty()) {
                if (openedScreen) {
                    mc.setScreen(null);
                }
                return -1;
            }
        }

        previousSlot = previousSlotParam;
        targetHotbarSlot = selectedSlot;
        pendingHand = hand;
        pendingSwing = swing;

        invUseInvSlot = invSlot;
        invUseTempHotbar = selectedSlot;
        invUsePrevSelected = previousSlotParam;
        invUseHotbarSnapshot = inventory.getStack(selectedSlot).copy();
        invUseState = screenLegit ? INV_STATE_SCREEN_OPEN : INV_STATE_SWAP_IN;
        invUseStepAt = screenLegit ? System.currentTimeMillis() : 0L;
        invSelectAck = false;
        invUseScreenLegit = screenLegit;
        invUseOpenedScreen = openedScreen;

        return invSlot;
    }

    private int useFromHotbarNonLegit(int previousSlotParam, int targetSlot, int activeSlot, Hand hand, boolean swing) {
        if (!isValidHotbarSlot(targetSlot)) {
            return -1;
        }

        int restoreSlot = isValidHotbarSlot(previousSlotParam) ? previousSlotParam : activeSlot;

        if (targetSlot != activeSlot) {
            updateClientSelectedSlot(targetSlot);
            Module.sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        } else {
            updateClientSelectedSlot(targetSlot);
        }

        sendUsePacket(hand, swing);

        if (targetSlot != restoreSlot && isValidHotbarSlot(restoreSlot)) {
            updateClientSelectedSlot(restoreSlot);
            Module.sendPacket(new UpdateSelectedSlotC2SPacket(restoreSlot));
        }

        return targetSlot;
    }

    private int useFromInventoryNonLegit(int previousSlotParam, int invSlot, int handlerSlot, int activeSlot, Hand hand, boolean swing) {
        var player = mc.player;
        var handler = player.currentScreenHandler;
        if (handler == null || !isValidHotbarSlot(activeSlot)) {
            return -1;
        }

        int syncId = handler.syncId;
        boolean swappedIn = false;
        int restoreSlot = isValidHotbarSlot(previousSlotParam) ? previousSlotParam : activeSlot;

        try {
            mc.interactionManager.clickSlot(syncId, handlerSlot, activeSlot, SlotActionType.SWAP, player);
            swappedIn = true;
        } catch (Throwable t) {
            return -1;
        }

        try {
            updateClientSelectedSlot(activeSlot);
            Module.sendPacket(new UpdateSelectedSlotC2SPacket(activeSlot));
            sendUsePacket(hand, swing);
        } finally {
            if (swappedIn) {
                try {
                    mc.interactionManager.clickSlot(syncId, handlerSlot, activeSlot, SlotActionType.SWAP, player);
                } catch (Throwable ignored) {
                }
            }
            if (isValidHotbarSlot(restoreSlot)) {
                updateClientSelectedSlot(restoreSlot);
                Module.sendPacket(new UpdateSelectedSlotC2SPacket(restoreSlot));
            } else {
                updateClientSelectedSlot(activeSlot);
                Module.sendPacket(new UpdateSelectedSlotC2SPacket(activeSlot));
            }
        }

        return invSlot;
    }

    private void handleInventoryState(long now) {
        if (mc.player == null || mc.interactionManager == null) {
            resetInventoryUse();
            return;
        }

        var player = mc.player;
        var handler = player.currentScreenHandler;
        if (handler == null) {
            resetInventoryUse();
            return;
        }

        switch (invUseState) {
            case INV_STATE_SCREEN_OPEN -> {
                if (!invUseScreenLegit) {
                    invUseState = INV_STATE_SWAP_IN;
                    invUseStepAt = now;
                    return;
                }

                if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
                    failInventoryRestore();
                    return;
                }

                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    mc.setScreen(new InventoryScreen(player));
                    invUseOpenedScreen = true;
                    invUseStepAt = now;
                    return;
                }

                if (!hasElapsed(now, invUseStepAt, INVENTORY_SWAP_INTERVAL)) {
                    return;
                }

                if (!handler.getCursorStack().isEmpty()) {
                    failInventoryRestore();
                    return;
                }

                invUseState = INV_STATE_SWAP_IN;
                invUseStepAt = now;
            }
            case INV_STATE_SWAP_IN -> {
                if (!hasElapsed(now, invUseStepAt, 0L)) {
                    return;
                }

                int handlerInv = toHandlerSlot(invUseInvSlot);
                int handlerHotbar = toHandlerHotbarSlot(invUseTempHotbar);
                if (handlerInv == -1 || handlerHotbar == -1) {
                    failInventoryRestore();
                    return;
                }

                if (!clickSwap(handler.syncId, handlerInv, invUseTempHotbar, player)) {
                    failInventoryRestore();
                    return;
                }

                invUseState = INV_STATE_SELECT;
                invUseStepAt = now;
            }
            case INV_STATE_SELECT -> {
                if (!hasElapsed(now, invUseStepAt, INVENTORY_SWAP_INTERVAL)) {
                    return;
                }

                if (player.getInventory().getSelectedSlot() != invUseTempHotbar) {
                    selectHotbarSlot(invUseTempHotbar);
                } else {
                    Module.sendPacket(new UpdateSelectedSlotC2SPacket(invUseTempHotbar));
                }

                invSelectAck = false;
                invUseState = INV_STATE_WAIT_ACK;
                invUseStepAt = now;
            }
            case INV_STATE_WAIT_ACK -> {
                if (!invSelectAck && now - invUseStepAt < INVENTORY_SELECT_TIMEOUT) {
                    return;
                }
                invUseState = INV_STATE_USE;
                invUseStepAt = now;
            }
            case INV_STATE_USE -> {
                if (!hasElapsed(now, invUseStepAt, 0L)) {
                    return;
                }

                sendUsePacket(pendingHand, pendingSwing);
                invUseState = INV_STATE_POST_USE;
                invUseStepAt = now;
            }
            case INV_STATE_POST_USE -> {
                if (!hasElapsed(now, invUseStepAt, INVENTORY_POST_USE_DELAY)) {
                    return;
                }

                if (isValidHotbarSlot(invUsePrevSelected) && invUsePrevSelected != invUseTempHotbar) {
                    selectHotbarSlot(invUsePrevSelected);
                }

                invUseState = INV_STATE_RESELECT;
                invUseStepAt = now;
            }
            case INV_STATE_RESELECT -> {
                if (!hasElapsed(now, invUseStepAt, INVENTORY_RESTORE_INTERVAL)) {
                    return;
                }

                invUseState = INV_STATE_RESTORE;
                invUseStepAt = now;
            }
            case INV_STATE_RESTORE -> {
                restoreInventoryStack(handler, player);
                if (invUseScreenLegit) {
                    scheduleHwLegitScreenClose(invUseOpenedScreen, now);
                }
                resetInventoryUse();
            }
        }
    }

    private void resetStage() {
        stage = STAGE_IDLE;
        stageTimer = 0L;
        pendingHand = Hand.MAIN_HAND;
        pendingSwing = false;
        previousSlot = -1;
        targetHotbarSlot = -1;
    }

    private void resetInventoryUse() {
        invUseState = INV_STATE_IDLE;
        invUseStepAt = 0L;
        invSelectAck = false;
        invUseInvSlot = -1;
        invUseTempHotbar = -1;
        invUsePrevSelected = -1;
        invUseHotbarSnapshot = ItemStack.EMPTY;
        invUseScreenLegit = false;
        invUseOpenedScreen = false;
        resetStage();
    }

    private void resetAll() {
        resetInventoryUse();
        resetStage();
    }

    private void scheduleHwLegitScreenClose(boolean opened, long now) {
        if (!opened && !hwLegitOpened) {
            return;
        }
        hwLegitOpened = true;
        hwLegitCloseAt = now + HW_LEGIT_SCREEN_HOLD_MS;
    }

    private void handleHwLegitScreenClose(long now) {
        if (!hwLegitOpened || now < hwLegitCloseAt) {
            return;
        }
        if (stage != STAGE_IDLE || invUseState != INV_STATE_IDLE) {
            return;
        }
        if (mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
        }
        hwLegitOpened = false;
        hwLegitCloseAt = 0L;
    }

    private void failInventoryRestore() {
        if (isValidHotbarSlot(invUsePrevSelected) && mc.player != null) {
            selectHotbarSlot(invUsePrevSelected);
        }
        if (invUseScreenLegit) {
            scheduleHwLegitScreenClose(invUseOpenedScreen, System.currentTimeMillis());
        }
        resetInventoryUse();
    }

    private void restoreInventoryStack(net.minecraft.screen.ScreenHandler handler, net.minecraft.client.network.ClientPlayerEntity player) {
        int handlerInv = toHandlerSlot(invUseInvSlot);
        int handlerHotbar = toHandlerHotbarSlot(invUseTempHotbar);

        if (handlerInv == -1 || handlerHotbar == -1) {
            failInventoryRestore();
            return;
        }

        if (!clickSwap(handler.syncId, handlerInv, invUseTempHotbar, player)) {
            failInventoryRestore();
            return;
        }

        syncClientInventoryView(handler, player);
    }

    private boolean clickSwap(int syncId, int handlerSlot, int hotbarSlot, net.minecraft.client.network.ClientPlayerEntity player) {
        try {
            mc.interactionManager.clickSlot(syncId, handlerSlot, hotbarSlot, SlotActionType.SWAP, player);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void syncClientInventoryView(net.minecraft.screen.ScreenHandler handler, net.minecraft.client.network.ClientPlayerEntity player) {
        var inventory = player.getInventory();

        if (isValidHotbarSlot(invUseTempHotbar)) {
            int handlerHotbar = toHandlerHotbarSlot(invUseTempHotbar);
            if (handlerHotbar != -1) {
                var slot = handler.getSlot(handlerHotbar);
                if (slot != null) {
                    ItemStack serverStack = slot.getStack();
                    ItemStack visual = resolveHotbarVisual(serverStack);
                    applyClientStack(inventory, slot, invUseTempHotbar, visual);
                }
            }
        }

        if (invUseInvSlot >= 0 && invUseInvSlot < inventory.size()) {
            int handlerInv = toHandlerSlot(invUseInvSlot);
            if (handlerInv != -1) {
                var slot = handler.getSlot(handlerInv);
                if (slot != null) {
                    ItemStack serverStack = slot.getStack();
                    ItemStack visual = serverStack.isEmpty() ? ItemStack.EMPTY : serverStack.copy();
                    applyClientStack(inventory, slot, invUseInvSlot, visual);
                }
            }
        }

        inventory.markDirty();
    }

    private ItemStack resolveHotbarVisual(ItemStack serverStack) {
        if (invUseHotbarSnapshot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!ItemStack.areItemsAndComponentsEqual(invUseHotbarSnapshot, serverStack)) {
            return invUseHotbarSnapshot.copy();
        }
        return serverStack.isEmpty() ? ItemStack.EMPTY : serverStack.copy();
    }

    private void applyClientStack(net.minecraft.entity.player.PlayerInventory inventory, net.minecraft.screen.slot.Slot slot, int invSlot, ItemStack stack) {
        ItemStack newStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        inventory.setStack(invSlot, newStack);
        ItemStack slotStack = newStack.isEmpty() ? ItemStack.EMPTY : newStack.copy();
        slot.setStack(slotStack);
        slot.markDirty();
    }

    private boolean hasElapsed(long now, long last, long delay) {
        return last == 0L || now - last >= delay;
    }

    private void sendUsePacket(Hand hand, boolean swing) {
        if (mc.player == null) {
            return;
        }

        Angle rotation = RotationController.INSTANCE.getRotation();
        float yaw = rotation != null ? rotation.getYaw() : mc.player.getYaw();
        float pitch = rotation != null ? rotation.getPitch() : mc.player.getPitch();
        Module.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(hand, sequence, yaw, pitch));
        if (swing) {
            mc.player.swingHand(hand);
        }
    }

    private void updateClientSelectedSlot(int slot) {
        if (mc.player == null || !isValidHotbarSlot(slot)) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void selectHotbarSlot(int slot) {
        if (!isValidHotbarSlot(slot)) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        Module.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
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

    private boolean isValidHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }
}
