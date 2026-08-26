package dev.client.cmd.core.util;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.function.UnaryOperator;

public final class CommandTextUtil {
    private CommandTextUtil() {
    }

    public static MutableText bracketedButton(String label, Formatting color, ClickEvent.Action clickAction, String clickValue, String hoverText) {
        MutableText button = Text.literal(" [").formatted(Formatting.DARK_GRAY);
        MutableText labelText = Text.literal(label).formatted(color);

        labelText = labelText.styled(applyInteractions(clickAction, clickValue, hoverText));

        button.append(labelText);
        button.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        return button;
    }

    public static MutableText hintLine(String message, String hoverText, ClickEvent.Action action, String command) {
        MutableText text = Text.literal(message);
        text = text.styled(applyInteractions(action, command, hoverText));
        return text;
    }

    private static UnaryOperator<Style> applyInteractions(ClickEvent.Action action, String value, String hoverText) {
        return style -> {
            Style result = style.withItalic(false);

            if (hoverText != null && !hoverText.isEmpty()) {
                result = result.withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText)));
            }

            if (action != null && value != null && !value.isEmpty()) {
                ClickEvent clickEvent = createClickEvent(action, value);
                if (clickEvent != null) {
                    result = result.withClickEvent(clickEvent);
                }
            }

            return result;
        };
    }

    private static ClickEvent createClickEvent(ClickEvent.Action action, String value) {
        return switch (action) {
            case OPEN_URL -> parseUrlClick(value);
            case RUN_COMMAND -> new ClickEvent.RunCommand(value);
            case SUGGEST_COMMAND -> new ClickEvent.SuggestCommand(value);
            case COPY_TO_CLIPBOARD -> new ClickEvent.CopyToClipboard(value);
            case OPEN_FILE -> new ClickEvent.OpenFile(value);
            case CHANGE_PAGE -> parsePageClick(value);
            default -> null;
        };
    }

    private static ClickEvent parsePageClick(String value) {
        try {
            return new ClickEvent.ChangePage(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ClickEvent parseUrlClick(String value) {
        try {
            return new ClickEvent.OpenUrl(java.net.URI.create(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
