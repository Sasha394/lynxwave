package dev.client.component.core.client;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.IModule;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.component.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ModuleConnectionUpdate extends Component {
    private static final File STORAGE_FILE = new File(mc.runDirectory, "last_server.txt");

    private IModule.ServerType activeServer = null;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        IModule.ServerType current = getCurrentServer();

        if (current == null) {
            if (activeServer != null) {
                saveLastServer(activeServer);
            }
            activeServer = null;

            for (Module module : LynxWave.getInstance().getModuleManager()) {
                if (module.isEnabled() && !module.getAllowedServers().isEmpty()) {
                    module.printClient("Модуль \"" + module.getName() + "\" был отключён: он недоступен в одиночном мире");
                    module.toggle();
                }
            }
            return;
        }

        if (activeServer != current) {
            activeServer = current;
            saveLastServer(current);
        }

        for (Module module : LynxWave.getInstance().getModuleManager()) {
            if (module.isEnabled()
                    && !module.getAllowedServers().isEmpty()
                    && !module.getAllowedServers().contains(current)) {
                module.printClient("Модуль \"" + module.getName() + "\" был отключён: он недоступен на сервере " + current.name());
                module.toggle();
            }
        }
    }

    private IModule.ServerType getCurrentServer() {
        String ip = ConnectionHelper.getServerIP();
        if (ip == null || ip.isEmpty() || ip.equals("local")) return null;

        for (IModule.ServerType type : IModule.ServerType.values()) {
            if (!type.getId().isEmpty() && ip.contains(type.getId())) {
                return type;
            }
        }
        return null;
    }

    private void saveLastServer(IModule.ServerType type) {
        try {
            File parent = STORAGE_FILE.getParentFile();
            if (parent != null) parent.mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(STORAGE_FILE))) {
                writer.write(type.name());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
