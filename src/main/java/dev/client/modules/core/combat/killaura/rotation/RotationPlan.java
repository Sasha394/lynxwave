package dev.client.modules.core.combat.killaura.rotation;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.angle.AngleSmoothMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Setter
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RotationPlan implements ClientApi {
    Angle angle;
    Vec3d vec3d;
    Entity entity;
    AngleSmoothMode angleSmooth;
    int ticksUntilReset;
    float resetThreshold;
    boolean moveCorrection, freeCorrection;

    public Angle nextRotation(Angle fromAngle, boolean isResetting) {
        if (isResetting) {
            return angleSmooth.limitAngleChange(fromAngle, AngleUtil.fromVec2f(mc.player.getRotationClient()));
        }
        return angleSmooth.limitAngleChange(fromAngle, angle, vec3d, entity);
    }
}