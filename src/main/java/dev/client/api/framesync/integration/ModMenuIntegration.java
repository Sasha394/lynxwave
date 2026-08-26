package dev.client.api.framesync.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.client.api.framesync.config.ConfigScreen;

@SuppressWarnings("unused")
public class ModMenuIntegration implements ModMenuApi {
   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return ConfigScreen::new;
   }
}
