package dev.client.api.nullcry.events.core.input;

import dev.client.api.nullcry.events.Event;
import lombok.*;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class MoveEvent extends Event {
    Vec3d movement;
}