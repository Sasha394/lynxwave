package dev.client.api.nullcry.events.core.player;

import dev.client.api.nullcry.events.Event;
import lombok.Getter;

@Getter
public class PlayerJumpEvent extends Event {
    private final boolean pre;

    public PlayerJumpEvent(boolean pre) {
        this.pre = pre;
    }
}
