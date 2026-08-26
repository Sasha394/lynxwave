package dev.client.api.nullcry;

import dev.other.vector.Vec2i;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2f;

public interface IScreen {
    default Vec2i getMouseInteger(int mouseX, int mouseY) {
        return new Vec2i((int) (mouseX * MinecraftClient.getInstance().getWindow().getScaleFactor() / 2), (int) (mouseY * MinecraftClient.getInstance().getWindow().getScaleFactor() / 2));
    }

    static Vector2f getMouseDouble(double mouseX, double mouseY) {
        return new Vector2f((float) (mouseX * MinecraftClient.getInstance().getWindow().getScaleFactor() / 2), (float) (mouseY * MinecraftClient.getInstance().getWindow().getScaleFactor() / 2));
    }
}