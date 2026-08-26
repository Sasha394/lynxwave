package dev.client.api.nullcry.helper.player;

import dev.client.api.injection.accessor.RenderTickCounterDynamicAccessor;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

@UtilityClass
public class TimerManager {
    private final float NORMAL_TICK_MILLIS = 50.0F;

    public void setTimer(float speed) {
        float clampedSpeed = MathHelper.clamp(speed, 0.05F, 20.0F);
        RenderTickCounter renderTickCounter = MinecraftClient.getInstance().getRenderTickCounter();

        if (renderTickCounter instanceof RenderTickCounter.Dynamic dynamic) {
            ((RenderTickCounterDynamicAccessor) dynamic).danq$setTargetMillisPerTick(ignored -> NORMAL_TICK_MILLIS / clampedSpeed);
        }
    }

    public void reset() {
        setTimer(1.0F);
    }
}
