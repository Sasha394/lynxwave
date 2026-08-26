package dev.client.api.nullcry.events.core.input;

import dev.client.api.nullcry.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
public class MovementEvent extends Event {
    private Vec3d from, to, motion;
    private boolean toGround;
    private Box aabbFrom;
    private boolean ignoreHorizontal, ignoreVertical, collidedHorizontal, collidedVertical;

    public MovementEvent(Vec3d from, Vec3d to, Vec3d motion, boolean toGround, boolean isCollidedHorizontal, boolean isCollidedVertical, Box aabbFrom) {
        this.from = from;
        this.to = to;
        this.motion = motion;
        this.toGround = toGround;
        this.collidedHorizontal = isCollidedHorizontal;
        this.collidedVertical = isCollidedVertical;
        this.aabbFrom = aabbFrom;
    }
}
