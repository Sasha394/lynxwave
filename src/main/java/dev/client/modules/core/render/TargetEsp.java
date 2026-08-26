package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderCompat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.LynxEspPipelines;
import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.OutBack;
import dev.client.modules.core.combat.KillAura;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class TargetEsp extends Module {

    private final Animation espAnim = new OutBack(300, 1);

    private static final Identifier PENTAGRAM_CIRCLE_1_TEXTURE = Identifier.of("lynxwave", "images/targetesp/circles1.png");
    private static final Identifier PENTAGRAM_CIRCLE_2_TEXTURE = Identifier.of("lynxwave", "images/targetesp/circles2.png");

    private final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Ромб", "Призрак", "Цепь", "Кристаллы", "Круг", "Пентаграмма")
            .defaultValue("Ромб").register(this);

    private final Slider crystalRotationSpeed = new Slider("Скорость вращения кристаллов",
            () -> mode.isSelected("Кристаллы"))
            .set(0.1f, 2.0f, 0.1f).defaultValue(0.5f).register(this);

    private final Slider pentagramRotateSpeed = new Slider("Скорость пентаграммы",
            () -> mode.isSelected("Пентаграмма"))
            .set(0.1f, 5.0f, 0.1f).defaultValue(1.0f).register(this);

    private final ModeElement colorMode = new ModeElement("Цвет", () -> true)
            .set("Клиент", "Свой").defaultValue("Клиент").register(this);

    private final ColorPicker color1 = new ColorPicker("Цвет 1", () -> colorMode.isSelected("Свой"))
            .set(new Color(255, 255, 255, 255).getRGB()).defaultValue(new Color(255, 255, 255, 255).getRGB()).register(this);

    private final ColorPicker color2 = new ColorPicker("Цвет 2", () -> colorMode.isSelected("Свой"))
            .set(new Color(0, 60, 255, 255).getRGB()).defaultValue(new Color(0, 60, 255, 255).getRGB()).register(this);

    private final ColorPicker color3 = new ColorPicker("Цвет 3", () -> colorMode.isSelected("Свой") && mode.isSelected("Призрак"))
            .set(new Color(255, 0, 0, 255).getRGB()).defaultValue(new Color(255, 0, 0, 255).getRGB()).register(this);

    private int col1() { return colorMode.isSelected("Клиент") && Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor(0) : color1.getColorRGBA(); }
    private int col2() { return colorMode.isSelected("Клиент") && Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor(3) : color2.getColorRGBA(); }
    private int col3() { return colorMode.isSelected("Клиент") && Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor(6) : color3.getColorRGBA(); }

    private Vec3d smoothedPos = null;
    private LivingEntity lastTarget = null;
    private float movingValue = 0;
    private float hurtProgress = 0;

    private Entity lastRenderedTarget = null;
    private final List<Crystal> crystalList = new ArrayList<>();
    private float rotationAngle = 0;

    private float circleStep = 0;
    private double smoothY = 0;
    private double smoothY2 = 0;

    private long lastFrameTime = System.currentTimeMillis();
    private static final float TARGET_FRAME_TIME = 1000f / 60f;

    public TargetEsp() {
        super("Target ESP", ModuleCategory.Render, "Красиво выделяет цель");
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaMs = currentTime - lastFrameTime;
        lastFrameTime = currentTime;
        deltaMs = Math.max(1f, Math.min(deltaMs, 100f));
        return deltaMs / TARGET_FRAME_TIME;
    }

    private LivingEntity getTarget() {
        return KillAura.INSTANCE != null ? KillAura.INSTANCE.getTarget() : null;
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        float deltaTime = getDeltaTime();

        LivingEntity target = getTarget();

        if (target == null) {
            smoothedPos = null;
            lastTarget = null;
            espAnim.setDirection(Direction.BACKWARDS);
            smoothY = 0;
            smoothY2 = 0;
            return;
        }

        espAnim.setDirection(Direction.FORWARDS);
        float alpha = espAnim.getOutput().floatValue();
        if (alpha <= 0.01f) return;

        movingValue += 2f * deltaTime;
        if (movingValue > 360000) movingValue = 0;

        float hurtDecay = 0.1f * deltaTime;
        hurtProgress = target.hurtTime > 0 ? (float) target.hurtTime / 10f : Math.max(0, hurtProgress - hurtDecay);

        circleStep += 0.06f * deltaTime;

        if (mode.isSelected("Круг")) {
            renderCircle(event.getMatrices(), target, alpha, event.getTickCounter().getTickProgress(true));
            return;
        }

        if (mode.isSelected("Пентаграмма")) {
            renderPentagram(event.getMatrices(), target, alpha, event.getTickCounter().getTickProgress(true));
            return;
        }

        MatrixStack stack = event.getMatrices();
        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = event.getTickCounter().getTickProgress(true);
        Vec3d targetPos = target.getLerpedPos(partialTicks);

        if (lastTarget != target || smoothedPos == null) {
            smoothedPos = targetPos;
            lastTarget = target;
        } else {
            float smoothingFactor = Math.min(1.0f, partialTicks * 1.5f);
            double dx = targetPos.x - smoothedPos.x;
            double dy = targetPos.y - smoothedPos.y;
            double dz = targetPos.z - smoothedPos.z;
            smoothedPos = new Vec3d(
                    smoothedPos.x + dx * smoothingFactor,
                    smoothedPos.y + dy * smoothingFactor,
                    smoothedPos.z + dz * smoothingFactor);
        }

        stack.push();
        stack.translate(
                smoothedPos.x - camPos.x,
                smoothedPos.y - camPos.y,
                smoothedPos.z - camPos.z);

        if (mode.isSelected("Ромб")) {
            renderRhomb(stack, provider, target, alpha);
        } else if (mode.isSelected("Призрак")) {
            renderGhost(stack, provider, target, alpha);
        } else if (mode.isSelected("Цепь")) {
            renderChain(stack, provider, target, alpha);
        } else if (mode.isSelected("Кристаллы")) {
            if (crystalList.isEmpty() || lastRenderedTarget != target) {
                createCrystals(target);
                lastRenderedTarget = target;
            }
            renderCrystals(stack, provider, target, alpha, deltaTime);
        }

        provider.draw();
        stack.pop();
    }

    // ───────────────────────── Pentagram ─────────────────────────
    private void renderPentagram(MatrixStack stack, LivingEntity target, float alpha, float partialTicks) {
        if (target == null || !target.isAlive()) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d t = target.getLerpedPos(partialTicks);
        double x = t.x;
        double y = t.y + 0.01;
        double z = t.z;

        float speed = Math.max(0.1f, pentagramRotateSpeed.getValue());
        float ticks = mc.player != null ? mc.player.age + partialTicks : partialTicks;
        float rotation = ticks * 18.0f * speed;
        float counterRotation = -rotation;
        float pulse = 1.0f + 0.06f * MathHelper.sin(ticks * 0.12f);
        float scale = (0.82f + target.getWidth() * 0.55f) * alpha * pulse;

        int baseColor1 = col1();
        int baseColor2 = col2();
        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }

        VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();

        renderPentagramGlow(stack, provider, camPos, x, y, z, rotation, scale * 1.60f, alpha * 1.05f, PENTAGRAM_CIRCLE_1_TEXTURE, baseColor1, baseColor2);
        renderPentagramGlow(stack, provider, camPos, x, y, z, counterRotation, scale * 1.98f, alpha * 0.95f, PENTAGRAM_CIRCLE_2_TEXTURE, baseColor1, baseColor2);
        renderPentagramLayer(stack, provider, camPos, x, y, z, PENTAGRAM_CIRCLE_1_TEXTURE, rotation, scale * 0.80f, alpha, baseColor1, baseColor2);
        renderPentagramLayer(stack, provider, camPos, x, y, z, PENTAGRAM_CIRCLE_2_TEXTURE, counterRotation, scale * 0.99f, alpha, baseColor1, baseColor2);

        provider.draw();
    }

    private void renderPentagramLayer(MatrixStack stack, VertexConsumerProvider provider, Vec3d camPos,
                                      double x, double y, double z, Identifier texture,
                                      float rotation, float scale, float alpha, int color1, int color2) {
        if (scale <= 0.01f || alpha <= 0.01f) return;

        VertexConsumer consumer = provider.getBuffer(LynxEspPipelines.PENTAGRAM_ESP.apply(texture));

        int c0 = pentagramColor(0, alpha, color1, color2);
        int c90 = pentagramColor(90, alpha, color1, color2);
        int c180 = pentagramColor(180, alpha, color1, color2);
        int c270 = pentagramColor(270, alpha, color1, color2);

        stack.push();
        stack.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        stack.scale(scale, scale, scale);

        Matrix4f m = stack.peek().getPositionMatrix();
        consumer.vertex(m, -1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f).color(c270);
        consumer.vertex(m, 1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f).color(c0);
        consumer.vertex(m, 1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f).color(c180);
        consumer.vertex(m, -1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f).color(c90);
        stack.pop();
    }

    private void renderPentagramGlow(MatrixStack stack, VertexConsumerProvider provider, Vec3d camPos,
                                     double x, double y, double z, float rotation,
                                     float radius, float alpha, Identifier texture, int color1, int color2) {
        if (alpha <= 0.01f || radius <= 0.01f) return;

        VertexConsumer consumer = provider.getBuffer(LynxEspPipelines.PENTAGRAM_ESP.apply(texture));

        stack.push();
        stack.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        Matrix4f m = stack.peek().getPositionMatrix();

        int layers = 70;
        float maxHeight = radius * 0.1f;
        float expand = radius * 0.28f;

        for (int i = 0; i < layers; i++) {
            float progress = (float) i / (float) layers;
            float layerAlpha = alpha * (1.0f - progress) * (1.0f - progress) * 0.14f;
            if (layerAlpha <= 0.01f) continue;

            float layerRadius = radius + expand * progress;
            float half = layerRadius / 2.0f;
            float zOffset = -maxHeight * progress;
            int c0 = pentagramColor((int) (progress * 180.0f), layerAlpha, color1, color2);
            int c90 = pentagramColor(90 + (int) (progress * 180.0f), layerAlpha, color1, color2);
            int c180 = pentagramColor(180 + (int) (progress * 180.0f), layerAlpha, color1, color2);
            int c270 = pentagramColor(270 + (int) (progress * 180.0f), layerAlpha, color1, color2);

            consumer.vertex(m, -half, half, zOffset).texture(0.0f, 1.0f).color(c270);
            consumer.vertex(m, half, half, zOffset).texture(1.0f, 1.0f).color(c0);
            consumer.vertex(m, half, -half, zOffset).texture(1.0f, 0.0f).color(c180);
            consumer.vertex(m, -half, -half, zOffset).texture(0.0f, 0.0f).color(c90);
        }
        stack.pop();
    }

    private int pentagramColor(int offset, float alpha, int color1, int color2) {
        float t = (float) (0.5 - 0.5 * Math.cos(Math.toRadians(offset)));
        int color = lerpColor(color1, color2, t);
        return multAlpha(color, MathHelper.clamp(alpha, 0.0f, 1.0f));
    }

    // ───────────────────────── Circle (inline-порт Render3D.drawCircle) ─────────────────────────
    private void renderCircle(MatrixStack stack, LivingEntity target, float alpha, float partialTicks) {
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        boolean canSee = mc.player != null && mc.player.canSee(target);

        int baseColor1 = col1();
        int baseColor2 = col2();
        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }

        int size = 64;
        float hitEffect = Math.min(hurtProgress * 2f, 1f);
        float distanceMultiplier = 1.0f + (float) Math.sin(hitEffect * Math.PI) * 0.18f;
        float entityWidth = target.getWidth() * distanceMultiplier;
        float entityHeight = target.getHeight();

        double cs = circleStep;
        double targetY = smoothSin(cs) * entityHeight;
        double targetY2 = smoothSin(cs - 0.35) * entityHeight;
        smoothY = lerp(smoothY, targetY, 0.12);
        smoothY2 = lerp(smoothY2, targetY2, 0.10);

        Vec3d t = target.getLerpedPos(partialTicks);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (canSee) RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        stack.push();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f m = stack.peek().getPositionMatrix();

        // Один Tessellator = один активный буфер за раз → два прохода последовательно.
        // Проход 1: градиентные квады (лента круга и след).
        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < size; i++) {
            float tt = (float) i / size;
            float tNext = (float) ((i + 1) % size) / size;
            float gradientT = (float) (0.5 - 0.5 * Math.cos(tt * Math.PI * 2));
            float gradientTNext = (float) (0.5 - 0.5 * Math.cos(tNext * Math.PI * 2));

            int currentColor = lerpColor(baseColor1, baseColor2, gradientT);
            int nextColor = lerpColor(baseColor1, baseColor2, gradientTNext);

            int brightColor = multAlpha(currentColor, 0.8f * alpha);
            int brightColorNext = multAlpha(nextColor, 0.8f * alpha);
            int fadeColor = multAlpha(currentColor, 0f);
            int fadeColorNext = multAlpha(nextColor, 0f);

            Vec3d cosSin = cosSin(i, size, entityWidth);
            Vec3d nextCosSin = cosSin((i + 1) % size, size, entityWidth);

            Vec3d circlePoint = t.add(cosSin.x, smoothY, cosSin.z);
            Vec3d trailPoint = t.add(cosSin.x, smoothY2, cosSin.z);
            Vec3d nextCirclePoint = t.add(nextCosSin.x, smoothY, nextCosSin.z);
            Vec3d nextTrailPoint = t.add(nextCosSin.x, smoothY2, nextCosSin.z);

            gradientQuad(quads, m, circlePoint, nextCirclePoint, nextTrailPoint, trailPoint,
                    brightColor, brightColorNext, fadeColorNext, fadeColor);
            gradientQuad(quads, m, trailPoint, nextTrailPoint, nextCirclePoint, circlePoint,
                    fadeColor, fadeColorNext, brightColorNext, brightColor);
        }
        var quadsBuilt = quads.endNullable();
        if (quadsBuilt != null) RenderCompat.draw(quadsBuilt);

        // Проход 2: линии (вертикальный след + контур круга).
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < size; i++) {
            float tt = (float) i / size;
            float tNext = (float) ((i + 1) % size) / size;
            float gradientT = (float) (0.5 - 0.5 * Math.cos(tt * Math.PI * 2));
            float gradientTNext = (float) (0.5 - 0.5 * Math.cos(tNext * Math.PI * 2));

            int currentColor = lerpColor(baseColor1, baseColor2, gradientT);
            int nextColor = lerpColor(baseColor1, baseColor2, gradientTNext);

            Vec3d cosSin = cosSin(i, size, entityWidth);
            Vec3d nextCosSin = cosSin((i + 1) % size, size, entityWidth);

            Vec3d circlePoint = t.add(cosSin.x, smoothY, cosSin.z);
            Vec3d trailPoint = t.add(cosSin.x, smoothY2, cosSin.z);
            Vec3d nextCirclePoint = t.add(nextCosSin.x, smoothY, nextCosSin.z);

            int trailTop = multAlpha(currentColor, 0.15f * alpha);
            int trailBottom = multAlpha(currentColor, 0f);
            line(lines, m, circlePoint, trailPoint, trailTop, trailBottom);

            int circleColor = multAlpha(currentColor, alpha);
            int circleColorNext = multAlpha(nextColor, alpha);
            line(lines, m, circlePoint, nextCirclePoint, circleColor, circleColorNext);
        }
        var linesBuilt = lines.endNullable();
        if (linesBuilt != null) RenderCompat.draw(linesBuilt);

        stack.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void gradientQuad(BufferBuilder buf, Matrix4f m, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4,
                             int c1, int c2, int c3, int c4) {
        buf.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(c1);
        buf.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(c2);
        buf.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(c3);
        buf.vertex(m, (float) p4.x, (float) p4.y, (float) p4.z).color(c4);
    }

    private void line(BufferBuilder buf, Matrix4f m, Vec3d a, Vec3d b, int ca, int cb) {
        buf.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(ca);
        buf.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(cb);
    }

    private static Vec3d cosSin(int i, int size, float radius) {
        double ang = (i / (double) size) * Math.PI * 2.0;
        return new Vec3d(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
    }

    private static double smoothSin(double input) {
        double sin = (Math.sin(input) + 1) / 2;
        double t = sin;
        return -(Math.cos(Math.PI * t) - 1) / 2;
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    // ───────────────────────── Chain ─────────────────────────
    private void renderChain(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha) {
        VertexConsumer consumer = provider
                .getBuffer(LynxEspPipelines.CHAIN_ESP.apply(Identifier.of("lynxwave", "images/world/chain.png")));

        float animValue = (System.currentTimeMillis() % 360000) / 1000f * 60f;
        float gradusX = (float) (20 * Math.min(1 + Math.sin(Math.toRadians(animValue)), 1));
        float gradusZ = (float) (20 * (Math.min(1 + Math.sin(Math.toRadians(animValue)), 2) - 1));
        float width = target.getWidth() * 3;

        int linksStep = 18;
        int totalAngle = 360 * 2;
        float chainSizeVal = 8;
        float down = 1.5f;
        float chainScale = 0.5f;

        int alphaVal = MathHelper.clamp((int) (alpha * 128), 0, 128);

        int baseColor1 = col1();
        int baseColor2 = col2();
        if (hurtProgress > 0) {
            baseColor1 = lerpColor(baseColor1, 0xFFFF0000, hurtProgress);
            baseColor2 = lerpColor(baseColor2, 0xFFFF0000, hurtProgress);
        }
        int c1 = withAlpha(baseColor1, alphaVal);
        int c2 = withAlpha(baseColor2, alphaVal);

        float rotationValue = (System.currentTimeMillis() % 720000) / 1000f * 30f;

        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2f - 0.5f * (chain == 0 ? 1.0f : 0.9f);
            stack.push();
            stack.translate(0, target.getHeight() / 2.0f, 0);
            stack.scale(chainScale, chainScale, chainScale);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chain == 0 ? gradusX : -gradusX));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(chain == 0 ? gradusZ : -gradusZ));

            float x = 0, y = -0.5f, z = 0;
            Matrix4f matrix = stack.peek().getPositionMatrix();

            int modif = linksStep / 2;
            for (int i = 0; i < totalAngle; i += modif) {
                float offsetX = (chain == 0 ? gradusX : -gradusX) / 100F;
                float offsetZ = (chain == 0 ? -gradusZ : gradusZ) / 100F;

                float prevSin = (float) (x + offsetX + Math.sin(Math.toRadians(i - modif + rotationValue)) * width * val);
                float prevCos = (float) (z + offsetZ + Math.cos(Math.toRadians(i - modif + rotationValue)) * width * val);
                float sin = (float) (x + offsetX + Math.sin(Math.toRadians(i + rotationValue)) * width * val);
                float cos = (float) (z + offsetZ + Math.cos(Math.toRadians(i + rotationValue)) * width * val);

                float u0 = 1f / 360f * (float) (i - modif) * chainSizeVal;
                float u1 = 1f / 360f * (float) i * chainSizeVal;

                consumer.vertex(matrix, prevSin, y, prevCos).texture(u0, 0).color(c1);
                consumer.vertex(matrix, sin, y, cos).texture(u1, 0).color(c1);
                consumer.vertex(matrix, sin, y + down, cos).texture(u1, 0.99f).color(c2);
                consumer.vertex(matrix, prevSin, y + down, prevCos).texture(u0, 0.99f).color(c2);
            }
            stack.pop();
        }
    }

    // ───────────────────────── Rhomb ─────────────────────────
    private void renderRhomb(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha) {
        VertexConsumer consumer = provider
                .getBuffer(LynxEspPipelines.ROMB_ESP.apply(Identifier.of("lynxwave", "images/world/cube.png")));

        Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();

        stack.translate(0, target.getHeight() / 2f, 0);
        stack.multiply(camRot);

        float timeRotation = (System.currentTimeMillis() % 6283) / 1000f;
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(timeRotation) * 360));

        float size = 0.5f;
        stack.scale(size, size, 1);

        int c1 = withAlpha(col1(), (int) (255 * alpha));
        int c2 = withAlpha(col2(), (int) (255 * alpha));

        var m = stack.peek();
        consumer.vertex(m, -1, -1, 0).texture(0, 0).color(c2);
        consumer.vertex(m, -1, 1, 0).texture(0, 1).color(c1);
        consumer.vertex(m, 1, 1, 0).texture(1, 1).color(c2);
        consumer.vertex(m, 1, -1, 0).texture(1, 0).color(c1);
    }

    // ───────────────────────── Ghost ─────────────────────────
    private void renderGhost(MatrixStack stack, VertexConsumerProvider consumers, LivingEntity target, float alpha) {
        VertexConsumer consumer = consumers
                .getBuffer(LynxEspPipelines.GHOSTS_ESP.apply(Identifier.of("lynxwave", "images/particle/ghost-glow.png")));

        stack.translate(0, target.getHeight() * 0.5f, 0);

        particle(stack, consumer, (sin, cos) -> new Vec3d(sin, cos, -cos), alpha, 0);
        particle(stack, consumer, (sin, cos) -> new Vec3d(-sin, sin, -cos), alpha, 1);
        particle(stack, consumer, (sin, cos) -> new Vec3d(-sin, -sin, cos), alpha, 2);
    }

    private void particle(MatrixStack stack, VertexConsumer consumer, Transformation transformation, float alpha, int colorIndex) {
        double radius = 0.7f;
        double distance = 11;
        float particleSize = 0.5f;
        int alphaFactor = 15;
        long elapsed = System.currentTimeMillis();

        int baseColor = switch (colorIndex) {
            case 0 -> col1();
            case 1 -> col2();
            default -> col3();
        };

        for (int i = 0; i < 40 * alpha; i++) {
            stack.push();
            double angle = 0.15 * ((elapsed * 0.5) - (i * distance)) / 30.0;
            double sin = Math.sin(angle) * radius;
            double cos = Math.cos(angle) * radius;

            Vec3d trans = transformation.make(sin, cos);
            stack.translate(trans.x, trans.y, trans.z);
            stack.multiply(mc.gameRenderer.getCamera().getRotation());

            float spinRotation = (elapsed * 0.1f) - (i * 10f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinRotation));
            stack.translate(particleSize / 2f, particleSize / 2f, 0);

            float x = (float) i / 40f;
            int lerped = lerpColor(baseColor, getNextColor(colorIndex), x);
            int c1 = withAlpha(lerped, (int) ((255 - i * alphaFactor) * alpha));
            int c2 = withAlpha(lerped, (int) ((255 - i * alphaFactor) * alpha));

            var m = stack.peek();
            consumer.vertex(m, 0, -particleSize, 0).texture(0, 0).color(c2);
            consumer.vertex(m, -particleSize, -particleSize, 0).texture(0, 1).color(c1);
            consumer.vertex(m, -particleSize, 0, 0).texture(1, 1).color(c2);
            consumer.vertex(m, 0, 0, 0).texture(1, 0).color(c1);

            stack.pop();
        }
    }

    // ───────────────────────── Crystals ─────────────────────────
    private void createCrystals(Entity target) {
        crystalList.clear();
        crystalList.add(new Crystal(new Vec3d(0, 0.85, 0.8), new Vec3d(-49, 0, 40)));
        crystalList.add(new Crystal(new Vec3d(0.2, 0.85, -0.675), new Vec3d(35, 0, -30)));
        crystalList.add(new Crystal(new Vec3d(0.6, 1.35, 0.6), new Vec3d(-30, 0, 35)));
        crystalList.add(new Crystal(new Vec3d(-0.74, 1.05, 0.4), new Vec3d(-25, 0, -30)));
        crystalList.add(new Crystal(new Vec3d(0.74, 0.95, -0.4), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.475, 0.85, -0.375), new Vec3d(30, 0, -25)));
        crystalList.add(new Crystal(new Vec3d(0, 1.35, -0.6), new Vec3d(45, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(0.85, 0.7, 0.1), new Vec3d(-30, 0, 30)));
        crystalList.add(new Crystal(new Vec3d(-0.7, 1.35, -0.3), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.3, 1.35, 0.55), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.7, 0.75, 0), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(new Vec3d(-0.2, 0.65, -0.7), new Vec3d(0, 0, 0)));
    }

    private void renderCrystals(MatrixStack stack, VertexConsumerProvider provider, LivingEntity target, float alpha, float deltaTime) {
        if (target == null || crystalList.isEmpty()) return;

        rotationAngle += crystalRotationSpeed.getValue() * deltaTime;
        rotationAngle = rotationAngle % 360;

        stack.push();
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAngle));

        int baseColor = col1();
        if (hurtProgress > 0) baseColor = lerpColor(baseColor, 0xFFFF0000, hurtProgress);

        for (Crystal crystal : crystalList) {
            crystal.render(stack, provider, alpha, baseColor);
        }
        stack.pop();
    }

    private class Crystal {
        private final Vec3d position;
        private final Vec3d rotation;
        private final float rotationSpeed;

        public Crystal(Vec3d position, Vec3d rotation) {
            this.position = position;
            this.rotation = rotation;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
        }

        public void render(MatrixStack stack, VertexConsumerProvider provider, float alpha, int baseColor) {
            stack.push();
            stack.translate(position.x, position.y, position.z);

            float timeSeconds = (System.currentTimeMillis() % 31416) / 1000f;
            float pulsation = 1.0f + (float) (Math.sin(timeSeconds * 2f) * 0.1f);
            stack.scale(pulsation, pulsation, pulsation);

            float selfRotation = (System.currentTimeMillis() % 36000) / 100.0f * rotationSpeed;
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y + selfRotation));
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));

            float userAlpha = 0.3f;

            VertexConsumer filledConsumer = provider.getBuffer(LynxEspPipelines.CRYSTAL_FILLED);
            drawFilledCrystal(stack, filledConsumer, baseColor, userAlpha * 0.85f, alpha);

            VertexConsumer glowConsumer = provider.getBuffer(LynxEspPipelines.CRYSTAL_GLOW);
            stack.push();
            stack.scale(1.15f, 1.15f, 1.15f);
            drawFilledCrystal(stack, glowConsumer, baseColor, userAlpha * 0.25f, alpha);
            stack.pop();

            stack.push();
            stack.scale(1.3f, 1.3f, 1.3f);
            drawFilledCrystal(stack, glowConsumer, baseColor, userAlpha * 0.1f, alpha);
            stack.pop();

            drawBloomEffect(stack, provider, baseColor, alpha);

            stack.pop();
        }

        private void drawFilledCrystal(MatrixStack stack, VertexConsumer consumer, int baseColor, float alphaMultiplier, float anim) {
            float s = 0.05f;
            float hPrism = s;
            float hPyramid = s * 1.5f;
            int numSides = 8;

            List<Vector3f> topVertices = new ArrayList<>();
            List<Vector3f> bottomVertices = new ArrayList<>();
            for (int i = 0; i < numSides; i++) {
                float angle = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(angle));
                float z = (float) (s * Math.sin(angle));
                topVertices.add(new Vector3f(x, hPrism / 2, z));
                bottomVertices.add(new Vector3f(x, -hPrism / 2, z));
            }
            Vector3f vTop = new Vector3f(0, hPrism / 2 + hPyramid, 0);
            Vector3f vBottom = new Vector3f(0, -hPrism / 2 - hPyramid, 0);

            int finalAlpha = (int) (alphaMultiplier * 255 * anim);
            int finalColor = withAlpha(baseColor, finalAlpha);
            int darkerColor = withAlpha(darkenColor(baseColor, 0.7f), finalAlpha);
            int lighterColor = withAlpha(lightenColor(baseColor, 1.2f), finalAlpha);

            Matrix4f matrix = stack.peek().getPositionMatrix();

            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = bottomVertices.get(i);
                Vector3f v2 = bottomVertices.get((i + 1) % numSides);
                Vector3f v3 = topVertices.get((i + 1) % numSides);
                Vector3f v4 = topVertices.get(i);
                int sideColor = (i % 2 == 0) ? finalColor : darkerColor;
                drawQuadFilled(matrix, consumer, v1, v2, v3, v4, sideColor);
            }
            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = topVertices.get(i);
                Vector3f v2 = topVertices.get((i + 1) % numSides);
                int pyramidColor = (i % 2 == 0) ? lighterColor : finalColor;
                drawTriangleFilled(matrix, consumer, vTop, v2, v1, pyramidColor);
            }
            for (int i = 0; i < numSides; i++) {
                Vector3f v1 = bottomVertices.get(i);
                Vector3f v2 = bottomVertices.get((i + 1) % numSides);
                int pyramidColor = (i % 2 == 0) ? darkerColor : finalColor;
                drawTriangleFilled(matrix, consumer, vBottom, v1, v2, pyramidColor);
            }
        }

        private void drawTriangleFilled(Matrix4f matrix, VertexConsumer consumer, Vector3f v1, Vector3f v2, Vector3f v3, int color) {
            consumer.vertex(matrix, v1.x, v1.y, v1.z).color(color);
            consumer.vertex(matrix, v2.x, v2.y, v2.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
        }

        private void drawQuadFilled(Matrix4f matrix, VertexConsumer consumer, Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4, int color) {
            consumer.vertex(matrix, v1.x, v1.y, v1.z).color(color);
            consumer.vertex(matrix, v2.x, v2.y, v2.z).color(color);
            consumer.vertex(matrix, v3.x, v3.y, v3.z).color(color);
            consumer.vertex(matrix, v4.x, v4.y, v4.z).color(color);
        }

        private void drawBloomEffect(MatrixStack stack, VertexConsumerProvider provider, int baseColor, float anim) {
            VertexConsumer bloomConsumer = provider
                    .getBuffer(LynxEspPipelines.BLOOM_ESP.apply(Identifier.of("lynxwave", "images/particle/glow.png")));

            int bloomAlpha = (int) (0.3f * 60 * anim);
            int bloomColor = withAlpha(baseColor, bloomAlpha);
            float bloomSize = 0.05f * 15.0f;
            Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
            int segments = 6;

            for (int i = 0; i < segments; i++) {
                stack.push();
                float angle = (360.0f / segments) * i;
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                stack.multiply(camRot);
                Matrix4f matrix = stack.peek().getPositionMatrix();
                bloomConsumer.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bloomConsumer.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);
                stack.pop();
            }
            for (int i = 0; i < segments; i++) {
                stack.push();
                float angle = (360.0f / segments) * i;
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                stack.multiply(camRot);
                Matrix4f matrix = stack.peek().getPositionMatrix();
                bloomConsumer.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bloomConsumer.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bloomConsumer.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);
                stack.pop();
            }
        }
    }

    // ───────────────────────── helpers ─────────────────────────
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getNextColor(int colorIndex) {
        return switch (colorIndex) {
            case 0 -> col2();
            case 1 -> col3();
            default -> col1();
        };
    }

    private int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int multAlpha(int color, float factor) {
        int a = MathHelper.clamp((int) (((color >>> 24) & 0xFF) * factor), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @FunctionalInterface
    private interface Transformation {
        Vec3d make(double sin, double cos);
    }
}
