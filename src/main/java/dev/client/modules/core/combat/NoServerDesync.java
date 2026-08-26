package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

public class NoServerDesync extends Module {
    public static NoServerDesync INSTANCE;

    public NoServerDesync() {
        super("NoServerDesync", ModuleCategory.Combat, "Предотвращает рассинхронизацию слотов и поворота камеры между клиентом и сервером");
    }

    float targetYaw;
    float targetPitch;
    boolean isPacketSent;

    @Subscribe
    private void onPacket(PacketEvent packetEvent) {
        if (mc.player == null || mc.world == null) return;

        if (packetEvent.getPacket() instanceof UpdateSelectedSlotS2CPacket(int slot)) {
            if (slot != mc.player.getInventory().getSelectedSlot()) {
                sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
                packetEvent.cancelled();
            }
        }

        if (packetEvent.isSend()) {
            if (this.isPacketSent) {
                if (packetEvent.getPacket() instanceof PlayerMoveC2SPacket oldPacket) {
                    PlayerMoveC2SPacket newPacket = new PlayerMoveC2SPacket.Full(
                            oldPacket.getX(mc.player.getX()),
                            oldPacket.getY(mc.player.getY()),
                            oldPacket.getZ(mc.player.getZ()),
                            this.targetYaw,
                            this.targetPitch,
                            oldPacket.isOnGround(),
                            oldPacket.horizontalCollision()
                    );
                    sendPacket(newPacket);
                    packetEvent.cancelled();
                    this.isPacketSent = false;
                }
            }
        }
    }

    public void sendRotationPacket(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.isPacketSent = true;
    }
}
