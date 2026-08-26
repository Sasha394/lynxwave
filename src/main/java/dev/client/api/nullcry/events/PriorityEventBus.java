package dev.client.api.nullcry.events;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.annotation.ListenerPriority;
import dev.client.api.nullcry.events.types.Priority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replacement for Guava's {@link com.google.common.eventbus.EventBus} that understands listener priorities while
 * keeping the familiar {@link Subscribe} annotation.
 */
public final class PriorityEventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<ListenerMethod>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, List<ListenerMethod>> registrations = new ConcurrentHashMap<>();

    public void register(Object listener) {
        if (listener == null) {
            return;
        }

        if (registrations.containsKey(listener)) {
            unregister(listener);
        }

        List<ListenerMethod> discovered = discoverListenerMethods(listener);
        if (discovered.isEmpty()) {
            return;
        }

        registrations.computeIfAbsent(listener, key -> new ArrayList<>()).addAll(discovered);
        for (ListenerMethod data : discovered) {
            listeners.computeIfAbsent(data.eventType(), key -> new CopyOnWriteArrayList<>()).add(data);
            resort(data.eventType());
        }
    }

    public void unregister(Object listener) {
        if (listener == null) {
            return;
        }

        List<ListenerMethod> registered = registrations.remove(listener);
        if (registered == null) {
            return;
        }

        for (ListenerMethod data : registered) {
            CopyOnWriteArrayList<ListenerMethod> methods = listeners.get(data.eventType());
            if (methods != null) {
                methods.removeIf(method -> method.matches(data));
                if (methods.isEmpty()) {
                    listeners.remove(data.eventType());
                }
            }
        }
    }

    public void post(Object event) {
        if (event == null) {
            return;
        }

        for (Class<?> eventType : flattenHierarchy(event.getClass())) {
            CopyOnWriteArrayList<ListenerMethod> methods = listeners.get(eventType);
            if (methods == null || methods.isEmpty()) {
                continue;
            }

            for (ListenerMethod method : methods) {
                invoke(method, event);
            }
        }
    }

    private void resort(Class<?> eventType) {
        CopyOnWriteArrayList<ListenerMethod> methods = listeners.get(eventType);
        if (methods == null || methods.size() < 2) {
            return;
        }
        List<ListenerMethod> sorted = new ArrayList<>(methods);
        sorted.sort(Comparator.comparingInt(ListenerMethod::priority));
        listeners.put(eventType, new CopyOnWriteArrayList<>(sorted));
    }

    private List<ListenerMethod> discoverListenerMethods(Object listener) {
        List<ListenerMethod> result = new ArrayList<>();
        Class<?> current = listener.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!isSubscriberMethod(method)) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                Class<?> eventType = parameterTypes[0];
                byte priority = resolvePriority(method);
                if (!method.canAccess(listener)) {
                    method.setAccessible(true);
                }
                result.add(new ListenerMethod(listener, method, eventType, priority));
            }
            current = current.getSuperclass();
        }
        return result;
    }

    private boolean isSubscriberMethod(Method method) {
        if (!method.isAnnotationPresent(Subscribe.class)) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 1 && Event.class.isAssignableFrom(parameterTypes[0]);
    }

    private byte resolvePriority(Method method) {
        ListenerPriority annotation = method.getAnnotation(ListenerPriority.class);
        byte value = annotation != null ? annotation.value() : Priority.MEDIUM;
        for (byte priority : Priority.VALUE_ARRAY) {
            if (priority == value) {
                return value;
            }
        }
        return Priority.MEDIUM;
    }

    private Set<Class<?>> flattenHierarchy(Class<?> clazz) {
        LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
        Queue<Class<?>> queue = new ArrayDeque<>();
        queue.add(clazz);
        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (type == null || !types.add(type)) {
                continue;
            }
            Class<?> superclass = type.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                queue.add(iface);
            }
        }
        return types;
    }

    private void invoke(ListenerMethod data, Object event) {
        try {
            data.method().invoke(data.target(), event);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to dispatch event " + event + " to " + data.method(), e);
        }
    }

    private record ListenerMethod(Object target, Method method, Class<?> eventType, byte priority) {
        boolean matches(ListenerMethod other) {
            return target == other.target && method.equals(other.method);
        }
    }
}
