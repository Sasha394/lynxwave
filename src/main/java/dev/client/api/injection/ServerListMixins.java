package dev.client.api.injection;

import dev.client.api.nullcry.helper.client.server.CustomServerManager;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerList.class)
public class ServerListMixins {
    @Shadow private List<ServerInfo> servers;
    @Shadow private List<ServerInfo> hiddenServers;

    @Inject(method = "loadFile", at = @At("TAIL"))
    private void lumora$injectLoad(CallbackInfo ci) {
        CustomServerManager.synchronize(this.servers, this.hiddenServers);
    }

    @Inject(method = "saveFile", at = @At("HEAD"))
    private void lumora$ensureBeforeSave(CallbackInfo ci) {
        CustomServerManager.synchronize(this.servers, this.hiddenServers);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void lumora$preventRemoval(ServerInfo serverInfo, CallbackInfo ci) {
        if (CustomServerManager.isCustomServer(serverInfo)) {
            ci.cancel();
        }
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void lumora$ensureAfterAdd(ServerInfo serverInfo, boolean hidden, CallbackInfo ci) {
        CustomServerManager.synchronize(this.servers, this.hiddenServers);
    }

    @Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
    private void lumora$preventSwap(int index1, int index2, CallbackInfo ci) {
        if (CustomServerManager.isCustomIndex(this.servers, index1) || CustomServerManager.isCustomIndex(this.servers, index2)) {
            ci.cancel();
        }
    }
}

