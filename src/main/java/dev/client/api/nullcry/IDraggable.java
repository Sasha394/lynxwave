package dev.client.api.nullcry;

import dev.client.api.nullcry.cmdHelper.managers.dragHandler.DraggableManager;
import dev.client.api.nullcry.helper.other.DraggableHandler;

public interface IDraggable {
    default DraggableHandler addDraggable(String name, float x, float y) {
        DraggableHandler handler = new DraggableHandler(name, x, y);
        DraggableManager.draggable.put(name, handler);
        return handler;
    }
}
