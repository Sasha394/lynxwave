package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class HitBoxes extends Module {

    public HitBoxes() {
        super("Hitboxes", ModuleCategory.Combat, "Увеличивает хитбокс цели");
    }

    CheckBox visible = new CheckBox("Видимость", () -> true).defaultValue(false).register(this);
    Slider size = new Slider("Размер", () -> true).set(0.0f, 3.0f, 0.1f).defaultValue(0.2f).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!visible.getEnabled()) return;

        float sizeMultiplier = this.size.getValue() * 2.5F;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isNotValid(player)) {
                player.setBoundingBox(calculateBoundingBox(player, sizeMultiplier));
            }
        }
    }

    private Box calculateBoundingBox(Entity entity, float size) {
        double minX = entity.getX() - size;
        double minY = entity.getBoundingBox().minY;
        double minZ = entity.getZ() - size;
        double maxX = entity.getX() + size;
        double maxY = entity.getBoundingBox().maxY;
        double maxZ = entity.getZ() + size;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean isNotValid(PlayerEntity player) {
        return player == mc.player || !player.isAlive();
    }
}