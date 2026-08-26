package dev.client.api.injection;

import com.google.common.base.Strings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.StandaloneCommandDispatcher;
import dev.client.api.nullcry.cmdHelper.interfaces.Command;
import dev.client.api.nullcry.cmdHelper.interfaces.CommandWithAdvice;
import dev.client.modules.core.render.Chat;
import dev.client.modules.core.player.AutoPilot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.OrderedText;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixins {
    @Unique
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");

    @Shadow
    @Final
    TextFieldWidget textField;
    @Shadow
    @Nullable
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Shadow
    @Final
    private List<OrderedText> messages;
    @Shadow
    @Nullable
    private ParseResults<ClientCommandSource> parse;
    @Shadow
    boolean completingSuggestions;
    @Shadow
    @Nullable
    private ChatInputSuggestor.SuggestionWindow window;
    @Shadow
    @Final
    private boolean slashOptional;
    @Shadow
    @Final
    MinecraftClient client;
    @Shadow
    @Final
    private boolean suggestingWhenEmpty;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        Chat chat = Chat.INSTANCE;
        if (chat != null && chat.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void onRefresh(CallbackInfo ci) {
        String string = this.textField.getText();
        if (this.parse != null && !this.parse.getReader().getString().equals(string)) {
            this.parse = null;
        }

        if (!this.completingSuggestions) {
            this.textField.setSuggestion(null);
            this.window = null;
        }

        this.messages.clear();
        StringReader stringReader = new StringReader(string);
        boolean bl = stringReader.canRead() && stringReader.peek() == '/';
        if (bl) {
            stringReader.skip();
        }

        boolean bl2 = this.slashOptional || bl;
        int i = this.textField.getCursor();
        if (bl2) {
            CommandDispatcher<ClientCommandSource> commandDispatcher = this.client.player.networkHandler.getCommandDispatcher();
            if (this.parse == null) {
                this.parse = commandDispatcher.parse(stringReader, this.client.player.networkHandler.getCommandSource());
            }

            int j = this.suggestingWhenEmpty ? stringReader.getCursor() : 1;
            if (i >= j && (this.window == null || !this.completingSuggestions)) {
                this.pendingSuggestions = commandDispatcher.getCompletionSuggestions(this.parse, i);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) {
                        this.showCommandSuggestions();
                    }
                });
            }
        } else {
            String string2 = string.substring(0, i);
            int j = getStartOfCurrentWord(string2);
            Collection<String> collection = this.client.player.networkHandler.getCommandSource().getChatSuggestions();
            this.pendingSuggestions = CommandSource.suggestMatching(collection, new SuggestionsBuilder(string2, j));
        }

        StandaloneCommandDispatcher dispatcher = LynxWave.getInstance().getCmdInitializer().getCommandDispatcher();
        String pfx = dispatcher.getPrefix().get();

        boolean flagClient = !pfx.isEmpty() && string.startsWith(pfx);

        if (flagClient) {
            if (string.length() >= pfx.length() + 1
                    && string.startsWith(pfx + " ")
                    && string.trim().equals(pfx)) {
                this.pendingSuggestions = Suggestions.empty();
                this.show(true);
                ci.cancel();
                return;
            }

            String payload = string.substring(pfx.length());
            String[] args = payload.split(" ");
            List<String> completions = new ArrayList<>();
            boolean endsWithSpace = payload.endsWith(" ");

            if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
                dispatcher.getCommandMap().keySet().stream()
                        .filter(ChatInputSuggestorMixins::isClientCommandVisible)
                        .forEach(completions::add);
            } else {
                String commandName = args[0];
                Command command = dispatcher.command(commandName);

                if (!isClientCommandVisible(commandName)) {
                    command = null;
                }

                if (command == null) {
                    if (!endsWithSpace) {
                        dispatcher.getCommandMap().keySet().stream()
                                .filter(ChatInputSuggestorMixins::isClientCommandVisible)
                                .filter(cmd -> cmd.toLowerCase().startsWith(commandName.toLowerCase()))
                                .forEach(completions::add);
                    }

                } else if (command instanceof CommandWithAdvice adviceCommand) {
                    if (args.length == 1 && endsWithSpace) {
                        completions.addAll(adviceCommand.parametersCommand());

                    } else if (args.length == 2) {
                        String partialSubCommand = args[1];

                        if (!endsWithSpace) {
                            if (isNumericInput(partialSubCommand)) {
                                completions.addAll(adviceCommand.parametersCommand());
                            } else {
                                adviceCommand.parametersCommand().stream()
                                        .filter(param -> param.toLowerCase().startsWith(partialSubCommand.toLowerCase()))
                                        .forEach(completions::add);
                            }
                        } else {
                            completions.addAll(adviceCommand.firstArguments(partialSubCommand));
                        }

                    } else if (args.length >= 3) {
                        String subCommand = args[1];
                        List<String> previousArgs = List.of(args)
                                .subList(2, args.length - (endsWithSpace ? 0 : 1));
                        int step = previousArgs.size();

                        List<String> nextArgs = adviceCommand.getArguments(subCommand, step, previousArgs);

                        if (!endsWithSpace) {
                            String currentArg = args[args.length - 1];
                            if (isNumericInput(currentArg)) {
                                completions.addAll(nextArgs);
                            } else {
                                nextArgs.stream()
                                        .filter(arg -> arg.toLowerCase().startsWith(currentArg.toLowerCase()))
                                        .forEach(completions::add);
                            }
                        } else {
                            completions.addAll(nextArgs);
                        }
                    }
                }
            }

            List<String> suggestions = completions.stream()
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            int j = getClientTokenStart(string, i, pfx);
            this.pendingSuggestions = CommandSource.suggestMatching(suggestions, new SuggestionsBuilder(string, j));
            this.show(true);
            ci.cancel();
        }
    }

    @Unique
    private static int getStartOfCurrentWord(String input) {
        if (Strings.isNullOrEmpty(input)) {
            return 0;
        } else {
            int i = 0;

            for (Matcher matcher = WHITESPACE_PATTERN.matcher(input); matcher.find(); i = matcher.end()) {
            }

            return i;
        }
    }

    @Unique
    private static int getClientTokenStart(String full, int cursor, String prefix) {
        int min = Math.min(Math.max(cursor, 0), full.length());
        int start = prefix.length();
        int j = start;
        for (int k = min - 1; k >= start; k--) {
            if (Character.isWhitespace(full.charAt(k))) {
                j = k + 1;
                break;
            }
        }
        return Math.max(j, start);
    }

    @Unique
    private static boolean isNumericInput(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input.equals("-")) {
            return true;
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (i == 0 && c == '-') {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static boolean isClientCommandVisible(String name) {
        if (!"autopilot".equalsIgnoreCase(name)) {
            return true;
        }
        return AutoPilot.INSTANCE != null && AutoPilot.INSTANCE.isEnabled();
    }
}
