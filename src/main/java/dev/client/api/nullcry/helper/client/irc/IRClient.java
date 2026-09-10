package dev.client.api.nullcry.helper.client.irc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import dev.client.LynxWave;
import dev.client.cmd.core.Cmd_Initializer;
import dev.client.cmd.core.util.CommandTextUtil;
import dev.client.modules.core.misc.IRC;
import dev.client.modules.core.render.Interface;
import dev.other.ircLib.java_websocket.client.WebSocketClient;
import dev.other.ircLib.java_websocket.handshake.ServerHandshake;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PartyMemberSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class IRClient extends WebSocketClient {
    public static String API_KEY = "LynxWave_secret_key_1488";

    public static String SENDER = LynxWave.getInstance().getUserProfile().getUser();

    private static final int CLIENT_VERSION = 2;

    private static final String PARTY_EVENT_PREFIX = "!party ";

    private static final Gson gson = new Gson();
    private int minRequiredVersion = 1;
    private int serverVersion = 1;
    private final URI serverUri;
    private boolean reconnecting = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<MuteEntry> mutedUsers = new ArrayList<>();
    private String currentMessageClientLabel = null;
    private String currentMessageServerName = null;
    private PrefixTest selectedPrefix = PrefixData.PREFIX_TESTS.length > 0 ? PrefixData.PREFIX_TESTS[0] : null;
    private String pendingJoinRequestKey = null;
    private String pendingJoinRequestPlayer = null;
    private String pendingJoinRequestPlayerMc = null;
    private long pendingJoinRequestTimestamp = 0;
    private static final long JOIN_REQUEST_TIMEOUT_MS = 60000;
    private final Object pendingRequestLock = new Object();
    private boolean presenceSynced = false;
    private final AtomicBoolean presenceHeartbeatScheduled = new AtomicBoolean(false);
    private String currentUsername = null;
    private String currentNickname = null;
    private boolean isModerator = false;
    private boolean isAdmin = false;
    private final IRCAutoLoginStorage autoLoginStorage = IRCAutoLoginStorage.getInstance();
    private IRCAutoLoginStorage.Credentials pendingCredentials = null;
    private boolean autoLoginAttempted = false;
    private Consumer<List<OnlineUser>> onlineUsersCallback = null;
    private List<OnlineUser> cachedOnlineUsers = Collections.emptyList();
    private long cachedOnlineUsersTimestamp = 0L;
    private static final long CACHE_TIMEOUT_MS = 30_000L;

    // ----- Friends & direct messages -----
    private final List<OnlineUser> friends = new ArrayList<>();
    private final List<String> incomingFriendRequests = new ArrayList<>();
    private final java.util.Map<String, java.util.Deque<DmEntry>> dmLogs = new java.util.concurrent.ConcurrentHashMap<>();
    /** Lower-cased DM key to the nickname's display casing. */
    private final java.util.Map<String, String> dmPeerNames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DM_LIMIT = 100;
    /** One direct message. Reply fields mirror {@link ChatLogEntry}; null when not a reply. */
    public record DmEntry(long time, boolean self, String message, String replyAuthor, String replyText) {
        public DmEntry(long time, boolean self, String message) {
            this(time, self, message, null, null);
        }

        public boolean isReply() {
            return replyAuthor != null && !replyAuthor.isBlank() && replyText != null;
        }
    }

    /**
     * One line of the global chat. {@code replyAuthor}/{@code replyText} are the
     * quoted message this one answers, or null when it is not a reply — the
     * server forwards them untouched, so older peers just omit them.
     */
    public record ChatLogEntry(long time, String author, String client, String prefix, String message,
                               String replyAuthor, String replyText) {
        public ChatLogEntry(long time, String author, String client, String prefix, String message) {
            this(time, author, client, prefix, message, null, null);
        }

        public boolean isReply() {
            return replyAuthor != null && !replyAuthor.isBlank() && replyText != null;
        }
    }
    private static final java.util.Deque<ChatLogEntry> CHAT_LOG = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final int CHAT_LOG_LIMIT = 200;
    public static java.util.List<ChatLogEntry> getChatLog() {
        return new java.util.ArrayList<>(CHAT_LOG);
    }

    public String getChatPrefix() {
        return Formatting.DARK_GRAY + "[" + themeColorCode() + "IRC" + Formatting.DARK_GRAY + "]" + Formatting.RESET;
    }

    private String themeColorCode() {
        Interface ui = Interface.INSTANCE;
        int color = ui != null ? ui.getMainColor() : 0xFF47A3FF;
        return toColorCode(color);
    }

    private String toColorCode(int argb) {
        int rgb = argb & 0xFFFFFF;
        String hex = String.format("%06X", rgb);
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            builder.append('§').append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    public boolean isLoggedIn() {
        return currentNickname != null;
    }

    public boolean isModerator() {
        return isModerator;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public String getCurrentNickname() {
        return currentNickname != null ? currentNickname : SENDER;
    }

    public URI getServerUri() {
        return serverUri;
    }

    private static class MuteEntry {
        String prefix;
        String author;
        long muteEndTime;

        MuteEntry(String prefix, String author, long durationSeconds) {
            this.prefix = prefix;
            this.author = author;
            this.muteEndTime = System.currentTimeMillis() + durationSeconds * 1000;
        }

        boolean isMuted() {
            return System.currentTimeMillis() < muteEndTime;
        }
    }

    public boolean removeMutedUser(String prefix, String author) {
        return mutedUsers.removeIf(mute -> mute.prefix.equals(prefix) && mute.author.equals(author));
    }

    public List<String> listMutedUsers() {
        List<String> result = new ArrayList<>();
        for (MuteEntry mute : mutedUsers) {
            long remainingSeconds = (mute.muteEndTime - System.currentTimeMillis()) / 1000;
            if (remainingSeconds > 0) {
                result.add(mute.author + " (" + mute.prefix + ") - " + remainingSeconds + " сек.");
            }
        }
        return result;
    }

    public static class OnlineUser {
        private final String nickname;
        private final String gameUsername;
        private final String clientLabel;
        private final String role;
        private final String server;
        private final boolean online;

        public OnlineUser(String nickname, String gameUsername, String clientLabel) {
            this(nickname, gameUsername, clientLabel, null, null, true);
        }

        public OnlineUser(String nickname, String gameUsername, String clientLabel,
                          String role, String server, boolean online) {
            this.nickname = nickname;
            this.gameUsername = gameUsername;
            this.clientLabel = clientLabel;
            this.role = role;
            this.server = server;
            this.online = online;
        }

        public String getNickname() {
            return nickname;
        }

        public String getGameUsername() {
            return gameUsername;
        }

        public String getClientLabel() {
            return clientLabel;
        }

        public String getRole() {
            return role;
        }

        public String getServer() {
            return server;
        }

        public boolean isOnline() {
            return online;
        }

        public String getDisplayName() {
            if (gameUsername != null && !gameUsername.isEmpty() && !gameUsername.equalsIgnoreCase(nickname)) {
                return gameUsername + " (" + nickname + ")";
            }
            return nickname;
        }
    }

    public synchronized void setSelectedPrefix(PrefixTest prefix) {
        this.selectedPrefix = prefix;
    }

    public synchronized PrefixTest getSelectedPrefix() {
        return selectedPrefix;
    }

    private static String formattingRole(String role) {
        return role;
    }

    private String format(String str) {
        str = str.replace("${yellow}", Formatting.YELLOW + "");
        str = str.replace("${white}", Formatting.WHITE + "");
        str = str.replace("${cyan}", Formatting.BLUE + "");
        str = str.replace("${aqua}", Formatting.AQUA + "");
        str = str.replace("${dark_gray}", Formatting.DARK_GRAY + "");
        str = str.replace("${gray}", Formatting.GRAY + "");
        str = str.replace("${red}", Formatting.RED + "");
        str = str.replace("${dark_red}", Formatting.DARK_RED + "");
        str = str.replace("${green}", Formatting.GREEN + "");
        return str;
    }

    public void print(String text, String nickname, String mainText, boolean userPrint) {
        text = text.replace("${yellow}", Formatting.YELLOW + "");
        text = text.replace("${white}", Formatting.WHITE + "");
        text = text.replace("${cyan}", Formatting.BLUE + "");
        text = text.replace("${aqua}", Formatting.AQUA + "");
        text = text.replace("${dark_gray}", Formatting.DARK_GRAY + "");
        text = text.replace("${gray}", Formatting.GRAY + "");
        text = text.replace("${red}", Formatting.RED + "");
        text = text.replace("${dark_red}", Formatting.DARK_RED + "");
        text = text.replace("${green}", Formatting.GREEN + "");

        MutableText message = Text.literal(text);
        MutableText hoverText = Text.empty();
        nickname = nickname.replace("${red}", "");
        if (userPrint) {
            hoverText = hoverText.styled(style -> style.withColor(Formatting.WHITE));
            hoverText.append(Text.literal("- Информация о сообщении: " + format(nickname) + "  \n").formatted(Formatting.WHITE));
            hoverText.append(Text.literal("Время отправки: ").formatted(Formatting.WHITE));
            hoverText.append(Text.literal(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n")
                    .formatted(Formatting.WHITE));
            hoverText.append(Text.literal("Длина сообщения: " + mainText.length()).formatted(Formatting.WHITE));
            if (currentMessageClientLabel != null) {
                hoverText.append(Text.literal("\n").formatted(Formatting.WHITE));
                hoverText.append(Text.literal("Клиент: ").formatted(Formatting.GOLD));
                hoverText.append(Text.literal(currentMessageClientLabel).formatted(Formatting.GOLD));
            }
            if (currentMessageServerName != null) {
                hoverText.append(Text.literal("\n").formatted(Formatting.WHITE));
                hoverText.append(Text.literal("Игровой сервер: ").formatted(Formatting.GREEN));
                hoverText.append(Text.literal(currentMessageServerName).formatted(Formatting.GREEN));
            }
            MutableText finalHoverText = hoverText;
            message = message.styled(style -> style.withHoverEvent(new HoverEvent.ShowText(finalHoverText)));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(message);
        }
        currentMessageClientLabel = null;
        currentMessageServerName = null;
    }

    public IRClient(URI serverUri) {
        super(serverUri);
        this.serverUri = serverUri;
        this.setConnectionLostTimeout(60);
        this.scheduler.scheduleAtFixedRate(this::checkConnection, 15L, 15L, TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(this::cleanExpiredMutes, 60L, 60L, TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(this::checkPendingRequestTimeout, 5L, 5L, TimeUnit.SECONDS);
    }

    private void checkConnection() {
        if (!this.isOpen() && !this.reconnecting) {
            this.reconnect();
        }
    }

    private void cleanExpiredMutes() {
        mutedUsers.removeIf(mute -> !mute.isMuted());
    }

    private void checkPendingRequestTimeout() {
        synchronized (pendingRequestLock) {
            if (pendingJoinRequestKey != null && pendingJoinRequestTimestamp > 0) {
                long elapsed = System.currentTimeMillis() - pendingJoinRequestTimestamp;
                if (elapsed > JOIN_REQUEST_TIMEOUT_MS) {
                    String display = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : pendingJoinRequestPlayer;
                    print(getChatPrefix() + "${yellow}Запрос на присоединение от " + display + " истёк.", "", "", false);
                    clearPendingJoinRequest();
                }
            }
        }
    }

    private void schedulePresenceAnnouncement() {
        presenceSynced = false;
        presenceHeartbeatScheduled.set(false);
        scheduler.schedule(() -> attemptPresenceAnnouncement(0, true), 1L, TimeUnit.SECONDS);
    }

    private void attemptPresenceAnnouncement(int attempt, boolean requestSnapshot) {
        if (!this.isOpen()) {
            return;
        }
        sendPresencePayload(requestSnapshot);
        if (presenceHeartbeatScheduled.compareAndSet(false, true)) {
            scheduler.schedule(this::sendPresenceHeartbeat, 45L, TimeUnit.SECONDS);
        }
        if (resolvePlayerName() == null && attempt < 6) {
            scheduler.schedule(() -> attemptPresenceAnnouncement(attempt + 1, false), 3L, TimeUnit.SECONDS);
        }
    }

    private void sendPresenceHeartbeat() {
        if (!this.isOpen()) {
            presenceHeartbeatScheduled.set(false);
            return;
        }
        sendPresencePayload(false);
        scheduler.schedule(this::sendPresenceHeartbeat, 45L, TimeUnit.SECONDS);
    }

    private void sendPresencePayload(boolean requestSnapshot) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "presence");
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        payload.addProperty("author", SENDER);
        String playerName = resolvePlayerName();
        if (playerName != null && !playerName.isEmpty()) {
            payload.addProperty("player", playerName);
        }
        IRCSettings settings = IRCSettings.get();
        settings.ensureLoaded();
        if (!settings.isHideMcServer() && !settings.isInvisibleMode()) {
            String serverName = resolveCurrentServerName();
            if (serverName != null && !serverName.isBlank()) {
                payload.addProperty("server", serverName);
            }
        }
        if (settings.isInvisibleMode()) {
            payload.addProperty("invisible", true);
        }
        if (requestSnapshot && !presenceSynced) {
            payload.addProperty("snapshot", true);
        }
        try {
            this.send(cypher(payload.toString()));
        } catch (Exception ignored) {
        }
    }

    private String resolvePlayerName() {
        try {
            MinecraftClient minecraft = MinecraftClient.getInstance();
            if (minecraft != null && minecraft.player != null) {
                return minecraft.player.getGameProfile().name();
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    public void onOpen(ServerHandshake handshake) {
        print(getChatPrefix() + "${white} Подключение установлено", "", "", false);
        System.out.println("Подключение установлено");
        this.reconnecting = false;
        schedulePresenceAnnouncement();
        autoLoginAttempted = false;
        attemptAutoLogin();
    }


    public void onClose(int code, String reason, boolean remote) {
        print(getChatPrefix() + "${white} Соединение закрыто, включите модуль IRC для переподключения", "", "", false);
        System.out.println("Соединение закрыто. Код: " + code + ", причина: " + reason);
        presenceHeartbeatScheduled.set(false);
        presenceSynced = false;
        autoLoginAttempted = false;
        pendingCredentials = null;
    }


    public void onError(Exception ex) {
        print(getChatPrefix() + "${white} Ошибка при подключении", "", "", false);
        System.out.println("Ошибка: " + ex.getMessage());
        ex.printStackTrace();
        presenceHeartbeatScheduled.set(false);
    }

    private void attemptAutoLogin() {
        if (autoLoginAttempted || !isOpen() || isLoggedIn()) {
            return;
        }
        IRCSettings settings = IRCSettings.get();
        settings.ensureLoaded();
        if (!settings.isAutoLogin()) {
            return;
        }
        autoLoginAttempted = true;
        autoLoginStorage.loadCredentials().ifPresent(credentials -> {
            String username = credentials.username();
            String password = credentials.password();
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return;
            }
            print(getChatPrefix() + "${yellow} Выполняется автоматический вход...", "", "", false);
            login(username, password);
        });
    }


    public void shutdown() {
        synchronized (pendingRequestLock) {
            clearPendingJoinRequest();
        }

        this.scheduler.shutdown();

        try {
            if (!this.scheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
            }
        } catch (InterruptedException var3) {
            this.scheduler.shutdownNow();
        }

        try {
            this.close();
        } catch (Exception var2) {
            print(getChatPrefix() + "${white} Ошибка отключения: " + var2.getMessage(), "", "", false);
            System.out.println("Ошибка при закрытии соединения: " + var2.getMessage());
        }
    }


    public void reconnect() {
        try {
            this.reconnecting = true;
            print(getChatPrefix() + "${white} Подключение к серверу...", "", "", false);
            if (this.isOpen()) {
                this.close();
            }

            IRClient newIRClient = new IRClient(this.serverUri);
            LynxWave.getInstance().setIRClient(newIRClient);
            newIRClient.connect();

            this.scheduler.shutdown();
        } catch (Exception var2) {
            print(getChatPrefix() + "${white} Ошибка при подключении: " + var2.getMessage(), "", "", false);
            System.out.println("Ошибка при переподключении: " + var2.getMessage());
            this.reconnecting = false;
        }
    }


    public void onMessage(String message) {
        handleMessage(cypher(message));
    }

    private void handleMessage(String jsonMessage) {
        try {
            JsonObject json = gson.fromJson(jsonMessage, JsonObject.class);
            if(!json.has("type"))
                return;

            String type = json.get("type").getAsString();
            switch(type) {
                case "text":
                    if(!json.has("message") || !json.has("client") || !json.has("author")) return;
                    String content = json.get("message").getAsString();
                    String author = json.get("author").getAsString();
                    String client = json.get("client").getAsString();
                    ClientLabelRegistry labelRegistry = ClientLabelRegistry.getInstance();
                    labelRegistry.assignLabel(author, client);
                    if (author.equalsIgnoreCase(SENDER)) {
                        labelRegistry.setSelfLabel(client);
                    }
                    String prefix = json.get("prefix").getAsString();
                    if (content.startsWith(PARTY_EVENT_PREFIX)) {
                        handlePartyPayload(author, client, prefix, content.substring(PARTY_EVENT_PREFIX.length()));
                        return;
                    }
                    String replyAuthor = optString(json, "reply_author");
                    String replyText = optString(json, "reply_text");
                    CHAT_LOG.addLast(new ChatLogEntry(System.currentTimeMillis(), author, client, prefix, content,
                            replyAuthor, replyText));
                    while (CHAT_LOG.size() > CHAT_LOG_LIMIT) CHAT_LOG.pollFirst();
                    notifyText(author, content);
                    currentMessageClientLabel = client;
                    currentMessageServerName = json.has("server") ? json.get("server").getAsString() : null;
                    String darkGray = Formatting.DARK_GRAY.toString();
                    String white = Formatting.WHITE.toString();
                    String gray = Formatting.GRAY.toString();
                    String messageText = darkGray + "[" + themeColorCode() + client + darkGray + "] "
                            + darkGray + "[" + prefix + darkGray + "] "
                            + white + author + " " + gray + " " + darkGray + "-> "
                            + white + content + Formatting.RESET;
                    print(messageText, author, content, true);
                    break;
                case "broadcast":
                    handleBroadcastMessage(json);
                    break;
                case "client_presence":
                    handleClientPresence(json);
                    break;
                case "minigame":
                    if(!json.has("question") || !json.has("message")) return;
                    String question = json.get("question").getAsString();
                    String minigameMessage = json.get("message").getAsString();
                    print(getChatPrefix() + "${white} " + minigameMessage, "", question, false);
                    break;
                case "minigame_result":
                    if(!json.has("message") || !json.has("author")) return;
                    String resultMessage = json.get("message").getAsString();
                    String resultAuthor = json.get("author").getAsString();
                    print(getChatPrefix() + "${white} " + resultMessage, resultAuthor, resultMessage, false);
                    break;
                case "party_pos":
                    handlePartyPositionsBroadcast(json);
                    break;
                case "party_join_request":
                    handlePartyJoinRequest(json);
                    break;
                case "online_users_list":
                    handleOnlineUsersList(json);
                    break;
                case "system":
                    if(!json.has("message")) return;
                    String systemMsg = json.get("message").getAsString();
                    switch (systemMsg) {
                        case "CONNECTED":
                            print(getChatPrefix() + "${green} Успешное подключение к серверу!", "", "", false);
                            break;
                        case "DECLINED":
                            print(getChatPrefix() + "${red} Сервер отклонил ваше подключение! Попробуйте выключить ВПН, если он включен.", "", "", false);
                            break;
                        case "MESSAGE_FILTERED":
                            print(getChatPrefix() + "${red} Сообщение содержит запрещённый контент!", "", "", false);
                            break;
                        case "INVALID_API_KEY":
                            print(getChatPrefix() + "${red} Ключ не прошел проверку на сервере, подключение отказано", "", "", false);
                            break;
                        case "WAIT":
                            print(getChatPrefix() + "${yellow} Подождите перед отправкой следующего сообщения!", "", "", false);
                            break;
                        case "MESSAGE_TOO_LONG":
                            print(getChatPrefix() + "${red} Сообщение слишком длинное!", "", "", false);
                            break;
                        case "AUTHENTICATION_REQUIRED":
                            print(getChatPrefix() + "${red} Вы должны войти в аккаунт для использования IRC!", "", "", false);
                            break;
                        default:
                            if (systemMsg.startsWith("REGISTER_SUCCESS:")) {
                                print(getChatPrefix() + "${green} " + systemMsg.substring("REGISTER_SUCCESS:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("REGISTER_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("REGISTER_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("LOGIN_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("LOGIN_FAILED:".length()).trim(), "", "", false);
                                autoLoginStorage.clear();
                                pendingCredentials = null;
                                autoLoginAttempted = false;
                            } else if (systemMsg.startsWith("MUTED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("MUTED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("PERMISSION_DENIED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("PERMISSION_DENIED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("MUTE_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("MUTE_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("UNMUTE_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("UNMUTE_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("SETMODERATOR_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("SETMODERATOR_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("SETADMIN_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("SETADMIN_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("RAFFLE_JOIN_SUCCESS:")) {
                                print(getChatPrefix() + "${green} " + systemMsg.substring("RAFFLE_JOIN_SUCCESS:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("RAFFLE_JOIN_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("RAFFLE_JOIN_FAILED:".length()).trim(), "", "", false);
                            } else if (systemMsg.startsWith("VERSION_REQUIRED:")) {
                                print(getChatPrefix() + "${red}═══════════════════════════════════════", "", "", false);
                                print(getChatPrefix() + "${red} " + systemMsg.substring("VERSION_REQUIRED:".length()).trim(), "", "", false);
                                print(getChatPrefix() + "${red}═══════════════════════════════════════", "", "", false);
                            } else if (systemMsg.startsWith("PRIVATE_MESSAGE_FAILED:")) {
                                print(getChatPrefix() + "${red} " + systemMsg.substring("PRIVATE_MESSAGE_FAILED:".length()).trim(), "", "", false);
                            }
                            break;
                    }
                    break;
                case "login_success":
                    if (json.has("user")) {
                        JsonObject user = json.getAsJsonObject("user");
                        currentUsername = extractString(user, "username");
                        currentNickname = extractString(user, "nickname");
                        isModerator = user.has("isModerator") && user.get("isModerator").getAsBoolean();
                        isAdmin = user.has("isAdmin") && user.get("isAdmin").getAsBoolean();
                        String loginMsg = json.has("message") ? json.get("message").getAsString() : "Вход выполнен успешно";
                        print(getChatPrefix() + "${green} " + loginMsg, "", "", false);
                        if (currentNickname != null) {
                            print(getChatPrefix() + "${green} Ваш никнейм в IRC: " + currentNickname, "", "", false);
                        }
                        if (isAdmin) {
                            print(getChatPrefix() + "${red} У вас есть права администратора", "", "", false);
                        } else if (isModerator) {
                            print(getChatPrefix() + "${yellow} У вас есть права модератора", "", "", false);
                        }
                        if (pendingCredentials != null) {
                            autoLoginStorage.saveCredentials(pendingCredentials.username(), pendingCredentials.password());
                            pendingCredentials = null;
                        }
                        requestFriends();
                    }
                    break;
                case "moderator_response":
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        String msg = json.has("message") ? json.get("message").getAsString() : "Команда выполнена";
                        print(getChatPrefix() + "${green} " + msg, "", "", false);
                    }
                    break;
                case "user_muted":
                    if (json.has("nickname")) {
                        String mutedNick = json.get("nickname").getAsString();
                        String mutedBy = json.has("mutedBy") ? json.get("mutedBy").getAsString() : "модератор";
                        int duration = json.has("durationMinutes") ? json.get("durationMinutes").getAsInt() : 0;
                        print(getChatPrefix() + "${yellow} Пользователь " + mutedNick + " замьючен на " + duration + " минут модератором " + mutedBy, "", "", false);
                    }
                    break;
                case "user_unmuted":
                    if (json.has("nickname")) {
                        String unmutedNick = json.get("nickname").getAsString();
                        String unmutedBy = json.has("unmutedBy") ? json.get("unmutedBy").getAsString() : "модератор";
                        print(getChatPrefix() + "${green} С пользователя " + unmutedNick + " снят мут модератором " + unmutedBy, "", "", false);
                    }
                    break;
                case "mass_mute":
                    if (json.has("mutedCount")) {
                        int mutedCount = json.get("mutedCount").getAsInt();
                        int duration = json.has("durationMinutes") ? json.get("durationMinutes").getAsInt() : 0;
                        String reason = json.has("reason") ? json.get("reason").getAsString() : null;
                        print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
                        print(getChatPrefix() + "${red} Массовый мут", "", "", false);
                        print(getChatPrefix() + "${yellow} Замучено пользователей: " + mutedCount, "", "", false);
                        print(getChatPrefix() + "${yellow} Длительность: " + duration + " минут", "", "", false);
                        if (reason != null && !reason.isEmpty()) {
                            print(getChatPrefix() + "${yellow} Причина: " + reason, "", "", false);
                        }
                        print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
                    }
                    break;
                case "mass_unmute":
                    if (json.has("unmutedCount")) {
                        int unmutedCount = json.get("unmutedCount").getAsInt();
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                        print(getChatPrefix() + "${green} Массовый размут", "", "", false);
                        print(getChatPrefix() + "${green} Размучено пользователей: " + unmutedCount, "", "", false);
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                    }
                    break;
                case "raffle_start":
                    if (json.has("message")) {
                        String raffleMessage = json.get("message").getAsString();
                        String prize = json.has("prize") ? json.get("prize").getAsString() : "Крутая награда!";
                        int duration = json.has("durationSeconds") ? json.get("durationSeconds").getAsInt() : 60;
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                        print(getChatPrefix() + "${green}" + raffleMessage, "", "", false);
                        print(getChatPrefix() + "${yellow} Приз: " + prize, "", "", false);
                        print(getChatPrefix() + "${yellow} Длительность: " + duration + " секунд", "", "", false);
                        print(getChatPrefix() + "${yellow} Используйте: ${aqua}.irc raffle join${yellow} для участия", "", "", false);
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                    }
                    break;
                case "raffle_end":
                    if (json.has("message")) {
                        String endMessage = json.get("message").getAsString();
                        String winner = json.has("winner") && !json.get("winner").isJsonNull() ? json.get("winner").getAsString() : null;
                        String prize = json.has("prize") ? json.get("prize").getAsString() : "Крутая награда!";
                        int participantsCount = json.has("participantsCount") ? json.get("participantsCount").getAsInt() : 0;
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                        print(getChatPrefix() + "${green}" + endMessage, "", "", false);
                        if (winner != null) {
                            print(getChatPrefix() + "${yellow}🎉 Победитель: ${green}" + winner + "${yellow}!", "", "", false);
                            print(getChatPrefix() + "${yellow} Приз: " + prize, "", "", false);
                        }
                        print(getChatPrefix() + "${yellow} Участников: " + participantsCount, "", "", false);
                        print(getChatPrefix() + "${green}═══════════════════════════════════════", "", "", false);
                    }
                    break;
                case "version_info":
                    if (json.has("serverVersion")) {
                        serverVersion = json.get("serverVersion").getAsInt();
                    }
                    if (json.has("minClientVersion")) {
                        minRequiredVersion = json.get("minClientVersion").getAsInt();
                    }
                    if (CLIENT_VERSION < minRequiredVersion) {
                        print(getChatPrefix() + "${red}═══════════════════════════════════════", "", "", false);
                        print(getChatPrefix() + "${red} Версия клиента устарела!", "", "", false);
                        print(getChatPrefix() + "${yellow} Требуется версия: " + minRequiredVersion, "", "", false);
                        print(getChatPrefix() + "${yellow} Ваша версия: " + CLIENT_VERSION, "", "", false);
                        print(getChatPrefix() + "${red} Пожалуйста, обновите клиент!", "", "", false);
                        print(getChatPrefix() + "${red}═══════════════════════════════════════", "", "", false);
                        this.close();
                        return;
                    }
                    break;
                case "private_message":
                    if (!json.has("message") || !json.has("author")) return;
                    String pmContent = json.get("message").getAsString();
                    String pmAuthor = json.get("author").getAsString();
                    recordDm(pmAuthor, false, pmContent,
                            optString(json, "reply_author"), optString(json, "reply_text"));
                    print(getChatPrefix() + "${aqua}[PM] ${yellow}" + pmAuthor + "${gray}: ${white}" + pmContent, pmAuthor, pmContent, false);
                    notifyPrivate(pmAuthor, pmContent);
                    break;
                case "friends_list":
                    handleFriendsList(json);
                    break;
                case "friend_request": {
                    String from = extractString(json, "author");
                    if (from != null && !from.isBlank()) {
                        synchronized (friends) {
                            if (incomingFriendRequests.stream().noneMatch(n -> n.equalsIgnoreCase(from))) {
                                incomingFriendRequests.add(from);
                            }
                        }
                        print(getChatPrefix() + "${green}" + from + "${gray} хочет добавить вас в друзья. Откройте вкладку «Друзья».", "", "", false);
                        notifyFriendRequest(from);
                    }
                    break;
                }
                case "friend_added": {
                    String n = extractString(json, "nickname");
                    if (n != null && !n.isBlank()) {
                        synchronized (friends) {
                            incomingFriendRequests.removeIf(x -> x.equalsIgnoreCase(n));
                            if (friends.stream().noneMatch(f -> f.getNickname() != null && f.getNickname().equalsIgnoreCase(n))) {
                                friends.add(new OnlineUser(n, null, "LynxWave", "user", null, false));
                            }
                        }
                        print(getChatPrefix() + "${green}" + n + "${gray} теперь у вас в друзьях.", "", "", false);
                        requestFriends();
                    }
                    break;
                }
                case "friend_removed": {
                    String n = extractString(json, "nickname");
                    if (n != null && !n.isBlank()) {
                        synchronized (friends) {
                            friends.removeIf(f -> f.getNickname() != null && f.getNickname().equalsIgnoreCase(n));
                        }
                        print(getChatPrefix() + "${yellow}" + n + "${gray} удалил вас из друзей.", "", "", false);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[WS] Failed to process message: " + e.getMessage());
        }
    }

    private void handleClientPresence(JsonObject payload) {
        if (payload == null || !payload.has("entries") || !payload.get("entries").isJsonArray()) {
            return;
        }

        ClientLabelRegistry registry = ClientLabelRegistry.getInstance();
        String localPlayerName = resolvePlayerName();
        JsonArray entries = payload.getAsJsonArray("entries");

        List<OnlineUser> updatedUsers;
        synchronized (this) {
            updatedUsers = cachedOnlineUsers.isEmpty() ? new ArrayList<>() : new ArrayList<>(cachedOnlineUsers);
        }
        boolean changed = false;

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String status = extractString(entry, "status");
            boolean offline = status != null && status.equalsIgnoreCase("offline");
            String label = extractString(entry, "label");
            String author = extractString(entry, "author");
            String player = extractString(entry, "player");

            if ((label == null || label.isEmpty()) && !offline) {
                continue;
            }

            if (offline || label == null || label.isEmpty()) {
                if (author != null) {
                    registry.removeLabel(author);
                }
                if (player != null) {
                    registry.removeLabel(player);
                }
                changed |= removeCachedOnlineUser(updatedUsers, author, player);
                continue;
            }

            if (author != null) {
                registry.assignLabel(author, label);
            }
            if (player != null) {
                registry.assignLabel(player, label);
            }

            changed |= upsertCachedOnlineUser(updatedUsers, author, player, label);

            if (author != null && author.equalsIgnoreCase(SENDER)) {
                registry.setSelfLabel(label);
                presenceSynced = true;
            } else if (player != null && localPlayerName != null && localPlayerName.equalsIgnoreCase(player)) {
                registry.setSelfLabel(label);
                presenceSynced = true;
            }
        }

        if (changed) {
            synchronized (this) {
                cachedOnlineUsers = updatedUsers;
                cachedOnlineUsersTimestamp = System.currentTimeMillis();
            }
        }

        if (payload.has("full")) {
            try {
                if (payload.get("full").getAsBoolean()) {
                    presenceSynced = true;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean removeCachedOnlineUser(List<OnlineUser> users, String ircName, String gameName) {
        if (users == null || users.isEmpty()) {
            return false;
        }
        boolean removed = false;
        Iterator<OnlineUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            OnlineUser user = iterator.next();
            if (matchesUser(user, ircName) || matchesUser(user, gameName)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    private boolean upsertCachedOnlineUser(List<OnlineUser> users, String ircName, String gameName, String label) {
        if (users == null) {
            return false;
        }
        String nickname = (ircName != null && !ircName.isBlank()) ? ircName : gameName;
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        for (int i = 0; i < users.size(); i++) {
            OnlineUser existing = users.get(i);
            if (matchesUser(existing, nickname) || matchesUser(existing, gameName)) {
                if (Objects.equals(existing.getNickname(), nickname)
                        && Objects.equals(existing.getGameUsername(), gameName)
                        && Objects.equals(existing.getClientLabel(), label)) {
                    return false;
                }
                users.set(i, new OnlineUser(nickname, gameName, label));
                return true;
            }
        }
        users.add(new OnlineUser(nickname, gameName, label));
        return true;
    }

    private boolean matchesUser(OnlineUser user, String candidate) {
        if (user == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        if (user.getNickname() != null && user.getNickname().equalsIgnoreCase(candidate)) {
            return true;
        }
        return user.getGameUsername() != null && user.getGameUsername().equalsIgnoreCase(candidate);
    }

    private void handlePartyPayload(String author, String clientLabel, String prefixValue, String encoded) {
        currentMessageClientLabel = null;
        currentMessageServerName = null;
        try {
            String jsonData = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            JsonObject payload = gson.fromJson(jsonData, JsonObject.class);
            if (payload == null || !payload.has("action")) {
                return;
            }

            String action = payload.get("action").getAsString();
            JsonObject data = payload.has("data") && payload.get("data").isJsonObject()
                    ? payload.getAsJsonObject("data")
                    : new JsonObject();

            PartyManager partyManager = PartyManager.getInstance();
            String self = SENDER;

            switch (action) {
                case "create" -> {
                    String key = extractString(data, "key");
                    String leader = extractString(data, "leader");
                    String leaderMc = extractString(data, "leader_mc");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!self.equalsIgnoreCase(author)) {
                        String leaderName = leaderMc != null ? leaderMc : leader != null ? leader : author;
                        print(getChatPrefix() + "${yellow}" + leaderName + "${gray} создал пати.", "", "", false);
                    }
                }
                case "join_request" -> {

                }
                case "invite" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    String target = extractString(data, "target");
                    String targetMc = extractString(data, "target_mc");
                    String inviter = extractString(data, "inviter");
                    String inviterMc = extractString(data, "inviter_mc");
                    String inviteeLabel = target != null && !target.isEmpty()
                            ? target
                            : targetMc != null && !targetMc.isEmpty() ? targetMc : "unknown";
                    String selfNickname = getCurrentNickname();
                    String selfGameName = resolvePlayerName();
                    boolean targeted = false;
                    if (target != null && !target.isEmpty()) {
                        targeted = target.equalsIgnoreCase(self) || (selfNickname != null && target.equalsIgnoreCase(selfNickname));
                    }
                    if (!targeted && targetMc != null && !targetMc.isEmpty() && selfGameName != null) {
                        targeted = targetMc.equalsIgnoreCase(selfGameName);
                    }
                    if (!targeted) {
                        if (self.equalsIgnoreCase(author)) {
                            print(getChatPrefix() + "${green} Приглашение отправлено игроку ${white}" + inviteeLabel, "", "", false);
                        }
                        return;
                    }
                    if (PartyManager.getInstance().hasParty()) {
                        print(getChatPrefix() + "${yellow} Вы уже состоите в пати. Сначала покиньте текущую.", "", "", false);
                        return;
                    }
                    String inviterDisplay = inviterMc != null && !inviterMc.isEmpty()
                            ? inviterMc
                            : inviter != null && !inviter.isEmpty() ? inviter : author;
                    print(getChatPrefix() + "${green}" + inviterDisplay + "${gray} приглашает вас в пати.", "", "", false);

                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.inGameHud != null) {
                        Cmd_Initializer initializer = LynxWave.getInstance().getCmdInitializer();
                        prefixValue = ".";
                        if (initializer != null && initializer.getCommandDispatcher() != null) {
                            prefixValue = initializer.getCommandDispatcher().getPrefix().get();
                        }
                        MutableText inviteLine = Text.literal(getChatPrefix() + "§7Быстрое присоединение: ");
                        inviteLine.append(CommandTextUtil.bracketedButton(
                                "Вступить",
                                Formatting.AQUA,
                                ClickEvent.Action.SUGGEST_COMMAND,
                                prefixValue + "irc party join " + key,
                                "§bПрисоединиться к пати"
                        ));
                        client.inGameHud.getChatHud().addMessage(inviteLine);
                    }
                }
                case "join_accept" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    List<PartyMemberSnapshot> members = extractMemberSnapshots(data);
                    String leader = extractString(data, "leader");
                    String leaderMc = extractString(data, "leader_mc");
                    boolean pending = partyManager.isPendingKey(key);
                    boolean matches = partyManager.matchesKey(key);
                    if (!pending && !matches) {
                        return;
                    }
                    boolean isLeaderSelf = leader != null && leader.equalsIgnoreCase(self);
                    partyManager.applyRemoteState(leader, leaderMc, key, members, isLeaderSelf);
                    if (pending) {
                        partyManager.clearPendingKey();
                    }
                    String leaderDisplay = leaderMc != null ? leaderMc : leader;
                    if (isLeaderSelf) {
                        print(getChatPrefix() + "${gray} Состав пати обновлён (" + members.size() + ").", "", "", false);
                    } else if (pending || self.equalsIgnoreCase(author)) {
                        print(getChatPrefix() + "${green} Вы присоединились к пати " + (leaderDisplay != null ? leaderDisplay : "") + ".", "", "", false);
                    } else {
                        print(getChatPrefix() + "${gray} Состав пати обновлён.", "", "", false);
                    }
                }
                case "leave" -> {
                    String key = extractString(data, "key");
                    String player = extractString(data, "player");
                    String playerMc = extractString(data, "player_mc");
                    if (key == null || player == null) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    boolean selfLeave = self.equalsIgnoreCase(player);
                    boolean removed = partyManager.removeMember(player, playerMc);

                    if (selfLeave && partyManager.isLeader()) {
                        synchronized (pendingRequestLock) {
                            if (pendingJoinRequestKey != null && pendingJoinRequestKey.equals(key)) {
                                clearPendingJoinRequest();
                            }
                        }
                    }

                    if (selfLeave) {
                        partyManager.clear();
                        if (!self.equalsIgnoreCase(author)) {
                            print(getChatPrefix() + "${yellow} Вы покинули пати.", "", "", false);
                        }
                    } else if (removed) {
                        String display = playerMc != null ? playerMc : player;
                        print(getChatPrefix() + "${gray}" + display + " покинул пати.", "", "", false);
                    }
                }
                case "kick" -> {
                    String key = extractString(data, "key");
                    String player = extractString(data, "player");
                    String playerMc = extractString(data, "player_mc");
                    if (key == null || player == null) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    boolean selfKicked = self.equalsIgnoreCase(player);
                    partyManager.removeMember(player, playerMc);
                    if (selfKicked) {
                        partyManager.clear();
                        if (!self.equalsIgnoreCase(author)) {
                            print(getChatPrefix() + "${red} Вас исключили из пати.", "", "", false);
                        }
                    } else {
                        String display = playerMc != null ? playerMc : player;
                        print(getChatPrefix() + "${gray}" + display + " исключён из пати.", "", "", false);
                    }
                }
                case "disband" -> {
                    String key = extractString(data, "key");
                    String leader = extractString(data, "leader");
                    String leaderMc = extractString(data, "leader_mc");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    synchronized (pendingRequestLock) {
                        if (pendingJoinRequestKey != null && pendingJoinRequestKey.equals(key)) {
                            clearPendingJoinRequest();
                        }
                    }
                    partyManager.clear();
                    if (!self.equalsIgnoreCase(author)) {
                        String leaderName = leaderMc != null ? leaderMc : leader != null ? leader : author;
                        print(getChatPrefix() + "${yellow} Пати лидера " + leaderName + " распущена.", "", "", false);
                    }
                }
                case "update" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    List<PartyMemberSnapshot> members = extractMemberSnapshots(data);
                    partyManager.replaceMembers(members);
                }
                case "chat" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    String text = extractString(data, "message");
                    if (text == null || text.isEmpty()) {
                        return;
                    }
                    String sender = extractString(data, "sender");
                    String senderMc = extractString(data, "sender_mc");
                    if (sender == null || sender.isEmpty()) {
                        sender = author;
                    }
                    String displayName = senderMc != null && !senderMc.isEmpty()
                            ? senderMc
                            : sender != null && !sender.isEmpty() ? sender : "unknown";
                    partyManager.addOrUpdateMember(sender, senderMc);
                    boolean selfMessage = sender != null && sender.equalsIgnoreCase(self);
                    String nameColor = selfMessage ? "${green}" : "${white}";
                    print(getChatPrefix() + "${gray}[${aqua}Party${gray}] " + nameColor + displayName + "${gray}: ${white}" + text,
                            "", "", false);
                }
                case "marker" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    String owner = extractString(data, "owner");
                    String ownerMc = extractString(data, "owner_mc");
                    if ((owner == null || owner.isEmpty()) && (ownerMc == null || ownerMc.isEmpty())) {
                        owner = author;
                    }
                    Double x = extractDouble(data, "x");
                    Double y = extractDouble(data, "y");
                    Double z = extractDouble(data, "z");
                    if (x == null || y == null || z == null) {
                        return;
                    }
                    long timestamp = extractLong(data, "timestamp", System.currentTimeMillis());
                    partyManager.updateMarker(owner, ownerMc, x, y, z, timestamp);
                    partyManager.addOrUpdateMember(owner, ownerMc);
                }
                case "pos" -> {
                    String key = extractString(data, "key");
                    if (key == null || key.isEmpty()) {
                        return;
                    }
                    if (!partyManager.matchesKey(key)) {
                        return;
                    }
                    String player = extractString(data, "player");
                    String playerMc = extractString(data, "player_mc");
                    Double x = extractDouble(data, "x");
                    Double y = extractDouble(data, "y");
                    Double z = extractDouble(data, "z");
                    if (x == null || y == null || z == null) {
                        return;
                    }
                    partyManager.addOrUpdateMember(player, playerMc);
                    partyManager.updatePosition(player, playerMc, x, y, z, System.currentTimeMillis());
                    IRC module = IRC.INSTANCE;
                    if (module != null) {
                        module.handleIncomingPartyCoordinates(player, playerMc, x, y, z);
                    }
                }
                case "party_pos" -> {
                    handlePartyPositionsBroadcast(payload);
                }
            }
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            System.err.println("[IRC] Failed to decode party payload: " + e.getMessage());
        }
    }

    private String resolveCurrentServerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        try {
            if (client.getCurrentServerEntry() != null) {
                String address = client.getCurrentServerEntry().address;
                if (address != null && !address.isBlank()) {
                    return address;
                }
            }
            if (client.isInSingleplayer()) {
                return "Singleplayer";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractString(JsonObject data, String memberName) {
        if (data == null || !data.has(memberName)) {
            return null;
        }
        JsonElement element = data.get(memberName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
         String value = element.getAsString();
        return value != null ? value.trim() : null;
    }

    private Double extractDouble(JsonObject data, String key) {
        if (data == null || !data.has(key)) {
            return null;
        }
        try {
            return data.get(key).getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long extractLong(JsonObject data, String key, long fallback) {
        if (data == null || !data.has(key)) {
            return fallback;
        }
        try {
            return data.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<PartyMemberSnapshot> extractMemberSnapshots(JsonObject data) {
        if (data == null || !data.has("members") || !data.get("members").isJsonArray()) {
            return Collections.emptyList();
        }
        List<PartyMemberSnapshot> members = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("members");
        for (JsonElement element : array) {
            PartyMemberSnapshot snapshot = parseMemberElement(element);
            if (snapshot != null) {
                members.add(snapshot);
            }
        }
        return members;
    }

    private JsonArray toMemberJsonArray(List<PartyMemberSnapshot> members) {
        JsonArray array = new JsonArray();
        if (members == null) {
            return array;
        }
        for (PartyMemberSnapshot member : members) {
            if (member == null) {
                continue;
            }
            String irc = member.getIrcName();
            String mc = member.getGameName();
            if (irc == null && mc == null) {
                continue;
            }
            StringBuilder value = new StringBuilder();
            if (irc != null) {
                value.append(irc);
            }
            value.append('|');
            if (mc != null) {
                value.append(mc);
            }
            array.add(value.toString());
        }
        return array;
    }

    private PartyMemberSnapshot parseMemberElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value != null) {
                int separator = value.indexOf('|');
                if (separator >= 0) {
                    String irc = separator > 0 ? value.substring(0, separator) : null;
                    String mc = separator < value.length() - 1 ? value.substring(separator + 1) : null;
                    return PartyMemberSnapshot.of(irc, mc);
                }
            }
            return PartyMemberSnapshot.of(value, value);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String irc = extractString(object, "irc");
            String mc = extractString(object, "mc");
            if (irc == null) {
                irc = extractString(object, "i");
            }
            if (mc == null) {
                mc = extractString(object, "m");
            }
            if (irc == null) {
                irc = extractString(object, "name");
            }
            if (mc == null) {
                mc = extractString(object, "display");
            }
            return PartyMemberSnapshot.of(irc, mc);
        }
        return null;
    }

    /** Collapses a quoted message to a single capped line for the wire. */
    private static String flattenQuote(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() > 160 ? flat.substring(0, 160) : flat;
    }

    /** Reads an optional string field, treating absent/null/blank alike as null. */
    private static String optString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return null;
        try {
            String value = json.get(key).getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    public void sendMessage(String message) {
        sendMessage(message, null, null);
    }

    /**
     * Sends a chat message, optionally quoting {@code replyAuthor}'s {@code replyText}.
     * The quote travels as two extra fields the server forwards verbatim.
     */
    public void sendMessage(String message, String replyAuthor, String replyText) {
        System.out.println("[IRC] sendMessage called: " + message);
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Не подключено к серверу IRC", "", "", false);
            System.out.println("[IRC] sendMessage aborted: socket not open");
            return;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Войдите в аккаунт, чтобы писать в чат", "", "", false);
            System.out.println("[IRC] sendMessage aborted: not logged in");
            return;
        }
        for (MuteEntry mute : mutedUsers) {
            if (mute.author.equals(SENDER) && mute.isMuted()) {
                print(getChatPrefix() + "${red} Вы замучены до " + (mute.muteEndTime - System.currentTimeMillis()) / 1000 + " секунд!", "", "", false);
                return;
            }
        }
        PrefixTest activePrefix = getSelectedPrefix();
        String currentPrefix = activePrefix != null ? activePrefix.value() : "Default";

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "text");
        payload.addProperty("message", message);

        payload.addProperty("author", currentNickname != null ? currentNickname : SENDER);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        payload.addProperty("prefix", currentPrefix);
        if (replyAuthor != null && !replyAuthor.isBlank() && replyText != null && !replyText.isBlank()) {
            payload.addProperty("reply_author", replyAuthor);
            payload.addProperty("reply_text", flattenQuote(replyText));
        }
        String serverName = resolveCurrentServerName();
        if (serverName != null && !serverName.isBlank()) {
            payload.addProperty("server", serverName);
        }
        try {
            this.send(cypher(payload.toString()));
            System.out.println("[IRC] sendMessage sent ok");
        } catch (Exception e) {
            System.out.println("[IRC] sendMessage send error: " + e);
            e.printStackTrace();
            print(getChatPrefix() + "${red} Ошибка отправки: " + e.getMessage(), "", "", false);
        }
    }

    public void sendPrivateMessage(String targetNickname, String message) {
        sendPrivateMessage(targetNickname, message, null, null);
    }

    /** Sends a DM, optionally quoting {@code replyAuthor}'s {@code replyText}. */
    public void sendPrivateMessage(String targetNickname, String message, String replyAuthor, String replyText) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Вы должны войти в аккаунт для отправки личных сообщений!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "private_message");
        payload.addProperty("target", targetNickname);
        payload.addProperty("message", message);
        payload.addProperty("author", currentNickname != null ? currentNickname : SENDER);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        boolean quoting = replyAuthor != null && !replyAuthor.isBlank() && replyText != null && !replyText.isBlank();
        String quote = quoting ? flattenQuote(replyText) : null;
        if (quoting) {
            payload.addProperty("reply_author", replyAuthor);
            payload.addProperty("reply_text", quote);
        }
        this.send(cypher(payload.toString()));
        recordDm(targetNickname, true, message, quoting ? replyAuthor : null, quote);
    }

    public void register(String username, String password) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        String gameUsername = resolvePlayerName();
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "register");
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        if (gameUsername != null) {
            payload.addProperty("gameUsername", gameUsername);
        }
        this.send(cypher(payload.toString()));
    }

    public void login(String username, String password) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        pendingCredentials = new IRCAutoLoginStorage.Credentials(username, password);
        String gameUsername = resolvePlayerName();
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "login");
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        if (gameUsername != null) {
            payload.addProperty("gameUsername", gameUsername);
        }
        this.send(cypher(payload.toString()));
    }

    public void logout() {
        currentUsername = null;
        currentNickname = null;
        isModerator = false;
        isAdmin = false;
        pendingCredentials = null;
        synchronized (this) {
            cachedOnlineUsers = Collections.emptyList();
            cachedOnlineUsersTimestamp = 0L;
            onlineUsersCallback = null;
        }
        synchronized (friends) {
            friends.clear();
            incomingFriendRequests.clear();
        }
        dmLogs.clear();
        PartyManager.getInstance().clear();
        print(getChatPrefix() + "${yellow} Вы вышли из аккаунта", "", "", false);
    }

    public void muteUser(String nickname, int durationMinutes, String reason) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "moderator_command");
        payload.addProperty("command", "mute");
        payload.addProperty("target", nickname);
        payload.addProperty("duration", durationMinutes);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        if (reason != null && !reason.isEmpty()) {
            payload.addProperty("reason", reason);
        }
        this.send(cypher(payload.toString()));
    }

    public void unmuteUser(String nickname) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "moderator_command");
        payload.addProperty("command", "unmute");
        payload.addProperty("target", nickname);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void setModerator(String username, boolean isModerator) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "moderator_command");
        payload.addProperty("command", "setmoderator");
        payload.addProperty("target", username);
        payload.addProperty("isModerator", isModerator);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void setAdmin(String username, boolean isAdmin) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "moderator_command");
        payload.addProperty("command", "setadmin");
        payload.addProperty("target", username);
        payload.addProperty("isAdmin", isAdmin);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void joinRaffle() {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "raffle_command");
        payload.addProperty("command", "join");
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }


    public void sendPartyEvent(String action, JsonObject data) {
        if (action == null || action.isBlank()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        String sender = getCurrentNickname();
        if (sender == null || sender.isBlank()) {
            sender = SENDER;
        }
        payload.addProperty("sender", sender);
        if (data != null) {
            payload.add("data", data);
        }

        String raw = gson.toJson(payload);
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        sendMessage(PARTY_EVENT_PREFIX + encoded);
    }

    public void sendPartyInvite(String targetNickname, String targetGameName) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Авторизуйтесь для отправки приглашений", "", "", false);
            return;
        }
        PartyManager partyManager = PartyManager.getInstance();
        if (!partyManager.hasParty()) {
            print(getChatPrefix() + "${red} Вы не состоите в пати", "", "", false);
            return;
        }
        String key = partyManager.getPartyKey();
        if (key == null || key.isBlank()) {
            return;
        }
        String nickname = targetNickname != null ? targetNickname.trim() : "";
        if (nickname.isEmpty()) {
            print(getChatPrefix() + "${red} Укажите ник для приглашения", "", "", false);
            return;
        }
        if (getCurrentNickname() != null && nickname.equalsIgnoreCase(getCurrentNickname())) {
            print(getChatPrefix() + "${yellow} Нельзя пригласить самого себя", "", "", false);
            return;
        }
        String gameName = targetGameName != null ? targetGameName.trim() : "";

        JsonObject data = new JsonObject();
        data.addProperty("key", key);
        data.addProperty("target", nickname);
        if (!gameName.isEmpty()) {
            data.addProperty("target_mc", gameName);
        }
        PartyManager.PartyMemberSnapshot leaderSnapshot = partyManager.getLeaderSnapshot().orElse(null);
        String inviterIrc = leaderSnapshot != null && leaderSnapshot.getIrcName() != null
                ? leaderSnapshot.getIrcName()
                : getCurrentNickname();
        String inviterMc = leaderSnapshot != null && leaderSnapshot.getGameName() != null
                ? leaderSnapshot.getGameName()
                : resolvePlayerName();
        if (inviterIrc != null && !inviterIrc.isBlank()) {
            data.addProperty("inviter", inviterIrc);
        }
        if (inviterMc != null && !inviterMc.isBlank()) {
            data.addProperty("inviter_mc", inviterMc);
        }
        sendPartyEvent("invite", data);
        print(getChatPrefix() + "${green} Приглашение отправлено игроку ${white}" + nickname, "", "", false);
    }

    public void sendPartyMarker(double x, double y, double z) {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        PartyManager partyManager = PartyManager.getInstance();
        if (!partyManager.hasParty()) {
            print(getChatPrefix() + "${red} Вы не состоите в пати!", "", "", false);
            return;
        }
        String key = partyManager.getPartyKey();
        if (key == null || key.isBlank()) {
            return;
        }
        String ircName = getCurrentNickname();
        String gameName = resolvePlayerName();
        long timestamp = System.currentTimeMillis();

        JsonObject data = new JsonObject();
        data.addProperty("key", key);
        if (ircName != null && !ircName.isBlank()) {
            data.addProperty("owner", ircName);
        }
        if (gameName != null && !gameName.isBlank()) {
            data.addProperty("owner_mc", gameName);
        }
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("z", z);
        data.addProperty("timestamp", timestamp);

        sendPartyEvent("marker", data);
        partyManager.updateMarker(ircName, gameName, x, y, z, timestamp);
        print(getChatPrefix() + "${green} Метка отправлена участникам пати", "", "", false);
    }

    public void sendOwnPartyPosition() {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Авторизуйтесь для отправки координат", "", "", false);
            return;
        }
        PartyManager partyManager = PartyManager.getInstance();
        if (!partyManager.hasParty()) {
            print(getChatPrefix() + "${red} Вы не состоите в пати", "", "", false);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        String key = partyManager.getPartyKey();
        if (key == null || key.isBlank()) {
            return;
        }
        String ircName = getCurrentNickname();
        String gameName = resolvePlayerName();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        sendPartyPosition(key, ircName, gameName, x, y, z);
        partyManager.updatePosition(ircName, gameName, x, y, z, System.currentTimeMillis());
        print(getChatPrefix() + "${green} Координаты отправлены участникам пати", "", "", false);
    }

    public void sendPartyPosition(String key, String ircName, String gameName, double x, double y, double z) {
        if (key == null || key.isBlank()) {
            return;
        }
        if ((ircName == null || ircName.isBlank()) && (gameName == null || gameName.isBlank())) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "party_pos");
        payload.addProperty("key", key);
        if (ircName != null && !ircName.isBlank()) {
            payload.addProperty("member", ircName);
        }
        if (gameName != null && !gameName.isBlank()) {
            payload.addProperty("player_mc", gameName);
        }
        payload.addProperty("x", x);
        payload.addProperty("y", y);
        payload.addProperty("z", z);
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    private void handleOnlineUsersList(JsonObject json) {
        List<OnlineUser> users = new ArrayList<>();
        if (json != null && json.has("users") && json.get("users").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("users")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject userObj = element.getAsJsonObject();
                String nickname = extractString(userObj, "nickname");
                if (nickname == null || nickname.isEmpty()) {
                    continue;
                }
                String gameUsername = extractString(userObj, "gameUsername");
                String clientLabel = extractString(userObj, "clientLabel");
                String role = extractString(userObj, "role");
                String server = extractString(userObj, "server");
                boolean online = !userObj.has("online") || userObj.get("online").getAsBoolean();
                users.add(new OnlineUser(nickname, gameUsername, clientLabel, role, server, online));
            }
        }
        synchronized (this) {
            cachedOnlineUsers = users;
            cachedOnlineUsersTimestamp = System.currentTimeMillis();
        }
        Consumer<List<OnlineUser>> callback = onlineUsersCallback;
        onlineUsersCallback = null;
        if (callback != null) {
            try {
                callback.accept(new ArrayList<>(users));
            } catch (Exception ignored) {
            }
        }
    }

    public List<OnlineUser> getCachedOnlineUsers() {
        List<OnlineUser> snapshot;
        boolean refresh;
        synchronized (this) {
            long now = System.currentTimeMillis();
            refresh = cachedOnlineUsers.isEmpty() || (now - cachedOnlineUsersTimestamp) > CACHE_TIMEOUT_MS;
            snapshot = cachedOnlineUsers.isEmpty() ? Collections.emptyList() : new ArrayList<>(cachedOnlineUsers);
        }
        if (refresh) {
            refreshOnlineUsersCache();
        }
        return snapshot;
    }

    public void refreshOnlineUsersCache() {
        requestOnlineUsers(users -> {});
    }

    public void requestOnlineUsers(Consumer<List<OnlineUser>> callback) {
        if (callback == null) {
            return;
        }
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            callback.accept(Collections.emptyList());
            return;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Вы должны войти в аккаунт для получения списка пользователей!", "", "", false);
            callback.accept(Collections.emptyList());
            return;
        }
        onlineUsersCallback = callback;
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "get_online_users");
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        try {
            this.send(cypher(payload.toString()));
        } catch (Exception ignored) {
            onlineUsersCallback = null;
            callback.accept(Collections.emptyList());
        }
    }

    // ---------- FRIENDS & DM ----------

    private String dmKey(String nick) {
        return nick == null ? "" : nick.toLowerCase();
    }

    /**
     * Nicknames this session has exchanged DMs with, most recently active first.
     * Casing is whatever the peer last presented, so it is display-ready.
     */
    public List<String> getDmPeers() {
        List<String> keys = new ArrayList<>(dmLogs.keySet());
        keys.sort((a, b) -> Long.compare(lastDmTime(b), lastDmTime(a)));
        List<String> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            out.add(dmPeerNames.getOrDefault(key, key));
        }
        return out;
    }

    private long lastDmTime(String key) {
        java.util.Deque<DmEntry> log = dmLogs.get(key);
        DmEntry last = log == null ? null : log.peekLast();
        return last == null ? 0L : last.time();
    }

    private void recordDm(String nick, boolean self, String message) {
        recordDm(nick, self, message, null, null);
    }

    private void recordDm(String nick, boolean self, String message, String replyAuthor, String replyText) {
        if (nick == null || nick.isBlank() || message == null) {
            return;
        }
        dmPeerNames.put(dmKey(nick), nick);
        java.util.Deque<DmEntry> log = dmLogs.computeIfAbsent(dmKey(nick),
                k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        log.addLast(new DmEntry(System.currentTimeMillis(), self, message, replyAuthor, replyText));
        while (log.size() > DM_LIMIT) {
            log.pollFirst();
        }
    }

    public List<DmEntry> getDmLog(String nick) {
        java.util.Deque<DmEntry> log = dmLogs.get(dmKey(nick));
        return log == null ? Collections.emptyList() : new ArrayList<>(log);
    }

    public List<OnlineUser> getFriends() {
        synchronized (friends) {
            return new ArrayList<>(friends);
        }
    }

    public List<String> getIncomingFriendRequests() {
        synchronized (friends) {
            return new ArrayList<>(incomingFriendRequests);
        }
    }

    public boolean isFriend(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        synchronized (friends) {
            for (OnlineUser f : friends) {
                if (f.getNickname() != null && f.getNickname().equalsIgnoreCase(nickname)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleFriendsList(JsonObject json) {
        List<OnlineUser> parsedFriends = new ArrayList<>();
        if (json.has("friends") && json.get("friends").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("friends")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                String nickname = extractString(obj, "nickname");
                if (nickname == null || nickname.isEmpty()) {
                    continue;
                }
                String gameUsername = extractString(obj, "gameUsername");
                String clientLabel = extractString(obj, "clientLabel");
                String role = extractString(obj, "role");
                String server = extractString(obj, "server");
                boolean online = obj.has("online") && obj.get("online").getAsBoolean();
                parsedFriends.add(new OnlineUser(nickname, gameUsername, clientLabel, role, server, online));
            }
        }
        List<String> parsedRequests = new ArrayList<>();
        if (json.has("requests") && json.get("requests").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("requests")) {
                if (element.isJsonPrimitive()) {
                    String value = element.getAsString();
                    if (value != null && !value.isBlank()) {
                        parsedRequests.add(value);
                    }
                }
            }
        }
        synchronized (friends) {
            friends.clear();
            friends.addAll(parsedFriends);
            incomingFriendRequests.clear();
            incomingFriendRequests.addAll(parsedRequests);
        }
    }

    public void sendFriendRequest(String targetNickname) {
        if (!ensureReadyForFriends()) {
            return;
        }
        String nickname = targetNickname != null ? targetNickname.trim() : "";
        if (nickname.isEmpty()) {
            print(getChatPrefix() + "${red} Укажите ник для заявки в друзья", "", "", false);
            return;
        }
        if (getCurrentNickname() != null && nickname.equalsIgnoreCase(getCurrentNickname())) {
            print(getChatPrefix() + "${yellow} Нельзя добавить самого себя", "", "", false);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "friend_request");
        payload.addProperty("target", nickname);
        payload.addProperty("author", getCurrentNickname());
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void respondFriendRequest(String fromNickname, boolean accept) {
        if (!ensureReadyForFriends()) {
            return;
        }
        if (fromNickname == null || fromNickname.isBlank()) {
            return;
        }
        synchronized (friends) {
            incomingFriendRequests.removeIf(n -> n.equalsIgnoreCase(fromNickname));
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "friend_action");
        payload.addProperty("action", accept ? "accept" : "decline");
        payload.addProperty("target", fromNickname.trim());
        payload.addProperty("author", getCurrentNickname());
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void removeFriend(String nickname) {
        if (!ensureReadyForFriends()) {
            return;
        }
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        synchronized (friends) {
            friends.removeIf(f -> f.getNickname() != null && f.getNickname().equalsIgnoreCase(nickname));
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "friend_action");
        payload.addProperty("action", "remove");
        payload.addProperty("target", nickname.trim());
        payload.addProperty("author", getCurrentNickname());
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        this.send(cypher(payload.toString()));
    }

    public void requestFriends() {
        if (!this.isOpen() || !isLoggedIn()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "get_friends");
        payload.addProperty("api_key", API_KEY);
        payload.addProperty("client_version", CLIENT_VERSION);
        try {
            this.send(cypher(payload.toString()));
        } catch (Exception ignored) {
        }
    }

    private boolean ensureReadyForFriends() {
        if (!this.isOpen()) {
            print(getChatPrefix() + "${red} Нет подключения к серверу!", "", "", false);
            return false;
        }
        if (!isLoggedIn()) {
            print(getChatPrefix() + "${red} Войдите в аккаунт для работы с друзьями", "", "", false);
            return false;
        }
        return true;
    }

    private void notifyFriendRequest(String author) {
        if (author == null) return;
        IRCSettings s = IRCSettings.get();
        s.ensureLoaded();
        if (!s.isNotificationsEnabled()) return;
        WindowsToastNotifier.show("Заявка в друзья • " + author, author + " хочет добавить вас в друзья");
    }

    public List<String> getOnlineNicknamesForSuggestions() {
        List<OnlineUser> users = getCachedOnlineUsers();
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        String selfName = getCurrentNickname();
        List<String> result = new ArrayList<>();
        outer:
        for (OnlineUser user : users) {
            if (user == null) {
                continue;
            }
            String nickname = user.getNickname();
            if (nickname == null || nickname.isBlank()) {
                continue;
            }
            if (selfName != null && nickname.equalsIgnoreCase(selfName)) {
                continue;
            }
            for (String existing : result) {
                if (existing.equalsIgnoreCase(nickname)) {
                    continue outer;
                }
            }
            result.add(nickname);
            if (result.size() >= 20) {
                break;
            }
        }
        return result;
    }

    public boolean isUserOnline(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        List<OnlineUser> users = getCachedOnlineUsers();
        if (users.isEmpty()) {
            return false;
        }
        for (OnlineUser user : users) {
            if (user == null) {
                continue;
            }
            String userNickname = user.getNickname();
            if (userNickname != null && userNickname.equalsIgnoreCase(nickname)) {
                return true;
            }
        }
        return false;
    }

    public void muteUser(String prefix, String author, long durationSeconds) {
        if (prefix == null || author == null || durationSeconds <= 0) {
            print(getChatPrefix() + "${red} Неверные параметры для мута!", "", "", false);
            return;
        }
        mutedUsers.add(new MuteEntry(prefix, author, durationSeconds));
        print(getChatPrefix() + "${red} Пользователь " + author + " (" + prefix + ") замучен локально на " + durationSeconds + " секунд!", "", "", false);
    }

    private void handleBroadcastMessage(JsonObject payload) {
        if (payload == null || !payload.has("message")) {
            return;
        }
        String message = payload.get("message").getAsString();
        String title = payload.has("title") ? payload.get("title").getAsString() : "Системное уведомление";

        if (payload.has("leader_mc")) {
            String leaderMc = payload.get("leader_mc").getAsString();
            String leader = payload.has("leader") ? payload.get("leader").getAsString() : leaderMc;

            String playerName = resolvePlayerName();
            if (SENDER.equalsIgnoreCase(leader) || (playerName != null && playerName.equalsIgnoreCase(leaderMc))) {
                return;
            }

            print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
            print(getChatPrefix() + "${green}" + title, "", "", false);
            print(getChatPrefix() + "${white}" + message, "", "", false);
            print(getChatPrefix() + "${gray}Обратитесь к создателю для получения ключа", "", "", false);
            print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
        } else {
            print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
            print(getChatPrefix() + "${red}[${yellow}" + title + "${red}]", "", "", false);
            print(getChatPrefix() + "${white}" + message, "", "", false);
            print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
        }
    }

    private void handlePartyJoinRequest(JsonObject payload) {
        if (payload == null) {
            return;
        }
        try {
            String key = extractString(payload, "key");
            String player = extractString(payload, "player");
            String playerMc = extractString(payload, "player_mc");

            if (key == null || key.isEmpty() || player == null || player.isEmpty()) {
                return;
            }

            PartyManager partyManager = PartyManager.getInstance();

            if (!partyManager.hasParty() || !partyManager.isLeader() || !partyManager.matchesKey(key)) {
                return;
            }

            boolean alreadyMember = partyManager.isMember(player);
            if (playerMc != null && !playerMc.isEmpty()) {
                alreadyMember = alreadyMember || partyManager.isMemberGame(playerMc);
            }
            if (alreadyMember) {
                return;
            }

            String selfGameName = resolvePlayerName();
            if (player.equalsIgnoreCase(SENDER) || (selfGameName != null && playerMc != null && playerMc.equalsIgnoreCase(selfGameName))) {
                return;
            }

            synchronized (pendingRequestLock) {

                if (pendingJoinRequestKey != null) {
                    if (pendingJoinRequestPlayer != null && pendingJoinRequestPlayer.equalsIgnoreCase(player)) {

                        pendingJoinRequestTimestamp = System.currentTimeMillis();
                        return;
                    }

                    String oldDisplay = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : pendingJoinRequestPlayer;
                    print(getChatPrefix() + "${gray}Предыдущий запрос от " + oldDisplay + " автоматически отклонён (новый запрос).", "", "", false);
                }

                pendingJoinRequestKey = key;
                pendingJoinRequestPlayer = player;
                pendingJoinRequestPlayerMc = playerMc != null && !playerMc.isEmpty() ? playerMc : null;
                pendingJoinRequestTimestamp = System.currentTimeMillis();

                String displayName = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : player;
                print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
                print(getChatPrefix() + "${green}Запрос на присоединение к пати", "", "", false);
                print(getChatPrefix() + "${white}" + displayName + "${gray} хочет присоединиться к вашей пати.", "", "", false);
                sendPartyJoinActionsLine();
                print(getChatPrefix() + "${yellow}═══════════════════════════════════════", "", "", false);
            }
        } catch (Exception e) {
            System.err.println("[IRC] Error handling party join request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPartyJoinActionsLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        Cmd_Initializer initializer = LynxWave.getInstance().getCmdInitializer();
        if (initializer == null || initializer.getCommandDispatcher() == null) {
            return;
        }
        String prefixValue = initializer.getCommandDispatcher().getPrefix().get();
        if (prefixValue == null) {
            prefixValue = ".";
        }

        MutableText line = Text.literal(getChatPrefix() + "§7Быстрые действия:");
        line.append(CommandTextUtil.bracketedButton(
                "Принять",
                Formatting.GREEN,
                ClickEvent.Action.SUGGEST_COMMAND,
                prefixValue + "irc party accept",
                "§aПринять запрос"
        ));
        line.append(CommandTextUtil.bracketedButton(
                "Отклонить",
                Formatting.RED,
                ClickEvent.Action.SUGGEST_COMMAND,
                prefixValue + "irc party decline",
                "§cОтклонить запрос"
        ));

        client.inGameHud.getChatHud().addMessage(line);
    }

    public void acceptPendingJoinRequest() {
        synchronized (pendingRequestLock) {
            if (pendingJoinRequestKey == null || pendingJoinRequestPlayer == null) {
                return;
            }

            try {
                PartyManager partyManager = PartyManager.getInstance();
                if (!partyManager.hasParty() || !partyManager.isLeader() || !partyManager.matchesKey(pendingJoinRequestKey)) {
                    clearPendingJoinRequest();
                    return;
                }

                boolean alreadyMember = partyManager.isMember(pendingJoinRequestPlayer);
                if (pendingJoinRequestPlayerMc != null) {
                    alreadyMember = alreadyMember || partyManager.isMemberGame(pendingJoinRequestPlayerMc);
                }
                if (alreadyMember) {
                    String display = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : pendingJoinRequestPlayer;
                    print(getChatPrefix() + "${yellow}" + display + "${gray} уже состоит в пати.", "", "", false);
                    clearPendingJoinRequest();
                    return;
                }

                partyManager.addOrUpdateMember(pendingJoinRequestPlayer, pendingJoinRequestPlayerMc);
                PartyMemberSnapshot leaderSnapshot = partyManager.getLeaderSnapshot().orElse(null);

                JsonObject response = new JsonObject();
                response.addProperty("key", partyManager.getPartyKey());
                String leaderName = leaderSnapshot != null && leaderSnapshot.getIrcName() != null ? leaderSnapshot.getIrcName() : SENDER;
                response.addProperty("leader", leaderName);
                if (leaderSnapshot != null && leaderSnapshot.getGameName() != null) {
                    response.addProperty("leader_mc", leaderSnapshot.getGameName());
                }
                response.add("members", toMemberJsonArray(partyManager.getMembersSnapshot()));

                sendPartyEvent("join_accept", response);

                String display = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : pendingJoinRequestPlayer;
                print(getChatPrefix() + "${green}" + display + "${gray} присоединился к вашей пати.", "", "", false);

                clearPendingJoinRequest();
            } catch (Exception e) {
                System.err.println("[IRC] Error accepting join request: " + e.getMessage());
                e.printStackTrace();
                clearPendingJoinRequest();
            }
        }
    }

    public void declinePendingJoinRequest() {
        synchronized (pendingRequestLock) {
            if (pendingJoinRequestKey == null || pendingJoinRequestPlayer == null) {
                return;
            }
            try {
                String display = pendingJoinRequestPlayerMc != null ? pendingJoinRequestPlayerMc : pendingJoinRequestPlayer;
                print(getChatPrefix() + "${yellow}Запрос от " + display + " отклонён.", "", "", false);
            } catch (Exception e) {
                System.err.println("[IRC] Error declining join request: " + e.getMessage());
            } finally {
                clearPendingJoinRequest();
            }
        }
    }

    private void clearPendingJoinRequest() {
        pendingJoinRequestKey = null;
        pendingJoinRequestPlayer = null;
        pendingJoinRequestPlayerMc = null;
        pendingJoinRequestTimestamp = 0;
    }

    public boolean hasPendingJoinRequest() {
        synchronized (pendingRequestLock) {
            return pendingJoinRequestKey != null;
        }
    }

    private void handlePartyPositionsBroadcast(JsonObject payload) {
        if (payload == null) {
            return;
        }
        String key = extractString(payload, "key");
        if (key == null || key.isEmpty()) {
            key = extractString(payload, "party_key");
        }
        if (key == null || key.isEmpty()) {
            return;
        }
        PartyManager partyManager = PartyManager.getInstance();
        if (!partyManager.matchesKey(key)) {
            return;
        }
        if (!payload.has("positions") || !payload.get("positions").isJsonArray()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (JsonElement element : payload.getAsJsonArray("positions")) {
            if (!element.isJsonObject()) continue;
            JsonObject pos = element.getAsJsonObject();
            String member = extractString(pos, "member");
            String memberMc = extractString(pos, "player_mc");
            if (memberMc == null) {
                memberMc = extractString(pos, "mc");
            }
            if ((member == null || member.isEmpty()) && (memberMc == null || memberMc.isEmpty())) {
                continue;
            }
            Double x = extractDouble(pos, "x");
            Double y = extractDouble(pos, "y");
            Double z = extractDouble(pos, "z");
            if (x == null || y == null || z == null) {
                continue;
            }
            long timestamp = extractLong(pos, "timestamp", now);
            String ircName = member != null && !member.isEmpty() ? member : memberMc;
            String gameName = memberMc != null && !memberMc.isEmpty() ? memberMc : member;
            partyManager.addOrUpdateMember(ircName, gameName);
            partyManager.updatePosition(ircName, gameName, x, y, z, timestamp);
        }
    }


    private void notifyText(String author, String content) {
        if (author == null || content == null) return;
        if (author.equalsIgnoreCase(SENDER) || author.equalsIgnoreCase(currentNickname)) return;
        IRCSettings s = IRCSettings.get();
        s.ensureLoaded();
        if (!s.isNotificationsEnabled()) return;
        if (s.isNotifyMentionsOnly()) {
            String me = currentNickname != null ? currentNickname : SENDER;
            if (me == null) return;
            if (!content.toLowerCase().contains("@" + me.toLowerCase())
                    && !content.toLowerCase().contains(me.toLowerCase())) return;
        }
        WindowsToastNotifier.show("IRC • " + author, content);
    }

    private void notifyPrivate(String author, String content) {
        if (author == null || content == null) return;
        if (author.equalsIgnoreCase(SENDER) || author.equalsIgnoreCase(currentNickname)) return;
        IRCSettings s = IRCSettings.get();
        s.ensureLoaded();
        if (!s.isNotificationsEnabled()) return;
        if (!s.isNotifyDirectMessages()) return;
        WindowsToastNotifier.show("Личное сообщение • " + author, content);
    }

    private String cypher(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= (0x15 ^ 0x3) ^ 2;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
