package dev.client.api.nullcry.render.core;

import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public final class GuiQuadData {
    private static final float PARAM_SCALE = 16.0f;
    private static final int PARAM_MAX = 0x3FFF;

    private GuiQuadData() {
    }

    public static void vertex(
            BufferBuilder builder,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            int color,
            float localX,
            float localY,
            float paramX,
            float paramY
    ) {
        builder.vertex(matrix, x, y, z)
                .texture(u, v)
                .color(color)
                .light(pack(localX, paramX), pack(localY, paramY));
    }

    public static float maxRadius(QuadRadiusState radius) {
        if (radius == null) {
            return 0.0f;
        }

        return Math.max(0.0f, Math.max(
                Math.max(radius.radius1(), radius.radius2()),
                Math.max(radius.radius3(), radius.radius4())
        ));
    }

    private static int pack(float coord, float parameter) {
        int coordBit = coord >= 0.5f ? 1 : 0;
        int value = Math.round(clamp(parameter, 0.0f, (float) PARAM_MAX / PARAM_SCALE) * PARAM_SCALE);
        value = Math.min(PARAM_MAX, Math.max(0, value));
        return (value << 1) | coordBit;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
