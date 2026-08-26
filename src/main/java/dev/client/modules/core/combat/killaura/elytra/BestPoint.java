package dev.client.modules.core.combat.killaura.elytra;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@UtilityClass
public class BestPoint implements ClientApi {
    public Vec3d getNearestPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double step = 0.1;
        Vec3d bestVec = null;
        double closestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    double dist = mc.player.getEyePos().distanceTo(sample);
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        bestVec = sample;
                    }
                }
            }
        }
        return bestVec;
    }

    public Vec3d getNearestVisiblePoint(Entity target, Vec3d preferredPoint, double range) {
        if (preferredPoint == null || mc.player == null || mc.world == null) {
            return preferredPoint;
        }

        if (isPointVisible(target, preferredPoint, range)) {
            return preferredPoint;
        }

        Box box = target.getBoundingBox();
        double step = 0.12;
        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    if (!isPointVisible(target, sample, range)) {
                        continue;
                    }

                    double distanceToCurrent = sample.squaredDistanceTo(preferredPoint);
                    if (distanceToCurrent < bestDistance) {
                        bestDistance = distanceToCurrent;
                        bestPoint = sample;
                    }
                }
            }
        }

        return bestPoint != null ? bestPoint : preferredPoint;
    }

    private boolean isPointVisible(Entity target, Vec3d point, double range) {
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(point);
        if (distance > range) {
            return false;
        }

        Vec3d direction = point.subtract(eyePos).normalize();
        if (!RaytraceUtil.rayTrace(direction, distance + 0.2, target.getBoundingBox())) {
            return false;
        }

        var blockHit = RaytraceUtil.rayCast(eyePos, point, RaycastContext.ShapeType.COLLIDER, mc.player);
        return blockHit.getType() == HitResult.Type.MISS || eyePos.squaredDistanceTo(blockHit.getPos()) >= eyePos.squaredDistanceTo(point) - 1e-4;
    }
}
