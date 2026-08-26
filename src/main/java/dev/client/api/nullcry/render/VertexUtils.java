package dev.client.api.nullcry.render;

import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class VertexUtils {

    public static final RenderLayer IMAGE = RenderLayers.entityTranslucentEmissiveNoOutline(Identifier.of("lynxwave", "particle/bloom.png"));
    public static final RenderLayer LINES = RenderLayers.linesTranslucent();


    public static void drawImageQuad(VertexConsumer vertices, Matrix4f matrix, float posX, float posY, float posZ, float halfSize, int color) {
        vertices.vertex(matrix, posX - halfSize, posY - halfSize, posZ).texture(0, 1).color(color);
        vertices.vertex(matrix, posX - halfSize, posY + halfSize, posZ).texture(0, 0).color(color);
        vertices.vertex(matrix, posX + halfSize, posY + halfSize, posZ).texture(1, 0).color(color);
        vertices.vertex(matrix, posX + halfSize, posY - halfSize, posZ).texture(1, 1).color(color);
    }

    public static void drawImageQuad2(VertexConsumer vertices, Matrix4f matrix, float posX, float posY, float posZ, float halfSize, int color) {
        vertices.vertex(matrix, posX - halfSize, posY - halfSize, posZ).texture(0f, 0f).color(color);
        vertices.vertex(matrix, posX - halfSize, posY + halfSize, posZ).texture(0f, 1f).color(color);
        vertices.vertex(matrix, posX + halfSize, posY + halfSize, posZ).texture(1f, 1f).color(color);
        vertices.vertex(matrix, posX + halfSize, posY - halfSize, posZ).texture(1f, 0f).color(color);
    }
}
