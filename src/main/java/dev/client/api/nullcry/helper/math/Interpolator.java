package dev.client.api.nullcry.helper.math;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;

@UtilityClass
public class Interpolator {

    public static Vector3d interpolate(Entity entity, float partialTicks) {
        double posX = linear(entity.lastRenderX, entity.getX(), partialTicks);
        double posY = linear(entity.lastRenderY, entity.getY(), partialTicks);
        double posZ = linear(entity.lastRenderZ, entity.getZ(), partialTicks);
        return new Vector3d(posX, posY, posZ);
    }

    public float linear(float input, float target, double step) {
        return (float) (input + step * (target - input));
    }

    public double linear(double input, double target, double step) {
        return input + step * (target - input);
    }

    public int linear(int input, int target, double step) {
        return (int) (input + step * (target - input));
    }
}