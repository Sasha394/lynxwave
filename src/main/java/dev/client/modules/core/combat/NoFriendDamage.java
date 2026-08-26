package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public class NoFriendDamage extends Module {

    public NoFriendDamage() {
        super("NoFriendDamage", ModuleCategory.Combat, "Отменяет урон по друзьям");
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket playerInteractEntityC2SPacket) {
            Entity entity = mc.world.getEntityById(playerInteractEntityC2SPacket.entityId);

            if (entity instanceof PlayerEntity player) {
                if (handlerClient().getFriendManager().isFriend(player.getName().getString()) && playerInteractEntityC2SPacket.type.getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
                    event.cancelled();
                }
            }
        }
    }
}
