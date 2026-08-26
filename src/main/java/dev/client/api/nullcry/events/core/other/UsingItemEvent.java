package dev.client.api.nullcry.events.core.other;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends Event {
    byte type;
}
