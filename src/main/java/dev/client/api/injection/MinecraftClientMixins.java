package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.helper.client.window.WindowTheme;
import dev.client.api.nullcry.events.core.game.GameUpdateEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.world.WorldChangeEvent;
import dev.client.api.nullcry.events.core.world.WorldLoadEvent;
import dev.client.api.nullcry.render.core.renderers.core.BuiltBlur;
import dev.client.api.nullcry.render.core.renderers.core.BuiltLiquidGlass;
import dev.client.modules.core.misc.LockSlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixins {
    @Shadow @Final private Window window;
    private static final AtomicBoolean CONFIG_SAVED = new AtomicBoolean(false);
    @Unique
    private long lastHookTime = Util.getMeasuringTimeNano();
    @Unique
    private int accumulatedCalls = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci) {
        WindowTheme.applyOnce();
        EventManager.call(new TickEvent());
    }

    @Inject(method = "setWorld", at = @At("TAIL"))
    private void onSetWorldTail(ClientWorld world, CallbackInfo ci) {
        if (world != null) {
            EventManager.call(new WorldLoadEvent());
        }
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void onSetWorldHead(ClientWorld newWorld, CallbackInfo ci) {
        EventManager.call(new WorldChangeEvent());
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        BuiltBlur.resetCaptureState();
        BuiltLiquidGlass.resetCaptureState();
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void onHandleInputEvents(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.currentScreen != null) return;

        LockSlot lockSlot = LockSlot.INSTANCE;
        KeyBinding dropKey = client.options.dropKey;

        while (dropKey.wasPressed()) {
            int currentSlot = client.player.getInventory().getSelectedSlot();
            boolean ctrl = dev.client.api.compat.InputCompat.isControlDown();
            boolean protectedNow = lockSlot.isSlotProtected(currentSlot);

            boolean allowDrop = !lockSlot.isEnabled()
                    || (ctrl && lockSlot.pressedCtrl.getEnabled())
                    || !protectedNow;

            if (allowDrop) {
                if (!client.player.isSpectator() && client.player.dropSelectedItem(ctrl)) {
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }

        }
    }

    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(boolean tick, CallbackInfo ci) {
        var timeNano = Util.getMeasuringTimeNano();
        var deltaTime = timeNano - lastHookTime;
        accumulatedCalls += (int) (deltaTime / 4_166_666L);
        lastHookTime += (long) accumulatedCalls * 4_166_666L;

        for (accumulatedCalls = Math.min(accumulatedCalls, 240); accumulatedCalls > 0; --accumulatedCalls) EventManager.call(new GameUpdateEvent());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        saveOnce();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        saveOnce();
    }

    @Inject(method = "printCrashReport(Lnet/minecraft/util/crash/CrashReport;)V", at = @At("HEAD"))
    private void onPrintCrashReportInst(CrashReport report, CallbackInfo ci) {
        saveOnce();
    }

    @Inject(method = "printCrashReport(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;Lnet/minecraft/util/crash/CrashReport;)V", at = @At("HEAD"))
    private static void onPrintCrashReportStaticH(@Nullable MinecraftClient client, java.io.File runDir, CrashReport report, CallbackInfo ci) {
        try {
            if (client != null) {
                LynxWave.getInstance().getConfigurationManager().saveCurrentConfigSafely();
            }
        } catch (Throwable ignored) {}
    }

    @Unique
    private void saveOnce() {
        if (CONFIG_SAVED.compareAndSet(false, true)) {
            try {
                if (LynxWave.getInstance() != null) {
                    LynxWave.getInstance().getConfigurationManager().saveCurrentConfigSafely();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
