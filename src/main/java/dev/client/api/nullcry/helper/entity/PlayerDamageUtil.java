package dev.client.api.nullcry.helper.entity;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.player.PlayerDamageReceivedEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import lombok.Getter;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

public class PlayerDamageUtil implements ClientApi {
    private final TimerUtil timeTracker = new TimerUtil();
    @Getter
    private boolean normalDamage;
    private boolean fallDamage;
    private boolean explosionDamage;
    private boolean arrowDamage;
    private boolean pearlDamage;

    public void onPacket(PacketEvent packetEvent) {
        boolean isDamage = this.fallDamage || this.arrowDamage || this.explosionDamage || this.pearlDamage;

        if (this.isBadEffects()) {
            return;
        }
        if (packetEvent.getPacket() instanceof ExplosionS2CPacket) {
            this.explosionDamage = true;
        }
        if (!isDamage) {
            Packet<?> packet = packetEvent.getPacket();
            if (packet instanceof EntityStatusS2CPacket statusPacket) {
                if (statusPacket.getStatus() == 2 && statusPacket.getEntity(mc.world) == ClientApi.mc.player) {
                    this.normalDamage = true;
                }
            }
        } else if (mc.player.hurtTime > 0) {
            this.normalDamage = false;
            this.reset();
        }
    }

    public boolean time(long time) {
        if (this.normalDamage) {
            if (this.timeTracker.isReached(time)) {
                this.normalDamage = false;
                this.timeTracker.reset();
                return true;
            }
        } else {
            this.timeTracker.reset();
        }
        return false;
    }

    public void processDamage(PlayerDamageReceivedEvent playerDamageReceivedEvent) {
        switch (playerDamageReceivedEvent.getDamageType()) {
            case FALL -> this.fallDamage = true;
            case ARROW -> this.arrowDamage = true;
            case ENDER_PEARL -> this.pearlDamage = true;
        }
        this.normalDamage = false;
    }

    private void reset() {
        this.fallDamage = false;
        this.explosionDamage = false;
        this.arrowDamage = false;
        this.pearlDamage = false;
    }

    private boolean isBadEffects() {
        if (mc.player == null) {
            return false;
        }
        return mc.player.hasStatusEffect(StatusEffects.POISON) || mc.player.hasStatusEffect(StatusEffects.WITHER) || mc.player.hasStatusEffect(StatusEffects.INSTANT_DAMAGE);
    }
}