package dev.client.api.nullcry.events.core.player;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.Entity;

@AllArgsConstructor
@Getter
public class PlayerAttackEvent extends Event {
    Entity entity;
}
