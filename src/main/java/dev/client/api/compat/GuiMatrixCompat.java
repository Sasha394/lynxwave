package dev.client.api.compat;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

public final class GuiMatrixCompat {
    private GuiMatrixCompat() {
    }

    public static Matrix4f toMatrix4(DrawContext context) {
        return new Matrix4f().mul(context.getMatrices());
    }
}
