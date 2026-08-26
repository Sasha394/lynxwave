package dev.client.modules.core.combat.killaura.attack;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.other.UsingItemEvent;
import dev.client.api.nullcry.events.types.EventType;
import dev.client.api.nullcry.helper.entity.SimulatedPlayer;
import dev.client.api.nullcry.helper.entity.world.HandSwapHelper;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import dev.client.modules.core.combat.Criticals;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttackHandler implements ClientApi {
    public final TimerUtil attackTimer = new TimerUtil(), shieldWatch = new TimerUtil();
    private final ClickScheduler clickScheduler = new ClickScheduler();
    @Setter
    private int count = 0;

    void tick() {
    }

    void onPacket(PacketEvent e) {
        Packet<?> packet = e.getPacket();
        if (packet instanceof HandSwingC2SPacket || packet instanceof UpdateSelectedSlotC2SPacket) {
            clickScheduler.recalculate();
        }
    }

    void onUsingItem(UsingItemEvent e) {
        if (e.getType() == EventType.START && !shieldWatch.isFinished(50)) {
            e.cancelled();
        }
    }

    public boolean canAttack(AttackPerpetrator.AttackPerpetratorConfigurable config, int ticks) {
        for (int i = 0;i <= ticks;i++) {
            if (canCrit(config, i)) {
                return true;
            }
        }
        return false;
    }

    public boolean canCrit(AttackPerpetrator.AttackPerpetratorConfigurable config, int ticks) {
        if (!clickScheduler.isCooldownComplete(config.isUseDynamicCooldown(), ticks)) {
            return false;
        }

        SimulatedPlayer simulated = SimulatedPlayer.simulateLocalPlayer(ticks);

        if (config.isSmartCritical()) {
            if (simulated.onGround) {
                return attackTimer.isReached(500);
            }
            if (simulated.isFallFlying && Criticals.INSTANCE != null && Criticals.INSTANCE.isEnabled()) {
                return true;
            }
            if (simulated.touchingWater) {
                return attackTimer.isReached(500);
            }
            return isPlayerInCriticalState(simulated);
        }

        if (config.isOnlyCritical()) {
            if (simulated.isFallFlying && Criticals.INSTANCE != null && Criticals.INSTANCE.isEnabled()) {
                return true;
            }
            if (simulated.touchingWater) {
                return attackTimer.isReached(500);
            }
            return isPlayerInCriticalState(simulated);
        }

        return true;
    }

    private boolean hasMovementRestrictions(SimulatedPlayer simulated) {
        return simulated.hasStatusEffect(StatusEffects.BLINDNESS)
                || simulated.hasStatusEffect(StatusEffects.LEVITATION)
                || PlayerUtil.isBoxInBlock(simulated.boundingBox.expand(-1e-3), Blocks.COBWEB)
                || simulated.touchingWater
                || simulated.isInLava()
                || simulated.isClimbing()
                || simulated.isFallFlying
                || simulated.player.hasVehicle()
                || !PlayerUtil.canChangeIntoPose(EntityPose.STANDING, simulated.pos)
                || simulated.player.getAbilities().flying;
    }

    private boolean isPlayerInCriticalState(SimulatedPlayer simulated) {
        if (simulated.onGround || hasMovementRestrictions(simulated)) {
            return false;
        }

        if (simulated.fallDistance > 0.0f) {
            return true;
        }

        return Criticals.INSTANCE != null && Criticals.INSTANCE.isEnabled();
    }
}
