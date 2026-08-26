package dev.client.api.injection;

import dev.client.api.nullcry.helper.client.server.CustomServerManager;
import net.minecraft.MinecraftVersion;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixins {
    @Shadow protected MultiplayerServerListWidget serverListWidget;
    @Shadow private ButtonWidget buttonDelete;

    @Inject(method = "updateButtonActivationStates", at = @At("TAIL"))
    private void lumora$disableDeleteForCustom(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry entry = this.serverListWidget.getSelectedOrNull();
        if (entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry) {
            ServerInfo serverInfo = serverEntry.getServer();
            if (CustomServerManager.isCustomServer(serverInfo)) {
                this.buttonDelete.active = false;
            }
        }
    }


    @Inject(method = "removeEntry", at = @At("HEAD"), cancellable = true)
    private void lumora$preventRemoval(boolean confirmedAction, CallbackInfo ci) {
        if (!confirmedAction) {
            return;
        }

        MultiplayerServerListWidget.Entry entry = this.serverListWidget.getSelectedOrNull();
        if (entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry) {
            if (CustomServerManager.isCustomServer(serverEntry.getServer())) {
                ci.cancel();
            }
        }
    }
}
