package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.checkboxSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;

public class CheckBoxComponent extends SettingComponent {
    boolean isHovered;
    float textScroll = 0;
    long scrollTimer = System.currentTimeMillis();

    public CheckBoxComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float moduleNameHeight = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 5;
        size(240f / 2 - 10, moduleNameHeight);
    }

    @Override
    public CheckBoxComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        String text = getSetting().getName();
        float textSize = 7f;
        final float defaultClipPadding = 18f;
        float clipPadding = clipPadding(defaultClipPadding);

        float visibility = getGlobalAlpha();
        float textX = getX();
        float textY = getY() - 1f;

        float iconInset = defaultClipPadding - 16f;
        float iconX = getX() + getWidth() - clipPadding + iconInset - 6f;
        float iconY = getY() - 1.5f;
        float boxW = 19f;
        float boxH = 10f;
        float knobSize = 7.4f;

        float maxTextWidth = Math.max(0f, iconX - getX() - 1.8f);

        float textWidth = Builder.INTER.get().getWidth(text, textSize);
        float textHeight = Builder.INTER.get().getHeight(text, textSize);

        float scrollSpeed = 1.25f;
        float loopOffset = textWidth + 20f;

        boolean isHoveredText = MouseClick.isClick(mouseX, mouseY, textX, getY(), maxTextWidth, textHeight);

        if (textWidth > maxTextWidth) {
            if (isHoveredText) {
                if (System.currentTimeMillis() - scrollTimer > 15) {
                    textScroll += scrollSpeed;
                    if (textScroll > loopOffset) {
                        textScroll = 0;
                    }
                    scrollTimer = System.currentTimeMillis();
                }
            } else {
                if (textScroll > loopOffset / 2f) {
                    textScroll = Animation.fast(textScroll, loopOffset, 10);
                    if (textScroll >= loopOffset - 0.5f) {
                        textScroll = 0;
                    }
                } else {
                    textScroll = Animation.fast(textScroll, 0, 10);
                }
            }
        } else {
            textScroll = 0;
        }

        ScissorUtil.enable(textX, getY() - 1f, maxTextWidth, getHeight());
        Builder.TEXT_BUILDER
                .size(textSize)
                .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                .text(text)
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), textX - textScroll, textY);

        if (textWidth > maxTextWidth) {
            Builder.TEXT_BUILDER
                    .size(textSize)
                    .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                    .text(text)
                    .font(Builder.INTER.get())
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), textX - textScroll + loopOffset, textY);
        }

        ScissorUtil.disable();

        boolean isEnabled = ((CheckBox) getSetting()).getEnabled();
        float animationValue = getSetting().getAnimation().getOutput().floatValue();
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        int c1 = Interface.INSTANCE.getFirstColor();
        int c2 = Interface.INSTANCE.getSecondColor();

        float backgroundAlpha = 150f + 25f * animationValue;
        int backgroundAlphaInt = Math.round(backgroundAlpha * visibility);

        // Subtle glow behind toggle when enabled
        if (animationValue > 0.01f) {
            Builder.GLOW_BUILDER
                    .size(new SizeState(boxW, boxH))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, (int) (18 * visibility * animationValue)),
                            ColorUtils.setAlpha(c2, (int) (12 * visibility * animationValue)),
                            ColorUtils.setAlpha(c2, (int) (6 * visibility * animationValue)),
                            ColorUtils.setAlpha(c1, (int) (10 * visibility * animationValue))
                    ))
                    .softness(0.6f)
                    .glowRadius(2f)
                    .build()
                    .render(matrix, iconX, iconY + 0.5f);
        }

        if (!Interface.INSTANCE.isExcellentMode() && Interface.INSTANCE.blurStrength.getValue() > 0) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                    .radius(new QuadRadiusState(4f))
                    .size(new SizeState(boxW, boxH))
                    .alpha(visibility)
                    .build()
                    .render(matrix, iconX, iconY + 0.5f);
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(boxW, boxH))
                .color(new QuadColorState(
                        HelperElements.themedPanel(28, 30, 35, backgroundAlphaInt),
                        HelperElements.themedPanel(24, 26, 30, backgroundAlphaInt),
                        HelperElements.themedPanel(22, 24, 28, backgroundAlphaInt),
                        HelperElements.themedPanel(26, 28, 33, backgroundAlphaInt)
                ))
                .radius(new QuadRadiusState(4f))
                .build()
                .render(matrix, iconX, iconY + 0.5f);

        // Gradient border on toggle track
        int borderAlpha = isEnabled ? (int) (60 * visibility * animationValue) : (int) (15 * visibility);
        if (borderAlpha > 0) {
            Builder.BORDER_BUILDER
                    .size(new SizeState(boxW, boxH))
                    .radius(new QuadRadiusState(4f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(c1, borderAlpha),
                            ColorUtils.setAlpha(c2, borderAlpha),
                            ColorUtils.setAlpha(c2, borderAlpha / 2),
                            ColorUtils.setAlpha(c1, borderAlpha / 2)
                    ))
                    .thickness(-0.1f)
                    .build()
                    .render(matrix, iconX, iconY + 0.5f);
        }

        float knobRadius = knobSize / 2.5f;
        float knobX = iconX + 1.5f + (animationValue * (boxW - knobSize - 2.9f));
        float knobY = iconY + 1.7f;

        // Gradient knob when enabled
        int knobColorTL, knobColorTR, knobColorBR, knobColorBL;
        if (isEnabled) {
            knobColorTL = ColorUtils.setAlpha(c1, (int) (255 * visibility));
            knobColorTR = ColorUtils.setAlpha(ColorUtils.interpolate(c1, c2, 0.4f), (int) (255 * visibility));
            knobColorBR = ColorUtils.setAlpha(c2, (int) (240 * visibility));
            knobColorBL = ColorUtils.setAlpha(ColorUtils.interpolate(c1, c2, 0.6f), (int) (240 * visibility));
        } else {
            int gray = ColorUtils.rgb(120, 120, 120);
            int grayAlpha = (int) (255 * visibility);
            knobColorTL = knobColorTR = knobColorBR = knobColorBL = ColorUtils.setAlpha(gray, grayAlpha);
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(knobSize, knobSize))
                .radius(new QuadRadiusState(knobRadius))
                .color(new QuadColorState(knobColorTL, knobColorTR, knobColorBR, knobColorBL))
                .build()
                .render(matrix, knobX, knobY);

        isHovered = MouseClick.isClick(mouseX, mouseY, iconX, iconY, boxW, boxH);
        if (isHovered) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final float defaultClipPadding = 21f;
        float clipPadding = clipPadding(defaultClipPadding - 3f);
        float iconInset = defaultClipPadding - 16f;
        float iconX = getX() + getWidth() - clipPadding + iconInset - 6f;
        float iconY = getY() - 1.5f;
        float boxW = 19f;
        float boxH = 10f;

        boolean hit = MouseClick.isClick(mouseX, mouseY, iconX, iconY, boxW, boxH);
        if (hit) {
            CheckBox checkBox = (CheckBox) getSetting();
            checkBox.applyDefault(!checkBox.getEnabled());
            return true;
        }
        return false;
    }
}
