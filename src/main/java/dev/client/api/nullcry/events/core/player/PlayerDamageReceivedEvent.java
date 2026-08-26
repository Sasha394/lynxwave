package dev.client.api.nullcry.events.core.player;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PlayerDamageReceivedEvent extends Event {
    private final DamageType damageType;

    public enum DamageType {
        FALL,
        ARROW,
        ENDER_PEARL
    }
}