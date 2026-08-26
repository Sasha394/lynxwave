package dev.client.api.framesync.client;

import dev.client.api.framesync.FrameSync;
import dev.client.api.framesync.config.ModConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

public final class RenderLimiter {
   private static final int DEFAULT_REFRESH_RATE = 60;
   private static final int FRAME_HISTORY_SIZE = 10;
   private static final long NANOS_PER_SECOND = 1_000_000_000L;

   private int monitorHz;
   private long lastRenderTime;
   private long nextRenderTime;
   private boolean enabled;
   private long frameInterval;
   private final long[] frameTimeHistory = new long[FRAME_HISTORY_SIZE];
   private int frameTimeIndex;

   public RenderLimiter(int monitorHz) {
      setMonitorHz(monitorHz);
      this.enabled = true;
      FrameSync.LOGGER.info("RenderLimiter initialized with {} Hz", this.monitorHz);
   }

   public boolean canRender() {
      if (!this.enabled) {
         return true;
      }

      return ModConfig.getInstance().lowEndMode
            ? this.canRenderSmooth()
            : this.canRenderAggressive();
   }

   /**
    * In low-end mode, wait for the next frame instead of repeatedly running and
    * cancelling the renderer. This keeps CPU usage down and avoids the timing
    * drift present in the original accumulator implementation.
    */
   private boolean canRenderSmooth() {
      long remaining = this.nextRenderTime - System.nanoTime();
      if (remaining > 0L) {
         LockSupport.parkNanos(remaining);
      }

      return this.markFrameIfDue(System.nanoTime());
   }

   private boolean canRenderAggressive() {
      long remaining = this.nextRenderTime - System.nanoTime();
      if (remaining > 1_000_000L) {
         LockSupport.parkNanos(remaining - 500_000L);
      }
      while (System.nanoTime() < this.nextRenderTime && !Thread.currentThread().isInterrupted()) {
         Thread.onSpinWait();
      }
      return this.markFrameIfDue(System.nanoTime());
   }

   private boolean markFrameIfDue(long now) {
      if (now < this.nextRenderTime) {
         return false;
      }

      long frameTime = now - this.lastRenderTime;
      if (frameTime > 0L) {
         this.frameTimeHistory[this.frameTimeIndex] = frameTime;
         this.frameTimeIndex = (this.frameTimeIndex + 1) % FRAME_HISTORY_SIZE;
      }

      this.lastRenderTime = now;
      this.nextRenderTime += this.frameInterval;
      if (this.nextRenderTime <= now) {
         this.nextRenderTime = now + this.frameInterval;
      }
      return true;
   }

   public void setMonitorHz(int hz) {
      if (hz <= 0) {
         FrameSync.LOGGER.warn("Invalid monitor Hz value: {}, using default {}", hz, DEFAULT_REFRESH_RATE);
         hz = DEFAULT_REFRESH_RATE;
      }

      this.monitorHz = hz;
      this.frameInterval = Math.max(1L, NANOS_PER_SECOND / hz);
      this.resetTiming();
      FrameSync.LOGGER.info("Monitor Hz updated to: {}", hz);
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      this.resetTiming();
      FrameSync.LOGGER.info("RenderLimiter {}", enabled ? "enabled" : "disabled");
   }

   private void resetTiming() {
      long now = System.nanoTime();
      this.lastRenderTime = now;
      this.nextRenderTime = now;
      this.frameTimeIndex = 0;
      Arrays.fill(this.frameTimeHistory, 0L);
   }

   public int getMonitorHz() {
      return this.monitorHz;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public double getCurrentFPS() {
      if (!this.enabled) {
         return -1.0D;
      }

      long totalTime = 0L;
      int validFrames = 0;
      for (long frameTime : this.frameTimeHistory) {
         if (frameTime > 0L) {
            totalTime += frameTime;
            validFrames++;
         }
      }

      if (validFrames == 0) {
         return this.monitorHz;
      }

      return (double) NANOS_PER_SECOND / ((double) totalTime / validFrames);
   }

   public static int detectMonitorHz() {
      try {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getWindow() != null) {
            int refreshRate = client.getWindow().getRefreshRate();
            if (refreshRate > 0) {
               FrameSync.LOGGER.info("Detected monitor refresh rate: {} Hz", refreshRate);
               return refreshRate;
            }
         }
      } catch (RuntimeException exception) {
         FrameSync.LOGGER.warn("Failed to detect monitor refresh rate", exception);
      }

      FrameSync.LOGGER.info("Using default refresh rate: {} Hz", DEFAULT_REFRESH_RATE);
      return DEFAULT_REFRESH_RATE;
   }

   public static int safeDetectMonitorHz() {
      return detectMonitorHz();
   }
}
