package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Tracks a target with a smooth, multi-harmonic sine trajectory. The wave fades
 * before an attack so the server-side rotation still converges on the hit point.
 */
public final class SuperSineAngleSmoothMode extends AngleSmoothMode {
    private static final double TWO_PI = Math.PI * 2.0D;

    private LivingEntity trackedTarget;
    private double phase;
    private float filteredYawWave;
    private float filteredPitchWave;

    public SuperSineAngleSmoothMode() {
        super("SuperSine");
    }

    public void reset() {
        trackedTarget = null;
        phase = 0.0D;
        filteredYawWave = 0.0F;
        filteredPitchWave = 0.0F;
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        if (mc.player == null || !(entity instanceof LivingEntity livingTarget)) {
            reset();
            return approach(currentAngle, targetAngle, 0.55F, 36.0F, 12.0F);
        }

        if (trackedTarget != livingTarget) {
            initialize(livingTarget);
        }

        float yawError = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float pitchError = targetAngle.getPitch() - currentAngle.getPitch();
        float angularError = (float) Math.hypot(yawError, pitchError);

        float cooldown = mc.player.getAttackCooldownProgress(0.5F);
        KillAura aura = KillAura.INSTANCE;
        boolean attackDelayFinished = aura == null || aura.getTicksToAttack() <= 1;
        float attackFocus = attackDelayFinished
                ? smoothStep(MathHelper.clamp((cooldown - 0.68F) / 0.30F, 0.0F, 1.0F))
                : 0.0F;

        double targetMotion = Math.min(1.0D, livingTarget.getVelocity().horizontalLength() * 2.5D);
        phase = wrapPhase(phase + 0.18D + targetMotion * 0.07D);

        float nearTarget = 1.0F - smoothStep(MathHelper.clamp((angularError - 4.0F) / 52.0F, 0.0F, 1.0F));
        float waveScale = nearTarget * (1.0F - attackFocus * 0.96F);
        float distanceScale = MathHelper.clamp((float) mc.player.distanceTo(livingTarget) / 6.0F, 0.25F, 1.0F);

        float rawYawWave = superSine(phase) * (0.9F + distanceScale * 0.85F) * waveScale;
        float rawPitchWave = superSine(phase * 0.79D + 1.73D) * (0.35F + distanceScale * 0.45F) * waveScale;

        float filter = 0.28F + attackFocus * 0.42F;
        filteredYawWave = MathHelper.lerp(filter, filteredYawWave, rawYawWave);
        filteredPitchWave = MathHelper.lerp(filter, filteredPitchWave, rawPitchWave);

        Angle waveTarget = new Angle(
                targetAngle.getYaw() + filteredYawWave,
                MathHelper.clamp(targetAngle.getPitch() + filteredPitchWave, -89.0F, 89.0F)
        );

        float pulse = 0.5F + superSine(phase * 0.43D + 0.91D) * 0.5F;
        float yawResponse = MathHelper.lerp(attackFocus, 0.30F + pulse * 0.16F, 0.92F);
        float pitchResponse = MathHelper.lerp(attackFocus, 0.24F + pulse * 0.12F, 0.84F);
        float maxYawStep = MathHelper.lerp(attackFocus, 22.0F, 48.0F);
        float maxPitchStep = MathHelper.lerp(attackFocus, 7.0F, 18.0F);

        return approach(currentAngle, waveTarget, yawResponse, pitchResponse, maxYawStep, maxPitchStep);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    private void initialize(LivingEntity target) {
        trackedTarget = target;
        phase = wrapPhase(target.getId() * 0.754877666D + mc.player.age * 0.173205081D);
        filteredYawWave = 0.0F;
        filteredPitchWave = 0.0F;
    }

    private Angle approach(Angle currentAngle, Angle targetAngle, float response, float maxYawStep, float maxPitchStep) {
        return approach(currentAngle, targetAngle, response, response, maxYawStep, maxPitchStep);
    }

    private Angle approach(
            Angle currentAngle,
            Angle targetAngle,
            float yawResponse,
            float pitchResponse,
            float maxYawStep,
            float maxPitchStep
    ) {
        Angle delta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawStep = MathHelper.clamp(delta.getYaw() * yawResponse, -maxYawStep, maxYawStep);
        float pitchStep = MathHelper.clamp(delta.getPitch() * pitchResponse, -maxPitchStep, maxPitchStep);

        return new Angle(
                currentAngle.getYaw() + yawStep,
                MathHelper.clamp(currentAngle.getPitch() + pitchStep, -89.0F, 89.0F)
        );
    }

    private static float superSine(double value) {
        double wave = Math.sin(value)
                + Math.sin(value * 2.07D + 1.21D) * 0.48D
                + Math.sin(value * 3.91D - 0.64D) * 0.22D
                + Math.sin(value * 7.13D + 2.08D) * 0.08D;
        return (float) (wave / 1.78D);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static double wrapPhase(double value) {
        value %= TWO_PI;
        return value < 0.0D ? value + TWO_PI : value;
    }
}
