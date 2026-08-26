package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.colorPickerSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class ColorPickerComponent extends SettingComponent {
    private boolean opened = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;
    private boolean draggingColor = false;

    private float hueSliderX = 0f;
    private float alphaSliderX = 0f;

    boolean isHovered;

    private static final float PICKER_W = 105f;
    private static final float PICKER_H = 100f;

    public ColorPickerComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float height = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 5;
        size(240f / 2 - 10, height);

        ColorPicker setting = (ColorPicker) getSetting();
        hueSliderX = setting.getHsb() * (PICKER_W - 10f);
        alphaSliderX = setting.getAlphas() * (PICKER_W - 10f);
    }

    @Override
    public ColorPickerComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        ColorPicker setting = (ColorPicker) getSetting();
        float visibility = getGlobalAlpha();

        int mainColor = Interface.INSTANCE.getMainColor();
        int rgb = java.awt.Color.HSBtoRGB(setting.getHsb(), setting.getSaturation(), setting.getBrightness());
        int alpha = Math.round(setting.getAlphas() * 255f);
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        int previewColor = ColorUtils.setAlpha(color, Math.round(alpha * visibility));
        int nameColor = HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility));

        Builder.TEXT_BUILDER
                .size(7)
                .color(nameColor)
                .text(getSetting().getName())
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX(), getY() - 1);

        float dot = 10f;
        float r = dot / 3f;
        float cx = getX() + getWidth() - r - 10f;
        float cy = getY() + r;

        int previewLight = ColorUtils.setAlpha(
                ColorUtils.interpolate(previewColor & 0xFFFFFF, 0xFFFFFF, 0.35f),
                Math.round(alpha * visibility)
        );
        int previewDark = ColorUtils.setAlpha(
                ColorUtils.interpolate(previewColor & 0xFFFFFF, 0x000000, 0.35f),
                Math.round(alpha * visibility)
        );

        Builder.GRADIENT_BUILDER
                .radius(new QuadRadiusState(4))
                .size(new SizeState(dot, dot))
                .colors(new QuadColorState(previewLight, previewDark, previewColor, previewDark))
                .smoothness(1.0f)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), cx - r, cy - r);

        float rectX = cx - r;
        float rectY = cy - r;
        isHovered = MouseClick.isClick(mouseX, mouseY, rectX - 0.5f, rectY, dot, dot);
        if (isHovered) CursorsUtil.setCursor(CursorsUtil.HAND);

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7f);
        float pickerX = getX() - 1;
        float pickerY = getY() + nameH + 5f;

        if (opened) {
            if (!Interface.INSTANCE.isExcellentMode() && Interface.INSTANCE.blurStrength.getValue() > 0) {
                Builder.BLUR_BUILDER
                        .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                        .radius(new QuadRadiusState(3f))
                        .size(new SizeState(PICKER_W, PICKER_H))
                        .alpha(visibility)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), pickerX, pickerY);
            }

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(PICKER_W, PICKER_H))
                    .color(new QuadColorState(
                            HelperElements.themedPanel(28, 30, 35, (int) (150 * visibility)),
                            HelperElements.themedPanel(24, 26, 30, (int) (150 * visibility)),
                            HelperElements.themedPanel(22, 24, 28, (int) (150 * visibility)),
                            HelperElements.themedPanel(26, 28, 33, (int) (150 * visibility))
                    ))
                    .radius(new QuadRadiusState(3f))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), pickerX, pickerY);

            float sbX = pickerX + 5f;
            float sbY = pickerY + 5f;
            float sbW = PICKER_W - 10f;
            float sbH = PICKER_H - 30f;

            int tl = java.awt.Color.HSBtoRGB(setting.getHsb(), 0f, 1f);
            int bl = java.awt.Color.HSBtoRGB(setting.getHsb(), 0f, 0f);
            int tr = java.awt.Color.HSBtoRGB(setting.getHsb(), 1f, 1f);
            int br = java.awt.Color.HSBtoRGB(setting.getHsb(), 1f, 0f);

            tl = ColorUtils.setAlpha(tl, Math.round(ColorUtils.getAlpha(tl) * visibility));
            bl = ColorUtils.setAlpha(bl, Math.round(ColorUtils.getAlpha(bl) * visibility));
            tr = ColorUtils.setAlpha(tr, Math.round(ColorUtils.getAlpha(tr) * visibility));
            br = ColorUtils.setAlpha(br, Math.round(ColorUtils.getAlpha(br) * visibility));

            Builder.GRADIENT_BUILDER
                    .radius(new QuadRadiusState(2))
                    .size(new SizeState(sbW, sbH))
                    .colors(new QuadColorState(tl, bl, tr, br))
                    .smoothness(1.0f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), sbX, sbY);

            float radius = 3f;
            float diameter = radius * 2f;
            float satX = sbX + (setting.getSaturation() * sbW);
            float brightY = sbY + ((1f - setting.getBrightness()) * sbH);
            satX = Math.max(sbX + radius, Math.min(sbX + sbW - radius, satX));
            brightY = Math.max(sbY + radius, Math.min(sbY + sbH - radius, brightY));

            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(2))
                    .size(new SizeState(diameter, diameter))
                    .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(255, 255, 255), Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), satX - radius, brightY - radius);

            float barH = 6f;
            float barW = PICKER_W - 10f;
            float barX = pickerX + 5f;
            float hueY = pickerY + PICKER_H - 20f;
            float alphaY = pickerY + PICKER_H - 10f;
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(barW, barH))
                    .texture(0, 0, 1, 1, ClientTexture.of("images/huebar.png"))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX, hueY);

            Builder.BORDER_BUILDER
                    .radius(new QuadRadiusState(0f))
                    .size(new SizeState(barW, barH))
                    .thickness(-.3f)
                    .color(new QuadColorState(ColorUtils.setAlpha(
                            Interface.INSTANCE.getMainColor(), Math.round(100 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX, hueY);

            Builder.TEXTURE_BUILDER
                    .size(new SizeState(barW, barH))
                    .texture(0, 0, 1, 1, ClientTexture.of("images/alpha.png"))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX, alphaY);

            int rgbNoA = java.awt.Color.HSBtoRGB(setting.getHsb(), setting.getSaturation(), setting.getBrightness()) & 0xFFFFFF;
            int rr = (rgbNoA >> 16) & 0xFF;
            int gg = (rgbNoA >> 8) & 0xFF;
            int bb = rgbNoA & 0xFF;

            Builder.GRADIENT_BUILDER
                    .radius(new QuadRadiusState(0f))
                    .size(new SizeState(barW, barH))
                    .colors(new QuadColorState(
                            ColorUtils.rgba(rr, gg, bb, Math.round(0 * visibility)),
                            ColorUtils.rgba(rr, gg, bb, Math.round(0 * visibility)),
                            ColorUtils.rgba(rr, gg, bb, Math.round(255 * visibility)),
                            ColorUtils.rgba(rr, gg, bb, Math.round(255 * visibility))
                    ))
                    .smoothness(1.0f)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX, alphaY);

            Builder.BORDER_BUILDER
                    .radius(new QuadRadiusState(0f))
                    .size(new SizeState(barW, barH))
                    .thickness(-.3f)
                    .color(new QuadColorState(ColorUtils.setAlpha(
                            Interface.INSTANCE.getMainColor(), Math.round(100 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX, alphaY);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2, barH + 4))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX + hueSliderX - 1, hueY - 2);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2, barH + 4))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX + alphaSliderX - 1, alphaY - 2);

            if (draggingHue) {
                float relative = Math.max(0, Math.min(mouseX - barX, barW));
                setting.set(relative / barW, setting.getSaturation(), setting.getBrightness(), setting.getAlphas());
                hueSliderX = relative;
            }
            if (draggingAlpha) {
                float relative = Math.max(0, Math.min(mouseX - barX, barW));
                setting.set(setting.getHsb(), setting.getSaturation(), setting.getBrightness(), (relative / barW));
                alphaSliderX = relative;
            }
            if (draggingColor) {
                float sx = Math.max(0, Math.min(mouseX - sbX, sbW));
                float sy = Math.max(0, Math.min(mouseY - sbY, sbH));
                float sat = sx / sbW;
                float bright = 1f - sy / sbH;
                setting.set(setting.getHsb(), sat, bright, setting.getAlphas());
            }

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2, barH + 4))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX + hueSliderX - 1, hueY - 2);

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2, barH + 4))
                    .color(new QuadColorState(HelperElements.themedWhite(Math.round(255 * visibility))))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), barX + alphaSliderX - 1, alphaY - 2);

            if (MouseClick.isClick(mouseX, mouseY, barX, hueY, barW, barH) || MouseClick.isClick(mouseX, mouseY, barX, alphaY, barW, barH) || MouseClick.isClick(mouseX, mouseY, sbX, sbY, sbW, sbH)) {
                CursorsUtil.setCursor(CursorsUtil.HAND);
            }

            float extra = (pickerY + PICKER_H + 2f) - getY();
            size(getWidth(), Math.max(getHeight(), extra));
        } else {
            float baseH = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 5;
            size(getWidth(), baseH);
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float dot = 10f;
        float r = dot / 3f;
        float cx = getX() + getWidth() - r - 10f;
        float cy = getY() + r;
        float rectX = cx - r;
        float rectY = cy - r;

        if (MouseClick.isClick(mouseX, mouseY, rectX - 0.5f, rectY, dot, dot)) {
            opened = !opened;
            return true;
        }

        if (!opened) return false;

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7f);
        float pickerX = getX() - 1f;
        float pickerY = getY() + nameH + 5f;

        float sbX = pickerX + 5f;
        float sbY = pickerY + 5f;
        float sbW = PICKER_W - 10f;
        float sbH = PICKER_H - 30f;

        float barH = 6f;
        float barW = PICKER_W - 10f;
        float barX = pickerX + 5f;
        float hueY = pickerY + PICKER_H - 20f;
        float alphaY = pickerY + PICKER_H - 10f;

        if (MouseClick.isClick(mouseX, mouseY, barX, hueY, barW, barH)) {
            draggingHue = true;
            return true;
        }
        if (MouseClick.isClick(mouseX, mouseY, barX, alphaY, barW, barH)) {
            draggingAlpha = true;
            return true;
        }
        if (MouseClick.isClick(mouseX, mouseY, sbX, sbY, sbW, sbH)) {
            draggingColor = true;
            return true;
        }

        if (!MouseClick.isClick(mouseX, mouseY, pickerX, pickerY, PICKER_W, PICKER_H)) {
            opened = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false;
        draggingAlpha = false;
        draggingColor = false;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            draggingHue = false;
            draggingAlpha = false;
            draggingColor = false;
            opened = false;
            return true;
        }
        return false;
    }
}
