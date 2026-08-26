package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.attack.AttackPerpetrator;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;

public class MatrixSmoothMode extends AngleSmoothMode {

    public MatrixSmoothMode() {
        super("Matrix");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        AttackHandler attackHandler = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler();
        Angle angleDelta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        KillAura aura = KillAura.INSTANCE;
        float yawDelta = angleDelta.getYaw(), pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        boolean canAttack = aura.canAttack();

        float yaw = canAttack ? 0 : (float) (randomLerp(4, 6) * Math.sin(System.currentTimeMillis() / 40D));
        float pitch = canAttack ? 0 : (float) (randomLerp(2, 3) * Math.cos(System.currentTimeMillis() / 40D));

        float speed = canAttack ? 1 : mc.player.age % 2 == 0 ? new SecureRandom().nextBoolean() ? 0.5F : 0.3F : 0;
        float lineYaw = (Math.abs(yawDelta / rotationDifference) * 180);
        float linePitch = (Math.abs(pitchDelta / rotationDifference) * 180);

        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);

        Angle moveAngle = new Angle(currentAngle.getYaw(), currentAngle.getPitch());
        moveAngle.setYaw(MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentAngle.getYaw(), currentAngle.getYaw() + moveYaw) + yaw);
        moveAngle.setPitch(MathHelper.lerp(randomLerp(speed, speed + 0.2F), currentAngle.getPitch(), currentAngle.getPitch() + movePitch) + pitch);
        return moveAngle;
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(new SecureRandom().nextFloat(), min, max);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.1, 0.1, 0.1);
    }
}