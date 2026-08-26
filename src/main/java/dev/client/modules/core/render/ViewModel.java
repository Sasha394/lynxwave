package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.compat.GuiMatrixCompat;
import dev.client.api.compat.RenderCompat;
import dev.client.api.nullcry.events.core.render.ShaderHand2DEvent;
import dev.client.api.nullcry.events.core.render.ShaderHandEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ClickSetting;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.HandHighlightRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public class ViewModel extends Module {
    public static ViewModel INSTANCE;

    private static final int GUIDE_DIVISIONS = 10;
    private static final float GUIDE_SNAP_THRESHOLD = 6.0f;
    private static final float ROTATION_RING_THICKNESS = 1.75f;
    private static final float ROTATION_MIN_MOUSE_RADIUS = 8.0f;
    private static final float ROTATION_SCROLL_STEP = 5.0f;
    private static final long RENDERED_ANCHOR_LIFETIME_NANOS = 500_000_000L;

    public ViewModel() {
        super("ViewModel", ModuleCategory.Render, "Дает возможность кастомизировать руки по X,Y,Z");
    }

    public final CheckBox throughChat = new CheckBox("Через чат", () -> true)
            .defaultValue(false)
            .register(this);

    public final Slider right_x = new Slider("Правая рука X", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider right_y = new Slider("Правая рука Y", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider right_z = new Slider("Правая рука Z", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider right_scale = new Slider("Правая рука масштаб", () -> true).set(0.25f, 2.5f, 0.05F).defaultValue(1.0F).register(this);
    public final Slider right_rotation = new Slider("Правая рука поворот Z", () -> true).set(-180.0f, 180.0f, 1.0F).defaultValue(0.0F).register(this);

    public final Slider left_x = new Slider("Левая рука X", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider left_y = new Slider("Левая рука Y", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider left_z = new Slider("Левая рука Z", () -> true).set(-2.0f, 2.0f, 0.1F).defaultValue(0.0F).register(this);
    public final Slider left_scale = new Slider("Левая рука масштаб", () -> true).set(0.25f, 2.5f, 0.05F).defaultValue(1.0F).register(this);
    public final Slider left_rotation = new Slider("Левая рука поворот Z", () -> true).set(-180.0f, 180.0f, 1.0F).defaultValue(0.0F).register(this);

    private Framebuffer beforeHandFbo;
    private Framebuffer withHandFbo;
    private EditedArm draggedArm;
    private EditedArm rotatingArm;
    private EditedArm rotationPreviewArm;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private float dragStartX;
    private float dragStartY;
    private double rotationStartMouseAngle;
    private float rotationStartValue;
    private boolean rotationKeyHeld;
    private boolean freeMoveMode;
    private boolean ctrlAltLatch;
    private float animatedRightScale = 1.0f;
    private float animatedLeftScale = 1.0f;
    private long rightScaleFrameNanos;
    private long leftScaleFrameNanos;
    private EditedArm itemCaptureArm;
    private float itemCaptureTickDelta;
    private float renderedRightCenterX;
    private float renderedRightCenterY;
    private float renderedRightRadius;
    private float renderedLeftCenterX;
    private float renderedLeftCenterY;
    private float renderedLeftRadius;
    private long renderedRightAnchorNanos;
    private long renderedLeftAnchorNanos;

    public final ClickSetting reset = new ClickSetting("Сбросить позиции", () -> true)
            .onClick(() -> {
                right_x.resetToDefault();
                right_y.resetToDefault();
                right_z.resetToDefault();
                right_scale.resetToDefault();
                right_rotation.resetToDefault();
                left_x.resetToDefault();
                left_y.resetToDefault();
                left_z.resetToDefault();
                left_scale.resetToDefault();
                left_rotation.resetToDefault();
            })
            .cooldown(300)
            .register(this);

    @Subscribe
    public void onHandCapture(ShaderHandEvent event) {
        this.beforeHandFbo = event.getBeforeHandFbo();
        this.withHandFbo = event.getWithHandFbo();
    }

    @Subscribe
    public void onHandOverlay(ShaderHand2DEvent event) {
        if (!canEditThroughChat()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
        EditedArm hovered = rotatingArm != null
                ? rotatingArm
                : draggedArm != null
                ? draggedArm
                : rotationKeyHeld && rotationPreviewArm != null
                ? rotationPreviewArm
                : findHoveredArm(mouseX, mouseY);
        if (hovered == null || !validFramebuffers()) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        String shader = hovered == EditedArm.RIGHT ? "hand_outline_right" : "hand_outline_left";
        new HandHighlightRenderer(
                withHandFbo,
                beforeHandFbo,
                0.0f,
                0.01f,
                1.0f,
                new float[]{1.0f, 1.0f, 1.0f},
                width,
                height,
                shader
        ).render(GuiMatrixCompat.toMatrix4(event.getContext()));
    }

    public boolean onChatMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !canEditThroughChat()) {
            return false;
        }

        syncRotationKeyState(mouseX, mouseY);
        if (rotationKeyHeld) {
            EditedArm target = findRotationTarget(mouseX, mouseY);
            if (target == null) {
                return false;
            }

            draggedArm = null;
            rotatingArm = target;
            rotationPreviewArm = target;
            double dx = mouseX - armCenterX(target);
            double dy = mouseY - armCenterY(target);
            rotationStartMouseAngle = dx * dx + dy * dy < ROTATION_MIN_MOUSE_RADIUS * ROTATION_MIN_MOUSE_RADIUS
                    ? Double.NaN
                    : mouseAngle(target, mouseX, mouseY);
            rotationStartValue = rotationFor(target).getValue();
            return true;
        }

        EditedArm hovered = findHoveredArm(mouseX, mouseY);
        if (hovered == null) {
            return false;
        }

        rotatingArm = null;
        draggedArm = hovered;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartX = hovered == EditedArm.RIGHT ? right_x.getValue() : left_x.getValue();
        dragStartY = hovered == EditedArm.RIGHT ? right_y.getValue() : left_y.getValue();
        return true;
    }

    public boolean onChatMouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !canEditThroughChat()) {
            return false;
        }

        if (rotatingArm != null) {
            if (!rotationKeyHeld) {
                rotatingArm = null;
                return false;
            }

            double centerX = armCenterX(rotatingArm);
            double centerY = armCenterY(rotatingArm);
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            if (dx * dx + dy * dy < ROTATION_MIN_MOUSE_RADIUS * ROTATION_MIN_MOUSE_RADIUS) {
                return true;
            }

            double currentAngle = mouseAngle(rotatingArm, mouseX, mouseY);
            if (Double.isNaN(rotationStartMouseAngle)) {
                rotationStartMouseAngle = currentAngle;
                rotationStartValue = rotationFor(rotatingArm).getValue();
                return true;
            }
            float delta = MathHelper.wrapDegrees((float) (currentAngle - rotationStartMouseAngle));
            rotationFor(rotatingArm).setValue(Math.round(MathHelper.wrapDegrees(rotationStartValue + delta)));
            return true;
        }

        if (draggedArm == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double width = Math.max(1, client.getWindow().getScaledWidth());
        double height = Math.max(1, client.getWindow().getScaledHeight());
        float newX = dragStartX + (float) ((mouseX - dragStartMouseX) / (width * 0.22));
        float newY = dragStartY - (float) ((mouseY - dragStartMouseY) / (height * 0.28));

        updateFreeMoveToggle();
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT)) {
            newX = dragStartX;
        }
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            newY = dragStartY;
        }

        if (!freeMoveMode) {
            float[] snapped = snapToGuides(draggedArm, newX, newY, (float) width, (float) height);
            newX = snapped[0];
            newY = snapped[1];
        }

        Slider x = draggedArm == EditedArm.RIGHT ? right_x : left_x;
        Slider y = draggedArm == EditedArm.RIGHT ? right_y : left_y;
        x.setValue(roundHundredth(newX));
        y.setValue(roundHundredth(newY));
        return true;
    }

    public boolean onChatMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!canEditThroughChat() || amount == 0.0) {
            return false;
        }

        syncRotationKeyState(mouseX, mouseY);
        if (rotationKeyHeld) {
            EditedArm target = rotatingArm != null ? rotatingArm : rotationPreviewArm;
            if (target == null || !hasItem(target)) {
                return false;
            }

            float step = amount > 0.0 ? ROTATION_SCROLL_STEP : -ROTATION_SCROLL_STEP;
            float rotation = MathHelper.wrapDegrees(rotationFor(target).getValue() + step);
            rotationFor(target).setValue(Math.round(rotation));
            return true;
        }

        EditedArm hovered = draggedArm != null ? draggedArm : findHoveredArm(mouseX, mouseY);
        if (hovered == null) {
            return false;
        }

        Slider scale = hovered == EditedArm.RIGHT ? right_scale : left_scale;
        float step = amount > 0.0 ? 0.05f : -0.05f;
        scale.setValue(Math.round((scale.getValue() + step) * 20.0f) / 20.0f);
        return true;
    }

    public boolean onChatKeyPressed(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_Z || !canEditThroughChat()) {
            return false;
        }

        if (rotationKeyHeld) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
        EditedArm hovered = draggedArm != null ? draggedArm : findHoveredArm(mouseX, mouseY);
        if (hovered == null) {
            return false;
        }

        rotationKeyHeld = true;
        rotationPreviewArm = hovered;
        return true;
    }

    public boolean shouldConsumeChatCharacter() {
        return rotationKeyHeld && canEditThroughChat();
    }

    public void onChatKeyReleased(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_Z) {
            rotationKeyHeld = false;
            rotatingArm = null;
            rotationPreviewArm = null;
        }
    }

    public void renderChatEditor(DrawContext context, double mouseX, double mouseY) {
        if (!canEditThroughChat()) {
            stopChatEditing();
            return;
        }

        syncRotationKeyState(mouseX, mouseY);
        updateFreeMoveToggle();

        if (draggedArm != null && !freeMoveMode) {
            drawGuides(context);
        }

        if (draggedArm != null) {
            drawMoveHints(context, draggedArm);
        }

        if (!rotationKeyHeld) {
            return;
        }

        if (rotatingArm == null) {
            EditedArm hovered = findHoveredArm(mouseX, mouseY);
            if (hovered != null) {
                rotationPreviewArm = hovered;
            }
        }

        EditedArm target = rotatingArm != null ? rotatingArm : rotationPreviewArm;
        if (target != null && hasItem(target)) {
            drawRotationGizmo(context, target);
        }
    }

    public float getRenderScale(Arm arm) {
        boolean right = arm == Arm.RIGHT;
        float target = right ? right_scale.getValue() : left_scale.getValue();
        long now = System.nanoTime();
        long lastFrame = right ? rightScaleFrameNanos : leftScaleFrameNanos;
        float current = right ? animatedRightScale : animatedLeftScale;

        if (lastFrame == 0L) {
            current = target;
        } else {
            float deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0f, 0.05f);
            float progress = 1.0f - (float) Math.exp(-12.0f * deltaSeconds);
            current += (target - current) * progress;
            if (Math.abs(target - current) < 0.001f) {
                current = target;
            }
        }

        if (right) {
            animatedRightScale = current;
            rightScaleFrameNanos = now;
        } else {
            animatedLeftScale = current;
            leftScaleFrameNanos = now;
        }
        return current;
    }

    public float getRenderRotation(Arm arm) {
        return (arm == Arm.RIGHT ? right_rotation : left_rotation).getValue();
    }

    public void beginRenderedItemCapture(Arm arm, float tickDelta) {
        if (!isEnabled()) {
            return;
        }
        itemCaptureArm = arm == Arm.RIGHT ? EditedArm.RIGHT : EditedArm.LEFT;
        itemCaptureTickDelta = tickDelta;
    }

    public void captureRenderedItemBounds(ItemRenderState state, MatrixStack matrices) {
        EditedArm arm = itemCaptureArm;
        if (arm == null || state == null || matrices == null) {
            return;
        }

        Box bounds = state.getModelBoundingBox();
        if (bounds == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int framebufferWidth = client.getWindow().getFramebufferWidth();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }

        float fov = client.gameRenderer.getFov(client.gameRenderer.getCamera(), itemCaptureTickDelta, false);
        Matrix4f projection = new Matrix4f().perspective(
                fov * MathHelper.RADIANS_PER_DEGREE,
                framebufferWidth / (float) framebufferHeight,
                0.05f,
                100.0f
        );
        Matrix4f modelView = new Matrix4f(dev.client.api.compat.RenderSystem.getModelViewMatrix())
                .mul(matrices.peek().getPositionMatrix());
        Matrix4f transform = projection.mul(modelView);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int projectedPoints = 0;
        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();

        for (int xi = 0; xi < 2; xi++) {
            float x = (float) (xi == 0 ? bounds.minX : bounds.maxX);
            for (int yi = 0; yi < 2; yi++) {
                float y = (float) (yi == 0 ? bounds.minY : bounds.maxY);
                for (int zi = 0; zi < 2; zi++) {
                    float z = (float) (zi == 0 ? bounds.minZ : bounds.maxZ);
                    Vector4f point = transform.transform(new Vector4f(x, y, z, 1.0f));
                    if (point.w <= 0.001f || !Float.isFinite(point.w)) {
                        continue;
                    }

                    float projectedX = (point.x / point.w * 0.5f + 0.5f) * screenWidth;
                    float projectedY = (0.5f - point.y / point.w * 0.5f) * screenHeight;
                    if (!Float.isFinite(projectedX) || !Float.isFinite(projectedY)) {
                        continue;
                    }

                    minX = Math.min(minX, projectedX);
                    minY = Math.min(minY, projectedY);
                    maxX = Math.max(maxX, projectedX);
                    maxY = Math.max(maxY, projectedY);
                    projectedPoints++;
                }
            }
        }

        if (projectedPoints == 0 || maxX <= minX || maxY <= minY) {
            return;
        }

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float halfWidth = (maxX - minX) * 0.5f;
        float halfHeight = (maxY - minY) * 0.5f;
        float radius = (float) Math.hypot(halfWidth, halfHeight) + 8.0f;
        float maxRadius = Math.min(screenWidth, screenHeight) * 0.36f;
        radius = MathHelper.clamp(radius, 24.0f, maxRadius);
        long now = System.nanoTime();

        if (arm == EditedArm.RIGHT) {
            renderedRightCenterX = centerX;
            renderedRightCenterY = centerY;
            renderedRightRadius = radius;
            renderedRightAnchorNanos = now;
        } else {
            renderedLeftCenterX = centerX;
            renderedLeftCenterY = centerY;
            renderedLeftRadius = radius;
            renderedLeftAnchorNanos = now;
        }
    }

    public void endRenderedItemCapture() {
        itemCaptureArm = null;
    }

    public void stopChatDragging() {
        draggedArm = null;
        rotatingArm = null;
    }

    public void stopChatEditing() {
        stopChatDragging();
        rotationPreviewArm = null;
        rotationKeyHeld = false;
        ctrlAltLatch = false;
    }

    @Override
    public void onDisabled() {
        stopChatEditing();
        freeMoveMode = false;
        itemCaptureArm = null;
        renderedRightAnchorNanos = 0L;
        renderedLeftAnchorNanos = 0L;
        rightScaleFrameNanos = 0L;
        leftScaleFrameNanos = 0L;
        super.onDisabled();
    }

    private boolean canEditThroughChat() {
        MinecraftClient client = MinecraftClient.getInstance();
        return isEnabled()
                && throughChat.getEnabled()
                && client.currentScreen instanceof ChatScreen
                && client.player != null
                && client.world != null
                && client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    private EditedArm findHoveredArm(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }

        boolean rightHovered = hasItem(EditedArm.RIGHT) && isInsideArmArea(EditedArm.RIGHT, mouseX, mouseY);
        boolean leftHovered = hasItem(EditedArm.LEFT) && isInsideArmArea(EditedArm.LEFT, mouseX, mouseY);
        if (rightHovered && leftHovered) {
            return mouseX >= client.getWindow().getScaledWidth() * 0.5 ? EditedArm.RIGHT : EditedArm.LEFT;
        }
        if (rightHovered) {
            return EditedArm.RIGHT;
        }
        return leftHovered ? EditedArm.LEFT : null;
    }

    private boolean isInsideArmArea(EditedArm arm, double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        double width = client.getWindow().getScaledWidth();
        double height = client.getWindow().getScaledHeight();
        boolean right = arm == EditedArm.RIGHT;

        float scale = right ? right_scale.getValue() : left_scale.getValue();
        double centerX = armCenterX(arm);
        double centerY = armCenterY(arm);
        double halfWidth = width * 0.20 * MathHelper.clamp(scale, 0.5f, 1.8f);
        double halfHeight = height * 0.31 * MathHelper.clamp(scale, 0.5f, 1.8f);

        return mouseX >= centerX - halfWidth
                && mouseX <= centerX + halfWidth
                && mouseY >= centerY - halfHeight
                && mouseY <= centerY + halfHeight;
    }

    private EditedArm findRotationTarget(double mouseX, double mouseY) {
        if (rotationPreviewArm != null && isInsideRotationArea(rotationPreviewArm, mouseX, mouseY)) {
            return rotationPreviewArm;
        }
        return findHoveredArm(mouseX, mouseY);
    }

    private boolean isInsideRotationArea(EditedArm arm, double mouseX, double mouseY) {
        double centerX = armCenterX(arm);
        double centerY = armCenterY(arm);
        double radius = rotationRadius(arm) + 14.0;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private double mouseAngle(EditedArm arm, double mouseX, double mouseY) {
        return Math.toDegrees(Math.atan2(armCenterY(arm) - mouseY, mouseX - armCenterX(arm)));
    }

    private double armCenterX(EditedArm arm) {
        if (hasFreshRenderedAnchor(arm)) {
            return arm == EditedArm.RIGHT ? renderedRightCenterX : renderedLeftCenterX;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double width = client.getWindow().getScaledWidth();
        boolean right = arm == EditedArm.RIGHT;
        float xOffset = right ? right_x.getValue() : left_x.getValue();
        return width * (right ? 0.79 : 0.21) + xOffset * width * 0.22;
    }

    private double armCenterY(EditedArm arm) {
        if (hasFreshRenderedAnchor(arm)) {
            return arm == EditedArm.RIGHT ? renderedRightCenterY : renderedLeftCenterY;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double height = client.getWindow().getScaledHeight();
        float yOffset = arm == EditedArm.RIGHT ? right_y.getValue() : left_y.getValue();
        return height * 0.72 - yOffset * height * 0.28;
    }

    private float rotationRadius(EditedArm arm) {
        if (hasFreshRenderedAnchor(arm)) {
            return arm == EditedArm.RIGHT ? renderedRightRadius : renderedLeftRadius;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float width = client.getWindow().getScaledWidth();
        float height = client.getWindow().getScaledHeight();
        float scale = arm == EditedArm.RIGHT ? right_scale.getValue() : left_scale.getValue();
        float radius = Math.min(width * 0.14f, height * 0.24f) * MathHelper.clamp(scale, 0.65f, 1.5f);
        return Math.max(24.0f, Math.min(radius, Math.min(width, height) * 0.36f));
    }

    private boolean hasFreshRenderedAnchor(EditedArm arm) {
        long capturedAt = arm == EditedArm.RIGHT ? renderedRightAnchorNanos : renderedLeftAnchorNanos;
        return capturedAt != 0L && System.nanoTime() - capturedAt <= RENDERED_ANCHOR_LIFETIME_NANOS;
    }

    private Slider rotationFor(EditedArm arm) {
        return arm == EditedArm.RIGHT ? right_rotation : left_rotation;
    }

    private float[] snapToGuides(EditedArm arm, float x, float y, float width, float height) {
        float gridX = width / GUIDE_DIVISIONS;
        float gridY = height / GUIDE_DIVISIONS;
        float baseX = width * (arm == EditedArm.RIGHT ? 0.79f : 0.21f);
        float baseY = height * 0.72f;
        float centerX = baseX + x * width * 0.22f;
        float centerY = baseY - y * height * 0.28f;
        int nearestIndexX = Math.max(1, Math.min(GUIDE_DIVISIONS - 1, Math.round(centerX / gridX)));
        int nearestIndexY = Math.max(1, Math.min(GUIDE_DIVISIONS - 1, Math.round(centerY / gridY)));
        float nearestCenterX = nearestIndexX * gridX;
        float nearestCenterY = nearestIndexY * gridY;

        if (Math.abs(centerX - nearestCenterX) < GUIDE_SNAP_THRESHOLD) {
            x = (nearestCenterX - baseX) / (width * 0.22f);
        }
        if (Math.abs(centerY - nearestCenterY) < GUIDE_SNAP_THRESHOLD) {
            y = (baseY - nearestCenterY) / (height * 0.28f);
        }
        return new float[]{x, y};
    }

    private void updateFreeMoveToggle() {
        boolean altHeld = isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
        boolean ctrlHeld = isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (draggedArm != null && ctrlHeld && altHeld) {
            if (!ctrlAltLatch) {
                freeMoveMode = !freeMoveMode;
                ctrlAltLatch = true;
            }
        } else {
            ctrlAltLatch = false;
        }
    }

    private boolean isKeyDown(int key) {
        return GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private void syncRotationKeyState(double mouseX, double mouseY) {
        if (!isKeyDown(GLFW.GLFW_KEY_Z)) {
            rotationKeyHeld = false;
            rotatingArm = null;
            rotationPreviewArm = null;
            return;
        }

        if (!rotationKeyHeld) {
            EditedArm hovered = findHoveredArm(mouseX, mouseY);
            if (hovered != null) {
                rotationKeyHeld = true;
                rotationPreviewArm = hovered;
            }
        }
    }

    private void drawGuides(DrawContext context) {
        float width = MinecraftClient.getInstance().getWindow().getScaledWidth();
        float height = MinecraftClient.getInstance().getWindow().getScaledHeight();
        Matrix4f matrix = GuiMatrixCompat.toMatrix4(context);
        int color = ColorUtils.rgba(255, 255, 255, 85);

        for (int i = 1; i < GUIDE_DIVISIONS; i++) {
            float x = i * width / GUIDE_DIVISIONS;
            drawRectangle(matrix, x - 0.75f, 0.0f, 1.5f, height, color, 0.0f);
        }
        for (int i = 1; i < GUIDE_DIVISIONS; i++) {
            float y = i * height / GUIDE_DIVISIONS;
            drawRectangle(matrix, 0.0f, y - 0.75f, width, 1.5f, color, 0.0f);
        }
    }

    private void drawRotationGizmo(DrawContext context, EditedArm arm) {
        float centerX = (float) armCenterX(arm);
        float centerY = (float) armCenterY(arm);
        float radius = rotationRadius(arm);
        float rotation = rotationFor(arm).getValue();
        int ringColor = ColorUtils.rgba(255, 255, 255, 210);
        int indicatorColor = 0xFFFFFFFF;

        drawRing(context, centerX, centerY, radius, ROTATION_RING_THICKNESS, ringColor);

        float angle = (float) Math.toRadians(-rotation);
        float endX = centerX + MathHelper.cos(angle) * radius;
        float endY = centerY + MathHelper.sin(angle) * radius;
        drawLine(context, centerX, centerY, endX, endY, 1.5f, indicatorColor);

        Matrix4f matrix = GuiMatrixCompat.toMatrix4(context);
        drawRectangle(matrix, centerX - 2.5f, centerY - 2.5f, 5.0f, 5.0f, indicatorColor, 2.5f);
        drawRectangle(matrix, endX - 3.5f, endY - 3.5f, 7.0f, 7.0f, indicatorColor, 3.5f);

        drawRotationHint(context, centerX, centerY, radius, rotation);
    }

    private void drawRotationHint(DrawContext context, float centerX, float centerY, float radius, float rotation) {
        String hint = "Z + колёсико — изменить угол  " + Math.round(rotation) + "°";
        float fontSize = 8.0f;
        float textWidth = Builder.INTER.get().getWidth(hint, fontSize);
        float textHeight = Builder.INTER.get().getHeight(hint, fontSize);
        float screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        boolean placeBelow = centerY < screenHeight * 0.5f;
        float textX = Math.round(centerX - textWidth * 0.5f);
        float textY = Math.round(placeBelow
                ? centerY + radius + 10.0f
                : centerY - radius - textHeight - 10.0f);

        Builder.TEXT_BUILDER
                .text(hint)
                .size(fontSize)
                .color(0xFFFFFFFF)
                .font(Builder.INTER.get())
                .build()
                .render(GuiMatrixCompat.toMatrix4(context), textX, textY);
    }

    private void drawMoveHints(DrawContext context, EditedArm arm) {
        String tipMain = "Ctrl + Alt свободное перемещение";
        String tipLock = "ALT — блок X, SHIFT — блок Y";
        float fontSize = 8.0f;
        float centerX = (float) armCenterX(arm);
        float centerY = (float) armCenterY(arm);
        float radius = rotationRadius(arm);
        float mainWidth = Builder.INTER.get().getWidth(tipMain, fontSize);
        float mainHeight = Builder.INTER.get().getHeight(tipMain, fontSize);
        float lockWidth = Builder.INTER.get().getWidth(tipLock, fontSize);
        float lockHeight = Builder.INTER.get().getHeight(tipLock, fontSize);
        float screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        boolean placeBelow = centerY < screenHeight * 0.5f;
        float mainY = placeBelow
                ? centerY + radius + 12.0f
                : centerY - radius - 6.0f - mainHeight;
        float lockY = mainY - lockHeight - 2.0f;
        Matrix4f matrix = GuiMatrixCompat.toMatrix4(context);

        Builder.TEXT_BUILDER
                .text(tipMain)
                .size(fontSize)
                .color(0xFFFFFFFF)
                .font(Builder.INTER.get())
                .build()
                .render(matrix, Math.round(centerX - mainWidth * 0.5f), Math.round(mainY));

        Builder.TEXT_BUILDER
                .text(tipLock)
                .size(fontSize)
                .color(0xFFFFFFFF)
                .font(Builder.INTER.get())
                .build()
                .render(matrix, Math.round(centerX - lockWidth * 0.5f), Math.round(lockY));
    }

    private void drawRing(DrawContext context, float centerX, float centerY, float radius, float thickness, int color) {
        int segments = 96;
        float outerRadius = radius + thickness * 0.5f;
        float innerRadius = Math.max(0.0f, radius - thickness * 0.5f);
        Matrix4f matrix = GuiMatrixCompat.toMatrix4(context);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI * 2.0 * i / segments);
            float cos = MathHelper.cos(angle);
            float sin = MathHelper.sin(angle);
            builder.vertex(matrix, centerX + cos * outerRadius, centerY + sin * outerRadius, 0.0f).color(color);
            builder.vertex(matrix, centerX + cos * innerRadius, centerY + sin * innerRadius, 0.0f).color(color);
        }
        RenderCompat.draw(builder.end());
    }

    private void drawLine(DrawContext context, float startX, float startY, float endX, float endY, float thickness, int color) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001f) {
            return;
        }

        float normalX = -dy / length * thickness * 0.5f;
        float normalY = dx / length * thickness * 0.5f;
        Matrix4f matrix = GuiMatrixCompat.toMatrix4(context);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, startX + normalX, startY + normalY, 0.0f).color(color);
        builder.vertex(matrix, startX - normalX, startY - normalY, 0.0f).color(color);
        builder.vertex(matrix, endX + normalX, endY + normalY, 0.0f).color(color);
        builder.vertex(matrix, endX - normalX, endY - normalY, 0.0f).color(color);
        RenderCompat.draw(builder.end());
    }

    private void drawRectangle(Matrix4f matrix, float x, float y, float width, float height, int color, float radius) {
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .build()
                .render(matrix, x, y);
    }

    private boolean hasItem(EditedArm arm) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }

        boolean mainArm = client.player.getMainArm() == (arm == EditedArm.RIGHT ? Arm.RIGHT : Arm.LEFT);
        ItemStack stack = mainArm ? client.player.getMainHandStack() : client.player.getOffHandStack();
        return !stack.isEmpty()
                && !(stack.getItem() instanceof FilledMapItem)
                && !client.player.isUsingSpyglass();
    }

    private boolean validFramebuffers() {
        return beforeHandFbo != null
                && withHandFbo != null
                && beforeHandFbo.getColorAttachmentView() != null
                && withHandFbo.getColorAttachmentView() != null
                && beforeHandFbo.textureWidth == withHandFbo.textureWidth
                && beforeHandFbo.textureHeight == withHandFbo.textureHeight;
    }

    private static float roundHundredth(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    private enum EditedArm {
        LEFT,
        RIGHT
    }
}
