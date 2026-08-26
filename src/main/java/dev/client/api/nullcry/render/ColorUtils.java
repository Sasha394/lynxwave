package dev.client.api.nullcry.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

@SuppressWarnings("unused")
@UtilityClass
public class ColorUtils {
    public final int green = new Color(64, 255, 64).getRGB();
    public final int yellow = new Color(255, 255, 64).getRGB();
    public final int orange = new Color(255, 128, 32).getRGB();
    public final int red = new Color(255, 0, 0).getRGB();

    public int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public int blue(int c) {
        return c & 0xFF;
    }

    public int alpha(int c) {
        return (c >> 24) & 0xFF;
    }

    public float redf(int c) {
        return red(c) / 255.0f;
    }

    public float greenf(int c) {
        return green(c) / 255.0f;
    }

    public float bluef(int c) {
        return blue(c) / 255.0f;
    }

    public float alphaf(int c) {
        return alpha(c) / 255.0f;
    }

    public static int setAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    public static int resolveModeColor(ModeElement mode, ColorPicker customColor, int clientColor, String... clientModes) {
        if (mode == null) {
            return clientColor;
        }
        if (customColor != null && mode.isSelected("Свой")) {
            return customColor.getColorRGBA();
        }
        if (clientModes != null) {
            for (String clientMode : clientModes) {
                if (mode.isSelected(clientMode)) {
                    return clientColor;
                }
            }
        }
        return clientColor;
    }

    public static int resolveModeColor(ModeElement mode, ColorPicker customColor, int clientColor) {
        return resolveModeColor(mode, customColor, clientColor, "Клиент", "Клиентский");
    }

    public static int getAlpha(final int hex) {
        return hex >> 24 & 255;
    }

    public int multAlpha(int color, float percent01) {
        return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
    }

    public static int reAlphaInt(final int color, final int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 16777215);
    }

    public static int rgb(int r, int g, int b) {
        return 255 << 24 | r << 16 | g << 8 | b;
    }

    public static int rgba(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static float[] rgba(final int color) {
        return new float[]{(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >> 24 & 0xFF) / 255f};
    }

    public float[] getRGBAf(int c) {
        return new float[]{redf(c), greenf(c), bluef(c), alphaf(c)};
    }

    private int getColor(int red, int green, int blue, int alpha) {
        return ((MathHelper.clamp(alpha, 0, 255) << 24) |
                (MathHelper.clamp(red, 0, 255) << 16) |
                (MathHelper.clamp(green, 0, 255) << 8) |
                MathHelper.clamp(blue, 0, 255));
    }

    public static int gradient(int start, int end, int index, int speed) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;

        float t = MathHelper.clamp(angle / 180f - 1, 0, 1);
        int color = interpolate(start, end, t);

        float[] hs = rgba(color);
        float[] hsb = Color.RGBtoHSB((int) (hs[0] * 255), (int) (hs[1] * 255), (int) (hs[2] * 255), null);

        hsb[1] *= 1.5F;
        hsb[1] = Math.min(hsb[1], 1.0f);

        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    public static int gradient(int speed, int index, int... colors) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int colorIndex = (int) (angle / 360f * colors.length);
        if (colorIndex == colors.length) {
            colorIndex--;
        }
        int color1 = colors[colorIndex];
        int color2 = colors[colorIndex == colors.length - 1 ? 0 : colorIndex + 1];
        return interpolate(color1, color2, angle / 360f * colors.length - colorIndex);
    }

    public static int interpolate(int start, int end, float value) {
        float[] startColor = rgba(start);
        float[] endColor = rgba(end);

        return rgba((int) MathUtil.interpolate(startColor[0] * 255, endColor[0] * 255, value), (int) MathUtil.interpolate(startColor[1] * 255, endColor[1] * 255, value), (int) MathUtil.interpolate(startColor[2] * 255, endColor[2] * 255, value), (int) MathUtil.interpolate(startColor[3] * 255, endColor[3] * 255, value));
    }

    public static class IntColor {
        public static float[] rgb(final int color) {
            return new float[]{(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >> 24 & 0xFF) / 255f};
        }

        public static float[] rgba(int color) {
            float r = (color >> 16 & 255) / 255.0F;
            float g = (color >> 8 & 255) / 255.0F;
            float b = (color & 255) / 255.0F;
            float a = (color >> 24 & 255) / 255.0F;
            return new float[]{r, g, b, a};
        }

        public static int rgb(int r, int g, int b) {
            return 255 << 24 | r << 16 | g << 8 | b;
        }

        public static int getRed(final int hex) {
            return hex >> 16 & 255;
        }

        public static int getGreen(final int hex) {
            return hex >> 8 & 255;
        }

        public static int getBlue(final int hex) {
            return hex & 255;
        }

        public static int getAlpha(final int hex) {
            return hex >> 24 & 255;
        }
    }
}
