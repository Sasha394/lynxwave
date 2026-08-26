package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltGlow;

public class GlowBuilder extends AbstractBuilder<BuiltGlow> {
    private SizeState size;
    private QuadColorState color;
    private QuadRadiusState radius;
    private float glowRadius;
    private float softness;

    public GlowBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public GlowBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public GlowBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public GlowBuilder glowRadius(float glowRadius) {
        this.glowRadius = glowRadius;
        return this;
    }

    public GlowBuilder softness(float softness) {
        this.softness = softness;
        return this;
    }

    @Override
    protected BuiltGlow _build() {
        return new BuiltGlow(
            this.size,
            this.color,
            this.radius,
            this.softness,
            this.glowRadius
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.color = QuadColorState.TRANSPARENT;
        this.radius = QuadRadiusState.NO_ROUND;
        this.glowRadius = 0.0f;
        this.softness = 0.0f;
    }
}
