package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.util.Formatting;

public class AutoRespawn extends Module {

    public AutoRespawn() {
        super("AutoRespawn", ModuleCategory.Player, "Автоматически респавнит после смерти");
    }

    private final CheckBox autoGps = new CheckBox("Gps после смерти", () -> true).defaultValue(false).register(this);
    private final CheckBox notification = new CheckBox("Уведомление в чате после смерти", () -> true).defaultValue(false).register(this);

    private boolean deathProcessed = false;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen instanceof DeathScreen) {
            handleDeathActions();
            handleAutoRespawn();
        } else {
            deathProcessed = false;
        }
    }

    private void handleDeathActions() {
        if (deathProcessed) return;

        if (mc.player.getHealth() < 1.0f && mc.player.deathTime < 1) {
            int positionX = mc.player.getBlockPos().getX();
            int positionY = mc.player.getBlockPos().getY();
            int positionZ = mc.player.getBlockPos().getZ();

            if (notification.getEnabled()) {
                printClient("Ваши координаты смерти: X: " + positionX + " Y: " + positionY + " Z: " + positionZ);
            }

            if (autoGps.getEnabled()) {
                addGpsPoint(positionX, positionZ);
            } else {
                printDeathCoordinates(positionX, positionY, positionZ);
            }

            deathProcessed = true;
        }
    }

    private void handleAutoRespawn() {
        if (mc.player.deathTime > 5) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
    }

    private void printDeathCoordinates(int x, int y, int z) {
        printClient("Координаты смерти: " + Formatting.GRAY + "X: " + x + " Y: " + y + " Z: " + z + Formatting.RESET);
    }

    private void addGpsPoint(int x, int z) {
        String gpsCommand = ".gps add " + x + " " + z;
        printChat(gpsCommand);
    }
}
