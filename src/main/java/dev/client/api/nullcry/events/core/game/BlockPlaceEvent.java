package dev.client.api.nullcry.events.core.game;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

@Getter
@AllArgsConstructor
public class BlockPlaceEvent extends Event {
    public BlockPos position;
    public Block block;
    public ItemStack stack;
}