package dev.client.api.nullcry.events.core.render;

import dev.client.api.nullcry.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CameraPositionEvent extends Event {
    Vec3d pos;
}
