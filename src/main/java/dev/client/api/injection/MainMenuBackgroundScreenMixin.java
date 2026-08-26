package dev.client.api.injection;

import dev.client.api.compat.GuiMatrixCompat;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.title.TitleBackgroundRenderer;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.DemoScreen;
import net.minecraft.client.gui.screen.FatalErrorScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.PopupScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers vanilla screens which override Screen#renderBackground and would
 * otherwise bypass the common MainMenu wallpaper replacement.
 */
@Mixin({
        PopupScreen.class,
        DemoScreen.class,
        FatalErrorScreen.class,
        MessageScreen.class,
        CreditsScreen.class
})
public class MainMenuBackgroundScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void replaceOverriddenBackground(DrawContext context, int mouseX, int mouseY,
                                             float delta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().world != null) {
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
