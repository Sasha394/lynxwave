package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.entity.effect.StatusEffects;

public class Sprint extends Module {
    public static Sprint INSTANCE;

    public static Sprint getInstance() {
        return INSTANCE;
    }

    public static int tickStop;

    private final SelectElements settings = new SelectElements("Эффекты для остановки бега", () -> true)
            .set("Слепота", "Замедление")
            .applyDefault("Слепота");

    public Sprint() {
        super("Sprint", ModuleCategory.Movement, "Автоматически спринтит за вас");
        settings.register(this);
        INSTANCE = this;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        boolean haveSlowness = mc.player.hasStatusEffect(StatusEffects.SLOWNESS);
        boolean haveBlindness = mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean cancelSlowness = haveSlowness && settings.isSelected("Замедление");
        boolean cancelBlindness = haveBlindness && settings.isSelected("Слепота");
        boolean horizontal = mc.player.horizontalCollision && !mc.player.collidedSoftly;
        boolean sneaking = mc.player.isSneaking() && !mc.player.isSwimming();

        if (tickStop > 0 || sneaking || cancelSlowness || cancelBlindness) {
            mc.player.setSprinting(false);
        } else if (!horizontal && mc.player.forwardSpeed > 0 && !mc.options.sprintKey.isPressed()) {
            mc.player.setSprinting(true);
        }

        tickStop--;
    }
}
