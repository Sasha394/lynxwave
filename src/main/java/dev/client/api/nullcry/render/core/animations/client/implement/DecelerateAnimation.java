package dev.client.api.nullcry.render.core.animations.client.implement;


import dev.client.api.nullcry.render.core.animations.client.Animation;

public class DecelerateAnimation extends Animation {

    @Override
    public double calculation(double value) {
        double x = value / ms;
        return 1 - (x - 1) * (x - 1);
    }
}
