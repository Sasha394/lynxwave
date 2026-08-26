package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateful SpookyTime rotation adapted to LynxWave's 1.21.11 rotation pipeline.
 *
 * <p>Sensitivity/GCD correction is intentionally left to
 * {@link dev.client.modules.core.combat.killaura.rotation.RotationController},
 * which applies it once to every angle returned by this mode.</p>
 */
public final class SpookyTimeAngleSmoothMode extends AngleSmoothMode {
    private final LinearSmoothMode returnMode = new LinearSmoothMode();

    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;

    private Vec3d currentAimPoint;
    private Vec3d targetAimPoint;
    private int aimPointTicks;
    private int aimPointRefreshTicks;

    private int idleTicks;
    private float idlePhase;

    private float currentSpeed;
    private int speedChangeTimer;
    private int fallDistanceTimer;
    private float jitterPhase;

    private boolean insideHitbox;
    private float hitboxSlowdownMultiplier;
    private float proximitySlowdownMultiplier;

    public SpookyTimeAngleSmoothMode() {
        super("SpookyTime");
        reset();
    }

    public void reset() {
        trackedTarget = null;
        currentAimPoint = null;
        targetAimPoint = null;
        aimPointTicks = 0;
        aimPointRefreshTicks = 0;

        idleTicks = 0;
        idlePhase = 0.0f;
        currentSpeed = 0.0f;
        speedChangeTimer = 0;
        fallDistanceTimer = 0;
        jitterPhase = 0.0f;

        insideHitbox = false;
        hitboxSlowdownMultiplier = 1.0f;
        proximitySlowdownMultiplier = 1.0f;

        initialized = mc.player != null;
        if (initialized) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        } else {
            lastYaw = 0.0f;
            lastPitch = 0.0f;
        }
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        if (mc.player == null) {
            reset();
            return currentAngle;
        }

        updateFallDistance();

        if (!(entity instanceof LivingEntity target)) {
            clearTrackedTarget();
            return updateIdle(currentAngle, targetAngle, vec3d, entity);
        }

        if (trackedTarget != target) {
            initializeTarget(target, currentAngle);
        } else {
            // The controller has already applied the 1.21.11 sensitivity step.
            lastYaw = currentAngle.getYaw();
            lastPitch = currentAngle.getPitch();
        }

        idleTicks = 0;
        idlePhase = 0.0f;

        boolean attacking = shouldAttack(target);
        Vec3d aimPoint = selectAimPoint(target, attacking);
        updateHitboxSlowdown(target, aimPoint);

        Angle desiredAngle = AngleUtil.calculateAngle(aimPoint);
        float yawDelta = MathHelper.wrapDegrees(desiredAngle.getYaw() - currentAngle.getYaw());
        float pitchDelta = desiredAngle.getPitch() - currentAngle.getPitch();
        float angleDistance = (float) Math.hypot(yawDelta, pitchDelta);

        float maxCap = Math.min(160.0f, angleDistance * 1.05f + 2.0f);
        float denominator = angleDistance + 0.001f;
        float yawCap = MathHelper.clamp(Math.abs(yawDelta / denominator) * maxCap, 0.0f, maxCap);
        float pitchCap = MathHelper.clamp(Math.abs(pitchDelta / denominator) * maxCap, 0.0f, maxCap);
        float clampedYawDelta = MathHelper.clamp(yawDelta, -yawCap, yawCap);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -pitchCap, pitchCap);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (speedChangeTimer <= 0) {
            currentSpeed = attacking
                    ? 0.25f + random.nextFloat() * 0.35f
                    : 0.10f + random.nextFloat() * 0.15f;
            speedChangeTimer = random.nextInt(4, 12);
        } else {
            speedChangeTimer--;
            currentSpeed = MathHelper.clamp(
                    currentSpeed + random.nextFloat() * 0.01f - 0.005f,
                    0.05f,
                    0.70f
            );
        }

        float finalSpeed = currentSpeed * hitboxSlowdownMultiplier * proximitySlowdownMultiplier;
        jitterPhase += 0.02f * (0.5f + random.nextFloat() * 0.6f);
        float jitterYaw = (float) Math.sin(jitterPhase) * 0.015f;
        float jitterPitch = (float) Math.cos(jitterPhase * 0.7f + 0.3f) * 0.01f;

        lastYaw = currentAngle.getYaw() + clampedYawDelta * finalSpeed + jitterYaw;
        lastPitch = MathHelper.clamp(
                currentAngle.getPitch() + clampedPitchDelta * finalSpeed + jitterPitch,
                -89.9f,
                89.9f
        );
        return new Angle(lastYaw, lastPitch);
    }

    @Override
    public Vec3d randomValue() {
        // This mode samples its own point inside the predicted target hitbox.
        return Vec3d.ZERO;
    }

    private void initializeTarget(LivingEntity target, Angle currentAngle) {
        trackedTarget = target;
        lastYaw = currentAngle.getYaw();
        lastPitch = currentAngle.getPitch();
        initialized = true;

        currentAimPoint = null;
        targetAimPoint = null;
        aimPointTicks = 0;
        aimPointRefreshTicks = ThreadLocalRandom.current().nextInt(10, 20);

        insideHitbox = false;
        hitboxSlowdownMultiplier = 1.0f;
        proximitySlowdownMultiplier = 1.0f;
    }

    private void clearTrackedTarget() {
        trackedTarget = null;
        currentAimPoint = null;
        targetAimPoint = null;
        aimPointTicks = 0;
    }

    private Vec3d selectAimPoint(LivingEntity target, boolean attacking) {
        Box box = getPredictedBox(target);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (currentAimPoint == null || targetAimPoint == null) {
            currentAimPoint = sampleAimPoint(box, 0.30, 0.70, 0.45, 0.80);
            targetAimPoint = currentAimPoint;
            return currentAimPoint;
        }

        if (aimPointTicks++ >= aimPointRefreshTicks) {
            targetAimPoint = sampleAimPoint(box, 0.25, 0.75, 0.40, 0.85);
            aimPointTicks = 0;
            aimPointRefreshTicks = attacking
                    ? random.nextInt(8, 16)
                    : random.nextInt(14, 24);
        }

        float lerpSpeed = attacking
                ? 0.035f + random.nextFloat() * 0.025f
                : 0.015f + random.nextFloat() * 0.015f;
        currentAimPoint = new Vec3d(
                MathHelper.lerp(lerpSpeed, currentAimPoint.x, targetAimPoint.x),
                MathHelper.lerp(lerpSpeed, currentAimPoint.y, targetAimPoint.y),
                MathHelper.lerp(lerpSpeed, currentAimPoint.z, targetAimPoint.z)
        );
        return currentAimPoint;
    }

    private Vec3d sampleAimPoint(
            Box box,
            double minHorizontalFactor,
            double maxHorizontalFactor,
            double minVerticalFactor,
            double maxVerticalFactor
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Vec3d(
                MathHelper.lerp(random.nextDouble(minHorizontalFactor, maxHorizontalFactor), box.minX, box.maxX),
                MathHelper.lerp(random.nextDouble(minVerticalFactor, maxVerticalFactor), box.minY, box.maxY),
                MathHelper.lerp(random.nextDouble(minHorizontalFactor, maxHorizontalFactor), box.minZ, box.maxZ)
        );
    }

    private Box getPredictedBox(LivingEntity target) {
        Vec3d velocity = target.getVelocity();
        double predictionTicks = target.isGliding() ? 1.5 : 0.5;
        return target.getBoundingBox().offset(velocity.multiply(predictionTicks));
    }

    private void updateHitboxSlowdown(LivingEntity target, Vec3d aimPoint) {
        boolean wasInside = insideHitbox;
        insideHitbox = checkInsideHitbox(target);
        if (insideHitbox) {
            hitboxSlowdownMultiplier = Math.max(0.25f, hitboxSlowdownMultiplier - 0.05f);
        } else if (wasInside) {
            hitboxSlowdownMultiplier = Math.min(1.0f, hitboxSlowdownMultiplier + 0.03f);
        } else {
            hitboxSlowdownMultiplier = 1.0f;
        }

        Angle targetAngle = AngleUtil.calculateAngle(aimPoint);
        float yawDifference = Math.abs(MathHelper.wrapDegrees(targetAngle.getYaw() - lastYaw));
        float pitchDifference = Math.abs(targetAngle.getPitch() - lastPitch);
        float angularDistance = (float) Math.hypot(yawDifference, pitchDifference);
        proximitySlowdownMultiplier = angularDistance < 10.0f
                ? MathHelper.clamp(0.5f + angularDistance / 20.0f, 0.5f, 1.0f)
                : 1.0f;
    }

    private boolean checkInsideHitbox(LivingEntity target) {
        return target.getBoundingBox().expand(0.3).contains(mc.player.getEyePos());
    }

    private boolean shouldAttack(LivingEntity target) {
        return mc.player.distanceTo(target) < 4.5f
                && mc.player.getAttackCooldownProgress(1.5f) > 0.78f;
    }

    private Angle updateIdle(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        if (!initialized) {
            lastYaw = currentAngle.getYaw();
            lastPitch = currentAngle.getPitch();
            initialized = true;
        }

        idleTicks++;
        idlePhase += 0.004f;

        /*
         * A missing entity means RotationController is returning to the real
         * camera. Keep the source's tiny idle drift, but converge so the
         * controller can finish and release its rotation plan.
         */
        float decay = Math.max(0.0f, 1.0f - idleTicks / 40.0f);
        float driftYaw = (float) Math.sin(idlePhase * 0.3f) * 0.08f * decay;
        float driftPitch = (float) Math.sin(idlePhase * 0.4f + 0.5f) * 0.05f * decay;
        float yawDelta = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float pitchDelta = targetAngle.getPitch() - currentAngle.getPitch();

        if (Math.hypot(yawDelta, pitchDelta) > 0.05) {
            lastYaw = currentAngle.getYaw() + yawDelta * 0.35f + driftYaw;
            lastPitch = MathHelper.clamp(
                    currentAngle.getPitch() + pitchDelta * 0.35f + driftPitch,
                    -89.9f,
                    89.9f
            );
            return new Angle(lastYaw, lastPitch);
        }

        return returnMode.limitAngleChange(currentAngle, targetAngle, vec3d, entity);
    }

    private void updateFallDistance() {
        if (++fallDistanceTimer <= ThreadLocalRandom.current().nextInt(10, 30)) {
            return;
        }

        fallDistanceTimer = 0;
        if (!mc.player.isOnGround()) {
            float offset = ThreadLocalRandom.current().nextFloat() - 0.5f;
            mc.player.fallDistance = Math.max(0.0f, mc.player.fallDistance + offset * 0.1f);
        }
    }
}
