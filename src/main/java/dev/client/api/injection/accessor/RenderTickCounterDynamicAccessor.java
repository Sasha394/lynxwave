package dev.client.api.injection.accessor;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTickCounter.Dynamic.class)
public interface RenderTickCounterDynamicAccessor {
    @Mutable
    @Accessor("targetMillisPerTick")
    void danq$setTargetMillisPerTick(FloatUnaryOperator targetMillisPerTick);
}
