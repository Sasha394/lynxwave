package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class FastBreak extends Module {

    public FastBreak() {
        super("FastBreak", ModuleCategory.Player, "Убирает задержку при ломании блоков");
    }

    Slider delay = new Slider("Задержка", () -> true).set(1, 3, 1).defaultValue(2).register(this);
    CheckBox matrixBypass = new CheckBox("Обход Matrix", () -> true).defaultValue(false).register(this);

    private int tickCounter = 0;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!(mc.crosshairTarget instanceof BlockHitResult result)) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        BlockPos pos = result.getBlockPos();

        if (mc.interactionManager.currentBreakingProgress <= 0.5f) return;
        tickCounter++;

        if (tickCounter >= delay.getValue().intValue()) {
            tickCounter = 0;

            if (matrixBypass.getEnabled()) {
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, result.getSide()));
            } else {
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, result.getSide()));
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, result.getSide()));
            }
        }
    }
}
