package dev.client.api.nullcry.uiClient.draggables;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;

import java.util.Objects;
import java.util.function.IntSupplier;

public final class DraggableHeaderRenderer implements ClientApi {
    private static final float PADDING_X = 6f;
    private static final float SPACING = 5f;
    private static final float SEPARATOR_WIDTH = 1f;
    private static final float SEPARATOR_HEIGHT = 9f;

    private DraggableHeaderRenderer() {
    }

    public static void render(RenderEvent.Draw2D event,
                              float x,
                              float y,
                              float width,
                              float showProgress,
                              float headerHeight,
                              String iconText,
                              float iconSize,
                              String title,
                              float textSize,
                              int baseTextColor,
                              int baseIconColor,
                              IntSupplier separatorColorSupplier) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(separatorColorSupplier, "separatorColorSupplier");

        float clampedShow = clamp01(showProgress);

        HelperElements.rectElements(event.getContext(), x, y, width, headerHeight, showProgress);

        float iconBaseX = x + PADDING_X;
        float iconBaseY = y + (headerHeight - iconSize) / 2f + 0.5f;
        float iconWidth = 0f;

        if (iconText != null && !iconText.isEmpty()) {
            int iconColor = separatorColorSupplier.getAsInt();
            Builder.TEXT_BUILDER
                    .text(iconText)
                    .font(Builder.ICONS.get())
                    .size(iconSize)
                    .color(ColorUtils.setAlpha(iconColor, (int) (255 * clampedShow)))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), iconBaseX, iconBaseY);
            iconWidth = Builder.ICONS.get().getWidth(iconText, iconSize);
        }

        float sepX = Math.round(iconBaseX + iconWidth + SPACING);
        float sepY = Math.round(y + (headerHeight - SEPARATOR_HEIGHT) / 2f) + 0.5f;

        int separatorBaseColor = separatorColorSupplier.getAsInt();
        int separatorColor = ColorUtils.setAlpha(separatorBaseColor, (int) (255 * clampedShow));

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(SEPARATOR_WIDTH, SEPARATOR_HEIGHT))
                .color(new QuadColorState(separatorColor))
                .radius(new QuadRadiusState(1f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), sepX, sepY);

        float textX = sepX + SEPARATOR_WIDTH + SPACING;
        float textY = y + (headerHeight - textSize) / 2f - 0.5f;

        int resolvedTextColor = baseTextColor == -1
                ? HelperElements.themedText((int) (255 * clampedShow))
                : ColorUtils.setAlpha(baseTextColor, (int) (255 * clampedShow));

        Builder.TEXT_BUILDER
                .text(title)
                .font(Builder.INTER.get())
                .size(textSize)
                .color(resolvedTextColor)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), textX, textY);

        int accentBaseColor = separatorColorSupplier.getAsInt();
        int accentAlpha = (int) (45 * clampedShow);
        int accentFadeAlpha = (int) (8 * clampedShow);
        float lineY = y + headerHeight - 0.5f;

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, 0.5f))
                .color(new QuadColorState(
                        ColorUtils.setAlpha(accentBaseColor, accentAlpha),
                        ColorUtils.setAlpha(accentBaseColor, accentFadeAlpha),
                        ColorUtils.setAlpha(accentBaseColor, accentFadeAlpha),
                        ColorUtils.setAlpha(accentBaseColor, accentAlpha)
                ))
                .radius(new QuadRadiusState(0f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), x, lineY);
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}
