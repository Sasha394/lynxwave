package dev.client.component;

import dev.client.LynxWave;
import dev.client.component.core.client.ClientComponent;
import dev.client.component.core.client.ModuleConnectionUpdate;
import dev.client.component.core.client.ServerAutoConfigurator;
import dev.client.component.core.inventory.HandComponent;
import dev.client.component.core.inventory.ItemUseComponent;
import dev.client.component.core.sync.SyncFixComponent;

import java.util.HashMap;

public class ComponentManager extends HashMap<Class<? extends Component>, Component> {

    public void initComponents() {
        add(
                // client
                new ClientComponent(),
                new ModuleConnectionUpdate(),
                new ServerAutoConfigurator(),

                // inventory
                new HandComponent(),
                new ItemUseComponent(),

                // sync
                new SyncFixComponent()
        );

        this.values().forEach(component -> LynxWave.getInstance().getEventBus().register(component));
    }

    public void add(Component... components) {
        for (Component component : components) {
            this.put(component.getClass(), component);
        }
    }

    public <T extends Component> T get(final Class<T> clazz) {
        return this.values()
                .stream()
                .filter(component -> component.getClass() == clazz)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
}
