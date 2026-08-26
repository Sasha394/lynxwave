package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class MinecraftLogger implements Logger, ClientApi {

    @Override
    public void log(String message) {
        printClient(message);
    }

    @Override
    public void log(Text message) {
        printClient(message);
    }
}
