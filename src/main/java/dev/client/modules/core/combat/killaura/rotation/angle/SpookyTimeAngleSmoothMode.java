package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class SpookyTimeAngleSmoothMode extends AngleSmoothMode {
    private static final float ROTATION_SPEED = 52.0F;
    private static final float EPSILON = 1.0E-3F;

    public SpookyTimeAngleSmoothMode() {
        super("SpookyTime");
    }

    public void reset() {
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        Angle delta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();

        int ticks = mc.player != null ? mc.player.age : 0;
        float sinOffset = (float) (Math.sin(ticks) * 3.0);
        float cosOffset = (float) (Math.cos(ticks) * 0.5);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float randYaw = (float) (random.nextDouble(-0.5, 0.5));
        float randPitch = (float) (random.nextDouble(-0.5, 0.5));

        yawDelta += sinOffset + randYaw;
        pitchDelta += cosOffset + randPitch;

        float length = (float) Math.hypot(yawDelta, pitchDelta);
        Angle moveAngle = new Angle(currentAngle.getYaw(), currentAngle.getPitch());

        if (length > EPSILON) {
            float step = Math.min(length, ROTATION_SPEED);
            float scale = step / length;

            float newYaw = currentAngle.getYaw() + yawDelta * scale;
            float newPitch = MathHelper.clamp(currentAngle.getPitch() + pitchDelta * scale, -89.0F, 90.0F);

            moveAngle.setYaw(newYaw);
            moveAngle.setPitch(newPitch);
        }

        return moveAngle;
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.1, 0.1, 0.1);
    }
}
