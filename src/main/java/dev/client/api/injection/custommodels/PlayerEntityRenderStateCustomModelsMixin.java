package dev.client.api.injection.custommodels;

import dev.client.modules.core.render.custommodels.ICustomPlayerModelState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateCustomModelsMixin implements ICustomPlayerModelState {
    @Unique
    private boolean lynxwave$hasCustomModel;
    @Unique
    private String lynxwave$customModel = "";

    @Override
    public boolean lynxwave$hasCustomModel() {
        return lynxwave$hasCustomModel;
    }

    @Override
    public String lynxwave$getCustomModel() {
        return lynxwave$customModel;
    }

    @Override
    public void lynxwave$setCustomModel(boolean enabled, String model) {
        lynxwave$hasCustomModel = enabled;
        lynxwave$customModel = model == null ? "" : model;
    }
}
