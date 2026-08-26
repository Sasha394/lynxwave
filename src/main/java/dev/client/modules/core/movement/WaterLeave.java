package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class WaterLeave extends Module {

    public WaterLeave() {
        super("WaterLeave", ModuleCategory.Movement, "Высоко прыгает в воде");
    }

    SelectElements mode = new SelectElements("Работать на", () -> true).set("Песок душ", "Магму").defaultValue("Песок душ").register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        BlockPos belowPlayer = mc.player.getBlockPos().down();

        if (mc.player.isTouchingWater()) {
            if (mode.isSelected("Песок душ") && mc.world.getBlockState(belowPlayer).isOf(Blocks.SOUL_SAND)) {
                mc.player.jump();
                mc.player.setVelocity(mc.player.getVelocity().x, 1.6, mc.player.getVelocity().z);
            } else if (mode.isSelected("Магму") && mc.world.getBlockState(belowPlayer).isOf(Blocks.MAGMA_BLOCK)) {
                mc.player.jump();
                mc.player.setVelocity(mc.player.getVelocity().x, 1.72, mc.player.getVelocity().z);
            }
        }
    }
}
