package dev.client.api.injection;

import dev.client.api.nullcry.uiClient.autobuy.manager.AutoBuyManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixins extends HandledScreen<GenericContainerScreenHandler> {
    private ButtonWidget autoBuyButton;
    private boolean buttonAdded = false;
    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();

    protected GenericContainerScreenMixins(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String title = this.getTitle().getString();
        if (!buttonAdded) {
            addAutoBuyButton(title);
            buttonAdded = true;
        }

        if (autoBuyButton != null) {
            autoBuyButton.setMessage(Text.literal("AutoBuy: " + (autoBuyManager.isEnabled() ? "§aON" : "§cOFF")));
        }
    }

    private void addAutoBuyButton(String titleText) {
        if (!isAuctionScreen(titleText)) {
            return;
        }

        int autoBuyX = (this.width - this.backgroundWidth) / 2 + this.backgroundWidth / 2 - 40;
        int autoBuyY = (this.height - this.backgroundHeight) / 2 - 25;

        this.autoBuyButton = ButtonWidget.builder(
                Text.literal("AutoBuy: " + (autoBuyManager.isEnabled() ? "§aON" : "§cOFF")),
                button -> {
                    autoBuyManager.setEnabled(!autoBuyManager.isEnabled());
                    button.setMessage(Text.literal("AutoBuy: " + (autoBuyManager.isEnabled() ? "§aON" : "§cOFF")));
                }
        ).dimensions(autoBuyX, autoBuyY, 80, 20).build();

        this.addDrawableChild(autoBuyButton);
    }

    private boolean isAuctionScreen(String titleText) {
        return titleText.contains("Аукцион")
                || titleText.contains("Аукционы")
                || titleText.contains("Auction");
    }
}