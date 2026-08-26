package dev.client.api.compat;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class EntityCompat {
    private EntityCompat() {
    }

    public static Vec3d pos(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    public static Vec3d pos(Camera camera) {
        return camera.getCameraPos();
    }
}
