package dev.client.api.nullcry.render.core.animations.client.implement;

import dev.client.api.nullcry.render.core.animations.client.Animation;

public class SineOutAnimation extends Animation {

    @Override
    public double calculation(double value) {
        double x = Math.min(1.0, Math.max(0.0, value / ms));
        return Math.sin(x * Math.PI / 2.0);
    }
}
