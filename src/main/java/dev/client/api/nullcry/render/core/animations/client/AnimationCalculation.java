package dev.client.api.nullcry.render.core.animations.client;

public interface AnimationCalculation {
    default double calculation(double value){
        return 0;
    }
}
