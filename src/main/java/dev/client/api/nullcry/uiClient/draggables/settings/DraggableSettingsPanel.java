package dev.client.api.nullcry.uiClient.draggables.settings;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.Collection;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.uiClient.clickGui.api.Helper;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class DraggableSettingsPanel implements ClientApi {
    private final DraggableHandler handler;
    private final SettingProvider provider;
    private final List<SettingComponent> components = new ArrayList<>();

    private boolean initialized = false;
    private boolean open = false;
    private float scroll = 0f;

    private int lastMouseX;
    private int lastMouseY;

    private float panelX, panelY, panelWidth, panelHeight;

    private final CompactAnimation alphaAnimation = new CompactAnimation(Easing.EASE_OUT_QUAD, 300);

    private static final float FIXED_PANEL_WIDTH = 140f;
    private static final float PADDING = 5f;
    private static final float GAP = 5f;
    private static final float MAX_VISIBLE_HEIGHT = 130f;
    private static final float OFFSET_X = 6f;
    private static final float OFFSET_Y = 1f;
    private static final float COMPONENT_START_OFFSET_Y = 1.5f;
    private static final float CONTENT_INSET = 2f;
    private static final float CLICK_OFFSET_X = 0f;
    private static final float CLICK_OFFSET_Y = 0f;
    private static final float COMPONENT_CLIP_PADDING = 14f;

    private boolean useOverridePosition = false;
    private float overrideX;
    private float overrideY;
    private boolean freezePosition = false;

    public DraggableSettingsPanel(DraggableHandler handler, SettingProvider provider) {
        this.handler = handler;
        this.provider = provider;
    }

    public SettingProvider getProvider() {
        return provider;
    }

    private boolean isHandlerActive() {
        return handler == null || handler.isActive();
    }

    public boolean hasSettings() {
        ensureInitialized();
        return !components.isEmpty();
    }

    public void toggle() {
        if (!hasSettings() || !isHandlerActive()) return;
        if (!open) {
            scroll = 0f;
            useOverridePosition = false;
            freezePosition = false;
        }
        open = !open;
    }

    public void toggleAt(float mouseX, float mouseY) {
        if (!hasSettings() || !isHandlerActive()) return;
        if (!open) {
            scroll = 0f;
            useOverridePosition = true;
            double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
            double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);
            overrideX = (float) renderMouseX + CLICK_OFFSET_X;
            overrideY = (float) renderMouseY + CLICK_OFFSET_Y;
            freezePosition = false;
            open = true;
        } else {
            open = false;
        }
    }

    public void close() {
        open = false;
        freezePosition = false;
    }

    public void closePreservingPosition() {
        if (!hasSettings()) {
            open = false;
            freezePosition = false;
            return;
        }

        if (isVisible()) {
            useOverridePosition = true;
            overrideX = panelX;
            overrideY = panelY;
            freezePosition = true;
        }

        open = false;
    }

    public boolean isOpen() {
        if (!isHandlerActive()) {
            close();
            return false;
        }
        return open && hasSettings();
    }

    public boolean isVisible() {
        if (!isHandlerActive()) {
            close();
            return false;
        }
        return hasSettings() && (open || alphaAnimation.getValue() > 0.01);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isHandlerActive()) {
            close();
            alphaAnimation.run(0.0);
            alphaAnimation.update();
            return;
        }
        boolean pushed = false;
        if (!GLUtils.isOverlayRenderingActive()) {
            GLUtils.setupOverlayRendering(2);
            pushed = true;
        }
        try {
            ensureInitialized();

            alphaAnimation.run(open ? 1.0 : 0.0);
            alphaAnimation.update();

            float baseAlpha = 1f;
            if (provider instanceof PanelAlphaProvider alphaProvider) {
                baseAlpha = MathHelper.clamp(alphaProvider.getPanelAlpha(), 0f, 1f);
            }

            float show = (float) (alphaAnimation.getValue() * baseAlpha);
            if (show <= 0.01f && !open) {
                return;
            }

            int renderMouseX = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int renderMouseY = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            float contentWidth = computeContentWidth();
            float contentHeight = computeContentHeight();
            float visibleHeight = Math.min(contentHeight, MAX_VISIBLE_HEIGHT);

            float marginWidth = (PADDING + CONTENT_INSET) * 2f;
            panelWidth = FIXED_PANEL_WIDTH;
            panelHeight = visibleHeight + PADDING * 2f;

            var window = MinecraftClient.getInstance().getWindow();
            float screenWidth = window.getScaledWidth();
            float screenHeight = window.getScaledHeight();

            float anchorX;
            float anchorY;

            float margin = 4f;

            if (useOverridePosition || freezePosition) {
                float maxX = Math.max(margin, screenWidth - panelWidth - margin);
                float maxY = Math.max(margin, screenHeight - panelHeight - margin);

                anchorX = Math.max(margin, Math.min(overrideX, maxX));
                anchorY = Math.max(margin, Math.min(overrideY, maxY));

                overrideX = anchorX;
                overrideY = anchorY;
            } else {
                anchorX = handler.getRenderRight() + OFFSET_X;
                anchorY = handler.getRenderY() + OFFSET_Y;

                if (anchorX + panelWidth > screenWidth - margin) {
                    anchorX = handler.getRenderX() - OFFSET_X - panelWidth;
                }

                if (anchorX < margin) {
                    anchorX = Math.max(margin, screenWidth - panelWidth - margin);
                }

                if (anchorY + panelHeight > screenHeight - margin) {
                    anchorY = screenHeight - panelHeight - margin;
                }

                if (anchorY < margin) {
                    anchorY = margin;
                }
            }

            panelX = anchorX;
            panelY = anchorY;

            if (!open && freezePosition && alphaAnimation.getValue() <= 0.01f) {
                freezePosition = false;
            }

            HelperElements.rectElements(context, panelX, panelY, panelWidth, panelHeight, show);

            float clipHeight = visibleHeight;

            float clipX = panelX + PADDING + CONTENT_INSET;
            float clipY = panelY + PADDING + CONTENT_INSET;
            float clipWidthAvailable = Math.max(0f, panelWidth - marginWidth);
            float clipWidth = clipWidthAvailable;

            float maxScroll = Math.max(0f, contentHeight - clipHeight);
            if (scroll > maxScroll) scroll = maxScroll;
            if (scroll < 0f) scroll = 0f;

            float scissorInset = 1.0f;
            float scissorX = clipX - scissorInset;
            float scissorY = clipY;
            float scissorW = clipWidth + scissorInset * 2f;
            float scissorH = clipHeight;

            if (scissorX < panelX) {
                scissorW -= (panelX - scissorX);
                scissorX = panelX;
            }

            float panelRight = panelX + panelWidth;
            float scissorRight = scissorX + scissorW;
            if (scissorRight > panelRight) {
                scissorW = Math.max(0f, panelRight - scissorX);
            }

            ScissorUtil.enable(scissorX, scissorY, scissorW, scissorH);

            float currentY = clipY + COMPONENT_START_OFFSET_Y - scroll;
            for (SettingComponent component : components) {
                if (!component.getSetting().getShown().get()) continue;
                component.setGlobalAlpha(show);
                float targetWidth = clipWidth > 0f ? clipWidth : component.getWidth();
                component.size(targetWidth, component.getHeight());
                component.position(clipX, currentY);
                component.render(context, renderMouseX, renderMouseY, delta);
                currentY += component.getHeight() + GAP;
            }

            ScissorUtil.disable();

            if (maxScroll > 0.5f) {
                float scrollbarHeight = Math.max(16f, clipHeight * (clipHeight / (contentHeight + 0.0001f)));
                float scrollbarProgress = scroll / maxScroll;
                float scrollbarY = clipY + (clipHeight - scrollbarHeight) * scrollbarProgress;
                float scrollbarX = panelX + panelWidth - PADDING / 2f - 1.5f;

                Builder.RECTANGLE_BUILDER
                        .size(new dev.client.api.nullcry.render.core.builders.states.SizeState(2f, scrollbarHeight))
                        .color(new dev.client.api.nullcry.render.core.builders.states.QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (160 * show))))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), scrollbarX, scrollbarY);
            }
        } finally {
            if (pushed) {
                GLUtils.restoreRendering();
            }
        }
    }

    public boolean renderLingering(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isHandlerActive()) {
            close();
            return false;
        }
        if (!hasSettings() || open) return false;
        if (alphaAnimation.isDone() && alphaAnimation.getValue() <= 0.01f) {
            return false;
        }

        int renderMouseX = mouseX >= 0 ? mouseX : lastMouseX;
        int renderMouseY = mouseY >= 0 ? mouseY : lastMouseY;

        render(context, renderMouseX, renderMouseY, delta);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isHandlerActive()) return false;

        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);

        if (!isInside(renderMouseX, renderMouseY)) {
            if (button == 0) {
                close();
            }
            return false;
        }

        for (SettingComponent component : components) {
            if (!component.getSetting().getShown().get()) continue;
            if (component.mouseClicked(renderMouseX, renderMouseY, button)) {
                return true;
            }
        }

        if (button == 1) {
            close();
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isHandlerActive()) return false;
        boolean handled = false;
        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);
        for (SettingComponent component : components) {
            if (!component.getSetting().getShown().get()) continue;
            handled |= component.mouseReleased(renderMouseX, renderMouseY, button);
        }
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);
        if (!isVisible() || !isHandlerActive() || !isInside(renderMouseX, renderMouseY)) return false;

        float contentHeight = computeContentHeight();
        float visibleHeight = Math.min(contentHeight, MAX_VISIBLE_HEIGHT);
        float maxScroll = Math.max(0f, contentHeight - visibleHeight);
        if (maxScroll <= 0f) return false;

        scroll -= verticalAmount * 6f;
        if (scroll < 0f) scroll = 0f;
        if (scroll > maxScroll) scroll = maxScroll;
        return true;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);
        return isVisible() && isInside(renderMouseX, renderMouseY);
    }

    private float computeContentWidth() {
        ensureInitialized();
        float maxWidth = 0f;
        for (SettingComponent component : components) {
            maxWidth = Math.max(maxWidth, component.getWidth());
        }
        return Math.max(0f, maxWidth);
    }

    private float computeContentHeight() {
        ensureInitialized();
        float total = 0f;
        boolean any = false;
        for (SettingComponent component : components) {
            if (!component.getSetting().getShown().get()) continue;
            total += component.getHeight();
            total += GAP;
            any = true;
        }
        if (any) total -= GAP;
        return Math.max(0f, total);
    }

    private void ensureInitialized() {
        if (initialized) return;
        components.clear();
        for (Setting setting : provider.getSettings()) {
            if (setting instanceof ColorPicker || setting instanceof Collection) continue;
            var component = Helper.find(setting);
            if (component == null) continue;
            component.init();
            component.setClipPaddingOverride(COMPONENT_CLIP_PADDING);
            components.add(component);
        }
        initialized = true;
    }
}
