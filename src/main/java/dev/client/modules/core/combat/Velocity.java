package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Velocity extends Module {

    public Velocity() {
        super("Velocity", ModuleCategory.Combat, "Убирает отталкивание от игроков");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Matrix", "Cancel", "Grim Skip", "Grim Cancel", "FunTime").defaultValue("Matrix").register(this);

    boolean cancel;
    boolean damaged;
    int skip = 0;
    int packetCounter = 0;

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.isReceive()) {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket entityVelocityUpdateS2CPacket && entityVelocityUpdateS2CPacket.getEntityId() != mc.player.getId()) return;

            if (mode.isSelected("Matrix")) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
                    event.cancelled();
                }
                if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                    skip = 0;
                    cancel = false;
                }
            }

            if (mode.isSelected("Cancel")) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
                    event.cancelled();
                }
            }

            if (mode.isSelected("Grim Skip")) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
                    skip = 6;
                    event.cancelled();
                }

                if (event.getPacket() instanceof PlayerMoveC2SPacket) {
                    if (skip > 0) {
                        skip--;
                        event.cancelled();
                    }
                }
            }

            if (mode.isSelected("Grim Cancel")) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
                    event.cancelled();
                    cancel = true;
                }
                if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                    skip = 3;
                }

                if (event.getPacket() instanceof PlayerMoveC2SPacket) {
                    skip--;
                    if (cancel) {
                        if (skip <= 0) {
                            BlockPos blockPos = new BlockPos((int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
                            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
                            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
                        }
                        cancel = false;
                    }
                }
            }

            if (mode.isSelected("FunTime")) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket p) {
                    if (skip >= 2) {
                        return;
                    }
                    if (p.getEntityId() != mc.player.getId()) {
                        return;
                    }
                    event.cancelled();
                    damaged = true;
                }
                if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                    skip = 3;
                }
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mode.isSelected("FunTime") && damaged) {
            packetCounter++;

            if (packetCounter >= 5) {
                double posX = mc.player.getX();
                double posY = mc.player.getY();
                double posZ = mc.player.getZ();

                sendPacket(new PlayerMoveC2SPacket.Full(posX, posY, posZ, mc.player.getYaw(), mc.player.getPitch(), true, false));
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY + 0.05, posZ, true, false));
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY, posZ, true, false));
                sendPacket(new PlayerMoveC2SPacket.Full(posX, posY, posZ, mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));

                packetCounter = 0;
                damaged = false;
            }
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        skip = 0;
        cancel = false;
        damaged = false;
        packetCounter = 0;
    }
}