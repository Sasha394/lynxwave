package dev.client.modules.core.misc;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;

public class DiscordRPC extends Module {

    public DiscordRPC() {
        super("DiscordRPC", ModuleCategory.Misc, "Отображает активность клиента в дискорде");
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        handlerClient().getDiscordManager().initRpc();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        handlerClient().getDiscordManager().stop();
    }
}
