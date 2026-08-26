package dev.client.api.nullcry.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public class AlphaVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider delegate;
    private final float alpha;

    public AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new AlphaVertexConsumer(delegate.getBuffer(layer), alpha);
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(red, green, blue, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer color(int color) {
            int a = (color >>> 24) & 0xFF;
            int scaled = ((int) (a * alpha) << 24) | (color & 0x00FFFFFF);
            delegate.color(scaled);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            delegate.lineWidth(width);
            return this;
        }
    }
}
