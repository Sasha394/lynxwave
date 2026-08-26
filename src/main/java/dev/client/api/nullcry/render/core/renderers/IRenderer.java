package dev.client.api.nullcry.render.core.renderers;

import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

public interface IRenderer {
    Matrix4f DEFAULT_MATRIX = new Matrix4f();

    default void render(double x, double y) {
        this.render((float) x, (float) y);
    }

    default void render(float x, float y) {
        this.render(DEFAULT_MATRIX, x, y);
    }

    default void render(Matrix4f matrix, double x, double y) {
        this.render(matrix, (float) x, (float) y);
    }

    default void render(Matrix4f matrix, float x, float y) {
        this.render(matrix, x, y, 0.0f);
    }

    default void render(double x, double y, double z) {
        this.render((float) x, (float) y, (float) z);
    }

    default void render(float x, float y, float z) {
        this.render(DEFAULT_MATRIX, x, y, z);
    }

    default void render(Matrix4f matrix, double x, double y, double z) {
        this.render(matrix, (float) x, (float) y, (float) z);
    }

    default void quad(BufferBuilder builder, Matrix4f matrix4f, float x, float y, float z, float width, float height, QuadColorState color) {
        builder.vertex(matrix4f, x, y, z).color(color.color1());
        builder.vertex(matrix4f, x, y + height, z).color(color.color2());
        builder.vertex(matrix4f, x + width, y + height, z).color(color.color3());
        builder.vertex(matrix4f, x + width, y, z).color(color.color4());
    }

    void render(Matrix4f matrix, float x, float y, float z);

}