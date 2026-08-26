package dev.client.modules.core.combat.killaura.rotation.angle;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class AngleSmoothMode implements ClientApi {
    String name;

    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle) {
        return limitAngleChange(currentAngle, targetAngle, null, null);
    }

    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d) {
        return limitAngleChange(currentAngle, targetAngle, vec3d, null);
    }

    public abstract Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity);

    public abstract Vec3d randomValue();
}