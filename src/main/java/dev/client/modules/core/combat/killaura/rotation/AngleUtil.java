package dev.client.modules.core.combat.killaura.rotation;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import static java.lang.Math.hypot;
import static java.lang.Math.toDegrees;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@UtilityClass
public class AngleUtil implements ClientApi {
    public Angle fromVec2f(Vec2f vector2f) {
        return new Angle(vector2f.y, vector2f.x);
    }

    public Angle fromVec3d(Vec3d vector) {
        return new Angle((float) wrapDegrees(toDegrees(Math.atan2(vector.z, vector.x)) - 90), (float) wrapDegrees(toDegrees(-Math.atan2(vector.y, hypot(vector.x, vector.z)))));
    }

    public Angle calculateDelta(Angle start, Angle end) {
        float deltaYaw = MathHelper.wrapDegrees(end.getYaw() - start.getYaw());
        float deltaPitch = MathHelper.wrapDegrees(end.getPitch() - start.getPitch());
        return new Angle(deltaYaw, deltaPitch);
    }

    public Angle calculateAngle(Vec3d to) {
        if (mc.player == null) {
            return Angle.DEFAULT;
        }
        return fromVec3d(to.subtract(mc.player.getEyePos()));
    }

    public Angle pitch(float pitch) {
        return mc.player != null ? new Angle(mc.player.getYaw(), pitch) : new Angle(Angle.DEFAULT.getYaw(), pitch);
    }

    public Angle cameraAngle() {
        return mc.player != null ? new Angle(mc.player.getYaw(), mc.player.getPitch()) : Angle.DEFAULT;
    }

}
