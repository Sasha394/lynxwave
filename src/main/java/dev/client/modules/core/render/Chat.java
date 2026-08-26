package dev.client.modules.core.render;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.client.api.injection.accessor.IChatHudAccessor;
import dev.client.api.injection.accessor.IChatInputSuggestorAccessor;
import dev.client.api.injection.accessor.IChatScreenAccessor;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Chat extends Module {
    public static Chat INSTANCE;

    private final Slider visibleMessages = new Slider("Видимые сообщения", () -> true)
            .set(5.0f, 30.0f, 1.0f)
            .defaultValue(10.0f)
            .register(this);

    private final CheckBox fontShadow = new CheckBox("Тень шрифта", () -> true)
            .defaultValue(false)
            .register(this);

    private final CompactAnimation openAnimation = new CompactAnimation(Easing.EASE_OUT_QUART, 600L);

    private float cursorX;
    private ChatHudLine lastNewestMessage;
    private float chatScrollAnimation;
    private float visualLineCount;
    private float scrollOffset;
    private float targetScrollOffset;
    private int totalVisualLines;
    private float suggestionBoxWidth;
    private float chatOffsetX;

    public Chat() {
        super("Chat", ModuleCategory.Render, "Пользовательский рендеринг чата.");
        INSTANCE = this;
    }

    public void onScroll(double delta) {
        targetScrollOffset += (float) (delta * 3.0);
        float maxScroll = Math.max(0.0F, totalVisualLines - visibleMessages.getValue().intValue());
        if (targetScrollOffset < 0.0F) targetScrollOffset = 0.0F;
        if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
    }

    @Subscribe
    private void onRender(RenderEvent.Draw2D event) {
        renderChatHistory(event.getContext());

        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;
        ChatScreen chatScreen = isChatOpen ? (ChatScreen) mc.currentScreen : null;

        openAnimation.setDuration(400L);
        openAnimation.run(isChatOpen ? 1.0 : 0.0);
        float scale = (float) openAnimation.getValue();

        if (scale <= 0.001F) {
            return;
        }

        float typeHeight = 15.0F;
        float typeWidth = 295.0F;
        float typeX = 10.0F;
        float typeY = mc.getWindow().getScaledHeight() - 15.0F - 10.0F;
        float centerX = 157.5F;
        float centerY = typeY + 7.5F;

        float bgWidth = typeWidth * scale;
        float bgHeight = typeHeight * scale;
        float bgX = centerX - bgWidth / 2.0F;
        float bgY = centerY - bgHeight / 2.0F;

        drawRounded(event.getContext(), bgX, bgY, bgWidth, bgHeight, 5.0F * scale, new Color(0, 0, 0, 130));

        suggestionBoxWidth = 0.0F;
        renderSuggestions(event.getContext(), chatScreen, typeX, typeY, scale);

        if (chatScreen == null) {
            return;
        }

        DrawContext ctx = event.getContext();
        int scX = (int) bgX;
        int scY = (int) bgY;
        int scW = Math.max(0, (int) bgWidth);
        int scH = Math.max(0, (int) bgHeight);
        ctx.enableScissor(scX, scY, scX + scW, scY + scH);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(centerX, centerY);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-centerX, -centerY);

        TextFieldWidget chatField = ((IChatScreenAccessor) chatScreen).getChatField();
        if (chatField == null) {
            ctx.getMatrices().popMatrix();
            ctx.disableScissor();
            return;
        }
        String text = chatField.getText();
        if (text == null) text = "";
        int cursor = Math.min(chatField.getCursor(), text.length());
        int selection = extractSelectionIndex(chatField, cursor);
        int start = Math.min(cursor, selection);
        int end = Math.max(cursor, selection);

        if (start != end && start >= 0 && end <= text.length()) {
            String textBeforeCursor = text.substring(0, start);
            String selectedText = text.substring(start, end);
            int alpha = mc.textRenderer.getWidth(textBeforeCursor);
            int color = mc.textRenderer.getWidth(selectedText);
            ctx.fill((int) (15.0F + alpha), (int) (typeY + 3.0F), (int) (15.0F + alpha + color), (int) (typeY + 13.0F), new Color(60, 60, 255, 100).getRGB());
        }

        ctx.drawText(mc.textRenderer, text, 15, (int) (typeY + 4.0F), 0xFFFFFFFF, fontShadow.getEnabled());

        String textBeforeCursor = text.substring(0, cursor);
        float targetX = mc.textRenderer.getWidth(textBeforeCursor) + 1.0F;
        cursorX = (float) (cursorX + (targetX - cursorX) * 0.15);

        int alpha = (int) (255.0 * Math.abs(Math.sin(System.currentTimeMillis() / 500.0)));
        int cursorColor = (alpha << 24) | 0xFFFFFF;
        ctx.drawText(mc.textRenderer, "_",
                (int) (15.0F + cursorX),
                (int) (typeY + 3.0F + (cursor == text.length() ? 0.0F : 2.0F)),
                cursorColor,
                fontShadow.getEnabled()
        );

        ctx.getMatrices().popMatrix();
        ctx.disableScissor();

        double mouseX = mc.mouse.getX() * (double) mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * (double) mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        Style style = getStyleAt(mouseX, mouseY);
        if (style != null && style.getHoverEvent() != null) {
            ctx.drawHoverEvent(mc.textRenderer, style, (int) mouseX, (int) mouseY);
        }
    }

    private void renderSuggestions(DrawContext ctx, ChatScreen chatScreen, float typeX, float typeY, float scale) {
        if (chatScreen == null) return;

        ChatInputSuggestor suggestor = ((IChatScreenAccessor) chatScreen).getChatInputSuggestor();
        if (suggestor == null) return;

        ChatInputSuggestor.SuggestionWindow window = ((IChatInputSuggestorAccessor) suggestor).getWindow();
        if (window == null) return;

        List<Suggestion> suggestions = extractSuggestionList(window);
        if (suggestions == null || suggestions.isEmpty()) return;

        int currentIndex = Math.max(0, Math.min(extractSuggestionSelection(window), suggestions.size() - 1));
        int maxVisible = Math.min(suggestions.size(), 10);
        int startIndex = Math.max(0, Math.min(currentIndex - maxVisible / 2, suggestions.size() - maxVisible));

        float lineHeight = 12.0F;
        float padding = 4.0F;
        float maxWidth = 0.0F;

        for (int i = startIndex; i < Math.min(startIndex + maxVisible, suggestions.size()); ++i) {
            String text = getSuggestionText(suggestions.get(i));
            float width = mc.textRenderer.getWidth(text);
            if (width > maxWidth) maxWidth = width;
        }

        float boxWidth = Math.max(maxWidth + padding * 2.0F, 80.0F);
        float height = (float) maxVisible * lineHeight + padding * 2.0F;
        float boxY = typeY - height - 10.0F;

        drawRounded(ctx, typeX, boxY, boxWidth, height, 5.0F, new Color(0, 0, 0, 130));
        suggestionBoxWidth = boxWidth + 5.0F;

        for (int i = 0; i < maxVisible; ++i) {
            int idx = startIndex + i;
            if (idx >= suggestions.size()) break;
            Suggestion suggestion = suggestions.get(idx);
            String text = getSuggestionText(suggestion);
            float drawY = boxY + padding + (float) i * lineHeight;
            int textColor = idx == currentIndex ? new Color(255, 251, 0).getRGB() : 0xFFFFFFFF;
            if (idx == currentIndex) {
                ctx.fill((int) typeX, (int) drawY, (int) (typeX + boxWidth), (int) (drawY + lineHeight - 1.0F), new Color(255, 255, 255, 30).getRGB());
            }
            ctx.drawText(mc.textRenderer, text, (int) (typeX + padding), (int) drawY + 1, textColor, fontShadow.getEnabled());
        }
    }

    private void renderChatHistory(DrawContext ctx) {
        ChatHud chatHud = mc.inGameHud.getChatHud();
        List<ChatHudLine> messages;
        synchronized (((IChatHudAccessor) chatHud).getMessages()) {
            messages = new ArrayList<>(((IChatHudAccessor) chatHud).getMessages());
        }

        if (messages.isEmpty()) return;

        ChatHudLine currentNewest = messages.getFirst();
        if (lastNewestMessage != currentNewest) {
            if (lastNewestMessage != null && messages.contains(lastNewestMessage)) {
                int newCount = 0;
                for (ChatHudLine m : messages) {
                    if (m == lastNewestMessage) break;
                    Text content = m.content();
                    List<OrderedText> split = mc.textRenderer.wrapLines(content, 279);
                    newCount += split.size();
                }
                chatScrollAnimation += (float) (newCount * 10);
            } else {
                chatScrollAnimation = 0.0F;
            }
            lastNewestMessage = currentNewest;
        }

        chatScrollAnimation = (float) ((double) chatScrollAnimation * 0.85);
        if ((double) chatScrollAnimation < 0.1) {
            chatScrollAnimation = 0.0F;
        }

        float chatWidth = 295.0F;
        float scale = (float) openAnimation.getValue();
        float y = (mc.getWindow().getScaledHeight() - 45.0F) + (1.0F - scale) * 20.0F;
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        if (!chatOpen) {
            scrollOffset = 0.0F;
            targetScrollOffset = 0.0F;
            suggestionBoxWidth = 0.0F;
        }

        scrollOffset += (targetScrollOffset - scrollOffset) * 0.3F;
        chatOffsetX += (suggestionBoxWidth - chatOffsetX) * 0.15F;

        int currentTick = mc.inGameHud.getTicks();
        int targetLineCount = 0;
        int liveLineCount = 0;
        List<OrderedText> allVisualLines = new ArrayList<>();

        for (ChatHudLine msg : messages) {
            if (msg == null) continue;

            boolean expired = !chatOpen && currentTick - msg.creationTick() > 200;
            Text content = msg.content();
            List<OrderedText> lines = mc.textRenderer.wrapLines(content, (int) chatWidth - 16);

            for (OrderedText seq : Lists.reverse(lines)) {
                allVisualLines.add(seq);
                if (!expired) {
                    ++targetLineCount;
                    ++liveLineCount;
                }
            }
        }

        totalVisualLines = chatOpen ? allVisualLines.size() : liveLineCount;
        if (chatOpen) {
            targetLineCount = Math.min(allVisualLines.size(), visibleMessages.getValue().intValue());
        } else {
            targetLineCount = Math.min(targetLineCount, visibleMessages.getValue().intValue());
        }

        visualLineCount = (float) ((double) visualLineCount + ((float) targetLineCount - visualLineCount) * 0.1);
        int renderCount = (int) Math.ceil((double) visualLineCount);
        renderCount = Math.min(renderCount, allVisualLines.size());

        if (renderCount <= 0 && (double) visualLineCount < 0.1) return;

        int scrollStart = Math.max(0, Math.min((int) scrollOffset, allVisualLines.size()));
        int scrollEnd = Math.max(scrollStart, Math.min(scrollStart + renderCount, allVisualLines.size()));
        List<OrderedText> linesToRender = allVisualLines.subList(scrollStart, scrollEnd);

        float totalHeight = visualLineCount * 10.0F;
        float rectBottom = y + 10.0F;
        float rectTop = rectBottom - totalHeight - 3.0F;
        float rectHeight = rectBottom - rectTop;
        if (rectHeight <= 6.0F) return;

        drawRounded(ctx, 10.0F + chatOffsetX, rectTop, chatWidth, rectHeight, 5.0F, new Color(0, 0, 0, 130));

        float finalScrollAnimation = chatScrollAnimation;
        float finalScrollOffset = scrollOffset;
        float finalChatOffsetX = chatOffsetX;

        int scMinY = (int) (rectTop + 1.5F);
        int scMaxY = (int) (rectTop + rectHeight);
        ctx.enableScissor((int) (10.0F + finalChatOffsetX), scMinY, (int) (10.0F + finalChatOffsetX + chatWidth), scMaxY);

        for (int i = 0; i < linesToRender.size(); ++i) {
            OrderedText seq = linesToRender.get(i);
            float scrollFraction = finalScrollOffset - (float) scrollStart;
            float drawY = y - (float) (i * 10) + finalScrollAnimation + scrollFraction * 10.0F;
            ctx.drawText(mc.textRenderer, seq, (int) (14.0F + finalChatOffsetX), (int) drawY, 0xFFFFFFFF, fontShadow.getEnabled());
        }

        ctx.disableScissor();
    }

    public Style getStyleAt(double mouseX, double mouseY) {
        ChatHud chatHud = mc.inGameHud.getChatHud();
        List<ChatHudLine> messages;
        synchronized (((IChatHudAccessor) chatHud).getMessages()) {
            messages = new ArrayList<>(((IChatHudAccessor) chatHud).getMessages());
        }

        if (messages.isEmpty()) return null;

        float chatWidth = 295.0F;
        float scale = (float) openAnimation.getValue();
        float y = (mc.getWindow().getScaledHeight() - 45.0F) + (1.0F - scale) * 20.0F;
        List<OrderedText> allVisualLines = new ArrayList<>();

        for (ChatHudLine msg : messages) {
            if (msg == null) continue;
            Text content = msg.content();
            List<OrderedText> split = mc.textRenderer.wrapLines(content, (int) chatWidth - 16);
            allVisualLines.addAll(Lists.reverse(split));
        }

        int renderCount = (int) Math.ceil((double) visualLineCount);
        renderCount = Math.min(renderCount, allVisualLines.size());
        if (renderCount <= 0 && (double) visualLineCount < 0.1) return null;

        int scrollStart = Math.max(0, Math.min((int) scrollOffset, allVisualLines.size()));
        int scrollEnd = Math.max(scrollStart, Math.min(scrollStart + renderCount, allVisualLines.size()));
        List<OrderedText> linesToRender = allVisualLines.subList(scrollStart, scrollEnd);

        float totalHeight = visualLineCount * 10.0F;
        float rectBottom = y + 10.0F;
        float rectTop = rectBottom - totalHeight - 3.0F;
        float rectHeight = rectBottom - rectTop;
        int scMinY = (int) (rectTop + 1.5F);
        int scMaxY = (int) (rectTop + rectHeight);

        if (mouseY < scMinY || mouseY > scMaxY) return null;
        if (mouseX < (10.0F + chatOffsetX) || mouseX > (10.0F + chatOffsetX + chatWidth)) return null;

        float finalScrollAnimation = chatScrollAnimation;
        float finalScrollOffset = scrollOffset;
        float finalChatOffsetX = chatOffsetX;

        for (int i = 0; i < linesToRender.size(); ++i) {
            OrderedText seq = linesToRender.get(i);
            float scrollFraction = finalScrollOffset - (float) scrollStart;
            float drawY = y - (float) (i * 10) + finalScrollAnimation + scrollFraction * 10.0F;
            if (mouseY >= drawY && mouseY < (drawY + 10.0F)) {
                float relativeX = (float) (mouseX - (14.0F + finalChatOffsetX));
                return getStyleFromSeq(seq, (int) relativeX);
            }
        }

        return null;
    }

    private Style getStyleFromSeq(OrderedText seq, int x) {
        final float[] width = new float[]{0.0F};
        final Style[] foundStyle = new Style[]{null};
        seq.accept((i, style, codePoint) -> {
            String s = new String(Character.toChars(codePoint));
            float charWidth = mc.textRenderer.getWidth(Text.literal(s).setStyle(style));
            if (width[0] + charWidth >= (float) x) {
                foundStyle[0] = style;
                return false;
            } else {
                width[0] += charWidth;
                return true;
            }
        });
        return foundStyle[0];
    }

    private void drawRounded(DrawContext ctx, float x, float y, float w, float h, float radius, Color color) {
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(w, h))
                .color(new QuadColorState(color.getRGB()))
                .radius(new QuadRadiusState(radius))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), x, y);
    }

    private static String getSuggestionText(Suggestion s) {
        try {
            return s.getText();
        } catch (Exception e) {
            return s.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Suggestion> extractSuggestionList(ChatInputSuggestor.SuggestionWindow window) {
        for (String field : List.of("suggestions", "suggestionList", "suggestion", "completions")) {
            try {
                Field f = window.getClass().getDeclaredField(field);
                f.setAccessible(true);
                Object v = f.get(window);
                if (v instanceof Suggestions s) return s.getList();
                if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Suggestion) return (List<Suggestion>) list;
            } catch (Exception ignored) {
            }
        }
        for (Field f : window.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object v = f.get(window);
                if (v instanceof Suggestions s) return s.getList();
                if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Suggestion) return (List<Suggestion>) list;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int extractSuggestionSelection(ChatInputSuggestor.SuggestionWindow window) {
        for (String field : List.of("selection", "inWindowIndex", "selectedSuggestion", "current", "index", "selected")) {
            try {
                Field f = window.getClass().getDeclaredField(field);
                f.setAccessible(true);
                Object v = f.get(window);
                if (v instanceof Integer i) return i;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static int extractSelectionIndex(TextFieldWidget field, int fallback) {
        for (String method : List.of("getSelectionStart", "getSelectionEnd", "getSelection")) {
            try {
                Method m = field.getClass().getMethod(method);
                Object v = m.invoke(field);
                if (v instanceof Integer i) return i;
            } catch (Exception ignored) {
            }
        }

        for (String name : List.of("selectionStart", "selectionEnd", "selection", "highlightPos", "selectionPos")) {
            try {
                Field f = field.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(field);
                if (v instanceof Integer i) return i;
            } catch (Exception ignored) {
            }
        }

        return fallback;
    }

}

