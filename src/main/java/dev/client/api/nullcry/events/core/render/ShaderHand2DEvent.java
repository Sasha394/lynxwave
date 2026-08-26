package dev.client.api.nullcry.events.core.render;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

@Getter
@AllArgsConstructor
public class ShaderHand2DEvent extends Event {
    private final DrawContext context;
    private final RenderTickCounter tickCounter;
}
