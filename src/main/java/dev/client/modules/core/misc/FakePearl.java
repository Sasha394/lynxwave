package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class FakePearl extends Module {
    public static FakePearl INSTANCE;

    private final KeyBind throwKey = new KeyBind("Клавиша броска", () -> true).set(-1).register(this);
    private final CheckBox swing = new CheckBox("Анимация руки", () -> true).defaultValue(true).register(this);
    private final CheckBox requirePearl = new CheckBox("Нужен жемчуг в руке", () -> true).defaultValue(true).register(this);
    private final Slider cooldown = new Slider("Задержка", () -> true).set(50f, 2000f, 50f).defaultValue(200f).register(this);

    private final TimerUtil timer = new TimerUtil();

    public FakePearl() {
        super("FakePearl", ModuleCategory.Misc, "Визуально кидает эндер-жемчуг без телепортации");
        INSTANCE = this;
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (throwKey.getKey() == null || throwKey.getKey() == -1) return;
        if (!event.isKeyDown(throwKey.getKey())) return;
        if (!timer.isReached((long) cooldown.getValue().floatValue())) return;

        if (requirePearl.getEnabled() && !hasPearl()) return;

        throwFake();
        timer.reset();
    }

    private boolean hasPearl() {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return true;
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) return true;
        return false;
    }

    private void throwFake() {
        Hand hand = mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL
                ? Hand.MAIN_HAND : Hand.OFF_HAND;

        if (swing.getEnabled()) {
            mc.player.swingHand(hand);
            sendPacketSilent(new HandSwingC2SPacket(hand));
        }

        ItemStack stack = requirePearl.getEnabled()
                ? mc.player.getStackInHand(hand).copy()
                : new ItemStack(Items.ENDER_PEARL);

        EnderPearlEntity ghost = new EnderPearlEntity(mc.world, mc.player, stack);
        Vec3d eye = mc.player.getEyePos();
        ghost.setPosition(eye.x, eye.y, eye.z);

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        float velocity = 1.5f;
        double vx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double vy = -Math.sin(Math.toRadians(pitch));
        double vz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length > 0) {
            vx = vx / length * velocity;
            vy = vy / length * velocity;
            vz = vz / length * velocity;
        }
        Vec3d playerVel = mc.player.getVelocity();
        ghost.setVelocity(vx + playerVel.x, vy + (mc.player.isOnGround() ? 0 : playerVel.y), vz + playerVel.z);

        mc.world.addEntity(ghost);
    }
}
