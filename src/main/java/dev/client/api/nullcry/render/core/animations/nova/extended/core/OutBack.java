package dev.client.api.nullcry.render.core.animations.nova.extended.core;

import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;

// Порт rich.util.animations.OutBack (overshoot-эластичность) под базу LynxWave Animation.
public class OutBack extends Animation {

    public OutBack(int ms, double endPoint) {
        super(ms, endPoint);
    }

    @Override
    protected double getEquation(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3) + c1 * Math.pow(x - 1.0, 2);
    }
}
