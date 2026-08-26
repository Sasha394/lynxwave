package dev.client.api.nullcry.events;

import dev.client.LynxWave;

public class EventManager {

    public static void register(Object listener) {
        LynxWave.getInstance().getEventBus().register(listener);
    }

    public static void unregister(Object listener) {
        LynxWave.getInstance().getEventBus().unregister(listener);
    }

    public static void call(Event event) {
        LynxWave.getInstance().getEventBus().post(event);
    }

    public static void cancelled(Event event) {
        event.cancelled();
    }

    public static boolean isCancelled(Event event) {
        return event.isCancelled();
    }

}
