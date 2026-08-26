package dev.client.modules.core.misc;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AutoDuel extends Module {

    public AutoDuel() {
        super("AutoDuel", ModuleCategory.Misc, "Автоматически отправляет запрос на дуэль");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("ReallyWorld").defaultValue("ReallyWorld").register(this);
    SelectElements kits = new SelectElements("Киты", () -> mode.isSelected("ReallyWorld")).set("Щит", "Шипы 3", "Лук", "Тотемы", "nodebaff", "shari", "classic", "chiterskii rai", "nezerka").defaultValue("Тотемы").register(this);

    private final ReallyWorld reallyWorld = new ReallyWorld();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mode.isSelected("ReallyWorld") && isRW()) {
            reallyWorld.handleRWUpdateEvent(event);
        }
    }

    @Subscribe
    private void onPacket(PacketEvent packetEvent) {
        if (mode.isSelected("ReallyWorld") && isRW()) {
            reallyWorld.handleRWPacketEvent(packetEvent);
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();

        if (!mode.isSelected("ReallyWorld") || !isRW()) {
            printClient("Мод работает только на сервере ReallyWorld");
            toggle();
            return;
        }

        reallyWorld.sent.clear();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
    }

    private class ReallyWorld {
        private final Pattern pattern = Pattern.compile("^\\w{3,16}$");
        private final List<String> sent = Lists.newArrayList();
        private final TimerUtil counter = new TimerUtil();
        private final TimerUtil counter2 = new TimerUtil();
        private final TimerUtil counterChoice = new TimerUtil();
        private final TimerUtil counterTo = new TimerUtil();

        public void handleRWUpdateEvent(UpdateEvent updateEvent) {
            final List<String> players = getOnlinePlayers();

            if (counter2.isReached(800L * players.size())) {
                sent.clear();
                counter2.reset();
            }

            for (final String player : players) {
                if (!sent.contains(player) && !player.equals(mc.session.getUsername())) {
                    if (counter.isReached(1000)) {
                        printChat("/duel " + player);
                        sent.add(player);
                        counter.reset();
                    }
                }
            }

            if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
                if (mc.currentScreen.getTitle().getString().contains("Выбор набора (1/1)")) {
                    handleKitSelection(chest);
                } else if (mc.currentScreen.getTitle().getString().contains("Настройка поединка")) {
                    handleDuelSetup(chest);
                }
            }
        }

        public void handleRWPacketEvent(PacketEvent packetEvent) {
            if (packetEvent.isReceive()) {
                Packet<?> packet = packetEvent.getPacket();

                if (packet instanceof GameMessageS2CPacket chat) {
                    final String text = chat.content().getString().toLowerCase();
                    if ((text.contains("начало") && text.contains("через") && text.contains("секунд!")) || (text.equals("дуэли » во время поединка запрещено использовать команды"))) {
                        toggle();
                    }
                }
            }
        }

        private List<String> getOnlinePlayers() {
            return mc.player.networkHandler.getPlayerList().stream()
                    .map(PlayerListEntry::getProfile)
                    .map(GameProfile::name)
                    .filter(profileName -> pattern.matcher(profileName).matches())
                    .collect(Collectors.toList());
        }

        private void handleKitSelection(GenericContainerScreenHandler chest) {
            final List<Integer> slotsID = new ArrayList<>();
            int index = 0;

            for (CheckBox checkBox : kits.getValues()) {
                if (!checkBox.getEnabled()) {
                    index++;
                    continue;
                }
                slotsID.add(index);
                index++;
            }

            Collections.shuffle(slotsID);
            final int slotID = slotsID.get(0);

            if (counterChoice.isReached(90)) {
                mc.interactionManager.clickSlot(chest.syncId, slotID, 0, SlotActionType.QUICK_MOVE, mc.player);
                counterChoice.reset();
            }
        }

        private void handleDuelSetup(GenericContainerScreenHandler chest) {
            if (counterTo.isReached(90)) {
                mc.interactionManager.clickSlot(chest.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                counterTo.reset();
            }
        }
    }
}
