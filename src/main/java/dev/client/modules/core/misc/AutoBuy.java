package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.autobuy.autobuyui.AutoBuyScreen;
import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.manager.AutoBuyManager;
import dev.client.modules.core.misc.autobuy.*;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AutoBuy extends Module {
    private final ModeElement leaveType = new ModeElement("Тип обхода", () -> true)
            .set("Проверяющий", "Покупающий")
            .defaultValue("Проверяющий")
            .register(this);
    private final Slider timer2 = new Slider("Таймер обновления аукциона", () -> leaveType.isSelected("Покупающий"))
            .set(350f, 750f, 1f)
            .defaultValue(350f)
            .register(this);
    private final CheckBox bypassDelay = new CheckBox("Обход задержки 1.16.5 анках", () -> leaveType.isSelected("Покупающий"))
            .defaultValue(false)
            .register(this);
    private final CheckBox bypassDelay1214 = new CheckBox("Обход задержки 1.21.11 анках", () -> leaveType.isSelected("Покупающий"))
            .defaultValue(false)
            .register(this);
    private final CheckBox autoStorage = new CheckBox("Автоскладирование", () -> leaveType.isSelected("Покупающий"))
            .defaultValue(false)
            .register(this);
    private final KeyBind menuKey = new KeyBind("Кнопка меню", () -> true)
            .set(-1)
            .register(this);

    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();
    private final NetworkManager networkManager;
    private final ServerManager serverManager;
    private final StorageManager storageManager;
    private final AuctionHandler auctionHandler;
    private final AfkHandler afkHandler;

    private final TimerUtil openTimer = TimerUtil.create();
    private final TimerUtil updateTimer = TimerUtil.create();
    private final TimerUtil buyTimer = TimerUtil.create();
    private final TimerUtil switchTimer = TimerUtil.create();
    private final TimerUtil enterDelayTimer = TimerUtil.create();
    private final TimerUtil ahSpamTimer = TimerUtil.create();
    private final TimerUtil connectionCheckTimer = TimerUtil.create();
    private final TimerUtil auctionRequestTimer = TimerUtil.create();

    private boolean open = false;
    private boolean serverInAuction = false;
    private boolean justEntered = false;
    private boolean spammingAh = false;
    private boolean waitingForAuctionOpen = false;
    private final List<AutoBuyableItem> cachedEnabledItems = new ArrayList<>();

    public AutoBuy() {
        super("AutoBuy", ModuleCategory.Misc, "pena (mb)");

        networkManager = new NetworkManager();
        serverManager = new ServerManager(bypassDelay, bypassDelay1214);
        storageManager = new StorageManager(autoStorage);
        auctionHandler = new AuctionHandler(autoBuyManager);
        afkHandler = new AfkHandler();
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        resetTimers();
        resetState();

        if (leaveType.isSelected("Покупающий") && (bypassDelay.getEnabled() || bypassDelay1214.getEnabled())) {
            mc.options.pauseOnLostFocus = false;
        }

        cacheEnabledItems();
        networkManager.start(leaveType.getValue());
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        networkManager.stop();
        serverManager.reset();
        storageManager.reset();
        afkHandler.resetMovementKeys(mc.options);
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        Integer key = menuKey.getKey();
        if (key == null || key == -1) {
            return;
        }
        if (event.getKey() != key) {
            return;
        }
        if (mc.currentScreen instanceof AutoBuyScreen) {
            mc.setScreen(null);
            return;
        }
        if (mc.currentScreen == null) {
            mc.setScreen(new AutoBuyScreen());
        }
    }

    private void resetTimers() {
        openTimer.resetCounter();
        updateTimer.resetCounter();
        buyTimer.resetCounter();
        switchTimer.resetCounter();
        enterDelayTimer.resetCounter();
        ahSpamTimer.resetCounter();
        connectionCheckTimer.resetCounter();
        auctionRequestTimer.resetCounter();
        serverManager.resetTimers();
        storageManager.resetTimers();
        afkHandler.resetTimers();
    }

    private void resetState() {
        open = false;
        serverInAuction = false;
        justEntered = false;
        spammingAh = false;
        waitingForAuctionOpen = false;
        cachedEnabledItems.clear();
        networkManager.clearQueues();
        auctionHandler.clear();
    }

    private void cacheEnabledItems() {
        cachedEnabledItems.clear();
        for (AutoBuyableItem item : autoBuyManager.getAllItems()) {
            if (item.isEnabled()) {
                cachedEnabledItems.add(item);
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof GameMessageS2CPacket gameMessage) {
            Text content = gameMessage.content();
            String message = content.getString();

            if (message.contains("Вы уже подключены к этому серверу!")) {
                serverManager.switchToNextServer(mc.player, networkManager, leaveType.isSelected("Покупающий"));
                return;
            }

            if (leaveType.isSelected("Покупающий")) {
                PurchaseHandler.handlePurchaseMessage(message, autoBuyManager);
            }
        }
    }

    @Subscribe
    public void onTick(UpdateEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (!autoBuyManager.isEnabled()) return;

        handleConnectionStatus();
        afkHandler.handle(mc);
        storageManager.handle(mc, open);

        if (storageManager.isActive()) {
            return;
        }

        if (storageManager.handlePostStorage(mc, enterDelayTimer, ahSpamTimer)) {
            justEntered = true;
        }

        boolean wasInHub = serverManager.isInHub();
        serverManager.updateHubStatus(mc.world);

        if (serverManager.shouldJoinAnarchy(bypassDelay.getEnabled(), bypassDelay1214.getEnabled())) {
            serverManager.joinAnarchyFromHub(mc.player);
        }

        if (wasInHub && !serverManager.isInHub()) {
            handleServerSwitch();
        }

        if (serverManager.isWaitingForServerLoad() || ServerSwitchHandler.isWaitingForServerLoad()) {
            if (ServerSwitchHandler.hasTimedOut() || (!wasInHub && !serverManager.isInHub())) {
                serverManager.setWaitingForServerLoad(false);
                ServerSwitchHandler.setWaitingForServerLoad(false);
                handleServerSwitch();
            }
        }

        handleAhSpam();
        handleAuction();
        handleServerAutoSwitch();
        handleCheckerAuctionRequest();
    }

    private void handleConnectionStatus() {
        if (leaveType.isSelected("Проверяющий")) {
            if (connectionCheckTimer.isReached(5000)) {
                if (!networkManager.isConnectedToServer()) {
                    networkManager.start(leaveType.getValue());
                }
                connectionCheckTimer.resetCounter();
            }
        }
    }

    private void handleServerSwitch() {
        justEntered = true;
        enterDelayTimer.resetCounter();
        switchTimer.resetCounter();
        storageManager.resetMaxShulkers();
        waitingForAuctionOpen = false;
        auctionRequestTimer.resetCounter();
    }

    private void handleAhSpam() {
        if (bypassDelay.getEnabled() || bypassDelay1214.getEnabled()) {
            if (justEntered && enterDelayTimer.isReached(2000)) {
                if (!spammingAh) {
                    spammingAh = true;
                    ahSpamTimer.resetCounter();
                }
            }
            if (spammingAh && !afkHandler.isPerformingAction()) {
                if (ahSpamTimer.isReached(1250)) {
                    if (mc.player.networkHandler != null) {
                        CommandSender.sendCommand(mc.player, "/ah");
                    }
                    ahSpamTimer.resetCounter();
                }
            }
        }
    }

    private void handleCheckerAuctionRequest() {
        if (leaveType.isSelected("Проверяющий")) {
            if (!open && !waitingForAuctionOpen) {
                if (auctionRequestTimer.isReached(3000)) {
                    if (networkManager.isConnectedToServer()) {
                        CommandSender.openAuction();
                        waitingForAuctionOpen = true;
                        auctionRequestTimer.resetCounter();
                    }
                }
            }
            if (waitingForAuctionOpen && auctionRequestTimer.isReached(5000)) {
                waitingForAuctionOpen = false;
                auctionRequestTimer.resetCounter();
            }
        }
    }

    private void handleAuction() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            int syncId = screen.getScreenHandler().syncId;
            List<Slot> slots = screen.getScreenHandler().slots;

            if (title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Поиск")) {
                if (!open) {
                    enterAuction();
                    return;
                }

                storageManager.handleAuctionEnter();

                if (leaveType.isSelected("Покупающий")) {
                    handleBuyerMode(screen, syncId, slots);
                } else if (leaveType.isSelected("Проверяющий")) {
                    handleCheckerMode(slots);
                }
            } else if (title.contains("Подозрительная цена")) {
                auctionHandler.handleSuspiciousPrice(mc, syncId, slots);
                openTimer.resetCounter();
                buyTimer.resetCounter();
            } else {
                exitAuction();
            }
        } else {
            exitAuction();
        }
    }

    private void enterAuction() {
        open = true;
        openTimer.resetCounter();
        updateTimer.resetCounter();
        buyTimer.resetCounter();
        storageManager.notifyAuctionEnter();
        serverInAuction = true;
        auctionHandler.clear();
        justEntered = false;
        spammingAh = false;
        waitingForAuctionOpen = false;
        storageManager.clearStorageCompleted();
        if (!storageManager.getPostStorageTimer().isReached(2000)) {
            storageManager.disableStartStorage();
        }
        cacheEnabledItems();
        if (leaveType.isSelected("Проверяющий")) {
            networkManager.notifyAuctionEnter();
        }
        if (leaveType.isSelected("Покупающий")) {
            networkManager.requestAuctionOpen();
        }
    }

    private void exitAuction() {
        if (open) {
            open = false;
            serverInAuction = false;
            auctionHandler.clear();
            if (leaveType.isSelected("Проверяющий")) {
                networkManager.notifyAuctionLeave();
            }
        }
    }

    private void handleBuyerMode(GenericContainerScreen screen, int syncId, List<Slot> slots) {
        long clientCount = networkManager.getClientInAuctionCount();

        if (networkManager.getQueueSize() > 30) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
            networkManager.clearQueues();
            return;
        }

        if (!storageManager.hasReachedMaxShulkers()) {
            BuyRequest request = networkManager.pollRequest();
            if (request != null) {
                auctionHandler.handleBuyRequest(mc, syncId, slots, request, networkManager);
            }
        }

        if (auctionHandler.shouldUpdate()) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
            networkManager.clearQueues();
        }

        if (updateTimer.isReached(timer2.getValue().longValue()) && serverInAuction &&
                clientCount > 0 && networkManager.isQueuesEmpty()) {
            auctionHandler.updateAuction(mc, syncId);
            networkManager.sendUpdateToClients();
            updateTimer.resetCounter();
        }
    }

    private void handleCheckerMode(List<Slot> slots) {
        List<Slot> bestSlots = auctionHandler.findMatchingSlots(slots, cachedEnabledItems);
        if (!bestSlots.isEmpty()) {
            auctionHandler.processBestSlots(bestSlots, networkManager);
            buyTimer.resetCounter();
        }
    }

    private void handleServerAutoSwitch() {
        if (leaveType.isSelected("Покупающий") && (bypassDelay.getEnabled() || bypassDelay1214.getEnabled())) {
            if (!serverManager.isInHub() && switchTimer.isReached(60000)) {
                serverManager.switchToNextServer(mc.player, networkManager, true);
            }
        }
    }
}
