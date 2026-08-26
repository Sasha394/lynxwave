package dev.client.modules.core.combat.killaura.rotation;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.math.MathUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class RotationUtil implements ClientApi {

    public float shortenAngle(float from, float to) {
        return (((from - to) % 360) + 540) % 360 - 180;
    }

    public float shortenAngle(float degree) {
        return shortenAngle(mc.player.getYaw(), degree);
    }

    public Angle calculateAngle(Vec3d target) {
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, dist)), -89, 89);

        return applyGCD(yaw, pitch);
    }

    public Angle applyGCD(float yaw, float pitch) {
        double gcd = MathUtil.computeGcd();
        Angle prev = RotationController.INSTANCE.getServerAngle();

        float adjYaw = prev.getYaw() + Math.round((yaw - prev.getYaw()) / gcd) * (float) gcd;
        float adjPitch = prev.getPitch() + Math.round((pitch - prev.getPitch()) / gcd) * (float) gcd;

        return new Angle(adjYaw, MathHelper.clamp(adjPitch, -90f, 90f));
    }

    public float getGCD() {
        double sens = mc.options.getMouseSensitivity().getValue();
        double d = sens * 0.6 + 0.2;
        return (float) (d * d * d * 8.0 * 0.15);
    }

    public float sensitivityFactor(float value, float min, float max) {
        float gcd = getGCD();
        float adjusted = value - value % gcd;
        return MathHelper.clamp(adjusted, min, max);
    }
}
