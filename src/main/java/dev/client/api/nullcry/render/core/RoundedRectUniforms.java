package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import org.lwjgl.system.MemoryStack;

public final class RoundedRectUniforms {
    public static final String ROUNDED_CONFIG = "RoundedRectConfig";
    public static final String OUTLINE_CONFIG = "OutlineConfig";
    public static final String EFFECT_CONFIG = "EffectConfig";

    private static final int ROUNDED_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();
    private static final int OUTLINE_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();
    private static final int EFFECT_SIZE = OUTLINE_SIZE;

    private RoundedRectUniforms() {
    }

    public static GpuBuffer rounded(float width, float height, QuadRadiusState radius, float smoothness) {
        QuadRadiusState resolvedRadius = radius != null ? radius : QuadRadiusState.NO_ROUND;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, ROUNDED_SIZE)
                    .putVec4(width, height, smoothness, 0.0f)
                    .putVec4(
                            resolvedRadius.radius1(),
                            resolvedRadius.radius2(),
                            resolvedRadius.radius3(),
                            resolvedRadius.radius4()
                    );
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave rounded rect config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    public static GpuBuffer outline(float width, float height, QuadRadiusState radius, float thickness, float internalSmoothness, float externalSmoothness) {
        QuadRadiusState resolvedRadius = radius != null ? radius : QuadRadiusState.NO_ROUND;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, OUTLINE_SIZE)
                    .putVec4(width, height, 0.0f, 0.0f)
                    .putVec4(
                            resolvedRadius.radius1(),
                            resolvedRadius.radius2(),
                            resolvedRadius.radius3(),
                            resolvedRadius.radius4()
                    )
                    .putVec4(thickness, internalSmoothness, externalSmoothness, 0.0f);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave outline config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    public static GpuBuffer effect(float width, float height, QuadRadiusState radius, float softness, float effectRadius) {
        QuadRadiusState resolvedRadius = radius != null ? radius : QuadRadiusState.NO_ROUND;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, EFFECT_SIZE)
                    .putVec4(width, height, softness, effectRadius)
                    .putVec4(
                            resolvedRadius.radius1(),
                            resolvedRadius.radius2(),
                            resolvedRadius.radius3(),
                            resolvedRadius.radius4()
                    )
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f);
            return RenderSystem.getDevice().createBuffer(() -> "lynxwave rounded effect config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }
}
