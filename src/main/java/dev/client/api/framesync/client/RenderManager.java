package dev.client.api.framesync.client;

import dev.client.api.framesync.FrameSync;
import net.minecraft.client.MinecraftClient;

public class RenderManager {
   private static final int VANILLA_UNLIMITED_FPS = 260;
   private static final RenderManager INSTANCE = new RenderManager();
   private RenderLimiter renderLimiter;
   private boolean initialized = false;

   private RenderManager() {
   }

   public static RenderManager getInstance() {
      return INSTANCE;
   }

   public void initialize(int monitorHz, boolean enabled) {
      if (this.initialized) {
         this.updateSettings(monitorHz, enabled);
      } else {
         this.renderLimiter = new RenderLimiter(monitorHz);
         this.renderLimiter.setEnabled(enabled);
         this.initialized = true;
         this.updateVanillaLimiter(enabled);
         FrameSync.LOGGER.info("RenderManager initialized with {} Hz, enabled: {}", monitorHz, enabled);
      }

   }

   public boolean shouldRender() {
      if (!this.initialized || this.renderLimiter == null) {
         return true;
      }
      if (this.renderLimiter.isEnabled()) {
         // Prevent Minecraft's normal FPS setting from applying a second,
         // lower cap after FrameSync has already paced this frame.
         this.updateVanillaLimiter(true);
      }
      return this.renderLimiter.canRender();
   }

   public void updateSettings(int monitorHz, boolean enabled) {
      if (!this.initialized) {
         this.initialize(monitorHz, enabled);
      } else {
         this.renderLimiter.setMonitorHz(monitorHz);
         this.renderLimiter.setEnabled(enabled);
         this.updateVanillaLimiter(enabled);
      }

   }

   public RenderLimiter getRenderLimiter() {
      return this.renderLimiter;
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   private void updateVanillaLimiter(boolean frameSyncEnabled) {
      MinecraftClient client = MinecraftClient.getInstance();
      int vanillaLimit = frameSyncEnabled
            ? VANILLA_UNLIMITED_FPS
            : client.options.getMaxFps().getValue();
      client.getInactivityFpsLimiter().setMaxFps(vanillaLimit);
   }
}
