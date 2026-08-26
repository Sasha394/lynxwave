package dev.client.api.injection;

import dev.client.api.injection.accessor.IChatScreenAccessor;
import dev.client.modules.core.render.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixins extends ClickableWidget {

    @Shadow private TextRenderer textRenderer;
    @Shadow private String text;
    @Shadow private int firstCharacterIndex;

    @Shadow public abstract boolean drawsBackground();
    @Shadow public abstract int getInnerWidth();

    protected TextFieldWidgetMixins(int x, int y, int width, int height) {
        super(x, y, width, height, null);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void onRenderWidgetHead(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatScreen chatScreen) {
            TextFieldWidget chatField = ((IChatScreenAccessor) chatScreen).getChatField();
            if ((Object) this == chatField && Chat.INSTANCE != null && Chat.INSTANCE.isEnabled()) {
                ci.cancel();
                return;
            }
        }

        final int left = this.drawsBackground() ? this.getX() + 4 : this.getX();
        final int right = left + this.getInnerWidth() - 4;
        final int top = this.getY() - 1;
        final int bottom = this.getY() + this.getHeight() + 1;
        ctx.enableScissor(left, top, right, bottom);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void onRenderWidgetTail(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ctx.disableScissor();
    }

    @Inject(method = "getCharacterX", at = @At("HEAD"), cancellable = true)
    private void onGetCharacterX(int index, CallbackInfoReturnable<Integer> cir) {
        final int baseX = this.drawsBackground() ? this.getX() + 4 : this.getX();
        if (index <= 0 || this.text == null || this.text.isEmpty()) {
            cir.setReturnValue(baseX);
            return;
        }

        final int len = this.text.length();
        final int clamped = Math.min(index, len);
        final int from = Math.min(this.firstCharacterIndex, len);
        final int to = Math.max(from, clamped);

        int dx = 0;
        if (from < to) dx = this.textRenderer.getWidth(this.text.substring(from, to));
        int x = baseX + dx;
        final int innerRight = baseX + this.getInnerWidth() - 4;
        if (x > innerRight) x = innerRight;
        cir.setReturnValue(x);
    }
}
