package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Random;

public class AirJump extends Module {

    public AirJump() {
        super("AirJump", ModuleCategory.Movement, "При зажатии пробела поднмает вас высоко");
    }

    ModeElement type = new ModeElement("Режим", () -> true).set("Default", "Matrix").defaultValue("Default").register(this);
    Slider matrixSpeed = new Slider("Скорость", () -> type.isSelected("Matrix")).set(0.05f, 2.0f, 0.05f).defaultValue(0.15f).register(this);

    private final TimerUtil timerUtil = new TimerUtil();
    public final Random random = new Random();
    double lastVerticalSpeed = 0.0;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (FreeCamera.INSTANCE.isEnabled()) return;

        if (mc.player == null || mc.player.isOnGround() || !mc.options.jumpKey.isPressed()) return;

        switch (type.getValue()) {
            case "Default":
                handleDefaultMode(mc.player);
                break;

            case "Matrix":
                handleMatrixMode(mc.player);
                break;
        }
    }

    private void handleDefaultMode(ClientPlayerEntity player) {
        player.setVelocity(player.getVelocity().x, 0.42, player.getVelocity().z);
    }

    private void handleMatrixMode(ClientPlayerEntity player) {
        double selectedSpeed = matrixSpeed.getValue();

        if (timerUtil.isReached(200)) {
            double randomSpeed = randomizeWithinRange(selectedSpeed - 0.02, selectedSpeed + 0.02);
            player.setVelocity(player.getVelocity().x, randomSpeed, player.getVelocity().z);
            lastVerticalSpeed = randomSpeed;
            timerUtil.reset();
        }
    }

    private double randomizeWithinRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
}
