package dev.client.api.nullcry.helper.client.irc;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.misc.IRC;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IRChatScreen extends Screen implements ClientApi {

    private enum Tab { CHAT, FRIENDS, ONLINE, SETTINGS }
    private enum AuthMode { LOGIN, REGISTER }
    private enum Field { NONE, USERNAME, PASSWORD, MESSAGE, DM }

    // Sidebar tabs (shared by render + hit-test so they never drift)
    private static final Tab[] SIDEBAR_TABS = { Tab.CHAT, Tab.FRIENDS, Tab.ONLINE, Tab.SETTINGS };
    private static final String[] SIDEBAR_GLYPHS = { "Ч", "Д", "О", "Н" };
    private static final String[] SIDEBAR_TOOLTIPS = { "Чат", "Друзья", "Онлайн", "Настройки" };

    /** A clickable region captured during render so click handling never re-derives layout. */
    private interface Action { void run(); }
    private static final class Hit {
        final float x, y, w, h; final Action action;
        Hit(float x, float y, float w, float h, Action action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
    }

    // 4px grid
    private static final float W = 540f;
    private static final float H = 360f;
    private static final float PAD = 12f;
    private static final float GAP = 8f;
    private static final float HEADER_H = 40f;
    private static final float TAB_H = 24f;
    private static final float INPUT_H = 26f;
    private static final float BTN_H = 30f;
    private static final float ROW_H = 22f;
    private static final float USER_ROW_H = 32f;
    private static final float SCROLL_W = 3f;

    // Discord-like layout
    private static final float SIDEBAR_W = 44f;
    private static final float MEMBERS_W = 144f;
    private static final float SIDEBAR_ICON = 32f;

    private static final float R_LG = 10f;   // panel
    private static final float R_MD = 8f;    // cards / inputs
    private static final float R_SM = 6f;    // tabs / chips

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    // Layout
    private float panelX, panelY;

    // State
    private Tab tab = Tab.CHAT;
    private AuthMode authMode = AuthMode.LOGIN;
    private Field focused = Field.NONE;
    private String username = "";
    private String password = "";
    private String message = "";
    private boolean selectAll = false;     // Ctrl+A on the focused field
    private long caretBlinkStart = System.currentTimeMillis();

    // Chat
    private float chatScroll = 0f, chatTarget = 0f;
    private boolean chatAuto = true;
    private int lastLogSize = -1;
    private float chatListH = 0f;

    // Online
    private float onlineScroll = 0f, onlineTarget = 0f;
    private long lastOnlineRefresh = 0L;
    private IRClient.OnlineUser selectedProfile = null;
    private float onlineListH = 0f;

    // Settings
    private float settingsScroll = 0f, settingsTarget = 0f;
    private float settingsListH = 0f;
    private float settingsContentH = 0f;

    // Friends & DM
    private float friendsScroll = 0f, friendsTarget = 0f;
    private float friendsListH = 0f, friendsContentH = 0f;
    private long lastFriendsRefresh = 0L;
    private String dmPeer = null;           // nickname of the open conversation
    private String dmInput = "";
    private float dmScroll = 0f, dmScrollTarget = 0f, dmListH = 0f;
    private boolean dmAuto = true;
    private int lastDmSize = -1;
    private final List<Hit> friendHits = new ArrayList<>();
    private final List<Hit> profileHits = new ArrayList<>();
    private final float[] friendsListBounds = new float[4];
    private final float[] dmBounds = new float[4];

    // FX
    private final CompactAnimation fade = new CompactAnimation(Easing.EASE_OUT_CUBIC, 320);
    private float alpha = 0f;
    private boolean closing = false;
    private boolean handCursor = false;

    public IRChatScreen() {
        super(Text.literal("IRC"));
    }

    @Override
    protected void init() {
        if (client != null) {
            width = GLUtils.getScaledWidthFor(2);
            height = GLUtils.getScaledHeightFor(2);
        }
        panelX = (width - W) / 2f;
        panelY = (height - H) / 2f;
        tab = Tab.CHAT;
        fade.setValue(0.0);
        fade.setDestinationValue(1.0);
        fade.reset();
        CursorsUtil.resetState();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Прозрачный фон — игра видна за GUI
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        GLUtils.setupOverlayRendering(2);
        boolean restored = false;
        try {
            CursorsUtil.beginFrame();
            handCursor = false;

            fade.run(closing ? 0.0 : 1.0);
            fade.update();
            alpha = clamp01((float) fade.getValue());

            if (closing && alpha <= 0.01f) {
                if (client != null) client.setScreen(null);
                CursorsUtil.applyFrameCursor();
                CursorsUtil.resetState();
                restored = true;
                GLUtils.restoreRendering();
                return;
            }

            if (client != null) {
                width = GLUtils.getScaledWidthFor(2);
                height = GLUtils.getScaledHeightFor(2);
            }
            panelX = (width - W) / 2f;
            panelY = (height - H) / 2f;

            int mx = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int my = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));
            Matrix4f m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

            int accent = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFF47A3FF;
            int baseText = HelperElements.themedText();
            int text = a(baseText, alpha);

            // Soft drop shadow — lifts the panel off the game for depth
            Builder.SHADOW_BUILDER.size(new SizeState(W, H))
                    .color(new QuadColorState(a(ColorUtils.rgb(0, 0, 0), alpha * 0.45f)))
                    .radius(new QuadRadiusState(R_LG))
                    .shadow(18f).softness(0.9f)
                    .build().render(m, panelX, panelY);

            // Panel background
            HelperElements.rect(m, panelX, panelY, W, H, alpha);

            renderHeader(m, mx, my, accent, text);

            boolean loggedIn = isLoggedIn();

            if (!loggedIn) {
                float bodyY = panelY + HEADER_H + GAP;
                float bodyH = H - (bodyY - panelY) - PAD;
                float bodyX = panelX + PAD;
                float bodyW = W - PAD * 2f;
                renderAuth(m, mx, my, bodyX, bodyY, bodyW, bodyH, accent, text);
            } else {
                // Discord layout: [ sidebar | main | members ]
                float topY = panelY + HEADER_H;
                float colsH = H - HEADER_H - PAD;
                float sidebarX = panelX + PAD;
                float mainX = sidebarX + SIDEBAR_W + GAP;
                float membersX = panelX + W - PAD - MEMBERS_W;
                boolean showMembers = tab == Tab.CHAT;
                float mainW = (showMembers ? membersX - GAP : panelX + W - PAD) - mainX;

                renderSidebar(m, mx, my, sidebarX, topY, colsH, accent, text);

                if (tab == Tab.SETTINGS) {
                    renderSettings(ctx, m, mx, my, mainX, topY, panelX + W - PAD - mainX, colsH, accent, text);
                } else if (tab == Tab.ONLINE) {
                    renderOnline(ctx, m, mx, my, mainX, topY, panelX + W - PAD - mainX, colsH, accent, text);
                } else if (tab == Tab.FRIENDS) {
                    renderFriends(ctx, m, mx, my, mainX, topY, panelX + W - PAD - mainX, colsH, accent, text);
                } else {
                    renderChat(ctx, m, mx, my, mainX, topY, mainW, colsH, accent, text);
                    renderMembers(ctx, m, mx, my, membersX, topY, MEMBERS_W, colsH, accent, text);
                }
            }

            if (handCursor) CursorsUtil.setCursor(CursorsUtil.HAND);
            CursorsUtil.applyFrameCursor();
        } finally {
            if (!restored) GLUtils.restoreRendering();
        }
    }

    // ---------- HEADER ----------

    private void renderHeader(Matrix4f m, int mx, int my, int accent, int text) {
        // Header strip — subtle vertical accent gradient for depth
        int hTop = scale(a(accent, alpha), 0.11f);
        int hBot = scale(a(accent, alpha), 0.02f);
        Builder.RECTANGLE_BUILDER.size(new SizeState(W - 2f, HEADER_H))
                .color(new QuadColorState(hTop, hTop, hBot, hBot))
                .radius(new QuadRadiusState(R_LG - 2f))
                .build().render(m, panelX + 1f, panelY + 1f);

        // Accent dot with a soft halo
        float dotX = panelX + PAD;
        float dotY = panelY + (HEADER_H - 8f) / 2f;
        rect(m, dotX - 3f, dotY - 3f, 14f, 14f, scale(a(accent, alpha), 0.22f), 7f);
        rect(m, dotX, dotY, 8f, 8f, a(accent, alpha), 4f);

        // Title — context-aware
        float titleX = dotX + 14f;
        String title = headerTitle();
        float th = Builder.INTER.get().getHeight(title, 11f);
        Builder.TEXT_BUILDER.text(title).size(11f).font(Builder.INTER.get()).color(text).build()
                .render(m, titleX, panelY + (HEADER_H - th) / 2f - 0.3f);

        // Connection pill
        IRClient cc = LynxWave.getInstance().getIRClient();
        boolean online = cc != null && cc.isOpen();
        int dotColor = online ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(255, 90, 90);
        String label = online ? "ONLINE" : "OFFLINE";
        renderPill(m, panelX + W - PAD - pillWidth(label, 7f),
                panelY + (HEADER_H - 16f) / 2f, label, 7f, dotColor);

        // Divider
        rect(m, panelX + PAD, panelY + HEADER_H - 1f, W - PAD * 2f, 1f, scale(a(accent, alpha), 0.16f), 0.5f);
    }

    private String headerTitle() {
        if (!isLoggedIn()) {
            return authMode == AuthMode.REGISTER ? "IRC  •  Регистрация" : "IRC  •  Вход";
        }
        return switch (tab) {
            case ONLINE -> "IRC  •  Онлайн";
            case FRIENDS -> "IRC  •  Друзья";
            case SETTINGS -> "IRC  •  Настройки";
            default -> {
                IRClient c = LynxWave.getInstance().getIRClient();
                yield "IRC  •  Общий чат" + (c != null ? "  ·  " + c.getCurrentNickname() : "");
            }
        };
    }

    private float pillWidth(String label, float size) {
        return Builder.INTER.get().getWidth(label, size) + 26f;
    }

    private void renderPill(Matrix4f m, float x, float y, String label, float textSize, int color) {
        float lw = Builder.INTER.get().getWidth(label, textSize);
        float lh = Builder.INTER.get().getHeight(label, textSize);
        float pw = lw + 26f;
        float ph = 16f;
        rect(m, x, y, pw, ph, a(color, alpha * 0.18f), ph / 2f);
        // dot
        rect(m, x + 8f, y + (ph - 5f) / 2f, 5f, 5f, a(color, alpha), 2.5f);
        // text
        Builder.TEXT_BUILDER.text(label).size(textSize).font(Builder.INTER.get())
                .color(a(color, alpha)).build()
                .render(m, x + 18f, y + (ph - lh) / 2f);
    }

    // ---------- SIDEBAR (Discord-like icon rail) ----------

    private void renderSidebar(Matrix4f m, int mx, int my,
                               float x, float y, float h,
                               int accent, int text) {
        // Subtle column background
        containerCard(m, x, y, SIDEBAR_W, h, accent);

        Tab[] tabs = SIDEBAR_TABS;
        String[] glyphs = SIDEBAR_GLYPHS;
        String[] tooltips = SIDEBAR_TOOLTIPS;

        IRClient cc = LynxWave.getInstance().getIRClient();
        int requestCount = cc != null ? cc.getIncomingFriendRequests().size() : 0;

        float ix = x + (SIDEBAR_W - SIDEBAR_ICON) / 2f;
        float iy = y + 12f;
        float gap = 6f;

        for (int i = 0; i < tabs.length; i++) {
            float by = iy + i * (SIDEBAR_ICON + gap);
            boolean active = tab == tabs[i];
            boolean hover = MouseClick.isClick(mx, my, ix, by, SIDEBAR_ICON, SIDEBAR_ICON);
            if (hover) handCursor = true;

            int bgColor = active
                    ? a(accent, alpha * 0.22f)
                    : a(accent, alpha * (hover ? 0.12f : 0.06f));
            float radius = active ? R_MD : SIDEBAR_ICON / 2f;
            rect(m, ix, by, SIDEBAR_ICON, SIDEBAR_ICON, bgColor, radius);

            if (active) {
                // Discord left accent indicator
                rect(m, x + 2f, by + 6f, 2.5f,
                        SIDEBAR_ICON - 12f, a(accent, alpha), 1.25f);
            }

            // Glyph centered
            String g = glyphs[i];
            float glyphSize = 12f;
            float gw = Builder.INTER.get().getWidth(g, glyphSize);
            float gh = Builder.INTER.get().getHeight(g, glyphSize);
            int glyphColor = active ? a(accent, alpha)
                    : (hover ? text : scale(text, 0.6f));
            Builder.TEXT_BUILDER.text(g).size(glyphSize).font(Builder.INTER.get())
                    .color(glyphColor).build()
                    .render(m, ix + (SIDEBAR_ICON - gw) / 2f,
                            by + (SIDEBAR_ICON - gh) / 2f - 0.3f);

            // Friend-request badge
            if (tabs[i] == Tab.FRIENDS && requestCount > 0) {
                String badge = requestCount > 9 ? "9+" : Integer.toString(requestCount);
                float bs = 6f;
                float bx = ix + SIDEBAR_ICON - bs - 1f;
                float byb = by + 1f;
                int red = ColorUtils.rgb(255, 80, 80);
                rect(m, bx - 1f, byb - 1f, bs + 2f, bs + 2f, scale(a(accent, alpha), 0.6f), (bs + 2f) / 2f);
                rect(m, bx, byb, bs, bs, a(red, alpha), bs / 2f);
                float bSize = 5f;
                float bw = Builder.INTER.get().getWidth(badge, bSize);
                float bh = Builder.INTER.get().getHeight(badge, bSize);
                Builder.TEXT_BUILDER.text(badge).size(bSize).font(Builder.INTER.get())
                        .color(a(-1, alpha)).build()
                        .render(m, bx + (bs - bw) / 2f, byb + (bs - bh) / 2f - 0.2f);
            }

            // Hover tooltip
            if (hover) {
                String tip = tooltips[i];
                float tipSize = 7.5f;
                float tw = Builder.INTER.get().getWidth(tip, tipSize);
                float tipH = 16f;
                float tx = ix + SIDEBAR_ICON + 6f;
                float ty = by + (SIDEBAR_ICON - tipH) / 2f;
                rect(m, tx, ty, tw + 14f, tipH, a(0, alpha * 0.85f), 4f);
                float th = Builder.INTER.get().getHeight(tip, tipSize);
                Builder.TEXT_BUILDER.text(tip).size(tipSize).font(Builder.INTER.get())
                        .color(a(-1, alpha)).build()
                        .render(m, tx + 7f, ty + (tipH - th) / 2f);
            }
        }
    }

    // ---------- AUTH (Discord-like centered card) ----------

    // Auth card geometry
    private static final float AUTH_CARD_W = 320f;
    private static final float AUTH_LABEL_GAP = 6f;
    private static final float AUTH_FIELD_GAP = 14f;

    private void renderAuth(Matrix4f m, int mx, int my,
                            float bodyX, float bodyY, float bodyW, float bodyH,
                            int accent, int text) {
        boolean register = authMode == AuthMode.REGISTER;

        // Card position (centered)
        float cardW = Math.min(AUTH_CARD_W, bodyW - 24f);
        float cardX = panelX + (W - cardW) / 2f;

        // Calculate content height to vertically center the card
        float titleH = 16f;
        float subtitleH = 10f;
        float titleBlock = titleH + 4f + subtitleH;
        float fieldsH = (10f + AUTH_LABEL_GAP + INPUT_H) * 2 + AUTH_FIELD_GAP;
        float btnGap = 16f;
        float footerGap = 14f;
        float footerH = 9f;
        float contentH = titleBlock + 22f + fieldsH + btnGap + BTN_H + footerGap + footerH;

        float cardY = bodyY + Math.max(0f, (bodyH - contentH) / 2f);

        // Title centered
        String title = register ? "Создать аккаунт" : "С возвращением!";
        String subtitle = register
                ? "Придумайте логин и пароль для IRC"
                : "Мы рады видеть вас снова";

        float tw = Builder.INTER.get().getWidth(title, 14f);
        float th = Builder.INTER.get().getHeight(title, 14f);
        Builder.TEXT_BUILDER.text(title).size(14f).font(Builder.INTER.get()).color(text).build()
                .render(m, panelX + (W - tw) / 2f, cardY);

        float subY = cardY + th + 6f;
        float sw = Builder.INTER.get().getWidth(subtitle, 8f);
        Builder.TEXT_BUILDER.text(subtitle).size(8f).font(Builder.INTER.get())
                .color(scale(text, 0.55f)).build()
                .render(m, panelX + (W - sw) / 2f, subY);

        // Fields
        float fy = subY + 28f;
        renderLabeledInput(m, cardX, fy, cardW,
                register ? "ЛОГИН" : "ЛОГИН ИЛИ EMAIL",
                "", username, focused == Field.USERNAME, false, accent, text);
        fy += 10f + AUTH_LABEL_GAP + INPUT_H + AUTH_FIELD_GAP;

        renderLabeledInput(m, cardX, fy, cardW,
                "ПАРОЛЬ", "", password, focused == Field.PASSWORD, true, accent, text);
        fy += 10f + AUTH_LABEL_GAP + INPUT_H;

        // Submit button
        float btnY = fy + btnGap;
        boolean hoverBtn = MouseClick.isClick(mx, my, cardX, btnY, cardW, BTN_H);
        if (hoverBtn) handCursor = true;

        int top = a(accent, alpha * (hoverBtn ? 1f : 0.88f));
        int bot = a(accent, alpha * (hoverBtn ? 0.8f : 0.66f));
        Builder.RECTANGLE_BUILDER.size(new SizeState(cardW, BTN_H))
                .color(new QuadColorState(top, top, bot, bot))
                .radius(new QuadRadiusState(R_MD)).build().render(m, cardX, btnY);

        String label = register ? "Продолжить" : "Войти";
        float lw = Builder.INTER.get().getWidth(label, 10f);
        float lh = Builder.INTER.get().getHeight(label, 10f);
        Builder.TEXT_BUILDER.text(label).size(10f).font(Builder.INTER.get())
                .color(a(-1, alpha)).build()
                .render(m, cardX + (cardW - lw) / 2f, btnY + (BTN_H - lh) / 2f - 0.3f);

        // Footer link — "Нет аккаунта? Зарегистрироваться" / "Уже есть аккаунт? Войти"
        float footerY = btnY + BTN_H + footerGap;
        String hint = register ? "Уже есть аккаунт?  " : "Нужен аккаунт?  ";
        String linkText = register ? "Войти" : "Зарегистрироваться";
        float hintW = Builder.INTER.get().getWidth(hint, 8f);
        float linkW = Builder.INTER.get().getWidth(linkText, 8f);
        float totalW = hintW + linkW;
        float fxStart = panelX + (W - totalW) / 2f;

        Builder.TEXT_BUILDER.text(hint).size(8f).font(Builder.INTER.get())
                .color(scale(text, 0.55f)).build().render(m, fxStart, footerY);

        boolean hoverLink = MouseClick.isClick(mx, my, fxStart + hintW, footerY - 2f, linkW, 12f);
        if (hoverLink) handCursor = true;
        Builder.TEXT_BUILDER.text(linkText).size(8f).font(Builder.INTER.get())
                .color(a(accent, alpha * (hoverLink ? 1f : 0.85f))).build()
                .render(m, fxStart + hintW, footerY);
    }

    /** Field label above input, like in Discord. */
    private void renderLabeledInput(Matrix4f m, float x, float y, float w,
                                    String label, String placeholder,
                                    String value, boolean focused, boolean password,
                                    int accent, int text) {
        Builder.TEXT_BUILDER.text(label).size(7f).font(Builder.INTER.get())
                .color(scale(text, 0.55f)).build().render(m, x, y);
        renderInput(m, x, y + 10f + AUTH_LABEL_GAP, w,
                placeholder, value, focused, password, accent, text);
    }

    private void renderInput(Matrix4f m, float x, float y, float w, String placeholder,
                             String value, boolean focused, boolean password,
                             int accent, int text) {
        int bg = scale(a(accent, alpha), focused ? 0.1f : 0.05f);
        rect(m, x, y, w, INPUT_H, bg, R_MD);
        if (focused) {
            Builder.BORDER_BUILDER.size(new SizeState(w, INPUT_H))
                    .color(new QuadColorState(a(accent, alpha * 0.55f)))
                    .radius(new QuadRadiusState(R_MD)).thickness(-0.3f)
                    .build().render(m, x, y);
        }

        boolean empty = value.isEmpty();
        String shown = password && !empty ? "•".repeat(value.length()) : value;
        float textY = y + (INPUT_H - 8f) / 2f;
        if (empty) {
            Builder.TEXT_BUILDER.text(placeholder).size(8f).font(Builder.INTER.get())
                    .color(scale(text, 0.4f)).build().render(m, x + 10f, textY);
        } else {
            float maxW = w - 20f;
            String trimmed = trimToWidth(shown, 8f, maxW, true);
            if (focused && selectAll) {
                float selW = Math.min(Builder.INTER.get().getWidth(trimmed, 8f), maxW) + 3f;
                rect(m, x + 9f, y + (INPUT_H - 12f) / 2f, selW, 12f, a(accent, alpha * 0.3f), 2f);
            }
            Builder.TEXT_BUILDER.text(trimmed).size(8f).font(Builder.INTER.get()).color(text).build()
                    .render(m, x + 10f, textY);
        }

        if (focused) {
            long t = (System.currentTimeMillis() - caretBlinkStart) % 1000L;
            if (t < 530L) {
                float caretX = x + 10f + Builder.INTER.get().getWidth(shown, 8f);
                if (caretX > x + w - 10f) caretX = x + w - 10f;
                rect(m, caretX + 1f, y + (INPUT_H - 12f) / 2f, 0.8f, 12f, text, 0.4f);
            }
        }
    }

    // ---------- CHAT (Discord-style: head row + body lines) ----------

    private static final float CHAT_AVATAR = 22f;
    private static final float CHAT_HEAD_H = 26f;       // первая строка группы (с аватаром + ник)
    private static final float CHAT_LINE_H = 16f;       // каждое последующее сообщение
    private static final float CHAT_GROUP_GAP = 6f;     // пробел между группами

    private boolean isGrouped(IRClient.ChatLogEntry prev, IRClient.ChatLogEntry cur) {
        return prev != null
                && prev.author() != null && cur.author() != null
                && prev.author().equalsIgnoreCase(cur.author())
                && (cur.time() - prev.time()) < 5L * 60L * 1000L;
    }

    /** [topY, height] для каждой записи. topY — отн. начала контента. */
    private float[][] computeChatLayout(List<IRClient.ChatLogEntry> log) {
        float[][] out = new float[log.size()][2];
        float cy = 0f;
        IRClient.ChatLogEntry prev = null;
        for (int i = 0; i < log.size(); i++) {
            IRClient.ChatLogEntry e = log.get(i);
            boolean grouped = isGrouped(prev, e);
            float entryH;
            if (!grouped) {
                if (i > 0) cy += CHAT_GROUP_GAP;
                entryH = CHAT_HEAD_H;
            } else {
                entryH = CHAT_LINE_H;
            }
            out[i][0] = cy;
            out[i][1] = entryH;
            cy += entryH;
            prev = e;
        }
        return out;
    }

    private float computeChatTotalH(List<IRClient.ChatLogEntry> log, boolean compact) {
        if (log.isEmpty()) return 0f;
        float[][] layout = computeChatLayout(log);
        float[] last = layout[layout.length - 1];
        return last[0] + last[1];
    }

    private void renderChat(DrawContext ctx, Matrix4f m, int mx, int my,
                            float x, float y, float w, float h,
                            int accent, int text) {
        IRCSettings cfg = IRCSettings.get();
        cfg.ensureLoaded();
        boolean showTime = cfg.isShowTimestamps();
        boolean showClient = cfg.isShowClientPrefix();

        float inputBlockH = INPUT_H + GAP;
        float listH = h - inputBlockH;

        // Background card
        containerCard(m, x, y, w, listH, accent);

        chatListH = listH;
        List<IRClient.ChatLogEntry> log = IRClient.getChatLog();

        float[][] layout = log.isEmpty() ? new float[0][] : computeChatLayout(log);
        float total = log.isEmpty() ? 0f
                : layout[layout.length - 1][0] + layout[layout.length - 1][1];

        if (log.size() != lastLogSize) {
            float maxNow = Math.max(0f, total - listH + 12f);
            if (chatAuto) {
                chatTarget = maxNow;
                chatScroll = maxNow;
            }
            lastLogSize = log.size();
        }
        chatScroll += (chatTarget - chatScroll) * 0.18f;
        float maxScroll = Math.max(0f, total - listH + 12f);

        ScissorUtil.enableContext(ctx, x + 4f, y + 6f, w - 8f, listH - 12f);

        IRClient self = LynxWave.getInstance().getIRClient();
        String selfNick = self != null ? self.getCurrentNickname() : "";

        float baseY = y + 8f - chatScroll;
        float avX = x + 10f;
        float contentX = avX + CHAT_AVATAR + 10f;

        IRClient.ChatLogEntry prev = null;
        for (int i = 0; i < log.size(); i++) {
            IRClient.ChatLogEntry e = log.get(i);
            float entryY = baseY + layout[i][0];
            float entryH = layout[i][1];
            boolean grouped = isGrouped(prev, e);

            if (entryY + entryH < y || entryY > y + listH) {
                prev = e;
                continue;
            }

            boolean own = selfNick != null && selfNick.equalsIgnoreCase(e.author());
            boolean hover = MouseClick.isClick(mx, my, x + 6f, entryY, w - 12f, entryH);

            // Hover row tint
            if (hover) {
                rect(m, x + 6f, entryY, w - 12f, entryH, a(accent, alpha * 0.05f), R_SM);
            }

            if (!grouped) {
                // === HEAD ROW ===
                // Avatar — vertically centered in CHAT_HEAD_H
                float avY = entryY + (CHAT_HEAD_H - CHAT_AVATAR) / 2f;
                int avColor = own ? accent : ColorUtils.rgb(120, 145, 200);
                String mcNick = mcNickFor(e.author());
                renderAvatar(m, avX, avY, CHAT_AVATAR, mcNick, e.author(), avColor);

                // Top row: name [tag] [time]
                float nameSize = 8.5f;
                float nameH = Builder.INTER.get().getHeight("X", nameSize);
                float topY = entryY + 3f;
                int nameColor = own ? a(accent, alpha) : a(ColorUtils.rgb(220, 225, 240), alpha);
                String name = e.author() != null ? e.author() : "?";
                Builder.TEXT_BUILDER.text(name).size(nameSize).font(Builder.INTER.get())
                        .color(nameColor).build().render(m, contentX, topY);
                float nameW = Builder.INTER.get().getWidth(name, nameSize);
                float metaX = contentX + nameW + 6f;

                if (showClient && e.client() != null && !e.client().isBlank()) {
                    String tag = e.client();
                    float tagSize = 6f;
                    float tw = Builder.INTER.get().getWidth(tag, tagSize);
                    float tagH = 10f;
                    float tagY = topY - 1f;
                    rect(m, metaX, tagY, tw + 8f, tagH, a(accent, alpha * 0.2f), tagH / 2f);
                    float tagTextH = Builder.INTER.get().getHeight(tag, tagSize);
                    Builder.TEXT_BUILDER.text(tag).size(tagSize).font(Builder.INTER.get())
                            .color(a(accent, alpha)).build()
                            .render(m, metaX + 4f, tagY + (tagH - tagTextH) / 2f);
                    metaX += tw + 8f + 6f;
                }

                if (showTime) {
                    String time = TIME_FMT.format(Instant.ofEpochMilli(e.time()));
                    float timeSize = 6f;
                    Builder.TEXT_BUILDER.text(time).size(timeSize).font(Builder.INTER.get())
                            .color(scale(text, 0.4f)).build()
                            .render(m, metaX, topY + 2f);
                }

                // Bottom row: message
                float msgSize = 8f;
                float msgH = Builder.INTER.get().getHeight("X", msgSize);
                float msgY = entryY + CHAT_HEAD_H - msgH - 4f;
                String msg = trimToWidth(e.message(), msgSize, x + w - 12f - contentX, false);
                Builder.TEXT_BUILDER.text(msg).size(msgSize).font(Builder.INTER.get()).color(text).build()
                        .render(m, contentX, msgY);
            } else {
                // === GROUPED LINE === only message centered in CHAT_LINE_H
                float msgSize = 8f;
                float msgH = Builder.INTER.get().getHeight("X", msgSize);
                float msgY = entryY + (CHAT_LINE_H - msgH) / 2f;

                if (hover && showTime) {
                    String time = TIME_FMT.format(Instant.ofEpochMilli(e.time()));
                    float ts = 5.5f;
                    float tw = Builder.INTER.get().getWidth(time, ts);
                    float th = Builder.INTER.get().getHeight(time, ts);
                    Builder.TEXT_BUILDER.text(time).size(ts).font(Builder.INTER.get())
                            .color(scale(text, 0.35f)).build()
                            .render(m, avX + (CHAT_AVATAR - tw) / 2f,
                                    entryY + (CHAT_LINE_H - th) / 2f);
                }

                String msg = trimToWidth(e.message(), msgSize, x + w - 12f - contentX, false);
                Builder.TEXT_BUILDER.text(msg).size(msgSize).font(Builder.INTER.get()).color(text).build()
                        .render(m, contentX, msgY);
            }

            prev = e;
        }
        ScissorUtil.disableContext(ctx);

        renderScroll(m, x + w - SCROLL_W - 4f, y + 6f, listH - 12f,
                total, chatScroll, maxScroll, accent);

        // Input row — Discord-style: rounded pill input with send icon inside
        float inputY = y + listH + GAP;
        float fieldH = INPUT_H;
        // Background
        rect(m, x, inputY, w, fieldH, scale(a(accent, alpha), focused == Field.MESSAGE ? 0.1f : 0.06f), R_MD);
        if (focused == Field.MESSAGE) {
            Builder.BORDER_BUILDER.size(new SizeState(w, fieldH))
                    .color(new QuadColorState(a(accent, alpha * 0.45f)))
                    .radius(new QuadRadiusState(R_MD)).thickness(-0.3f)
                    .build().render(m, x, inputY);
        }

        float sendBtnSize = fieldH - 8f;
        float fieldTextW = w - 16f - sendBtnSize - 10f;

        // Placeholder / value
        boolean empty = message.isEmpty();
        String shown = empty ? "Написать в #общий-чат" : message;
        String trimmed = trimToWidth(shown, 8f, fieldTextW, true);
        if (focused == Field.MESSAGE && selectAll && !empty) {
            float selW = Math.min(Builder.INTER.get().getWidth(trimmed, 8f), fieldTextW) + 3f;
            rect(m, x + 11f, inputY + (fieldH - 12f) / 2f, selW, 12f, a(accent, alpha * 0.3f), 2f);
        }
        Builder.TEXT_BUILDER.text(trimmed).size(8f).font(Builder.INTER.get())
                .color(empty ? scale(text, 0.4f) : text).build()
                .render(m, x + 12f, inputY + (fieldH - 8f) / 2f);

        // Caret
        if (focused == Field.MESSAGE && !empty) {
            long t = (System.currentTimeMillis() - caretBlinkStart) % 1000L;
            if (t < 530L) {
                float caretX = x + 12f + Builder.INTER.get().getWidth(trimmed, 8f);
                if (caretX > x + 12f + fieldTextW) caretX = x + 12f + fieldTextW;
                rect(m, caretX + 1f, inputY + (fieldH - 12f) / 2f, 0.8f, 12f, text, 0.4f);
            }
        } else if (focused == Field.MESSAGE) {
            long t = (System.currentTimeMillis() - caretBlinkStart) % 1000L;
            if (t < 530L) {
                rect(m, x + 12f, inputY + (fieldH - 12f) / 2f, 0.8f, 12f, text, 0.4f);
            }
        }

        // Send button (circle with arrow)
        float btnX = x + w - sendBtnSize - 4f;
        float btnY = inputY + (fieldH - sendBtnSize) / 2f;
        boolean hoverSend = MouseClick.isClick(mx, my, btnX, btnY, sendBtnSize, sendBtnSize);
        if (hoverSend) handCursor = true;
        boolean canSend = !message.isBlank();
        int sendBg = canSend ? a(accent, alpha * (hoverSend ? 1f : 0.85f))
                : a(accent, alpha * 0.22f);
        rect(m, btnX, btnY, sendBtnSize, sendBtnSize, sendBg, sendBtnSize / 2f);
        String arrow = "›";
        float aw = Builder.INTER.get().getWidth(arrow, 12f);
        float ah = Builder.INTER.get().getHeight(arrow, 12f);
        int arrowColor = canSend ? a(-1, alpha) : a(-1, alpha * 0.5f);
        Builder.TEXT_BUILDER.text(arrow).size(12f).font(Builder.INTER.get())
                .color(arrowColor).build()
                .render(m, btnX + (sendBtnSize - aw) / 2f - 0.3f,
                        btnY + (sendBtnSize - ah) / 2f - 0.6f);
    }

    // ---------- MEMBERS panel (right column when in CHAT) ----------

    private void renderMembers(DrawContext ctx, Matrix4f m, int mx, int my,
                               float x, float y, float w, float h,
                               int accent, int text) {
        long now = System.currentTimeMillis();
        if (now - lastOnlineRefresh > 5000L) {
            lastOnlineRefresh = now;
            IRClient cc = LynxWave.getInstance().getIRClient();
            if (cc != null && cc.isLoggedIn()) cc.refreshOnlineUsersCache();
        }

        IRClient client = LynxWave.getInstance().getIRClient();
        List<IRClient.OnlineUser> users = client != null
                ? new ArrayList<>(client.getCachedOnlineUsers()) : new ArrayList<>();
        users.sort(Comparator
                .comparing((IRClient.OnlineUser u) -> !u.isOnline())
                .thenComparingInt(u -> roleWeight(u.getRole()))
                .thenComparing(u -> u.getNickname() == null ? "" : u.getNickname(),
                        String.CASE_INSENSITIVE_ORDER));

        containerCard(m, x, y, w, h, accent);

        int onlineCount = 0;
        for (IRClient.OnlineUser u : users) if (u.isOnline()) onlineCount++;

        // Group by role: Admins / Mods / Members / Offline (Discord roles style)
        java.util.List<IRClient.OnlineUser> admins = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> mods = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> members = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> offline = new ArrayList<>();
        for (IRClient.OnlineUser u : users) {
            if (!u.isOnline()) { offline.add(u); continue; }
            String r = u.getRole() == null ? "" : u.getRole().toLowerCase();
            switch (r) {
                case "admin" -> admins.add(u);
                case "moderator", "mod" -> mods.add(u);
                default -> members.add(u);
            }
        }

        ScissorUtil.enableContext(ctx, x + 4f, y + 4f, w - 8f, h - 8f);
        float cy = y + 8f - onlineScroll;

        cy = renderMemberSection(m, mx, my, x, cy, w, "АДМИНЫ", admins, accent, text);
        cy = renderMemberSection(m, mx, my, x, cy, w, "МОДЕРАТОРЫ", mods, accent, text);
        cy = renderMemberSection(m, mx, my, x, cy, w, "ОНЛАЙН — " + onlineCount, members, accent, text);
        cy = renderMemberSection(m, mx, my, x, cy, w, "НЕ В СЕТИ — " + offline.size(), offline, accent, text);

        ScissorUtil.disableContext(ctx);

        float totalH = (cy + onlineScroll) - (y + 8f);
        onlineListH = h - 16f;
        float maxScroll = Math.max(0f, totalH - onlineListH);
        if (onlineTarget > maxScroll) onlineTarget = maxScroll;
        if (onlineTarget < 0) onlineTarget = 0;
        onlineScroll += (onlineTarget - onlineScroll) * 0.18f;

        renderScroll(m, x + w - SCROLL_W - 4f, y + 8f, onlineListH,
                Math.max(totalH, 1f), onlineScroll, maxScroll, accent);
    }

    private float renderMemberSection(Matrix4f m, int mx, int my,
                                      float x, float startY, float w, String title,
                                      java.util.List<IRClient.OnlineUser> list,
                                      int accent, int text) {
        if (list.isEmpty()) return startY;
        float cy = startY;

        Builder.TEXT_BUILDER.text(title).size(6.5f).font(Builder.INTER.get())
                .color(scale(text, 0.45f)).build().render(m, x + 12f, cy);
        cy += 14f;

        for (IRClient.OnlineUser u : list) {
            float rowH = 26f;
            boolean hover = MouseClick.isClick(mx, my, x + 6f, cy, w - 12f, rowH);
            boolean selected = selectedProfile != null && u.getNickname() != null
                    && u.getNickname().equalsIgnoreCase(selectedProfile.getNickname());
            if (hover) handCursor = true;

            if (hover || selected) {
                rect(m, x + 6f, cy, w - 12f, rowH,
                        a(accent, alpha * (selected ? 0.18f : 0.08f)), R_SM);
            }

            float avSize = 18f;
            float avX = x + 10f;
            float avY = cy + (rowH - avSize) / 2f;
            int rc = roleColor(u.getRole(), accent);
            renderAvatar(m, avX, avY, avSize, u.getGameUsername(), u.getNickname(), rc);

            // Status dot bottom-right of avatar
            int statusColor = u.isOnline() ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(140, 140, 150);
            float sd = 5f;
            rect(m, avX + avSize - sd + 1f, avY + avSize - sd + 1f, sd, sd,
                    a(statusColor, alpha), sd / 2f);

            // Nickname
            String nick = u.getNickname() != null ? u.getNickname() : "?";
            String shown = trimToWidth(nick, 8f, w - (avX + avSize + 8f - x) - 14f, false);
            int nickColor = u.isOnline() ? text : scale(text, 0.45f);
            Builder.TEXT_BUILDER.text(shown).size(8f).font(Builder.INTER.get())
                    .color(nickColor).build()
                    .render(m, avX + avSize + 8f, cy + (rowH - 8f) / 2f);

            cy += rowH + 2f;
        }

        cy += 6f;
        return cy;
    }

    // ---------- ONLINE ----------

    private void renderOnline(DrawContext ctx, Matrix4f m, int mx, int my,
                              float x, float y, float w, float h,
                              int accent, int text) {
        long now = System.currentTimeMillis();
        if (now - lastOnlineRefresh > 5000L) {
            lastOnlineRefresh = now;
            IRClient c = LynxWave.getInstance().getIRClient();
            if (c != null && c.isLoggedIn()) c.refreshOnlineUsersCache();
        }

        IRClient client = LynxWave.getInstance().getIRClient();
        List<IRClient.OnlineUser> users = client != null
                ? new ArrayList<>(client.getCachedOnlineUsers()) : new ArrayList<>();
        users.sort(Comparator
                .comparing((IRClient.OnlineUser u) -> !u.isOnline())
                .thenComparingInt(u -> roleWeight(u.getRole()))
                .thenComparing(u -> u.getNickname() == null ? "" : u.getNickname(),
                        String.CASE_INSENSITIVE_ORDER));

        // 60/40 split
        float listW = (float) Math.floor((w - GAP) * 0.6f);
        float profW = w - listW - GAP;
        float listX = x;
        float profX = x + listW + GAP;

        renderOnlineList(ctx, m, mx, my, listX, y, listW, h, users, accent, text);
        renderProfile(m, mx, my, profX, y, profW, h, accent, text);
    }

    private void renderOnlineList(DrawContext ctx, Matrix4f m, int mx, int my,
                                  float x, float y, float w, float h,
                                  List<IRClient.OnlineUser> users,
                                  int accent, int text) {
        containerCard(m, x, y, w, h, accent);

        // Header
        int onlineCount = 0;
        for (IRClient.OnlineUser u : users) if (u.isOnline()) onlineCount++;
        float headerH = 28f;
        Builder.TEXT_BUILDER.text("Игроки").size(8f).font(Builder.INTER.get())
                .color(text).build().render(m, x + 12f, y + (headerH - 8f) / 2f);
        String count = onlineCount + " / " + users.size();
        float cw = Builder.INTER.get().getWidth(count, 7f);
        Builder.TEXT_BUILDER.text(count).size(7f).font(Builder.INTER.get())
                .color(scale(text, 0.45f)).build()
                .render(m, x + w - cw - 12f, y + (headerH - 7f) / 2f);
        rect(m, x + 10f, y + headerH, w - 20f, 1f, scale(a(accent, alpha), 0.1f), 0.5f);

        // Scroll area
        float listY = y + headerH + 4f;
        float listVisH = h - headerH - 8f;
        float contentH = users.size() * (USER_ROW_H + GAP);
        float maxScroll = Math.max(0f, contentH - listVisH);
        onlineScroll += (onlineTarget - onlineScroll) * 0.18f;
        if (onlineTarget > maxScroll) onlineTarget = maxScroll;
        if (onlineTarget < 0) onlineTarget = 0;
        onlineListH = listVisH;

        float rowsX = x + 8f;
        float rowsW = w - 16f - (maxScroll > 0 ? SCROLL_W + 6f : 0f);

        ScissorUtil.enableContext(ctx, rowsX, listY, rowsW + (maxScroll > 0 ? SCROLL_W + 6f : 0f), listVisH);
        for (int i = 0; i < users.size(); i++) {
            IRClient.OnlineUser u = users.get(i);
            float rowY = listY - onlineScroll + i * (USER_ROW_H + GAP);
            if (rowY + USER_ROW_H < listY || rowY > listY + listVisH) continue;

            boolean hover = MouseClick.isClick(mx, my, rowsX, rowY, rowW(rowsW), USER_ROW_H);
            boolean selected = selectedProfile != null && u.getNickname() != null
                    && u.getNickname().equalsIgnoreCase(selectedProfile.getNickname());
            if (hover) handCursor = true;

            int rowBg = selected
                    ? a(accent, alpha * 0.18f)
                    : a(accent, alpha * (hover ? 0.1f : 0.05f));
            rect(m, rowsX, rowY, rowsW, USER_ROW_H, rowBg, R_SM);
            if (selected) {
                rect(m, rowsX + 3f, rowY + 6f, 2.5f, USER_ROW_H - 12f, a(accent, alpha), 1.25f);
            }

            // Avatar
            float avSize = 22f;
            float avX = rowsX + 10f;
            float avY = rowY + (USER_ROW_H - avSize) / 2f;
            int rc = roleColor(u.getRole(), accent);
            rect(m, avX, avY, avSize, avSize, a(rc, alpha * 0.22f), avSize / 2f);
            Builder.BORDER_BUILDER.size(new SizeState(avSize, avSize))
                    .color(new QuadColorState(a(rc, alpha * 0.55f)))
                    .radius(new QuadRadiusState(avSize / 2f)).thickness(-0.3f)
                    .build().render(m, avX, avY);

            String initial = initialOf(u.getNickname());
            float ti = Builder.INTER.get().getWidth(initial, 9f);
            float thi = Builder.INTER.get().getHeight(initial, 9f);
            Builder.TEXT_BUILDER.text(initial).size(9f).font(Builder.INTER.get())
                    .color(a(rc, alpha)).build()
                    .render(m, avX + (avSize - ti) / 2f, avY + (avSize - thi) / 2f - 0.3f);

            // Status dot
            int statusColor = u.isOnline()
                    ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(140, 140, 150);
            float sdSize = 6f;
            float sdX = avX + avSize - sdSize + 1f;
            float sdY = avY + avSize - sdSize + 1f;
            // Background ring (cuts the avatar a bit)
            rect(m, sdX - 1f, sdY - 1f, sdSize + 2f, sdSize + 2f,
                    scale(a(accent, alpha), 0.5f), (sdSize + 2f) / 2f);
            rect(m, sdX, sdY, sdSize, sdSize, a(statusColor, alpha), sdSize / 2f);

            // Nick + meta
            float textX = avX + avSize + 10f;
            String nick = u.getNickname() != null ? u.getNickname() : "?";
            String trimmedNick = trimToWidth(nick, 9f, rowsW - (textX - rowsX) - 8f, false);
            Builder.TEXT_BUILDER.text(trimmedNick).size(9f).font(Builder.INTER.get())
                    .color(u.isOnline() ? text : scale(text, 0.55f)).build()
                    .render(m, textX, rowY + 7f);

            String meta = roleLabel(u.getRole()) + "  •  "
                    + (u.getServer() != null && !u.getServer().isBlank() ? u.getServer() : "—");
            String trimmedMeta = trimToWidth(meta, 7f, rowsW - (textX - rowsX) - 8f, false);
            Builder.TEXT_BUILDER.text(trimmedMeta).size(7f).font(Builder.INTER.get())
                    .color(scale(text, 0.45f)).build()
                    .render(m, textX, rowY + 21f);
        }
        ScissorUtil.disableContext(ctx);

        renderScroll(m, x + w - SCROLL_W - 6f, listY, listVisH,
                contentH, onlineScroll, maxScroll, accent);
    }

    private float rowW(float baseW) { return baseW; }

    private void renderProfile(Matrix4f m, float x, float y, float w, float h,
                               int accent, int text) {
        renderProfile(m, (int) -9999, (int) -9999, x, y, w, h, accent, text);
    }

    private void renderProfile(Matrix4f m, int mx, int my, float x, float y, float w, float h,
                               int accent, int text) {
        profileHits.clear();
        // Card background
        containerCard(m, x, y, w, h, accent);

        if (selectedProfile == null) {
            String hint = "Выберите игрока";
            float hw = Builder.INTER.get().getWidth(hint, 8f);
            float hh = Builder.INTER.get().getHeight(hint, 8f);
            Builder.TEXT_BUILDER.text(hint).size(8f).font(Builder.INTER.get())
                    .color(scale(text, 0.4f)).build()
                    .render(m, x + (w - hw) / 2f, y + (h - hh) / 2f);
            return;
        }

        IRClient.OnlineUser u = selectedProfile;
        int rc = roleColor(u.getRole(), accent);

        // 1) Banner ⎯ flat header strip, full width
        final float BANNER_H = 56f;
        rect(m, x, y, w, BANNER_H, a(rc, alpha * 0.16f), R_MD);

        // 2) Avatar ⎯ fully below banner, centered
        final float AV_SIZE = 40f;
        float avX = x + (w - AV_SIZE) / 2f;
        float avY = y + BANNER_H + 4f;
        renderAvatar(m, avX, avY, AV_SIZE, u.getGameUsername(), u.getNickname(), rc);

        // 3) Nick ⎯ centered, fixed offset from avatar
        float nickSize = 11f;
        String nick = u.getNickname() != null ? u.getNickname() : "?";
        String nickShown = trimToWidth(nick, nickSize, w - 24f, false);
        float nw = Builder.INTER.get().getWidth(nickShown, nickSize);
        float nickY = avY + AV_SIZE + 10f;
        Builder.TEXT_BUILDER.text(nickShown).size(nickSize).font(Builder.INTER.get()).color(text).build()
                .render(m, x + (w - nw) / 2f, nickY);

        // 4) Status pill ⎯ centered under nick (fixed offset, no getHeight magic)
        int statusColor = u.isOnline() ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(160, 160, 170);
        String statusLabel = u.isOnline() ? "Онлайн" : "Не в сети";
        float pillW = Builder.INTER.get().getWidth(statusLabel, 7f) + 26f;
        float pillY = nickY + 16f;          // 11px text + small gap
        renderPill(m, x + (w - pillW) / 2f, pillY, statusLabel, 7f, statusColor);

        // 5) Info card ⎯ separate inner card with even rows
        float infoY = pillY + 16f + 12f;    // 16 = pill height, 12 = gap
        float infoX = x + 10f;
        float infoW = w - 20f;
        String[][] rows = {
                { "Роль", roleLabel(u.getRole()) },
                { "Сервер", or(u.getServer(), "—") },
                { "Игровой ник", or(u.getGameUsername(), "—") },
                { "Клиент", or(u.getClientLabel(), "—") },
        };
        int[] valueColors = {
                a(rc, alpha),
                a(accent, alpha),
                text,
                text,
        };

        // Card behind the rows — reserve bottom space for the action bar
        float actionBarH = 26f;
        float infoBottomLimit = y + h - 8f - actionBarH;
        float infoRowH = 20f;
        float infoCardH = rows.length * infoRowH + 4f;
        if (infoY + infoCardH > infoBottomLimit) {
            infoCardH = Math.max(0f, infoBottomLimit - infoY);
        }
        rect(m, infoX, infoY, infoW, infoCardH, scale(a(accent, alpha), 0.05f), R_MD);

        float labelSize = 6.5f;
        float valueSize = 7f;
        // Reserve space for the longest label so values don't collide
        float labelW = Builder.INTER.get().getWidth("Игровой ник", labelSize) + 4f;

        for (int i = 0; i < rows.length; i++) {
            float rowY = infoY + 2f + i * infoRowH;
            // Label left, vertically centered
            Builder.TEXT_BUILDER.text(rows[i][0]).size(labelSize).font(Builder.INTER.get())
                    .color(scale(text, 0.5f)).build()
                    .render(m, infoX + 10f, rowY + (infoRowH - labelSize) / 2f);

            // Value — right-aligned, trimmed to remaining width
            float maxValueW = infoW - 20f - labelW;
            String value = trimToWidth(rows[i][1], valueSize, maxValueW, false);
            float vw = Builder.INTER.get().getWidth(value, valueSize);
            Builder.TEXT_BUILDER.text(value).size(valueSize).font(Builder.INTER.get())
                    .color(valueColors[i]).build()
                    .render(m, infoX + infoW - vw - 10f, rowY + (infoRowH - valueSize) / 2f);

            // Divider between rows
            if (i < rows.length - 1) {
                rect(m, infoX + 10f, rowY + infoRowH - 1f,
                        infoW - 20f, 1f, scale(text, 0.18f), 0.5f);
            }
        }

        // 6) Action bar — Написать (DM) + Добавить в друзья
        float barH = 18f;
        float barY = y + h - barH - 8f;
        float halfW = (w - 20f - GAP) / 2f;
        float b1x = x + 10f;
        float b2x = b1x + halfW + GAP;

        boolean hoverMsg = MouseClick.isClick(mx, my, b1x, barY, halfW, barH);
        if (hoverMsg) handCursor = true;
        rect(m, b1x, barY, halfW, barH, scale(a(accent, alpha), hoverMsg ? 0.22f : 0.12f), R_SM);
        drawGlyphCentered(m, "Написать", 7.5f, b1x, barY, halfW, barH, a(accent, alpha));
        final String peerNick = u.getNickname();
        profileHits.add(new Hit(b1x, barY, halfW, barH, () -> {
            dmPeer = peerNick;
            tab = Tab.FRIENDS;
            focused = Field.NONE;
            dmInput = "";
            lastDmSize = -1;
            dmAuto = true;
        }));

        IRClient cc = LynxWave.getInstance().getIRClient();
        boolean self = cc != null && peerNick != null
                && peerNick.equalsIgnoreCase(cc.getCurrentNickname());
        boolean already = cc != null && cc.isFriend(peerNick);
        if (self) {
            rect(m, b2x, barY, halfW, barH, scale(a(accent, alpha), 0.06f), R_SM);
            drawGlyphCentered(m, "Это вы", 7.5f, b2x, barY, halfW, barH, scale(text, 0.5f));
        } else if (already) {
            int green = ColorUtils.rgb(80, 200, 120);
            rect(m, b2x, barY, halfW, barH, scale(a(green, alpha), 0.22f), R_SM);
            drawGlyphCentered(m, "В друзьях", 7.5f, b2x, barY, halfW, barH, a(green, alpha));
        } else {
            boolean hoverAdd = MouseClick.isClick(mx, my, b2x, barY, halfW, barH);
            if (hoverAdd) handCursor = true;
            rect(m, b2x, barY, halfW, barH, a(accent, alpha * (hoverAdd ? 1f : 0.85f)), R_SM);
            drawGlyphCentered(m, "Добавить", 7.5f, b2x, barY, halfW, barH, a(-1, alpha));
            profileHits.add(new Hit(b2x, barY, halfW, barH, () -> {
                IRClient c = LynxWave.getInstance().getIRClient();
                if (c != null) c.sendFriendRequest(peerNick);
            }));
        }
    }

    // ---------- FRIENDS & DM ----------

    private static final float FRIEND_ROW_H = 34f;
    private static final float REQUEST_ROW_H = 46f;
    private static final float DM_HEAD_H = 30f;
    private static final float DM_MSG_H = 22f;

    private void renderFriends(DrawContext ctx, Matrix4f m, int mx, int my,
                               float x, float y, float w, float h,
                               int accent, int text) {
        friendHits.clear();

        IRClient cc = LynxWave.getInstance().getIRClient();
        long now = System.currentTimeMillis();
        if (now - lastFriendsRefresh > 5000L) {
            lastFriendsRefresh = now;
            if (cc != null && cc.isLoggedIn()) cc.requestFriends();
        }
        List<String> requests = cc != null ? cc.getIncomingFriendRequests() : new ArrayList<>();
        List<IRClient.OnlineUser> friends = cc != null ? cc.getFriends() : new ArrayList<>();
        friends.sort(Comparator
                .comparing((IRClient.OnlineUser u) -> !u.isOnline())
                .thenComparing(u -> u.getNickname() == null ? "" : u.getNickname(),
                        String.CASE_INSENSITIVE_ORDER));

        float listW = (float) Math.floor((w - GAP) * 0.42f);
        float convW = w - listW - GAP;
        float listX = x;
        float convX = x + listW + GAP;

        friendsListBounds[0] = listX; friendsListBounds[1] = y; friendsListBounds[2] = listW; friendsListBounds[3] = h;
        dmBounds[0] = convX; dmBounds[1] = y; dmBounds[2] = convW; dmBounds[3] = h;

        renderFriendsPanel(ctx, m, mx, my, listX, y, listW, h, requests, friends, accent, text);
        renderConversation(ctx, m, mx, my, convX, y, convW, h, accent, text);
    }

    private void renderFriendsPanel(DrawContext ctx, Matrix4f m, int mx, int my,
                                    float x, float y, float w, float h,
                                    List<String> requests, List<IRClient.OnlineUser> friends,
                                    int accent, int text) {
        containerCard(m, x, y, w, h, accent);

        float total = 0f;
        if (!requests.isEmpty()) total += 16f + requests.size() * (REQUEST_ROW_H + 4f) + 8f;
        total += 16f + friends.size() * (FRIEND_ROW_H + 2f) + 8f;

        friendsListH = h - 16f;
        friendsContentH = total;
        float maxScroll = Math.max(0f, total - friendsListH);
        if (friendsTarget > maxScroll) friendsTarget = maxScroll;
        if (friendsTarget < 0) friendsTarget = 0;
        friendsScroll += (friendsTarget - friendsScroll) * 0.18f;

        ScissorUtil.enableContext(ctx, x + 4f, y + 8f, w - 8f, h - 16f);
        float cy = y + 8f - friendsScroll;
        float rowW = w - 16f - (maxScroll > 0 ? SCROLL_W + 6f : 0f);
        float rowX = x + 8f;

        if (!requests.isEmpty()) {
            Builder.TEXT_BUILDER.text("ЗАЯВКИ — " + requests.size()).size(6.5f).font(Builder.INTER.get())
                    .color(scale(text, 0.5f)).build().render(m, rowX + 4f, cy + 2f);
            cy += 16f;
            for (String reqNick : requests) {
                if (cy + REQUEST_ROW_H > y + 6f && cy < y + h - 6f) {
                    renderRequestRow(m, mx, my, rowX, cy, rowW, reqNick, accent, text);
                }
                cy += REQUEST_ROW_H + 4f;
            }
            cy += 8f;
        }

        Builder.TEXT_BUILDER.text("ДРУЗЬЯ — " + friends.size()).size(6.5f).font(Builder.INTER.get())
                .color(scale(text, 0.5f)).build().render(m, rowX + 4f, cy + 2f);
        cy += 16f;
        if (friends.isEmpty()) {
            Builder.TEXT_BUILDER.text("Пока никого. Добавьте друзей").size(7f).font(Builder.INTER.get())
                    .color(scale(text, 0.4f)).build().render(m, rowX + 4f, cy + 4f);
            Builder.TEXT_BUILDER.text("во вкладке «Онлайн».").size(7f).font(Builder.INTER.get())
                    .color(scale(text, 0.4f)).build().render(m, rowX + 4f, cy + 16f);
        } else {
            for (IRClient.OnlineUser f : friends) {
                if (cy + FRIEND_ROW_H > y + 6f && cy < y + h - 6f) {
                    renderFriendRow(m, mx, my, rowX, cy, rowW, f, accent, text);
                }
                cy += FRIEND_ROW_H + 2f;
            }
        }

        ScissorUtil.disableContext(ctx);
        renderScroll(m, x + w - SCROLL_W - 4f, y + 8f, friendsListH,
                Math.max(total, 1f), friendsScroll, maxScroll, accent);
    }

    private void renderRequestRow(Matrix4f m, int mx, int my, float x, float y, float w,
                                  String nick, int accent, int text) {
        rect(m, x, y, w, REQUEST_ROW_H, scale(a(accent, alpha), 0.05f), R_SM);

        float avSize = 22f;
        float avX = x + 8f;
        float avY = y + 6f;
        renderAvatar(m, avX, avY, avSize, mcNickFor(nick), nick, ColorUtils.rgb(120, 145, 200));

        float textX = avX + avSize + 8f;
        Builder.TEXT_BUILDER.text(trimToWidth(nick, 8f, w - (textX - x) - 8f, false)).size(8f)
                .font(Builder.INTER.get()).color(text).build().render(m, textX, y + 7f);
        Builder.TEXT_BUILDER.text("хочет в друзья").size(6.5f).font(Builder.INTER.get())
                .color(scale(text, 0.45f)).build().render(m, textX, y + 19f);

        // Buttons row
        float by = y + REQUEST_ROW_H - 18f - 6f;
        final String fn = nick;
        float aw = renderTextButton(m, mx, my, textX, by, "Принять",
                a(ColorUtils.rgb(80, 200, 120), alpha), friendHits, () -> {
                    IRClient c = LynxWave.getInstance().getIRClient();
                    if (c != null) c.respondFriendRequest(fn, true);
                });
        renderTextButton(m, mx, my, textX + aw + 6f, by, "Отклонить",
                a(ColorUtils.rgb(210, 80, 80), alpha * 0.85f), friendHits, () -> {
                    IRClient c = LynxWave.getInstance().getIRClient();
                    if (c != null) c.respondFriendRequest(fn, false);
                });
    }

    private void renderFriendRow(Matrix4f m, int mx, int my, float x, float y, float w,
                                 IRClient.OnlineUser f, int accent, int text) {
        String nick = f.getNickname() != null ? f.getNickname() : "?";
        boolean selected = dmPeer != null && nick.equalsIgnoreCase(dmPeer);
        boolean hover = MouseClick.isClick(mx, my, x, y, w, FRIEND_ROW_H);
        if (hover) handCursor = true;
        if (hover || selected) {
            rect(m, x, y, w, FRIEND_ROW_H, a(accent, alpha * (selected ? 0.16f : 0.07f)), R_SM);
        }
        if (selected) {
            rect(m, x + 3f, y + 6f, 2.5f, FRIEND_ROW_H - 12f, a(accent, alpha), 1.25f);
        }

        float avSize = 22f;
        float avX = x + 8f;
        float avY = y + (FRIEND_ROW_H - avSize) / 2f;
        int rc = roleColor(f.getRole(), accent);
        renderAvatar(m, avX, avY, avSize, f.getGameUsername(), nick, rc);
        int statusColor = f.isOnline() ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(140, 140, 150);
        float sd = 6f;
        rect(m, avX + avSize - sd, avY + avSize - sd, sd, sd, scale(a(accent, alpha), 0.6f), sd / 2f);
        rect(m, avX + avSize - sd + 1f, avY + avSize - sd + 1f, sd - 2f, sd - 2f,
                a(statusColor, alpha), (sd - 2f) / 2f);

        float rmSize = 16f;
        float rmX = x + w - rmSize - 6f;
        float rmY = y + (FRIEND_ROW_H - rmSize) / 2f;
        boolean hoverRm = MouseClick.isClick(mx, my, rmX, rmY, rmSize, rmSize);
        if (hoverRm) handCursor = true;
        int red = ColorUtils.rgb(235, 90, 90);
        rect(m, rmX, rmY, rmSize, rmSize, a(red, alpha * (hoverRm ? 0.4f : 0.0f)), rmSize / 2f);
        drawGlyphCentered(m, "x", 9f, rmX, rmY, rmSize, rmSize,
                hoverRm ? a(red, alpha) : scale(a(red, alpha), 0.6f));

        float textX = avX + avSize + 8f;
        String shown = trimToWidth(nick, 8.5f, rmX - textX - 6f, false);
        Builder.TEXT_BUILDER.text(shown).size(8.5f).font(Builder.INTER.get())
                .color(f.isOnline() ? text : scale(text, 0.5f)).build().render(m, textX, y + 7f);
        String sub = f.isOnline()
                ? (f.getServer() != null && !f.getServer().isBlank() ? f.getServer() : "В сети")
                : "Не в сети";
        Builder.TEXT_BUILDER.text(trimToWidth(sub, 6.5f, rmX - textX - 6f, false)).size(6.5f)
                .font(Builder.INTER.get()).color(scale(text, 0.4f)).build().render(m, textX, y + 21f);

        final String fn = nick;
        friendHits.add(new Hit(rmX, rmY, rmSize, rmSize, () -> {
            IRClient c = LynxWave.getInstance().getIRClient();
            if (c != null) c.removeFriend(fn);
            if (fn.equalsIgnoreCase(dmPeer)) dmPeer = null;
        }));
        friendHits.add(new Hit(x, y, w, FRIEND_ROW_H, () -> {
            dmPeer = fn;
            dmInput = "";
            focused = Field.NONE;
            lastDmSize = -1;
            dmAuto = true;
        }));
    }

    private void renderConversation(DrawContext ctx, Matrix4f m, int mx, int my,
                                    float x, float y, float w, float h,
                                    int accent, int text) {
        containerCard(m, x, y, w, h, accent);

        if (dmPeer == null) {
            String hint = "Выберите друга слева";
            String hint2 = "Личные сообщения видны только вам двоим";
            float hw = Builder.INTER.get().getWidth(hint, 8.5f);
            Builder.TEXT_BUILDER.text(hint).size(8.5f).font(Builder.INTER.get())
                    .color(scale(text, 0.45f)).build().render(m, x + (w - hw) / 2f, y + h / 2f - 10f);
            float hw2 = Builder.INTER.get().getWidth(hint2, 6.5f);
            Builder.TEXT_BUILDER.text(hint2).size(6.5f).font(Builder.INTER.get())
                    .color(scale(text, 0.3f)).build().render(m, x + (w - hw2) / 2f, y + h / 2f + 4f);
            return;
        }

        IRClient cc = LynxWave.getInstance().getIRClient();
        boolean peerOnline = cc != null && cc.isUserOnline(dmPeer);

        // Header
        float avSize = 20f;
        float avX = x + 10f;
        float avY = y + (DM_HEAD_H - avSize) / 2f;
        renderAvatar(m, avX, avY, avSize, mcNickFor(dmPeer), dmPeer, accent);
        Builder.TEXT_BUILDER.text(trimToWidth(dmPeer, 9f, w - 80f, false)).size(9f)
                .font(Builder.INTER.get()).color(text).build().render(m, avX + avSize + 8f, y + 7f);
        int statusColor = peerOnline ? ColorUtils.rgb(80, 220, 120) : ColorUtils.rgb(150, 150, 160);
        Builder.TEXT_BUILDER.text(peerOnline ? "в сети" : "не в сети").size(6.5f).font(Builder.INTER.get())
                .color(a(statusColor, alpha)).build().render(m, avX + avSize + 8f, y + 19f);
        rect(m, x + 10f, y + DM_HEAD_H, w - 20f, 1f, scale(a(accent, alpha), 0.12f), 0.5f);

        // Message list
        float inputBlockH = INPUT_H + GAP;
        float listY = y + DM_HEAD_H + 6f;
        float listVisH = h - DM_HEAD_H - 6f - inputBlockH;
        dmListH = listVisH;

        List<IRClient.DmEntry> log = cc != null ? cc.getDmLog(dmPeer) : new ArrayList<>();
        float total = log.size() * DM_MSG_H;
        if (log.size() != lastDmSize) {
            float maxNow = Math.max(0f, total - listVisH);
            if (dmAuto) { dmScrollTarget = maxNow; dmScroll = maxNow; }
            lastDmSize = log.size();
        }
        dmScroll += (dmScrollTarget - dmScroll) * 0.2f;
        float maxScroll = Math.max(0f, total - listVisH);
        if (dmScrollTarget > maxScroll) dmScrollTarget = maxScroll;
        if (dmScrollTarget < 0) dmScrollTarget = 0;

        ScissorUtil.enableContext(ctx, x + 6f, listY, w - 12f, listVisH);
        float baseY = listY - dmScroll;
        if (log.isEmpty()) {
            String empty = "Нет сообщений. Напишите первым!";
            float ew = Builder.INTER.get().getWidth(empty, 7f);
            Builder.TEXT_BUILDER.text(empty).size(7f).font(Builder.INTER.get())
                    .color(scale(text, 0.35f)).build().render(m, x + (w - ew) / 2f, listY + listVisH / 2f - 4f);
        }
        for (int i = 0; i < log.size(); i++) {
            IRClient.DmEntry e = log.get(i);
            float ry = baseY + i * DM_MSG_H;
            if (ry + DM_MSG_H < listY || ry > listY + listVisH) continue;
            float msgSize = 8f;
            float maxBubbleW = w - 40f;
            String msg = trimToWidth(e.message(), msgSize, maxBubbleW - 16f, false);
            float tw = Builder.INTER.get().getWidth(msg, msgSize);
            float bw = tw + 16f;
            float bh = DM_MSG_H - 4f;
            float bx = e.self() ? (x + w - 10f - bw) : (x + 10f);
            int bg = e.self() ? a(accent, alpha * 0.85f) : scale(a(accent, alpha), 0.10f);
            rect(m, bx, ry, bw, bh, bg, R_SM);
            int tcol = e.self() ? a(-1, alpha) : text;
            Builder.TEXT_BUILDER.text(msg).size(msgSize).font(Builder.INTER.get()).color(tcol).build()
                    .render(m, bx + 8f, ry + (bh - msgSize) / 2f);
        }
        ScissorUtil.disableContext(ctx);
        renderScroll(m, x + w - SCROLL_W - 4f, listY, listVisH,
                Math.max(total, 1f), dmScroll, maxScroll, accent);

        // Input
        float fieldH = INPUT_H;
        float inputY = y + h - fieldH;
        float fieldLeft = x + 8f;
        float fieldW = w - 16f;
        rect(m, fieldLeft, inputY, fieldW, fieldH,
                scale(a(accent, alpha), focused == Field.DM ? 0.1f : 0.06f), R_MD);
        if (focused == Field.DM) {
            Builder.BORDER_BUILDER.size(new SizeState(fieldW, fieldH))
                    .color(new QuadColorState(a(accent, alpha * 0.45f)))
                    .radius(new QuadRadiusState(R_MD)).thickness(-0.3f).build().render(m, fieldLeft, inputY);
        }
        float sendBtnSize = fieldH - 8f;
        float fieldTextW = fieldW - 16f - sendBtnSize - 10f;
        boolean empty = dmInput.isEmpty();
        String shown = empty ? "Сообщение…" : dmInput;
        String trimmed = trimToWidth(shown, 8f, fieldTextW, true);
        if (focused == Field.DM && selectAll && !empty) {
            float selW = Math.min(Builder.INTER.get().getWidth(trimmed, 8f), fieldTextW) + 3f;
            rect(m, fieldLeft + 11f, inputY + (fieldH - 12f) / 2f, selW, 12f, a(accent, alpha * 0.3f), 2f);
        }
        Builder.TEXT_BUILDER.text(trimmed).size(8f).font(Builder.INTER.get())
                .color(empty ? scale(text, 0.4f) : text).build()
                .render(m, fieldLeft + 12f, inputY + (fieldH - 8f) / 2f);
        if (focused == Field.DM) {
            long t = (System.currentTimeMillis() - caretBlinkStart) % 1000L;
            if (t < 530L) {
                float caretX = fieldLeft + 12f + (empty ? 0f : Builder.INTER.get().getWidth(trimmed, 8f));
                if (caretX > fieldLeft + 12f + fieldTextW) caretX = fieldLeft + 12f + fieldTextW;
                rect(m, caretX + 1f, inputY + (fieldH - 12f) / 2f, 0.8f, 12f, text, 0.4f);
            }
        }
        float btnX = fieldLeft + fieldW - sendBtnSize - 4f;
        float btnY = inputY + (fieldH - sendBtnSize) / 2f;
        boolean hoverSend = MouseClick.isClick(mx, my, btnX, btnY, sendBtnSize, sendBtnSize);
        if (hoverSend) handCursor = true;
        boolean canSend = !dmInput.isBlank();
        int sendBg = canSend ? a(accent, alpha * (hoverSend ? 1f : 0.85f)) : a(accent, alpha * 0.22f);
        rect(m, btnX, btnY, sendBtnSize, sendBtnSize, sendBg, sendBtnSize / 2f);
        drawGlyphCentered(m, "›", 12f, btnX, btnY, sendBtnSize, sendBtnSize,
                canSend ? a(-1, alpha) : a(-1, alpha * 0.5f));

        friendHits.add(new Hit(fieldLeft, inputY, fieldW - sendBtnSize - 8f, fieldH, () -> {
            focused = Field.DM;
            caretBlinkStart = System.currentTimeMillis();
        }));
        friendHits.add(new Hit(btnX, btnY, sendBtnSize, sendBtnSize, this::doSendDm));
    }

    private float renderTextButton(Matrix4f m, int mx, int my, float x, float y, String label,
                                   int colorBase, List<Hit> hits, Action onClick) {
        float ts = 7f;
        float tw = Builder.INTER.get().getWidth(label, ts);
        float bw = tw + 16f;
        float bh = 18f;
        boolean hover = MouseClick.isClick(mx, my, x, y, bw, bh);
        if (hover) handCursor = true;
        rect(m, x, y, bw, bh, hover ? colorBase : scale(colorBase, 0.85f), bh / 2f);
        float th = Builder.INTER.get().getHeight(label, ts);
        Builder.TEXT_BUILDER.text(label).size(ts).font(Builder.INTER.get())
                .color(a(-1, alpha)).build().render(m, x + 8f, y + (bh - th) / 2f);
        hits.add(new Hit(x, y, bw, bh, onClick));
        return bw;
    }

    private void drawGlyphCentered(Matrix4f m, String glyph, float size,
                                   float x, float y, float w, float h, int color) {
        float gw = Builder.INTER.get().getWidth(glyph, size);
        float gh = Builder.INTER.get().getHeight(glyph, size);
        Builder.TEXT_BUILDER.text(glyph).size(size).font(Builder.INTER.get()).color(color).build()
                .render(m, x + (w - gw) / 2f, y + (h - gh) / 2f - 0.2f);
    }

    private void doSendDm() {
        selectAll = false;
        if (dmPeer == null || dmInput.isBlank()) return;
        IRClient c = LynxWave.getInstance().getIRClient();
        if (c == null || !c.isOpen()) return;
        c.sendPrivateMessage(dmPeer, dmInput);
        dmInput = "";
        dmAuto = true;
    }

    // ---------- SETTINGS ----------

    /** Single source-of-truth for the settings layout — used both by render and click hit-test. */
    private static final class SettingItem {
        final String label;
        final String desc;
        final java.util.function.BooleanSupplier getter;
        final java.util.function.Consumer<Boolean> setter;
        final boolean isAction;
        final int actionColor; // for action rows

        static SettingItem toggle(String label, String desc,
                                  java.util.function.BooleanSupplier g,
                                  java.util.function.Consumer<Boolean> s) {
            return new SettingItem(label, desc, g, s, false, 0);
        }
        static SettingItem action(String label, String desc, int color, Runnable r) {
            return new SettingItem(label, desc, null, b -> r.run(), true, color);
        }
        private SettingItem(String label, String desc,
                            java.util.function.BooleanSupplier g,
                            java.util.function.Consumer<Boolean> s,
                            boolean action, int color) {
            this.label = label; this.desc = desc;
            this.getter = g; this.setter = s;
            this.isAction = action; this.actionColor = color;
        }
    }

    private static final class SettingGroup {
        final String title;
        final java.util.List<SettingItem> items;
        SettingGroup(String title, java.util.List<SettingItem> items) {
            this.title = title; this.items = items;
        }
    }

    private java.util.List<SettingGroup> buildSettings() {
        IRCSettings s = IRCSettings.get();
        s.ensureLoaded();
        java.util.List<SettingGroup> groups = new ArrayList<>();

        groups.add(new SettingGroup("Уведомления", java.util.List.of(
                SettingItem.toggle("Системные уведомления Windows",
                        WindowsToastNotifier.isSupported()
                                ? "Toast в правом нижнем углу при новых сообщениях"
                                : "Доступно только на Windows",
                        s::isNotificationsEnabled, s::setNotificationsEnabled),
                SettingItem.toggle("Только когда игра свёрнута",
                        "Не отвлекать тостами, пока окно майнкрафта в фокусе",
                        s::isNotifyOnlyWhenMinimized, s::setNotifyOnlyWhenMinimized),
                SettingItem.toggle("Только упоминания",
                        "Уведомлять только когда упомянули ваш ник",
                        s::isNotifyMentionsOnly, s::setNotifyMentionsOnly),
                SettingItem.toggle("Личные сообщения",
                        "Уведомлять о ЛС всегда, независимо от других правил",
                        s::isNotifyDirectMessages, s::setNotifyDirectMessages)
        )));

        groups.add(new SettingGroup("Чат", java.util.List.of(
                SettingItem.toggle("Показывать время",
                        "Метка HH:mm перед каждым сообщением",
                        s::isShowTimestamps, s::setShowTimestamps),
                SettingItem.toggle("Компактный режим",
                        "Меньше отступов, больше сообщений на экране",
                        s::isCompactMode, s::setCompactMode),
                SettingItem.toggle("Показывать клиент",
                        "Префикс [LynxWave] и т.п. перед ником",
                        s::isShowClientPrefix, s::setShowClientPrefix)
        )));

        groups.add(new SettingGroup("Присутствие", java.util.List.of(
                SettingItem.toggle("Скрывать сервер Minecraft",
                        "Не показывать другим, на каком сервере вы играете",
                        s::isHideMcServer, s::setHideMcServer),
                SettingItem.toggle("Невидимый режим",
                        "Не отображаться в списке онлайн (статус «не в сети»)",
                        s::isInvisibleMode, s::setInvisibleMode)
        )));

        groups.add(new SettingGroup("Аккаунт", java.util.List.of(
                SettingItem.toggle("Автовход",
                        "Сохранять логин/пароль и входить автоматически",
                        s::isAutoLogin, v -> {
                            s.setAutoLogin(v);
                            if (!v) IRCAutoLoginStorage.getInstance().clear();
                        }),
                SettingItem.action("Сменить аккаунт",
                        "Выйти и открыть форму входа с новым аккаунтом",
                        ColorUtils.rgb(255, 180, 80),
                        () -> {
                            IRCAutoLoginStorage.getInstance().clear();
                            IRClient c = LynxWave.getInstance().getIRClient();
                            if (c != null) c.logout();
                            username = "";
                            password = "";
                            authMode = AuthMode.LOGIN;
                            focused = Field.NONE;
                        }),
                SettingItem.action("Выйти из аккаунта",
                        "Завершить текущую сессию",
                        ColorUtils.rgb(255, 100, 100),
                        () -> {
                            IRCAutoLoginStorage.getInstance().clear();
                            IRClient c = LynxWave.getInstance().getIRClient();
                            if (c != null) c.logout();
                            username = "";
                            password = "";
                            focused = Field.NONE;
                        })
        )));

        return groups;
    }

    private static final float SETTING_ROW_H = 38f;
    private static final float SETTING_GROUP_GAP = 14f;
    private static final float SETTING_TITLE_H = 22f;

    private void renderSettings(DrawContext ctx, Matrix4f m, int mx, int my,
                                float x, float y, float w, float h,
                                int accent, int text) {
        containerCard(m, x, y, w, h, accent);

        java.util.List<SettingGroup> groups = buildSettings();

        // Compute total content height
        float total = 0f;
        for (SettingGroup g : groups) {
            total += SETTING_TITLE_H + g.items.size() * SETTING_ROW_H + SETTING_GROUP_GAP;
        }
        settingsContentH = total;
        settingsListH = h - 16f;
        float maxScroll = Math.max(0f, total - settingsListH);
        if (settingsTarget > maxScroll) settingsTarget = maxScroll;
        if (settingsTarget < 0) settingsTarget = 0;
        settingsScroll += (settingsTarget - settingsScroll) * 0.18f;

        float contentX = x + 14f;
        float contentW = w - 28f - (maxScroll > 0 ? SCROLL_W + 6f : 0f);

        ScissorUtil.enableContext(ctx, x + 4f, y + 8f, w - 8f, h - 16f);

        float cy = y + 8f - settingsScroll;
        for (SettingGroup g : groups) {
            // Group title
            Builder.TEXT_BUILDER.text(g.title.toUpperCase()).size(7f).font(Builder.INTER.get())
                    .color(scale(text, 0.5f)).build()
                    .render(m, contentX, cy + 4f);
            cy += SETTING_TITLE_H;

            // Items as a card with dividers
            float cardY = cy;
            float cardH = g.items.size() * SETTING_ROW_H;
            rect(m, contentX, cardY, contentW, cardH, scale(a(accent, alpha), 0.05f), R_MD);

            for (int i = 0; i < g.items.size(); i++) {
                SettingItem it = g.items.get(i);
                float rowY = cardY + i * SETTING_ROW_H;

                boolean hover = MouseClick.isClick(mx, my, contentX, rowY, contentW, SETTING_ROW_H);
                if (hover) handCursor = true;
                if (hover) {
                    rect(m, contentX, rowY, contentW, SETTING_ROW_H,
                            a(accent, alpha * 0.06f), R_MD);
                }

                // Label
                Builder.TEXT_BUILDER.text(it.label).size(8.5f).font(Builder.INTER.get())
                        .color(it.isAction ? a(it.actionColor, alpha) : text).build()
                        .render(m, contentX + 12f, rowY + 8f);

                // Description
                String desc = trimToWidth(it.desc, 7f, contentW - 80f, false);
                Builder.TEXT_BUILDER.text(desc).size(7f).font(Builder.INTER.get())
                        .color(scale(text, 0.5f)).build()
                        .render(m, contentX + 12f, rowY + 22f);

                // Right side — toggle or arrow
                if (!it.isAction) {
                    boolean enabled = it.getter != null && it.getter.getAsBoolean();
                    renderToggle(m,
                            contentX + contentW - 36f,
                            rowY + (SETTING_ROW_H - 16f) / 2f,
                            enabled, accent);
                } else {
                    String arrow = "›";
                    float ah = Builder.INTER.get().getHeight(arrow, 14f);
                    Builder.TEXT_BUILDER.text(arrow).size(14f).font(Builder.INTER.get())
                            .color(a(it.actionColor, alpha * 0.7f)).build()
                            .render(m, contentX + contentW - 18f,
                                    rowY + (SETTING_ROW_H - ah) / 2f);
                }

                // Divider
                if (i < g.items.size() - 1) {
                    rect(m, contentX + 10f, rowY + SETTING_ROW_H - 0.5f,
                            contentW - 20f, 0.5f, scale(text, 0.1f), 0.25f);
                }
            }

            cy += cardH + SETTING_GROUP_GAP;
        }

        ScissorUtil.disableContext(ctx);

        renderScroll(m, x + w - SCROLL_W - 6f, y + 8f, settingsListH,
                total, settingsScroll, maxScroll, accent);
    }

    private void renderToggle(Matrix4f m, float x, float y, boolean on, int accent) {
        float w = 28f, h = 16f;
        int trackOff = ColorUtils.rgb(70, 75, 85);
        int track = on ? a(accent, alpha) : a(trackOff, alpha * 0.6f);
        rect(m, x, y, w, h, track, h / 2f);

        float knobSize = h - 4f;
        float knobX = on ? x + w - knobSize - 2f : x + 2f;
        float knobY = y + 2f;
        rect(m, knobX, knobY, knobSize, knobSize, a(-1, alpha), knobSize / 2f);
    }

    // ---------- COMMON ----------

    private void renderEmpty(Matrix4f m, float y, float h, String s, int color) {
        float w = Builder.INTER.get().getWidth(s, 9f);
        Builder.TEXT_BUILDER.text(s).size(9f).font(Builder.INTER.get())
                .color(scale(color, 0.55f)).build()
                .render(m, panelX + (W - w) / 2f, y + h / 2f - 5f);
    }

    private void renderScroll(Matrix4f m, float x, float y, float visH,
                              float contentH, float scroll, float maxScroll, int accent) {
        if (maxScroll <= 0f) return;
        rect(m, x, y, SCROLL_W, visH, scale(a(accent, alpha), 0.08f), SCROLL_W / 2f);
        float thumbH = Math.max(20f, visH * (visH / contentH));
        float thumbY = y + (scroll / maxScroll) * (visH - thumbH);
        rect(m, x, thumbY, SCROLL_W, thumbH, scale(a(accent, alpha), 0.4f), SCROLL_W / 2f);
    }

    // ---------- INPUT ----------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing) return true;
        if (button != 0) return super.mouseClicked(click, doubled);
        selectAll = false;
        double mx = GLUtils.scaleMouseX(mouseX, 2);
        double my = GLUtils.scaleMouseY(mouseY, 2);

        boolean loggedIn = isLoggedIn();
        float bodyX = panelX + PAD;
        float bodyW = W - PAD * 2f;

        if (!loggedIn) {
            return handleAuthClick(mx, my);
        }

        // Sidebar icons
        Tab[] tabs = SIDEBAR_TABS;
        float ix = panelX + PAD + (SIDEBAR_W - SIDEBAR_ICON) / 2f;
        float iyStart = panelY + HEADER_H + 12f;
        for (int i = 0; i < tabs.length; i++) {
            float by = iyStart + i * (SIDEBAR_ICON + 6f);
            if (MouseClick.isClick(mx, my, ix, by, SIDEBAR_ICON, SIDEBAR_ICON)) {
                tab = tabs[i];
                focused = Field.NONE;
                caretBlinkStart = System.currentTimeMillis();
                return true;
            }
        }

        float topY = panelY + HEADER_H;
        float colsH = H - HEADER_H - PAD;
        float mainX = panelX + PAD + SIDEBAR_W + GAP;
        float membersX = panelX + W - PAD - MEMBERS_W;
        boolean showMembers = tab == Tab.CHAT;
        float mainW = (showMembers ? membersX - GAP : panelX + W - PAD) - mainX;

        if (tab == Tab.CHAT) {
            // Chat input area
            float inputBlockH = INPUT_H + GAP;
            float listH = colsH - inputBlockH;
            float inputY = topY + listH + GAP;
            float fieldH = INPUT_H;
            float sendSize = fieldH - 8f;
            float fieldTextW = mainW - 16f - sendSize - 10f;

            if (MouseClick.isClick(mx, my, mainX, inputY, mainW - sendSize - 8f, fieldH)) {
                focused = Field.MESSAGE;
                caretBlinkStart = System.currentTimeMillis();
                return true;
            }
            float btnX = mainX + mainW - sendSize - 4f;
            float btnY = inputY + (fieldH - sendSize) / 2f;
            if (MouseClick.isClick(mx, my, btnX, btnY, sendSize, sendSize)) {
                doSend();
                return true;
            }

            // Members panel — click on row sets selectedProfile
            if (MouseClick.isClick(mx, my, membersX, topY, MEMBERS_W, colsH)) {
                handleMemberClick(mx, my, membersX, topY, MEMBERS_W, colsH);
                return true;
            }
            focused = Field.NONE;
            return true;
        }

        if (tab == Tab.FRIENDS) {
            for (Hit hit : friendHits) {
                if (MouseClick.isClick(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                    hit.action.run();
                    return true;
                }
            }
            focused = Field.NONE;
            return true;
        }

        if (tab == Tab.SETTINGS) {
            return handleSettingsClick(mx, my, mainX, topY, panelX + W - PAD - mainX, colsH);
        }

        if (tab == Tab.ONLINE) {
            // Profile action buttons take priority over row selection
            for (Hit hit : profileHits) {
                if (MouseClick.isClick(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                    hit.action.run();
                    return true;
                }
            }
            float fullW = panelX + W - PAD - mainX;
            float listW = (float) Math.floor((fullW - GAP) * 0.6f);
            float headerH = 28f;
            float listY = topY + headerH + 4f;
            float listVisH = colsH - headerH - 8f;

            IRClient client = LynxWave.getInstance().getIRClient();
            List<IRClient.OnlineUser> users = client != null
                    ? new ArrayList<>(client.getCachedOnlineUsers()) : new ArrayList<>();
            users.sort(Comparator
                    .comparing((IRClient.OnlineUser u) -> !u.isOnline())
                    .thenComparingInt(u -> roleWeight(u.getRole()))
                    .thenComparing(u -> u.getNickname() == null ? "" : u.getNickname(),
                            String.CASE_INSENSITIVE_ORDER));

            float contentH = users.size() * (USER_ROW_H + GAP);
            float maxScroll = Math.max(0f, contentH - listVisH);
            float rowsX = mainX + 8f;
            float rowsW = listW - 16f - (maxScroll > 0 ? SCROLL_W + 6f : 0f);

            for (int i = 0; i < users.size(); i++) {
                float rowY = listY - onlineScroll + i * (USER_ROW_H + GAP);
                if (rowY + USER_ROW_H < listY || rowY > listY + listVisH) continue;
                if (MouseClick.isClick(mx, my, rowsX, rowY, rowsW, USER_ROW_H)) {
                    selectedProfile = users.get(i);
                    return true;
                }
            }
            return true;
        }

        focused = Field.NONE;
        return super.mouseClicked(click, doubled);
    }

    private void handleMemberClick(double mx, double my, float x, float y, float w, float h) {
        IRClient cc = LynxWave.getInstance().getIRClient();
        List<IRClient.OnlineUser> users = cc != null
                ? new ArrayList<>(cc.getCachedOnlineUsers()) : new ArrayList<>();
        users.sort(Comparator
                .comparing((IRClient.OnlineUser u) -> !u.isOnline())
                .thenComparingInt(u -> roleWeight(u.getRole()))
                .thenComparing(u -> u.getNickname() == null ? "" : u.getNickname(),
                        String.CASE_INSENSITIVE_ORDER));

        java.util.List<IRClient.OnlineUser> admins = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> mods = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> mem = new ArrayList<>();
        java.util.List<IRClient.OnlineUser> off = new ArrayList<>();
        for (IRClient.OnlineUser u : users) {
            if (!u.isOnline()) { off.add(u); continue; }
            String r = u.getRole() == null ? "" : u.getRole().toLowerCase();
            switch (r) {
                case "admin" -> admins.add(u);
                case "moderator", "mod" -> mods.add(u);
                default -> mem.add(u);
            }
        }

        float cy = y + 8f - onlineScroll;
        cy = memberSectionClick(mx, my, x, cy, w, admins);
        if (cy < 0) return;
        cy = memberSectionClick(mx, my, x, cy, w, mods);
        if (cy < 0) return;
        cy = memberSectionClick(mx, my, x, cy, w, mem);
        if (cy < 0) return;
        memberSectionClick(mx, my, x, cy, w, off);
    }

    /** Returns next Y, or -1 if click was consumed (sets selectedProfile). */
    private float memberSectionClick(double mx, double my, float x, float startY, float w,
                                     java.util.List<IRClient.OnlineUser> list) {
        if (list.isEmpty()) return startY;
        float cy = startY + 14f;
        for (IRClient.OnlineUser u : list) {
            float rowH = 26f;
            if (MouseClick.isClick(mx, my, x + 6f, cy, w - 12f, rowH)) {
                selectedProfile = u;
                tab = Tab.ONLINE;
                return -1f;
            }
            cy += rowH + 2f;
        }
        return cy + 6f;
    }

    /** Hit test for settings tab — mirrors {@link #renderSettings}. */
    private boolean handleSettingsClick(double mx, double my, float x, float y, float w, float h) {
        java.util.List<SettingGroup> groups = buildSettings();
        float maxScroll = Math.max(0f, settingsContentH - settingsListH);

        float contentX = x + 14f;
        float contentW = w - 28f - (maxScroll > 0 ? SCROLL_W + 6f : 0f);

        float cy = y + 8f - settingsScroll;
        for (SettingGroup g : groups) {
            cy += SETTING_TITLE_H;
            float cardY = cy;
            for (int i = 0; i < g.items.size(); i++) {
                SettingItem it = g.items.get(i);
                float rowY = cardY + i * SETTING_ROW_H;
                if (rowY + SETTING_ROW_H < y || rowY > y + h) continue;
                if (MouseClick.isClick(mx, my, contentX, rowY, contentW, SETTING_ROW_H)) {
                    if (it.isAction) {
                        it.setter.accept(true);
                    } else {
                        boolean cur = it.getter != null && it.getter.getAsBoolean();
                        it.setter.accept(!cur);
                    }
                    return true;
                }
            }
            cy += g.items.size() * SETTING_ROW_H + SETTING_GROUP_GAP;
        }
        return true;
    }

    /** Hit test for auth screen — must mirror layout in {@link #renderAuth}. */
    private boolean handleAuthClick(double mx, double my) {
        boolean register = authMode == AuthMode.REGISTER;

        float bodyY = panelY + HEADER_H + GAP;
        float bodyH = H - (bodyY - panelY) - PAD;
        float cardW = Math.min(AUTH_CARD_W, (W - PAD * 2f) - 24f);
        float cardX = panelX + (W - cardW) / 2f;

        float titleH = 16f;
        float subtitleH = 10f;
        float titleBlock = titleH + 4f + subtitleH;
        float fieldsH = (10f + AUTH_LABEL_GAP + INPUT_H) * 2 + AUTH_FIELD_GAP;
        float btnGap = 16f;
        float footerGap = 14f;
        float footerH = 9f;
        float contentH = titleBlock + 22f + fieldsH + btnGap + BTN_H + footerGap + footerH;
        float cardY = bodyY + Math.max(0f, (bodyH - contentH) / 2f);

        float subY = cardY + Builder.INTER.get().getHeight("X", 14f) + 6f;

        // Username input
        float fy = subY + 28f + 10f + AUTH_LABEL_GAP;
        if (MouseClick.isClick(mx, my, cardX, fy, cardW, INPUT_H)) {
            focused = Field.USERNAME;
            caretBlinkStart = System.currentTimeMillis();
            return true;
        }

        // Password input
        float pwY = fy + INPUT_H + AUTH_FIELD_GAP + 10f + AUTH_LABEL_GAP;
        if (MouseClick.isClick(mx, my, cardX, pwY, cardW, INPUT_H)) {
            focused = Field.PASSWORD;
            caretBlinkStart = System.currentTimeMillis();
            return true;
        }

        // Submit button
        float btnY = pwY + INPUT_H + btnGap;
        if (MouseClick.isClick(mx, my, cardX, btnY, cardW, BTN_H)) {
            doSubmit();
            return true;
        }

        // Footer link
        float footerY = btnY + BTN_H + footerGap;
        String hint = register ? "Уже есть аккаунт?  " : "Нужен аккаунт?  ";
        String linkText = register ? "Войти" : "Зарегистрироваться";
        float hintW = Builder.INTER.get().getWidth(hint, 8f);
        float linkW = Builder.INTER.get().getWidth(linkText, 8f);
        float fxStart = panelX + (W - (hintW + linkW)) / 2f;
        if (MouseClick.isClick(mx, my, fxStart + hintW, footerY - 2f, linkW, 12f)) {
            authMode = register ? AuthMode.LOGIN : AuthMode.REGISTER;
            focused = Field.NONE;
            return true;
        }

        focused = Field.NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.SETTINGS) {
            float maxScroll = Math.max(0f, settingsContentH - settingsListH);
            settingsTarget = (float) Math.max(0, Math.min(maxScroll, settingsTarget - verticalAmount * 24));
            return true;
        }
        if (tab == Tab.ONLINE) {
            IRClient cc = LynxWave.getInstance().getIRClient();
            int size = cc != null ? cc.getCachedOnlineUsers().size() : 0;
            float contentH = size * (USER_ROW_H + GAP);
            float maxScroll = Math.max(0f, contentH - onlineListH);
            onlineTarget = (float) Math.max(0, Math.min(maxScroll, onlineTarget - verticalAmount * 24));
            return true;
        }
        if (tab == Tab.CHAT) {
            List<IRClient.ChatLogEntry> log = IRClient.getChatLog();
            float contentH = computeChatTotalH(log, IRCSettings.get().isCompactMode());
            float maxScroll = Math.max(0f, contentH - chatListH + 12f);
            chatTarget = (float) Math.max(0, Math.min(maxScroll, chatTarget - verticalAmount * 24));
            chatAuto = chatTarget >= maxScroll - 1f;
            return true;
        }
        if (tab == Tab.FRIENDS) {
            double smx = GLUtils.scaleMouseX(mouseX, 2);
            boolean overDm = dmPeer != null && smx >= dmBounds[0] && smx <= dmBounds[0] + dmBounds[2];
            if (overDm) {
                List<IRClient.DmEntry> log = LynxWave.getInstance().getIRClient() != null
                        ? LynxWave.getInstance().getIRClient().getDmLog(dmPeer) : new ArrayList<>();
                float total = log.size() * DM_MSG_H;
                float maxScroll = Math.max(0f, total - dmListH);
                dmScrollTarget = (float) Math.max(0, Math.min(maxScroll, dmScrollTarget - verticalAmount * 24));
                dmAuto = dmScrollTarget >= maxScroll - 1f;
            } else {
                float maxScroll = Math.max(0f, friendsContentH - friendsListH);
                friendsTarget = (float) Math.max(0, Math.min(maxScroll, friendsTarget - verticalAmount * 24));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClose();
            return true;
        }
        if (focused == Field.NONE) return super.keyPressed(keyInput);

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        // Ctrl+A — выделить всё
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            if (!currentFieldValue().isEmpty()) selectAll = true;
            return true;
        }
        // Ctrl+C — копировать (пароль не копируем)
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            String v = currentFieldValue();
            if (focused != Field.PASSWORD && !v.isEmpty() && client != null) {
                client.keyboard.setClipboard(v);
            }
            return true;
        }
        // Ctrl+X — вырезать
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            String v = currentFieldValue();
            if (!v.isEmpty()) {
                if (focused != Field.PASSWORD && client != null) client.keyboard.setClipboard(v);
                setFieldValue("");
            }
            selectAll = false;
            caretBlinkStart = System.currentTimeMillis();
            return true;
        }
        // Ctrl+V — вставить
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = client != null ? client.keyboard.getClipboard() : "";
            if (clip != null && !clip.isEmpty()) {
                clip = clip.replace("\r", " ").replace("\n", " ");
                String base = selectAll ? "" : currentFieldValue();
                int max = fieldMaxLength();
                String combined = base + clip;
                if (combined.length() > max) combined = combined.substring(0, max);
                setFieldValue(combined);
            }
            selectAll = false;
            caretBlinkStart = System.currentTimeMillis();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectAll) {
                setFieldValue("");
                selectAll = false;
            } else {
                String cur = currentFieldValue();
                if (!cur.isEmpty()) setFieldValue(cur.substring(0, cur.length() - 1));
            }
            caretBlinkStart = System.currentTimeMillis();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            selectAll = false;
            if (focused == Field.MESSAGE) doSend();
            else if (focused == Field.DM) doSendDm();
            else if (focused == Field.USERNAME) focused = Field.PASSWORD;
            else if (focused == Field.PASSWORD) doSubmit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            selectAll = false;
            if (focused == Field.USERNAME) focused = Field.PASSWORD;
            else if (focused == Field.PASSWORD) focused = Field.USERNAME;
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();
        if (focused == Field.NONE) return super.charTyped(charInput);
        if (chr < 32 || chr == 127) return false;
        int max = fieldMaxLength();
        String cur = selectAll ? "" : currentFieldValue();
        selectAll = false;
        if (cur.length() >= max) return true;
        setFieldValue(cur + chr);
        caretBlinkStart = System.currentTimeMillis();
        return true;
    }

    private int fieldMaxLength() {
        return (focused == Field.MESSAGE || focused == Field.DM) ? 256 : 64;
    }

    // ---------- HELPERS ----------

    private String currentFieldValue() {
        return switch (focused) {
            case USERNAME -> username;
            case PASSWORD -> password;
            case MESSAGE -> message;
            case DM -> dmInput;
            default -> "";
        };
    }

    private void setFieldValue(String v) {
        switch (focused) {
            case USERNAME -> username = v;
            case PASSWORD -> password = v;
            case MESSAGE -> message = v;
            case DM -> dmInput = v;
            default -> { }
        }
    }

    private void doSubmit() {
        selectAll = false;
        if (username.isBlank() || password.isBlank()) return;
        IRClient c = ensureClient();
        if (c == null) return;
        if (authMode == AuthMode.REGISTER) c.register(username, password);
        else c.login(username, password);
        password = "";
    }

    private void doSend() {
        selectAll = false;
        if (message.isBlank()) return;
        IRClient c = LynxWave.getInstance().getIRClient();
        if (c == null || !c.isOpen()) return;
        c.sendMessage(message);
        message = "";
        chatAuto = true;
    }

    private IRClient ensureClient() {
        try {
            IRC irc = IRC.INSTANCE;
            if (irc != null) return irc.ensureConnected();
        } catch (Exception ignored) {}
        return LynxWave.getInstance().getIRClient();
    }

    private boolean isLoggedIn() {
        IRClient c = LynxWave.getInstance().getIRClient();
        return c != null && c.isLoggedIn();
    }

    private String resolveStatus() {
        IRClient c = LynxWave.getInstance().getIRClient();
        if (c == null) return "Нет соединения";
        if (!c.isOpen()) return "Подключение...";
        if (c.isLoggedIn()) return "Авторизован — " + c.getCurrentNickname();
        return "Готов к авторизации";
    }

    private static String initialOf(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.substring(0, 1).toUpperCase();
    }

    private static String or(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    /** Try to find Minecraft nickname for an IRC author from cached online users. */
    private String mcNickFor(String ircAuthor) {
        if (ircAuthor == null || ircAuthor.isBlank()) return null;
        IRClient cc = LynxWave.getInstance().getIRClient();
        if (cc == null) return null;
        for (IRClient.OnlineUser u : cc.getCachedOnlineUsers()) {
            if (u.getNickname() != null && u.getNickname().equalsIgnoreCase(ircAuthor)) {
                String mc = u.getGameUsername();
                if (mc != null && !mc.isBlank()) return mc;
            }
        }
        return null;
    }

    /**
     * Renders a circular MC head if available, otherwise an initial-letter circle.
     * mcNick may be null — we still draw the placeholder circle.
     */
    private void renderAvatar(Matrix4f m, float x, float y, float size,
                              String mcNick, String displayName, int circleColor) {
        net.minecraft.util.Identifier textureId = mcNick != null ? PlayerHeadCache.get().headFor(mcNick) : null;
        if (textureId != null) {
            net.minecraft.client.texture.AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
            // Circular head from skin atlas
            Builder.HEAD_TEXTURE_BUILDER
                    .size(new SizeState(size, size))
                    .radius(new QuadRadiusState(size / 2f))
                    .color(new QuadColorState(a(-1, alpha)))
                    .smoothness(1.0f)
                    .texture(0f, 0f, 1f, 1f, texture)
                    .build().render(m, x, y);
            return;
        }
        // Fallback: tinted circle with initial
        rect(m, x, y, size, size, a(circleColor, alpha * 0.25f), size / 2f);
        Builder.BORDER_BUILDER.size(new SizeState(size, size))
                .color(new QuadColorState(a(circleColor, alpha * 0.55f)))
                .radius(new QuadRadiusState(size / 2f)).thickness(-0.3f)
                .build().render(m, x, y);
        String initial = initialOf(displayName);
        float iSize = Math.max(7f, size * 0.42f);
        float iw = Builder.INTER.get().getWidth(initial, iSize);
        float ih = Builder.INTER.get().getHeight(initial, iSize);
        Builder.TEXT_BUILDER.text(initial).size(iSize).font(Builder.INTER.get())
                .color(a(circleColor, alpha)).build()
                .render(m, x + (size - iw) / 2f, y + (size - ih) / 2f - 0.3f);
    }

    private int roleColor(String role, int accent) {
        if (role == null) return ColorUtils.rgb(150, 160, 175);
        return switch (role.toLowerCase()) {
            case "admin" -> ColorUtils.rgb(255, 100, 100);
            case "moderator", "mod" -> ColorUtils.rgb(255, 180, 80);
            case "user", "member" -> accent;
            default -> ColorUtils.rgb(150, 160, 175);
        };
    }

    private String roleLabel(String role) {
        if (role == null) return "Игрок";
        return switch (role.toLowerCase()) {
            case "admin" -> "Администратор";
            case "moderator", "mod" -> "Модератор";
            case "user", "member" -> "Игрок";
            case "guest" -> "Гость";
            default -> role;
        };
    }

    private int roleWeight(String role) {
        if (role == null) return 5;
        return switch (role.toLowerCase()) {
            case "admin" -> 0;
            case "moderator", "mod" -> 1;
            case "user", "member" -> 2;
            default -> 3;
        };
    }

    private String trimToWidth(String s, float size, float maxW, boolean keepStart) {
        if (s == null) return "";
        if (Builder.INTER.get().getWidth(s, size) <= maxW) return s;
        String dots = "...";
        if (keepStart) {
            while (s.length() > 1 && Builder.INTER.get().getWidth(s + dots, size) > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            return s + dots;
        } else {
            while (s.length() > 1 && Builder.INTER.get().getWidth(s, size) > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }
    }

    private void startClose() {
        if (closing) return;
        closing = true;
        fade.setValue(alpha);
        fade.setDestinationValue(0.0);
        fade.reset();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private int a(int color, float factor) {
        int baseAlpha = ColorUtils.alpha(color);
        return ColorUtils.setAlpha(color, Math.round(baseAlpha * clamp01(factor)));
    }

    private int scale(int color, float factor) {
        int base = ColorUtils.alpha(color);
        return ColorUtils.setAlpha(color, Math.round(base * clamp01(factor)));
    }

    /** Unified content container — subtle fill plus a hairline accent border for a clean, framed look. */
    private void containerCard(Matrix4f m, float x, float y, float w, float h, int accent) {
        rect(m, x, y, w, h, scale(a(accent, alpha), 0.04f), R_MD);
        Builder.BORDER_BUILDER.size(new SizeState(w, h))
                .color(new QuadColorState(scale(a(accent, alpha), 0.10f)))
                .radius(new QuadRadiusState(R_MD)).thickness(-0.3f)
                .build().render(m, x, y);
    }

    private void rect(Matrix4f m, float x, float y, float w, float h, int color, float radius) {
        Builder.RECTANGLE_BUILDER.size(new SizeState(w, h))
                .color(new QuadColorState(color))
                .radius(new QuadRadiusState(radius))
                .build().render(m, x, y);
    }
}
