package dev.client.api.nullcry.events.core.game;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RotationUpdateEvent extends Event {
    byte type;
}

