package dev.client.api.nullcry.events.core.render;

import dev.client.api.nullcry.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;



@Getter
@Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoundingBoxControlEvent extends Event {
    public Box box;
    public Entity entity;
}
