package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.client.LynxWave;
import dev.client.api.injection.accessor.IChatHudAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.misc.NameProtect;
import dev.client.modules.core.render.Beautifully;
import dev.client.modules.core.render.beautifully.ChatAnimationState;
import dev.client.modules.core.render.beautifully.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public abstract class ChatHudMixins {
    @Unique private static final Pattern COORDS_PATTERN = Pattern.compile("\\[(\\-?\\d+) (\\-?\\d+) (\\-?\\d+)]");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        Text modified = makeCoordinatesClickable(message);

        if (NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnabled()) {
            modified = NameProtect.INSTANCE.patchText(modified);
        }

        ChatHudLine line = new ChatHudLine(
                MinecraftClient.getInstance().inGameHud.getTicks(),
                modified,
                signature,
                indicator
        );

        IChatHudAccessor accessor = (IChatHudAccessor) this;
        accessor.invokeAddVisibleMessage(line);
        accessor.invokeAddMessage(line);
        ci.cancel();
    }


    @Unique
    private Text makeCoordinatesClickable(Text component) {
        if (component == null) return null;
        if (component instanceof MutableText) {
            MutableText copy = component.copy();
            copy.getSiblings().replaceAll(this::makeCoordinatesClickable);

            Matcher matcher = COORDS_PATTERN.matcher(copy.getString());
            if (matcher.find()) {
                String x = matcher.group(1);
                String y = matcher.group(2);
                String z = matcher.group(3);

                String prefix = LynxWave.getInstance()
                        .getCmdInitializer()
                        .getCommandDispatcher()
                        .getPrefix()
                        .get();

                String gpsCommand = String.format("%sgps add %s %s %s %s", prefix, "Ивент", x, y, z);

                copy.setStyle(
                        copy.getStyle()
                                .withClickEvent(new ClickEvent.SuggestCommand(gpsCommand))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Нажмите, чтобы вставить GPS-команду")))
                );
            }

            return copy;
        }
        return component;
    }

    // ---- Beautifully «Анимация чата» ----

    @Unique private static final float BEAUTIFULLY_FADE_TICKS = 4.0f;
    @Unique private static final float BEAUTIFULLY_SHIFT = 12.0f;
    @Unique private static final float BEAUTIFULLY_BAR_HEIGHT = 16.0f;
    @Unique private static final float BEAUTIFULLY_DURATION = 200.0f;

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;

    @Shadow public abstract int getVisibleLineCount();

    @Shadow
    private int getLineHeight() {
        throw new AssertionError();
    }

    @Shadow
    private double getChatScale() {
        throw new AssertionError();
    }

    @Unique private boolean lynx$animate;
    @Unique private float lynx$tick;
    @Unique private boolean lynx$focused;
    @Unique private boolean lynx$wasFocused;
    @Unique private long lynx$openStart = Long.MIN_VALUE;
    @Unique private long lynx$closeStart = Long.MIN_VALUE;
    @Unique private int lynx$focusedLines;
    @Unique private float lynx$lineOpacity = 1.0f;
    @Unique private float lynx$lineAlpha = 1.0f;
    @Unique private float lynx$lineShift;
    @Unique private int lynx$linesBefore;
    @Unique private float lynx$slideLines;
    @Unique private long lynx$slideStart = Long.MIN_VALUE;
    @Unique private boolean lynx$shifted;

    @Inject(method = "addVisibleMessage", at = @At("HEAD"))
    private void lynx$rememberLineCount(ChatHudLine message, CallbackInfo ci) {
        this.lynx$linesBefore = this.visibleMessages.size();
    }

    @Inject(method = "addVisibleMessage", at = @At("TAIL"))
    private void lynx$startSlide(ChatHudLine message, CallbackInfo ci) {
        // При прокрученном чате новые строки не выезжают — иначе список дёргается.
        if (!Beautifully.chatAnimation() || this.scrolledLines > 0) return;

        int added = this.visibleMessages.size() - this.lynx$linesBefore;
        if (added <= 0) return;

        this.lynx$slideLines = Math.min(4, added);
        this.lynx$slideStart = System.currentTimeMillis();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
    private void lynx$beginRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean refresh, CallbackInfo ci) {
        this.lynx$animate = Beautifully.chatAnimation();
        this.lynx$tick = currentTick + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        this.lynx$focused = focused;
        this.lynx$lineOpacity = 1.0f;
        this.lynx$lineAlpha = 1.0f;
        this.lynx$lineShift = 0.0f;
        ChatAnimationState.reset();
        this.lynx$shifted = false;

        if (focused != this.lynx$wasFocused) {
            this.lynx$wasFocused = focused;
            if (focused) {
                this.lynx$openStart = System.currentTimeMillis();
            } else {
                this.lynx$closeStart = System.currentTimeMillis();
            }
        }

        if (!this.lynx$animate || this.lynx$slideStart == Long.MIN_VALUE) return;

        float progress = (System.currentTimeMillis() - this.lynx$slideStart) / BEAUTIFULLY_DURATION;
        if (progress >= 1.0f || progress < 0.0f) return;

        float remaining = 1.0f - Easing.CUBIC_OUT.ease(progress, 0.0f, 1.0f, 1.0f);
        float offset = remaining * this.lynx$slideLines * this.getLineHeight() * (float) this.getChatScale();
        if (offset <= 0.05f) return;

        context.enableScissor(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight() - 40);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, offset);
        this.lynx$shifted = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("TAIL"))
    private void lynx$endRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean refresh, CallbackInfo ci) {
        if (this.lynx$shifted) {
            this.lynx$shifted = false;
            context.getMatrices().popMatrix();
            context.disableScissor();
        }
        this.lynx$drawClosingInput(context);
    }

    @Redirect(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
    private Object lynx$captureLine(List<ChatHudLine.Visible> list, int index) {
        ChatHudLine.Visible line = list.get(index);
        this.lynx$lineOpacity = 1.0f;
        this.lynx$lineAlpha = 1.0f;
        this.lynx$lineShift = 0.0f;
        if (!this.lynx$animate) return line;

        float life = lynx$life(this.lynx$tick - line.addedTime());
        float value;
        if (this.lynx$focused) {
            value = MathHelper.lerp(this.lynx$openProgress(), life, 1.0f);
            this.lynx$lineAlpha = value;
        } else {
            value = Math.max(life, this.lynx$closeFade());
            this.lynx$lineOpacity = value;
        }

        this.lynx$lineShift = (1.0f - value) * BEAUTIFULLY_SHIFT;
        ChatAnimationState.set(this.lynx$lineAlpha, this.lynx$lineShift);
        return line;
    }

    @ModifyExpressionValue(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud$OpacityRule;calculate(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;)F"))
    private float lynx$messageOpacity(float original) {
        return this.lynx$animate ? this.lynx$lineOpacity : original;
    }

    @Redirect(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;getVisibleLineCount()I"))
    private int lynx$visibleLineCount(ChatHud chatHud) {
        int count = chatHud.getVisibleLineCount();
        if (this.lynx$focused) {
            this.lynx$focusedLines = count;
            return count;
        }
        // Пока чат закрывается, держим прежнее число строк, иначе они пропадут рывком.
        if (!this.lynx$animate || this.lynx$closeFade() <= 0.0f) return count;
        return Math.max(count, this.lynx$focusedLines);
    }

    @Inject(method = "forEachVisibleLine", at = @At("RETURN"))
    private void lynx$resetLineAnimation(CallbackInfoReturnable<Integer> cir) {
        this.lynx$lineOpacity = 1.0f;
        this.lynx$lineAlpha = 1.0f;
        this.lynx$lineShift = 0.0f;
        ChatAnimationState.reset();
    }

    /** Полоса ввода, уезжающая вниз после закрытия чата. */
    @Unique
    private void lynx$drawClosingInput(DrawContext context) {
        if (!this.lynx$animate || this.lynx$focused) return;

        float fade = this.lynx$closeFade();
        if (fade <= 0.0f) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int background = MinecraftClient.getInstance().options.getTextBackgroundColor(Integer.MIN_VALUE);
        int alpha = MathHelper.clamp(Math.round((background >>> 24) * fade), 0, 255);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, (1.0f - fade) * BEAUTIFULLY_BAR_HEIGHT);
        context.fill(2, height - 14, width - 2, height - 2, background & 0xFFFFFF | alpha << 24);
        context.getMatrices().popMatrix();
    }

    /** Затухание строки по её возрасту в тиках. */
    @Unique
    private static float lynx$life(float age) {
        float value = MathHelper.clamp((200.0f - age) / BEAUTIFULLY_FADE_TICKS, 0.0f, 1.0f);
        return value * value;
    }

    @Unique
    private float lynx$openProgress() {
        if (this.lynx$openStart == Long.MIN_VALUE) return 1.0f;

        float progress = (System.currentTimeMillis() - this.lynx$openStart) / BEAUTIFULLY_DURATION;
        if (progress <= 0.0f) return 0.0f;
        if (progress >= 1.0f) return 1.0f;
        return Easing.CUBIC_OUT.ease(progress, 0.0f, 1.0f, 1.0f);
    }

    @Unique
    private float lynx$closeFade() {
        if (this.lynx$closeStart == Long.MIN_VALUE) return 0.0f;

        float progress = (System.currentTimeMillis() - this.lynx$closeStart) / BEAUTIFULLY_DURATION;
        if (progress <= 0.0f) return 1.0f;
        if (progress >= 1.0f) return 0.0f;
        return 1.0f - Easing.CUBIC_OUT.ease(progress, 0.0f, 1.0f, 1.0f);
    }
}
