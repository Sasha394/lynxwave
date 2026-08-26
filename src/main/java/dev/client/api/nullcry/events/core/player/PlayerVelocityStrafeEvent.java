package dev.client.api.nullcry.events.core.player;

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
public class PlayerVelocityStrafeEvent extends Event {
    final Vec3d movementInput;
    final float speed;
    final float yaw;
    Vec3d velocity;
}
