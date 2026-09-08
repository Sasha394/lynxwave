package dev.client.api.injection;

import dev.client.modules.core.render.beautifully.ChatAnimationState;
import net.minecraft.client.font.DrawnTextConsumer;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Beautifully «Анимация чата»: сдвиг и затухание отдельной строки применяются
 * к обоим бэкендам отрисовки чата, появившимся в 1.21.11 — обычному HUD и
 * интерактивному чату. Значения строки берутся из {@link ChatAnimationState},
 * который заполняет ChatHudMixins при обходе строк.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.hud.ChatHud$Hud",
        "net.minecraft.client.gui.hud.ChatHud$Interactable"
})
public abstract class ChatHudBackendMixins {

    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int lynx$shiftFillLeft(int x) {
        return Math.round(x - ChatAnimationState.shift());
    }

    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int lynx$shiftFillRight(int x) {
        return Math.round(x - ChatAnimationState.shift());
    }

    @ModifyArg(method = "fill(IIIII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), index = 4)
    private int lynx$fadeFill(int color) {
        int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * ChatAnimationState.alpha())));
        return color & 0x00FFFFFF | alpha << 24;
    }

    @ModifyArg(
            method = "text(IFLnet/minecraft/text/OrderedText;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/DrawnTextConsumer;text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/client/font/DrawnTextConsumer$Transformation;Lnet/minecraft/text/OrderedText;)V"),
            index = 3
    )
    private DrawnTextConsumer.Transformation lynx$animateText(DrawnTextConsumer.Transformation transformation) {
        float shift = ChatAnimationState.shift();
        Matrix3x2f pose = new Matrix3x2f(transformation.pose()).translate(-shift, 0.0f);
        return transformation.withPose(pose).withOpacity(transformation.opacity() * ChatAnimationState.alpha());
    }

    @ModifyVariable(method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int lynx$shiftIndicatorLeft(int x) {
        return Math.round(x - ChatAnimationState.shift());
    }

    @ModifyVariable(method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int lynx$shiftIndicatorRight(int x) {
        return Math.round(x - ChatAnimationState.shift());
    }

    @ModifyVariable(method = "indicator(IIIIFLnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float lynx$fadeIndicator(float opacity) {
        return opacity * ChatAnimationState.alpha();
    }

    @ModifyVariable(method = "indicatorIcon(IIZLnet/minecraft/client/gui/hud/MessageIndicator;Lnet/minecraft/client/gui/hud/MessageIndicator$Icon;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int lynx$shiftIndicatorIcon(int x) {
        return Math.round(x - ChatAnimationState.shift());
    }
}
