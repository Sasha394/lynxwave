package dev.client.api.nullcry.uiClient.autobuy.window;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuyItemSettings;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuySettingsComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class AutoBuyItemSettingsWindow {
    private final List<AutoBuySettingsComponent> components = new ArrayList<>();
    public final AutoBuyableItem item;
    private final AutoBuyItemSettings settings;

    private float x;
    private float y;
    private float width = 180f;
    private float height = 120f;

    private float scroll = 0f;
    private float smoothedScroll = 0f;
    private boolean dragging = false;
    private float dragX;
    private float dragY;
    private boolean closed = false;

    public AutoBuyItemSettingsWindow(AutoBuyableItem item, AutoBuyItemSettings settings) {
        this.item = item;
        this.settings = settings;
        initializeComponents();
    }

    private void initializeComponents() {
        components.add(new AutoBuySettingsComponent.BuyBelowComponent(settings));
        if (settings.isCanHaveQuantity()) {
            components.add(new AutoBuySettingsComponent.MinQuantityComponent(settings));
        }
    }

    public AutoBuyItemSettingsWindow position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public AutoBuyItemSettingsWindow size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (closed) return;

        if (dragging) {
            x = mouseX + dragX;
            y = mouseY + dragY;
        }

        float contentHeight = 0f;
        for (AutoBuySettingsComponent component : components) {
            contentHeight += component.getHeight();
        }

        height = MathHelper.clamp(contentHeight + 28f, 0f, 200f);
        boolean isLimitedHeight = MathHelper.clamp(height, 0f, 200f) == 200f;

        float maxScroll = Math.max(0f, contentHeight - (height - 28f));
        scroll = MathHelper.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = MathHelper.lerp(0.1f, smoothedScroll, scroll);

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        int bgColor = ColorUtils.rgba(18, 19, 20, 200);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .radius(new QuadRadiusState(8f))
                .color(new QuadColorState(bgColor))
                .build()
                .render(matrix, x, y);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, 1f))
                .radius(new QuadRadiusState(0f))
                .color(new QuadColorState(ColorUtils.rgba(155, 155, 155, 55)))
                .build()
                .render(matrix, x, y + 22f);

        ItemStack itemStack = item.createItemStack();
        DrawUtil.drawItemStack(context, itemStack, x + 7, y + 4, 0.8f);

        String title = item.getDisplayName();
        Builder.TEXT_BUILDER
                .text(title)
                .size(8.5f)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), 230))
                .build()
                .render(matrix, x + 25f, y + 9.5f);

        Builder.TEXT_BUILDER
                .text("X")
                .size(8.5f)
                .font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(-1, 180))
                .build()
                .render(matrix, x + width - 16f, y + 8.5f);

        if (isLimitedHeight) {
            ScissorUtil.enableContext(context, x, y + 23f, width, height - 24f);
        }

        float offset = 0f;
        for (AutoBuySettingsComponent component : components) {
            component.position(x, y + 24f + offset + smoothedScroll)
                    .size(width, component.getHeight())
                    .render(context, mouseX, mouseY, delta);
            offset += component.getHeight() + 2f;
        }

        if (isLimitedHeight) {
            ScissorUtil.disableContext(context);
        }

        if (isLimitedHeight && maxScroll > 0f) {
            float viewableHeight = height - 30f;
            float scrollbarHeight = Math.max(20f, (viewableHeight / contentHeight) * viewableHeight);
            float scrollPercent = -smoothedScroll / maxScroll;
            float scrollbarY = y + 30f + (scrollPercent * (viewableHeight - scrollbarHeight));

            float scrollbarX = x + width - 6f;
            float scrollbarWidth = 3f;

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(scrollbarWidth, viewableHeight - 6f))
                    .radius(new QuadRadiusState(1f))
                    .color(new QuadColorState(ColorUtils.rgba(30, 30, 30, 100)))
                    .build()
                    .render(matrix, scrollbarX, y + 30f);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(scrollbarWidth, Math.max(8f, scrollbarHeight - 6f)))
                    .radius(new QuadRadiusState(1.5f))
                    .color(new QuadColorState(ColorUtils.rgba(100, 100, 100, 180)))
                    .build()
                    .render(matrix, scrollbarX, scrollbarY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closed) return false;

        if (button == 0) {
            if (MouseClick.isClick(mouseX, mouseY, x + width - 20f, y + 5f, 15f, 15f)) {
                closed = true;
                return true;
            }
            if (MouseClick.isClick(mouseX, mouseY, x, y, width, 19f)) {
                dragging = true;
                dragX = (float) (x - mouseX);
                dragY = (float) (y - mouseY);
                return true;
            }
        }

        boolean handled = false;
        for (AutoBuySettingsComponent component : components) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                handled = true;
            }
        }
        return handled;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        for (AutoBuySettingsComponent component : components) {
            component.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean scrolled = MathHelper.clamp(height, 0, 200) == 200 && MouseClick.isClick(mouseX, mouseY, x, y, width, height);
        if (scrolled) {
            scroll += amount * 20f;
        }
        for (AutoBuySettingsComponent component : components) {
            component.mouseScrolled(mouseX, mouseY, 0, amount);
        }
        return scrolled;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AutoBuySettingsComponent component : components) {
            component.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        for (AutoBuySettingsComponent component : components) {
            component.charTyped(chr, modifiers);
        }
        return false;
    }

    public int getComponentHeight() {
        float offsetY = 0;
        for (AutoBuySettingsComponent component : components) {
            offsetY += component.getHeight();
        }
        return (int) (offsetY + 25);
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isForItem(AutoBuyableItem other) {
        if (other == null) {
            return false;
        }
        String a = item.getDisplayName();
        String b = other.getDisplayName();
        if (a == null || b == null) {
            return item.equals(other);
        }
        return a.equalsIgnoreCase(b);
    }
}
