package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

public class AntiAFK extends Module {

    public AntiAFK() {
        super("AntiAFK", ModuleCategory.Player, "Позволяет обходить кик за бездействие");
    }

    CheckBox funTime = new CheckBox("FunTime", () -> true).defaultValue(false).register(this);
    Input chatMessage = new Input("Чат сообщение", () -> funTime.getEnabled()).set("Что отправлять в чат?").defaultValue("/bal").register(this);
    Slider chatDelay = new Slider("Чат задержка (сек)", () -> funTime.getEnabled()).set(5, 300, 1).defaultValue(60).register(this);

    Slider actionDelay = new Slider("Задержка действий (сек)", () -> !funTime.getEnabled()).set(2, 60, 1).defaultValue(15).register(this);
    Slider actionRange = new Slider("Кол-во действий", () -> !funTime.getEnabled()).set(1, 6, 1).defaultValue(3).register(this);

    CheckBox enableJump = new CheckBox("Прыжки", () -> !funTime.getEnabled()).defaultValue(true).register(this);
    CheckBox enableSwing = new CheckBox("Махать рукой", () -> !funTime.getEnabled()).defaultValue(true).register(this);
    CheckBox enableSideMovement = new CheckBox("Движение в стороны", () -> !funTime.getEnabled()).defaultValue(true).register(this);

    private final TimerUtil timer = new TimerUtil();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (funTime.getEnabled()) {
            long delayMs = (long) (chatDelay.getValue() * 1000L);

            if (timer.isReached(delayMs)) {
                printChat(chatMessage.getValue());
                timer.reset();
            }
            return;
        }

        long delayMs = (long) (actionDelay.getValue() * 1000L);

        if (timer.isReached(delayMs)) {
            int actionChoice = ThreadLocalRandom.current().nextInt(actionRange.getValue().intValue());

            switch (actionChoice) {
                case 0 -> {
                    if (enableSwing.getEnabled()) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                case 1 -> {
                    if (enableJump.getEnabled()) {
                        mc.player.jump();
                    }
                }
                case 2 -> {
                    float newYaw = mc.player.getYaw() + ThreadLocalRandom.current().nextFloat() * 30 - 15;
                    mc.player.setYaw(newYaw);
                }
                case 3 -> {
                    dev.client.api.compat.InputCompat.setMovementForward(mc.player.input, ThreadLocalRandom.current().nextBoolean() ? 0.1F : -0.1F);
                }
                case 4 -> {
                    if (enableSideMovement.getEnabled()) {
                        dev.client.api.compat.InputCompat.setMovementSideways(mc.player.input, ThreadLocalRandom.current().nextBoolean() ? 0.1F : -0.1F);
                    }
                }
                case 5 -> {
                    float newPitch = mc.player.getPitch() + ThreadLocalRandom.current().nextFloat() * 10 - 5;
                    mc.player.setPitch(newPitch);
                }
            }

            timer.reset();
        }
    }
}
