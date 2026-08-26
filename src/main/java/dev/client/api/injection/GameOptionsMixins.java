package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.injection.accessor.ISimpleOptionAccessor;
import dev.client.modules.core.render.FullBright;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.PrintWriter;
import java.util.function.Consumer;

@Mixin(GameOptions.class)
public class GameOptionsMixins implements LynxWave.DelayHolder {
    @Unique private double lumora$throwInventoryScreenDelay = 0.0D;
    @Unique private double lumora$chestScreenDelay = 0.0D;

    @Override public double lumora$getThrowInventoryScreenDelay() {return lumora$throwInventoryScreenDelay;}
    @Override public void lumora$setThrowInventoryScreenDelay(double v) {lumora$throwInventoryScreenDelay = Math.max(0.0D, v);}
    @Override public double lumora$getChestScreenDelay() {return lumora$chestScreenDelay;}
    @Override public void lumora$setChestScreenDelay(double v) {lumora$chestScreenDelay = Math.max(0.0D, v);}

    // FullBright
    @Shadow @Final private SimpleOption<Double> gamma;
    @Unique private static boolean fb$applied = false;
    @Unique private static double fb$savedUserGamma = 0.5D;

    @Unique
    private static boolean fb$differs(double a, double b) {
        return Math.abs(a - b) > 1.0e-6;
    }

    @Inject(method = "getGamma", at = @At("RETURN"))
    private void lumora$fullbrightTick(CallbackInfoReturnable<SimpleOption<Double>> cir) {
        boolean enabled = false;
        boolean gammaMode = false;
        double targetGamma = 1.0;

        try {
            var fb = FullBright.INSTANCE;
            enabled = fb.isEnabled();
            gammaMode = fb.mode.isSelected("Гамма");
            targetGamma = fb.value.getValue();
        } catch (Throwable ignored) { }

        ISimpleOptionAccessor gammaAccessor = (ISimpleOptionAccessor) (Object) gamma;
        double current = gamma.getValue();
        double storedValue = gammaAccessor.lumora$getRawValue() instanceof Double rawStored ? rawStored : current;

        if (enabled && gammaMode) {
            if (!fb$applied) {
                fb$savedUserGamma = current;
                fb$applied = true;
            }
            double raw = targetGamma;
            double safe = Math.max(0.0, raw);
            if (fb$differs(storedValue, safe)) {
                gammaAccessor.lumora$setRawValue(safe);
                Consumer<Object> callback = gammaAccessor.lumora$getChangeCallback();
                if (callback != null) {
                    callback.accept(safe);
                }
            }
        } else if (fb$applied) {
            if (fb$differs(storedValue, fb$savedUserGamma)) {
                gamma.setValue(fb$savedUserGamma);
            }
            fb$applied = false;
        }
    }

    @Inject(method = "accept", at = @At("TAIL"))
    private void onAccept(GameOptions.Visitor visitor, CallbackInfo ci) {
        lumora$throwInventoryScreenDelay = visitor.visitFloat("throwInventoryScreenDelay", (float) lumora$throwInventoryScreenDelay);
        lumora$chestScreenDelay = visitor.visitFloat("chestScreenDelay", (float) lumora$chestScreenDelay);
    }

    @Inject(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;accept(Lnet/minecraft/client/option/GameOptions$Visitor;)V", shift = At.Shift.AFTER), locals = org.spongepowered.asm.mixin.injection.callback.LocalCapture.CAPTURE_FAILSOFT)
    private void onWrite(CallbackInfo ci, PrintWriter printWriter) {
        printWriter.print("throwInventoryScreenDelay:");
        printWriter.println(String.format(java.util.Locale.ROOT, "%.3f", lumora$throwInventoryScreenDelay));

        printWriter.print("chestScreenDelay:");
        printWriter.println(String.format(java.util.Locale.ROOT, "%.3f", lumora$chestScreenDelay));
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void lumora$beforeWrite(CallbackInfo ci) {
        if (fb$applied) {
            ISimpleOptionAccessor accessor = (ISimpleOptionAccessor) (Object) gamma;
            double stored = accessor.lumora$getRawValue() instanceof Double value ? value : gamma.getValue();
            if (fb$differs(stored, fb$savedUserGamma)) {
                gamma.setValue(fb$savedUserGamma);
            }
        }
    }
}
