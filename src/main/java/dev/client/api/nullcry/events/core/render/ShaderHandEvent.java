package dev.client.api.nullcry.events.core.render;

import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gl.Framebuffer;

@Getter
@AllArgsConstructor
public class ShaderHandEvent extends Event {
    private final Framebuffer beforeHandFbo;
    private final Framebuffer withHandFbo;
}
