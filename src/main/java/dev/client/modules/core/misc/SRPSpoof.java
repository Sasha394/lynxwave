package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;

public class SRPSpoof extends Module {

    public SRPSpoof() {
        super("SRPSpoof", ModuleCategory.Misc, "Пропускает установку серверного ресурс-пака");
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof ResourcePackStatusC2SPacket resourcePackStatusC2SPacket) {
            event.setCancelled(true);

            ResourcePackStatusC2SPacket fakePacket = new ResourcePackStatusC2SPacket(resourcePackStatusC2SPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
            mc.getNetworkHandler().sendPacket(fakePacket);
        }
    }
}
