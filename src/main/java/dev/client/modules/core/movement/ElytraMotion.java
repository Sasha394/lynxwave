package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.MoveEvent;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.elytra.PredictUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ElytraMotion extends Module {

    private final Slider distance = new Slider("Дистанция", () -> true).set(1f, 6f, 0.1f) .defaultValue(2.5).register(this);

    public ElytraMotion() {
        super("ElytraMotion", ModuleCategory.Movement, "Останавливается когда цель из KillAura находится в зоне выбранной дистанции");
    }

    @Subscribe
    public void onMove(MoveEvent e) {
        KillAura killAura = KillAura.INSTANCE;

        if (PlayerUtil.nullCheck() || killAura.getTarget() == null) return;

        if (mc.player.isGliding()) {
            double currentDistance;

            if (killAura.getTarget().isGliding() && killAura.elytraAttackSettings.isSelected("Перегонять")) {
                Vec3d predictPos = PredictUtil.predict(killAura.getTarget(), killAura.predictDistance.getValue());
                currentDistance = mc.player.getEyePos().distanceTo(predictPos);
            } else {
                currentDistance = mc.player.distanceTo(killAura.getTarget());
            }

            if (currentDistance < distance.getValue()) {
                e.setMovement(Vec3d.ZERO);
            }
        }
    }
}