package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.other.CameraClipEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.events.core.world.WorldChangeEvent;
import dev.client.api.nullcry.events.core.world.WorldLoadEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.builders.Builder;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class FreeCamera extends Module {
    public static FreeCamera INSTANCE;

    public FreeCamera() {
        super("FreeCamera", ModuleCategory.Movement, "Свободное перемещение камеры, отдельно от персонажа");
    }

    Slider speedXZ = new Slider("Скорость по XZ", () -> true).set(0.1f, 5, 0.5f).defaultValue(0.5f).register(this);
    Slider speedY = new Slider("Скорость по Y", () -> true).set(0.1f, 5, 0.5f).defaultValue(0.5f).register(this);

    CheckBox display = new CheckBox("Рендерить координаты", () -> true).defaultValue(true).register(this);

    private OtherClientPlayerEntity fakePlayer;
    public GameMode prev;
    float x, y, z;

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D draw2D) {
        if (display.getEnabled()) {
            BlockPos diff = mc.player.getBlockPos().subtract(new BlockPos((int) x, (int) y, (int) z));
            String pos = "X: " + diff.getX() + " Y: " + diff.getY() + " Z: " + diff.getZ();

            float screenWidth = mc.getWindow().getScaledWidth();
            float screenHeight = mc.getWindow().getScaledHeight();
            float textWidth = Builder.INTER.get().getWidth(pos, 9);

            Builder.TEXT_BUILDER
                    .text(pos)
                    .size(9)
                    .font(Builder.INTER.get())
                    .color(-1)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(draw2D.getContext()), screenWidth / 2F - textWidth / 2F, screenHeight / 2F + 10);
        }
    }

    @Subscribe
    public void onCameraDistance(CameraClipEvent event) {
        if (mc.world != null) event.cancelled();
    }

    @Subscribe
    public void onWorldChange(WorldChangeEvent event) {
        if (mc.world != null) toggle();
    }

    @Subscribe
    public void onWorldLoad(WorldLoadEvent event) {
        if (mc.world != null) toggle();
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (!mc.player.isSneaking() && mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, this.speedY.getValue(), mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -this.speedY.getValue(), mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(0.0, 0.0, 0.0);
        }

        MovingUtil.setVelocity(this.speedXZ.getValue());
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.isSend()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof PlayerMoveC2SPacket || packet instanceof UpdatePlayerAbilitiesC2SPacket)
                event.cancelled();
        }

        if (event.isReceive()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof PlayerPositionLookS2CPacket) {
                event.cancelled();
            }
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        this.prev = mc.interactionManager.getCurrentGameMode();
        mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()).setGameMode(GameMode.SPECTATOR);
        this.x = (float) mc.player.getX();
        this.y = (float) mc.player.getY();
        this.z = (float) mc.player.getZ();
        if (fakePlayer != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }

        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), mc.getSession().getUsername()));
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            fakePlayer.getInventory().setStack(i, mc.player.getInventory().getStack(i).copy());
        }
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.updatePositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());
        fakePlayer.headYaw = mc.player.headYaw;

        fakePlayer.setId(-1337);
        mc.world.addEntity(fakePlayer);
        mc.player.abilities.flying = true;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        if (prev == null || mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()) == null) return;
        mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()).setGameMode(this.prev);
        mc.player.setVelocity(0.0, 0.0, 0.0);
        mc.player.setVelocity(0.0, 0.0, 0.0);
        mc.player.setPosition(this.x, this.y, this.z);
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 5.941588215E-315, mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
        if (fakePlayer != null) {
            fakePlayer.setNoGravity(false);
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        } else {
            mc.world.removeEntity(-1337, Entity.RemovalReason.DISCARDED);
        }
        mc.player.abilities.flying = false;
    }

    public PlayerEntity getClonePlayer() {
        return fakePlayer;
    }
}

