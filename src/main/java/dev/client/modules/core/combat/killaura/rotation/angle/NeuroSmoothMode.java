package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import dev.client.modules.core.combat.killaura.rotation.neuro.NeuroRotationManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class NeuroSmoothMode extends AngleSmoothMode {
    private static final float MIN_STEP = 0.02f;
    private static final float MAX_YAW_STEP = 35.0f;
    private static final float MAX_PITCH_STEP = 25.0f;
    private static final float HIT_ASSIST_BASE = 0.42f;
    private static final float HIT_ASSIST_MAX = 0.78f;
    private static final float RECORDED_SPEED_MIN_SCALE = 0.78f;
    private static final float RECORDED_SPEED_MAX_SCALE = 1.16f;
    private static final float TRACK_BASE_WEIGHT = 0.30f;
    private static final float TRACK_MAX_WEIGHT = 0.72f;
    private static final float ACQUIRE_BASE_WEIGHT = 0.52f;
    private static final float ACQUIRE_MAX_WEIGHT = 0.9f;
    private static final float SWING_BASE_WEIGHT = 0.6f;
    private static final float SWING_MAX_WEIGHT = 0.95f;
    private static final float RECOVER_BASE_WEIGHT = 0.24f;
    private static final float RECOVER_MAX_WEIGHT = 0.56f;
    private static final float PLAYBACK_TRACK_BASE_WEIGHT = 0.08f;
    private static final float PLAYBACK_TRACK_MAX_WEIGHT = 0.18f;
    private static final float PLAYBACK_ACQUIRE_BASE_WEIGHT = 0.14f;
    private static final float PLAYBACK_ACQUIRE_MAX_WEIGHT = 0.28f;
    private static final float PLAYBACK_SWING_BASE_WEIGHT = 0.18f;
    private static final float PLAYBACK_SWING_MAX_WEIGHT = 0.34f;
    private static final float PLAYBACK_RECOVER_BASE_WEIGHT = 0.06f;
    private static final float PLAYBACK_RECOVER_MAX_WEIGHT = 0.16f;
    private final LinearSmoothMode fallback = new LinearSmoothMode();

    public NeuroSmoothMode() {
        super("Neuro");
    }


    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        if (entity == null) {
            return fallback.limitAngleChange(currentAngle, targetAngle, vec3d, entity);
        }

        NeuroRotationManager manager = LynxWave.getInstance().getNeuroRotationManager();
        if (manager == null) {
            return currentAngle;
        }

        NeuroRotationManager.RotationSample sample = manager.nextNeuroSample(currentAngle, targetAngle, entity);
        if (sample == null) {
            return currentAngle;
        }

        Angle delta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float deltaMagnitude = (float) Math.hypot(Math.abs(delta.getYaw()), Math.abs(delta.getPitch()));
        NeuroRotationManager.AimPhase phase = manager.getCurrentPhase();
        boolean playbackActive = manager.isPlaybackActive();
        float baseWeight = getBaseWeight(phase, playbackActive);
        float maxWeight = getMaxWeight(phase, playbackActive);
        float correctionWeight = MathHelper.clamp(
                baseWeight + deltaMagnitude / 180.0f,
                baseWeight,
                maxWeight
        );
        correctionWeight = applyRecordedTempoWeight(correctionWeight, baseWeight, maxWeight, sample);

        float profileYaw = MathHelper.clamp(sample.getYaw(), -MAX_YAW_STEP, MAX_YAW_STEP);
        float profilePitch = MathHelper.clamp(sample.getPitch(), -MAX_PITCH_STEP, MAX_PITCH_STEP);
        float targetYaw = MathHelper.clamp(delta.getYaw(), -MAX_YAW_STEP, MAX_YAW_STEP);
        float targetPitch = MathHelper.clamp(delta.getPitch(), -MAX_PITCH_STEP, MAX_PITCH_STEP);

        float moveYaw = MathHelper.clamp(
                profileYaw * (1.0f - correctionWeight) + targetYaw * correctionWeight,
                -MAX_YAW_STEP,
                MAX_YAW_STEP
        );
        float movePitch = MathHelper.clamp(
                profilePitch * (1.0f - correctionWeight) + targetPitch * correctionWeight,
                -MAX_PITCH_STEP,
                MAX_PITCH_STEP
        );
        moveYaw = matchRecordedAxisTempo(moveYaw, profileYaw, sample.getYawSpeed(), sample.getScreenSpeed(), sample.getGcdStepAverage(), MAX_YAW_STEP);
        movePitch = matchRecordedAxisTempo(movePitch, profilePitch, sample.getPitchSpeed(), sample.getScreenSpeed(), sample.getGcdStepAverage(), MAX_PITCH_STEP);

        Angle sampledAngle = new Angle(currentAngle.getYaw() + moveYaw, currentAngle.getPitch() + movePitch);
        if (shouldAssistForHit(entity, manager, sampledAngle)) {
            float remainingYaw = MathHelper.wrapDegrees(targetAngle.getYaw() - sampledAngle.getYaw());
            float remainingPitch = MathHelper.wrapDegrees(targetAngle.getPitch() - sampledAngle.getPitch());
            float assistWeight = MathHelper.clamp(
                    HIT_ASSIST_BASE
                            + deltaMagnitude / 90.0f
                            + MathHelper.clamp(sample.getScreenSpeed() / 26.0f, 0.0f, 0.12f)
                            + MathHelper.clamp(sample.getGcdStepAverage() / 20.0f, 0.0f, 0.08f),
                    HIT_ASSIST_BASE,
                    HIT_ASSIST_MAX
            );

            moveYaw += MathHelper.clamp(remainingYaw * assistWeight, -MAX_YAW_STEP, MAX_YAW_STEP);
            movePitch += MathHelper.clamp(remainingPitch * assistWeight, -MAX_PITCH_STEP, MAX_PITCH_STEP);
        }

        if (Math.abs(moveYaw) < MIN_STEP && Math.abs(delta.getYaw()) > MIN_STEP) {
            moveYaw = Math.copySign(Math.min(Math.abs(delta.getYaw()), MIN_STEP), delta.getYaw());
        }
        if (Math.abs(movePitch) < MIN_STEP && Math.abs(delta.getPitch()) > MIN_STEP) {
            movePitch = Math.copySign(Math.min(Math.abs(delta.getPitch()), MIN_STEP), delta.getPitch());
        }

        moveYaw = MathHelper.clamp(moveYaw, -MAX_YAW_STEP, MAX_YAW_STEP);
        movePitch = MathHelper.clamp(movePitch, -MAX_PITCH_STEP, MAX_PITCH_STEP);
        return new Angle(currentAngle.getYaw() + moveYaw, currentAngle.getPitch() + movePitch);
    }


    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    private float getBaseWeight(NeuroRotationManager.AimPhase phase, boolean playbackActive) {
        if (playbackActive) {
            return switch (phase) {
                case ACQUIRE -> PLAYBACK_ACQUIRE_BASE_WEIGHT;
                case SWING -> PLAYBACK_SWING_BASE_WEIGHT;
                case RECOVER -> PLAYBACK_RECOVER_BASE_WEIGHT;
                case TRACK, IDLE -> PLAYBACK_TRACK_BASE_WEIGHT;
            };
        }
        return switch (phase) {
            case ACQUIRE -> ACQUIRE_BASE_WEIGHT;
            case SWING -> SWING_BASE_WEIGHT;
            case RECOVER -> RECOVER_BASE_WEIGHT;
            case TRACK, IDLE -> TRACK_BASE_WEIGHT;
        };
    }

    private float getMaxWeight(NeuroRotationManager.AimPhase phase, boolean playbackActive) {
        if (playbackActive) {
            return switch (phase) {
                case ACQUIRE -> PLAYBACK_ACQUIRE_MAX_WEIGHT;
                case SWING -> PLAYBACK_SWING_MAX_WEIGHT;
                case RECOVER -> PLAYBACK_RECOVER_MAX_WEIGHT;
                case TRACK, IDLE -> PLAYBACK_TRACK_MAX_WEIGHT;
            };
        }
        return switch (phase) {
            case ACQUIRE -> ACQUIRE_MAX_WEIGHT;
            case SWING -> SWING_MAX_WEIGHT;
            case RECOVER -> RECOVER_MAX_WEIGHT;
            case TRACK, IDLE -> TRACK_MAX_WEIGHT;
        };
    }

    private float applyRecordedTempoWeight(float correctionWeight, float baseWeight, float maxWeight, NeuroRotationManager.RotationSample sample) {
        float screenFactor = MathHelper.clamp(sample.getScreenSpeed() / 12.0f, 0.0f, 1.0f);
        float gcdFactor = MathHelper.clamp(sample.getGcdStepAverage() / 10.0f, 0.0f, 1.0f);
        float recordedTempoScale = MathHelper.clamp(
                RECORDED_SPEED_MIN_SCALE + screenFactor * 0.26f + gcdFactor * 0.12f,
                RECORDED_SPEED_MIN_SCALE,
                RECORDED_SPEED_MAX_SCALE
        );
        return MathHelper.clamp(correctionWeight * recordedTempoScale, baseWeight * 0.92f, maxWeight);
    }

    private float matchRecordedAxisTempo(float move, float profileMove, float axisSpeed, float screenSpeed, float gcdStepAverage, float maxStep) {
        float speedFactor = MathHelper.clamp(axisSpeed / 10.0f, 0.0f, 1.0f);
        float screenFactor = MathHelper.clamp(screenSpeed / 14.0f, 0.0f, 1.0f);
        float gcdFactor = MathHelper.clamp(gcdStepAverage / 9.0f, 0.0f, 1.0f);
        float preserveWeight = MathHelper.clamp(0.62f - speedFactor * 0.18f - screenFactor * 0.08f + gcdFactor * 0.06f, 0.3f, 0.72f);
        float blendedMove = MathHelper.lerp(preserveWeight, move, profileMove);

        float recordedMagnitudeCap = Math.max(Math.abs(profileMove), axisSpeed / 4.5f);
        if (recordedMagnitudeCap <= MIN_STEP) {
            return MathHelper.clamp(blendedMove, -maxStep, maxStep);
        }

        float softenedCap = Math.min(maxStep, recordedMagnitudeCap * (1.05f + screenFactor * 0.18f));
        return Math.copySign(Math.min(Math.abs(blendedMove), softenedCap), blendedMove);
    }

    private boolean shouldAssistForHit(Entity entity, NeuroRotationManager manager, Angle sampledAngle) {
        KillAura aura = KillAura.INSTANCE;
        if (aura == null || !(entity instanceof LivingEntity living)) {
            return false;
        }

        AttackHandler attackHandler = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler();
        if (attackHandler == null || aura.getTarget() != living) {
            return false;
        }

        boolean attackNow = aura.canAttack();
        boolean swingPhase = manager.getCurrentPhase() == NeuroRotationManager.AimPhase.SWING;
        if (!attackNow && !swingPhase) {
            return false;
        }

        return !RaytracingUtil.rayTrace(sampledAngle.toVector(), aura.distance.getValue() - 0.25F, living.getBoundingBox());
    }
}
