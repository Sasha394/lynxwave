package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltShadow;

public class ShadowBuilder extends AbstractBuilder<BuiltShadow> {
    SizeState size;
    QuadColorState color;
    QuadRadiusState radius;
    float shadowRadius;
    float softness;

    public ShadowBuilder size(SizeState size) {
        this.size = size;

        return this;
    }

    public ShadowBuilder color(QuadColorState color) {
        this.color = color;

        return this;
    }

    public ShadowBuilder radius(QuadRadiusState radiusState) {
        this.radius = radiusState;

        return this;
    }

    public ShadowBuilder shadow(float radius) {
        this.shadowRadius = radius;

        return this;
    }

    public ShadowBuilder softness(float softness) {
        this.softness = softness;

        return this;
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.color = QuadColorState.TRANSPARENT;
        this.radius = QuadRadiusState.NO_ROUND;
        this.softness = 0.0f;
        this.shadowRadius = 0.0f;
    }

    @Override
    protected BuiltShadow _build() {
        return new BuiltShadow(
            size,
            color,
            radius,
            softness,
            shadowRadius
        );
    }
}
