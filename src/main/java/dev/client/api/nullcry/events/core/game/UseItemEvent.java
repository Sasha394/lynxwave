package dev.client.api.nullcry.events.core.game;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

@AllArgsConstructor
@Getter
public class UseItemEvent extends Event {
    private final LivingEntity entity;
    private final ItemStack itemStack;
}