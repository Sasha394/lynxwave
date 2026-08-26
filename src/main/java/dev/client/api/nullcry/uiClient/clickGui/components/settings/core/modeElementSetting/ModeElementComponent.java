package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.modeElementSetting;

import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import com.google.common.base.Suppliers;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Supplier;

public class ModeElementComponent extends SettingComponent {
    private final Supplier<ModeElement> modeSetting = Suppliers.memoize(() -> (ModeElement) getSetting());

    private boolean expanded = false;
    private float expandAnim = 0f;

    private static final float ROW_H = 15f;
    private static final float OPTION_H = 13f;
    private static final float NAME_SIZE = 7f;
    private static final float VALUE_SIZE = 6.5f;

    public ModeElementComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), NAME_SIZE);
        size(240f / 2f - 10f, nameH + ROW_H + 4f);
    }

    @Override
    public ModeElementComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        ModeElement mode = modeSetting.get();
        float visibility = getGlobalAlpha();
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        if (mode.getValue() == null && !mode.getValues().isEmpty()) {
            mode.set(mode.getValues().getFirst());
        }

        expandAnim += ((expanded ? 1f : 0f) - expandAnim) * 0.25f;

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), NAME_SIZE);
        Builder.TEXT_BUILDER
                .size(NAME_SIZE)
                .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                .text(getSetting().getName())
                .font(Builder.INTER.get())
                .build()
                .render(matrix, getX(), getY() - 1f);

        if (mode.getValues().isEmpty()) {
            size(getWidth(), nameH + 6f);
            return null;
        }

        float rowX = getX();
        float rowY = getY() + nameH + 3f;
        float rowW = getWidth();

        boolean rowHover = MouseClick.isClick(mouseX, mouseY, rowX, rowY, rowW, ROW_H);
        if (rowHover) CursorsUtil.setCursor(CursorsUtil.HAND);

        int c1 = Interface.INSTANCE.getFirstColor();
        boolean excellentStyle = Interface.INSTANCE.isExcellentMode();

        if (excellentStyle) {
            HelperElements.rect(matrix, rowX, rowY, rowW, ROW_H, visibility);
        } else {
            int rowBg = HelperElements.themedPanel(22, 24, 28, (int) ((rowHover ? 210 : 180) * visibility));
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(rowW, ROW_H))
                    .color(new QuadColorState(rowBg))
                    .radius(new QuadRadiusState(3f))
                    .build()
                    .render(matrix, rowX, rowY);

            int borderAlpha = (int) ((rowHover ? 55 : 28) * visibility);
            Builder.BORDER_BUILDER
                    .size(new SizeState(rowW, ROW_H))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(ColorUtils.setAlpha(c1, borderAlpha)))
                    .thickness(-0.1f)
                    .build()
                    .render(matrix, rowX, rowY);
        }

        String current = mode.getValue() == null ? "—" : mode.getValue().toString();
        float valueY = rowY + (ROW_H - Builder.INTER.get().getHeight(current, VALUE_SIZE)) / 2f - 0.5f;

        Builder.TEXT_BUILDER
                .size(VALUE_SIZE)
                .color(HelperElements.themedText((int) (220 * visibility)))
                .text(current)
                .font(Builder.INTER.get())
                .build()
                .render(matrix, rowX + 6f, valueY);

        String chevron = expanded ? "▲" : "▼";
        float chevSize = 5f;
        float chevW = Builder.INTER.get().getWidth(chevron, chevSize);
        Builder.TEXT_BUILDER
                .size(chevSize)
                .color(ColorUtils.setAlpha(c1, (int) (220 * visibility)))
                .text(chevron)
                .font(Builder.INTER.get())
                .build()
                .render(matrix, rowX + rowW - chevW - 6f, rowY + (ROW_H - Builder.INTER.get().getHeight(chevron, chevSize)) / 2f);

        float totalH = nameH + ROW_H + 4f;

        if (expandAnim > 0.01f) {
            float listX = rowX;
            float listY = rowY + ROW_H + 3f;
            float listW = rowW;
            int count = mode.getValues().size();
            float listH = count * OPTION_H + 4f;
            float revealH = listH * expandAnim;

            if (excellentStyle) {
                HelperElements.rect(matrix, listX, listY, listW, revealH, visibility * expandAnim);
            } else {
                int listBg = HelperElements.themedPanel(18, 20, 24, (int) (220 * visibility * expandAnim));
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(listW, revealH))
                        .color(new QuadColorState(listBg))
                        .radius(new QuadRadiusState(3f))
                        .build()
                        .render(matrix, listX, listY);

                Builder.BORDER_BUILDER
                        .size(new SizeState(listW, revealH))
                        .radius(new QuadRadiusState(3f))
                        .color(new QuadColorState(ColorUtils.setAlpha(c1, (int) (40 * visibility * expandAnim))))
                        .thickness(-0.1f)
                        .build()
                        .render(matrix, listX, listY);
            }

            if (expandAnim > 0.6f) {
                float aOpt = (expandAnim - 0.6f) / 0.4f;
                float oy = listY + 2f;
                for (String val : mode.getValues()) {
                    String t = (val == null ? "N/A" : val);
                    boolean selected = mode.isSelected(val);
                    boolean hovered = MouseClick.isClick(mouseX, mouseY, listX + 2f, oy, listW - 4f, OPTION_H);
                    if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);

                    if (hovered) {
                        Builder.RECTANGLE_BUILDER
                                .size(new SizeState(listW - 4f, OPTION_H))
                                .color(new QuadColorState(ColorUtils.setAlpha(c1, (int) (40 * visibility * aOpt))))
                                .radius(new QuadRadiusState(2f))
                                .build()
                                .render(matrix, listX + 2f, oy);
                    }

                    int textCol = selected
                            ? HelperElements.themedText((int) (255 * visibility * aOpt))
                            : ColorUtils.setAlpha(ColorUtils.rgb(180, 180, 185), (int) (210 * visibility * aOpt));

                    Integer previewColor = mode.getColor(val);
                    float textStartX = listX + 8f;
                    if (previewColor != null) {
                        float pSize = OPTION_H - 5f;
                        float px = listX + 6f;
                        float py = oy + (OPTION_H - pSize) / 2f;
                        int previewCol = ColorUtils.setAlpha(previewColor, (int) (ColorUtils.getAlpha(previewColor) * visibility * aOpt));
                        Builder.RECTANGLE_BUILDER
                                .radius(new QuadRadiusState(2f))
                                .size(new SizeState(pSize, pSize))
                                .color(new QuadColorState(previewCol))
                                .build()
                                .render(matrix, px, py);
                        textStartX = px + pSize + 4f;
                    }

                    Builder.TEXT_BUILDER
                            .size(VALUE_SIZE)
                            .color(textCol)
                            .text(t)
                            .font(Builder.INTER.get())
                            .build()
                            .render(matrix, textStartX, oy + (OPTION_H - Builder.INTER.get().getHeight(t, VALUE_SIZE)) / 2f - 0.5f);

                    if (selected) {
                        String check = "✓";
                        float ckSize = 6.5f;
                        float ckW = Builder.INTER.get().getWidth(check, ckSize);
                        Builder.TEXT_BUILDER
                                .size(ckSize)
                                .color(ColorUtils.setAlpha(c1, (int) (255 * visibility * aOpt)))
                                .text(check)
                                .font(Builder.INTER.get())
                                .build()
                                .render(matrix, listX + listW - ckW - 7f, oy + (OPTION_H - Builder.INTER.get().getHeight(check, ckSize)) / 2f - 0.5f);
                    }

                    oy += OPTION_H;
                }
            }

            totalH = nameH + ROW_H + 4f + revealH + 3f;
        }

        size(getWidth(), totalH);
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ModeElement mode = modeSetting.get();

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), NAME_SIZE);
        float rowX = getX();
        float rowY = getY() + nameH + 3f;
        float rowW = getWidth();

        if (MouseClick.isClick(mouseX, mouseY, rowX, rowY, rowW, ROW_H)) {
            if (mode.getValues().size() > 1) {
                expanded = !expanded;
            }
            return true;
        }

        if (expanded) {
            float listX = rowX;
            float listY = rowY + ROW_H + 3f;
            float listW = rowW;
            float oy = listY + 2f;
            for (String val : mode.getValues()) {
                if (MouseClick.isClick(mouseX, mouseY, listX + 2f, oy, listW - 4f, OPTION_H)) {
                    if (val != null) {
                        mode.set(val);
                        expanded = false;
                    }
                    return true;
                }
                oy += OPTION_H;
            }
            expanded = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
