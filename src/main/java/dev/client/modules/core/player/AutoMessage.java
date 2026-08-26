package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

@SuppressWarnings("unused")
public class AutoMessage extends Module {

    public AutoMessage() {
        super("AutoMessage", ModuleCategory.Player, "Автоматическое сообщение в чат");
    }

    private final ModeElement mode = new ModeElement("Отправлять", () -> true)
            .set("После убийства", "Во время таргета", "По задержке")
            .defaultValue("После убийства")
            .register(this);

    private final Slider timer = new Slider("Задержка", () -> mode.isSelected("По задержке") || mode.isSelected("Во время таргета"))
            .set(0f, 35000f, 1000f)
            .defaultValue(5000f)
            .register(this);

    private final Input text = new Input("Сообщение", () -> true)
            .set("Привет %target%!")
            .register(this);

    private final TimerUtil delayTimer = new TimerUtil();
    private LivingEntity lastTarget;
    private boolean waitingForDeath;

    @Subscribe
    public void onAttack(PlayerAttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mode.isSelected("После убийства")) return;

        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            lastTarget = living;
            waitingForDeath = true;
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.isSelected("По задержке")) {
            if (delayTimer.isReached(timer.getValue().longValue())) {
                sendMessage(replaceTarget(text.getValue(), null));
                delayTimer.reset();
            }
            return;
        }

        if (mode.isSelected("Во время таргета")) {
            KillAura aura = KillAura.INSTANCE;
            if (aura != null && aura.isEnabled() && aura.getTarget() != null
                    && delayTimer.isReached(timer.getValue().longValue())) {
                sendMessage(replaceTarget(text.getValue(), aura.getTarget()));
                delayTimer.reset();
            }
            return;
        }

        if (mode.isSelected("После убийства") && waitingForDeath && lastTarget != null) {
            boolean dead = !lastTarget.isAlive() || lastTarget.getHealth() <= 0.0f;
            boolean unloaded = mc.world.getEntityById(lastTarget.getId()) == null;

            if (dead || unloaded) {
                sendMessage(replaceTarget(text.getValue(), lastTarget));
                waitingForDeath = false;
                lastTarget = null;
                delayTimer.reset();
            }
        }
    }

    private void sendMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        printChat(msg);
    }

    private String replaceTarget(String msg, LivingEntity target) {
        return msg.replace("%target%", target != null ? target.getName().getString() : "хряк");
    }
}
