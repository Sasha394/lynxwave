package dev.client.api.nullcry.events.core.game;

import dev.client.api.nullcry.events.Event;
import net.minecraft.entity.LivingEntity;

public class JumpEvent extends Event {

    private final LivingEntity entity;

    public JumpEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
