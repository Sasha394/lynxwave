package dev.client.modules.core.render.particles;

import dev.client.api.nullcry.render.core.LynxRenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class WorldParticleLayers {

    private WorldParticleLayers() {}

    public static final RenderLayer POSITION_COLOR_QUADS = RenderLayer.of(
            "lynxwave_world_particles_quads",
            RenderSetup.builder(LynxRenderPipelines.WORLD_PARTICLE_QUADS)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .expectedBufferSize(2048)
                    .build());

    public static final RenderLayer POSITION_COLOR_DEBUG_LINES = RenderLayer.of(
            "lynxwave_world_particles_lines",
            RenderSetup.builder(LynxRenderPipelines.WORLD_PARTICLE_LINES)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .expectedBufferSize(2048)
                    .build());

    // RenderLayer.of создаёт буфер на каждый вызов — кэшируем по текстуре, чтобы не
    // плодить слои каждый кадр (в render() слой запрашивается для каждой частицы).
    private static final Map<Identifier, RenderLayer> GLOW_LAYER_CACHE = new ConcurrentHashMap<>();

    public static final Function<Identifier, RenderLayer> WORLD_PARTICLES_GLOW = texture ->
            GLOW_LAYER_CACHE.computeIfAbsent(texture, WorldParticleLayers::createGlowLayer);

    private static RenderLayer createGlowLayer(Identifier texture) {
        return RenderLayer.of(
                "lynxwave_world_particles_glow",
                RenderSetup.builder(LynxRenderPipelines.WORLD_PARTICLE_GLOW)
                        .texture("Sampler0", texture)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                        .expectedBufferSize(2048)
                        .build());
    }
}
