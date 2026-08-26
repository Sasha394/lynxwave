package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.point.Vector;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;

public class ReallyWorldAngleSmoothMode extends AngleSmoothMode {
    public ReallyWorldAngleSmoothMode() {
        super("ReallyWorld");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        AttackHandler attackHandler = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler();
        KillAura attackAura = KillAura.INSTANCE;
        if (entity != null) {
            Vec3d aimPoint = Vector.brain(entity, 1, 3.5F);
            targetAngle = AngleUtil.calculateAngle(aimPoint);
        }
        int count = attackHandler.getCount();
        TimerUtil attackTimer = attackHandler.getAttackTimer();
        Angle angleDelta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw(), pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        boolean canAttack = attackAura.canAttack();

        float preAttackSpeed = 1;
        float postAttackSpeed = 1;
        float speed = canAttack ? preAttackSpeed : postAttackSpeed;
        float lineYaw = (Math.abs(yawDelta / rotationDifference) * 180);
        float linePitch = (Math.abs(pitchDelta / rotationDifference) * 180);
        float jitterYaw = canAttack ? 0 : (float) (-6 * Math.cos(System.currentTimeMillis() / 90D));
        float jitterPitch = canAttack ? 0 : (float) (6 * Math.sin(System.currentTimeMillis() / 90D));

        if (!attackAura.isEnabled() || entity == null) {
            speed = 1F;
            jitterYaw = 0;
            jitterPitch = 0;
        }

        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        Angle moveAngle = new Angle(currentAngle.getYaw(), currentAngle.getPitch());
        moveAngle.setYaw(MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentAngle.getYaw(), currentAngle.getYaw() + moveYaw) + jitterYaw);
        moveAngle.setPitch(MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentAngle.getPitch(), currentAngle.getPitch() + movePitch) + jitterPitch);

        if (count > 0 && count % 50 == 0 && !attackTimer.isFinished(200)) {
            moveAngle.setPitch(MathHelper.lerp(0.55F, currentAngle.getPitch(), currentAngle.getPitch() - 90) + jitterPitch);
        }

        return moveAngle;
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(new SecureRandom().nextFloat(), min, max);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
