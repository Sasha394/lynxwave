package dev.client.api.nullcry.render.core.animations.nova.extended.core;

import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;

public class EaseOutSine extends Animation {

    public EaseOutSine(int ms, double endPoint) {
        super(ms, endPoint);
    }

    @Override
    protected double getEquation(double x) {
        return Math.sin(x * (Math.PI / 2));
    }

    @Override
    protected boolean correctOutput() {
        return true;
    }

}
