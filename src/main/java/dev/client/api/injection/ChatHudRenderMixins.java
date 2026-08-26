package dev.client.api.injection;

import dev.client.modules.core.render.Chat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudRenderMixins {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean refresh, CallbackInfo ci) {
        Chat chat = Chat.INSTANCE;
        if (chat != null && chat.isEnabled()) {
            ci.cancel();
        }
    }
}
