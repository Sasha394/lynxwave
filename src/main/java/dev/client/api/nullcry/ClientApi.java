package dev.client.api.nullcry;

import dev.client.LynxWave;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.core.*;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public interface ClientApi {
    MinecraftClient mc = MinecraftClient.getInstance();
    List<Text> printMessageClient = new ArrayList<>();

    default MutableText gradient(String message, int colorStart) {
        int base = (Interface.INSTANCE != null) ? Interface.INSTANCE.getFirstColor() : colorStart;
        int dark = darkenRgb(base, 0.30f);

        int len = message.length();
        float phase = (System.currentTimeMillis() % 2000L) / 2000f;

        MutableText text = Text.literal("");
        for (int i = 0; i < len; i++) {
            float pos = len > 1 ? (float) i / (float) (len - 1) : 0f;
            float t = (float) (Math.sin((pos + phase) * Math.PI * 2.0) * 0.5 + 0.5);
            int color = ColorUtils.interpolate(dark, base, t);
            text.append(Text.literal(String.valueOf(message.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0x00FFFFFF))));
        }
        return text;
    }

    private static int darkenRgb(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int r = Math.round(((color >>> 16) & 0xFF) * f);
        int g = Math.round(((color >>> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    default void printClient(String input) {
        if (mc == null || mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;

        String clientLabel = LynxWave.getInstance().getClientInfo().getName() + " client";
        int themeColor = (Interface.INSTANCE != null) ? Interface.INSTANCE.getMainColor() : 0xFFFFFF;
        MutableText prefix = gradient(
                clientLabel,
                themeColor
        ).copy().setStyle(Style.EMPTY.withBold(true));

        MutableText sep = Text.literal(" -> ").setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false));
        MutableText msg = Text.literal(input);
        MutableText finalText = Text.empty()
                .append(prefix)
                .append(sep)
                .append(msg);

        printMessageClient.add(finalText);
        mc.inGameHud.getChatHud().addMessage(finalText);
    }

    default void printClient(Text input) {
        if (mc == null || mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;

        String clientLabel = LynxWave.getInstance().getClientInfo().getName() + " client";
        int themeColor = (Interface.INSTANCE != null) ? Interface.INSTANCE.getMainColor() : 0xFFFFFF;
        MutableText prefix = gradient(
                clientLabel,
                themeColor
        ).copy().setStyle(Style.EMPTY.withBold(true));

        MutableText sep = Text.literal(" -> ")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false));

        MutableText finalText = Text.empty()
                .append(prefix)
                .append(sep)
                .append(input.copy());

        printMessageClient.add(finalText);
        mc.inGameHud.getChatHud().addMessage(finalText);
    }

    default void printChat(String message) {
        if (mc == null || mc.player == null) return;

        if (message.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(message.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(message);
        }
    }

    default void debug(Object msg) {
        String str = msg + "";
        try {
            if (str.contains("PHP_EOL")) {
                String[] msgs = str.split("PHP_EOL");
                for (String message : msgs) {
                    mc.inGameHud.getChatHud().addMessage(Text.literal(message));
                }
                return;
            }
            mc.inGameHud.getChatHud().addMessage(Text.literal(Formatting.AQUA + "[Debug] " + Formatting.RESET + msg));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default int calc(int value) {
        Window rs = mc.getWindow();
        return (int) (value * rs.getScaleFactor() / 2);
    }
}
