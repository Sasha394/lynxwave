package dev.client.component.core.inventory;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.component.Component;
import dev.client.component.core.Instance;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

public class HandComponent extends Component {
    public static boolean isEnabled;
    private boolean changingItem;
    private int currentSlot = -1;
    private final TimerUtil time = new TimerUtil();

    @Subscribe
    public void onPacket(PacketEvent packetEvent) {
        if (packetEvent.isSend()) {
            return;
        }

        final Packet<?> packet = packetEvent.getPacket();
        if (packet instanceof UpdateSelectedSlotS2CPacket) {
            this.changingItem = true;
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (this.changingItem && this.currentSlot != -1) {
            isEnabled = true;
            mc.player.getInventory().setSelectedSlot(this.currentSlot);
            if (time.isFinished(200)) {
                this.changingItem = false;
                this.currentSlot = -1;
                isEnabled = false;
            }
        }
    }

    public static void reset() {
        Instance.getComponent(HandComponent.class).time.reset();
    }

    public static void setCurrentSlot(int slot) {
        Instance.getComponent(HandComponent.class).currentSlot = slot;
    }
}