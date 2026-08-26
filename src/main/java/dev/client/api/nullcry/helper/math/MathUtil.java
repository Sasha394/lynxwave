package dev.client.api.nullcry.helper.math;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3x2fStack;
import org.joml.Vector4f;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class MathUtil extends org.joml.Math implements ClientApi {

    public static float stick(float value, float nearest, float threshold) {
        return MathUtil.abs(value - nearest) <= threshold ? nearest : value;
    }

    public static double getTickDelta() {
        return mc.getRenderTickCounter().getTickProgress(false);
    }

    public static void scaleStart(MatrixStack matrices, float x, float y, float scale, Runnable data) {
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(-x, -y, 0);
        data.run();
        matrices.pop();
    }

    public static void scaleStart(MatrixStack matrices, float x, float y, float scale) {
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(- x, - y, 0);
    }

    public static void scaleEnd(MatrixStack matrices) {
        matrices.pop();
    }

    public static void scaleStart(Matrix3x2fStack matrices, float x, float y, float scale, Runnable data) {
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);
        data.run();
        matrices.popMatrix();
    }

    public static void scaleStart(Matrix3x2fStack matrices, float x, float y, float scale) {
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);
    }

    public static void scaleEnd(Matrix3x2fStack matrices) {
        matrices.popMatrix();
    }

    public static Vector4f calculateRotationFromCamera(LivingEntity target) {
        Vec3d vec = dev.client.api.compat.EntityCompat.pos(target).subtract(mc.player.getEyePos());
        float rawYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90F);
        float rawPitch = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.sqrt(Math.pow(vec.x, 2) + Math.pow(vec.z, 2)))));
        float yawDelta = MathHelper.wrapDegrees(rawYaw - mc.player.getYaw());
        float pitchDelta = rawPitch - mc.player.getPitch();
        return new Vector4f(rawYaw, rawPitch, yawDelta, pitchDelta);
    }

    public static double calculateFOVFromCamera(LivingEntity target) {
        Vector4f rotation = calculateRotationFromCamera(target);
        float yawDelta = rotation.z;
        float pitchDelta = rotation.w;
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }

    public double computeGcd() {
        return (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;
    }

    // 1.16.5

    public Vec3d getVector(LivingEntity target) {
        double wHalf = target.getWidth() / 2;
        double yExpand = clamp(target.getEyeY() - target.getY(), 0, target.getHeight());
        double xExpand = clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }

    public Vec2f rotationToEntity(Entity target) {
        Vec3d vector3d = dev.client.api.compat.EntityCompat.pos(target).subtract(dev.client.api.compat.EntityCompat.pos(MinecraftClient.getInstance().player));
        double magnitude = Math.hypot(vector3d.x, vector3d.z);
        return new Vec2f((float) Math.toDegrees(Math.atan2(vector3d.z, vector3d.x)) - 90.0F, (float) (-Math.toDegrees(Math.atan2(vector3d.y, magnitude))));
    }

    public double round(double num, double increment) {
        double v = (double) Math.round(num / increment) * increment;
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public double getDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double deltaX = calculateDiff(x2, x1);
        double deltaY = calculateDiff(y2, y1);
        double deltaZ = calculateDiff(z2, z1);
        return MathHelper.sqrt((float) (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }

    public double getDistance(BlockPos pos1, BlockPos pos2) {
        double deltaX = calculateDiff(pos1.getX(), pos2.getX());
        double deltaY = calculateDiff(pos1.getY(), pos2.getY());
        double deltaZ = calculateDiff(pos1.getZ(), pos2.getZ());
        return MathHelper.sqrt((float) (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }

    public boolean canSeen(Vec3d targetPos) {
        Vec3d start = dev.client.api.compat.EntityCompat.pos(mc.player).add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
        RaycastContext context = new RaycastContext(start, targetPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(context);
        return result.getType() == HitResult.Type.MISS;
    }

    public Vec3d fast(Vec3d end, Vec3d start, float multiple) {
        return new Vec3d(fast((float) end.getX(), (float) start.getX(), multiple), fast((float) end.getY(), (float) start.getY(), multiple), fast((float) end.getZ(), (float) start.getZ(), multiple));
    }

    public Vec3d interpolate(Vec3d end, Vec3d start, float multiple) {
        return new Vec3d(interpolate(end.getX(), start.getX(), multiple), interpolate(end.getY(), start.getY(), multiple), interpolate(end.getZ(), start.getZ(), multiple));
    }

    public float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public float linear(float end, float start, float multiple) {
        return (float) (end + (start - end) * MathHelper.clamp(deltaTime() * multiple, 0, 1));
    }

    public float random(float min, float max) {
        return (float) getRandom((double) min, (double) max);
    }

    public int getRandom(int min, int max) {
        return (int) getRandom((double) min, (double) max + 1);
    }

    public float getRandom(float min, float max) {
        return (float) getRandom((double) min, (double) max);
    }

    public double getRandom(double min, double max) {
        if (min == max) {
            return min;
        }

        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }

        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double linear(double end, double start, double multiple) {
        return (end + (start - end) * MathHelper.clamp(deltaTime() * multiple, 0, 1));
    }

    public float interpolate(float current, float old, float scale) {
        return old + (current - old) * scale;
    }

    public double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    public double step(double value, double steps) {
        double roundedValue = Math.round(value / steps) * steps;
        return Math.round(roundedValue * 100.0) / 100.0;
    }

    public float step(float current, float target, float step) {
        float difference = target - current;

        if (Math.abs(difference) <= step) {
            return target;
        }

        return current + Math.signum(difference) * step;
    }


    public int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public double deltaTime() {
        return MinecraftClient.getInstance().getCurrentFps() > 0 ? (1.0000 / MinecraftClient.getInstance().getCurrentFps()) : 1;
    }

    public float fast(float end, float start, float multiple) {
        return (1 - MathHelper.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathHelper.clamp((float) (deltaTime() * multiple), 0, 1) * start;
    }

    public float calculatePosition(float x, float width) {
        return x - width / 2f;
    }

    public double calculateDiff(double a, double b) {
        return a - b;
    }

    public float calculateDelta(float a, float b) {
        return a - b;
    }

    public int randomInt(int min, int max) {
        return (int) getRandom((double) min, (double) max);
    }
}
