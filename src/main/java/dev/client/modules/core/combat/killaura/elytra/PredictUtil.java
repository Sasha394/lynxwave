package dev.client.modules.core.combat.killaura.elytra;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class PredictUtil implements ClientApi {
    public static Vec3d predict(LivingEntity target, double ticksAhead) {
        if (Math.hypot(target.lastX - target.getX(), target.lastZ - target.getZ()) * 20 <= 1 && (target.lastY - target.getY()) <= 1) return dev.client.api.compat.EntityCompat.pos(target).add(0, target.getHeight() / 2, 0);

        Vec3d forward = Vec3d.fromPolar(target.getPitch() + (target.getPitch() - target.lastPitch), target.getYaw() + (target.getYaw() - target.lastYaw)).multiply((new Vec3d(target.getX() - target.lastX, target.getY() - target.lastY, target.getZ() - target.lastZ).length() * ticksAhead));

        Vec3d vec3d = target.getRotationVector(target.getPitch() + (target.getPitch() - target.lastPitch), target.getYaw() + (target.getYaw() - target.lastYaw));
        float f = target.getPitch() * 0.017453292F;
        double d = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double e = forward.horizontalLength();
        boolean bl = target.getVelocity().y <= 0.0;
        double g = bl && target.hasStatusEffect(StatusEffects.SLOW_FALLING) ? Math.min(target.getFinalGravity(), 0.01) : target.getFinalGravity();
        double h = MathHelper.square(Math.cos(f));
        forward = forward.add(0.0, g * (-1.0 + h * 0.75), 0.0);
        double i;
        if (forward.y < 0.0 && d > 0.0) {
            i = forward.y * -0.1 * h;
            forward = forward.add(vec3d.x * i / d, i, vec3d.z * i / d);
        }

        if (f < 0.0F && d > 0.0) {
            i = e * (double) (-MathHelper.sin(f)) * 0.04;
            forward = forward.add(-vec3d.x * i / d, i * 2.2f, -vec3d.z * i / d);
        }

        if (d > 0.0) {
            forward = forward.add((vec3d.x / d * e - forward.x) * 0.1, 0.0, (vec3d.z / d * e - forward.z) * 0.1);
        }

        return dev.client.api.compat.EntityCompat.pos(target).add(forward);
    }
}
