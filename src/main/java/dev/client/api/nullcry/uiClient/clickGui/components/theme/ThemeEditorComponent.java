package dev.client.api.nullcry.uiClient.clickGui.components.theme;

import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.component.Component;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ThemeEditorComponent extends Component {

    private static final float PAD = 6f;
    private static final float HEADER_H = 22f;
    private static final float NAME_FIELD_H = 18f;
    private static final float THEME_CARD_H = 18f;
    private static final float ELEMENT_H = 14f;
    private static final float GAP = 3f;
    private static final int MAX_NAME_LENGTH = 14;

    private float revealProgress = 1f;
    private boolean initialized = false;

    private final List<Theme> themes = new ArrayList<>();
    private Theme currentTheme;
    private Theme editTheme;

    private String typingText = "";
    private boolean typing = false;

    private int editingColorIndex = -1;
    private float hue = 0f, saturation = 1f, brightness = 1f, alpha = 1f;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;

    private float deleteBtnX = -1f, deleteBtnY = -1f, deleteBtnSize = -1f;

    public void setRevealProgress(float progress) {
        this.revealProgress = MathHelper.clamp(progress, 0f, 1f);
    }

    @Override
    public void init() {
        if (initialized) return;
        initialized = true;

        List<Theme> loaded = ThemeManager.getInstance().loadAll();
        themes.addAll(loaded);

        Theme last = ThemeManager.getInstance().loadLastSelected();
        if (last != null) {
            currentTheme = findByName(last.getName());
            if (currentTheme == null) {
                themes.add(last);
                currentTheme = last;
            }
        }

        if (themes.isEmpty()) {
            Theme def = new Theme("LynxWave");
            themes.add(def);
            currentTheme = def;
            ThemeManager.getInstance().save(def);
        } else if (currentTheme == null) {
            currentTheme = themes.get(0);
        }
    }

    private Theme findByName(String name) {
        if (name == null) return null;
        for (Theme t : themes) if (t.getName().equalsIgnoreCase(name)) return t;
        return null;
    }

    @Override
    public ThemeEditorComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (revealProgress < 0.01f) return this;
        init();

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        float px = getX(), py = getY(), pw = getWidth();
        int alphaInt = (int) (255 * revealProgress);
        int mainColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;

        // Panel - we'll draw it once we know total height. Compute content height first.
        float contentHeight = computeContentHeight();
        size(pw, contentHeight);

        HelperElements.rectGui(context, px, py, pw, contentHeight, revealProgress);

        // Header
        Builder.TEXT_BUILDER.size(9f).font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(mainColor, alphaInt))
                .text("Theme Editor").build()
                .render(matrix, px + PAD + 2f, py + 7f);

        float cursorY = py + HEADER_H;

        // Name field
        renderNameField(context, matrix, px + PAD, cursorY, pw - PAD * 2f, NAME_FIELD_H, alphaInt);
        cursorY += NAME_FIELD_H + GAP * 2f;

        if (editTheme == null) {
            renderThemeList(context, matrix, px + PAD, cursorY, pw - PAD * 2f, mouseX, mouseY, alphaInt);
        } else {
            renderElementList(context, matrix, px + PAD, cursorY, pw - PAD * 2f, mouseX, mouseY, alphaInt);
        }

        return this;
    }

    private float computeContentHeight() {
        float h = HEADER_H + NAME_FIELD_H + GAP * 2f;
        if (editTheme == null) {
            h += themes.size() * (THEME_CARD_H + GAP);
        } else {
            int count = Theme.ORDER.length;
            h += count * (ELEMENT_H + GAP);
            if (editingColorIndex >= 0 && editingColorIndex < count) {
                h += getColorPickerHeight() + GAP;
            }
        }
        return h + PAD;
    }

    private void renderNameField(DrawContext context, Matrix4f matrix, float x, float y, float w, float h, int alphaInt) {
        int bgAlpha = (int) (40 * revealProgress);
        Builder.RECTANGLE_BUILDER.size(new SizeState(w, h))
                .color(new QuadColorState(ColorUtils.setAlpha(HelperElements.themedWhite(255), bgAlpha)))
                .radius(new QuadRadiusState(3.5f)).build()
                .render(matrix, x, y);

        String display;
        if (editTheme != null) {
            display = editTheme.getName();
        } else {
            String cursor = typing && (System.currentTimeMillis() % 1000 > 500) ? "|" : " ";
            display = typingText.isEmpty() && !typing ? "Введите имя..." : typingText + cursor;
        }

        Builder.TEXT_BUILDER.size(7.5f).font(Builder.INTER.get())
                .color(ColorUtils.setAlpha(HelperElements.themedText(220), alphaInt))
                .text(display).build()
                .render(matrix, x + 5f, y + h / 2f - 3.75f);

        if (editTheme != null && themes.size() > 1) {
            float margin = 3f;
            float size = h - margin * 2f;
            float bx = x + w - size - margin;
            float by = y + margin;

            Builder.RECTANGLE_BUILDER.size(new SizeState(size, size))
                    .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(200, 60, 60), (int) (180 * revealProgress))))
                    .radius(new QuadRadiusState(2f)).build()
                    .render(matrix, bx, by);

            float iconPad = 2f;
            int iconColor = ColorUtils.setAlpha(-1, alphaInt);
            Builder.RECTANGLE_BUILDER.size(new SizeState(size - iconPad * 2f, 1f))
                    .color(new QuadColorState(iconColor))
                    .radius(new QuadRadiusState(0.5f)).build()
                    .render(matrix, bx + iconPad, by + size / 2f - 0.5f);

            deleteBtnX = bx; deleteBtnY = by; deleteBtnSize = size;
        } else {
            deleteBtnX = -1f; deleteBtnY = -1f; deleteBtnSize = -1f;
        }
    }

    private void renderThemeList(DrawContext context, Matrix4f matrix, float x, float y, float w, int mouseX, int mouseY, int alphaInt) {
        float cy = y;
        for (Theme theme : themes) {
            boolean selected = currentTheme == theme;
            boolean hovered = MouseClick.isClick(mouseX, mouseY, x, cy, w, THEME_CARD_H);
            if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);

            Color primary = theme.getElementColor(Theme.PRIMARY);
            Color header = theme.getElementColor(Theme.HEADER_TEXT);

            int bodyAlpha = (int) ((selected ? 255 : 180) * revealProgress);
            int left = ColorUtils.setAlpha(primary.getRGB(), bodyAlpha);
            int right = ColorUtils.setAlpha(header.getRGB(), bodyAlpha);

            Builder.RECTANGLE_BUILDER.size(new SizeState(w, THEME_CARD_H))
                    .color(new QuadColorState(left, right, right, left))
                    .radius(new QuadRadiusState(3.5f)).build()
                    .render(matrix, x, cy);

            Builder.TEXT_BUILDER.size(7.5f).font(Builder.INTER.get())
                    .color(ColorUtils.setAlpha(theme.getElementColor(Theme.TEXT).getRGB(), alphaInt))
                    .text(theme.getName()).build()
                    .render(matrix, centerTextX(theme.getName(), 7.5f, x, w), cy + THEME_CARD_H / 2f - 3.75f);

            cy += THEME_CARD_H + GAP;
        }
    }

    private void renderElementList(DrawContext context, Matrix4f matrix, float x, float y, float w, int mouseX, int mouseY, int alphaInt) {
        float cy = y;
        List<Theme.ElementColor> colors = editTheme.getElementColors();

        for (int i = 0; i < colors.size(); i++) {
            Theme.ElementColor ec = colors.get(i);
            boolean hovered = MouseClick.isClick(mouseX, mouseY, x, cy, w, ELEMENT_H);
            if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);

            Builder.TEXT_BUILDER.size(6.5f).font(Builder.INTER.get())
                    .color(HelperElements.themedText((int) (200 * revealProgress)))
                    .text(ec.getName()).build()
                    .render(matrix, x + 2f, cy + ELEMENT_H / 2f - 3.25f);

            float swatch = 8f;
            float sx = x + w - swatch - 2f;
            float sy = cy + ELEMENT_H / 2f - swatch / 2f;
            Builder.RECTANGLE_BUILDER.size(new SizeState(swatch, swatch))
                    .color(new QuadColorState(ColorUtils.setAlpha(ec.getColor().getRGB(),
                            (int) (ec.getColor().getAlpha() / 255f * 255 * revealProgress))))
                    .radius(new QuadRadiusState(swatch * 0.3f)).build()
                    .render(matrix, sx, sy);

            cy += ELEMENT_H;

            if (editingColorIndex == i) {
                float pickerH = getColorPickerHeight();
                renderColorPicker(matrix, x, cy + GAP, w, pickerH, mouseX, mouseY, ec);
                cy += pickerH + GAP;
            }

            cy += GAP;
        }
    }

    private float getColorPickerHeight() {
        return 70f;
    }

    private void renderColorPicker(Matrix4f matrix, float x, float y, float w, float h, int mouseX, int mouseY, Theme.ElementColor ec) {
        int alphaInt = (int) (255 * revealProgress);

        // Layout
        float barW = 7f;
        float gap = 3f;
        float hueBarX = x + w - barW * 2f - gap * 2f;
        float alphaBarX = x + w - barW;
        float svW = hueBarX - x - gap;
        float svH = h - 12f;
        float svY = y;

        int baseColor = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;

        // SV square
        Builder.RECTANGLE_BUILDER.size(new SizeState(svW, svH))
                .color(new QuadColorState(
                        ColorUtils.setAlpha(0xFFFFFFFF, alphaInt),
                        ColorUtils.setAlpha(baseColor, alphaInt),
                        ColorUtils.setAlpha(baseColor, alphaInt),
                        ColorUtils.setAlpha(0xFFFFFFFF, alphaInt)))
                .radius(new QuadRadiusState(2f)).build()
                .render(matrix, x, svY);

        Builder.RECTANGLE_BUILDER.size(new SizeState(svW, svH))
                .color(new QuadColorState(
                        ColorUtils.setAlpha(0, 0),
                        ColorUtils.setAlpha(0, 0),
                        ColorUtils.setAlpha(0xFF000000, alphaInt),
                        ColorUtils.setAlpha(0xFF000000, alphaInt)))
                .radius(new QuadRadiusState(2f)).build()
                .render(matrix, x, svY);

        // SV dot
        float dotX = x + saturation * svW - 2.5f;
        float dotY = svY + (1f - brightness) * svH - 2.5f;
        Builder.RECTANGLE_BUILDER.size(new SizeState(5f, 5f))
                .color(new QuadColorState(HelperElements.themedWhite(alphaInt)))
                .radius(new QuadRadiusState(2.5f)).build()
                .render(matrix, dotX, dotY);

        // Hue bar
        int[] hueColors = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        int segs = 6;
        float segH = svH / segs;
        for (int i = 0; i < segs; i++) {
            float sy = svY + i * segH;
            Builder.RECTANGLE_BUILDER.size(new SizeState(barW, segH + 1f))
                    .color(new QuadColorState(
                            ColorUtils.setAlpha(hueColors[i], alphaInt),
                            ColorUtils.setAlpha(hueColors[i], alphaInt),
                            ColorUtils.setAlpha(hueColors[i + 1], alphaInt),
                            ColorUtils.setAlpha(hueColors[i + 1], alphaInt)))
                    .radius(new QuadRadiusState(0f)).build()
                    .render(matrix, hueBarX, sy);
        }
        // Hue indicator
        float hy = svY + hue * svH - 1f;
        Builder.RECTANGLE_BUILDER.size(new SizeState(barW + 2f, 2f))
                .color(new QuadColorState(HelperElements.themedWhite(alphaInt)))
                .radius(new QuadRadiusState(1f)).build()
                .render(matrix, hueBarX - 1f, hy);

        // Alpha bar
        Color cur = ec.getColor();
        int transparent = ColorUtils.rgba(cur.getRed(), cur.getGreen(), cur.getBlue(), 0);
        int opaque = ColorUtils.rgba(cur.getRed(), cur.getGreen(), cur.getBlue(), alphaInt);
        Builder.RECTANGLE_BUILDER.size(new SizeState(barW, svH))
                .color(new QuadColorState(transparent, transparent, opaque, opaque))
                .radius(new QuadRadiusState(2f)).build()
                .render(matrix, alphaBarX, svY);

        float ay = svY + (1f - alpha) * svH - 1f;
        Builder.RECTANGLE_BUILDER.size(new SizeState(barW + 2f, 2f))
                .color(new QuadColorState(HelperElements.themedWhite(alphaInt)))
                .radius(new QuadRadiusState(1f)).build()
                .render(matrix, alphaBarX - 1f, ay);

        // Preview strip
        float previewY = svY + svH + 2f;
        Builder.RECTANGLE_BUILDER.size(new SizeState(w, 8f))
                .color(new QuadColorState(ColorUtils.setAlpha(cur.getRGB(), (int) (cur.getAlpha() / 255f * alphaInt))))
                .radius(new QuadRadiusState(2f)).build()
                .render(matrix, x, previewY);

        // Drag updates
        if (draggingSV) {
            saturation = MathHelper.clamp((mouseX - x) / svW, 0f, 1f);
            brightness = MathHelper.clamp(1f - (mouseY - svY) / svH, 0f, 1f);
            applyColorTo(ec);
        }
        if (draggingHue) {
            hue = MathHelper.clamp((mouseY - svY) / svH, 0f, 0.9999f);
            applyColorTo(ec);
        }
        if (draggingAlpha) {
            alpha = MathHelper.clamp(1f - (mouseY - svY) / svH, 0f, 1f);
            applyColorTo(ec);
        }
    }

    private void applyColorTo(Theme.ElementColor ec) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int a = (int) (alpha * 255f);
        Color c = new Color((a << 24) | (rgb & 0xFFFFFF), true);
        ec.setColor(c);

        if (editTheme != null && ec.getName().equals(Theme.PRIMARY)) {
            syncToInterface(c);
        }
    }

    private void syncToInterface(Color primary) {
        if (Interface.INSTANCE == null) return;
        if (!Interface.INSTANCE.theme.isSelected("Своя")) {
            Interface.INSTANCE.theme.set("Своя");
        }
        Interface.INSTANCE.customThemeColor.set(hue, saturation, brightness, alpha);
        int rgb = primary.getRGB();
        Interface.INSTANCE.theme.withColor("Своя", rgb);
    }

    private void syncHsbaFrom(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = c.getAlpha() / 255f;
    }

    private float centerTextX(String text, float size, float x, float w) {
        float tw = Builder.INTER.get().getWidth(text, size);
        return x + w / 2f - tw / 2f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealProgress < 0.01f) return false;
        if (!MouseClick.isClick(mouseX, mouseY, getX(), getY(), getWidth(), getHeight())) {
            typing = false;
            return false;
        }

        float px = getX(), py = getY(), pw = getWidth();

        // Header click (return to list when editing)
        if (editTheme != null && MouseClick.isClick(mouseX, mouseY, px, py, pw, HEADER_H)) {
            commitEdit();
            editTheme = null;
            editingColorIndex = -1;
            draggingSV = draggingHue = draggingAlpha = false;
            return true;
        }

        // Name field
        float nameX = px + PAD, nameY = py + HEADER_H;
        float nameW = pw - PAD * 2f;

        if (editTheme != null && deleteBtnSize > 0f
                && MouseClick.isClick(mouseX, mouseY, deleteBtnX, deleteBtnY, deleteBtnSize, deleteBtnSize)) {
            Theme toDelete = editTheme;
            themes.removeIf(t -> t == toDelete);
            ThemeManager.getInstance().remove(toDelete.getName());
            if (currentTheme == toDelete) {
                currentTheme = themes.isEmpty() ? null : themes.get(0);
                if (currentTheme != null) ThemeManager.getInstance().saveLastSelected(currentTheme);
            }
            editTheme = null;
            editingColorIndex = -1;
            return true;
        }

        if (MouseClick.isClick(mouseX, mouseY, nameX, nameY, nameW, NAME_FIELD_H)) {
            if (editTheme == null) typing = !typing;
            return true;
        } else {
            typing = false;
        }

        float listY = nameY + NAME_FIELD_H + GAP * 2f;

        if (editTheme == null) {
            // Theme cards
            float cy = listY;
            for (Theme theme : themes) {
                if (MouseClick.isClick(mouseX, mouseY, nameX, cy, nameW, THEME_CARD_H)) {
                    if (button == 0) {
                        currentTheme = theme;
                        ThemeManager.getInstance().saveLastSelected(theme);
                        applyCurrentThemeToInterface();
                    } else if (button == 1) {
                        editTheme = theme;
                        editingColorIndex = -1;
                    }
                    return true;
                }
                cy += THEME_CARD_H + GAP;
            }
        } else {
            // Element rows & active color picker
            float cy = listY;
            List<Theme.ElementColor> colors = editTheme.getElementColors();
            for (int i = 0; i < colors.size(); i++) {
                Theme.ElementColor ec = colors.get(i);
                if (MouseClick.isClick(mouseX, mouseY, nameX, cy, nameW, ELEMENT_H)) {
                    if (editingColorIndex == i) {
                        editingColorIndex = -1;
                    } else {
                        editingColorIndex = i;
                        syncHsbaFrom(ec.getColor());
                    }
                    return true;
                }
                cy += ELEMENT_H;
                if (editingColorIndex == i) {
                    float pickerH = getColorPickerHeight();
                    float pickerY = cy + GAP;
                    if (handleColorPickerClick(mouseX, mouseY, nameX, pickerY, nameW, pickerH, ec)) return true;
                    cy += pickerH + GAP;
                }
                cy += GAP;
            }
        }

        return true;
    }

    private boolean handleColorPickerClick(double mouseX, double mouseY, float x, float y, float w, float h, Theme.ElementColor ec) {
        float barW = 7f;
        float gap = 3f;
        float hueBarX = x + w - barW * 2f - gap * 2f;
        float alphaBarX = x + w - barW;
        float svW = hueBarX - x - gap;
        float svH = h - 12f;

        if (MouseClick.isClick(mouseX, mouseY, x, y, svW, svH)) {
            draggingSV = true;
            saturation = MathHelper.clamp((float) ((mouseX - x) / svW), 0f, 1f);
            brightness = MathHelper.clamp(1f - (float) ((mouseY - y) / svH), 0f, 1f);
            applyColorTo(ec);
            return true;
        }
        if (MouseClick.isClick(mouseX, mouseY, hueBarX - 1f, y, barW + 2f, svH)) {
            draggingHue = true;
            hue = MathHelper.clamp((float) ((mouseY - y) / svH), 0f, 0.9999f);
            applyColorTo(ec);
            return true;
        }
        if (MouseClick.isClick(mouseX, mouseY, alphaBarX - 1f, y, barW + 2f, svH)) {
            draggingAlpha = true;
            alpha = MathHelper.clamp(1f - (float) ((mouseY - y) / svH), 0f, 1f);
            applyColorTo(ec);
            return true;
        }
        return false;
    }

    private void applyCurrentThemeToInterface() {
        if (Interface.INSTANCE == null || currentTheme == null) return;
        Color primary = currentTheme.getPrimaryColor();
        float[] hsb = Color.RGBtoHSB(primary.getRed(), primary.getGreen(), primary.getBlue(), null);
        if (!Interface.INSTANCE.theme.isSelected("Своя")) {
            Interface.INSTANCE.theme.set("Своя");
        }
        Interface.INSTANCE.customThemeColor.set(hsb[0], hsb[1], hsb[2], primary.getAlpha() / 255f);
        Interface.INSTANCE.theme.withColor("Своя", primary.getRGB());
    }

    private void commitEdit() {
        if (editTheme != null) {
            ThemeManager.getInstance().save(editTheme);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingSV || draggingHue || draggingAlpha) {
                if (editTheme != null) ThemeManager.getInstance().save(editTheme);
            }
            draggingSV = draggingHue = draggingAlpha = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!typing || editTheme != null) return false;

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!typingText.isEmpty()) typingText = typingText.substring(0, typingText.length() - 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String name = typingText.trim();
            if (!name.isEmpty() && findByName(name) == null) {
                Theme created = new Theme(name);
                if (currentTheme != null) {
                    for (int i = 0; i < created.getElementColors().size(); i++) {
                        created.getElementColors().get(i).setColor(currentTheme.getElementColors().get(i).getColor());
                    }
                }
                themes.add(created);
                currentTheme = created;
                ThemeManager.getInstance().save(created);
                ThemeManager.getInstance().saveLastSelected(created);
            }
            typingText = "";
            typing = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            typing = false;
            typingText = "";
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!typing || editTheme != null) return false;
        if (typingText.length() >= MAX_NAME_LENGTH) return false;
        if (chr < 32 || chr == 127) return false;
        typingText += chr;
        return true;
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }
}
