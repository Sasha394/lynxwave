package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.modules.core.render.ViewModel;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixins {

    @Inject(method = "onKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/InactivityFpsLimiter;onInput()V"))
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null && action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            viewModel.onChatKeyReleased(input.key());
        }
        if (MinecraftClient.getInstance().currentScreen != null) return;
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            LynxWave.getInstance().keyPressed(input.key());
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        ViewModel viewModel = ViewModel.INSTANCE;
        if (viewModel != null && viewModel.shouldConsumeChatCharacter()) {
            ci.cancel();
        }
    }
}
