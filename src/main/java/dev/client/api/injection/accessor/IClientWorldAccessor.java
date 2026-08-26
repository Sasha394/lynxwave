package dev.client.api.injection.accessor;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientWorld.class)
public interface IClientWorldAccessor {
    @Accessor("pendingUpdateManager") PendingUpdateManager getPendingUpdateManager();
}