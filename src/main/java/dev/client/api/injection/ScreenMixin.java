package dev.client.api.injection;

import dev.client.api.compat.GuiMatrixCompat;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.altManager.AltScreenManager;
import dev.client.api.nullcry.uiClient.title.TitleBackgroundRenderer;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void disableBackgroundBlurAndDimming(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof AltScreenManager) {
            ci.cancel();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            Screen screen = (Screen) (Object) this;
            String backgroundPath = TitleScreenSettings.getBackgroundPath();

            Builder.TEXTURE_BUILDER
                    .size(new SizeState(screen.width, screen.height))
                    .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                    .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                    .build()
                    .render(GuiMatrixCompat.toMatrix4(context), 0, 0);
            ci.cancel();
        }
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void replaceEveryVanillaPanorama(DrawContext context, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            return;
        }

        Screen screen = (Screen) (Object) this;
        String backgroundPath = TitleScreenSettings.getBackgroundPath();
        Builder.TEXTURE_BUILDER
                .size(new SizeState(screen.width, screen.height))
                .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                .build()
                .render(GuiMatrixCompat.toMatrix4(context), 0, 0);
        ci.cancel();
    }
}
