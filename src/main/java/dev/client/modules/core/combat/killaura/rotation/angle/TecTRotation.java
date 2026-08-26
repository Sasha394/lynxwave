package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;


public final class TecTRotation extends AngleSmoothMode {
    private static final int HEAD = 0;
    private static final int TORSO = 1;
    private static final int LEGS = 2;

    private final LinearSmoothMode returnMode = new LinearSmoothMode();

    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private int tick;

    private int currentZone;
    private int targetZone;
    private float zoneProgress;
    private int zoneTimer;
    private Vec3d dynamicHeadPoint;
    private Vec3d dynamicTorsoPoint;
    private Vec3d dynamicLegsPoint;

    private float noisePhase;
    private float noiseYaw;
    private float noisePitch;

    private int overshootTicks;
    private float overshootYaw;
    private float overshootPitch;

    public TecTRotation() {
        super("TecT");
    }

    public void reset() {
        trackedTarget = null;
        tick = 0;
        currentZone = TORSO;
        targetZone = HEAD;
        zoneProgress = 0.0f;
        zoneTimer = 0;
        dynamicHeadPoint = null;
        dynamicTorsoPoint = null;
        dynamicLegsPoint = null;
        noisePhase = 0.0f;
        noiseYaw = 0.0f;
        noisePitch = 0.0f;
        overshootTicks = 0;
        overshootYaw = 0.0f;
        overshootPitch = 0.0f;

        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        if (mc.player == null || !(entity instanceof LivingEntity target)) {
            trackedTarget = null;
            return returnMode.limitAngleChange(currentAngle, targetAngle, vec3d, entity);
        }

        if (mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.SHIELD)) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            return new Angle(lastYaw, lastPitch);
        }

        if (trackedTarget != target) {
            initializeTarget(target, currentAngle);
        } else {
            // RotationController is authoritative after its 1.21.11 sensitivity correction.
            lastYaw = currentAngle.getYaw();
            lastPitch = currentAngle.getPitch();
        }

        tick++;
        Box predictedBox = getPredictedBox(target);
        double distance = mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());

        refreshZonePoints(predictedBox);
        updateZone();

        float transitionSpeed = 0.025f + ThreadLocalRandom.current().nextFloat() * 0.02f;
        zoneProgress = Math.min(1.0f, zoneProgress + transitionSpeed);
        float ease = zoneProgress * zoneProgress * (3.0f - 2.0f * zoneProgress);

        Vec3d sourcePoint = pointForZone(currentZone);
        Vec3d destinationPoint = pointForZone(targetZone);
        Vec3d aimPoint = zoneProgress < 1.0f
                ? new Vec3d(
                        MathHelper.lerp(ease, sourcePoint.x, destinationPoint.x),
                        MathHelper.lerp(ease, sourcePoint.y, destinationPoint.y),
                        MathHelper.lerp(ease, sourcePoint.z, destinationPoint.z)
                )
                : destinationPoint;

        updateNoise();
        aimPoint = aimPoint.add(noiseYaw, noisePitch, 0.0);

        Angle desiredAngle = AngleUtil.calculateAngle(aimPoint);
        float targetYaw = desiredAngle.getYaw();
        float targetPitch = desiredAngle.getPitch();
        float yawDifference = Math.abs(MathHelper.wrapDegrees(targetYaw - lastYaw));

        KillAura aura = KillAura.INSTANCE;
        boolean ready = mc.player.getAttackCooldownProgress(1.0f) > 0.9f
                && aura != null
                && aura.getTicksToAttack() <= 1;

        float aggression = (float) MathHelper.clamp(1.0 - (distance - 1.0) / 6.0, 0.2, 1.0);
        float baseSpeed = 0.025f + aggression * 0.06f;
        if (yawDifference > 60.0f) {
            baseSpeed = Math.max(baseSpeed, 0.2f);
        } else if (yawDifference > 30.0f) {
            baseSpeed = Math.max(baseSpeed, 0.12f);
        } else if (yawDifference > 10.0f) {
            baseSpeed = Math.max(baseSpeed, 0.06f);
        }

        if (ready) {
            baseSpeed *= 1.2f;
        }
        if (ThreadLocalRandom.current().nextFloat() < 0.03f) {
            baseSpeed *= 0.3f + ThreadLocalRandom.current().nextFloat() * 0.4f;
        }
        float speed = MathHelper.clamp(baseSpeed, 0.008f, 0.3f);

        updateOvershoot(ready);

        float microYaw = (float) (
                Math.sin(tick * 0.19) * 0.015
                        + Math.sin(tick * 0.47) * 0.008
                        + Math.cos(tick * 0.31) * 0.012
        );
        float microPitch = (float) (
                Math.cos(tick * 0.23) * 0.012
                        + Math.cos(tick * 0.53) * 0.006
                        + Math.sin(tick * 0.37) * 0.01
        );

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw) * speed
                + microYaw + overshootYaw + noiseYaw * 0.02f;
        float deltaPitch = (targetPitch - lastPitch) * speed
                + microPitch + overshootPitch + noisePitch * 0.02f;

        float maxYawDelta = ready ? 32.0f : 18.0f;
        float maxPitchDelta = ready ? 4.5f : 2.5f;
        deltaYaw = MathHelper.clamp(deltaYaw, -maxYawDelta, maxYawDelta);
        deltaPitch = MathHelper.clamp(deltaPitch, -maxPitchDelta, maxPitchDelta);

        lastYaw += deltaYaw;
        lastPitch = MathHelper.clamp(lastPitch + deltaPitch, -89.0f, 89.0f);
        return new Angle(lastYaw, lastPitch);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    private void initializeTarget(LivingEntity target, Angle currentAngle) {
        trackedTarget = target;
        tick = 0;
        currentZone = ThreadLocalRandom.current().nextInt(3);
        do {
            targetZone = ThreadLocalRandom.current().nextInt(3);
        } while (targetZone == currentZone);

        zoneProgress = 0.0f;
        zoneTimer = 20 + ThreadLocalRandom.current().nextInt(30);
        dynamicHeadPoint = null;
        dynamicTorsoPoint = null;
        dynamicLegsPoint = null;
        noisePhase = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0);
        noiseYaw = 0.0f;
        noisePitch = 0.0f;
        overshootTicks = 0;
        overshootYaw = 0.0f;
        overshootPitch = 0.0f;
        lastYaw = currentAngle.getYaw();
        lastPitch = currentAngle.getPitch();
    }

    private Box getPredictedBox(LivingEntity target) {
        Vec3d velocity = target.getVelocity();
        double predictionTicks = target.isGliding() ? 1.5 : 0.5;
        return target.getBoundingBox().offset(velocity.multiply(predictionTicks));
    }

    private Vec3d sampleZonePoint(Box box, int zone) {
        double height = box.maxY - box.minY;
        double width = box.maxX - box.minX;
        double depth = box.maxZ - box.minZ;

        double minY;
        double maxY;
        switch (zone) {
            case HEAD -> {
                minY = box.minY + height * 0.7;
                maxY = box.maxY;
            }
            case TORSO -> {
                minY = box.minY + height * 0.3;
                maxY = box.minY + height * 0.75;
            }
            case LEGS -> {
                minY = box.minY;
                maxY = box.minY + height * 0.35;
            }
            default -> {
                minY = box.minY;
                maxY = box.maxY;
            }
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            Vec3d point = new Vec3d(
                    box.minX + ThreadLocalRandom.current().nextDouble() * width,
                    minY + ThreadLocalRandom.current().nextDouble() * (maxY - minY),
                    box.minZ + ThreadLocalRandom.current().nextDouble() * depth
            );
            Angle pointAngle = AngleUtil.calculateAngle(point);
            if (RaytracingUtil.rayTrace(pointAngle.toVector(), 256.0, box)) {
                return point;
            }
        }

        double heightFactor = switch (zone) {
            case HEAD -> 0.85;
            case TORSO -> 0.5;
            default -> 0.18;
        };
        return new Vec3d(
                box.minX + width / 2.0,
                box.minY + height * heightFactor,
                box.minZ + depth / 2.0
        );
    }

    private void refreshZonePoints(Box box) {
        dynamicHeadPoint = sampleZonePoint(box, HEAD);
        dynamicTorsoPoint = sampleZonePoint(box, TORSO);
        dynamicLegsPoint = sampleZonePoint(box, LEGS);
    }

    private void updateZone() {
        zoneTimer--;
        if (zoneTimer > 0) {
            return;
        }

        currentZone = targetZone;
        do {
            targetZone = ThreadLocalRandom.current().nextInt(3);
        } while (targetZone == currentZone);
        zoneProgress = 0.0f;
        zoneTimer = 25 + ThreadLocalRandom.current().nextInt(35);
    }

    private Vec3d pointForZone(int zone) {
        return switch (zone) {
            case HEAD -> dynamicHeadPoint;
            case TORSO -> dynamicTorsoPoint;
            default -> dynamicLegsPoint;
        };
    }

    private void updateNoise() {
        noisePhase += 0.1f + ThreadLocalRandom.current().nextFloat() * 0.04f;
        noiseYaw += ((float) (
                Math.sin(noisePhase) * 0.35
                        + Math.sin(noisePhase * 2.4) * 0.15
                        + Math.cos(noisePhase * 0.6) * 0.1
        ) - noiseYaw) * 0.1f;
        noisePitch += ((float) (
                Math.cos(noisePhase * 1.3) * 0.25
                        + Math.cos(noisePhase * 3.7) * 0.1
                        + Math.sin(noisePhase * 0.8) * 0.08
        ) - noisePitch) * 0.1f;
    }

    private void updateOvershoot(boolean ready) {
        if (overshootTicks > 0) {
            overshootTicks--;
            overshootYaw *= 0.75f;
            overshootPitch *= 0.75f;
        } else if (ready && ThreadLocalRandom.current().nextFloat() < 0.025f) {
            overshootTicks = 2 + ThreadLocalRandom.current().nextInt(3);
            overshootYaw = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.6f;
            overshootPitch = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.25f;
        }
    }
}
