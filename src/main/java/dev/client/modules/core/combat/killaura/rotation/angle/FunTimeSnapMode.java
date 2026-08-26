package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.LynxWave;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class FunTimeSnapMode extends AngleSmoothMode {

    private static final float MAX_STEP_DEG = 35.0F;
    private static final float MAX_STEP_DEG_FAST = 60.0F;

    private long lastTickMs = -1L;
    private long smoothbackShakeStartMs = -1L;
    private float activeShakePhase = 0.0F;

    public FunTimeSnapMode() {
        super("FunTime");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        KillAura aura = KillAura.INSTANCE;
        AttackHandler attackHandler = LynxWave.getInstance().getAttackPerpetrator().getAttackHandler();

        if (aura == null || mc.player == null) {
            return currentAngle;
        }

        if (aura.isEnabled() && aura.getTarget() != null && entity != null) {
            this.smoothbackShakeStartMs = -1L;

            Box bb = entity.getBoundingBox();
            float cooldown = mc.player.getAttackCooldownProgress(0.0F);

            Vec3d aimPoint;
            if (cooldown >= 0.92F) {
                Vec3d centre = bb.getCenter();
                aimPoint = new Vec3d(centre.x, centre.y, centre.z);
            } else {
                aimPoint = this.computeAimPoint(bb);
            }

            Angle aimAngle = AngleUtil.fromVec3d(aimPoint.subtract(mc.player.getEyePos()));
            Angle deltaAngle = AngleUtil.calculateDelta(currentAngle, aimAngle);
            float yawDelta = deltaAngle.getYaw();
            float pitchDelta = deltaAngle.getPitch();
            float totalDelta = (float) Math.hypot(yawDelta, pitchDelta);

            float noiseYaw = 0.0F;
            float noisePitch = 0.0F;
            if (cooldown < 0.92F) {
                this.activeShakePhase += 0.15F;
                noiseYaw = (float) (Math.sin(this.activeShakePhase) * 4.0F);
                noisePitch = (float) (Math.cos(this.activeShakePhase * 0.7F) * 2.0F);
            } else {
                this.activeShakePhase = 0.0F;
            }

            if (totalDelta < 1.0E-4F) {
                return new Angle(
                        currentAngle.getYaw() + noiseYaw,
                        MathHelper.clamp(currentAngle.getPitch() + noisePitch, -89.0F, 90.0F)
                );
            }

            float speedBonus = this.computeSpeedBonus(entity);
            float budget = MathHelper.clamp(35.0F + speedBonus, 35.0F, 60.0F);
            if (totalDelta <= budget) {
                return new Angle(
                        currentAngle.getYaw() + yawDelta + noiseYaw,
                        MathHelper.clamp(currentAngle.getPitch() + pitchDelta + noisePitch, -89.0F, 90.0F)
                );
            }

            float scale = budget / totalDelta;
            return new Angle(
                    currentAngle.getYaw() + yawDelta * scale + noiseYaw,
                    MathHelper.clamp(currentAngle.getPitch() + pitchDelta * scale + noisePitch, -89.0F, 90.0F)
            );
        } else {
            Angle playerAngle = new Angle(mc.player.getYaw(), mc.player.getPitch());
            Angle returnDelta = AngleUtil.calculateDelta(currentAngle, playerAngle);
            float retYaw = returnDelta.getYaw();
            float retPitch = returnDelta.getPitch();
            float retTotal = (float) Math.hypot(retYaw, retPitch);

            if (retTotal < 1.0E-4F) {
                this.smoothbackShakeStartMs = -1L;
                this.lastTickMs = -1L;
                return currentAngle;
            }

            if (this.smoothbackShakeStartMs < 0L) {
                this.smoothbackShakeStartMs = System.currentTimeMillis();
                if (attackHandler != null) {
                    attackHandler.setCount(0);
                }
                this.lastTickMs = -1L;
            }

            float fadeRatio = 1.0F - MathHelper.clamp((float) (System.currentTimeMillis() - this.smoothbackShakeStartMs) / 1000.0F, 0.0F, 1.0F);
            float shakeYaw = (float) (this.randomBetween(18.0F, 28.0F) * Math.sin(System.currentTimeMillis() / 60.0)) * fadeRatio;
            float shakePitch = (float) (this.randomBetween(6.0F, 16.0F) * Math.cos(System.currentTimeMillis() / 60.0)) * fadeRatio;
            float limitMultiplier = attackHandler != null && !attackHandler.getAttackTimer().isFinished(535.0) ? 0.0F : 45.0F;
            float yawLim = Math.abs(retYaw / retTotal) * limitMultiplier;
            float pitchLim = Math.abs(retPitch / retTotal) * limitMultiplier;

            return new Angle(
                    MathHelper.lerp(0.85F, currentAngle.getYaw(), currentAngle.getYaw() + MathHelper.clamp(retYaw, -yawLim, yawLim) + shakeYaw),
                    MathHelper.lerp(0.85F, currentAngle.getPitch(), currentAngle.getPitch() + MathHelper.clamp(retPitch, -pitchLim, pitchLim) + shakePitch)
            );
        }
    }

    private Vec3d computeAimPoint(Box bb) {
        double height = bb.maxY - bb.minY;
        double aimY = bb.minY + height * 0.08;
        Vec3d centre = bb.getCenter();
        return new Vec3d(centre.x, aimY, centre.z);
    }

    private float computeSpeedBonus(Entity entity) {
        if (mc.player == null) {
            return 0.0F;
        }
        double playerSpeed = horizontalSpeed(mc.player.getVelocity());
        double targetSpeed = horizontalSpeed(entity.getVelocity());
        double combined = playerSpeed + targetSpeed;
        return combined < 0.1 ? 0.0F : (float) MathHelper.clamp(combined * 25.0, 0.0, 50.0);
    }

    private static double horizontalSpeed(Vec3d v) {
        return v == null ? 0.0 : Math.hypot(v.x, v.z);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    private float randomBetween(float min, float max) {
        if (min == max) {
            return min;
        }
        if (min > max) {
            float tmp = min;
            min = max;
            max = tmp;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }
}
