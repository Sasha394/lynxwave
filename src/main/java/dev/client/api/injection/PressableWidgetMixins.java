package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixins extends ClickableWidget {

    protected PressableWidgetMixins(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void lumora$renderButtonWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof ButtonWidget)) {
            return;
        }

        if (!this.visible) {
            ci.cancel();
            return;
        }

        boolean hovered = this.isHovered();
        float baseShow = this.active ? (hovered ? 1.0f : 0.75f) : 0.45f;
        float show = MathHelper.clamp(this.alpha * baseShow, 0.0f, 1.0f);

        int color;

        if (LynxWave.getInstance() != null) {
            int baseColor = -1;
            float textIntensity = this.active ? (hovered ? 1.0f : 0.9f) : 0.55f;
            int alpha = MathHelper.clamp(Math.round(ColorUtils.getAlpha(baseColor) * textIntensity * this.alpha), 0, 255);
            color = ColorUtils.setAlpha(baseColor, alpha);
        } else {
            int baseColor = this.active ? 0xFFFFFF : 0xA0A0A0;
            int alpha = MathHelper.ceil(this.alpha * 255.0F);
            color = (alpha << 24) | baseColor;
        }

        Text message = this.getMessage();
        float fontSize = 8f;
        float textWidth = Builder.INTER.get().getWidth(message, fontSize);
        float textHeight = Builder.INTER.get().getHeight(message, fontSize);

        float textX = this.getX() + (this.getWidth() - textWidth) / 2f;
        float textY = this.getY() + (this.getHeight() - textHeight) / 2f;

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        RenderCoreCompat.withPostGuiDraws(() -> {
            renderCrispButtonBackground(context, hovered, show);
            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(message)
                    .size(fontSize)
                    .color(color)
                    .build()
                    .render(matrix, textX, textY);
        });

        ci.cancel();
    }

    private void renderCrispButtonBackground(DrawContext context, boolean hovered, float show) {
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        if (Interface.INSTANCE != null && Interface.INSTANCE.isExcellentMode()) {
            HelperElements.rect(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), show);

            if (hovered && this.active) {
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(this.getWidth(), this.getHeight()))
                        .color(new QuadColorState(ColorUtils.setAlpha(-1, MathHelper.clamp(Math.round(16.0f * show), 0, 255))))
                        .radius(new QuadRadiusState(2.0f))
                        .build()
                        .render(matrix, this.getX(), this.getY());
            }
            return;
        }

        int alpha = MathHelper.clamp(Math.round(150.0f * show), 0, 255);
        int top = HelperElements.themedPanel(hovered ? 38 : 24, hovered ? 40 : 26, hovered ? 48 : 32, alpha);
        int bottom = HelperElements.themedPanel(hovered ? 24 : 16, hovered ? 26 : 18, hovered ? 32 : 24, alpha);
        int border = ColorUtils.setAlpha(ColorUtils.rgb(255, 255, 255), MathHelper.clamp(Math.round(45.0f * show), 0, 255));

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(this.getWidth(), this.getHeight()))
                .color(new QuadColorState(top, top, bottom, bottom))
                .radius(new QuadRadiusState(4.0f))
                .build()
                .render(matrix, this.getX(), this.getY());

        Builder.BORDER_BUILDER
                .size(new SizeState(this.getWidth(), this.getHeight()))
                .color(new QuadColorState(border))
                .radius(new QuadRadiusState(4.0f))
                .thickness(-0.35f)
                .build()
                .render(matrix, this.getX(), this.getY());
    }
}
