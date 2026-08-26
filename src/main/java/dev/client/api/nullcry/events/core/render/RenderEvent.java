package dev.client.api.nullcry.events.core.render;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

public class RenderEvent extends Event {

    @Getter
    @AllArgsConstructor
    public static class Draw3D extends RenderEvent {
        MatrixStack matrices;
        RenderTickCounter tickCounter;
    }

    @Getter
    @AllArgsConstructor
    public static class Draw2D extends RenderEvent {
        DrawContext context;
        RenderTickCounter tickCounter;
    }

    @Getter
    @AllArgsConstructor
    public static class RenderLabelsEvent<T extends Entity, S extends EntityRenderState> extends RenderEvent {
        S state;
    }
}
