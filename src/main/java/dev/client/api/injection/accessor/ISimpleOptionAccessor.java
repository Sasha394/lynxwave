package dev.client.api.injection.accessor;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

@Mixin(SimpleOption.class)
public interface ISimpleOptionAccessor {
    @Accessor("value")
    Object lumora$getRawValue();

    @Accessor("value")
    void lumora$setRawValue(Object value);

    @Accessor("changeCallback")
    Consumer<Object> lumora$getChangeCallback();
}
