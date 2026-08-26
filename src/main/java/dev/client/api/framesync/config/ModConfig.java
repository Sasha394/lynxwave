package dev.client.api.framesync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.client.api.framesync.FrameSync;
import dev.client.api.framesync.client.RenderLimiter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
   private static final int DEFAULT_MONITOR_HZ = 60;
   private static final int DEFAULT_MIN_FPS = 30;
   private static final String CONFIG_FILE_NAME = "framesync.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

   private static ModConfig instance;

   public int targetMonitorHz = DEFAULT_MONITOR_HZ;
   public boolean enableRenderLimit = true;
   public boolean autoDetectMonitorHz = true;
   public int minFpsLimit = DEFAULT_MIN_FPS;
   public boolean showFpsIndicator;
   public boolean lowEndMode;

   public static ModConfig getInstance() {
      if (instance == null) {
         instance = loadConfig();
      }
      return instance;
   }

   private static ModConfig loadConfig() {
      if (Files.exists(CONFIG_PATH)) {
         try {
            ModConfig config = GSON.fromJson(Files.readString(CONFIG_PATH), ModConfig.class);
            if (config != null) {
               config.validate();
               FrameSync.LOGGER.info(
                     "Config loaded: {} Hz, enabled: {}",
                     config.targetMonitorHz,
                     config.enableRenderLimit
               );
               return config;
            }
         } catch (Exception exception) {
            FrameSync.LOGGER.error("Failed to load FrameSync config, using defaults", exception);
         }
      }

      ModConfig defaultConfig = new ModConfig();
      if (defaultConfig.autoDetectMonitorHz) {
         defaultConfig.targetMonitorHz = RenderLimiter.safeDetectMonitorHz();
      }
      defaultConfig.saveConfig();
      return defaultConfig;
   }

   private void validate() {
      if (this.targetMonitorHz <= 0) {
         this.targetMonitorHz = DEFAULT_MONITOR_HZ;
      }
      if (this.minFpsLimit <= 0) {
         this.minFpsLimit = DEFAULT_MIN_FPS;
      }
   }

   public void saveConfig() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
         Files.writeString(CONFIG_PATH, GSON.toJson(this));
         FrameSync.LOGGER.debug(
               "Config saved: {} Hz, enabled: {}",
               this.targetMonitorHz,
               this.enableRenderLimit
         );
      } catch (IOException exception) {
         FrameSync.LOGGER.error("Failed to save FrameSync config", exception);
      }
   }

   public void setTargetMonitorHz(int hz) {
      if (hz <= 0) {
         FrameSync.LOGGER.warn("Invalid Hz value: {}, ignoring", hz);
         return;
      }
      this.targetMonitorHz = hz;
      this.saveConfig();
   }

   public void setEnableRenderLimit(boolean enabled) {
      this.enableRenderLimit = enabled;
      this.saveConfig();
   }

   public void setAutoDetectMonitorHz(boolean autoDetect) {
      this.autoDetectMonitorHz = autoDetect;
      if (autoDetect) {
         this.targetMonitorHz = RenderLimiter.safeDetectMonitorHz();
      }
      this.saveConfig();
   }

   public void refreshDetectedMonitorHz() {
      if (!this.autoDetectMonitorHz) {
         return;
      }

      int detectedHz = RenderLimiter.safeDetectMonitorHz();
      if (detectedHz != this.targetMonitorHz) {
         this.targetMonitorHz = detectedHz;
         this.saveConfig();
      }
   }

   public void setMinFpsLimit(int minFps) {
      if (minFps <= 0) {
         FrameSync.LOGGER.warn("Invalid min FPS value: {}, ignoring", minFps);
         return;
      }
      this.minFpsLimit = minFps;
      this.saveConfig();
   }

   public void setShowFpsIndicator(boolean show) {
      this.showFpsIndicator = show;
      this.saveConfig();
   }

   public void setLowEndMode(boolean lowEnd) {
      this.lowEndMode = lowEnd;
      this.saveConfig();
   }

   public int getEffectiveHz() {
      return Math.max(this.targetMonitorHz, this.minFpsLimit);
   }

   public void resetToDefaults() {
      this.enableRenderLimit = true;
      this.autoDetectMonitorHz = true;
      this.minFpsLimit = DEFAULT_MIN_FPS;
      this.showFpsIndicator = false;
      this.lowEndMode = false;
      this.targetMonitorHz = RenderLimiter.safeDetectMonitorHz();
      this.saveConfig();
      FrameSync.LOGGER.info("Config reset to defaults");
   }
}
