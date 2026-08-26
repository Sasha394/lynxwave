package dev.client.api.nullcry.helper.math;

import dev.client.api.nullcry.ClientApi;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MathVector implements ClientApi {

    public static float rotationDifference(Entity entity) {
        if (mc.player == null || entity == null) return 0;

        double x = interpolate(entity.lastX, dev.client.api.compat.EntityCompat.pos(entity).x) - interpolate(mc.player.lastX, dev.client.api.compat.EntityCompat.pos(mc.player).x);
        double z = interpolate(entity.lastZ, dev.client.api.compat.EntityCompat.pos(entity).z) - interpolate(mc.player.lastZ, dev.client.api.compat.EntityCompat.pos(mc.player).z);
        return (float) -(Math.atan2(x, z) * (180 / Math.PI));
    }

    public static Vec3d lerpPosition(Entity entity) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        return new Vec3d(
            entity.lastX + (entity.getX() - entity.lastX) * tickDelta,
            entity.lastY + (entity.getY() - entity.lastY) * tickDelta,
            entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta
        );
    }

    public static double interpolate(double d, double d2) {
        return d + (d2 - d) * (double) mc.getRenderTickCounter().getTickProgress(true);
    }

    public static double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float distanceTo(Vec3d from, Vec3d to) {
        float f = (float) (from.getX() - to.getX());
        float g = (float) (from.getY() - to.getY());
        float h = (float) (from.getZ() - to.getZ());
        return MathHelper.sqrt(f * f + g * g + h * h);
    }

    public static Vec3d calculatePositionDelta(Vec3d from, Vec3d to) {
        return to.subtract(from);
    }
}
