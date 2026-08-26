package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.game.JumpEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class  JumpCircles extends Module {

    public JumpCircles() {
        super("JumpCircles", ModuleCategory.Render, "Отображает красивый круг при прыжке");
    }

    ModeElement mode = new ModeElement("Режим",() -> true).set("Glow","Client").defaultValue("Client").register(this);
    Slider radiusCircle = new Slider("Радиус",() -> true).set(1,6,1).defaultValue(4).register(this);
    CheckBox renderThroughBlocks = new CheckBox("Рендерить в блоках", () -> true).defaultValue(true).register(this);
    ModeElement color = new ModeElement("Цвет", () -> true).set("Клиент", "Свой").defaultValue("Клиент").register(this);
    ColorPicker customColor = new ColorPicker("Выбор цвета", () -> color.isSelected("Свой")).set(-1).register(this);

    private final CopyOnWriteArrayList<Circle> circles = new CopyOnWriteArrayList<>();

    @Subscribe
    public void onJump(JumpEvent event) {
        if (mc.player == null) return;
        if (event.getEntity() != mc.player) return;

        Vec3d pos = dev.client.api.compat.EntityCompat.pos(event.getEntity()).add(0, 0.05, 0);
        circles.add(new Circle(pos));
    }

    @Subscribe
    public void onTick(TickEvent event) {
        circles.removeIf(circle -> System.currentTimeMillis() - circle.time > 2000);
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (circles.isEmpty()) return;
        MatrixStack matrices = event.getMatrices();
        Camera camera = mc.getEntityRenderDispatcher().camera;

        matrices.push();
        DrawUtil.setupRender();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        boolean renderInBlocks = renderThroughBlocks.getEnabled();
        if (renderInBlocks) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }

        var circleTexture = mode.isSelected("Glow")
                ? ClientTexture.of("images/jumpcircles/glow.png")
                : ClientTexture.of("images/jumpcircles/client.png");

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (Circle circle : circles) {
            float progress = (System.currentTimeMillis() - circle.time) / 2000.0f;
            float radius = MathUtil.lerp(0.0f, radiusCircle.getValue(), progress);
            float alpha = MathUtil.lerp(1.0f, 0.0f, progress * progress);
            if (progress > 1.0f) alpha = 0.0f;

            Vec3d pos = circle.position;
            double x = pos.x - camera.getCameraPos().x;
            double y = pos.y - camera.getCameraPos().y;
            double z = pos.z - camera.getCameraPos().z;

            float halfSize = radius / 2.0f;
            float minX = (float) x - halfSize;
            float maxX = (float) x + halfSize;
            float minZ = (float) z - halfSize;
            float maxZ = (float) z + halfSize;

            int circleColorInt = ColorUtils.resolveModeColor(color, customColor, Interface.INSTANCE.getMainColor());
            Color circleColor = new Color(circleColorInt, true);

            int alphaInt = (int) (alpha * 255);

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            bufferBuilder.vertex(matrix, minX, (float) y, minZ).texture(0.0f, 0.0f).color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), alphaInt);
            bufferBuilder.vertex(matrix, maxX, (float) y, minZ).texture(1.0f, 0.0f).color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), alphaInt);
            bufferBuilder.vertex(matrix, maxX, (float) y, maxZ).texture(1.0f, 1.0f).color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), alphaInt);
            bufferBuilder.vertex(matrix, minX, (float) y, maxZ).texture(0.0f, 1.0f).color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), alphaInt);
        }

        RenderCompat.drawTextured(bufferBuilder.end(), circleTexture);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (renderInBlocks) {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.disableBlend();
        DrawUtil.endRender();
        matrices.pop();
    }

    private static class Circle {
        private final Vec3d position;
        private final long time;

        public Circle(Vec3d position) {
            this.position = position;
            this.time = System.currentTimeMillis();
        }
    }
}
