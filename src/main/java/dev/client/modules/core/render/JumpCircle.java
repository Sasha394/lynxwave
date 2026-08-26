package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.opengl.GlStateManager;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.EaseBackIn;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class JumpCircle extends Module {
    public static JumpCircle INSTANCE;

    private static final String MODE_PULSE = "Пульс";
    private static final String MODE_SPIRAL = "Спираль";
    private static final String MODE_NOVA = "Нова";

    private static final String ANIM_SIZE = "Размер";
    private static final String ANIM_ALPHA = "Прозрачность";

    private final ModeElement mode = new ModeElement("Режим", () -> true)
            .set(MODE_PULSE, MODE_SPIRAL, MODE_NOVA).defaultValue(MODE_PULSE).register(this);

    private final Slider radius = new Slider("Радиус", () -> true)
            .set(0.3f, 1.0f, 0.05f).defaultValue(0.5f).register(this);

    private final Slider time = new Slider("Длительность", () -> true)
            .set(200f, 2000f, 100f).defaultValue(1000f).register(this);

    private final Slider animSpeed = new Slider("Скорость анимации", () -> true)
            .set(100f, 1000f, 50f).defaultValue(500f).register(this);

    private final SelectElements animations = new SelectElements("Анимации", () -> true)
            .set(ANIM_SIZE, ANIM_ALPHA).defaultValue(ANIM_SIZE, ANIM_ALPHA).register(this);

    private final Slider animValue = new Slider("Сила анимации", () -> animations.isSelected(ANIM_SIZE))
            .set(0.05f, 1.0f, 0.01f).defaultValue(1.0f).register(this);

    private final ModeElement colorMode = new ModeElement("Цвет", () -> true)
            .set("Клиент", "Свой").defaultValue("Клиент").register(this);

    private final ColorPicker customColor = new ColorPicker("Выбор цвета", () -> colorMode.isSelected("Свой"))
            .set(-1).defaultValue(-1).register(this);

    private final List<Circle> circles = new ArrayList<>();
    private final List<PlayerEntity> cache = new CopyOnWriteArrayList<>();

    private static final Quaternionf ROT_X_90 = RotationAxis.POSITIVE_X.rotationDegrees(90.0F);
    private static final int LUT_SIZE = 512;
    private static final float[] SIN_LUT = new float[LUT_SIZE];
    private static final float[] COS_LUT = new float[LUT_SIZE];

    private static final float[] PULSE_LAYER_A = {0.12F, 0.45F, 1.0F};
    private static final float[] PULSE_LEN_M = {1.5F, 1.0F, 0.65F};
    private static final float[] SPIRAL_LAYER_ALPHA = {0.1F, 0.4F, 0.9F};
    private static final float[] ARC_SPEEDS = {9.0E-4F, -7.0E-4F, 0.0013F, -0.0011F, 6.0E-4F, -0.0015F};
    private static final float[] ARC_LEN = {0.55F, 0.7F, 0.45F, 0.65F, 0.5F, 0.6F};
    private static final float[] ARC_R_MULT = {1.0F, 0.96F, 1.03F, 0.98F, 1.01F, 0.95F};
    private static final int[] ORBIT_DOT_COUNTS = {16, 24};
    private static final float[] ORBIT_SPDS = {0.001F, 6.0E-4F};
    private static final int[] ORBIT_DIRS = {1, -1};
    private static final float[] NOVA_LAYER_A = {0.08F, 0.35F, 0.9F};
    private static final float[] NOVA_LAYER_W = {3.0F, 1.5F, 0.6F};
    private static final double TWO_PI_ORBIT_0 = (Math.PI * 2D) / (double) ORBIT_DOT_COUNTS[0];
    private static final double TWO_PI_ORBIT_1 = (Math.PI * 2D) / (double) ORBIT_DOT_COUNTS[1];
    private static final double TWO_PI_ARC_6 = (Math.PI / 3D);
    private static final int SPOKE_SEGS = 10;
    private static final float[] SPOKE_EDGE = new float[SPOKE_SEGS + 1];
    private static final float[] ARC_GLOW_POW = new float[8];
    private static final float[] DOT_GLOW_POW = new float[5];
    private static final float[] CENTER_GLOW_POW = new float[10];
    private static final float[] HALO_POW = new float[14];
    private static final int ARC_EDGE_LUT_SIZE = 64;
    private static final float[] ARC_EDGE_LUT = new float[ARC_EDGE_LUT_SIZE];
    private static final float[] NEB_POW = new float[7];
    private static final float[] PULSE_CENTER_POW = new float[9];
    private static final int TRAIL_SEGS = 40;
    private static final float[] TRAIL_EDGE = new float[TRAIL_SEGS];
    private static final float[] TRAIL_ANGLE_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_F = new float[TRAIL_SEGS];
    private static final float[] TRAIL_ANGLE_OFFSET_B = new float[TRAIL_SEGS];
    private static final float[] TRAIL_F_B = new float[TRAIL_SEGS];
    private static final int RAY_COUNT = 16;
    private static final double TWO_PI_RAY_16 = (Math.PI / 8D);
    private static final float[] SPIRAL_HALO_POW = new float[10];
    private static final float[] SPIRAL_INNER_POW = new float[10];
    private static final float[] SPIRAL_TIP_POW = new float[7];
    private static final float[] TRAIL_COS_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_SIN_OFFSET = new float[TRAIL_SEGS];
    private static final float[] TRAIL_COS_OFFSET_B = new float[TRAIL_SEGS];
    private static final float[] TRAIL_SIN_OFFSET_B = new float[TRAIL_SEGS];
    private static final float TAIL_COS_DELTA;
    private static final float TAIL_SIN_DELTA;
    private static final double TWO_PI_SPIRAL_3 = 2.0943951023931953;
    private static final double TWO_PI_SPOKE_12 = (Math.PI / 6D);
    private static final int SPIRAL_SEGS = 80;
    private static final float[] SPIRAL_EDGE_POW07 = new float[SPIRAL_SEGS];

    private final float[] orbitRsBuf = new float[2];
    private final float[] orbitCos0 = new float[ORBIT_DOT_COUNTS[0]];
    private final float[] orbitSin0 = new float[ORBIT_DOT_COUNTS[0]];
    private final float[] orbitCos1 = new float[ORBIT_DOT_COUNTS[1]];
    private final float[] orbitSin1 = new float[ORBIT_DOT_COUNTS[1]];
    private final double[] arcStartA = new double[6];

    private int cachedThemeRGB;
    private int transparentColor;

    public JumpCircle() {
        super("JumpCircle", ModuleCategory.Render, "Рисует под игроком круг при прыжке и приземлении");
        INSTANCE = this;
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (circles.isEmpty()) return;

        MatrixStack stack = event.getMatrices();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(RenderSystem.SRC_ALPHA, RenderSystem.ONE,
                RenderSystem.ONE, RenderSystem.ZERO);

        cachedThemeRGB = resolveColor();
        transparentColor = cachedThemeRGB & 0x00FFFFFF;

        Vec3d cp = mc.getEntityRenderDispatcher().camera.getCameraPos();
        double camX = cp.x;
        double camY = cp.y;
        double camZ = cp.z;

        boolean animAlpha = animations.isSelected(ANIM_ALPHA);
        boolean animSize = animations.isSelected(ANIM_SIZE);
        float animVal = animValue.getValue();
        float rad = radius.getValue();
        long timeoutMs = (long) (animSpeed.getValue() + time.getValue());
        long now = System.currentTimeMillis();
        String modeName = mode.getValue();
        int modeId = MODE_PULSE.equals(modeName) ? 0 : (MODE_SPIRAL.equals(modeName) ? 1 : 2);

        for (int ci = 0, cn = circles.size(); ci < cn; ++ci) {
            Circle c = circles.get(ci);
            double animOut = c.animation.getOutput().doubleValue();
            float alpha = animAlpha ? (float) animOut : 2.0F;
            float sizeF = animSize ? (float) ((double) (1.0F - animVal) + animOut * (double) animVal) : 1.0F;
            float r = sizeF * rad;
            if (alpha < 0.01F) continue;

            if (c.animation.finished(Direction.FORWARDS) && now - c.timerStart >= timeoutMs) {
                c.animation.setDirection(Direction.BACKWARDS);
                c.timerStart = now;
            }

            long elapsed = now - c.birthTime;
            stack.push();
            stack.translate(c.pos.x - camX, c.pos.y - camY, c.pos.z - camZ);
            stack.multiply(ROT_X_90);
            Matrix4f mat = stack.peek().getPositionMatrix();
            switch (modeId) {
                case 0 -> drawPulse(mat, r, alpha, elapsed, cachedThemeRGB);
                case 1 -> drawSpiral(mat, r, alpha, elapsed, cachedThemeRGB);
                default -> drawNova(mat, r, alpha, elapsed, cachedThemeRGB);
            }
            stack.pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int resolveColor() {
        int clientColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
        return ColorUtils.resolveModeColor(colorMode, customColor, clientColor);
    }

    private void drawPulse(Matrix4f mat, float r, float alpha, long elapsed, int theme) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder fills = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int g = 0; g < 12; ++g) {
            float gf = (float) g / 12.0F;
            float grI = r * (1.0F + gf * 0.7F);
            float grO = r * (1.0F + (gf + 0.083333336F) * 0.7F);
            float ga = alpha * (1.0F - gf) * (1.0F - gf) * 0.18F;
            batchFilledRing(fills, mat, grI, grO, 48, col(theme, ga));
        }

        int nebSegs = 52;
        int nebBands = 7;
        double elapsedD = (double) elapsed * 0.0028;

        for (int b = 0; b < nebBands; ++b) {
            float bf = (float) b / (float) nebBands;
            float pulse = (float) (Math.sin(elapsedD + (double) b * 0.95) * 0.5 + 0.5);
            float bandR = r * (0.15F + bf * 0.72F);
            float thick = r * (0.055F + pulse * 0.035F);
            float ba = alpha * NEB_POW[b] * (0.06F + pulse * 0.1F);
            if (ba < 0.004F) continue;
            batchFilledRing(fills, mat, bandR - thick, bandR + thick, nebSegs, col(theme, ba));
        }

        float trailRot = (float) ((double) elapsed * 3.5E-4);
        double elapsedTrail = (double) elapsed * 0.003;
        float halfW = r * 0.03F;

        for (int arm = 0; arm < 2; ++arm) {
            float cosBase = (float) Math.cos((double) arm * Math.PI + (double) trailRot);
            float sinBase = (float) Math.sin((double) arm * Math.PI + (double) trailRot);

            for (int seg = 0; seg < 39; ++seg) {
                float edge = TRAIL_EDGE[seg];
                float fA = TRAIL_F[seg];
                float pulse = (float) (Math.sin(elapsedTrail + (double) (fA * 5.0F) + (double) arm * 2.1) * 0.25 + 0.75);
                float sa = alpha * edge * pulse * 0.22F;
                if (sa < 0.004F) continue;
                float coA = cosBase * TRAIL_COS_OFFSET[seg] - sinBase * TRAIL_SIN_OFFSET[seg];
                float siA = sinBase * TRAIL_COS_OFFSET[seg] + cosBase * TRAIL_SIN_OFFSET[seg];
                float coB = cosBase * TRAIL_COS_OFFSET_B[seg] - sinBase * TRAIL_SIN_OFFSET_B[seg];
                float siB = sinBase * TRAIL_COS_OFFSET_B[seg] + cosBase * TRAIL_SIN_OFFSET_B[seg];
                float dxA = -siA;
                float dxB = -siB;
                float fB = TRAIL_F_B[seg];
                float rA = fA * r * 0.88F;
                float rB = fB * r * 0.88F;
                fills.vertex(mat, coA * rA + dxA * halfW, siA * rA + coA * halfW, 0.0F).color(col(theme, sa));
                fills.vertex(mat, coA * rA - dxA * halfW, siA * rA - coA * halfW, 0.0F).color(col(theme, sa * 0.15F));
                fills.vertex(mat, coB * rB - dxB * halfW, siB * rB - coB * halfW, 0.0F).color(col(theme, sa * 0.15F));
                fills.vertex(mat, coB * rB + dxB * halfW, siB * rB + coB * halfW, 0.0F).color(col(theme, sa));
            }
        }

        float cp = (float) (Math.sin((double) elapsed * 0.005) * 0.5 + 0.5);
        float cpMod = 0.35F + cp * 0.3F;

        for (int g = 0; g < 9; ++g) {
            float gr = 0.006F + (float) g * 0.016F;
            float ga = alpha * PULSE_CENTER_POW[g] * cpMod;
            batchFilledRingOffset(fills, mat, gr * 0.25F, gr, 20, col(theme, ga), 0.0F, 0.0F);
        }

        RenderCompat.draw(fills.end());

        BufferBuilder lines = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        batchRing(lines, mat, r * 1.12F, 64, col(theme, alpha * 0.05F));
        batchRing(lines, mat, r * 1.06F, 64, col(theme, alpha * 0.14F));
        batchRing(lines, mat, r * 1.02F, 64, col(theme, alpha * 0.35F));
        batchRing(lines, mat, r * 1.0F, 64, col(theme, alpha * 0.8F));
        batchRing(lines, mat, r * 0.98F, 64, col(theme, alpha * 1.0F));

        double rot = (double) elapsed * 6.5E-4;
        double elapsedRay = (double) elapsed * 0.0045;

        for (int i = 0; i < RAY_COUNT; ++i) {
            double angle = TWO_PI_RAY_16 * (double) i + rot;
            float pulse = (float) (Math.sin(elapsedRay + (double) i * 0.6) * 0.5 + 0.5);
            float rLen = r * (0.1F + pulse * 0.35F);
            float rS = r * 1.005F;
            float ca = (float) Math.cos(angle);
            float sa2 = (float) Math.sin(angle);
            float pulsedAlphaBase = 0.2F + pulse * 0.6F;

            for (int l = 0; l < 3; ++l) {
                float la = alpha * pulsedAlphaBase * PULSE_LAYER_A[l];
                lines.vertex(mat, ca * rS, sa2 * rS, 0.0F).color(col(theme, la));
                lines.vertex(mat, ca * (rS + rLen * PULSE_LEN_M[l]), sa2 * (rS + rLen * PULSE_LEN_M[l]), 0.0F).color(transparentColor);
            }
        }

        for (int e = 0; e < 3; ++e) {
            float ep = (float) ((elapsed + (long) e * 500L) % 1600L) / 1600.0F;
            float er = r * (1.0F + ep * 0.85F);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.45F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 56, col(theme, ea * 0.4F));
            batchRing(lines, mat, er * 0.991F, 56, col(theme, ea * 0.8F));
            batchRing(lines, mat, er * 0.982F, 56, col(theme, ea));
        }

        RenderCompat.draw(lines.end());
    }

    private void drawSpiral(Matrix4f mat, float r, float alpha, long elapsed, int theme) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder fills = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int gs = 44;

        for (int g = 0; g < 10; ++g) {
            float gf = (float) g / 10.0F;
            float gr = r * (1.0F + gf * 0.55F);
            float ga = alpha * SPIRAL_HALO_POW[g] * 0.16F;
            batchFilledRing(fills, mat, gr * 0.976F, gr, gs, col(theme, ga));
        }

        for (int i = 0; i < 10; ++i) {
            float rf = (float) i / 10.0F;
            float ia = alpha * SPIRAL_INNER_POW[i] * 0.22F;
            batchFilledRing(fills, mat, r * rf * 0.92F, r * (rf + 0.1F) * 0.92F, gs, col(theme, ia));
        }

        int spirals = 3;
        for (int s = 0; s < spirals; ++s) {
            double phase = (double) elapsed * 0.0013 * (s % 2 == 0 ? 1.0 : -1.15) + TWO_PI_SPIRAL_3 * (double) s;
            double tipA = 17.27875959474386 + phase;
            float tx = (float) (Math.cos(tipA) * (double) r);
            float ty = (float) (Math.sin(tipA) * (double) r);
            float pulse = (float) (Math.sin((double) elapsed * 0.006 + (double) s * 2.1) * 0.5 + 0.5);
            float pBase = alpha * pulse * 0.75F;

            for (int g = 0; g < 7; ++g) {
                float gr = 0.015F + (float) g * 0.028F;
                float ga = pBase * SPIRAL_TIP_POW[g];
                batchFilledRingOffset(fills, mat, gr * 0.4F, gr, 20, col(theme, ga), tx, ty);
            }
        }

        RenderCompat.draw(fills.end());

        BufferBuilder lines = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int segs = SPIRAL_SEGS;

        for (int s = 0; s < spirals; ++s) {
            double dir = s % 2 == 0 ? 1.0 : -1.15;
            double phase = (double) elapsed * 0.0013 * dir + TWO_PI_SPIRAL_3 * (double) s;

            for (int i = 0; i < segs - 1; ++i) {
                float fA = (float) i / (float) segs;
                float fB = (float) (i + 1) / (float) segs;
                float edge = SPIRAL_EDGE_POW07[i];
                float pulse = (float) (Math.sin((double) elapsed * 0.004 + (double) (fA * 8.0F) + (double) s) * 0.06 + 0.94);
                float rA = fA * r * pulse;
                float rB = fB * r * pulse;
                double aA = (double) fA * Math.PI * 5.5 + phase;
                double aB = (double) fB * Math.PI * 5.5 + phase;
                float coA = (float) Math.cos(aA);
                float siA = (float) Math.sin(aA);
                float coB = (float) Math.cos(aB);
                float siB = (float) Math.sin(aB);
                float xA = coA * rA;
                float yA = siA * rA;
                float xB = coB * rB;
                float yB = siB * rB;

                for (int layer = 0; layer < 3; ++layer) {
                    float sa = alpha * edge * SPIRAL_LAYER_ALPHA[layer] * pulse;
                    if (sa < 0.008F) continue;
                    lines.vertex(mat, xA, yA, 0.0F).color(col(theme, sa));
                    lines.vertex(mat, xB, yB, 0.0F).color(col(theme, sa));
                }
            }
        }

        batchRing(lines, mat, r * 1.1F, 64, col(theme, alpha * 0.05F));
        batchRing(lines, mat, r * 1.05F, 64, col(theme, alpha * 0.15F));
        batchRing(lines, mat, r * 1.01F, 64, col(theme, alpha * 0.4F));
        batchRing(lines, mat, r * 1.0F, 64, col(theme, alpha * 0.85F));
        batchRing(lines, mat, r * 0.97F, 64, col(theme, alpha * 0.5F));

        for (int e = 0; e < 2; ++e) {
            float ep = (float) ((elapsed + (long) e * 700L) % 1400L) / 1400.0F;
            float er = r * (1.0F + ep * 0.7F);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.38F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 56, col(theme, ea * 0.5F));
            batchRing(lines, mat, er * 0.989F, 56, col(theme, ea));
        }

        RenderCompat.draw(lines.end());
    }

    private void drawNova(Matrix4f mat, float r, float alpha, long elapsed, int theme) {
        Tessellator tess = Tessellator.getInstance();

        for (int a = 0; a < 6; ++a) {
            arcStartA[a] = (double) ((float) elapsed * ARC_SPEEDS[a]) + (double) a * TWO_PI_ARC_6;
        }

        double orbitBase0 = (double) ((float) elapsed * ORBIT_SPDS[0] * (float) ORBIT_DIRS[0]);
        double orbitBase1 = (double) ((float) elapsed * ORBIT_SPDS[1] * (float) ORBIT_DIRS[1]);

        for (int i = 0; i < ORBIT_DOT_COUNTS[0]; ++i) {
            double a = TWO_PI_ORBIT_0 * (double) i + orbitBase0;
            orbitCos0[i] = (float) Math.cos(a);
            orbitSin0[i] = (float) Math.sin(a);
        }
        for (int i = 0; i < ORBIT_DOT_COUNTS[1]; ++i) {
            double a = TWO_PI_ORBIT_1 * (double) i + orbitBase1;
            orbitCos1[i] = (float) Math.cos(a);
            orbitSin1[i] = (float) Math.sin(a);
        }
        BufferBuilder fills = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int gs = 52;

        for (int g = 0; g < 14; ++g) {
            float gf = (float) g / 14.0F;
            float gr = r * (1.0F + gf * 0.9F);
            float ga = alpha * HALO_POW[g] * 0.2F;
            batchFilledRing(fills, mat, gr * 0.976F, gr, gs, col(theme, ga));
        }

        float webR = r * 0.88F;
        double webRot = (double) elapsed * 8.0E-4;

        for (int sp = 0; sp < 12; ++sp) {
            double angle = TWO_PI_SPOKE_12 * (double) sp + webRot;
            float pulse3 = (float) (Math.sin((double) elapsed * 0.005 + (double) sp * 0.8) * 0.5 + 0.5);
            float pulseAlpha = 0.08F + pulse3 * 0.12F;
            float ca = (float) Math.cos(angle);
            float si = (float) Math.sin(angle);

            for (int seg = 0; seg < 9; ++seg) {
                float edgeA = SPOKE_EDGE[seg];
                float edgeB = SPOKE_EDGE[seg + 1];
                float sa = alpha * edgeA * pulseAlpha;
                float sb = alpha * edgeB * pulseAlpha;
                if (sa < 0.004F && sb < 0.004F) continue;
                float fA = (float) seg / 10.0F;
                float fB = (float) (seg + 1) / 10.0F;
                float rA = fA * webR;
                float rB = fB * webR;
                fills.vertex(mat, ca * rA + si * 0.025F, si * rA - ca * 0.025F, 0.0F).color(col(theme, sa));
                fills.vertex(mat, ca * rA - si * 0.025F, si * rA + ca * 0.025F, 0.0F).color(col(theme, sa));
                fills.vertex(mat, ca * rB - si * 0.025F, si * rB + ca * 0.025F, 0.0F).color(col(theme, sb));
                fills.vertex(mat, ca * rB + si * 0.025F, si * rB - ca * 0.025F, 0.0F).color(col(theme, sb));
            }
        }

        for (int wr = 1; wr <= 4; ++wr) {
            float wrf = (float) wr / 4.0F;
            float wRad = webR * wrf;
            float pulse4 = (float) (Math.sin((double) elapsed * 0.004 + (double) wr * 1.2) * 0.5 + 0.5);
            float wa = alpha * (1.0F - wrf * 0.5F) * (0.08F + pulse4 * 0.12F);
            batchFilledRing(fills, mat, wRad * 0.985F, wRad * 1.015F, 40, col(theme, wa));
        }

        for (int g = 0; g < 10; ++g) {
            float gr = 0.01F + (float) g * 0.02F;
            float ga = alpha * CENTER_GLOW_POW[g] * 0.65F;
            batchFilledRingOffset(fills, mat, gr * 0.25F, gr, 20, col(theme, ga), 0.0F, 0.0F);
        }

        for (int a = 0; a < 6; ++a) {
            float arcR = r * ARC_R_MULT[a];
            double tipA = arcStartA[a] + (Math.PI * 2D) * (double) ARC_LEN[a];
            float tx = (float) (Math.cos(tipA) * (double) arcR);
            float ty = (float) (Math.sin(tipA) * (double) arcR);
            float pulse = (float) (Math.sin((double) elapsed * 0.007 + (double) a * 1.7) * 0.5 + 0.5);
            float pBase = alpha * pulse * 0.85F;

            for (int g = 0; g < 8; ++g) {
                float gr = 0.012F + (float) g * 0.022F;
                float ga = pBase * ARC_GLOW_POW[g];
                batchFilledRingOffset(fills, mat, gr * 0.35F, gr, 16, col(theme, ga), tx, ty);
            }
        }

        orbitRsBuf[0] = r * 1.28F;
        orbitRsBuf[1] = r * 1.48F;

        for (int o = 0; o < 2; ++o) {
            int dc = ORBIT_DOT_COUNTS[o];
            float or = orbitRsBuf[o];
            float[] oCos = o == 0 ? orbitCos0 : orbitCos1;
            float[] oSin = o == 0 ? orbitSin0 : orbitSin1;

            for (int i = 0; i < dc; ++i) {
                float dx = oCos[i] * or;
                float dy = oSin[i] * or;
                float pulse = (float) (Math.sin((double) elapsed * 0.008 + (double) i * 1.3 + (double) o) * 0.5 + 0.5);
                float daB = alpha * (0.3F + pulse * 0.55F) * 0.7F;

                for (int g = 0; g < 5; ++g) {
                    float gr = 0.01F + (float) g * 0.018F;
                    float ga = daB * DOT_GLOW_POW[g];
                    batchFilledRingOffset(fills, mat, gr * 0.3F, gr, 12, col(theme, ga), dx, dy);
                }
            }
        }

        RenderCompat.draw(fills.end());

        BufferBuilder lines = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int arcSegs = 36;

        for (int a = 0; a < 6; ++a) {
            float arcR = r * ARC_R_MULT[a];
            int total = (int) ((float) arcSegs * ARC_LEN[a]);
            float pulse = (float) (Math.sin((double) elapsed * 0.005 + (double) a * 1.3) * 0.35 + 0.65);
            int totalM1 = total - 1;
            if (totalM1 <= 0) continue;
            double arcAngleSpan = (Math.PI * 2D) * (double) ARC_LEN[a];

            for (int layer = 0; layer < 3; ++layer) {
                float rW = arcR * (1.0F + NOVA_LAYER_W[layer] * 0.012F);
                float layerAlphaPulse = alpha * NOVA_LAYER_A[layer] * pulse;

                for (int seg = 0; seg < totalM1; ++seg) {
                    int edgeIdx = seg * 63 / totalM1;
                    float edge = ARC_EDGE_LUT[edgeIdx];
                    float sa = layerAlphaPulse * edge;
                    if (sa < 0.008F) continue;
                    double aA = arcStartA[a] + (double) seg / (double) total * arcAngleSpan;
                    double aB = arcStartA[a] + (double) (seg + 1) / (double) total * arcAngleSpan;
                    lines.vertex(mat, (float) (Math.cos(aA) * (double) rW), (float) (Math.sin(aA) * (double) rW), 0.0F).color(col(theme, sa));
                    lines.vertex(mat, (float) (Math.cos(aB) * (double) rW), (float) (Math.sin(aB) * (double) rW), 0.0F).color(col(theme, sa));
                }
            }
        }

        batchRing(lines, mat, r * 1.14F, 72, col(theme, alpha * 0.04F));
        batchRing(lines, mat, r * 1.08F, 72, col(theme, alpha * 0.1F));
        batchRing(lines, mat, r * 1.03F, 72, col(theme, alpha * 0.25F));
        batchRing(lines, mat, r * 1.0F, 72, col(theme, alpha * 0.75F));
        batchRing(lines, mat, r * 0.98F, 72, col(theme, alpha * 1.0F));
        batchRing(lines, mat, r * 0.95F, 72, col(theme, alpha * 0.4F));

        for (int o = 0; o < 2; ++o) {
            int dc = ORBIT_DOT_COUNTS[o];
            float or = orbitRsBuf[o];
            float[] oCos = o == 0 ? orbitCos0 : orbitCos1;
            float[] oSin = o == 0 ? orbitSin0 : orbitSin1;
            float cosDelta = TAIL_COS_DELTA;
            float sinDelta = o == 0 ? -TAIL_SIN_DELTA : TAIL_SIN_DELTA;

            for (int i = 0; i < dc; ++i) {
                float pulse = (float) (Math.sin((double) elapsed * 0.008 + (double) i * 1.3 + (double) o) * 0.5 + 0.5);
                float da = alpha * (0.3F + pulse * 0.55F);
                float px = oCos[i] * or;
                float py = oSin[i] * or;

                for (int seg = 1; seg <= 3; ++seg) {
                    float sa = da * (1.0F - (float) seg * 0.28F);
                    float nx = px * cosDelta - py * sinDelta;
                    float ny = px * sinDelta + py * cosDelta;
                    if (sa >= 0.008F) {
                        lines.vertex(mat, px, py, 0.0F).color(col(theme, sa));
                        lines.vertex(mat, nx, ny, 0.0F).color(col(theme, sa));
                    }
                    px = nx;
                    py = ny;
                }
            }
        }

        for (int e = 0; e < 3; ++e) {
            float ep = (float) ((elapsed + (long) e * 550L) % 1800L) / 1800.0F;
            float er = r * (1.0F + ep);
            float ea = alpha * (1.0F - ep) * (1.0F - ep) * 0.48F;
            if (ea < 0.004F) continue;
            batchRing(lines, mat, er, 72, col(theme, ea * 0.3F));
            batchRing(lines, mat, er * 0.993F, 72, col(theme, ea * 0.65F));
            batchRing(lines, mat, er * 0.985F, 72, col(theme, ea));
        }

        RenderCompat.draw(lines.end());
    }

    private void batchRing(BufferBuilder buf, Matrix4f mat, float r, int segs, int color) {
        float step = (float) LUT_SIZE / (float) segs;
        for (int i = 0; i < segs; ++i) {
            int iA = (int) ((float) i * step) & (LUT_SIZE - 1);
            int iB = (int) ((float) (i + 1) * step) & (LUT_SIZE - 1);
            buf.vertex(mat, COS_LUT[iA] * r, SIN_LUT[iA] * r, 0.0F).color(color);
            buf.vertex(mat, COS_LUT[iB] * r, SIN_LUT[iB] * r, 0.0F).color(color);
        }
    }

    private void batchFilledRing(BufferBuilder buf, Matrix4f mat, float inner, float outer, int segs, int color) {
        batchFilledRingOffset(buf, mat, inner, outer, segs, color, 0.0F, 0.0F);
    }

    private void batchFilledRingOffset(BufferBuilder buf, Matrix4f mat, float inner, float outer, int segs, int color, float cx, float cy) {
        float step = (float) LUT_SIZE / (float) segs;
        for (int i = 0; i < segs; ++i) {
            int iA = (int) ((float) i * step) & (LUT_SIZE - 1);
            int iB = (int) ((float) (i + 1) * step) & (LUT_SIZE - 1);
            float coA = COS_LUT[iA];
            float siA = SIN_LUT[iA];
            float coB = COS_LUT[iB];
            float siB = SIN_LUT[iB];
            buf.vertex(mat, cx + coA * outer, cy + siA * outer, 0.0F).color(color);
            buf.vertex(mat, cx + coA * inner, cy + siA * inner, 0.0F).color(color);
            buf.vertex(mat, cx + coB * inner, cy + siB * inner, 0.0F).color(color);
            buf.vertex(mat, cx + coB * outer, cy + siB * outer, 0.0F).color(color);
        }
    }

    private int col(int themeRGB, float a) {
        int ai = Math.max(0, Math.min(255, (int) (a * 255.0F)));
        return (themeRGB & 0x00FFFFFF) | (ai << 24);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (!cache.contains(mc.player) && mc.player.isOnGround()) {
            cache.add(mc.player);
        }

        for (int i = cache.size() - 1; i >= 0; --i) {
            PlayerEntity pl = cache.get(i);
            if (pl != null && !pl.isOnGround()) {
                circles.add(new Circle(new Vec3d(pl.getX(), Math.floor(pl.getY()) + 0.05, pl.getZ())));
                cache.remove(i);
            }
        }

        for (int i = circles.size() - 1; i >= 0; --i) {
            if (circles.get(i).animation.finished(Direction.BACKWARDS)) {
                circles.remove(i);
            }
        }

        if (mc.currentScreen instanceof DeathScreen) {
            cache.clear();
        }
    }

    static {
        for (int i = 0; i < LUT_SIZE; ++i) {
            double a = 0.01227184630308513 * (double) i;
            SIN_LUT[i] = (float) Math.sin(a);
            COS_LUT[i] = (float) Math.cos(a);
        }

        for (int i = 0; i <= SPOKE_SEGS; ++i) {
            SPOKE_EDGE[i] = (float) Math.sin((double) ((float) i / 10.0F) * Math.PI);
        }

        for (int g = 0; g < 8; ++g) {
            float v = 1.0F - (float) g / 8.0F;
            ARC_GLOW_POW[g] = v * v;
        }

        for (int g = 0; g < 5; ++g) {
            float v = 1.0F - (float) g / 5.0F;
            DOT_GLOW_POW[g] = v * v;
        }

        for (int g = 0; g < 10; ++g) {
            float v = 1.0F - (float) g / 10.0F;
            CENTER_GLOW_POW[g] = v * v * v;
        }

        for (int g = 0; g < 14; ++g) {
            float gf = (float) g / 14.0F;
            HALO_POW[g] = (float) Math.pow((double) (1.0F - gf), (double) 2.2F);
        }

        for (int i = 0; i < ARC_EDGE_LUT_SIZE; ++i) {
            ARC_EDGE_LUT[i] = (float) Math.sin((double) ((float) i / 63.0F) * Math.PI);
        }

        for (int b = 0; b < 7; ++b) {
            float v = 1.0F - (float) b / 7.0F;
            NEB_POW[b] = (float) Math.pow((double) v, (double) 1.4F);
        }

        for (int g = 0; g < 9; ++g) {
            float v = 1.0F - (float) g / 9.0F;
            PULSE_CENTER_POW[g] = v * v * v;
        }

        for (int seg = 0; seg < TRAIL_SEGS; ++seg) {
            float fA = (float) seg / 40.0F;
            TRAIL_EDGE[seg] = (float) Math.sin((double) fA * Math.PI);
            TRAIL_ANGLE_OFFSET[seg] = (float) ((double) fA * Math.PI * 1.6);
            TRAIL_F[seg] = fA;
        }

        for (int seg = 0; seg < TRAIL_SEGS; ++seg) {
            float fB = (float) (seg + 1) / 40.0F;
            TRAIL_ANGLE_OFFSET_B[seg] = (float) ((double) fB * Math.PI * 1.6);
            TRAIL_F_B[seg] = fB;
        }

        for (int g = 0; g < 10; ++g) {
            float v = 1.0F - (float) g / 10.0F;
            SPIRAL_HALO_POW[g] = v * v;
        }

        for (int i = 0; i < 10; ++i) {
            float v = 1.0F - (float) i / 10.0F;
            SPIRAL_INNER_POW[i] = (float) Math.pow((double) v, (double) 2.5F);
        }

        for (int g = 0; g < 7; ++g) {
            float v = 1.0F - (float) g / 7.0F;
            SPIRAL_TIP_POW[g] = v * v;
        }

        for (int seg = 0; seg < TRAIL_SEGS; ++seg) {
            TRAIL_COS_OFFSET[seg] = (float) Math.cos((double) TRAIL_ANGLE_OFFSET[seg]);
            TRAIL_SIN_OFFSET[seg] = (float) Math.sin((double) TRAIL_ANGLE_OFFSET[seg]);
            TRAIL_COS_OFFSET_B[seg] = (float) Math.cos((double) TRAIL_ANGLE_OFFSET_B[seg]);
            TRAIL_SIN_OFFSET_B[seg] = (float) Math.sin((double) TRAIL_ANGLE_OFFSET_B[seg]);
        }

        TAIL_COS_DELTA = (float) Math.cos(0.1);
        TAIL_SIN_DELTA = (float) Math.sin(0.1);

        for (int i = 0; i < SPIRAL_SEGS; ++i) {
            float fA = (float) i / 80.0F;
            SPIRAL_EDGE_POW07[i] = (float) Math.pow(Math.sin((double) fA * Math.PI), (double) 0.7F);
        }
    }

    public class Circle {
        final Vec3d pos;
        final long birthTime = System.currentTimeMillis();
        long timerStart;
        final dev.client.api.nullcry.render.core.animations.nova.extended.Animation animation;

        Circle(Vec3d pos) {
            this.timerStart = this.birthTime;
            this.pos = pos;
            this.animation = new EaseBackIn(JumpCircle.this.animSpeed.getValue().intValue(), 1.5, 0.1F, Direction.FORWARDS);
        }
    }
}
