package dev.client.api.nullcry.events.core.world;

import dev.client.api.nullcry.events.Event;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FogEvent extends Event {
    float distance;
    int color;
}