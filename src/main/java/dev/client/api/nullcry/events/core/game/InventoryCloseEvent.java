package dev.client.api.nullcry.events.core.game;


import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.screen.Screen;

@Getter
@AllArgsConstructor
public class InventoryCloseEvent extends Event {
    private Screen screen;
    public int windowId;
}
