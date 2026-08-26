package dev.client.api.nullcry.uiClient.draggables;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.IDraggable;
import dev.client.api.nullcry.IModule;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.render.ScissorUtil;
import org.joml.Matrix3x2fStack;

public interface IHelper extends ClientApi, IModule, IDraggable {
    void onRender(RenderEvent.Draw2D event);
    default void onUpdate(UpdateEvent event) {}
    default LynxWave handlerClient() {return IModule.super.handlerClient();}
    default void onPlayerAttack(PlayerAttackEvent event) {}

    /** Перетаскиваемый элемент этого HUD-модуля (для масштабирования). null — если не поддерживается. */
    default DraggableHandler draggable() {return null;}

    /** Множитель масштаба элемента (1.0 = 100%), как в Interface -> Масштаб. */
    default float hudScale() {return 1f;}

    /**
     * Рендерит элемент с учётом его настройки «Масштаб». Масштабирование выполняется
     * вокруг якоря элемента, а размер хитбокса синхронизируется через {@link DraggableHandler#setScale(float)}.
     */
    default void renderScaled(RenderEvent.Draw2D event) {
        DraggableHandler handler = draggable();
        float scale = hudScale();
        if (handler != null) handler.setScale(scale);

        if (handler == null || Math.abs(scale - 1f) < 1.0E-4f) {
            onRender(event);
            return;
        }

        float anchorX = handler.getX();
        float anchorY = handler.getY();

        Matrix3x2fStack matrices = event.getContext().getMatrices();
        matrices.pushMatrix();
        matrices.translate(anchorX, anchorY);
        matrices.scale(scale, scale);
        matrices.translate(-anchorX, -anchorY);
        // Хитбокс scissor'ов масштабируется тем же преобразованием (см. ScissorUtil#setTransform)
        ScissorUtil.setTransform(scale, anchorX, anchorY);
        try {
            onRender(event);
        } finally {
            ScissorUtil.resetTransform();
            matrices.popMatrix();
        }
    }
}