package dev.client.component.core;

import dev.client.LynxWave;
import dev.client.component.Component;
import lombok.experimental.UtilityClass;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@UtilityClass
public class Instance {
    private final ConcurrentMap<Class<? extends Component>, Component> componentInstances = new ConcurrentHashMap<>();

    public <T extends Component> T getComponent(Class<T> clazz) {
        return clazz.cast(componentInstances.computeIfAbsent(clazz, instance -> LynxWave.getInstance().getComponentManager().get(instance)));
    }
}