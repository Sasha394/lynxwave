package dev.client.api.injection;

import dev.client.LynxWave;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixins extends RecipeBookScreen<PlayerScreenHandler> {
    @Unique private ButtonWidget throwButton;

    public InventoryScreenMixins(PlayerScreenHandler handler, RecipeBookWidget<?> recipeBook, PlayerInventory inventory, Text title) {
        super(handler, recipeBook, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        ci.cancel();
        if (this.client != null && this.client.player != null && this.client.player.isCreative()) {
            this.client.setScreen(new CreativeInventoryScreen(this.client.player, this.client.player.networkHandler.getEnabledFeatures(), (Boolean) this.client.options.getOperatorItemsTab().getValue()));
        } else {
            super.init();
            String throwText = "Throw all";
            if (this.client.getLanguageManager().getLanguage().equals("ru_ru")) {
                throwText = "Выкинуть все";
            }

            var delayOpt = new SimpleOption<>(
                    "lynxwave.delay",
                    SimpleOption.emptyTooltip(),
                    (label, v) -> Text.literal("Задержка: " + String.format("%.2f", v)),
                    SimpleOption.DoubleSliderCallbacks.INSTANCE,
                    0.10D,
                    v -> ((LynxWave.DelayHolder) client.options).lumora$setThrowInventoryScreenDelay(v)
            );

            delayOpt.setValue(((LynxWave.DelayHolder) client.options).lumora$getThrowInventoryScreenDelay());
            this.addDrawableChild(delayOpt.createWidget(this.client.options, width/2 - 50, height/2 - 127, 100));

            throwButton = ButtonWidget.builder(Text.literal(throwText), button -> {
                        if (client.player != null && client.interactionManager != null) {
                            dropItems();
                            button.active = false;
                        }
                    })
                    .dimensions(width / 2 - 50, height / 2 - 105, 100, 20)
                    .build();

            throwButton.active = hasItemsInInventory();

            this.addDrawableChild(throwButton);
        }
    }

    @Inject(method = "handledScreenTick", at = @At("TAIL"))
    private void onHandledScreenTick(CallbackInfo ci) {
        if (this.client != null && this.client.player != null && this.client.player.isCreative()) {
            this.client.setScreen(new CreativeInventoryScreen(this.client.player, this.client.player.networkHandler.getEnabledFeatures(), (Boolean) this.client.options.getOperatorItemsTab().getValue()));
        } else {
            if (throwButton != null) {
                throwButton.active = hasItemsInInventory();
            }
        }
    }

    @Unique
    public void dropItems() {
        new Thread(() -> {
            for (int i = 0; i < this.handler.getStacks().size() && this.client.currentScreen == this; ++i) {
                if (this.handler.getStacks().get(i).isEmpty()) continue;

                try {
                    double delaySec = ((LynxWave.DelayHolder) client.options).lumora$getThrowInventoryScreenDelay();
                    Thread.sleep((long) (delaySec * 1000));
                } catch (InterruptedException e) {
                    return;
                }

                int slotIndex = i;
                client.execute(() -> {
                    client.interactionManager.clickSlot(this.handler.syncId, slotIndex, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(this.handler.syncId, -999, 0, SlotActionType.PICKUP, client.player);
                });
            }
        }).start();
    }

    @Unique
    private boolean hasItemsInInventory() {
        return this.handler.getStacks().stream().anyMatch(stack -> !stack.isEmpty());
    }
}
