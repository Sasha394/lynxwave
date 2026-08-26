package dev.client.api.nullcry.events.core.player;

import dev.client.api.nullcry.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeSprintEvent extends Event {
    boolean sprinting;
}
