package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.inputSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class InputComponent extends SettingComponent {
    static InputComponent focusedComponent = null;
    boolean isFocused = false;
    boolean ctrlASelected = false;
    boolean isHovered;

    public InputComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float moduleNameHeight = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 17;
        size(240f / 2 - 10, moduleNameHeight);
    }

    @Override
    public InputComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        Input inputSetting = (Input) getSetting();
        float animation = getSetting().getAnimation().getOutput().floatValue();
        float visibility = getGlobalAlpha();

        Builder.TEXT_BUILDER
                .size(7)
                .color(HelperElements.themedText(Math.round(242 * visibility)))
                .text(getSetting().getName())
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX(), getY() - 1);

        float rectY = getY() + getHeight() - 13;
        float rectHeight = 12;

        if (!Interface.INSTANCE.isExcellentMode() && Interface.INSTANCE.blurStrength.getValue() > 0) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                    .radius(new QuadRadiusState(2f))
                    .size(new SizeState(getWidth() - 4, rectHeight))
                    .alpha(visibility)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() - 1, rectY);
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(getWidth() - 4, rectHeight))
                .color(new QuadColorState(
                        HelperElements.themedPanel(28, 30, 35, (int) (150 * visibility)),
                        HelperElements.themedPanel(24, 26, 30, (int) (150 * visibility)),
                        HelperElements.themedPanel(22, 24, 28, (int) (150 * visibility)),
                        HelperElements.themedPanel(26, 28, 33, (int) (150 * visibility))
                ))
                .radius(new QuadRadiusState(2f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() - 1, rectY);

        String renderText = inputSetting.getValue();
        boolean showPlaceholder = renderText.isEmpty() && !isFocused;
        boolean showCursor = isFocused && !ctrlASelected && (System.currentTimeMillis() / 500) % 2 == 0;
        String displayText = showPlaceholder ? "Описание отсутствует." : renderText + (showCursor ? "_" : "");

        int placeholderColor = ColorUtils.rgba(150, 150, 150, Math.round((1.0f - animation) * 255 * visibility));
        int textColor = HelperElements.themedText(Math.round(animation * 255 * visibility));

        final float defaultClipPadding = 10f;
        float clipPadding = clipPadding(defaultClipPadding);
        if (clipPadding > defaultClipPadding) {
            clipPadding = defaultClipPadding;
        }
        float clipWidth = Math.max(0f, getWidth() - clipPadding);
        ScissorUtil.enable(getX(), rectY, clipWidth, rectHeight);
        if (!showPlaceholder && ctrlASelected) {
            float textWidth = Builder.INTER.get().getWidth(renderText, 6);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(textWidth + 4, rectHeight - 2))
                    .radius(new QuadRadiusState(1))
                    .color(new QuadColorState(ColorUtils.rgba(30, 144, 255, Math.round(200 * animation * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() + 1, rectY + 1);
        }

        if (!displayText.isEmpty()) {
            Builder.TEXT_BUILDER
                    .size(6)
                    .color(showPlaceholder ? placeholderColor : textColor)
                    .text(displayText)
                    .font(Builder.INTER.get())
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX() + 2, rectY + 2);
        }

        isHovered = MouseClick.isClick(mouseX, mouseY, getX(), rectY, getWidth() - 4, rectHeight);
        if (isHovered) {
            CursorsUtil.setCursor(CursorsUtil.IBEAM);
        }

        ScissorUtil.disable();
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (isHovered) {
            if (focusedComponent != null) {
                focusedComponent.isFocused = false;
            }
            isFocused = true;
            focusedComponent = this;
            ctrlASelected = false;
            return true;
        }

        if (focusedComponent == this) {
            isFocused = false;
            focusedComponent = null;
        }
        ctrlASelected = false;
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused) return false;
        Input inputSetting = (Input) getSetting();
        String current = ctrlASelected ? "" : inputSetting.getValue();

        if (inputSetting.isOnlyNumber() && !Character.isDigit(chr)) {
            return false;
        }

        if (!Character.isISOControl(chr)) {
            inputSetting.set(current + chr);
            ctrlASelected = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) return false;
        Input inputSetting = (Input) getSetting();
        String current = inputSetting.getValue();

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (ctrlASelected) {
                inputSetting.set("");
                ctrlASelected = false;
            } else if (!current.isEmpty()) {
                inputSetting.set(current.substring(0, current.length() - 1));
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            isFocused = false;
            ctrlASelected = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            ctrlASelected = true;
            return true;
        }

        return false;
    }
}
