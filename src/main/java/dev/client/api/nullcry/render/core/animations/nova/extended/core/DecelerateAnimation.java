package dev.client.api.nullcry.render.core.animations.nova.extended.core;

import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;

public class DecelerateAnimation extends Animation {

    public DecelerateAnimation(int ms, double endPoint) {
        super(ms, endPoint);
    }

    protected double getEquation(double x) {
        return 1 - ((x - 1) * (x - 1));
    }
}
