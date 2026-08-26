package dev.client.cmd.core.cmd_modules;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.injection.accessor.IPlayerListHudAccessor;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.world.WorldChangeEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.Difficulty;

import java.util.List;
import java.util.Locale;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdRCT implements Command, CommandWithAdvice {
    static final int GUI_CLICK_DELAY_TICKS = 2;
    static final int MENU_FAILSAFE_DELAY_TICKS = 5;
    static final int AN_MIN = 1;
    static final int AN_MAX = 69;

    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    public CmdRCT(MinecraftClient mc, Prefix prefix, Logger logger) {
        this.mc = mc;
        this.prefix = prefix;
        this.logger = logger;
        LynxWave.getInstance().getEventBus().register(this);
    }

    enum Phase { IDLE, GO_HUB, WAIT_MENU, OPEN_MENU, SELECTING, DONE }
    Phase phase = Phase.IDLE;
    String pref;
    boolean processing;
    boolean lite;
    boolean explicitTarget;
    boolean menuFailsafeSent = false;

    String title = null;
    int numberAn;
    int waitAfterHub = 0;
    int guiDelayTicks = 0;
    int pendingSyncId = -1;

    boolean needClickLiteCategory;
    boolean needClickLiteNumber;
    boolean liteCategorySelected;
    boolean selectionDone;

    @Override public String name() { return "rct"; }

    @Override
    public String description() {
        return "Перезаходит на лайт-анархию HolyWorld, можно указать номер.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "rct - перезаходит на текущую лайт-анархию",
                prefix.get() + "rct <an> — заходит на указанную лайт-анархию (1–69)",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "rct" + Formatting.GRAY + " или " + Formatting.WHITE + prefix.get() + "rct 14"
        );
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("номер анархии");
    }

    @Override
    public void execute(Parameters parameters) {
        if (mc.player == null) return;

        if (!ConnectionHelper.isHW()) {
            logger.log("Этот RCT работает только на HolyWorld.");
            return;
        }
        if (ConnectionHelper.isPvP()) {
            logger.log("Невозможно перезайти в PvP-режиме.");
            return;
        }

        if (!parseParametersTarget(parameters)) return;

        logger.log((explicitTarget ? "Захожу на " : "Перезахожу на ") + formatTarget());

        resetTransientState();

        if (explicitTarget) {
            if (!isInHub()) {
                phase = Phase.GO_HUB;
                mc.player.networkHandler.sendChatCommand("hub");
            } else {
                phase = Phase.OPEN_MENU;
                sendMenu();
                menuFailsafeSent = true;
            }
        } else {
            if (isInHub()) {
                phase = Phase.OPEN_MENU;
                sendMenu();
                menuFailsafeSent = true;
            } else {
                phase = Phase.GO_HUB;
                mc.player.networkHandler.sendChatCommand("hub");
            }
        }
    }

    @Subscribe
    public void onWorldChange(WorldChangeEvent event) {
        if (!processing || mc == null || mc.world == null || mc.player == null) return;

        if (phase == Phase.GO_HUB) {
            waitAfterHub = MENU_FAILSAFE_DELAY_TICKS;
            menuFailsafeSent = false;
            phase = Phase.WAIT_MENU;
            return;
        }

        if (phase == Phase.SELECTING) {
            Difficulty diff = mc.world.getDifficulty();
            if (diff == Difficulty.EASY || diff == Difficulty.HARD) {
                processing = false;
                phase = Phase.DONE;
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!processing || mc == null || mc.player == null) return;

        if (phase == Phase.GO_HUB && waitAfterHub == 0) {
            waitAfterHub = MENU_FAILSAFE_DELAY_TICKS;
            menuFailsafeSent = false;
            phase = Phase.WAIT_MENU;
        }

        if (phase == Phase.WAIT_MENU) {
            if (waitAfterHub > 0) waitAfterHub--;
            if (waitAfterHub == 0 && !menuFailsafeSent) {
                sendMenu();
                menuFailsafeSent = true;
                phase = Phase.OPEN_MENU;
            }
        }

        if (guiDelayTicks > 0) {
            if (--guiDelayTicks == 0 && pendingSyncId != -1 && title != null) {
                tryClickCurrentScreen(pendingSyncId, title);
                pendingSyncId = -1;
                title = null;
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!event.isReceive() || !(event.getPacket() instanceof OpenScreenS2CPacket packet)) return;
        if (!processing || mc == null || mc.player == null) return;

        String titleRaw = packet.getName().getString();
        String title = titleRaw.replace("§", "").toLowerCase(Locale.ROOT);

        if (phase == Phase.WAIT_MENU || phase == Phase.OPEN_MENU) {
            phase = Phase.SELECTING;
        }
        if (phase != Phase.SELECTING || selectionDone || !lite) return;

        if (isMenuTitle(title)) {
            needClickLiteNumber = true;
            if (!liteCategorySelected) needClickLiteCategory = true;
            scheduleClick(packet.getSyncId(), titleRaw);
            return;
        }

        if (isTitle(title)) {
            scheduleClick(packet.getSyncId(), titleRaw);
            return;
        }
    }

    private void tryClickCurrentScreen(int syncId, String title) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        int topCount = getSlotsCount();

        Runnable clickCenter = () -> {
            if (topCount > 0) {
                int center = (topCount == 9) ? 4 : (topCount >= 27 ? 12 : topCount / 2);
                sendClickByTopIndex(syncId, center);
            }
        };

        String t = nrm(title);

        if (t.contains("выборлайтанарх") || (t.contains("лайт") && t.contains("анарх"))) {

            if (needClickLiteCategory && !liteCategorySelected) {
                int categoryIndex = switch (pref) {
                    case "Соло" -> 0;
                    case "Дуо"  -> 1;
                    case "Трио" -> 2;
                    case "Клан" -> 3;
                    default     -> -1;
                };
                if (categoryIndex >= 0 && categoryIndex < topCount) {
                    sendClickByTopIndex(syncId, categoryIndex);
                    liteCategorySelected = true;
                }
                needClickLiteCategory = false;
                scheduleClick(syncId, title);
                return;
            }

            if (needClickLiteNumber) {
                boolean byName = clickAnByName(syncId, pref, numberAn, topCount);
                boolean byAfter64 = !byName && numberAn > 64 && clickAfter64ByRightOffset(syncId, numberAn, topCount);
                boolean byCount = !byName && !byAfter64 && clickAnByCount(syncId, numberAn, topCount);

                if (!byName && !byAfter64 && !byCount) {
                    int calcIdx = getSlotIndexAnarchy(pref, numberAn);
                    if (calcIdx != -1 && calcIdx < topCount) sendClickByTopIndex(syncId, calcIdx);
                    else clickCenter.run();
                }
                needClickLiteNumber = false;
                selectionDone = true;
                return;
            }
        }

        if (t.contains("выберитережим") || t.contains("выборрежима") || t.contains("режим")) {
            boolean clicked = clickByNamePart(syncId, topCount, "лайт") || clickByNamePart(syncId, topCount, "light");
            if (!clicked) clickCenter.run();
        }
    }

    private boolean parseParametersTarget(Parameters parameters) {
        explicitTarget = false;

        String sArg = parameters.asString(0).orElse(null);
        if (sArg == null || sArg.isBlank()) {
            return parseCurrentFromTab();
        }

        Integer an = parseIntSafe(sArg);
        if (an == null) {
            logger.log("Неверная команда. Напишите в чат: .assists " + name());
            return false;
        }

        if (!validateAn(an)) return false;
        String mapped = getValidateAnarchy(an);
        if (mapped == null) {
            logger.log("Не удалось сопоставить категорию для Лайт #" + an + ".");
            return false;
        }

        lite = true;
        pref = mapped;
        numberAn = an;
        explicitTarget = true;
        return true;
    }

    private boolean parseCurrentFromTab() {
        Text headerText = ((IPlayerListHudAccessor) mc.inGameHud.getPlayerListHud()).getHeader();
        if (headerText == null) {
            logger.log("Не удалось определить текущую лайт-анархию");
            return false;
        }

        String tabHeader = headerText.getString();
        lite = tabHeader.contains("Лайт");

        int idx = tabHeader.indexOf('▶');
        if (idx == -1) {
            return false;
        }

        String lightInfo = tabHeader.substring(idx + 1).replace("Анархия", "").trim();
        String[] splitLight = lightInfo.split("Лайт", 2);
        pref = splitLight[0].trim();

        int hashIdx = lightInfo.indexOf('#');
        if (hashIdx == -1) {
            return false;
        }

        Integer parsed = parseIntSafe(lightInfo.substring(hashIdx + 1).trim());
        if (parsed == null) {
            logger.log("Не удалось распарсить номер лайт-анархии");
            return false;
        }
        if (!validateAn(parsed)) return false;

        numberAn = parsed;
        return true;
    }

    private boolean validateAn(int an) {
        if (an < AN_MIN || an > AN_MAX) {
            logger.log("Нет анки #" + an + ". Диапазон " + AN_MIN + "–" + AN_MAX + ".");
            return false;
        }
        return true;
    }

    private String getValidateAnarchy(int num) {
        if (num >= 1  && num <= 14) return "Соло";
        if (num >= 15 && num <= 32) return "Дуо";
        if (num >= 33 && num <= 47) return "Трио";
        if (num >= 48 && num <= 69) return "Клан";
        return null;
    }

    private int getSlotIndexAnarchy(String pref, int num) {
        int start1b, end1b, baseNum;
        switch (pref) {
            case "Соло" -> { start1b = 19; end1b = 32; baseNum = 1;  }
            case "Дуо"  -> { start1b = 19; end1b = 36; baseNum = 15; }
            case "Трио" -> { start1b = 19; end1b = 32; baseNum = 33; }
            case "Клан" -> { start1b = 19; end1b = 40; baseNum = 48; }
            default -> { return -1; }
        }
        int countSlots = end1b - start1b + 1;
        int offset = num - baseNum;
        if (offset < 0 || offset >= countSlots) return -1;
        int oneBasedSlot = start1b + offset;
        return oneBasedSlot - 1;
    }

    private int[] getClickSlotAnarchy(String pref) {
        return switch (pref) {
            case "Соло" -> new int[]{18, 31};
            case "Дуо"  -> new int[]{18, 35};
            case "Трио" -> new int[]{18, 31};
            case "Клан" -> new int[]{18, 39};
            default     -> new int[]{9, Integer.MAX_VALUE};
        };
    }

    private boolean isMenuTitle(String t) {
        return t.contains("выбор лайт анарх")
                || t.contains("сололайт анарх")
                || t.contains("дуолайт анарх")
                || t.contains("триолайт анарх")
                || t.contains("кланлайт анарх")
                || (t.contains("лайт") && t.contains("анарх"));
    }

    // click
    private boolean clickAnByCount(int syncId, int targetNumber, int topCount) {
        var handler = mc.player.currentScreenHandler;

        int[] range = getClickSlotAnarchy(pref);
        int from = Math.max(0, range[0]);
        int to   = Math.min(topCount - 1, range[1]);

        for (int i = from; i <= to; i++) {
            var slot = handler.slots.get(i);
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty()) continue;

            if (st.getCount() == targetNumber) {
                sendClickByTopIndex(syncId, i);
                return true;
            }
        }
        return false;
    }

    private boolean clickAfter64ByRightOffset(int syncId, int targetNumber, int topCount) {
        if (targetNumber <= 64) return false;
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        var handler = mc.player.currentScreenHandler;

        int[] range = getClickSlotAnarchy(pref);
        int from = Math.max(0, range[0]);
        int searchTo = Math.min(topCount - 1, range[1]);
        int offset = targetNumber - 64;

        for (int i = from; i <= searchTo; i++) {
            var slot = handler.slots.get(i);
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty() || st.getCount() != 64) continue;

            int targetIndex = i + offset;
            if (targetIndex >= topCount) continue;
            if (targetIndex / 9 != i / 9) continue;

            ItemStack targetStack = handler.slots.get(targetIndex).getStack();
            if (targetStack == null || targetStack.isEmpty() || targetStack.getCount() != 64) continue;

            sendClickByTopIndex(syncId, targetIndex);
            return true;
        }

        return false;
    }

    private boolean clickByNamePart(int syncId, int topCount, String key) {
        var handler = mc.player.currentScreenHandler;
        String k = nrm(key);
        for (int i = 0; i < topCount; i++) {
            var slot = handler.slots.get(i);
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty()) continue;
            String nn = nrm(st.getName().getString());
            if (nn.contains(k)) {
                sendClickByTopIndex(syncId, i);
                return true;
            }
        }
        return false;
    }

    private boolean clickAnByName(int syncId, String pref, int num, int topCount) {
        var handler = mc.player.currentScreenHandler;

        String prefKey = switch (pref) {
            case "Соло" -> "соло";
            case "Дуо"  -> "дуо";
            case "Трио" -> "трио";
            case "Клан" -> "клан";
            default     -> "";
        };

        String p1 = prefKey + "лайт#" + num;
        String p2 = prefKey + "лайтанархии#" + num;
        String p3 = prefKey + "лайт" + num;
        String p4 = prefKey + "лайтанархии" + num;

        int[] range = getClickSlotAnarchy(pref);
        int from = Math.max(0, range[0]);
        int to   = Math.min(topCount - 1, range[1]);

        for (int i = from; i <= to; i++) {
            var slot = handler.slots.get(i);
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty()) continue;

            String nn = nrm(st.getName().getString());
            boolean hit = nn.contains(p1) || nn.contains(p2) || nn.contains(p3) || nn.contains(p4)
                    || (nn.contains("#" + num) && nn.contains(prefKey + "лайт"));

            if (hit) { sendClickByTopIndex(syncId, i); return true; }
        }
        return false;
    }

    private int getSlotsCount() {
        if (mc == null || mc.player == null || mc.player.currentScreenHandler == null) return 0;
        var handler = mc.player.currentScreenHandler;
        int top = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).inventory == mc.player.getInventory()) break;
            top++;
        }
        return top;
    }

    private void sendClickByTopIndex(int syncId, int topIndex) {
        var handler = mc.player.currentScreenHandler;
        if (topIndex < 0 || topIndex >= handler.slots.size()) return;
        int slotId = handler.slots.get(topIndex).id;
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private String nrm(String s) {
        if (s == null) return "";
        return s.replace("§", "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[\\s_\\-:]+", "");
    }

    private Integer parseIntSafe(String string) {
        if (string == null || string.isBlank()) return null;
        try { return Integer.parseInt(string.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private void scheduleClick(int syncId, String titleRaw) {
        pendingSyncId = syncId;
        title = titleRaw;
        guiDelayTicks = GUI_CLICK_DELAY_TICKS;
    }

    private void sendMenu() {
        if (mc == null || mc.player == null) return;
        mc.player.networkHandler.sendChatCommand("menu");
        phase = Phase.OPEN_MENU;
    }

    private boolean isTitle(String t) {
        return t.contains("выберите режим") || t.contains("выбор режима") || t.contains("режим");
    }

    private String formatTarget() {
        return (pref == null ? "" : pref + " ") + "Лайт #" + numberAn;
    }

    private boolean isInHub() {
        return mc.world != null && mc.world.getDifficulty() == Difficulty.PEACEFUL;
    }

    private void resetTransientState() {
        processing = true;
        selectionDone = false;
        liteCategorySelected = false;
        waitAfterHub = 0;
        menuFailsafeSent = false;

        guiDelayTicks = 0;
        pendingSyncId = -1;
        title = null;

        needClickLiteCategory = false;
        needClickLiteNumber = false;
        phase = Phase.IDLE;
    }
}
