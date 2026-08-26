package dev.client.modules.core.combat.autoSwap;


import dev.client.api.compat.RenderCompat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.AutoSwap;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class TripleSwapScreen extends Screen implements ClientApi {
    private static final float INNER_RADIUS = 42.0F;
    private static final float OUTER_RADIUS = 92.0F;
    private static final float ICON_RADIUS = 70.0F;
    private static final float HOVER_INSET = 6.0F;
    private static final float HOVER_OUTSET = 10.0F;
    private static final float SWAP_MIN_RADIUS = 50.0F;
    private static final long SWAP_ARM_DELAY_MS = 60L;
    private static final float SECTOR_GAP = 0.0F;
    private static final float DIVIDER_WIDTH = 1.6F;
    private static final int BASE_COLOR = rgba(210, 210, 210, 160);
    private static final int HIGHLIGHT_COLOR = rgba(255, 214, 0, 210);
    private static final int OUTLINE_COLOR = rgba(255, 255, 255, 140);
    private static final int DIVIDER_COLOR = rgba(255, 255, 255, 120);
    private static final int PLUS_COLOR = rgba(255, 255, 255, 200);
    private static final float RIGHT_START = 330.0F;
    private static final float RIGHT_END = 90.0F;
    private static final float LEFT_START = 90.0F;
    private static final float LEFT_END = 210.0F;
    private static final float BOTTOM_START = 210.0F;
    private static final float BOTTOM_END = 330.0F;
    private final AutoSwap autoSwap;
    private AutoSwap.TripleSlot hoveredSlot;
    private boolean swapped;
    private long openedAtMs;

    public TripleSwapScreen(AutoSwap autoSwap) {
        super(Text.literal("AutoSwap"));
        this.autoSwap = autoSwap;
    }

    @Override
    protected void init() {
        super.init();
        this.swapped = false;
        this.openedAtMs = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float cx = (float) (this.width / 2);
        float cy = (float) (this.height / 2);
        Polar polar = toPolar((double) mouseX, (double) mouseY, cx, cy);
        this.hoveredSlot = this.resolveSlot(polar);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawSector(context.getMatrices(), cx, cy, 42.0F, 92.0F, 330.0F, 90.0F, BASE_COLOR);
        this.drawSector(context.getMatrices(), cx, cy, 42.0F, 92.0F, 90.0F, 210.0F, BASE_COLOR);
        this.drawSector(context.getMatrices(), cx, cy, 42.0F, 92.0F, 210.0F, 330.0F, BASE_COLOR);
        if (this.hoveredSlot != null) {
            float s;
            float e;
            if (this.hoveredSlot == AutoSwap.TripleSlot.RIGHT) {
                s = 330.0F;
                e = 90.0F;
            } else if (this.hoveredSlot == AutoSwap.TripleSlot.LEFT) {
                s = 90.0F;
                e = 210.0F;
            } else {
                s = 210.0F;
                e = 330.0F;
            }

            this.drawSector(context.getMatrices(), cx, cy, 42.0F, 92.0F, s, e, HIGHLIGHT_COLOR);
        }

        GL11.glEnable(2848);
        RenderSystem.lineWidth(1.6F);
        this.drawRadialLine(context.getMatrices(), cx, cy, 42.0F, 92.0F, 90.0F, DIVIDER_COLOR);
        this.drawRadialLine(context.getMatrices(), cx, cy, 42.0F, 92.0F, 210.0F, DIVIDER_COLOR);
        this.drawRadialLine(context.getMatrices(), cx, cy, 42.0F, 92.0F, 330.0F, DIVIDER_COLOR);
        RenderSystem.lineWidth(1.0F);
        GL11.glDisable(2848);
        this.renderSlot(context, cx, cy, AutoSwap.TripleSlot.RIGHT, 0.0F);
        this.renderSlot(context, cx, cy, AutoSwap.TripleSlot.LEFT, 180.0F);
        this.renderSlot(context, cx, cy, AutoSwap.TripleSlot.BOTTOM, 270.0F);
        this.handleHoverSwap(polar);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) {
            return super.mouseClicked(click, doubled);
        } else {
            float cx = (float) (this.width / 2);
            float cy = (float) (this.height / 2);
            Polar polar = toPolar(mouseX, mouseY, cx, cy);
            AutoSwap.TripleSlot slot = this.resolveSlot(polar);
            if (slot == null) {
                return super.mouseClicked(click, doubled);
            } else {
                if (this.autoSwap.hasTripleStack(slot)) {
                    this.autoSwap.clearTripleStack(slot);
                } else {
                    this.autoSwap.beginSelection(slot);
                }

                return true;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public AutoSwap.TripleSlot getSelectedSlot() {
        return this.hoveredSlot;
    }

    private void handleHoverSwap(Polar polar) {
        if (!this.swapped) {
            if (this.hoveredSlot != null) {
                if (this.autoSwap.hasTripleStack(this.hoveredSlot)) {
                    if (System.currentTimeMillis() - this.openedAtMs >= 60L) {
                        if (!(polar.distance < (double) 50.0F) && !(polar.distance > (double) 102.0F)) {
                            this.swapped = true;
                            this.autoSwap.swapTriple(this.hoveredSlot);
                            this.close();
                        }
                    }
                }
            }
        }
    }

    private AutoSwap.@Nullable TripleSlot resolveSlot(Polar polar) {
        float minR = Math.max(0.0F, 36.0F);
        float maxR = 102.0F;
        if (!(polar.distance < (double) minR) && !(polar.distance > (double) maxR)) {
            double angle = polar.angleDeg;
            if (isInSector(angle, 330.0F, 90.0F)) {
                return AutoSwap.TripleSlot.RIGHT;
            } else if (isInSector(angle, 90.0F, 210.0F)) {
                return AutoSwap.TripleSlot.LEFT;
            } else {
                return isInSector(angle, 210.0F, 330.0F) ? AutoSwap.TripleSlot.BOTTOM : null;
            }
        } else {
            return null;
        }
    }

    private static boolean isInSector(double angle, float startDeg, float endDeg) {
        if (endDeg < startDeg) {
            return angle >= (double) startDeg || angle < (double) endDeg;
        } else {
            return angle >= (double) startDeg && angle < (double) endDeg;
        }
    }

    private static Polar toPolar(double mouseX, double mouseY, float cx, float cy) {
        double dx = mouseX - (double) cx;
        double dy = mouseY - (double) cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < (double) 0.0F) {
            angle += (double) 360.0F;
        }

        return new Polar(dist, angle);
    }

    private void renderSlot(DrawContext context, float cx, float cy, AutoSwap.TripleSlot slot, float angleDeg) {
        ItemStack stack = this.autoSwap.getTripleStack(slot);
        float rad = (float) Math.toRadians((double) angleDeg);
        float ix = cx + MathHelper.cos(rad) * 70.0F - 8.0F;
        float iy = cy - MathHelper.sin(rad) * 70.0F - 8.0F;
        if (stack.isEmpty()) {
            context.drawText(this.textRenderer, "+", (int) ix + 4, (int) iy + 2, PLUS_COLOR, false);
        } else {
            RenderSystem.enableBlend();
            context.drawItem(stack, (int) ix, (int) iy);
            RenderSystem.disableBlend();
        }
    }

    private void drawSector(Matrix3x2fStack matrices, float cx, float cy, float innerR, float outerR, float startDeg, float endDeg, int color) {
        float a0 = startDeg;
        float a1 = endDeg;
        if (endDeg < startDeg) {
            a1 = endDeg + 360.0F;
        }

        int segments = 72;
        float step = (a1 - startDeg) / (float) segments;
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f m = new Matrix4f().mul(matrices);

        for (int i = 0; i <= segments; ++i) {
            float ang = (a0 + step * (float) i) % 360.0F;
            float rad = (float) Math.toRadians((double) ang);
            float cos = MathHelper.cos(rad);
            float sin = MathHelper.sin(rad);
            bb.vertex(m, cx + cos * outerR, cy - sin * outerR, 0.0F).color(color);
            bb.vertex(m, cx + cos * innerR, cy - sin * innerR, 0.0F).color(color);
        }

        RenderCompat.draw(bb.end());
        this.drawArcOutline(matrices, cx, cy, outerR, a0, a1, OUTLINE_COLOR);
        this.drawArcOutline(matrices, cx, cy, innerR, a0, a1, OUTLINE_COLOR);
    }

    private void drawArcOutline(Matrix3x2fStack matrices, float cx, float cy, float radius, float startDeg, float endDeg, int color) {
        int segments = 72;
        float step = (endDeg - startDeg) / (float) segments;
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f m = new Matrix4f().mul(matrices);

        for (int i = 0; i <= segments; ++i) {
            float ang = (startDeg + step * (float) i) % 360.0F;
            float rad = (float) Math.toRadians((double) ang);
            float cos = MathHelper.cos(rad);
            float sin = MathHelper.sin(rad);
            bb.vertex(m, cx + cos * radius, cy - sin * radius, 0.0F).color(color);
        }

        RenderCompat.draw(bb.end());
    }

    private void drawRadialLine(Matrix3x2fStack matrices, float cx, float cy, float innerR, float outerR, float angleDeg, int color) {
        float rad = (float) Math.toRadians((double) angleDeg);
        float cos = MathHelper.cos(rad);
        float sin = MathHelper.sin(rad);
        float x0 = cx + cos * innerR;
        float y0 = cy - sin * innerR;
        float x1 = cx + cos * outerR;
        float y1 = cy - sin * outerR;
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f m = new Matrix4f().mul(matrices);
        bb.vertex(m, x0, y0, 0.0F).color(color);
        bb.vertex(m, x1, y1, 0.0F).color(color);
        RenderCompat.draw(bb.end());
    }

    private static int rgba(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    private record Polar(double distance, double angleDeg) {
    }
}
