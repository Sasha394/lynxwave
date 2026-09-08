package dev.client.api.nullcry.helper.entity;

import net.minecraft.entity.Entity;

/**
 * Обратная ссылка со снимка рендера на сущность: в 1.21.6+ состояние собирается
 * отдельным проходом и сущность внутри себя не хранит.
 * <p>
 * Лежит вне миксин-пакета намеренно: Mixin запрещает грузить напрямую классы
 * из {@code dev.client.api.injection.*}, которые не зарегистрированы как миксины.
 */
public interface EntityRenderStateOwner {
    void lynx$setEntity(Entity entity);

    Entity lynx$getEntity();
}
