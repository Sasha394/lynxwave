package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.modules.core.combat.killaura.rotation.Angle;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Режим ротации для элитры — без сглаживания, сразу целевой угол.
 */
public class ElytraOptimizedMode extends AngleSmoothMode {

    public ElytraOptimizedMode() {
        super("Elytra");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        return targetAngle;
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
