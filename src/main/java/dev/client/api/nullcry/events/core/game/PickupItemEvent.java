package dev.client.api.nullcry.events.core.game;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

@AllArgsConstructor
@Getter
public class PickupItemEvent extends Event {
    private final ItemStack itemStack;
    private final LivingEntity livingEntity;
    private final ItemEntity itemEntity;
}
