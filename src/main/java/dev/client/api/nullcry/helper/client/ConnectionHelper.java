package dev.client.api.nullcry.helper.client;

import com.google.common.eventbus.Subscribe;
import dev.client.api.injection.accessor.IBossBarHudAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.scoreboard.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

@UtilityClass
public class ConnectionHelper implements ClientApi {
    private String server = "Vanilla";
    private int anarchy;

    @Subscribe
    public void tick(UpdateEvent eventUpdate) {
        anarchy = getAnarchyMode();
        server = updateServer();
    }

    public boolean isPvP() {
        if (mc.player == null) return false;

        Collection<ClientBossBar> bossBars = ((IBossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBarsMap().values();
        for (ClientBossBar bossBar : bossBars) {
            String name = bossBar.getName().getString().toLowerCase();
            if (name.contains("pvp") || name.contains("пвп")) {
                return true;
            }
        }
        return false;
    }

    private int getAnarchyMode() {
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        switch (server) {
            case "FunTime" -> {
                if (objective != null) {
                    String[] string = objective.getDisplayName().getString().split("-");
                    if (string.length > 1) return Integer.parseInt(string[1]);
                }
            }
            case "HolyWorld" -> {
                for (ScoreboardEntry scoreboardEntry : scoreboard.getScoreboardEntries(objective)) {
                    String text = Team.decorateName(scoreboard.getScoreHolderTeam(scoreboardEntry.owner()), scoreboardEntry.name()).getString();
                    if (!text.isEmpty()) {
                        String string = StringUtils.substringBetween(text, "#", " -◆-");
                        if (string != null && !string.isEmpty()) return Integer.parseInt(string);
                    }
                }
            }
        }
        return -1;
    }

    private String updateServer() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null || mc.getNetworkHandler().getBrand() == null) return "Vanilla";
        String serverIp = mc.getNetworkHandler().getServerInfo().address.toLowerCase();
        String brand = mc.getNetworkHandler().getBrand().toLowerCase();

        if (brand.contains("botfilter")) return "FunTime";
        else if (serverIp.contains("funtime") || serverIp.contains("skytime") || serverIp.contains("space-times") || serverIp.contains("funsky")) return "CopyTime";
        else if (brand.contains("holyworld")||brand.contains("leaf") || brand.contains("vk.com/idwok")) return "HolyWorld";
        else if (serverIp.contains("reallyworld")) return "ReallyWorld";
        return "Vanilla";
    }

    public String getServerIP() {
        if (mc.world == null) return "mainmenu";

        if (mc.isInSingleplayer()) return "local";

        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address.toLowerCase();
        }

        return "";
    }

    public boolean isConnectedToServer(String ip) {
        return mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null && mc.getCurrentServerEntry().address.contains(ip);
    }

    public int getAnarchy() {
        return anarchy;
    }

    public boolean isFT() {
        return isConnectedToServer("funtime");
    }
    public boolean isNM() {
        return isConnectedToServer("NexusMine");
    }

    public boolean isHW() {
        return isConnectedToServer("holyworld");
    }

    public boolean isRW() {
        return isConnectedToServer("reallyworld");
    }

    public boolean isFunSky() {
        return isConnectedToServer("funsky");
    }

    public boolean isSpookyTime() {
        return isConnectedToServer("spookytime");
    }
}