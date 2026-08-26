package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.msdf.core.MsdfFont;
import dev.client.api.nullcry.render.core.renderers.core.BuiltText;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class TextBuilder extends AbstractBuilder<BuiltText> {
    private MsdfFont font;
    private String text;
    private List<BuiltText.Run> runs;
    private float size;
    private float thickness;
    private int color;
    private float Smoothness;
    private float spacing;
    private int outlineColor;
    private float outlineThickness;
    private Integer itemTint = null;

    private String normalizeSmallCaps(String input) {
        if (input == null) return null;
        return input.replace('ᴀ', 'a').replace('ʙ', 'b').replace('ᴄ', 'c').replace('ᴅ', 'd').replace('ᴇ', 'e').replace('ꜰ', 'f').replace('ɢ', 'g').replace('ʜ', 'h').replace('ɪ', 'i').replace('ᴊ', 'j').replace('ᴋ', 'k').replace('ʟ', 'l').replace('ᴍ', 'm').replace('ɴ', 'n').replace('ᴏ', 'o').replace('ᴘ', 'p').replace('ǫ', 'q').replace('ʀ', 'r').replace('ꜱ', 's').replace('ᴛ', 't').replace('ᴜ', 'u').replace('ᴠ', 'v').replace('ᴡ', 'w').replace('ʏ', 'y').replace('ᴢ', 'z');
    }

    public TextBuilder font(MsdfFont font) {
        this.font = font;
        return this;
    }

    public TextBuilder text(String text) {
        this.text = normalizeSmallCaps(text);
        this.runs = null;
        return this;
    }

    public TextBuilder text(Text text) {
        if (text == null) return this.text("");
        List<BuiltText.Run> list = new ArrayList<>();
        OrderedText ordered = text.asOrderedText();

        final StringBuilder sb = new StringBuilder();

        int baseA = (this.color >>> 24) & 0xFF;
        int baseRGB = (this.itemTint != null) ? this.itemTint : (this.color & 0x00FFFFFF);
        final int baseRGBA = (baseA << 24) | baseRGB;

        final int[] curColor = { baseRGBA };
        final boolean[] legacyAwait = { false };

        ordered.accept((index, style, codePoint) -> {
            String charStr = normalizeSmallCaps(new String(Character.toChars(codePoint)));
            int cp = charStr.codePointAt(0);

            if (legacyAwait[0]) {
                legacyAwait[0] = false;
                char c = Character.toLowerCase((char) cp);

                if (c == 'r') {
                    if (sb.length() > 0) { list.add(new BuiltText.Run(sb.toString(), curColor[0])); sb.setLength(0); }
                    curColor[0] = baseRGBA;
                    return true;
                }
                if (c == 'l' || c == 'o' || c == 'n' || c == 'm' || c == 'k') {
                    return true;
                }
                Formatting f = Formatting.byCode(c);
                if (f != null && f.getColorValue() != null) {
                    int rgba = (baseA << 24) | (f.getColorValue() & 0x00FFFFFF);
                    if (rgba != curColor[0]) {
                        if (sb.length() > 0) { list.add(new BuiltText.Run(sb.toString(), curColor[0])); sb.setLength(0); }
                        curColor[0] = rgba;
                    }
                }
                return true;
            }

            if (cp == '§' || cp == '&') {
                legacyAwait[0] = true;
                return true;
            }

            if (cp == '\n') {
                sb.append('\n');
                list.add(new BuiltText.Run(sb.toString(), curColor[0]));
                sb.setLength(0);
                curColor[0] = baseRGBA;
                return true;
            }

            Integer styleRgb = (style.getColor() != null) ? (style.getColor().getRgb() & 0x00FFFFFF) : null;
            int targetRgba = (styleRgb != null) ? ((baseA << 24) | styleRgb) : baseRGBA;

            if (targetRgba != curColor[0]) {
                if (sb.length() > 0) { list.add(new BuiltText.Run(sb.toString(), curColor[0])); sb.setLength(0); }
                curColor[0] = targetRgba;
            }

            sb.appendCodePoint(cp);
            return true;
        });

        if (sb.length() > 0) list.add(new BuiltText.Run(sb.toString(), curColor[0]));

        this.runs = list;
        this.text = null;
        return this;
    }

    public TextBuilder rarityFallback(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getRarity() == null) {
            this.itemTint = null;
            return this;
        }
        Formatting fmt = stack.getRarity().getFormatting();
        Integer v = (fmt != null) ? fmt.getColorValue() : null;
        this.itemTint = (v != null) ? (v & 0x00FFFFFF) : null;
        return this;
    }

    public TextBuilder size(float size) { this.size = size; return this; }
    public TextBuilder thickness(float thickness) { this.thickness = thickness; return this; }
    public TextBuilder color(Color color) { return this.color(color.getRGB()); }
    public TextBuilder color(int color) { this.color = color; return this; }
    public TextBuilder Smoothness(float Smoothness) { this.Smoothness = Smoothness; return this; }
    public TextBuilder spacing(float spacing) { this.spacing = spacing; return this; }
    public TextBuilder outline(Color color, float thickness) { return this.outline(color.getRGB(), thickness); }
    public TextBuilder outline(int color, float thickness) { this.outlineColor = color; this.outlineThickness = thickness; return this; }

    @Override
    protected BuiltText _build() {
        return new BuiltText(this.font, this.text, this.size, this.thickness, this.color, this.Smoothness, this.spacing, this.outlineColor, this.outlineThickness, this.runs);
    }

    @Override
    protected void reset() {
        this.font = null;
        this.text = "";
        this.runs = null;
        this.size = 0.0f;
        this.thickness = 0.05f;
        this.color = -1;
        this.Smoothness = 0.5f;
        this.spacing = 0.0f;
        this.outlineColor = 0;
        this.outlineThickness = 0.0f;
        this.itemTint = null;
    }
}
