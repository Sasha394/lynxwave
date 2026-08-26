package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

public class InventoryPlus extends Module {

    public InventoryPlus() {
        super("InventoryPlus", ModuleCategory.Player, "Позволяет класть предметы в слоты крафта");
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        if (event.getPacket() instanceof CloseHandledScreenC2SPacket) {
            event.cancelled();
        }
    }
}
