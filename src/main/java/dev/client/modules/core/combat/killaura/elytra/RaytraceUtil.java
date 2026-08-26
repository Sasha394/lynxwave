package dev.client.modules.core.combat.killaura.elytra;

import dev.client.api.nullcry.ClientApi;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Objects;

@UtilityClass
public class RaytraceUtil implements ClientApi {
    public BlockHitResult rayCast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }

    public boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc.player).getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec,cameraVec.add(clientVec.multiply(range))).isPresent();
    }
}