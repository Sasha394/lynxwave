package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoChestWarden extends Module {
    public static AutoChestWarden INSTANCE;

    private static final Set<BlockEntityType<?>> LOOT_BLOCK_TYPES = Set.of(
            BlockEntityType.CHEST,
            BlockEntityType.TRAPPED_CHEST,
            BlockEntityType.BARREL,
            BlockEntityType.SHULKER_BOX
    );

    private static final Pattern TIMER_MMSS = Pattern.compile("(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})");
    private static final Pattern TIMER_SECOND_UNIT = Pattern.compile("(\\d{1,3})\\s*(?:сек(?:унд(?:ы|у|я)?)?|s|sec|seconds?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIMER_MINUTE_UNIT = Pattern.compile("(\\d{1,2})\\s*(?:мин(?:ут(?:ы|у|я)?)?|m|min|minutes?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIMER_NUMBER = Pattern.compile("(\\d{1,3})");

    private static final int HOLOGRAM_SEARCH_RADIUS = 2;
    private static final long LOOT_DELAY_MS = 50L;

    public AutoChestWarden() {
        super("AutoChestWarden", ModuleCategory.Misc, "Автоматически фармит таймерные сундуки на анархиях");
    }

    Slider lootAnka = new Slider("Лут Анка", () -> true)
            .set(101f, 605f, 1f).applyDefault(101f).register(this);
    Slider dropAnka = new Slider("Выброс Анка", () -> true)
            .set(101f, 605f, 1f).applyDefault(102f).register(this);

    private enum State {
        IDLE,
        AIMED,
        WAITING_TIMER,
        WAITING_HOLOGRAM_GONE,
        SENDING_LOOT_ANKA,
        WAITING_LOOT_ANKA,
        OPENING_CHEST,
        LOOTING,
        SENDING_DROP_ANKA,
        WAITING_DROP_ANKA,
        OPENING_DROP_CHEST,
        STORING,
        SENDING_LOOT_ANKA_RETURN,
        WAITING_LOOT_ANKA_RETURN,
        WAITING_HOLOGRAM_APPEAR,
        GOING_HUB,
        WAITING_HUB
    }

    private State state = State.IDLE;

    private final TimerUtil aimedWatch = new TimerUtil();
    private final TimerUtil timerNotifyWatch = new TimerUtil();
    private final TimerUtil actionWatch = new TimerUtil();
    private final TimerUtil lootWatch = new TimerUtil();
    private final TimerUtil lootFinishWatch = new TimerUtil();

    private BlockPos targetChestPos = null;
    private int cachedTimer = -1;
    private long timerEndTime = -1L;
    private boolean notifiedAimed = false;
    private boolean hasHologram = false;
    private boolean hologramGoneDetected = false;

    @Override
    public void onEnabled() {
        super.onEnabled();
        resetState();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        resetState();
    }

    private void notify(String msg, int seconds) {
        handlerClient().getNotificationManager().add(msg, seconds, NotificationType.INFO, RenderType.WORLD);
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (e.isReceive() && e.getPacket() instanceof GameMessageS2CPacket packet) {
            String msg = packet.content().getString();
            if (msg.contains("Сервер заполнен")) {
                notify("Сервер заполнен! Повтор через 1 сек...", 2);
                actionWatch.reset();
                switch (state) {
                    case WAITING_LOOT_ANKA -> state = State.SENDING_LOOT_ANKA;
                    case WAITING_DROP_ANKA -> state = State.SENDING_DROP_ANKA;
                    case WAITING_LOOT_ANKA_RETURN -> state = State.SENDING_LOOT_ANKA_RETURN;
                }
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case IDLE -> handleIdle();
            case AIMED -> handleAimed();
            case WAITING_TIMER -> handleWaitingTimer();
            case WAITING_HOLOGRAM_GONE -> handleWaitingHologramGone();
            case SENDING_LOOT_ANKA -> handleSendingLootAnka();
            case WAITING_LOOT_ANKA -> handleWaitingLootAnka();
            case OPENING_CHEST -> handleOpeningChest();
            case LOOTING -> handleLooting();
            case SENDING_DROP_ANKA -> handleSendingDropAnka();
            case WAITING_DROP_ANKA -> handleWaitingDropAnka();
            case OPENING_DROP_CHEST -> handleOpeningDropChest();
            case STORING -> handleStoring();
            case SENDING_LOOT_ANKA_RETURN -> handleSendingLootAnkaReturn();
            case WAITING_LOOT_ANKA_RETURN -> handleWaitingLootAnkaReturn();
            case WAITING_HOLOGRAM_APPEAR -> handleWaitingHologramAppear();
            case GOING_HUB -> handleGoingHub();
            case WAITING_HUB -> handleWaitingHub();
        }
    }

    private void handleIdle() {
        BlockPos chestPos = getLookedChest();
        if (chestPos == null) {
            notifiedAimed = false;
            return;
        }

        if (!notifiedAimed) {
            notify("Навелся!", 2);
            notifiedAimed = true;
            aimedWatch.reset();
            timerNotifyWatch.reset();
        }

        targetChestPos = chestPos;
        state = State.AIMED;
    }

    private void handleAimed() {
        BlockPos chestPos = getLookedChest();

        if (chestPos == null || !chestPos.equals(targetChestPos)) {
            notify("Взгляд отведён, сброс", 2);
            resetState();
            return;
        }

        int timer = findTimerNearChest(targetChestPos);
        hasHologram = timer >= 0;

        if (hasHologram) {
            cachedTimer = timer;
            timerEndTime = System.currentTimeMillis() + (timer * 1000L);
        }

        if (timerNotifyWatch.isReached(1000)) {
            if (hasHologram) {
                notify("До открытия: " + formatTimer(cachedTimer), 2);
            } else {
                notify("Голограммы нет, открою через " +
                        Math.max(0, 3 - aimedWatch.elapsedTime() / 1000) + "с", 2);
            }
            timerNotifyWatch.reset();
        }

        if (aimedWatch.isReached(3000)) {
            if (hasHologram) {
                notify("Идём в /hub, таймер: " + formatTimer(cachedTimer), 2);
                state = State.GOING_HUB;
            } else {
                notify("Открываем сундук без таймера", 2);
                openBlock(targetChestPos);
                actionWatch.reset();
                lootFinishWatch.reset();
                state = State.LOOTING;
            }
        }
    }

    private void handleWaitingTimer() {
        if (timerEndTime > 0) {
            long remainingMs = timerEndTime - System.currentTimeMillis();
            cachedTimer = (int) Math.max(0, remainingMs / 1000L);
        }

        if (targetChestPos != null) {
            int realTimer = findTimerNearChest(targetChestPos);
            if (realTimer >= 0) {
                cachedTimer = realTimer;
                timerEndTime = System.currentTimeMillis() + (realTimer * 1000L);
            }
        }

        if (timerNotifyWatch.isReached(1000) && cachedTimer >= 0) {
            notify("До открытия: " + formatTimer(cachedTimer), 2);
            timerNotifyWatch.reset();
        }

        if (cachedTimer >= 0 && cachedTimer <= 0) {
            state = State.SENDING_LOOT_ANKA;
        }
    }

    private void handleWaitingHologramGone() {
        if (targetChestPos == null) {
            state = State.OPENING_CHEST;
            return;
        }

        int timer = findTimerNearChest(targetChestPos);

        if (timer < 0) {
            if (!hologramGoneDetected) {
                notify("Голограмма исчезла! Открываем через 700мс", 2);
                actionWatch.reset();
                hologramGoneDetected = true;
            }

            if (actionWatch.isReached(500)) {
                openBlock(targetChestPos);
                actionWatch.reset();
                lootFinishWatch.reset();
                hologramGoneDetected = false;
                state = State.LOOTING;
            }
        } else {
            hologramGoneDetected = false;
        }
    }

    private void handleSendingLootAnka() {
        if (!actionWatch.isReached(1000)) return;
        int anka = lootAnka.getValue().intValue();
        sendChat("/an" + anka);
        notify("Отправлен /an" + anka, 2);
        actionWatch.reset();
        state = State.WAITING_LOOT_ANKA;
    }

    private void handleWaitingLootAnka() {
        if (!actionWatch.isReached(1300)) return;
        state = State.WAITING_HOLOGRAM_GONE;
    }

    private void handleOpeningChest() {
        if (targetChestPos == null) {
            resetState();
            return;
        }
        openBlock(targetChestPos);
        actionWatch.reset();
        lootFinishWatch.reset();
        state = State.LOOTING;
    }

    private void handleLooting() {
        if (mc.player.currentScreenHandler == null) {
            if (lootFinishWatch.isReached(1500)) {
                state = State.SENDING_DROP_ANKA;
            }
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;

        boolean hasItems = handler.slots.stream()
                .filter(slot -> !slot.inventory.equals(mc.player.getInventory()))
                .anyMatch(Slot::hasStack);

        if (!hasItems) {
            if (lootFinishWatch.isReached(500)) {
                closeScreen();
                state = State.SENDING_DROP_ANKA;
            }
            return;
        }

        lootFinishWatch.reset();

        if (lootWatch.isReached(LOOT_DELAY_MS)) {
            handler.slots.stream()
                    .filter(slot -> !slot.inventory.equals(mc.player.getInventory()))
                    .filter(Slot::hasStack)
                    .forEach(slot -> mc.interactionManager.clickSlot(
                            handler.syncId,
                            slot.id,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                    ));
            lootWatch.reset();
        }
    }

    private void handleSendingDropAnka() {
        if (!actionWatch.isReached(1000)) return;
        int anka = dropAnka.getValue().intValue();
        sendChat("/an" + anka);
        notify("Отправлен /an" + anka + " (выброс)", 2);
        actionWatch.reset();
        state = State.WAITING_DROP_ANKA;
    }

    private void handleWaitingDropAnka() {
        if (!actionWatch.isReached(3000)) return;
        state = State.OPENING_DROP_CHEST;
    }

    private void handleOpeningDropChest() {
        BlockPos chestPos = getLookedChest();
        if (chestPos == null) return;

        openBlock(chestPos);
        actionWatch.reset();
        lootFinishWatch.reset();
        state = State.STORING;
    }

    private void handleStoring() {
        if (mc.player.currentScreenHandler == null) {
            if (lootFinishWatch.isReached(1500)) {
                state = State.SENDING_LOOT_ANKA_RETURN;
            }
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;

        boolean hasItemsInInventory = handler.slots.stream()
                .filter(slot -> slot.inventory.equals(mc.player.getInventory()))
                .anyMatch(Slot::hasStack);

        if (!hasItemsInInventory) {
            if (lootFinishWatch.isReached(500)) {
                closeScreen();
                state = State.SENDING_LOOT_ANKA_RETURN;
            }
            return;
        }

        lootFinishWatch.reset();

        if (lootWatch.isReached(LOOT_DELAY_MS)) {
            handler.slots.stream()
                    .filter(slot -> slot.inventory.equals(mc.player.getInventory()))
                    .filter(Slot::hasStack)
                    .forEach(slot -> mc.interactionManager.clickSlot(
                            handler.syncId,
                            slot.id,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                    ));
            lootWatch.reset();
        }
    }

    private void handleSendingLootAnkaReturn() {
        if (!actionWatch.isReached(1000)) return;
        int anka = lootAnka.getValue().intValue();
        sendChat("/an" + anka);
        notify("Возврат на /an" + anka, 2);
        actionWatch.reset();
        state = State.WAITING_LOOT_ANKA_RETURN;
    }

    private void handleWaitingLootAnkaReturn() {
        if (!actionWatch.isReached(3000)) return;
        state = State.WAITING_HOLOGRAM_APPEAR;
    }

    private void handleWaitingHologramAppear() {
        if (targetChestPos == null) return;

        int timer = findTimerNearChest(targetChestPos);
        if (timer >= 0) {
            cachedTimer = timer;
            timerEndTime = System.currentTimeMillis() + (timer * 1000L);
            notify("Голограмма появилась! До открытия: " + formatTimer(timer), 2);
            state = State.GOING_HUB;
        }
    }

    private void handleGoingHub() {
        sendChat("/hub");
        notify("Отправлен /hub", 2);
        actionWatch.reset();
        state = State.WAITING_HUB;
    }

    private void handleWaitingHub() {
        if (!actionWatch.isReached(3000)) return;
        timerNotifyWatch.reset();
        state = State.WAITING_TIMER;
    }

    private BlockPos getLookedChest() {
        if (mc.crosshairTarget == null) return null;
        if (mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos();
        if (mc.world == null) return null;
        BlockEntity entity = mc.world.getBlockEntity(pos);
        if (entity == null) return null;
        if (!LOOT_BLOCK_TYPES.contains(entity.getType())) return null;
        return pos;
    }

    private int findTimerNearChest(BlockPos chestPos) {
        if (mc.world == null || chestPos == null) return -1;
        return mc.world.getEntitiesByClass(
                        ArmorStandEntity.class,
                        new Box(chestPos).expand(HOLOGRAM_SEARCH_RADIUS),
                        entity -> entity.isCustomNameVisible() && entity.getCustomName() != null
                ).stream()
                .mapToInt(entity -> parseTimer(entity.getCustomName()))
                .filter(t -> t >= 0)
                .min()
                .orElse(-1);
    }

    private int parseTimer(Text customName) {
        if (customName == null) return -1;
        String raw = customName.getString();
        if (raw.isEmpty()) return -1;

        String cleaned = raw
                .replaceAll("§[0-9a-fk-or]", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("[^\\p{L}\\p{N}:\\.]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (cleaned.isEmpty()) return -1;

        Matcher m = TIMER_MMSS.matcher(cleaned);
        if (m.find()) {
            int minutes = MathHelper.clamp(Integer.parseInt(m.group(1)), 0, 99);
            int seconds = MathHelper.clamp(Integer.parseInt(m.group(2)), 0, 59);
            return minutes * 60 + seconds;
        }

        m = TIMER_MINUTE_UNIT.matcher(cleaned);
        if (m.find()) {
            return MathHelper.clamp(Integer.parseInt(m.group(1)), 0, 99) * 60;
        }

        m = TIMER_SECOND_UNIT.matcher(cleaned);
        if (m.find()) {
            return MathHelper.clamp(Integer.parseInt(m.group(1)), 0, 599);
        }

        m = TIMER_NUMBER.matcher(cleaned);
        if (m.find()) {
            return MathHelper.clamp(Integer.parseInt(m.group(1)), 0, 999);
        }

        return -1;
    }

    private String formatTimer(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void openBlock(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return;
        Vec3d center = pos.toCenterPos();
        BlockHitResult hit = new BlockHitResult(
                center,
                net.minecraft.util.math.Direction.UP,
                pos,
                false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private void closeScreen() {
        if (mc.player == null) return;
        mc.player.closeHandledScreen();
    }

    private void sendChat(String message) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendChatMessage(message);
    }

    private void resetState() {
        state = State.IDLE;
        targetChestPos = null;
        cachedTimer = -1;
        timerEndTime = -1L;
        notifiedAimed = false;
        hasHologram = false;
        hologramGoneDetected = false;
        aimedWatch.reset();
        timerNotifyWatch.reset();
        actionWatch.reset();
        lootWatch.reset();
        lootFinishWatch.reset();
    }
}
