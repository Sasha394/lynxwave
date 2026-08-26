package dev.client.api.nullcry.helper.entity.world;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import lombok.Setter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

@Setter
public class HandSwapHelper implements ClientApi {
    public static boolean isEnabled;
    private boolean isChangingItem;
    private int originalSlot = -1;

    @Subscribe
    public void onPacket(PacketEvent packetEvent) {
        if (packetEvent.isSend()) {
            return;
        }
        if (packetEvent.getPacket() instanceof UpdateSelectedSlotS2CPacket) {
            this.isChangingItem = true;
        }
    }

    public void handleItemChange(boolean resetItem) {
        if (this.isChangingItem && this.originalSlot != -1) {
            isEnabled = true;
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            if (resetItem) {
                this.isChangingItem = false;
                this.originalSlot = -1;
                isEnabled = false;
            }
        }
    }

    public static void swapHand(Slot slot, Hand hand, boolean packet) {
        if (slot != null) swapHand(slot.id, hand, packet);
    }

    public static void closeScreen(boolean packet) {
        if (packet) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        else mc.player.closeHandledScreen();
    }

    public static void swapHand(int slotId, Hand hand, boolean packet) {
        if (slotId == -1 || !PlayerInventoryComponent.script.isFinished()) return;
        int button = hand.equals(Hand.MAIN_HAND) ? mc.player.getInventory().getSelectedSlot() : 40;

        if (MovingUtil.isMoving()) PlayerInventoryComponent.addTask(() -> InventoryHelper.clickSlotId(slotId, button, SlotActionType.SWAP, packet));
        else InventoryHelper.clickSlotId(slotId, button, SlotActionType.SWAP, packet);
    }
}
