package dev.client.api.nullcry.helper.entity;

import dev.client.modules.core.render.Trails;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public interface ITrailEntity {
    List<Trails.Trail> lumora$getTrails();
    Vec3d lumora$getLastTrailPos();
    void lumora$setLastTrailPos(Vec3d pos);
}
