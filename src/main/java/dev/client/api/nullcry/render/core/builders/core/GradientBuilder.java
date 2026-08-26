package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltGradient;

public final class GradientBuilder extends AbstractBuilder<BuiltGradient> {
    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState colors;
    private float smoothness;

    public GradientBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public GradientBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public GradientBuilder colors(QuadColorState colors) {
        this.colors = colors;
        return this;
    }

    public GradientBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    @Override
    protected BuiltGradient _build() {
        return new BuiltGradient(
                this.size,
                this.radius,
                this.colors,
                this.smoothness
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.colors = QuadColorState.TRANSPARENT;
        this.smoothness = 1.0f;
    }
}
