package dev.client.cmd.core.cmd_modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.helper.client.irc.ClientLabelRegistry;
import dev.client.api.nullcry.helper.client.irc.IRClient;
import dev.client.api.nullcry.helper.client.irc.PartyManager;
import dev.client.api.nullcry.helper.client.irc.PrefixData;
import dev.client.api.nullcry.helper.client.irc.PrefixTest;
import dev.client.modules.core.misc.IRC;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdIRC implements Command, CommandWithAdvice, ClientApi {
    final Prefix prefix;
    final Logger logger;
    private static final List<String> PARTY_ACTIONS = List.of(
            "create", "key", "code", "join", "invite", "add", "kick", "remove",
            "leave", "disband", "clear", "list", "show", "status", "help",
            "accept", "decline"
    );
    private static final Set<String> PARTY_ACTION_LOOKUP = new HashSet<>(PARTY_ACTIONS);
    private static final List<String> AUTHORIZED_COMMANDS = List.of(
            "ignore", "prefix", "setprefix", "party", "mute", "unmute",
            "setmoderator", "setadmin", "raffle", "pm", "logout"
    );
    private static final List<String> BASIC_COMMANDS = List.of("register", "login");
    private static final List<String> CONNECTED_COMMANDS = List.of("register", "login", "logout");

    @Override
    public List<String> parametersCommand() {
        return availableSubCommands();
    }

    private IRC requireModuleEnabled(IRC module) {
        IRC current = module != null ? module : IRC.INSTANCE;
        if (current == null || !current.isEnabled()) {
            throw new CommandException(Formatting.RED + "Функция IRC отключена. Включите модуль IRC.");
        }
        return current;
    }

    private IRClient requireConnectedIrc(IRC module) {
        requireModuleEnabled(module);
        IRClient IRClient = LynxWave.getInstance().getIRClient();
        if (IRClient == null || !IRClient.isOpen()) {
            throw new CommandException(Formatting.RED + "IRC не подключён. Включите модуль IRC.");
        }
        return IRClient;
    }

    @Override
    public void execute(Parameters parameters) {
        IRC module = IRC.INSTANCE;
        String commandType = parameters.asString(0).orElse("").toLowerCase(Locale.ROOT);
        IRClient IRClient = requireConnectedIrc(module);

        if (commandType.isEmpty()) {
            sendMessage(parameters, logger, IRClient);
            return;
        }

        List<String> availableCommands = availableSubCommands();
        if (!availableCommands.contains(commandType)) {
            if (handleUnknownSubCommand(parameters, commandType, availableCommands)) {
                return;
            }
            sendMessage(parameters, logger, IRClient);
            return;
        }

        switch (commandType) {
            case "ignore" -> handleIgnoreCommand(parameters, IRClient);
            case "prefix" -> handlePrefix(IRClient);
            case "setprefix" -> handleSetPrefix(parameters, IRClient);
            case "party" -> handleParty(parameters, IRClient);
            case "register" -> handleRegister(parameters, IRClient);
            case "login" -> handleLogin(parameters, IRClient);
            case "mute" -> handleMute(parameters, IRClient);
            case "unmute" -> handleUnmute(parameters, IRClient);
            case "setmoderator" -> handleSetModerator(parameters, IRClient);
            case "setadmin" -> handleSetAdmin(parameters, IRClient);
            case "raffle" -> handleRaffle(parameters, IRClient);
            case "pm" -> handlePrivateMessage(parameters, IRClient);
            case "logout" -> handleLogout(IRClient);
        }
    }

    @Override
    public String name() {
        return "irc";
    }

    @Override
    public String description() {
        return "Позволяет общатся между пользователями нескольких клиентов";
    }

    public List<String> adviceMessage() {
        String commandPrefix = prefix.get();
        return List.of(
                commandPrefix + "irc <message> - Отправить сообщение",
                commandPrefix + "irc pm <никнейм> <сообщение> - Отправить личное сообщение",
                commandPrefix + "irc register <username> <password> - Зарегистрироваться",
                commandPrefix + "irc login <username> <password> - Войти",
                commandPrefix + "irc mute <nickname> <минуты> [причина] - Замутить пользователя (модератор/админ)",
                commandPrefix + "irc unmute <nickname> - Снять мут (модератор/админ)",
                commandPrefix + "irc setmoderator <username> <true/false> - Выдать/отозвать права модератора (админ)",
                commandPrefix + "irc setadmin <username> <true/false> - Выдать/отозвать права администратора (админ)",
                commandPrefix + "irc raffle join - Принять участие в розыгрыше"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if (subCommand == null) {
            return List.of();
        }
        String lowered = subCommand.toLowerCase(Locale.ROOT);
        if (!availableSubCommands().contains(lowered)) {
            return List.of();
        }
        return switch (lowered) {
            case "ignore" -> List.of("add", "remove", "list");
            case "party" -> PARTY_ACTIONS;
            case "register", "login" -> List.of("username");
            case "mute", "unmute" -> List.of("nickname");
            case "setmoderator", "setadmin" -> List.of("username");
            case "raffle" -> List.of("join");
            case "pm" -> ircNicknameSuggestionsOrPlaceholder("username");
            case "setprefix" -> prefixIdSuggestions();
            default -> List.of();
        };
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if (subCommand == null) {
            return List.of();
        }
        String lowered = subCommand.toLowerCase(Locale.ROOT);
        if (!availableSubCommands().contains(lowered)) {
            return List.of();
        }
        switch (lowered) {
            case "ignore": {
                if (step == 0) {
                    return List.of("add", "remove", "list");
                }
                if (previousArgs != null && !previousArgs.isEmpty()) {
                    String action = previousArgs.get(0).toLowerCase(Locale.ROOT);
                    return switch (action) {
                        case "add" -> switch (step) {
                            case 1 -> List.of("client");
                            case 2 -> List.of("nickname");
                            case 3 -> List.of("seconds");
                            case 4 -> List.of("reason");
                            default -> List.of();
                        };
                        case "remove" -> switch (step) {
                            case 1 -> List.of("client");
                            case 2 -> List.of("nickname");
                            default -> List.of();
                        };
                        default -> List.of();
                    };
                }
                return List.of();
            }
            case "party": {
                if (step == 0) {
                    return PARTY_ACTIONS;
                }
                if (previousArgs != null && !previousArgs.isEmpty()) {
                    String action = previousArgs.get(0).toLowerCase(Locale.ROOT);
                    return switch (action) {
                        case "join" -> step == 1 ? List.of("KEY") : List.of();
                        case "invite", "add", "kick", "remove" -> step == 1
                                ? ircNicknameSuggestionsOrPlaceholder("username")
                                : List.of();
                        default -> List.of();
                    };
                }
                return List.of();
            }
            case "register":
            case "login":
                return switch (step) {
                    case 0 -> List.of("username");
                    case 1 -> List.of("password");
                    default -> List.of();
                };
            case "mute":
                return switch (step) {
                    case 0 -> List.of("nickname");
                    case 1 -> List.of("minutes");
                    case 2 -> List.of("reason");
                    default -> List.of();
                };
            case "unmute":
                return step == 0 ? List.of("nickname") : List.of();
            case "setmoderator":
            case "setadmin":
                return switch (step) {
                    case 0 -> List.of("username");
                    case 1 -> List.of("true", "false");
                    default -> List.of();
                };
            case "raffle":
                return step == 0 ? List.of("join") : List.of();
            case "pm":
                if (step == 0) {
                    return ircNicknameSuggestionsOrPlaceholder("username");
                }
                return List.of();
            case "setprefix":
                return step == 0 ? prefixIdSuggestions() : List.of();
            default:
                return List.of();
        }
    }

    private List<String> prefixIdSuggestions() {
        List<String> ids = new ArrayList<>();
        for (PrefixTest prefixTest : PrefixData.PREFIX_TESTS) {
            ids.add(String.valueOf(prefixTest.id()));
        }
        ids.sort(Comparator.comparingInt(id -> {
            try {
                return Integer.parseInt(id);
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }));
        return ids;
    }

    private MutableText buildCopyablePartyKeyText(String prefixText, Formatting prefixColor, String key) {
        String base = prefixText != null ? prefixText : "";
        if (!base.isEmpty() && !Character.isWhitespace(base.charAt(base.length() - 1))) {
            base = base + " ";
        }
        String safeKey = key != null ? key : "";
        MutableText prefixComponent = Text.literal(base).formatted(prefixColor);
        MutableText keyComponent = Text.literal(safeKey)
                .formatted(Formatting.AQUA)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(safeKey))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("Кликните для копирования").formatted(Formatting.AQUA))));
        return Text.empty().append(prefixComponent).append(keyComponent);
    }

    private void sendMessage(final Parameters parameters, Logger logger, IRClient IRClient) {
        String message = parameters.collectMessage(0);
        if (message.isBlank()) {
            sendUsage(logger);
            return;
        }
        IRClient.sendMessage(message);
    }

    private void handleIgnoreCommand(Parameters parameters, IRClient IRClient) {
        if (!parameters.asString(1).isPresent()) {
            throw new CommandException(Formatting.RED + "Недостаточно аргументов для команды ignore!");
        }

        String action = parameters.asString(1).get().toLowerCase();
        switch (action) {
            case "add" -> {
                if (!parameters.asString(2).isPresent() ||
                        !parameters.asString(3).isPresent() ||
                        !parameters.asString(4).isPresent()) {
                    throw new CommandException(Formatting.RED + "Недостаточно аргументов для команды ignore add!");
                }
                try {
                    String client = parameters.asString(2).get();
                    String nickname = parameters.asString(3).get();
                    long duration = Long.parseLong(parameters.asString(4).get());
                    if (duration <= 0) {
                        throw new CommandException(Formatting.RED + "Время мута должно быть больше 0 секунд!");
                    }
                    IRClient.muteUser(client, nickname, duration);
                } catch (NumberFormatException e) {
                    throw new CommandException(Formatting.RED + "Время должно быть числом в секундах!");
                }
            }
            case "remove" -> {
                if (!parameters.asString(2).isPresent() ||
                        !parameters.asString(3).isPresent()) {
                    throw new CommandException(Formatting.RED + "Недостаточно аргументов для команды ignore remove!");
                }
                String client = parameters.asString(2).get();
                String nickname = parameters.asString(3).get();
                boolean removed = IRClient.removeMutedUser(client, nickname);
                if (!removed) {
                    throw new CommandException(Formatting.RED + "Пользователь " + nickname + " (" + client + ") не найден в списке игнорируемых!");
                }
            }
            case "list" -> {
                List<String> mutedUsers = IRClient.listMutedUsers();
                if (mutedUsers.isEmpty()) {
                    throw new CommandException(Formatting.GRAY + "Список игнорируемых пользователей пуст.");
                } else {
                    String commandPrefix = prefix.get();
                    for (String user : mutedUsers) {
                        logger.log(buildMutedUserLine(user, commandPrefix));
                    }
                }
            }
            default -> throw new CommandException(Formatting.RED + "Неизвестное действие: " + action);
        }
    }
    private void handleSetPrefix(Parameters parameters, IRClient IRClient) {
        if (!parameters.asString(1).isPresent()) {
            throw new CommandException(Formatting.RED + "Не указан ID префикса!");
        }

        String idStr = parameters.asString(1).get().toLowerCase();

        int prefixId;

        try {
            prefixId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            logger.log(Formatting.RED + "Неверный ID префикса: " + idStr);
            return;
        }

        PrefixTest prefixToSet = null;
        for (PrefixTest prefix : PrefixData.PREFIX_TESTS) {
            if (prefix.id() == prefixId) {
                prefixToSet = prefix;
                break;
            }
        }

        if (prefixToSet == null) {
            logger.log(Formatting.RED + "Префикс с ID " + prefixId + " не найден!");
            return;
        }

        IRClient.setSelectedPrefix(prefixToSet);
        logger.log(Formatting.GREEN + "Префикс установлен: " + prefixToSet.value());
    }

    private void handleLogout(IRClient IRClient) {
        if (!IRClient.isLoggedIn()) {
            throw new CommandException(Formatting.RED + "Вы уже вышли из аккаунта IRC.");
        }
        IRClient.logout();
        PartyManager.getInstance().clear();
        logger.log(Formatting.YELLOW + "Доступны команды регистрации и входа.");
    }
    private void handlePrefix(IRClient IRClient) {
        PrefixTest[] prefixes = PrefixData.PREFIX_TESTS;
        PrefixTest activePrefix = IRClient.getSelectedPrefix();
        String commandPrefix = this.prefix.get();

        MutableText message = Text.literal(IRClient.getChatPrefix() + "§fКликните для установки префикса:");

        for (PrefixTest prefix : prefixes) {
            int prefixId = prefix.id();
            boolean isActive = activePrefix != null && activePrefix.id() == prefixId;
            Formatting valueColor = isActive ? Formatting.GREEN : Formatting.AQUA;
            String suffix = isActive ? Formatting.GRAY + " (активен)" : "";

            MutableText clickablePrefix = Text.literal(" \n§7[§f" + prefixId + "§7] §r")
                    .append(Text.literal(prefix.value()).formatted(valueColor))
                    .append(Text.literal(suffix))
                    .styled(style -> style
                            .withClickEvent(new ClickEvent.SuggestCommand(
                                    ensureRunCommand(commandPrefix + "irc setprefix " + prefixId)
                            ))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Text.literal("Кликните, чтобы установить префикс " + prefix.value())
                            ))
                    );

            message.append(clickablePrefix);
        }

        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(message);
        }
    }

    private void handleParty(Parameters parameters, IRClient IRClient) {
        PartyManager partyManager = PartyManager.getInstance();
        String selfName = IRClient.SENDER;
        String selfGameName = mc.player != null ? mc.player.getGameProfile().name() : selfName;

        String subCommand = parameters.asString(1).orElse(null);
        if (subCommand == null) {
            sendPartyChatMessage(parameters, partyManager, IRClient, selfName, selfGameName);
            return;
        }

        String action = subCommand.toLowerCase(Locale.ROOT);
        if (!PARTY_ACTION_LOOKUP.contains(action)) {
            sendPartyChatMessage(parameters, partyManager, IRClient, selfName, selfGameName);
            return;
        }

        switch (action) {
            case "create" -> {
                if (partyManager.hasParty()) {
                    if (partyManager.isLeader()) {
                        throw new CommandException(Formatting.RED + "Пати уже создана.");
                    } else {
                        throw new CommandException(Formatting.RED + "Сначала покиньте текущую пати.");
                    }
                }
                if (IRClient.hasPendingJoinRequest()) {
                    IRClient.declinePendingJoinRequest();
                }
                String key = partyManager.createParty(selfName, selfGameName);
                if (key == null) {
                    throw new CommandException(Formatting.RED + "Не удалось создать пати.");
                }
                JsonObject data = new JsonObject();
                data.addProperty("key", key);
                data.addProperty("leader", selfName);
                if (selfGameName != null) {
                    data.addProperty("leader_mc", selfGameName);
                }
                data.add("members", membersToJson(partyManager.getMembersSnapshot()));
                IRClient.sendPartyEvent("create", data);
                logger.log(buildCopyablePartyKeyText("Пати создана. Ключ: ", Formatting.GREEN, key));
            }
            case "key", "code" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                String key = partyManager.getPartyKey();
                if (key == null || key.isBlank()) {
                    throw new CommandException(Formatting.RED + "Ключ пати отсутствует.");
                }
                logger.log(buildCopyablePartyKeyText("Ключ пати: ", Formatting.AQUA, key));
            }
            case "join" -> {
                if (partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Вы уже состоите в пати.");
                }
                String key = parameters.asString(2)
                        .orElseThrow(() -> new CommandException(Formatting.RED + "Укажите ключ пати!"))
                        .trim()
                        .toUpperCase(Locale.ROOT);
                if (key.isEmpty()) {
                    throw new CommandException(Formatting.RED + "Ключ не может быть пустым.");
                }
                if (key.length() != 6 || !key.matches("^[A-Z0-9]+$")) {
                    throw new CommandException(Formatting.RED + "Неверный формат ключа. Ключ должен содержать 6 символов (A-Z, 0-9).");
                }

                if (partyManager.isPendingKey(key)) {
                    throw new CommandException(Formatting.YELLOW + "Запрос на присоединение к этой пати уже отправлен. Ожидайте ответа.");
                }
                partyManager.setPendingKey(key);
                JsonObject data = new JsonObject();
                data.addProperty("key", key);
                data.addProperty("player", selfName);
                if (selfGameName != null) {
                    data.addProperty("player_mc", selfGameName);
                }
                IRClient.sendPartyEvent("join_request", data);
                logger.log(Formatting.YELLOW + "Запрос на присоединение отправлен. Ожидайте подтверждения лидера.");
            }
            case "invite" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Сначала создайте пати.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может приглашать игроков.");
                }
                String target = parameters.asString(2)
                        .orElseThrow(() -> new CommandException(Formatting.RED + "Укажите ник игрока для приглашения!"))
                        .trim();
                if (!IRClient.isUserOnline(target)) {
                    throw new CommandException(Formatting.RED + "Пользователь " + target + " не находится в сети IRC.");
                }
                String targetGameName = resolveGameName(target);
                IRClient.sendPartyInvite(target, targetGameName);
                logger.log(Formatting.GREEN + "Приглашение отправлено " + target + ".");
            }
            case "add" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Сначала создайте пати.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может приглашать игроков.");
                }
                String target = parameters.asString(2)
                        .orElseThrow(() -> new CommandException(Formatting.RED + "Укажите ник игрока для добавления!"))
                        .trim();
                if (!IRClient.isUserOnline(target)) {
                    throw new CommandException(Formatting.RED + "Пользователь " + target + " не находится в сети IRC.");
                }
                String targetGameName = resolveGameName(target);
                if (partyManager.isMember(target) || (targetGameName != null && partyManager.isMemberGame(targetGameName))) {
                    throw new CommandException(Formatting.RED + "Игрок уже находится в пати.");
                }
                partyManager.addOrUpdateMember(target, targetGameName);
                JsonObject data = new JsonObject();
                data.addProperty("key", partyManager.getPartyKey());
                PartyManager.PartyMemberSnapshot leaderSnapshot = partyManager.getLeaderSnapshot().orElse(null);
                data.addProperty("leader", leaderSnapshot != null && leaderSnapshot.getIrcName() != null ? leaderSnapshot.getIrcName() : selfName);
                if (leaderSnapshot != null && leaderSnapshot.getGameName() != null) {
                    data.addProperty("leader_mc", leaderSnapshot.getGameName());
                } else if (selfGameName != null) {
                    data.addProperty("leader_mc", selfGameName);
                }
                data.add("members", membersToJson(partyManager.getMembersSnapshot()));
                IRClient.sendPartyEvent("join_accept", data);
                logger.log(Formatting.GREEN + "Игрок " + target + " добавлен в пати.");
            }
            case "kick", "remove" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может удалять игроков из пати.");
                }
                String target = parameters.asString(2)
                        .orElseThrow(() -> new CommandException(Formatting.RED + "Укажите ник игрока для удаления!"))
                        .trim();
                if (target.equalsIgnoreCase(selfName)) {
                    throw new CommandException(Formatting.RED + "Лидер не может исключить себя. Используйте disband.");
                }
                if (!partyManager.removeMember(target)) {
                    throw new CommandException(Formatting.RED + "Игрок " + target + " не найден в пати.");
                }
                JsonObject data = new JsonObject();
                data.addProperty("key", partyManager.getPartyKey());
                data.addProperty("player", target);
                String targetGame = resolveGameName(target);
                if (targetGame != null && !targetGame.isBlank()) {
                    data.addProperty("player_mc", targetGame);
                }
                IRClient.sendPartyEvent("kick", data);
                logger.log(Formatting.YELLOW + "Игрок " + target + " исключён из пати.");
            }
            case "leave" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                if (!partyManager.isMember(selfName)) {
                    throw new CommandException(Formatting.RED + "Вы не состоите в пати.");
                }
                String key = partyManager.getPartyKey();
                boolean wasLeader = partyManager.isLeader();
                if (wasLeader) {
                    if (IRClient.hasPendingJoinRequest()) {
                        IRClient.declinePendingJoinRequest();
                    }
                    partyManager.disband();
                    JsonObject data = new JsonObject();
                    data.addProperty("key", key);
                    data.addProperty("leader", selfName);
                    if (selfGameName != null) {
                        data.addProperty("leader_mc", selfGameName);
                    }
                    IRClient.sendPartyEvent("disband", data);
                    logger.log(Formatting.YELLOW + "Вы распустили пати.");
                } else {
                    partyManager.removeMember(selfName, selfGameName);
                    partyManager.clear();
                    JsonObject data = new JsonObject();
                    data.addProperty("key", key);
                    data.addProperty("player", selfName);
                    if (selfGameName != null) {
                        data.addProperty("player_mc", selfGameName);
                    }
                    IRClient.sendPartyEvent("leave", data);
                    logger.log(Formatting.YELLOW + "Вы покинули пати.");
                }
            }
            case "disband", "clear" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может распустить пати.");
                }
                // Clear any pending join requests when disbanding
                if (IRClient.hasPendingJoinRequest()) {
                    IRClient.declinePendingJoinRequest();
                }
                String key = partyManager.getPartyKey();
                partyManager.disband();
                JsonObject data = new JsonObject();
                data.addProperty("key", key);
                data.addProperty("leader", selfName);
                if (selfGameName != null) {
                    data.addProperty("leader_mc", selfGameName);
                }
                IRClient.sendPartyEvent("disband", data);
                logger.log(Formatting.YELLOW + "Пати распущена.");
            }
            case "list", "show", "status" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                List<PartyManager.PartyMemberSnapshot> members = partyManager.getMembersSnapshot();
                String leader = partyManager.getLeader().orElse("неизвестно");
                logger.log(Formatting.GRAY + "Пати " + Formatting.DARK_AQUA + "[" + partyManager.getPartyKey() + "] "
                        + Formatting.GRAY + "(лидер: " + Formatting.GREEN + leader + Formatting.GRAY + "):");
                boolean viewerLeader = partyManager.isLeader();
                String commandPrefix = prefix.get();
                for (PartyManager.PartyMemberSnapshot member : members) {
                    boolean isLeaderMember = partyManager.isLeader(member);
                    Formatting color = isLeaderMember ? Formatting.GREEN : Formatting.AQUA;
                    String suffix = isLeaderMember ? Formatting.GRAY + " [лидер]" : "";
                    String display = member.getDisplayName();
                    if (member.getIrcName() != null && member.getGameName() != null && !member.getIrcName().equalsIgnoreCase(member.getGameName())) {
                        display = member.getIrcName() + Formatting.DARK_GRAY + " (" + member.getGameName() + ")";
                    }
                    MutableText line = Text.literal("")
                            .append(Text.literal(display).formatted(color));
                    if (!suffix.isEmpty()) {
                        line.append(Text.literal(suffix));
                    }
                    String target = member.getIrcName() != null ? member.getIrcName() : member.getGameName();
                    if (viewerLeader && !isLeaderMember && target != null && !target.equalsIgnoreCase(selfName)) {
                        String removalCommand = ensureRunCommand(commandPrefix + "irc party remove " + target);
                        String hoverText = "Кликните, чтобы удалить " + member.getDisplayName() + " из пати";
                        line = line.styled(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(removalCommand))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText))));
                    }
                    logger.log(line);
                }
            }
            case "accept" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может принимать запросы.");
                }
                if (!IRClient.hasPendingJoinRequest()) {
                    throw new CommandException(Formatting.RED + "Нет активных запросов на присоединение.");
                }
                IRClient.acceptPendingJoinRequest();
            }
            case "decline" -> {
                if (!partyManager.hasParty()) {
                    throw new CommandException(Formatting.RED + "Пати не создана.");
                }
                if (!partyManager.isLeader()) {
                    throw new CommandException(Formatting.RED + "Только лидер может отклонять запросы.");
                }
                if (!IRClient.hasPendingJoinRequest()) {
                    throw new CommandException(Formatting.RED + "Нет активных запросов на присоединение.");
                }
                IRClient.declinePendingJoinRequest();
            }
            case "help" -> {
                String commandPrefix = prefix.get();
                logger.log(Formatting.GRAY + "Party команды:");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party create" + Formatting.GRAY + " - создать пати");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party key" + Formatting.GRAY + " - показать ключ пати");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party join <ключ>" + Formatting.GRAY + " - отправить запрос на вступление");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party accept" + Formatting.GRAY + " - принять запрос на присоединение (лидер)");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party decline" + Formatting.GRAY + " - отклонить запрос на присоединение (лидер)");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party add <ник>" + Formatting.GRAY + " - добавить игрока (лидер)");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party kick <ник>" + Formatting.GRAY + " - исключить игрока (лидер)");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party list" + Formatting.GRAY + " - список участников");
                logger.log(Formatting.YELLOW + commandPrefix + "irc party leave" + Formatting.GRAY + " - покинуть или распустить пати");
            }
            default -> throw new CommandException(Formatting.RED + "Неизвестная команда party: " + action);
        }
    }

    private void sendPartyChatMessage(Parameters parameters,
                                      PartyManager partyManager,
                                      IRClient IRClient,
                                      String selfName,
                                      String selfGameName) {
        if (!partyManager.hasParty()) {
            throw new CommandException(Formatting.RED + "Вы не состоите в пати.");
        }
        String message = parameters.collectMessage(1).trim();
        if (message.isEmpty()) {
            throw new CommandException(Formatting.RED + "Укажите сообщение для отправки в пати.");
        }
        if (message.length() > 120) {
            throw new CommandException(Formatting.RED + "Сообщение слишком длинное (макс. 120 символов).");
        }

        JsonObject data = new JsonObject();
        data.addProperty("key", partyManager.getPartyKey());
        data.addProperty("message", message);
        data.addProperty("sender", selfName);
        if (selfGameName != null) {
            data.addProperty("sender_mc", selfGameName);
        }
        IRClient.sendPartyEvent("chat", data);

        String displayName = selfGameName != null ? selfGameName : selfName;
        logger.log(Formatting.DARK_AQUA + "[Party] " + Formatting.GREEN + displayName
                + Formatting.GRAY + ": " + Formatting.WHITE + message);
    }

    private JsonArray membersToJson(List<PartyManager.PartyMemberSnapshot> members) {
        JsonArray array = new JsonArray();
        if (members != null) {
            for (PartyManager.PartyMemberSnapshot member : members) {
                if (member == null) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                if (member.getIrcName() != null) {
                    entry.addProperty("irc", member.getIrcName());
                }
                if (member.getGameName() != null) {
                    entry.addProperty("mc", member.getGameName());
                }
                if (!entry.has("name")) {
                    String display = member.getDisplayName();
                    if (display != null) {
                        entry.addProperty("name", display);
                    }
                }
                if (!entry.entrySet().isEmpty()) {
                    array.add(entry);
                }
            }
        }
        return array;
    }

    private String resolveGameName(String name) {
        if (name == null || mc.world == null) {
            return name;
        }
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player.getGameProfile() == null) {
                continue;
            }
            String gameName = player.getGameProfile().name();
            if (gameName != null && gameName.equalsIgnoreCase(name)) {
                return gameName;
            }
        }
        return name;
    }

    private void handleRegister(Parameters parameters, IRClient IRClient) {
        if (!parameters.asString(1).isPresent() ||
                !parameters.asString(2).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc register <username> <password>");
        }
        String username = parameters.asString(1).get();
        String password = parameters.asString(2).get();

        if (username.length() < 3 || username.length() > 32) {
            throw new CommandException(Formatting.RED + "Имя пользователя должно быть от 3 до 32 символов");
        }
        if (password.length() < 4) {
            throw new CommandException(Formatting.RED + "Пароль должен быть не менее 4 символов");
        }

        IRClient.register(username, password);
        logger.log(Formatting.GREEN + "Регистрация отправлена на сервер...");
    }

    private void handleLogin(Parameters parameters, IRClient IRClient) {
        if (!parameters.asString(1).isPresent() ||
                !parameters.asString(2).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc login <username> <password>");
        }
        String username = parameters.asString(1).get();
        String password = parameters.asString(2).get();

        IRClient.login(username, password);
        logger.log(Formatting.GREEN + "Запрос на вход отправлен на сервер...");
    }

    private void handleMute(Parameters parameters, IRClient IRClient) {
        if (!IRClient.isModerator() && !IRClient.isAdmin()) {
            throw new CommandException(Formatting.RED + "Только модераторы и администраторы могут использовать эту команду");
        }
        if (!parameters.asString(1).isPresent() ||
                !parameters.asString(2).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc mute <nickname> <минуты> [причина]");
        }
        String nickname = parameters.asString(1).get();
        try {
            int durationMinutes = Integer.parseInt(parameters.asString(2).get());
            if (durationMinutes <= 0) {
                throw new CommandException(Formatting.RED + "Длительность должна быть больше 0 минут");
            }
            String reason = parameters.asString(3).orElse(null);
            IRClient.muteUser(nickname, durationMinutes, reason);
            logger.log(Formatting.GREEN + "Запрос на мут отправлен на сервер...");
        } catch (NumberFormatException e) {
            throw new CommandException(Formatting.RED + "Длительность должна быть числом");
        }
    }

    private void handleUnmute(Parameters parameters, IRClient IRClient) {
        if (!IRClient.isModerator() && !IRClient.isAdmin()) {
            throw new CommandException(Formatting.RED + "Только модераторы и администраторы могут использовать эту команду");
        }
        if (!parameters.asString(1).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc unmute <nickname>");
        }
        String nickname = parameters.asString(1).get();
        IRClient.unmuteUser(nickname);
        logger.log(Formatting.GREEN + "Запрос на снятие мута отправлен на сервер...");
    }

    private void handleSetModerator(Parameters parameters, IRClient IRClient) {
        if (!IRClient.isAdmin()) {
            throw new CommandException(Formatting.RED + "Только администраторы могут использовать эту команду");
        }
        if (!parameters.asString(1).isPresent() ||
                !parameters.asString(2).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc setmoderator <username> <true/false>");
        }
        String username = parameters.asString(1).get();
        String isModeratorStr = parameters.asString(2).get().toLowerCase();
        boolean isModerator = isModeratorStr.equals("true") || isModeratorStr.equals("1") || isModeratorStr.equals("yes");
        IRClient.setModerator(username, isModerator);
        logger.log(Formatting.GREEN + "Запрос на изменение прав модератора отправлен на сервер...");
    }

    private void handleSetAdmin(Parameters parameters, IRClient IRClient) {
        if (!IRClient.isAdmin()) {
            throw new CommandException(Formatting.RED + "Только администраторы могут использовать эту команду");
        }
        if (!parameters.asString(1).isPresent() ||
                !parameters.asString(2).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc setadmin <username> <true/false>");
        }
        String username = parameters.asString(1).get();
        String isAdminStr = parameters.asString(2).get().toLowerCase();
        boolean isAdmin = isAdminStr.equals("true") || isAdminStr.equals("1") || isAdminStr.equals("yes");
        IRClient.setAdmin(username, isAdmin);
        logger.log(Formatting.GREEN + "Запрос на изменение прав администратора отправлен на сервер...");
    }

    private void handleRaffle(Parameters parameters, IRClient IRClient) {
        if (!parameters.asString(1).isPresent()) {
            throw new CommandException(Formatting.RED + "Использование: .irc raffle join");
        }
        String subcommand = parameters.asString(1).get().toLowerCase();
        if (subcommand.equals("join")) {
            IRClient.joinRaffle();
            logger.log(Formatting.GREEN + "Запрос на участие в розыгрыше отправлен на сервер...");
        } else {
            throw new CommandException(Formatting.RED + "Использование: .irc raffle join");
        }
    }

    private void handlePrivateMessage(Parameters parameters, IRClient IRClient) {
        String nicknameArgument = parameters.asString(1).orElse("");
        if (nicknameArgument.trim().isEmpty()) {
            if (!IRClient.isLoggedIn()) {
                throw new CommandException(Formatting.RED + "Вы должны войти в аккаунт для использования этой команды!");
            }
            logger.log(Formatting.YELLOW + "Запрос списка онлайн пользователей...");
            String selfName = IRClient.getCurrentNickname();
            showOnlineUsersList(
                    "Онлайн пользователи IRC (кликните для отправки сообщения):",
                    user -> prefix.get() + "irc pm " + user.getNickname() + " ",
                    user -> selfName != null && user.getNickname() != null && user.getNickname().equalsIgnoreCase(selfName),
                    user -> "Кликните, чтобы отправить сообщение " + user.getDisplayName()
            );
            return;
        }

        String nickname = nicknameArgument.trim();
        String message = parameters.collectMessage(2);
        if (message.isEmpty()) {
            throw new CommandException(Formatting.RED + "Укажите сообщение для отправки");
        }
        IRClient.sendPrivateMessage(nickname, message);
        logger.log(Formatting.GRAY + "Личное сообщение отправлено " + Formatting.AQUA + nickname + Formatting.GRAY + ": " + Formatting.WHITE + message);
    }

    private void showOnlineUsersList(String title,
                                     Function<IRClient.OnlineUser, String> commandBuilder,
                                     Predicate<IRClient.OnlineUser> skipPredicate,
                                     Function<IRClient.OnlineUser, String> hoverBuilder) {
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client == null || !client.isOpen()) {
            logger.log(Formatting.RED + "IRC не подключён.");
            return;
        }
        client.requestOnlineUsers(users -> {
            if (mc == null) {
                return;
            }
            mc.execute(() -> {
                if (users == null || users.isEmpty()) {
                    logger.log(Formatting.GRAY + "Нет онлайн пользователей IRC.");
                    return;
                }
                MutableText message = Text.literal(client.getChatPrefix())
                        .append(Text.literal(title).formatted(Formatting.WHITE));
                int appended = 0;
                for (IRClient.OnlineUser user : users) {
                    if (user == null) {
                        continue;
                    }
                    if (skipPredicate != null && skipPredicate.test(user)) {
                        continue;
                    }
                    String displayName = user.getDisplayName();
                    MutableText line = Text.literal("\n§7- ")
                            .append(Text.literal(displayName).formatted(Formatting.AQUA));
                    String label = user.getClientLabel();
                    if (label != null && !label.isBlank()) {
                        line.append(Text.literal(" [" + label + "]").formatted(Formatting.DARK_GRAY));
                    }
                    String command = commandBuilder != null ? commandBuilder.apply(user) : null;
                    if (command != null && !command.isBlank()) {
                        String hoverText = hoverBuilder != null
                                ? hoverBuilder.apply(user)
                                : "Кликните, чтобы выбрать " + displayName;
                        line = line.styled(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(ensureRunCommand(command)))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText))));
                    }
                    message.append(line);
                    appended++;
                }
                if (appended == 0) {
                    logger.log(Formatting.GRAY + "Нет доступных пользователей.");
                } else if (mc.inGameHud != null) {
                    mc.inGameHud.getChatHud().addMessage(message);
                }
            });
        });
    }

    private List<String> availableSubCommands() {
        if (!IRC.isActive()) {
            return List.of();
        }
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client == null || !client.isOpen()) {
            return BASIC_COMMANDS;
        }
        if (!client.isLoggedIn()) {
            return CONNECTED_COMMANDS;
        }
        return AUTHORIZED_COMMANDS;
    }

    private String ensureRunCommand(String command) {
        if (command == null || command.isBlank()) {
            return "/";
        }
        String withoutLeading = command.stripLeading();
        if (withoutLeading.isEmpty()) {
            return "/";
        }
        if (withoutLeading.startsWith("/") || withoutLeading.startsWith(prefix.get())) {
            return withoutLeading;
        }
        return "/" + withoutLeading;
    }

    private MutableText buildMutedUserLine(String entry, String commandPrefix) {
        if (entry == null || entry.isBlank()) {
            return Text.literal("§7- §fнеизвестно");
        }
        String trimmed = entry.trim();
        String author = trimmed;
        String client = "";
        String remainder = "";
        int open = trimmed.indexOf(" (");
        int close = open >= 0 ? trimmed.indexOf(')', open + 2) : -1;
        if (open >= 0 && close > open) {
            author = trimmed.substring(0, open);
            client = trimmed.substring(open + 2, close);
            if (close + 1 < trimmed.length()) {
                remainder = trimmed.substring(close + 1).trim();
            }
        }

        MutableText line = Text.literal("§7- ")
                .append(Text.literal(author).formatted(Formatting.AQUA));
        if (!client.isEmpty()) {
            line.append(Text.literal(" (" + client + ")").formatted(Formatting.DARK_GRAY));
        }
        if (!remainder.isEmpty()) {
            line.append(Text.literal(" " + remainder).formatted(Formatting.GRAY));
        }

        if (!client.isEmpty() && !author.isEmpty()) {
            final String commandAuthor = author;
            final String commandClient = client;
            String command = ensureRunCommand(commandPrefix + "irc ignore remove " + commandClient + " " + commandAuthor);
            line = line.styled(style -> style
                    .withClickEvent(new ClickEvent.SuggestCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.literal("Кликните, чтобы удалить " + commandAuthor + " из списка"))));
        }
        return line;
    }

    private List<String> pmNicknameSuggestions() {
        IRClient client = LynxWave.getInstance().getIRClient();
        String selfIrc = client != null ? client.getCurrentNickname() : null;
        String selfGame = null;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient != null && minecraftClient.player != null && minecraftClient.player.getGameProfile() != null) {
            selfGame = minecraftClient.player.getGameProfile().name();
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();

        if (client != null && client.isOpen()) {
            addSuggestions(ordered, seen, client.getOnlineNicknamesForSuggestions(), selfIrc, selfGame);
            if (ordered.isEmpty() && client.isLoggedIn()) {
                client.refreshOnlineUsersCache();
                addSuggestions(ordered, seen, client.getOnlineNicknamesForSuggestions(), selfIrc, selfGame);
            }
        }

        if (ordered.isEmpty()) {
            addSuggestions(ordered, seen, fallbackIrcNicknameSuggestions(selfGame), selfIrc, selfGame);
        }

        if (ordered.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(ordered);
    }

    private void addSuggestions(LinkedHashSet<String> target,
                                Set<String> seen,
                                List<String> candidates,
                                String selfIrc,
                                String selfGame) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (selfIrc != null && candidate.equalsIgnoreCase(selfIrc)) {
                continue;
            }
            if (selfGame != null && candidate.equalsIgnoreCase(selfGame)) {
                continue;
            }
            String lowered = candidate.toLowerCase(Locale.ROOT);
            if (seen.add(lowered)) {
                target.add(candidate);
                if (target.size() >= 20) {
                    break;
                }
            }
        }
    }

    private List<String> fallbackIrcNicknameSuggestions(String selfGame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return List.of();
        }
        ClientLabelRegistry registry = ClientLabelRegistry.getInstance();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        client.world.getPlayers().forEach(player -> {
            if (player == null || player.getGameProfile() == null) {
                return;
            }
            String name = player.getGameProfile().name();
            if (name == null || name.isBlank()) {
                return;
            }
            if (selfGame != null && name.equalsIgnoreCase(selfGame)) {
                return;
            }
            if (registry.labelForPlayer(name) == null) {
                return;
            }
            names.add(name);
        });
        return new ArrayList<>(names);
    }

    private List<String> ircNicknameSuggestionsOrPlaceholder(String placeholder) {
        List<String> suggestions = pmNicknameSuggestions();
        if (!suggestions.isEmpty()) {
            return suggestions;
        }
        if (placeholder == null || placeholder.isBlank()) {
            return List.of();
        }
        return List.of(placeholder);
    }

    private boolean handleUnknownSubCommand(Parameters parameters, String commandType, List<String> availableCommands) {
        if (commandType == null || commandType.isBlank()) {
            return false;
        }
        String remainder = parameters.collectMessage(1);
        if (remainder != null && !remainder.isBlank()) {
            return false;
        }
        String suggestion = findClosestSubCommand(commandType, availableCommands);
        if (suggestion == null) {
            return false;
        }
        String assistsCommand = prefix.get() + "assists irc";
        logger.log(Formatting.RED + "Укажите тип команды. Для помощи используйте "
                + Formatting.YELLOW + assistsCommand + Formatting.RED + ".");
        logger.log(Formatting.GRAY + "Возможно, вы имели в виду: "
                + Formatting.YELLOW + prefix.get() + "irc " + suggestion);
        return true;
    }

    private String findClosestSubCommand(String input, List<String> availableCommands) {
        if (availableCommands == null || availableCommands.isEmpty()) {
            return null;
        }
        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;
        for (String candidate : availableCommands) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int distance = levenshteinDistance(input, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }
        int threshold = input.length() <= 3 ? 1 : 2;
        return bestDistance <= threshold ? bestMatch : null;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private void sendUsage(Logger logger) {
        String base = prefix.get();
        logSuggestion(logger, base + "irc <сообщение>", "Отправить сообщение в IRC");
        IRClient client = LynxWave.getInstance().getIRClient();
        boolean connected = client != null && client.isOpen();
        boolean loggedIn = connected && client.isLoggedIn();
        if (!connected || !loggedIn) {
            logSuggestion(logger, base + "irc register <username> <password>", "Зарегистрироваться в IRC");
            logSuggestion(logger, base + "irc login <username> <password>", "Войти в IRC");
        }
        if (loggedIn) {
            logSuggestion(logger, base + "irc pm <никнейм> <сообщение>", "Отправить личное сообщение");
            logSuggestion(logger, base + "irc prefix", "Открыть список префиксов IRC");
            logSuggestion(logger, base + "irc party help", "Команды управления пати");
        }
    }

    private void logSuggestion(Logger logger, String suggestion, String description) {
        MutableText clickable = Text.literal(suggestion)
                .formatted(Formatting.YELLOW)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal(description + "\n§7Кликните для автозаполнения"))));
        if (description != null && !description.isBlank()) {
            clickable = clickable.append(Text.literal(" - " + description).formatted(Formatting.GRAY));
        }
        logger.log(clickable);
    }
}
