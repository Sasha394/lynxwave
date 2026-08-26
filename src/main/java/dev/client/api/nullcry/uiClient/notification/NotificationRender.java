package dev.client.api.nullcry.uiClient.notification;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltText;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.render.Interface;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Getter
@Setter
public class NotificationRender implements ClientApi {
    private NotificationType notificationType;
    private final RenderType renderType;
    private ItemStack itemStack;
    public CompactAnimation animation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    final long time = System.currentTimeMillis();
    int time2;

    public final String text;
    public final Text textFormatted;
    float x;
    float y;

    public NotificationRender(String text, int time, NotificationType type, RenderType renderType) {
        this.x = mc.getWindow() != null ? calc(mc.getWindow().getScaledWidth()) + 200 : 9999f;
        this.text = text;
        this.textFormatted = null;
        this.time2 = time;
        this.notificationType = type;
        this.renderType = renderType;
        this.itemStack = ItemStack.EMPTY;
    }

    public NotificationRender(Text text, int time, NotificationType type, RenderType renderType) {
        this.x = mc.getWindow() != null ? calc(mc.getWindow().getScaledWidth()) + 200 : 9999f;
        this.text = null;
        this.textFormatted = text;
        this.time2 = time;
        this.notificationType = type;
        this.renderType = renderType;
        this.itemStack = ItemStack.EMPTY;
    }

    public NotificationRender(Text textFormatted, int time, NotificationType type, RenderType renderType, ItemStack itemStack) {
        this.x = mc.getWindow() != null ? calc(mc.getWindow().getScaledWidth()) + 200 : 9999f;
        this.text = null;
        this.textFormatted = textFormatted;
        this.time2 = time;
        this.notificationType = type;
        this.renderType = renderType;
        this.itemStack = itemStack;
    }

    public float getWidth() {
        final float leftPaddingToText = 24f;
        final float rightMargin = 6f;
        final float textSize = 8f;

        float textWidth = measureTextWidth(textSize);

        int iconSize = getIconSize();
        float iconWidth = Builder.ICONS.get().getWidth(getIconChar(), iconSize);
        float sepOffset = iconWidth + getSepPadding(iconSize);

        float textStart = Math.max(leftPaddingToText, sepOffset + 1f);
        return textStart + textWidth + rightMargin;
    }

    public float getHeight() {
        float margin = 9.5f;
        float textHeight = measureTextHeight(8f);
        return margin + textHeight + 1f;
    }

    public void draw(DrawContext context, MatrixStack matrices, float drawX, float drawY, float alpha) {
        float margin = 9.5f;
        float width = getWidth();
        float textHeight = measureTextHeight(8f);
        float height = margin + textHeight + 1;
        int finalAlpha = (int) (255 * alpha);

        HelperElements.rectElements(context, drawX, drawY, width, height, alpha);

        BuiltText builtText = buildText(8f, finalAlpha);
        if (builtText != null) {
            builtText.render(matrices.peek().getPositionMatrix(), drawX + 25, drawY + 5);
        }

        int iconSize = getIconSize();
        float iconX = drawX + (notificationType == NotificationType.CONFIG ? 7.5f : notificationType == NotificationType.WARNING ? 6.5f : 6f);
        float iconY = drawY + (notificationType == NotificationType.CONFIG || notificationType == NotificationType.WARNING ? 5.5f : 5f);
        float iconWidth = Builder.ICONS.get().getWidth(getIconChar(), iconSize);
        float sepX = (float) Math.floor(drawX + iconWidth + getSepPadding(iconSize) + 0.5f);

        Builder.TEXT_BUILDER
                .text(getIconChar())
                .size(iconSize)
                .font(Builder.ICONS.get())
                .color(getIconColor(finalAlpha))
                .build()
                .render(matrices.peek().getPositionMatrix(), iconX, iconY);

        float separatorHeight = 9f;
        float sepY = drawY + (height - separatorHeight) / 2f + 0.5f;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(1f, separatorHeight))
                .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), finalAlpha)))
                .radius(new QuadRadiusState(1f))
                .build()
                .render(matrices.peek().getPositionMatrix(), sepX, sepY);
    }

    private float measureTextHeight(float textSize) {
        float textHeight = 0f;
        if (text != null) {
            textHeight = Builder.INTER.get().getHeight(text, textSize);
        } else if (textFormatted != null) {
            textHeight = Builder.INTER.get().getHeight(textFormatted.getString(), textSize);
        }
        return Math.max(9f, textHeight);
    }

    private float measureTextWidth(float textSize) {
        BuiltText builtText = buildText(textSize, 255);
        return builtText != null ? builtText.measureWidth() : 0f;
    }

    private BuiltText buildText(float textSize, int alpha) {
        if (text == null && textFormatted == null) {
            return null;
        }

        int color = HelperElements.themedText(alpha);

        if (text != null) {
            return Builder.TEXT_BUILDER
                    .text(text)
                    .size(textSize)
                    .font(Builder.INTER.get())
                    .color(color)
                    .build();
        }

        return Builder.TEXT_BUILDER
                .size(textSize)
                .font(Builder.INTER.get())
                .rarityFallback(itemStack)
                .color(color)
                .text(textFormatted)
                .build();
    }

    private int getIconSize() {
        return switch (notificationType) {
            case CONFIG -> 9;
            case INFO, WARNING -> 10;
            default -> 11;
        };
    }

    private float getSepPadding(int iconSize) {
        return 20f - iconSize;
    }

    private String getIconChar() {
        return switch (notificationType) {
            case SUCCESS -> "P";
            case ERROR -> "Q";
            case INFO -> "R";
            case WARNING -> "S";
            case CONFIG -> "T";
        };
    }

    private int getIconColor(int alpha) {
        return switch (notificationType) {
            case SUCCESS -> ColorUtils.setAlpha(ColorUtils.rgb(0, 255, 0), alpha);
            case ERROR -> ColorUtils.setAlpha(ColorUtils.rgb(255, 0, 0), alpha);
            case INFO -> HelperElements.themedWhite(alpha);
            case WARNING -> ColorUtils.setAlpha(ColorUtils.rgb(255, 255, 0), alpha);
            case CONFIG -> HelperElements.themedWhite(alpha);
        };
    }
}
