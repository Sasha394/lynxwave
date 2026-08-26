package dev.client.api.nullcry.events.core.input;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class KeyBindEvent extends Event {
    int key;

    public boolean isKeyDown(int key) {
        return this.key == key;
    }
}
