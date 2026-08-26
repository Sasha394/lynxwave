package dev.client.api.injection;

import dev.client.api.nullcry.helper.client.server.CustomServerManager;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixins {
    @Shadow @Final private ServerInfo server;
    @Shadow private TextFieldWidget addressField;
    @Shadow private TextFieldWidget serverNameField;

    @Inject(method = "init", at = @At("TAIL"))
    private void lumora$lockCustomServerFields(CallbackInfo ci) {
        if (!CustomServerManager.isCustomServer(this.server)) {
            return;
        }

        CustomServerManager.applyTemplate(this.server);
        this.serverNameField.setText(this.server.name);
        this.addressField.setText(this.server.address);
        this.serverNameField.setEditable(false);
        this.addressField.setEditable(false);
    }

    @Inject(method = "addAndClose", at = @At("HEAD"))
    private void lumora$keepCanonicalData(CallbackInfo ci) {
        if (!CustomServerManager.isCustomServer(this.server)) {
            return;
        }

        CustomServerManager.applyTemplate(this.server);
        this.serverNameField.setText(this.server.name);
        this.addressField.setText(this.server.address);
    }
}

