package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class ElytraGlide extends Module {

    Slider ticks = new Slider("Тики", () -> true).set(1f, 300f, 1f).defaultValue(60f).register(this);

    public ElytraGlide() {
        super("ElytraGlide", ModuleCategory.Movement, "ElytraGlide");
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding() || isBoostedByFirework()) return;
        if (mc.player.isTouchingWater()) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        float yaw = mc.player.getYaw();
        double forward = 0.087;

        Vec3d vel = mc.player.getVelocity();
        double motion = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;

        if (motion >= ticks.getValue()) {
            forward = 0d;
        }

        double dx = -Math.sin(Math.toRadians(yaw)) * forward;
        double dz = Math.cos(Math.toRadians(yaw)) * forward;

        mc.player.setVelocity(dx * randomRange(2.5f, 2.71f), mc.player.getVelocity().y, dz * randomRange(2.5f, 2.71f));
        mc.player.setPosition(px + dx, py, pz + dz);
        mc.player.setVelocity(dx * randomRange(2.5f, 2.71f), mc.player.getVelocity().y, dz * randomRange(2.5f, 2.71f));
    }

    private boolean isBoostedByFirework() {
        if (mc.world == null || mc.player == null) return false;
        return !mc.world.getEntitiesByClass(FireworkRocketEntity.class, mc.player.getBoundingBox().expand(5.0),
                firework -> !firework.isRemoved() && mc.player.equals(firework.getOwner())).isEmpty();
    }

    private static double randomRange(float min, float max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
