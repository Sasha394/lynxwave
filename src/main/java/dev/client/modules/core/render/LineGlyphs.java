package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Line Glyphs — падающие зигзагообразные линии в мире (как матрица).
 */
public class LineGlyphs extends Module implements ClientApi {

    public static LineGlyphs INSTANCE;

    private final CheckBox glowing = new CheckBox("Свечение", () -> true).defaultValue(true).register(this);
    private final CheckBox dashed = new CheckBox("Пунктир", () -> true).defaultValue(false).register(this);
    private final ModeElement colorMode = new ModeElement("Режим цвета", () -> true).set("Клиент", "Один цвет", "Два цвета").defaultValue("Клиент").register(this);
    private final ColorPicker pickColor1 = new ColorPicker("Цвет 1", () -> colorMode.isSelected("Один цвет") || colorMode.isSelected("Два цвета")).set(new java.awt.Color(100, 200, 255).getRGB()).defaultValue(new java.awt.Color(100, 200, 255).getRGB()).register(this);
    private final ColorPicker pickColor2 = new ColorPicker("Цвет 2", () -> colorMode.isSelected("Два цвета")).set(new java.awt.Color(255, 100, 200).getRGB()).defaultValue(new java.awt.Color(255, 100, 200).getRGB()).register(this);

    private final List<FallingLine> lines = new ArrayList<>();
    private final Random random = new Random();

    private static final int LINE_COUNT = 120;
    private static final float FALL_SPEED = 0.07f;
    private static final float LINE_LENGTH = 17f;
    private static final float SEGMENT_LENGTH = 2f;
    private static final float ZIGZAG_WIDTH = 5f;
    private static final double DASH_LENGTH = 0.15;
    private static final double GAP_LENGTH = 0.1;

    public LineGlyphs() {
        super("LineGlyphs", ModuleCategory.Render, "Падающие зигзагообразные линии в мире");
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        generateLines();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        lines.clear();
    }

    private void generateLines() {
        lines.clear();
        if (mc.player == null) return;
        for (int i = 0; i < LINE_COUNT; i++) {
            lines.add(new FallingLine(random, dev.client.api.compat.EntityCompat.pos(mc.player)));
        }
    }

    @Subscribe
    public void onDraw3D(RenderEvent.Draw3D event) {
        if (mc.player == null) return;

        int linesToSpawn = LINE_COUNT - lines.size();
        Iterator<FallingLine> iterator = lines.iterator();
        while (iterator.hasNext()) {
            FallingLine line = iterator.next();
            line.update(dev.client.api.compat.EntityCompat.pos(mc.player), FALL_SPEED, SEGMENT_LENGTH, ZIGZAG_WIDTH, LINE_LENGTH);
            if (line.shouldRespawn(dev.client.api.compat.EntityCompat.pos(mc.player))) {
                iterator.remove();
                linesToSpawn++;
            }
        }
        for (int i = 0; i < linesToSpawn; i++) {
            lines.add(new FallingLine(random, dev.client.api.compat.EntityCompat.pos(mc.player)));
        }

        renderLines();
    }

    private void renderLines() {
        if (lines.isEmpty() || mc.gameRenderer == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            FallingLine line = lines.get(lineIndex);
            if (line.points.size() < 2) continue;

            int color = getColor(lineIndex);
            float width = glowing.getEnabled() ? 2.5f : 1.5f;

            for (int i = 0; i < line.points.size() - 1; i++) {
                Vec3d start = line.points.get(i);
                Vec3d end = line.points.get(i + 1);
                if (start.distanceTo(camPos) > 60 || end.distanceTo(camPos) > 60) continue;

                float segmentAlpha = (float) (i + 1) / line.points.size();
                int segmentColor = ColorUtils.setAlpha(color, (int) (segmentAlpha * 255));

                if (dashed.getEnabled()) {
                    drawDashedLine(start, end, segmentColor, width);
                    if (glowing.getEnabled()) {
                        int glowColor = ColorUtils.setAlpha(color, (int) (segmentAlpha * 100));
                        drawDashedLine(start, end, glowColor, 4f);
                    }
                } else {
                    Draw3DUtil.drawLine(start, end, segmentColor, width, true);
                    if (glowing.getEnabled()) {
                        int glowColor = ColorUtils.setAlpha(color, (int) (segmentAlpha * 100));
                        Draw3DUtil.drawLine(start, end, glowColor, 4f, true);
                    }
                }
            }

            renderTurnCircles(line, color, camPos);
        }
    }

    private void drawDashedLine(Vec3d start, Vec3d end, int color, float width) {
        Vec3d direction = end.subtract(start);
        double lineLength = direction.length();
        if (lineLength < 1e-6) return;
        Vec3d normalizedDir = direction.normalize();
        double totalPattern = DASH_LENGTH + GAP_LENGTH;
        double currentDistance = 0;
        while (currentDistance < lineLength) {
            double dashEnd = Math.min(currentDistance + DASH_LENGTH, lineLength);
            Vec3d dashStart = start.add(normalizedDir.multiply(currentDistance));
            Vec3d dashEndPos = start.add(normalizedDir.multiply(dashEnd));
            Draw3DUtil.drawLine(dashStart, dashEndPos, color, width, true);
            currentDistance += totalPattern;
        }
    }

    private void renderTurnCircles(FallingLine line, int color, Vec3d camPos) {
        if (line.turnPoints.isEmpty()) return;

        for (Vec3d turnPoint : line.turnPoints) {
            if (turnPoint.distanceTo(camPos) > 60) continue;

            float alpha = 1f;
            if (!line.points.isEmpty()) {
                Vec3d lastPoint = line.points.get(line.points.size() - 1);
                double distanceFromEnd = turnPoint.distanceTo(lastPoint);
                alpha = Math.max(0.3f, 1f - (float) (distanceFromEnd / LINE_LENGTH));
            }
            int particleColor = ColorUtils.setAlpha(color, (int) (alpha * 255));
            float size = 0.2f;
            Box box = new Box(
                    turnPoint.x - size / 2, turnPoint.y - size / 2, turnPoint.z - size / 2,
                    turnPoint.x + size / 2, turnPoint.y + size / 2, turnPoint.z + size / 2
            );
            Draw3DUtil.drawBox(box, particleColor, 1f, false, true, true);
        }
    }

    private int getColor(int index) {
        if (colorMode.isSelected("Клиент") && Interface.INSTANCE != null) return Interface.INSTANCE.getMainColor();
        if (colorMode.isSelected("Один цвет")) return pickColor1.getColorRGBA();
        if (colorMode.isSelected("Два цвета")) return ColorUtils.gradient(pickColor1.getColorRGBA(), pickColor2.getColorRGBA(), index, 10);
        return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFFFFF;
    }

    private static class FallingLine {
        final List<Vec3d> points = new ArrayList<>();
        final List<Vec3d> turnPoints = new ArrayList<>();
        Vec3d currentDirection;
        double distanceTraveled = 0;
        double currentSegmentLength;
        final Random random = new Random();

        FallingLine(Random random, Vec3d playerPos) {
            if (mc.gameRenderer == null || mc.player == null) {
                double range = 15;
                double x = playerPos.x + (random.nextDouble() - 0.5) * range;
                double y = playerPos.y + random.nextDouble() * 14;
                double z = playerPos.z + (random.nextDouble() - 0.5) * range;
                points.add(new Vec3d(x, y, z));
            } else {
                float yaw = mc.player.getYaw();
                float pitch = mc.player.getPitch();
                double yawRad = Math.toRadians(yaw);
                double pitchRad = Math.toRadians(pitch);
                double distance = 2 + random.nextDouble() * 6;
                double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
                double dirY = -Math.sin(pitchRad);
                double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
                Vec3d lookDir = new Vec3d(dirX, dirY, dirZ);
                Vec3d worldUp = new Vec3d(0, 1, 0);
                Vec3d right = lookDir.crossProduct(worldUp).normalize();
                Vec3d up = right.crossProduct(lookDir).normalize();
                double horizontalSpread = (random.nextDouble() - 0.5) * 24;
                double verticalSpread = (random.nextDouble() - 0.5) * 18;
                double x = playerPos.x + dirX * distance + right.x * horizontalSpread + up.x * verticalSpread;
                double y = playerPos.y + dirY * distance + right.y * horizontalSpread + up.y * verticalSpread;
                double z = playerPos.z + dirZ * distance + right.z * horizontalSpread + up.z * verticalSpread;
                points.add(new Vec3d(x, y, z));
            }
            currentDirection = getRandomDirection();
            currentSegmentLength = 0.5 + random.nextDouble() * 1.5;
        }

        void update(Vec3d playerPos, float fallSpeed, float segmentLengthSetting, float zigzagWidth, float maxLineLength) {
            if (points.isEmpty()) return;
            Vec3d lastPoint = points.get(points.size() - 1);
            Vec3d movement = currentDirection.multiply(fallSpeed);
            Vec3d newPoint = lastPoint.add(movement);
            points.add(newPoint);
            distanceTraveled += fallSpeed;

            if (distanceTraveled >= currentSegmentLength) {
                turnPoints.add(newPoint);
                makeRandomTurn(zigzagWidth);
                distanceTraveled = 0;
                currentSegmentLength = 0.5 + random.nextDouble() * 1.5;
            }

            double totalLength = calculateTotalLength();
            while (totalLength > maxLineLength && points.size() > 2) {
                Vec3d removedPoint = points.remove(0);
                turnPoints.removeIf(turnPoint -> turnPoint.equals(removedPoint));
                totalLength = calculateTotalLength();
            }

            if (!points.isEmpty()) {
                Vec3d firstPoint = points.get(0);
                turnPoints.removeIf(turnPoint ->
                        !points.stream().anyMatch(p -> p.distanceTo(turnPoint) < 0.1) && turnPoint.distanceTo(firstPoint) > maxLineLength
                );
            }
        }

        private double calculateTotalLength() {
            double length = 0;
            for (int i = 0; i < points.size() - 1; i++) {
                length += points.get(i).distanceTo(points.get(i + 1));
            }
            return length;
        }

        private void makeRandomTurn(float zigzagWidth) {
            currentDirection = getRandomDirection();
        }

        private Vec3d getRandomDirection() {
            return switch (random.nextInt(6)) {
                case 0 -> new Vec3d(1, 0, 0);
                case 1 -> new Vec3d(-1, 0, 0);
                case 2 -> new Vec3d(0, 1, 0);
                case 3 -> new Vec3d(0, -1, 0);
                case 4 -> new Vec3d(0, 0, 1);
                default -> new Vec3d(0, 0, -1);
            };
        }

        boolean shouldRespawn(Vec3d playerPos) {
            if (points.isEmpty()) return true;
            Vec3d lastPoint = points.get(points.size() - 1);
            if (lastPoint.distanceTo(playerPos) > 18) return true;
            if (mc.gameRenderer != null) {
                Camera camera = mc.gameRenderer.getCamera();
                Vec3d cameraPos = camera.getCameraPos();
                Vec3d cameraDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                Vec3d toLine = lastPoint.subtract(cameraPos).normalize();
                if (cameraDir.dotProduct(toLine) < 0.5) return true;
            }
            return false;
        }
    }
}
