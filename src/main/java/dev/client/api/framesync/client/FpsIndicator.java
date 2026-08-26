package dev.client.api.framesync.client;

import dev.client.api.framesync.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

public final class FpsIndicator {
   private static final long UPDATE_INTERVAL_NANOS = 1_000_000_000L;

   private static long lastUpdateTime = System.nanoTime();
   private static int frameCount;
   private static double currentFps;

   private FpsIndicator() {
   }

   public static void updateFps() {
      frameCount++;
      long currentTime = System.nanoTime();
      long elapsed = currentTime - lastUpdateTime;
      if (elapsed >= UPDATE_INTERVAL_NANOS) {
         currentFps = frameCount * (double) UPDATE_INTERVAL_NANOS / elapsed;
         frameCount = 0;
         lastUpdateTime = currentTime;
      }
   }

   public static void render(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      ModConfig config = ModConfig.getInstance();
      if (!config.showFpsIndicator || client.options.hudHidden || !RenderManager.getInstance().isInitialized()) {
         return;
      }

      int targetFps = config.getEffectiveHz();
      String fpsText = String.format(Locale.ROOT, "FPS: %.1f / %d Hz", currentFps, targetFps);
      if (!config.enableRenderLimit) {
         fpsText += " (Unlimited)";
      }

      int color = getFpsColor(currentFps, targetFps);
      int textWidth = client.textRenderer.getWidth(fpsText);
      int x = context.getScaledWindowWidth() - textWidth - 10;
      int y = 10;
      context.fill(x - 2, y - 2, x + textWidth + 2, y + client.textRenderer.fontHeight + 2, 0x80000000);
      context.drawTextWithShadow(client.textRenderer, Text.literal(fpsText), x, y, color);
   }

   private static int getFpsColor(double fps, int targetFps) {
      double ratio = targetFps > 0 ? fps / targetFps : 1.0D;
      if (ratio >= 0.9D) {
         return 0xFF00FF00;
      }
      return ratio >= 0.7D ? 0xFFFFFF00 : 0xFFFF5555;
   }
}
