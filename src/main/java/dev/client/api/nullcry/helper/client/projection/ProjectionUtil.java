package dev.client.api.nullcry.helper.client.projection;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProjectionUtil implements ClientApi {
    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

    public static Vec3d projectCoordinates(Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return new Vec3d(0, 0, 0);
        int displayHeight = mc.getWindow().getHeight();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Vector3f target = new Vector3f();

        double deltaX = pos.x - camera.getCameraPos().x;
        double deltaY = pos.y - camera.getCameraPos().y;
        double deltaZ = pos.z - camera.getCameraPos().z;

        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.f).mul(lastWorldSpaceMatrix);
        Matrix4f matrixProj = new Matrix4f(lastProjMat);
        Matrix4f matrixModel = new Matrix4f(lastModMat);
        matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);

        return new Vec3d(target.x / mc.getWindow().getScaleFactor(), (displayHeight - target.y) / mc.getWindow().getScaleFactor(), target.z);
    }

    public static boolean canSee(Vec3d vec3d) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Angle angle = AngleUtil.calculateAngle(vec3d);
        return (Math.abs(MathHelper.wrapDegrees(angle.getYaw() - camera.getYaw())) < 90 && Math.abs(MathHelper.wrapDegrees(angle.getPitch() - camera.getPitch())) < 60) || canSee(new Box(BlockPos.ofFloored(vec3d)));
    }

    public static boolean canSee(Box box) {
        Frustum frustum = mc.worldRenderer.getCapturedFrustum();
        return box != null && frustum != null && frustum.isVisible(box);
    }

    public boolean cantSee(Vector4d vec) {
        return vec == null || (vec.x < 0 && vec.z < 1) || (vec.y < 0 && vec.w < 1);
    }
}
