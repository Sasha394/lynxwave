package dev.client.modules.core.combat.killaura.point;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public final class Vector {
    private Vector() {
    }

    public static Vec3d brain(Entity entity, int a, float b) {
        if (entity == null) {
            return Vec3d.ZERO;
        } else if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            return dev.client.api.compat.EntityCompat.pos(living).add(0.0, living.getHeight() * 0.55, 0.0);
        } else {
            return entity.getBoundingBox().getCenter();
        }
    }
}
