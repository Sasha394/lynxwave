package dev.client.api.nullcry.events.core.other;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

@AllArgsConstructor
@Getter
@Setter
public class CollisionEvent extends Event {
    private BlockState state;
    private BlockPos pos;
}
