package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.clicksSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;


import com.google.common.base.Suppliers;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.ClickSetting;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Supplier;

public class ClickComponent extends SettingComponent {
    private final Supplier<ClickSetting> clickSetting = Suppliers.memoize(() -> (ClickSetting) getSetting());
    boolean hovered;

    public ClickComponent(Setting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float btnH = 14f;
        size(240f / 2f - 10f, btnH + 6f);
    }

    @Override
    public ClickComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        ClickSetting cs = clickSetting.get();
        float visibility = getGlobalAlpha();

        final float btnH = 14f;
        final float textSize = 7f;

        String text = cs.getDisplayName();
        float tw = Builder.INTER.get().getWidth(text, textSize);
        float th = Builder.INTER.get().getHeight(text, textSize);

        float bx = getX() + 2;
        float by = getY() + 3f;
        float bw = getWidth() - 10;

        hovered = MouseClick.isClick(mouseX, mouseY, bx, by, bw, btnH);
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        boolean excellentStyle = Interface.INSTANCE.isExcellentMode();

        if (!excellentStyle && Interface.INSTANCE.blurStrength.getValue() > 0) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                    .radius(new QuadRadiusState(3f))
                    .size(new SizeState(bw, btnH))
                    .alpha(visibility)
                    .build()
                    .render(matrix, bx, by);
        }

        if (excellentStyle) {
            HelperElements.rect(matrix, bx, by, bw, btnH, visibility);
        } else {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(bw, btnH))
                    .color(new QuadColorState(
                            HelperElements.themedPanel(28, 30, 35, (int) (150 * visibility)),
                            HelperElements.themedPanel(24, 26, 30, (int) (150 * visibility)),
                            HelperElements.themedPanel(22, 24, 28, (int) (150 * visibility)),
                            HelperElements.themedPanel(26, 28, 33, (int) (150 * visibility))
                    ))
                    .radius(new QuadRadiusState(3f))
                    .build()
                    .render(matrix, bx, by);
        }


        cs.getAnimation().setMs(250).setValue(1.0);
        cs.getAnimation().setDirection(hovered ? Direction.FORWARDS : Direction.BACKWARDS);
        double t = cs.getAnimation().getOutput();
        int alpha = (int) Math.round(120 + (255 - 120) * t);
        int clampedAlpha = Math.round(Math.min(255, Math.max(0, alpha)) * visibility);
        int textCol = hovered
                ? HelperElements.themedText(clampedAlpha)
                : ColorUtils.setAlpha(ColorUtils.rgb(180, 180, 180), clampedAlpha);

        float tx = bx + (bw - tw) / 2f;
        float ty = by + (btnH - th) / 2f - 0.5f;

        Builder.TEXT_BUILDER
                .size(textSize)
                .color(textCol)
                .text(text)
                .font(Builder.INTER.get())
                .build()
                .render(matrix, tx, ty);

        if (hovered) CursorsUtil.setCursor(CursorsUtil.HAND);
        size(getWidth(), btnH + 6f);

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hovered) {
            ClickSetting cs = clickSetting.get();
            cs.tryClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
