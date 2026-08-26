package dev.client.api.nullcry.events.core.other;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class NoPushEvent extends Event {
    CollisionEnum collisionEnum;

    public enum CollisionEnum {
        Players,
        Blocks,
        Fluids
    }
}