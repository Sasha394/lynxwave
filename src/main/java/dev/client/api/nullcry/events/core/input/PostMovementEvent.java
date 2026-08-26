package dev.client.api.nullcry.events.core.input;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PostMovementEvent extends Event {
    private double horizontalMove;
}
