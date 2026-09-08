package dev.client.api.injection;

import dev.client.api.nullcry.helper.entity.EntityRenderStateOwner;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixins implements EntityRenderStateOwner {
    @Unique
    private Entity lynx$entity;

    @Override
    public void lynx$setEntity(Entity entity) {
        this.lynx$entity = entity;
    }

    @Override
    public Entity lynx$getEntity() {
        return this.lynx$entity;
    }
}
