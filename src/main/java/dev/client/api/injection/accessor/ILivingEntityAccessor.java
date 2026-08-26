package dev.client.api.injection.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface ILivingEntityAccessor {
    @Accessor("jumpingCooldown") int getLastJumpCooldown();
    @Accessor("jumpingCooldown") void setLastJumpCooldown(int val);
}