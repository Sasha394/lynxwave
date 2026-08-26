package dev.client.api.framesync;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrameSync implements ModInitializer {
   public static final String MOD_ID = "framesync";
   public static final Logger LOGGER = LoggerFactory.getLogger("framesync");

   @Override
   public void onInitialize() {
      LOGGER.info("FrameSync initialized");
   }
}
