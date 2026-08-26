package dev.client.api.framesync.client;

import dev.client.api.framesync.FrameSync;
import dev.client.api.framesync.config.ConfigScreen;
import dev.client.api.framesync.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class FrameSyncClient implements ClientModInitializer {
   private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
         Identifier.of(FrameSync.MOD_ID, "controls")
   );

   private static KeyBinding configKeyBinding;
   private static boolean renderSystemInitialized;

   @Override
   public void onInitializeClient() {
      FrameSync.LOGGER.info("FrameSync client initialized");
      registerKeyBindings();
      registerEventHandlers();
   }

   private static void initializeRenderSystemSafely(MinecraftClient client) {
      if (renderSystemInitialized || client == null || client.getWindow() == null) {
         return;
      }

      try {
         ModConfig config = ModConfig.getInstance();
         config.refreshDetectedMonitorHz();
         RenderManager.getInstance().initialize(config.getEffectiveHz(), config.enableRenderLimit);
         renderSystemInitialized = true;
         FrameSync.LOGGER.info("Render system initialized with {} Hz", config.getEffectiveHz());
      } catch (RuntimeException exception) {
         FrameSync.LOGGER.error("Failed to initialize render system", exception);
      }
   }

   private static void registerKeyBindings() {
      configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "framesync.key.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
      ));
   }

   private static void registerEventHandlers() {
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         initializeRenderSystemSafely(client);

         while (configKeyBinding.wasPressed()) {
            if (client.currentScreen == null) {
               client.setScreen(new ConfigScreen(null));
            }
         }
      });
   }
}
