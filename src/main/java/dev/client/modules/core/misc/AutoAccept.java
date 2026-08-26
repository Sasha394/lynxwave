package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoAccept extends Module {

    public AutoAccept() {
        super("AutoAccept", ModuleCategory.Misc, "Автоматически принимает дуэли/ТП/клан инвайты");
    }

    SelectElements mode = new SelectElements("Режим", () -> true)
            .set("Телепорт", "Принятие в клан", "Дуэли")
            .defaultValue("Телепорт", "Принятие в клан")
            .register(this);

    SelectElements kits = new SelectElements("Киты", () -> mode.isSelected("Дуэли"))
            .set("Классик", "Тотемы", "НоДебафф", "Читерский рай", "Незеритка", "Шипы", "Щит", "Лук", "Шары")
            .defaultValue("Тотемы")
            .register(this);

    SelectElements accept = new SelectElements("Принимать", () -> true)
            .set("Всех", "Друзей")
            .defaultValue("Друзей")
            .register(this);

    CheckBox onlyNearPlayer = new CheckBox("Принимать ТП если рядом игрок", () -> true).defaultValue(false).register(this);
    Slider radius = new Slider("Радиус игроков", () -> onlyNearPlayer.getEnabled()).set(1, 100, 1).defaultValue(50).register(this);

    private String duelChallengerName;
    private boolean duelPending;

    final String[] CLAN_INVITE_REQUESTS = {
            "приглашает вас в клан",
            "в клан"
    };

    private static final Pattern[] TP_NAME_PATTERNS = new Pattern[]{
            Pattern.compile("(^|\\s|[§\\p{Punct}«»\\[\\](){}<>=+*~^|]+)(?<name>[A-Za-z0-9_]{3,16})\\s+просит\\s+телепортироваться", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(^|\\s|[§\\p{Punct}«»\\[\\](){}<>=+*~^|]+)(?<name>[A-Za-z0-9_]{3,16})\\s+просит\\s+к\\s+вам\\s+телепортироваться", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("(^|\\s|[§\\p{Punct}«»\\[\\](){}<>=+*~^|]+)(?<name>[A-Za-z0-9_]{3,16})\\s+has\\s+requested\\s+teleport", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!event.isReceive()) return;

        String raw = extractMessage(event.getPacket());
        if (raw == null || raw.isEmpty()) return;

        handleMessage(raw);
    }

    private @Nullable String extractMessage(Packet<?> pkt) {
        if (pkt instanceof GameMessageS2CPacket p) {
            return normalize(Formatting.strip(p.content().getString()));
        }
        if (pkt instanceof ChatMessageS2CPacket p) {
            Text t = p.unsignedContent();
            return t != null ? normalize(Formatting.strip(t.getString())) : null;
        }
        if (pkt instanceof ProfilelessChatMessageS2CPacket p) {
            return normalize(Formatting.strip(p.message().getString()));
        }
        return null;
    }

    private String normalize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\u0A00-\\u0AFF\\u2000-\\u206F\\p{Cntrl}§]", "")
                .replace("➝", " ")
                .replace("੶", " ")
                .replace("੷", " ")
                .trim()
                .replaceAll("\\s{2,}", " ");
    }

    private void handleMessage(String rawMessage) {
        final String lowerCase = rawMessage.toLowerCase(Locale.ROOT);

        if (rawMessage.contains("Набор: ") && duelPending && mode.get("Дуэли").getEnabled()) {
            duelPending = false;
            boolean ok =
                    kits.isSelected("Классик") ||
                            kits.isSelected("Тотемы") ||
                            kits.isSelected("НоДебафф") ||
                            kits.isSelected("Читерский рай") ||
                            kits.isSelected("Незеритка") ||
                            kits.isSelected("Шипы") ||
                            kits.isSelected("Щит") ||
                            kits.isSelected("Лук") ||
                            kits.isSelected("Шары");

            printChat("/duel " + (ok ? "accept " : "deny ") + duelChallengerName);
            return;
        }

        if (rawMessage.startsWith("Ник: ") && mode.isSelected("Дуэли")) {
            String name = rawMessage.replaceFirst("^Ник:\\s*", "").trim();
            if (canAcceptName(name)) {
                duelPending = true;
                duelChallengerName = name;
            }
            return;
        }

        if (mode.get("Телепорт").getEnabled() &&
                (lowerCase.contains("телепортироваться") || lowerCase.contains("has requested teleport") || lowerCase.contains("просит к вам телепортироваться"))) {

            String name = extractNameFromTeleportMessage(rawMessage);
            if (name != null && canAcceptName(name)) {
                if (onlyNearPlayer.getEnabled() && PlayerHelper.getRadiusPlayer(radius.getValue()) == null) {
                    printClient("В указанном радиусе нет игроков — ТП не принят.");
                    return;
                }
                printChat("/tpaccept");
            }
            return;
        }

        if (mode.isSelected("Принятие в клан") && containsClanInviteRequest(lowerCase)) {
            String playerName = extractPlayerNameFromClan(rawMessage);
            if (playerName != null) {
                if (canAcceptName(playerName)) {
                    printChat("/clan accept " + playerName);
                } else {
                    printClient("Приглашение в клан от " + playerName + " проигнорировано (не друг).");
                }
            }
        }
    }

    private boolean canAcceptName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (accept.isSelected("Всех")) return true;
        if (accept.isSelected("Друзей")) {
            return handlerClient().getFriendManager().isFriend(name);
        }
        return false;
    }

    private boolean containsClanInviteRequest(String messageLc) {
        for (String keyword : CLAN_INVITE_REQUESTS) {
            if (messageLc.contains(keyword)) return true;
        }
        return false;
    }

    private String extractPlayerNameFromClan(String message) {
        String[] parts = message.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if ("приглашает".equalsIgnoreCase(parts[i]) && i - 1 >= 0) {
                return sanitizeName(parts[i - 1]);
            }
        }
        return null;
    }

    private String extractNameFromTeleportMessage(String message) {
        for (Pattern p : TP_NAME_PATTERNS) {
            Matcher m = p.matcher(message);
            if (m.find()) {
                String n = m.group("name");
                if (n != null && !n.isEmpty()) return sanitizeName(n);
            }
        }

        String head = message.trim();
        int cut = head.indexOf(' ');
        if (cut > 0) head = head.substring(0, cut);
        head = head.replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
        if (head.matches("[A-Za-z0-9_]{3,16}")) return head;

        return null;
    }

    private String sanitizeName(String s) {
        if (s == null) return null;
        String name = s.replaceAll("[^A-Za-z0-9_]", "");
        if (name.length() < 3 || name.length() > 16) return null;
        return name;
    }
}
