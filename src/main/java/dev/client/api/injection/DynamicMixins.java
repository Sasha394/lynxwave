package dev.client.api.injection;

import dev.client.LynxWave;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class DynamicMixins {
    @Shadow private float dynamicDeltaTicks;
    @Shadow private float tickProgress;
    @Shadow private long lastTimeMillis;
    @Final @Shadow private float tickTime;
    @Final @Shadow private FloatUnaryOperator targetMillisPerTick;

    @Inject(method = "beginRenderTick(J)I", at = @At("HEAD"), cancellable = true)
    private void onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        if (LynxWave.tickTimer == 1)
            return;

        this.dynamicDeltaTicks = ((timeMillis - this.lastTimeMillis) / this.targetMillisPerTick.apply(this.tickTime)) * LynxWave.tickTimer;
        this.lastTimeMillis = timeMillis;
        this.tickProgress += this.dynamicDeltaTicks;
        int i = (int) this.tickProgress;
        this.tickProgress -= (float) i;
        cir.setReturnValue(i);
    }
}
