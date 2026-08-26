package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltBlur;

public final class BlurBuilder extends AbstractBuilder<BuiltBlur> {
    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float Smoothness;
    private float blurRadius;

    public BlurBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public BlurBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public BlurBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public BlurBuilder Smoothness(float Smoothness) {
        this.Smoothness = Smoothness;
        return this;
    }

    public BlurBuilder blurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
        return this;
    }

    public BlurBuilder alpha(float a) {
        int ai = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        this.color = QuadColorState.whiteWithAlpha(ai);
        return this;
    }

    @Override
    protected BuiltBlur _build() {
        return new BuiltBlur(
            this.size,
            this.radius,
            this.color,
            this.Smoothness,
            this.blurRadius
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.Smoothness = 1.0f;
        this.blurRadius = 0.0f;
    }

}