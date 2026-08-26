package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.EntityPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public class Flight extends Module {

    public Flight() {
        super("Flight", ModuleCategory.Movement, "Позволяет летать без gm 1");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Vanilla", "Matrix Jump", "Matrix Glide", "PolarFlyX").defaultValue("Matrix").register(this);
    Slider horizontal = new Slider("Скорость по горизонтали", () -> !mode.isSelected("PolarFlyX")).set(0f, 5f, 0.1f).defaultValue(0.5f).register(this);
    Slider vertical = new Slider("Скорость по вертикали", () -> !mode.isSelected("PolarFlyX")).set(0f, 5f, 0.1f).defaultValue(0.5f).register(this);

    private static final double POLAR_HORIZONTAL = 6.8;
    private static final double POLAR_MANUAL_VERTICAL = 8.25;
    private static final double POLAR_CYCLE_VERTICAL = 0.10;
    private boolean polarGoingUp = true;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || FreeCamera.INSTANCE.isEnabled()) return;

        switch (mode.getValue()) {
            case "Vanilla" -> {
                updatePlayerMotion();
            }

            case "Matrix Jump" -> {
                if (mc.player.isOnGround())
                    mc.player.jump();
                else {
                    MovingUtil.setVelocity(Math.min(horizontal.getValue(), 1.97f));
                    mc.player.setVelocity(mc.player.getVelocity().x, vertical.getValue(), mc.player.getVelocity().z);
                }
            }

            case "Matrix Glide" -> {
                mc.player.velocity = Vec3d.ZERO;
                MovingUtil.setVelocity(horizontal.getValue());
                mc.player.setVelocity(mc.player.getVelocity().x, -0.003, mc.player.getVelocity().z);
            }

            case "PolarFlyX" -> {
                double yaw = Math.toRadians(mc.player.getYaw());
                double motionX = 0.0;
                double motionZ = 0.0;
                double motionY;

                if (mc.options.forwardKey.isPressed()) {
                    motionX -= Math.sin(yaw) * POLAR_HORIZONTAL;
                    motionZ += Math.cos(yaw) * POLAR_HORIZONTAL;
                }
                if (mc.options.backKey.isPressed()) {
                    motionX += Math.sin(yaw) * POLAR_HORIZONTAL;
                    motionZ -= Math.cos(yaw) * POLAR_HORIZONTAL;
                }
                if (mc.options.leftKey.isPressed()) {
                    motionX -= Math.cos(yaw) * POLAR_HORIZONTAL;
                    motionZ -= Math.sin(yaw) * POLAR_HORIZONTAL;
                }
                if (mc.options.rightKey.isPressed()) {
                    motionX += Math.cos(yaw) * POLAR_HORIZONTAL;
                    motionZ += Math.sin(yaw) * POLAR_HORIZONTAL;
                }

                if (mc.options.jumpKey.isPressed()) {
                    motionY = POLAR_MANUAL_VERTICAL;
                } else if (mc.options.sneakKey.isPressed()) {
                    motionY = -1.4;
                } else {
                    motionY = polarGoingUp ? POLAR_CYCLE_VERTICAL : -POLAR_CYCLE_VERTICAL;
                    if (mc.player.age % 2 == 0) {
                        polarGoingUp = !polarGoingUp;
                    }
                }

                mc.player.fallDistance = 0f;
                mc.player.setVelocity(motionX, motionY, motionZ);
            }

            case "Elytra Fly" -> {
                if (mc.player.age % 2 != 0) return;
                int slot = -1;

                for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
                    if (stack.getItem() == Items.ELYTRA) {
                        slot = mc.player.getInventory().getMainStacks().indexOf(stack);
                    }
                }

                mc.player.abilities.flying = false;

                if (slot == -1) return;
                int chestSlot = 6;

                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, chestSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.abilities.flying = true;
                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, chestSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null || FreeCamera.INSTANCE.isEnabled()) return;

        if (mode.getValue().equals("Matrix Jump")) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket playerPositionLookS2CPacket) {
                if (mc.player == null) {
                    toggle();
                    return;
                }

                EntityPosition pos = playerPositionLookS2CPacket.change();
                Set<PositionFlag> flags = playerPositionLookS2CPacket.relatives();

                double x = flags.contains(PositionFlag.X) ? mc.player.getX() + pos.position().x : pos.position().x;
                double y = flags.contains(PositionFlag.Y) ? mc.player.getY() + pos.position().y : pos.position().y;
                double z = flags.contains(PositionFlag.Z) ? mc.player.getZ() + pos.position().z : pos.position().z;

                float yaw = flags.contains(PositionFlag.Y_ROT) ? mc.player.getYaw() + pos.yaw() : pos.yaw();
                float pitch = flags.contains(PositionFlag.X_ROT) ? mc.player.getPitch() + pos.pitch() : pos.pitch();

                mc.player.setPosition(x, y, z);
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);

                event.cancelled();
                toggle();
            }
        }
    }

    private void updatePlayerMotion() {
        double motionY = mc.options.sneakKey.isPressed() ? -vertical.getValue() : mc.options.jumpKey.isPressed() ? vertical.getValue() : 0;
        MovingUtil.setVelocity(horizontal.getValue());
        mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        polarGoingUp = true;
        if (mode.isSelected("PolarFlyX") && mc.player != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            );
            mc.player.setVelocity(mc.player.getVelocity().x, 0.03, mc.player.getVelocity().z);
            mc.player.fallDistance = 0f;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        if (mc.player == null) return;
        mc.player.abilities.flying = false;
        if (mode.isSelected("PolarFlyX")) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        }
    }
}
