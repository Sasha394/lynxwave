package dev.client.api.nullcry;

import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import lombok.Getter;

public interface IModule extends ClientApi {

    default LynxWave handlerClient() {
        return LynxWave.getInstance();
    }

    private boolean isServer(String id) {
        return ConnectionHelper.isConnectedToServer(id);
    }

    default boolean isFT() {
        return isServer("funtime");
    }

    default boolean isHW() {
        return isServer("holyworld") || isServer("hollyworld");
    }

    default boolean isRW() {
        return isServer("reallyworld");
    }

    default boolean isSpookyTime() {
        return isServer("spookytime");
    }

    default boolean isFunSky() {
        return isServer("funsky");
    }

    default boolean isPvP() {
        return ConnectionHelper.isPvP();
    }

    default String getWorldType() {
        return mc.world.getRegistryKey().getValue().getPath();
    }

    default int getAnarchy() {
        return ConnectionHelper.getAnarchy();
    }

    @Getter
    enum ServerType {
        FunTime("funtime"),
        HolyWorld("holyworld"),
        ReallyWorld("reallyworld"),
        SpookyTime("spookytime"),
        FunSky("funsky");

        private final String id;

        ServerType(String id) {
            this.id = id;
        }

        public boolean isConnected() {
            return ConnectionHelper.isConnectedToServer(id);
        }
    }
}
