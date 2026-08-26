package dev.client.api.nullcry.render.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.render.ColorUtils;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class Draw3DUtil implements ClientApi {
    private final Map<VoxelShape, Pair<List<Box>, List<Line>>> SHAPE_OUTLINES = new HashMap<>();
    private final Map<VoxelShape, List<Box>> SHAPE_BOXES = new HashMap<>();
    public final List<Line> LINE_DEPTH = new ArrayList<>();
    public final List<Line> LINE = new ArrayList<>();
    public final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public final List<Quad> QUAD = new ArrayList<>();
    @Setter public Matrix4f lastProjMat = new Matrix4f();
    @Setter public MatrixStack.Entry lastWorldSpaceMatrix = new MatrixStack().peek();
    Tessellator tessellator = Tessellator.getInstance();

    public void onDraw3D(RenderEvent.Draw3D event) {
        if (!LINE.isEmpty()) {
            Set<Float> widths = LINE.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderLayer layer = RenderLayers.linesTranslucent();
            widths.forEach(width -> {
                BufferBuilder buffer = RenderCoreCompat.begin(layer);
                LINE.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd, width));
                RenderCoreCompat.draw(layer, buffer.end());
            });
            LINE.clear();
        }
        if (!QUAD.isEmpty()) {
            RenderLayer layer = RenderLayers.debugQuads();
            BufferBuilder buffer = RenderCoreCompat.begin(layer);
            QUAD.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            RenderCoreCompat.draw(layer, buffer.end());
            QUAD.clear();
        }
        if (!LINE_DEPTH.isEmpty()) {
            Set<Float> widths = LINE_DEPTH.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderLayer layer = RenderLayers.lines();
            widths.forEach(width -> {
                BufferBuilder buffer = RenderCoreCompat.begin(layer);
                LINE_DEPTH.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd, width));
                RenderCoreCompat.draw(layer, buffer.end());
            });
            LINE_DEPTH.clear();
        }
        if (!QUAD_DEPTH.isEmpty()) {
            RenderLayer layer = RenderLayers.debugQuads();
            BufferBuilder buffer = RenderCoreCompat.begin(layer);
            QUAD_DEPTH.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            RenderCoreCompat.draw(layer, buffer.end());
            QUAD_DEPTH.clear();
        }
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width) {
        drawShape(blockPos, voxelShape, color, width, true, false);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        if (SHAPE_BOXES.containsKey(voxelShape)) {
            SHAPE_BOXES.get(voxelShape).forEach(box -> {
                box = box.offset(blockPos);
                if (ProjectionUtil.canSee(box)) drawBox(box, color, width, true, fill, depth);
            });
            return;
        }
        SHAPE_BOXES.put(voxelShape, voxelShape.getBoundingBoxes());
    }

    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        Vec3d vec3d = Vec3d.of(blockPos);
        if (!ProjectionUtil.canSee(new Box(blockPos))) return;

        Pair<List<Box>, List<Line>> pair = SHAPE_OUTLINES.get(voxelShape);
        if (pair == null) {
            List<Line> lines = new ArrayList<>();
            voxelShape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) ->
                    lines.add(new Line(null, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), 0, 0, 0))
            );
            pair = new Pair<>(voxelShape.getBoundingBoxes(), lines);
            SHAPE_OUTLINES.put(voxelShape, pair);
        }
        if (fill) pair.getLeft().forEach(box -> drawBox(box.offset(vec3d), color, width, false, true, depth));
        pair.getRight().forEach(line -> drawLine(line.start.add(vec3d), line.end.add(vec3d), color, width, depth));
    }

    public void drawBox(Box box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public void drawBox(Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, depth) ;
    }

    public void drawBox(MatrixStack.Entry entry, Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        box = box.expand(1e-3);

        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        if (fill) {
            int fillColor = ColorUtils.multAlpha(color, 0.1f);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y1, z1), fillColor, depth);
            drawQuad(entry, new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), new Vec3d(x2, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z2), new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y2, z1), new Vec3d(x1, y2, z2), new Vec3d(x2, y2, z2), new Vec3d(x2, y2, z1), fillColor, depth);
        }

        if (line) {
            drawLine(entry, x1, y1, z1, x2, y1, z1, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y1, z2, color, width, depth);
            drawLine(entry, x2, y1, z2, x1, y1, z2, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y1, z1, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y1, z1, x1, y2, z1, color, width, depth);
            drawLine(entry, x2, y1, z2, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x1, y2, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x2, y2, z1, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y2, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y2, z2, x1, y2, z1, color, width, depth);
        }
    }

    public @NotNull Vector3f getNormal(Vector3f start, Vector3f end) {
        Vector3f normal = new Vector3f(start).sub(end);
        float sqrt = MathHelper.sqrt(normal.lengthSquared());
        return normal.div(sqrt);
    }

    public void vertexQuad(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d vec1, Vec3d vec2, Vec3d vec3, Vec3d vec4, int color) {
        vertexQuad(entry, buffer, vec1.toVector3f(), vec2.toVector3f(), vec3.toVector3f(), vec4.toVector3f(), color);
    }

    public void vertexLine(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor) {
        vertexLine(entry, buffer, start, end, startColor, endColor, 1.0f);
    }

    public void vertexLine(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor, float width) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        Vector3f vec = getNormal(start, end);
        buffer.vertex(entry, start).color(startColor).normal(entry, vec).lineWidth(width);
        buffer.vertex(entry, end).color(endColor).normal(entry, vec).lineWidth(width);
    }

    public void vertexQuad(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f vec1, Vector3f vec2, Vector3f vec3, Vector3f vec4, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        buffer.vertex(entry, vec1).color(color);
        buffer.vertex(entry, vec2).color(color);
        buffer.vertex(entry, vec3).color(color);
        buffer.vertex(entry, vec4).color(color);
    }

    public void drawLine(MatrixStack.Entry entry, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(entry, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), color, color, width, depth);
    }

    public void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLine(null, start, end, color, color, width, depth);
    }

    public void drawLine(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        Line line = new Line(entry, start, end, colorStart, colorEnd, width);
        if (depth) LINE_DEPTH.add(line); else LINE.add(line);
    }

    public void drawQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        Quad quad = new Quad(entry, x, y, w, z, color);
        if (depth) QUAD_DEPTH.add(quad); else QUAD.add(quad);
    }

    public record Line(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width) {}
    public record Quad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color) {}
}
