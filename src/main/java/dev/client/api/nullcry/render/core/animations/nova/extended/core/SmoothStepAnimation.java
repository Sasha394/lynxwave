package dev.client.api.nullcry.render.core.animations.nova.extended.core;

import dev.client.api.nullcry.render.core.animations.nova.extended.Animation;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;

public class SmoothStepAnimation extends Animation {

    public SmoothStepAnimation(int ms, double endPoint) {
        super(ms, endPoint);
    }

    public SmoothStepAnimation(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }

    protected double getEquation(double x) {
        double x1 = x / (double) duration;
        return -2 * Math.pow(x1, 3) + (3 * Math.pow(x1, 2));
    }

}
