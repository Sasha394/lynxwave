package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;

public class TestAngleRotation extends AngleSmoothMode {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long LOOK_DELAY_MS = 3500L;
    private static final int AIR_MISS_INTERVAL = 31;
    private static final long AIR_MISS_LOOK_MS = 250L;
    private static final long AIR_MISS_SWING_MS = 238L;

    private int lastAirMissSwingCount = -1;

    public TestAngleRotation() {
        super("TestAngle");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        AttackHandler attackHandler = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler();
        KillAura aura = KillAura.INSTANCE;
        if (attackHandler == null || aura == null) {
            return currentAngle;
        }

        TimerUtil attackTimer = attackHandler.getAttackTimer();
        int count = attackHandler.getCount();
        Angle angleDelta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw();
        float pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (rotationDifference < 1.0E-4F) {
            rotationDifference = 1.0E-4F;
        }

        long elapsed = attackTimer.getTime();
        boolean airMiss = count > 0 && count % AIR_MISS_INTERVAL == 0 && elapsed < AIR_MISS_LOOK_MS;
        if (airMiss) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && elapsed >= AIR_MISS_SWING_MS && lastAirMissSwingCount != count) {
                client.player.swingHand(Hand.MAIN_HAND);
                lastAirMissSwingCount = count;
            }

            float missYaw = currentAngle.getYaw() + MathHelper.clamp(yawDelta, -22.0F, 22.0F);
            return new Angle(missYaw, -85.0F);
        }

        if (entity != null) {
            return applyTracking(currentAngle, yawDelta, pitchDelta, rotationDifference, entity, aura, attackHandler, attackTimer);
        }

        return applyDelay(currentAngle, yawDelta, pitchDelta, rotationDifference, attackTimer, count);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.05D, 0.1D, 0.02D);
    }

    private Angle applyTracking(
            Angle currentAngle,
            float yawDelta,
            float pitchDelta,
            float rotationDifference,
            Entity entity,
            KillAura aura,
            AttackHandler attackHandler,
            TimerUtil attackTimer
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        int count = attackHandler.getCount();
        boolean attackZone = client.player != null && client.player.distanceTo(entity) <= aura.distance.getValue();
        boolean attackNow = aura.canAttack();
        boolean attackSoon = attackTimer.isFinished(350);
        boolean recentAttack = !attackTimer.isFinished(180);

        float yawBudget = rand(18.0F, 28.0F);
        float pitchBudget = rand(2.8F, 6.2F);

        if (attackSoon) {
            yawBudget = Math.max(yawBudget, rand(34.0F, 52.0F));
            pitchBudget = Math.max(pitchBudget, rand(4.2F, 7.8F));
        }

        if (recentAttack) {
            yawBudget = Math.max(yawBudget, rand(44.0F, 72.0F));
            pitchBudget = Math.max(pitchBudget, rand(5.4F, 10.0F));
        }

        if (Math.abs(yawDelta) > 40.0F) {
            yawBudget += rand(10.0F, 18.0F);
        }
        if (Math.abs(yawDelta) > 75.0F) {
            yawBudget += rand(12.0F, 24.0F);
        }
        if (Math.abs(pitchDelta) > 20.0F) {
            pitchBudget += rand(1.4F, 3.2F);
        }
        if (Math.abs(pitchDelta) > 35.0F) {
            pitchBudget += rand(1.6F, 3.8F);
        }

        float moveYaw = MathHelper.clamp(yawDelta, -axisBudget(yawDelta, rotationDifference, yawBudget), axisBudget(yawDelta, rotationDifference, yawBudget));
        float movePitch = MathHelper.clamp(pitchDelta, -axisBudget(pitchDelta, rotationDifference, pitchBudget), axisBudget(pitchDelta, rotationDifference, pitchBudget));

        float blend = attackNow
                ? 1.0F
                : attackSoon
                ? rand(0.88F, 0.97F)
                : recentAttack
                ? rand(0.74F, 0.88F)
                : rand(0.56F, 0.74F);

        if (attackZone && !attackSoon && !recentAttack) {
            blend = Math.max(blend, rand(0.68F, 0.82F));
        }

        float shakeScale = attackZone ? 1.25F : 0.9F;
        if (attackSoon) {
            shakeScale = Math.max(shakeScale, 1.4F);
        }
        if (recentAttack) {
            shakeScale = Math.max(shakeScale, 1.55F);
        }

        float shakeYaw = trackShakeYaw(attackTimer.getTime(), count, shakeScale, Math.abs(yawDelta));
        float shakePitch = trackShakePitch(attackTimer.getTime(), count, shakeScale, Math.abs(pitchDelta));

        if (Math.abs(yawDelta) < 4.0F) {
            shakeYaw *= 0.35F;
        }
        if (Math.abs(pitchDelta) < 2.5F) {
            shakePitch *= 0.25F;
        }

        float newYaw = MathHelper.lerp(blend, currentAngle.getYaw(), currentAngle.getYaw() + moveYaw) + shakeYaw;
        float newPitch = MathHelper.lerp(blend, currentAngle.getPitch(), currentAngle.getPitch() + movePitch) + shakePitch;
        return new Angle(newYaw, MathHelper.clamp(newPitch, -90.0F, 90.0F));
    }

    private Angle applyDelay(
            Angle currentAngle,
            float yawDelta,
            float pitchDelta,
            float rotationDifference,
            TimerUtil attackTimer,
            int count
    ) {
        long elapsed = attackTimer.getTime();
        Angle oscillation = switch (count % 4) {
            case 0 -> new Angle(
                    (float) Math.cos(elapsed / 40.0F + (count % 6)),
                    (float) Math.sin(elapsed / 40.0F + (count % 6))
            );
            case 1 -> new Angle(
                    (float) Math.sin(elapsed / 40.0F + (count % 6)),
                    (float) Math.cos(elapsed / 40.0F + (count % 6))
            );
            case 2 -> new Angle(
                    (float) Math.sin(elapsed / 40.0F + (count % 6)),
                    (float) -Math.cos(elapsed / 40.0F + (count % 6))
            );
            default -> new Angle(
                    (float) -Math.cos(elapsed / 40.0F + (count % 6)),
                    (float) Math.sin(elapsed / 40.0F + (count % 6))
            );
        };

        float holdProgress = MathHelper.clamp(elapsed / (float) LOOK_DELAY_MS, 0.0F, 1.0F);
        float holdScale = attackTimer.isFinished(LOOK_DELAY_MS) ? 0.0F : 1.0F - holdProgress * 0.55F;

        float idleYaw = holdScale > 0.0F ? rand(12.0F, 22.0F) * oscillation.getYaw() * holdScale : 0.0F;
        float pitchWave = rand(0.35F, 1.35F) * (float) Math.cos((double) System.currentTimeMillis() / 420.0D + count);
        float idlePitch = holdScale > 0.0F
                ? (rand(2.2F, 5.8F) * oscillation.getPitch() + pitchWave) * holdScale
                : 0.0F;

        float yawBudget = !attackTimer.isFinished(180)
                ? rand(0.0F, 3.5F)
                : !attackTimer.isFinished(600)
                ? rand(4.0F, 10.0F)
                : attackTimer.isFinished(LOOK_DELAY_MS)
                ? rand(12.0F, 28.0F)
                : rand(6.0F, 14.0F);

        float pitchBudget = !attackTimer.isFinished(180)
                ? rand(0.0F, 1.0F)
                : !attackTimer.isFinished(600)
                ? rand(1.2F, 3.0F)
                : attackTimer.isFinished(LOOK_DELAY_MS)
                ? rand(3.0F, 6.8F)
                : rand(1.5F, 4.2F);

        float moveYaw = MathHelper.clamp(yawDelta, -axisBudget(yawDelta, rotationDifference, yawBudget), axisBudget(yawDelta, rotationDifference, yawBudget));
        float movePitch = MathHelper.clamp(pitchDelta, -axisBudget(pitchDelta, rotationDifference, pitchBudget), axisBudget(pitchDelta, rotationDifference, pitchBudget));

        float returnBlend = !attackTimer.isFinished(180)
                ? 0.0F
                : !attackTimer.isFinished(600)
                ? rand(0.08F, 0.22F)
                : attackTimer.isFinished(LOOK_DELAY_MS)
                ? rand(0.54F, 0.78F)
                : rand(0.20F, 0.42F);

        float newYaw = MathHelper.lerp(returnBlend, currentAngle.getYaw(), currentAngle.getYaw() + moveYaw) + idleYaw;
        float newPitch = MathHelper.lerp(returnBlend, currentAngle.getPitch(), currentAngle.getPitch() + movePitch) + idlePitch;
        return new Angle(newYaw, MathHelper.clamp(newPitch, -90.0F, 90.0F));
    }

    private float trackShakeYaw(long elapsed, int count, float scale, float absYawDelta) {
        float shake = (float) Math.sin(elapsed / 38.0F + count * 0.37F) * rand(0.45F, 1.25F)
                + (float) Math.cos(elapsed / 71.0F + count * 0.18F) * rand(0.18F, 0.55F);

        if (chance(absYawDelta > 24.0F ? 0.22F : 0.08F)) {
            shake += rand(-1.55F, 1.55F);
        }

        return shake * scale;
    }

    private float trackShakePitch(long elapsed, int count, float scale, float absPitchDelta) {
        float shake = (float) Math.sin(elapsed / 52.0F + count * 0.21F) * rand(0.10F, 0.42F)
                + (float) Math.cos(elapsed / 93.0F + count * 0.11F) * rand(0.08F, 0.28F);

        if (chance(absPitchDelta > 8.0F ? 0.18F : 0.06F)) {
            shake += rand(-0.55F, 0.55F);
        }

        return shake * scale;
    }

    private float axisBudget(float axisDelta, float rotationDifference, float budget) {
        return Math.abs(axisDelta / rotationDifference) * budget;
    }

    private boolean chance(float probability) {
        return RANDOM.nextFloat() < probability;
    }

    private float rand(float min, float max) {
        return MathHelper.lerp(RANDOM.nextFloat(), min, max);
    }
}
