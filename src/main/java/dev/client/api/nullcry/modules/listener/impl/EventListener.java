package dev.client.api.nullcry.modules.listener.impl;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.other.UsingItemEvent;
import dev.client.api.nullcry.modules.listener.Listener;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class EventListener implements Listener {
    public static boolean serverSprint;
    public static int selectedSlot;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        LynxWave.getInstance().getAttackPerpetrator().tick();
        PlayerInventoryComponent.tick();
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        switch (e.getPacket()) {
            case ClientCommandC2SPacket command -> serverSprint = switch (command.getMode()) {
                case ClientCommandC2SPacket.Mode.START_SPRINTING -> true;
                case ClientCommandC2SPacket.Mode.STOP_SPRINTING -> false;
                default -> serverSprint;
            };
            case UpdateSelectedSlotC2SPacket slot -> selectedSlot = slot.getSelectedSlot();
            default -> {}
        }

        LynxWave.getInstance().getAttackPerpetrator().onPacket(e);
    }

    @Subscribe
    public void onUsingItemEvent(UsingItemEvent event) {
        LynxWave.getInstance().getAttackPerpetrator().onUsingItem(event);
    }
}
