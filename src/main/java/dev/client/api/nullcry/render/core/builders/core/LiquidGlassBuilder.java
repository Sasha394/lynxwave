package dev.client.api.nullcry.render.core.builders.core;

import dev.client.api.nullcry.render.core.builders.AbstractBuilder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.renderers.core.BuiltLiquidGlass;

public final class LiquidGlassBuilder extends AbstractBuilder<BuiltLiquidGlass> {
    private SizeState size;
    private QuadRadiusState radius;
    private QuadColorState color;
    private float smoothness;
    private float cornerSmoothness;
    private float globalAlpha;
    private float fresnelPower;
    private int fresnelColor;
    private float baseAlpha;
    private boolean fresnelInvert;
    private float fresnelMix;
    private float distortStrength;

    public LiquidGlassBuilder size(SizeState size) {
        this.size = size;
        return this;
    }

    public LiquidGlassBuilder radius(QuadRadiusState radius) {
        this.radius = radius;
        return this;
    }

    public LiquidGlassBuilder color(QuadColorState color) {
        this.color = color;
        return this;
    }

    public LiquidGlassBuilder smoothness(float smoothness) {
        this.smoothness = smoothness;
        return this;
    }

    public LiquidGlassBuilder cornerSmoothness(float cornerSmoothness) {
        this.cornerSmoothness = cornerSmoothness;
        return this;
    }

    public LiquidGlassBuilder globalAlpha(float globalAlpha) {
        this.globalAlpha = globalAlpha;
        return this;
    }

    public LiquidGlassBuilder fresnelPower(float fresnelPower) {
        this.fresnelPower = fresnelPower;
        return this;
    }

    public LiquidGlassBuilder fresnelColor(int fresnelColor) {
        this.fresnelColor = fresnelColor;
        return this;
    }

    public LiquidGlassBuilder baseAlpha(float baseAlpha) {
        this.baseAlpha = baseAlpha;
        return this;
    }

    public LiquidGlassBuilder fresnelInvert(boolean fresnelInvert) {
        this.fresnelInvert = fresnelInvert;
        return this;
    }

    public LiquidGlassBuilder fresnelMix(float fresnelMix) {
        this.fresnelMix = fresnelMix;
        return this;
    }

    public LiquidGlassBuilder distortStrength(float distortStrength) {
        this.distortStrength = distortStrength;
        return this;
    }

    @Override
    protected BuiltLiquidGlass _build() {
        return new BuiltLiquidGlass(
                this.size,
                this.radius,
                this.color,
                this.smoothness,
                this.cornerSmoothness,
                this.globalAlpha,
                this.fresnelPower,
                this.fresnelColor,
                this.baseAlpha,
                this.fresnelInvert,
                this.fresnelMix,
                this.distortStrength
        );
    }

    @Override
    protected void reset() {
        this.size = SizeState.NONE;
        this.radius = QuadRadiusState.NO_ROUND;
        this.color = QuadColorState.WHITE;
        this.smoothness = 0.5f;
        this.cornerSmoothness = 2.0f;
        this.globalAlpha = 1.0f;
        this.fresnelPower = 50.0f;
        this.fresnelColor = 0xFFFFFFFF;
        this.baseAlpha = 1.0f;
        this.fresnelInvert = true;
        this.fresnelMix = 0.0f;
        this.distortStrength = 0.08f;
    }
}
