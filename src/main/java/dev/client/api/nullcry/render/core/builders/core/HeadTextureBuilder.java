package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltHeadTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.AbstractTexture;

public final class HeadTextureBuilder extends AbstractBuilder<BuiltHeadTexture> {
    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;
    private float u, v;
    private float texWidth, texHeight;
    private GpuTextureView textureView;
    private GpuSampler sampler;

    public HeadTextureBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public HeadTextureBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public HeadTextureBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public HeadTextureBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public HeadTextureBuilder texture(float u, float v, float texWidth, float texHeight, AbstractTexture texture) {
        this.u = u;
        this.v = v;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.textureView = texture != null ? texture.getGlTextureView() : null;
        this.sampler = texture != null ? texture.getSampler() : null;
        return this;
    }

    public HeadTextureBuilder texture(float u, float v, float texWidth, float texHeight, int textureId) {
        this.u = u;
        this.v = v;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.textureView = null;
        this.sampler = null;
        return this;
    }

    @Override
    protected BuiltHeadTexture _build() {
        return new BuiltHeadTexture(
                this.size,
                this.radius,
                this.color,
                this.smoothness,
                this.u, this.v,
                this.texWidth, this.texHeight,
                this.textureView,
                this.sampler
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.smoothness = 1.0f;
        this.u = 0.0f;
        this.v = 0.0f;
        this.texWidth = 0.0f;
        this.texHeight = 0.0f;
        this.textureView = null;
        this.sampler = null;
    }
}
