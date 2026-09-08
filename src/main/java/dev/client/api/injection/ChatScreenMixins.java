package dev.client.api.injection;

import dev.client.api.injection.accessor.IClickableWidgetAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.cmdHelper.managers.dragHandler.DraggableManager;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Beautifully;
import dev.client.modules.core.render.Chat;
import dev.client.modules.core.render.beautifully.Easing;
import dev.client.modules.core.render.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixins extends Screen {

    protected ChatScreenMixins() {
        super(Text.empty());
    }

    @Shadow
    protected TextFieldWidget chatField;
    @Unique
    private static boolean streamerMode = false;

    @Unique
    private final int PAD_L = 0, SPACE = 2;
    @Unique
    private final int BOX_PAD_X = 1;
    @Unique
    private final int BOX_EXTRA_W = 4;

    @Unique
    private int streamerBoxX, resetBoxX, buttonBoxY, buttonBoxH, streamerBoxW, resetBoxW;
    @Unique
    private int streamerGlyphX, resetGlyphX, glyphBaselineY;

    @Unique
    private final String[] STREAMER_HIDE = {
            "reg", "register", "l", "login", "call", "tpa", "warp", "cp"
    };

    @Inject(method = "init", at = @At("TAIL"))
    private void initTail(CallbackInfo ci) {
        int reserve = reservedRight();
        int outer = widgetOuterWidth(chatField);
        setFieldWidth(chatField, Math.max(40, outer - reserve - 1));
        computeGlyphLayout();
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void onResize(int width, int height, CallbackInfo ci) {
        int reserve = reservedRight();
        int outer = widgetOuterWidth(chatField);
        setFieldWidth(chatField, Math.max(40, outer - reserve - 1));
        computeGlyphLayout();
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void shrinkChatRect(Args args) {
        Chat chat = Chat.INSTANCE;
        if (chat != null && chat.isEnabled()) {
            args.set(4, 0); // transparent color
            args.set(2, args.<Integer>get(0)); // x2 = x1 => zero width
            return;
        }

        if (this.chatField != null) {
            computeGlyphLayout();
        }

        int originalRight = args.<Integer>get(2);
        int fallbackRight = originalRight - reservedRight() - 1;

        int targetRight = (this.chatField != null ? Math.max(0, this.streamerBoxX - 1) : fallbackRight);
        int newRight = Math.min(originalRight, targetRight);
        args.set(2, newRight);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;render(Lnet/minecraft/client/gui/DrawContext;II)V", shift = At.Shift.BEFORE))
    private void drawGlyphsWithBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        computeGlyphLayout();
        var tr = MinecraftClient.getInstance().textRenderer;

        int bg = MinecraftClient.getInstance().options.getTextBackgroundColor(Integer.MIN_VALUE);
        ctx.fill(streamerBoxX, buttonBoxY, streamerBoxX + streamerBoxW, buttonBoxY + buttonBoxH, bg);
        ctx.fill(resetBoxX, buttonBoxY, resetBoxX + resetBoxW, buttonBoxY + buttonBoxH, bg);

        int colorR = streamerMode ? -1 : ColorUtils.rgb(180, 180, 180);
        int colorH = ColorUtils.rgb(180, 180, 180);
        ctx.drawText(tr, "R", streamerGlyphX, glyphBaselineY, colorR, false);
        ctx.drawText(tr, "H", resetGlyphX, glyphBaselineY, colorH, false);

        if (isHoveredStreamer(mouseX, mouseY)) {
            ctx.drawTooltip(tr, Text.literal("Режим стримера"), mouseX, mouseY);
        } else if (isHoveredReset(mouseX, mouseY)) {
            ctx.drawTooltip(tr, Text.literal("Сбросить позиции драггейбла"), mouseX, mouseY);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderDragsAndTopMask(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean anyHovered = false;

        CursorsUtil.beginFrame();
        DraggableManager.updatePanelCursor(mouseX, mouseY);

        Matrix4f matrix4f = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);
        List<DraggableSettingsPanel> openPanels = new ArrayList<>();

        for (var dragging : DraggableManager.draggable.values()) {
            dragging.drawElement(context, matrix4f, mouseX, mouseY, MinecraftClient.getInstance().getWindow());
            boolean active = dragging.isActive();
            if (active && dragging.isHovered(mouseX, mouseY)) {
                anyHovered = true;
            }
            var panel = dragging.getSettingsPanel();
            if (panel != null && panel.isVisible() && active) {
                openPanels.add(panel);
            }
        }

        if (anyHovered) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null) {
            viewModel.renderChatEditor(context, mouseX, mouseY);
        }

        openPanels.forEach(panel -> panel.render(context, mouseX, mouseY, delta));
        CursorsUtil.applyFrameCursor();

        if (streamerMode && shouldHide(chatField.getText())) {
            var tr = MinecraftClient.getInstance().textRenderer;

            boolean bgOn = chatField.drawsBackground();
            float innerLeft = bgOn ? chatField.getX() + 3 : chatField.getX() - 1;
            float innerWidth = chatField.getInnerWidth();

            String visible = tr.trimToWidth(chatField.getText(), (int) innerWidth);
            float textW = tr.getWidth(visible);
            float maskW = Math.min(textW + 1, innerWidth);

            if (maskW > 0) {
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(maskW, chatField.getHeight() + 0.5f))
                        .color(new QuadColorState(0xFFFFFFFF))
                        .radius(new QuadRadiusState(0f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), innerLeft, chatField.getY() - 2);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onViewModelKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null && viewModel.onChatKeyPressed(input.key())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        computeGlyphLayout();

        for (var dragging : DraggableManager.draggable.values()) {
            var panel = dragging.getSettingsPanel();
            if (panel != null && panel.mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (button == 0) {
            if (isHoveredStreamer((int) mouseX, (int) mouseY)) {
                streamerMode = !streamerMode;
                cir.setReturnValue(true);
                return;
            }
            if (isHoveredReset((int) mouseX, (int) mouseY)) {
                DraggableManager.draggable.values().forEach(DraggableHandler::resetPosition);
                DraggableManager.closeAllPanels();
                cir.setReturnValue(true);
                return;
            }
        }

        for (var dragging : DraggableManager.draggable.values()) {
            if (dragging.onMouseClick(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (this.chatField != null && this.chatField.isMouseOver(mouseX, mouseY)) {
            return;
        }

        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null && viewModel.onChatMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null) {
            viewModel.stopChatDragging();
        }
        DraggableManager.draggable.values().forEach(d -> {
            d.onMouseRelease(button);
            if (d.getSettingsPanel() != null) {
                d.getSettingsPanel().mouseReleased(mouseX, mouseY, button);
            }
        });
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null && viewModel.onChatMouseDragged(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        for (var dragging : DraggableManager.draggable.values()) {
            var panel = dragging.getSettingsPanel();
            if (panel != null && panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }

        ViewModel viewModel = ViewModel.INSTANCE;
        boolean overChatField = this.chatField != null && this.chatField.isMouseOver(mouseX, mouseY);
        if (!overChatField && viewModel != null && viewModel.onChatMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }

        Chat chat = Chat.INSTANCE;
        if (chat != null && chat.isEnabled()) {
            chat.onScroll(verticalAmount);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null) {
            viewModel.stopChatEditing();
        }
        DraggableManager.closeAllPanels();
    }

    @Unique
    private void computeGlyphLayout() {
        if (this.chatField == null) return;
        var tr = MinecraftClient.getInstance().textRenderer;

        int wR = tr.getWidth("R");
        int wH = tr.getWidth("H");
        this.streamerBoxW = wR + BOX_PAD_X * 2 + BOX_EXTRA_W;
        this.resetBoxW = wH + BOX_PAD_X * 2 + BOX_EXTRA_W;

        this.buttonBoxH = this.chatField.getHeight();
        this.buttonBoxY = this.chatField.getY() - 2;

        int rightEdge = this.width - 2;
        int totalW = streamerBoxW + SPACE + resetBoxW;

        this.streamerBoxX = rightEdge - totalW;
        this.resetBoxX = this.streamerBoxX + streamerBoxW + SPACE;

        this.streamerGlyphX = (int) (this.streamerBoxX + (streamerBoxW - wR) / 2f + 0.5f);
        this.resetGlyphX = (int) (this.resetBoxX + (resetBoxW - wH) / 2f + 0.5f);
        this.glyphBaselineY = (int) (this.buttonBoxY + (this.buttonBoxH - tr.fontHeight) / 2f + 1.25f);
    }

    @Unique
    private boolean shouldHide(String raw) {
        if (raw == null) return false;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        if (t.startsWith("/")) t = t.substring(1);
        for (String k : STREAMER_HIDE) {
            if (t.equals(k) || t.startsWith(k + " ")) return true;
        }
        return false;
    }

    @Unique
    private int reservedRight() {
        var tr = MinecraftClient.getInstance().textRenderer;
        int wR = tr.getWidth("R");
        int wH = tr.getWidth("H");
        int boxRW = wR + BOX_PAD_X * 2 + BOX_EXTRA_W;
        int boxHW = wH + BOX_PAD_X * 2 + BOX_EXTRA_W;
        return PAD_L + boxRW + SPACE + boxHW + 1;
    }

    @Unique
    private int widgetOuterWidth(TextFieldWidget field) {
        int inner = field.getInnerWidth();
        return field.drawsBackground() ? inner + 8 : inner;
    }

    @Unique
    private void setFieldWidth(TextFieldWidget field, int width) {
        try {
            field.setWidth(width);
        } catch (Throwable ignore) {
            ((IClickableWidgetAccessor) field).setWidth(width);
        }
    }

    @Unique
    private boolean isHoveredStreamer(int mouseX, int mouseY) {
        return MouseClick.isClick(mouseX, mouseY, streamerBoxX, buttonBoxY, streamerBoxW, buttonBoxH);
    }

    @Unique
    private boolean isHoveredReset(int mouseX, int mouseY) {
        return MouseClick.isClick(mouseX, mouseY, resetBoxX, buttonBoxY, resetBoxW, buttonBoxH);
    }

    // ---- Beautifully «Анимация чата»: выезд полосы ввода ----

    @Unique private long lynx$openTime;
    @Unique private boolean lynx$barShifted;

    @Inject(method = "init", at = @At("HEAD"))
    private void lynx$startOpenAnimation(CallbackInfo ci) {
        this.lynx$openTime = System.currentTimeMillis();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", shift = At.Shift.AFTER))
    private void lynx$pushInputAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.lynx$barShifted = false;
        if (!Beautifully.chatAnimation()) return;

        float progress = (System.currentTimeMillis() - this.lynx$openTime) / 200.0f;
        if (progress >= 1.0f || progress < 0.0f) return;

        float eased = Easing.CUBIC_OUT.ease(progress, 0.0f, 1.0f, 1.0f);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, (1.0f - eased) * 16.0f);
        this.lynx$barShifted = true;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;render(Lnet/minecraft/client/gui/DrawContext;II)V", shift = At.Shift.AFTER))
    private void lynx$popInputAnimation(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.lynx$barShifted) return;

        this.lynx$barShifted = false;
        context.getMatrices().popMatrix();
    }
}
