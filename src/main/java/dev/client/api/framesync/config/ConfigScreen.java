package dev.client.api.framesync.config;

import dev.client.api.framesync.client.RenderManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class ConfigScreen extends Screen {
   private static final int MIN_MONITOR_HZ = 30;
   private static final int MAX_MONITOR_HZ = 500;
   private static final int MIN_FPS = 15;
   private static final int MAX_MIN_FPS = 120;

   private final Screen parent;
   private final ModConfig config;

   public ConfigScreen(Screen parent) {
      super(Text.translatable("framesync.config.title"));
      this.parent = parent;
      this.config = ModConfig.getInstance();
   }

   @Override
   protected void init() {
      this.clearChildren();
      int centerX = this.width / 2;
      int startY = this.height / 4;
      int buttonWidth = 200;
      int buttonHeight = 20;
      int spacing = 25;

      SliderWidget hzSlider = new SliderWidget(
            centerX - buttonWidth / 2,
            startY,
            buttonWidth,
            buttonHeight,
            Text.empty(),
            normalize(this.config.targetMonitorHz, MIN_MONITOR_HZ, MAX_MONITOR_HZ)
      ) {
         @Override
         protected void updateMessage() {
            this.setMessage(Text.translatable("framesync.config.monitor_hz", monitorHz()));
         }

         @Override
         protected void applyValue() {
            int hz = monitorHz();
            ConfigScreen.this.config.setTargetMonitorHz(hz);
            ConfigScreen.this.updateRenderSettings();
         }

         private int monitorHz() {
            return valueFromSlider(this.value, MIN_MONITOR_HZ, MAX_MONITOR_HZ);
         }
      };
      hzSlider.active = !this.config.autoDetectMonitorHz;
      this.addDrawableChild(hzSlider);

      this.addDrawableChild(CyclingButtonWidget.onOffBuilder(
            Text.translatable("framesync.config.enabled"),
            Text.translatable("framesync.config.disabled"),
            this.config.enableRenderLimit
      ).build(
            centerX - buttonWidth / 2,
            startY + spacing,
            buttonWidth,
            buttonHeight,
            Text.translatable("framesync.config.enable_render_limit"),
            (button, enabled) -> {
               this.config.setEnableRenderLimit(enabled);
               this.updateRenderSettings();
            }
      ));

      this.addDrawableChild(CyclingButtonWidget.onOffBuilder(
            Text.translatable("framesync.config.auto_on"),
            Text.translatable("framesync.config.auto_off"),
            this.config.autoDetectMonitorHz
      ).build(
            centerX - buttonWidth / 2,
            startY + spacing * 2,
            buttonWidth,
            buttonHeight,
            Text.translatable("framesync.config.auto_detect"),
            (button, autoDetect) -> {
               this.config.setAutoDetectMonitorHz(autoDetect);
               this.updateRenderSettings();
               this.clearAndInit();
            }
      ));

      this.addDrawableChild(new SliderWidget(
            centerX - buttonWidth / 2,
            startY + spacing * 3,
            buttonWidth,
            buttonHeight,
            Text.empty(),
            normalize(this.config.minFpsLimit, MIN_FPS, MAX_MIN_FPS)
      ) {
         @Override
         protected void updateMessage() {
            this.setMessage(Text.translatable("framesync.config.min_fps", minimumFps()));
         }

         @Override
         protected void applyValue() {
            ConfigScreen.this.config.setMinFpsLimit(minimumFps());
            ConfigScreen.this.updateRenderSettings();
         }

         private int minimumFps() {
            return valueFromSlider(this.value, MIN_FPS, MAX_MIN_FPS);
         }
      });

      this.addDrawableChild(CyclingButtonWidget.onOffBuilder(
            Text.translatable("framesync.config.show"),
            Text.translatable("framesync.config.hide"),
            this.config.showFpsIndicator
      ).build(
            centerX - buttonWidth / 2,
            startY + spacing * 4,
            buttonWidth,
            buttonHeight,
            Text.translatable("framesync.config.fps_indicator"),
            (button, show) -> this.config.setShowFpsIndicator(show)
      ));

      this.addDrawableChild(CyclingButtonWidget.onOffBuilder(
            Text.translatable("framesync.config.lowend_on"),
            Text.translatable("framesync.config.lowend_off"),
            this.config.lowEndMode
      ).build(
            centerX - buttonWidth / 2,
            startY + spacing * 5,
            buttonWidth,
            buttonHeight,
            Text.translatable("framesync.config.lowend_mode"),
            (button, lowEnd) -> this.config.setLowEndMode(lowEnd)
      ));

      this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("framesync.config.reset_defaults"),
            button -> {
               this.config.resetToDefaults();
               this.updateRenderSettings();
               this.clearAndInit();
            }
      ).dimensions(centerX - buttonWidth / 2, startY + spacing * 6, buttonWidth, buttonHeight).build());

      this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.done"),
            button -> this.close()
      ).dimensions(centerX - buttonWidth / 2, startY + spacing * 7, buttonWidth, buttonHeight).build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);
      context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(String.format(
                  "Current: %d Hz, %s",
                  this.config.getEffectiveHz(),
                  this.config.enableRenderLimit ? "Enabled" : "Disabled"
            )),
            this.width / 2,
            this.height - 30,
            0xFFAAAAAA
      );
      super.render(context, mouseX, mouseY, delta);
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   private void updateRenderSettings() {
      RenderManager.getInstance().updateSettings(
            this.config.getEffectiveHz(),
            this.config.enableRenderLimit
      );
   }

   private static double normalize(int value, int min, int max) {
      return Math.max(0.0D, Math.min(1.0D, (value - min) / (double) (max - min)));
   }

   private static int valueFromSlider(double sliderValue, int min, int max) {
      return min + (int) Math.round(sliderValue * (max - min));
   }
}
