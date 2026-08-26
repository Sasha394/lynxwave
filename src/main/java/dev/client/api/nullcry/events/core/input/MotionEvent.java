package dev.client.api.nullcry.events.core.input;

import dev.client.api.nullcry.events.Event;
import lombok.*;
import lombok.experimental.FieldDefaults;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Setter
public class MotionEvent extends Event {
    public static float LAST_YAW, LAST_PITCH;

    double x, y, z;
    float yaw, pitch;
    boolean onGround;

    public MotionEvent(float yaw, float pitch) {
        this(0, 0, 0, yaw, pitch, false);
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        LAST_YAW = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        LAST_PITCH = pitch;
    }
}