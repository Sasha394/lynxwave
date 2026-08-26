package dev.client.modules.core.misc;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.other.PushUtils;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ReallyWorldHelper extends Module {

    public ReallyWorldHelper() {
        super("ReallyWorldHelper", ModuleCategory.Misc, "Вспомогательная функция для сервера \"ReallyWorld\"");
        setModuleServer(ServerType.ReallyWorld);
    }

    SelectElements mode = new SelectElements("Функции", () -> true).set("Блокировать запрещ. слова", "Закрывать меню").defaultValue("Блокировать запрещ. слова", "Закрывать меню").register(this);

    CheckBox autoGps = new CheckBox("Авто точка", () -> true).defaultValue(true).register(this);
    CheckBox notification = new CheckBox("Уведомление", () -> autoGps.getEnabled()).defaultValue(true).register(this);

    public final String[] banWords = new String[]{
            "экспа", "экспенсив", "экспой", "нуриком", "целкой", "нурлан",
            "нурсултан", "целестиал", "целка", "нурик", "атернос", "блейд", "blade", "expa",
            "celka", "nurik", "expensive", "celestial", "nursultan", "фанпей",
            "funpay", "fluger", "акриен", "akrien", "фантайм", "ft", "funtime",
            "безмамный", "rich", "рич", "без мамный", "wild", "вилд", "excellent",
            "экселлент", "hvh", "хвх", "matix", "impact", "матикс", "импакт", "wurst"};

    public final TimerUtil timerUtil = new TimerUtil();
    public final List<String> sent = Lists.newArrayList();

    private boolean joined;
    private UUID uuid;

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.isSend() && event.getPacket() instanceof ChatMessageC2SPacket p) {
            if (mode.isSelected("Блокировать запрещ. слова") && containsBanWords(p.chatMessage())) {
                printClient(Formatting.RED + "Обнаружены запрещенные слова в вашем сообщении.");
                event.cancelled();
            }
        }

        if (event.isReceive()) {
            handleIncomingPackets(event.getPacket());
        }
    }

    private void handleIncomingPackets(Object packet) {
        if (packet instanceof BossBarS2CPacket) {
            handleBossInfo((BossBarS2CPacket) packet);
        } else if (packet instanceof PlayerRespawnS2CPacket) {
            joined = true;
            timerUtil.reset();
        } else if (packet instanceof OpenScreenS2CPacket w) {
            if (mode.isSelected("Закрывать меню") &&
                    w.getName().getString().contains("Меню") &&
                    joined &&
                    !timerUtil.isReached(2000)) {
                mc.player.closeScreen();
                joined = false;
            }
        }
    }

    private void handleBossInfo(BossBarS2CPacket packet) {
        if (!autoGps.getEnabled()) return;

        BossBarS2CPacket.Type type = packet.action.getType();

        if (type == BossBarS2CPacket.Type.ADD) {
            if (packet.action instanceof BossBarS2CPacket.AddAction addAction) {
                String bossName = addAction.name.getString().toLowerCase().replaceAll("\\s+", " ");

                if (bossName.contains("аирдроп")) {
                    parseCoordinates("АирДроп", bossName, "x: ", "z: ");
                } else if (bossName.contains("талисман")) {
                    parseCoordinates("Талисман", bossName, "координаты");
                } else if (bossName.contains("скрудж")) {
                    parseCoordinates("Скрудж", bossName, "координаты");
                }

                uuid = packet.uuid;
            }
        } else if (type == BossBarS2CPacket.Type.REMOVE && packet.uuid.equals(uuid)) {
            resetCoordinates();
        }
    }

    private void parseCoordinates(String entityName, String bossName, String... coordinateKeywords) {
        for (String keyword : coordinateKeywords) {
            int x = extractCoordinate(bossName, "x:");
            int y = extractCoordinate(bossName, "y:");
            int z = extractCoordinate(bossName, "z:");

            if (x != -1 && y != -1 && z != -1) {
                printChat(".gps add " + entityName + " " + x + " " + y + " " + z);

                if (notification.getEnabled()) {
                    PushUtils.sendPush("Уведомление", entityName + " появился на координатах " + x + ", " + z);
                }
                return;
            }
        }
    }

    private void resetCoordinates() {
        printChat(".gps delete АирДроп");
        printChat(".gps delete Талисман");
        printChat(".gps delete Скрудж");
    }

    private boolean containsBanWords(String message) {
        return Arrays.stream(banWords).anyMatch(banWord -> message.toLowerCase().contains(banWord));
    }

    private int extractCoordinate(String text, String coordinateIdentifier) {
        int index = text.indexOf(coordinateIdentifier);
        if (index != -1) {
            String coordinateText = text.substring(index + coordinateIdentifier.length()).split(" ")[0];
            try {
                return Integer.parseInt(coordinateText);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        sent.clear();
    }
}