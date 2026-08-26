package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.Getter;

import java.awt.*;
import java.util.function.Supplier;

import static net.minecraft.util.math.ColorHelper.*;

public class ColorPicker extends Setting {
    @Getter
    float hsb;
    @Getter
    float saturation;
    @Getter
    float brightness = 0.0f;
    @Getter
    float alphas = 1.0f;

    public ColorPicker(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public ColorPicker set(Integer color) {
        if (color == -1) {
            this.hsb = 0.0f;
            this.saturation = 0.0f;
            this.brightness = 1.0f;
            this.alphas = 1.0f;
        } else {
            float[] hsbValue = Color.RGBtoHSB(getRed(color), getGreen(color), getBlue(color), new float[3]);
            this.hsb = hsbValue[0];
            this.saturation = hsbValue[1];
            this.brightness = hsbValue[2];
            this.alphas = getAlpha(color) / 255.0f;
            if (this.alphas <= 0) this.alphas = 1.0f;
        }
        return this;
    }

    public ColorPicker set(float hsb, float saturation, float brightness, float alpha) {
        this.hsb = hsb;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alphas = alpha;

        return this;
    }

    public int getColorRGBA() {
        int rgb = Color.HSBtoRGB(hsb, saturation, brightness);
        int alpha = Math.round(alphas * 255) << 24;
        if ((rgb & 0xFFFFFF) == 0 && alpha == 0) {
            alpha = 0xFF000000;
        }
        return alpha | (rgb & 0xFFFFFF);
    }

    @Override
    public ColorPicker collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
