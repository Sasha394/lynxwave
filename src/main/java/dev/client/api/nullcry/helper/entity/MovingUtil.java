package dev.client.api.nullcry.helper.entity;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.compat.InputCompat;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import lombok.experimental.UtilityClass;
import net.minecraft.block.AirBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.stream.Stream;

@UtilityClass
public class MovingUtil implements ClientApi {

    public KeyBinding[] getMovementKeys(boolean includeSneak) {
        return Stream.of(
                        mc.options.forwardKey,
                        mc.options.backKey,
                        mc.options.leftKey,
                        mc.options.rightKey,
                        mc.options.jumpKey,
                        mc.options.sprintKey,
                        includeSneak ? mc.options.sneakKey : null).filter(Objects::nonNull)
                .toArray(KeyBinding[]::new);
    }

    public boolean isMoving() {
        return InputCompat.movementForward(mc.player.input) != 0f || InputCompat.movementSideways(mc.player.input) != 0f;
    }

    public boolean isPlayerMoving() {
        return mc.player.lastX != mc.player.getX() || mc.player.lastY != mc.player.getY() || mc.player.lastZ != mc.player.getZ();
    }

    public double getVelocity() {
        return Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
    }

    public static boolean reason(boolean water) {
        boolean critWater = water && mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ())).getBlock() instanceof FluidBlock && mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ())).getBlock() instanceof AirBlock;
        return mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || mc.player.isClimbing() || mc.player.isTouchingWater() && !critWater || mc.player.abilities.flying;
    }

    public void setVelocity(final double motion) {
        double forward = InputCompat.movementForward(mc.player.input);
        double strafe = InputCompat.movementSideways(mc.player.input);
        float yaw = mc.player.getYaw();

        if (forward == 0 && strafe == 0) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        } else {
            if (forward != 0) {
                if (strafe > 0) {
                    yaw += (float) (forward > 0 ? -45 : 45);
                } else if (strafe < 0) {
                    yaw += (float) (forward > 0 ? 45 : -45);
                }
                strafe = 0;
                forward = (forward > 0) ? 1 : (forward < 0 ? -1 : 0);
            }

            float yawRad = (float) Math.toRadians(yaw + 90.0f);
            float cosYaw = MathHelper.cos(yawRad);
            float sinYaw = MathHelper.sin(yawRad);

            double velocityX = forward * motion * cosYaw + strafe * motion * sinYaw;
            double velocityZ = forward * motion * sinYaw - strafe * motion * cosYaw;

            mc.player.setVelocity(velocityX, mc.player.getVelocity().y, velocityZ);
        }
    }

    public static double[] forward(final double d) {
        float f = InputCompat.movementForward(mc.player.input);
        float f2 = InputCompat.movementSideways(mc.player.input);
        float f3 = mc.player.getYaw();
        if (f != 0.0f) {
            if (f2 > 0.0f) {
                f3 += ((f > 0.0f) ? -45 : 45);
            } else if (f2 < 0.0f) {
                f3 += ((f > 0.0f) ? 45 : -45);
            }
            f2 = 0.0f;
            if (f > 0.0f) {
                f = 1.0f;
            } else if (f < 0.0f) {
                f = -1.0f;
            }
        }
        final double d2 = Math.sin(Math.toRadians(f3 + 90.0f));
        final double d3 = Math.cos(Math.toRadians(f3 + 90.0f));
        final double d4 = f * d * d3 + f2 * d * d2;
        final double d5 = f * d * d2 - f2 * d * d3;
        return new double[]{d4, d5};
    }

    public double[] getSpeed(double speed) {
        KillAura killAura = KillAura.INSTANCE;
        boolean check = (killAura.getTarget() != null);
        float yaw = check ? RotationController.INSTANCE.getMoveRotation().getYaw() : mc.player.getYaw();
        float forward = InputCompat.movementForward(mc.player.input);
        float strafe = InputCompat.movementSideways(mc.player.input);
        if (forward != 0) {
            if (strafe > 0) {
                yaw += (forward > 0 ? -45 : 45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }
            strafe = 0;
            if (forward > 0) {
                forward = 1;
            } else if (forward < 0) {
                forward = -1;
            }
        }
        return new double[]{(forward * speed * Math.cos(Math.toRadians(yaw + 90)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90))), (forward * speed * Math.sin(Math.toRadians(yaw + 90)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90))), yaw};
    }

    public static float getDirection() {
        KillAura killAura = KillAura.INSTANCE;
        boolean check = (killAura.getTarget() != null);
        float rotationYaw = check ? RotationController.INSTANCE.getMoveRotation().getYaw() : mc.player.getYaw();

        float strafeFactor = 0f;

        float forward = InputCompat.movementForward(mc.player.input);
        float sideways = InputCompat.movementSideways(mc.player.input);

        if (forward > 0) strafeFactor = 1;
        if (forward < 0) strafeFactor = -1;

        if (strafeFactor == 0) {
            if (sideways > 0) rotationYaw -= 90;

            if (sideways < 0) rotationYaw += 90;
        } else {
            if (sideways > 0) rotationYaw -= 45 * strafeFactor;

            if (sideways < 0) rotationYaw += 45 * strafeFactor;
        }

        if (strafeFactor < 0) rotationYaw -= 180;

        return (float) Math.toRadians(rotationYaw);
    }

    public double getDegreesRelativeToView(
            Vec3d positionRelativeToPlayer,
            float yaw) {

        float optimalYaw =
                (float) Math.atan2(-positionRelativeToPlayer.x, positionRelativeToPlayer.z);
        double currentYaw = Math.toRadians(MathHelper.wrapDegrees(yaw));

        return Math.toDegrees(MathHelper.wrapDegrees((optimalYaw - currentYaw)));
    }

    public PlayerInput getDirectionalInputForDegrees(PlayerInput input, double dgs, float deadAngle) {
        boolean forwards = input.forward();
        boolean backwards = input.backward();
        boolean left = input.left();
        boolean right = input.right();

        if (dgs >= (-90.0F + deadAngle) && dgs <= (90.0F - deadAngle)) {
            forwards = true;
        } else if (dgs < (-90.0F - deadAngle) || dgs > (90.0F + deadAngle)) {
            backwards = true;
        }

        if (dgs >= (0.0F + deadAngle) && dgs <= (180.0F - deadAngle)) {
            right = true;
        } else if (dgs >= (-180.0F + deadAngle) && dgs <= (0.0F - deadAngle)) {
            left = true;
        }

        return new PlayerInput(forwards, backwards, left, right, input.jump(), input.sneak(), input.sprint());
    }

    public PlayerInput getDirectionalInputForDegrees(PlayerInput input, double dgs) {
        return getDirectionalInputForDegrees(input, dgs, 20.0F);
    }

    public double getBaseSpeed() {
        double baseSpeed = 0.2873;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            baseSpeed *= 1.0 + 0.2 * (double) (amplifier + 1);
        }
        return baseSpeed;
    }

    public int getSpeedEffect() {
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            return Objects.requireNonNull(mc.player.getStatusEffect(StatusEffects.SPEED)).getAmplifier() + 1;
        }
        return 0;
    }

    public static void setStrafe(double motion) {
        if (!isMoving()) return;
        double radians = getDirection();
        double x = -Math.sin(radians) * motion;
        double z = Math.cos(radians) * motion;
        mc.player.setVelocity(x, mc.player.getVelocity().y, z);
    }

    public static double direction(float rotationYaw, final double moveForward, final double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }
}
