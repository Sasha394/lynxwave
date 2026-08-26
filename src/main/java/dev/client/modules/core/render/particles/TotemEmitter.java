package dev.client.modules.core.render.particles;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class TotemEmitter {
    private final @Nullable Entity entity;
    private int ticks;
    private final int duration;

    public TotemEmitter(@Nullable Entity entity, int duration) {
        this.entity = entity;
        this.duration = duration;
    }

    public void tick() {
        ticks++;
    }

    public boolean isAlive() {
        return entity != null && !entity.isRemoved() && ticks < duration;
    }

    public @Nullable Entity getEntity() {
        return entity;
    }

    public float getProgress() {
        return duration <= 0 ? 1f : ticks / (float) duration;
    }
}
