package dev.client.api.nullcry.events.core.world;

import dev.client.api.nullcry.events.Event;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class AspectRatioEvent extends Event {
    float ratio;
}
