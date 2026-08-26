package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;

public class HolyWorldAngle extends AngleSmoothMode {

    public HolyWorldAngle() {
        super("HolyWorld");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        KillAura aura = KillAura.INSTANCE;
        if (aura == null || !aura.isEnabled()) {
            if (mc.player != null) return new Angle(mc.player.getYaw(), mc.player.getPitch());
            return currentAngle;
        }

        if (entity == null) {
            if (mc.player != null) return new Angle(mc.player.getYaw(), mc.player.getPitch());
            return currentAngle;
        }

        AttackHandler attackHandler = LynxWave.getInstance()
                .getAttackPerpetrator()
                .getAttackHandler();

        Vec3d aimPoint = brain(entity, 1, 3.5F);
        targetAngle = AngleUtil.calculateAngle(aimPoint);

        if (mc.player != null && aura.aimMode.isSelected("HolyWorld")) {
            float playerYaw = mc.player.getYaw();
            float targetYaw = targetAngle.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
            float snap = aura.snapFov.getValue();
            float limit = MathHelper.clamp(snap * 0.5f, 30f, 90f);

            if (Math.abs(yawDiff) > limit) {
                return currentAngle;
            }
        }

        Angle delta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();

        float rotationDiff = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (rotationDiff < 1.0E-3f) {
            return currentAngle;
        }

        boolean canAttack = aura.canAttack();

        float preAttackSpeed = 0.6f;
        float postAttackSpeed = 0.6f;
        float speed = canAttack ? preAttackSpeed : postAttackSpeed;

        float lineYaw = Math.abs(yawDelta / rotationDiff) * 180.0f;
        float linePitch = Math.abs(pitchDelta / rotationDiff) * 180.0f;

        float jitterYaw = 0f;
        float jitterPitch = 0f;

        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);

        Angle out = new Angle(currentAngle.getYaw(), currentAngle.getPitch());

        float lerpFactor = randomLerp(speed - 0.2F, speed);
        out.setYaw(MathHelper.lerp(lerpFactor,
                currentAngle.getYaw(),
                currentAngle.getYaw() + moveYaw) + jitterYaw);

        out.setPitch(MathHelper.lerp(lerpFactor,
                currentAngle.getPitch(),
                currentAngle.getPitch() + movePitch) + jitterPitch);

        return out;
    }

    public static Vec3d brain(Entity entity, float min, float max) {
        double distance = dev.client.api.compat.EntityCompat.pos(mc.player).distanceTo(dev.client.api.compat.EntityCompat.pos(entity));
        double normalizedDistance = MathHelper.clamp((distance - min) / (max - min), 0, 1);
        double heightFactor = normalizedDistance;

        double minHeight = 0.2;
        double maxHeight = 0.8;
        double targetHeight = minHeight + (maxHeight - minHeight) * heightFactor;

        double targetY = entity.getY() + (entity.getHeight() * targetHeight);

        return new Vec3d(
                entity.getX(),
                targetY,
                entity.getZ()
        );
    }

    private float randomLerp(float min, float max) {
        float t = new SecureRandom().nextFloat();
        return MathHelper.lerp(t, min, max);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.1, 0.1, 0.1);
    }
}
