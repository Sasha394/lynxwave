package dev.client.api.framesync.mixin;

import dev.client.api.framesync.client.RenderManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void framesync$limitRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
      RenderManager renderManager = RenderManager.getInstance();
      if (renderManager.isInitialized() && !renderManager.shouldRender()) {
         ci.cancel();
      }

   }
}
