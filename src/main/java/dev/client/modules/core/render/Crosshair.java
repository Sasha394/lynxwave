package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.other.vector.Vec2i;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class Crosshair extends Module {
    public static Crosshair INSTANCE;

    public Crosshair() {
        super("Crosshair", ModuleCategory.Render, "Заменяет стандартный прицел игры на кастомный.");
    }

    public final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Default", "Circle")
            .applyDefault("Default")
            .register(this);

    public final CheckBox changeColorOnTarget = new CheckBox("Менять цвет при наводке", () -> true)
            .defaultValue(false)
            .register(this);

    public final ModeElement colorMode = new ModeElement("Цвет", () -> true)
            .set("Клиент", "Свой")
            .defaultValue("Клиент")
            .register(this);

    public final ColorPicker customColor = new ColorPicker("Выбор цвета", () -> colorMode.isSelected("Свой"))
            .set(-1)
            .defaultValue(-1)
            .register(this);

    public final ColorPicker targetColor = new ColorPicker("Цвет наведения", changeColorOnTarget::getEnabled)
            .set(ColorUtils.rgb(255, 72, 72))
            .defaultValue(ColorUtils.rgb(255, 72, 72))
            .register(this);

    public final CheckBox animate = new CheckBox("Анимация удара", () -> mode.isSelected("Default"))
            .defaultValue(true)
            .register(this);

    public final CheckBox dot = new CheckBox("Точка", () -> mode.isSelected("Default"))
            .defaultValue(true)
            .register(this);

    public final CheckBox staticCrosshair = new CheckBox("Статичный", () -> mode.isSelected("Default"))
            .defaultValue(false)
            .register(this);

    public final CheckBox tCrosshair = new CheckBox("T-образный", () -> mode.isSelected("Default"))
            .defaultValue(false)
            .register(this);

    public final Slider width = new Slider("Ширина", () -> mode.isSelected("Default"))
            .set(0f, 10f, 1f)
            .defaultValue(5f)
            .register(this);

    public final Slider height = new Slider("Высота", () -> mode.isSelected("Default"))
            .set(0f, 10f, 1f)
            .defaultValue(5f)
            .register(this);

    public final Slider gap = new Slider("Точка", () -> mode.isSelected("Default"))
            .set(0f, 10f, 1f)
            .defaultValue(3f)
            .register(this);
    
    public final Slider thickness = new Slider("Толщина", () -> mode.isSelected("Default"))
            .set(2f, 10f, 1f)
            .defaultValue(1f)
            .register(this);

    public CompactAnimation animation = new CompactAnimation(Easing.LINEAR, 50);
    private float animationSize;
    private boolean vanillaCrosshairPass;

    public void renderVanillaCrosshairPass(DrawContext context, RenderTickCounter tickCounter) {
        vanillaCrosshairPass = true;
        try {
            onRender2D(new RenderEvent.Draw2D(context, tickCounter));
        } finally {
            vanillaCrosshairPass = false;
        }
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!vanillaCrosshairPass) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (mc.options == null || mc.options.getPerspective() != Perspective.FIRST_PERSON) return;

        DrawContext context = event.getContext();
        if (context == null) return;

        Window window = mc.getWindow();
        Vec2i center = getMouseInteger(window.getScaledWidth(), window.getScaledHeight());
        double centerX = center.x / 2F;
        double centerY = center.y / 2F;

        double cwidth = this.width.getValue();
        double cheight = this.height.getValue();

        int defaultColor = ColorUtils.resolveModeColor(colorMode, customColor, Interface.INSTANCE.getMainColor(4));

        boolean hasTarget = mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof LivingEntity livingEntity
                && livingEntity.isAlive();

        int color = changeColorOnTarget.getEnabled() && hasTarget
                ? targetColor.getColorRGBA()
                : defaultColor;

        float lineThickness = this.thickness.getValue();
        float swingProgress = mc.player.handSwingProgress;
        float sin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

        boolean useAnimation = animate.getEnabled() && !staticCrosshair.getEnabled();
        if (useAnimation) {
            animation.run(sin);
        }

        if (mode.isSelected("Default")) {
            float gapValue = this.gap.getValue() + (useAnimation ? (float) (animation.getValue() * 4F) : 0F);

            if (dot.getEnabled()) {
                drawRect(context, (float) (centerX - 0.5F), (float) (centerY - 0.5F), 1F, 1F, color);
            }

            if (!tCrosshair.getEnabled()) {
                drawRect(context,
                        (float) (centerX - (lineThickness / 2F)),
                        (float) (centerY - gapValue - cheight),
                        (float) lineThickness,
                        (float) cheight,
                        color);
                drawRect(context,
                        (float) (centerX - (lineThickness / 2F)),
                        (float) (centerY + gapValue),
                        (float) lineThickness,
                        (float) cheight,
                        color);
                drawRect(context,
                        (float) (centerX - gapValue - cwidth),
                        (float) (centerY - (lineThickness / 2F)),
                        (float) cwidth,
                        (float) lineThickness,
                        color);
                drawRect(context,
                        (float) (centerX + gapValue),
                        (float) (centerY - (lineThickness / 2F)),
                        (float) cwidth,
                        (float) lineThickness,
                        color);
            }
        }

        if (mode.isSelected("Circle")) {
            float attackStrength = mc.player.getAttackCooldownProgress(1.0f);
            float targetFillAngle = attackStrength * 360f;

            animationSize = MathUtil.fast(animationSize, targetFillAngle, 10f);

            float radius = 4f;
            float circleThickness = 1f;

            drawArc(context,
                    (float) centerX,
                    (float) centerY,
                    0f,
                    360f,
                    radius,
                    circleThickness,
                    ColorUtils.rgb(30, 30, 30));

            int circleColor = defaultColor;
            if (changeColorOnTarget.getEnabled() && hasTarget) {
                circleColor = targetColor.getColorRGBA();
            }

            drawArc(context,
                    (float) centerX,
                    (float) centerY,
                    0f,
                    animationSize,
                    radius,
                    circleThickness,
                    circleColor);
        }
    }

    private void drawRect(DrawContext context, float x, float y, float width, float height, int color) {
        if (width <= 0.0f || height <= 0.0f) return;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(new QuadColorState(color))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y);
    }

    private void drawArc(DrawContext context, float centerX, float centerY, float startAngle, float endAngle, float radius, float thickness, int color) {
        if (radius <= 0.0f || thickness <= 0.0f || endAngle <= startAngle) return;

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        DrawUtil.setupRender();
        RenderSystem.disableCull();
        float span = endAngle - startAngle;
        int segments = Math.max(1, Math.round(span / 360f * 64f));

        float outerRadius = radius + thickness / 2f;
        float innerRadius = Math.max(0f, radius - thickness / 2f);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float progress = i / (float) segments;
            float angle = (startAngle + span * progress) * ((float) Math.PI / 180f);
            float cos = MathHelper.cos(angle);
            float sin = MathHelper.sin(angle);

            float outerX = centerX + cos * outerRadius;
            float outerY = centerY + sin * outerRadius;
            float innerX = centerX + cos * innerRadius;
            float innerY = centerY + sin * innerRadius;

            builder.vertex(matrix, outerX, outerY, 0.0f).color(color);
            builder.vertex(matrix, innerX, innerY, 0.0f).color(color);
        }

        RenderCompat.draw(builder.end());

        RenderSystem.enableCull();
        DrawUtil.endRender();
    }
}
