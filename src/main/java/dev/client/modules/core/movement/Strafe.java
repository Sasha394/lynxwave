package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.MovementEvent;
import dev.client.api.nullcry.events.core.input.PostMovementEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.player.PlayerDamageReceivedEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.helper.entity.PlayerDamageUtil;
import dev.client.api.nullcry.helper.entity.PlayerSpeedUtil;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Random;

public class Strafe extends Module {

    public Strafe() {
        super("Strafe", ModuleCategory.Movement, "Позволяет стрейфить по нажатию клавиш: W,A,S,D");
    }

    CheckBox elytra = new CheckBox("Буст с элитрой", () -> true).defaultValue(false).register(this);
    Slider setSpeed = new Slider("Скорость", () -> elytra.getEnabled()).set(0.5f, 2.5f, 0.1f).defaultValue(1.5f).register(this);
    CheckBox damageBoost = new CheckBox("Буст с дамагом", () -> true).defaultValue(false).register(this);
    CheckBox autoJump = new CheckBox("Прыгать", () -> true).defaultValue(false).register(this);

    public final PlayerDamageUtil playerDamageUtil = new PlayerDamageUtil();
    public final PlayerSpeedUtil playerSpeedUtil = new PlayerSpeedUtil();
    int waterTicks;

    @Subscribe
    private void onMovement(MovementEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        int elytraSlot = InventoryHelper.getElytraHotbarSlot();
        if (elytra.getEnabled() && elytraSlot != -1) {
            if (MovingUtil.isMoving() && !mc.player.onGround && mc.player.fallDistance >= 0.15 && event.isToGround()) {
                MovingUtil.setVelocity(setSpeed.getValue());
                playerSpeedUtil.setOldSpeed(setSpeed.getIncrements() / 1.06);
            }
        }

        if (mc.player.isTouchingWater() || mc.player.isInLava()) {
            waterTicks = 10;
        } else {
            waterTicks--;
        }

            if (waterTicks > 0) return;
            if (MovingUtil.isMoving() && MovingUtil.getVelocity() <= 0.289385188) {
                if (!event.isToGround()) {
                    MovingUtil.setStrafe(MovingUtil.reason(false) || mc.player.isUsingItem() ? MovingUtil.getVelocity() - 0.00001f : 0.245f - (new Random().nextFloat() * 0.000001f));
            }
        }
    }

    @Subscribe
    private void onPostMovement(PostMovementEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        playerSpeedUtil.postMove(event.getHorizontalMove());
    }

    @Subscribe
    private void onPacket(PacketEvent packetEvent) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        if (packetEvent.getType() == PacketEvent.Type.RECEIVE) {
            if (damageBoost.getEnabled()) {
                playerDamageUtil.onPacket(packetEvent);
            }

            if (packetEvent.getPacket() instanceof PlayerPositionLookS2CPacket) {
                playerSpeedUtil.setOldSpeed(0);
            }
        }
    }

    @Subscribe
    private void onPlayerDamage(PlayerDamageReceivedEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        if (damageBoost.getEnabled()) {
            playerDamageUtil.processDamage(event);
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        if (mc.player == null || mc.interactionManager == null) return;

        if (autoJump.getEnabled()) {
            if (mc.player.isOnGround() && !mc.player.isTouchingWater() && !mc.player.isInLava()) {
                mc.player.jump();
            }
        }

        if (!elytra.getEnabled()) return;
        int elytra = InventoryHelper.getElytraHotbarSlot();

        if (mc.player.isTouchingWater() || mc.player.isInLava() || waterTicks > 0 || elytra == -1) return;
        if (mc.player.fallDistance != 0 && mc.player.fallDistance < 0.1 && mc.player.getVelocity().y < -0.1) {
            if (elytra != -2) {
                mc.interactionManager.clickSlot(0, elytra, 1, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
            }
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            if (elytra != -2) {
                mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, elytra, 1, SlotActionType.PICKUP, mc.player);
            }
        }
    }

    @Override
    public void onEnabled() {
        playerSpeedUtil.setOldSpeed(0);
        super.onEnabled();
    }
}