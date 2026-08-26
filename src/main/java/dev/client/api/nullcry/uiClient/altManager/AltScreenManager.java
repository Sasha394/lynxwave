package dev.client.api.nullcry.uiClient.altManager;

import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.helper.other.NameGenerator;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.GLUtils;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import dev.client.api.nullcry.uiClient.title.TitleBackgroundRenderer;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AltScreenManager extends Screen {
    public String copyStatusText = "Чтобы скопировать ник, выберите аккаунт и нажмите на CTRL + C";
    public String pasteStatusText = "Чтобы вставить ник, выберите ввод и нажмите на CTRL + V";
    private String errorText = null;

    final float LEFT_W = 200f, RIGHT_W = 225f, GAP = 16f, PAD = 16f;
    final float ROW_H = 42f, INPUT_H = 22f, BTN_H = 22f, BETWEEN = 6f;
    final float TITLE_SIZE = 8f, TEXT_SIZE = 9f, INPUT_TEXT = 8f;
    final float HEADER_H = 44f;
    final float RADIUS = 6f;

    final float INPUT_W = LEFT_W - 80f;
    final float ADD_W = 23f;
    final float ADD_H = 22f;
    final float ADD_GAP = 6f;

    private static final float STAR_ICON_SIZE = 10f;
    private static final float STAR_RIGHT_PADDING = 10f;
    private static final float STAR_HITBOX_PADDING = 2f;

    private static final List<ServerShortcut> SERVER_SHORTCUTS = List.of(
            new ServerShortcut("NexusMine", "mc.nexusmine.org", "images/server/nexusmine.png"),
            new ServerShortcut("FunTime", "mc.funtime.su", "images/server/funtime.png"),
            new ServerShortcut("HolyWorld", "start.holyworld.me", "images/server/holyworld.png"),
            new ServerShortcut("ReallyWorld", "mc.reallyworld.ru", "images/server/reallyworld.png"),
            new ServerShortcut("SpookyTime", "spookytime.net", "images/server/spookytime.png")
    );

    private final List<Account> accounts = new ArrayList<>();
    private final List<ServerButtonArea> serverButtonAreas = new ArrayList<>();
    private String input = "";
    boolean inputFocused = false;

    int selectedIndex = -1;
    int hoveredIndex = -1;
    boolean selectAll = false;

    float panelX, panelY, panelW, panelH;
    float listScroll = 0f, listTarget = 0f;

    private final CompactAnimation alphaAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private float renderAlpha = 1f;
    private boolean closing = false;
    private Runnable closeCallback = null;
    private boolean renderHandCursor = false;
    private boolean renderTextCursor = false;

    private static Field proxyMenuButtonField;
    private static boolean proxyMenuFieldSearched;
    private static boolean proxyIntegrationErrored;
    private ButtonWidget proxyMenuPlaceholder;

    private static final float HEAD_U = 8f / 64f;
    private static final float HEAD_V = 8f / 64f;
    private static final float HEAD_TEX_SIZE = 8f / 64f;
    private static final float HEAD_OVERLAY_U = 40f / 64f;
    private static final float HEAD_OVERLAY_V = 8f / 64f;
    private static final float HEAD_SMOOTHNESS = 0.35f;

    public AltScreenManager() {
        super(Text.literal(""));
    }

    @Override
    protected void init() {
        if (this.client != null) {
            this.width = GLUtils.getScaledWidthFor(2);
            this.height = GLUtils.getScaledHeightFor(2);
        }
        panelW = LEFT_W + GAP + RIGHT_W + PAD * 2f;
        panelH = 310f;
        panelX = (this.width - panelW) / 2f;
        panelY = (this.height - panelH) / 2f;

        reloadAccountsPreservingSelection(AltConfiguration.getSelected());
        restartReveal();
        clampScroll();
    }

    public void restartReveal() {
        alphaAnimation.setDestinationValue(1.0);
        alphaAnimation.setValue(0.0);
        alphaAnimation.reset();
        renderAlpha = 0f;
        closing = false;
        closeCallback = null;
        CursorsUtil.resetState();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        GLUtils.setupOverlayRendering(2);
        boolean restored = false;
        try {
            CursorsUtil.beginFrame();
            renderHandCursor = false;
            renderTextCursor = false;
            serverButtonAreas.clear();

            alphaAnimation.run(closing ? 0.0 : 1.0);
            alphaAnimation.update();
            renderAlpha = clamp01((float) alphaAnimation.getValue());

            if (closing && renderAlpha <= 0.01f) {
                if (closeCallback != null) {
                    Runnable callback = closeCallback;
                    closeCallback = null;
                    closing = false;
                    CursorsUtil.applyFrameCursor();
                    CursorsUtil.resetState();
                    restored = true;
                    GLUtils.restoreRendering();
                    callback.run();
                    return;
                }
            }

            if (this.client != null) {
                this.width = GLUtils.getScaledWidthFor(2);
                this.height = GLUtils.getScaledHeightFor(2);
            }
            panelW = LEFT_W + GAP + RIGHT_W + PAD * 2f;
            panelH = 310f;
            panelX = (this.width - panelW) / 2f;
            panelY = (this.height - panelH) / 2f;

            int renderMouseX = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int renderMouseY = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));

            var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

        String backgroundPath = TitleScreenSettings.getBackgroundPath();
        Builder.TEXTURE_BUILDER
                .size(new SizeState(this.width, this.height))
                .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                .build()
                .render(matrix, 0, 0);


        int baseMain = Interface.INSTANCE.getMainColor();
        int baseText = -1;
        int baseIcons = -1;

        int main = applyAlpha(baseMain, renderAlpha);
        int text = applyAlpha(baseText, renderAlpha);
        int iconsColor = applyAlpha(baseIcons, renderAlpha);
        int accentColor = applyAlpha(baseMain, renderAlpha);

        drawCurrentNick(ctx, text);

        // Soft glow behind panel
        int glowColor = applyAlpha(baseMain, renderAlpha * 0.18f);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(panelW + 16f, panelH + 16f))
                .color(new QuadColorState(glowColor))
                .radius(new QuadRadiusState(12f))
                .build()
                .render(matrix, panelX - 8f, panelY - 8f);

        HelperElements.rect(matrix,panelX,panelY,panelW,panelH);

        // === Header strip (inset so rounded corners show through) ===
        int headerTint = scaleAlpha(main, 0.06f);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(panelW - 2f, HEADER_H))
                .color(new QuadColorState(headerTint))
                .radius(new QuadRadiusState(3.5f))
                .build().render(matrix, panelX + 1f, panelY + 1f);

        // Accent bar on header
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(3f, HEADER_H - 14f))
                .color(new QuadColorState(accentColor))
                .radius(new QuadRadiusState(1.5f))
                .build().render(matrix, panelX + PAD, panelY + 7f);

        // Title + subtitle
        String hdrTitle = "Менеджер аккаунтов";
        String hdrSub = "Управление никнеймами LynxWave";
        float hdrTitleX = panelX + PAD + 10f;
        Builder.TEXT_BUILDER.text(hdrTitle).size(10.5f).font(Builder.INTER.get()).color(text).build()
                .render(matrix, hdrTitleX, panelY + 9f);
        Builder.TEXT_BUILDER.text(hdrSub).size(6.5f).font(Builder.INTER.get())
                .color(scaleAlpha(text, 0.45f)).build()
                .render(matrix, hdrTitleX, panelY + 23f);

        // Count pill on the right
        String countStr = accounts.size() + " акк.";
        float cntW = Builder.INTER.get().getWidth(countStr, 7f);
        float cntPillW = cntW + 12f;
        float cntPillH = 15f;
        float cntPillX = panelX + panelW - PAD - cntPillW;
        float cntPillY = panelY + (HEADER_H - cntPillH) / 2f;
        int cntBg = applyAlpha(baseMain, renderAlpha * 0.18f);
        Builder.RECTANGLE_BUILDER.size(new SizeState(cntPillW, cntPillH))
                .color(new QuadColorState(cntBg))
                .radius(new QuadRadiusState(cntPillH / 2f))
                .build().render(matrix, cntPillX, cntPillY);
        Builder.BORDER_BUILDER.size(new SizeState(cntPillW, cntPillH))
                .color(new QuadColorState(scaleAlpha(main, 0.35f)))
                .radius(new QuadRadiusState(cntPillH / 2f))
                .thickness(-0.3f).build().render(matrix, cntPillX, cntPillY);
        Builder.TEXT_BUILDER.text(countStr).size(7f).font(Builder.INTER.get()).color(accentColor).build()
                .render(matrix, cntPillX + (cntPillW - cntW) / 2f, cntPillY + (cntPillH - 7f) / 2f);

        // Accent divider under header
        Builder.RECTANGLE_BUILDER.size(new SizeState(panelW - PAD, 1f))
                .color(new QuadColorState(scaleAlpha(main, 0.2f)))
                .radius(new QuadRadiusState(0.5f))
                .build().render(matrix, panelX + PAD / 2f, panelY + HEADER_H + 1f);

        float leftX = panelX + PAD;
        float rightX = panelX + PAD + LEFT_W + GAP;
        float baseY = panelY + HEADER_H + 10f;

        // Section headers with dot accent
        drawSectionHeader(matrix, "НИКНЕЙМ", leftX, baseY - 3f, accentColor, scaleAlpha(text, 0.55f));
        drawSectionHeader(matrix, "АККАУНТЫ", rightX, baseY - 3f, accentColor, scaleAlpha(text, 0.55f));

        float inputY = baseY + 11f;
        drawInput(ctx, leftX, inputY, renderMouseX, renderMouseY, text, main, iconsColor);

        float addX = leftX + INPUT_W + ADD_GAP;
        float leftButtonsW = (addX + ADD_W) - leftX;

        float serverStartY = inputY + INPUT_H + 16f;
        // Servers section header
        drawSectionHeader(matrix, "СЕРВЕРЫ", leftX, serverStartY - 12f, accentColor, scaleAlpha(text, 0.55f));
        drawServerButtons(ctx, leftX, serverStartY, leftButtonsW, renderMouseX, renderMouseY, main, text);

        float buttonsBlockH = BTN_H * 2 + BETWEEN;
        float btnY = panelY + panelH - PAD - buttonsBlockH + 2;

        drawGradientButton(ctx, leftX, btnY, leftButtonsW, "X", "Случайный ник", renderMouseX, renderMouseY, baseMain, text, () -> {
            addAccount(NameGenerator.generate());
            inputFocused = false;
            errorText = null;
        });
        drawOutlineButton(ctx, leftX, btnY + BTN_H + BETWEEN, leftButtonsW, "W", "Очистить всё", renderMouseX, renderMouseY, main, text, iconsColor, () -> {
            AltConfiguration.clear();
            reloadAccountsPreservingSelection(null);
            errorText = null;
        });

        float listY = baseY + 12f;
        float listH = panelY + panelH - PAD - listY;
        drawAccountList(ctx, rightX, listY, RIGHT_W, listH, renderMouseX, renderMouseY, main, text, iconsColor, delta);
        renderStatus(ctx, text);

            if (renderTextCursor) {
                CursorsUtil.setCursor(CursorsUtil.IBEAM);
            } else if (renderHandCursor) {
                CursorsUtil.setCursor(CursorsUtil.HAND);
            }
            CursorsUtil.applyFrameCursor();
        } finally {
            if (!restored) {
                GLUtils.restoreRendering();
            }
        }
    }

    private void drawCurrentNick(DrawContext ctx, int textColor) {
        var mc = MinecraftClient.getInstance();
        String sessionName = (mc != null && mc.getSession() != null) ? mc.getSession().getUsername() : "";

        String line = "Ваш никнейм: " + sessionName;

        int screenW = mc.getWindow().getScaledWidth();
        float y = Math.max(4f, panelY - 25f);
        int col = scaleAlpha(textColor, 100 / 255f);

        float w = Builder.INTER.get().getWidth(line, 9f);
        float x = Math.round((screenW - w) / 2f);

        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);
        Builder.TEXT_BUILDER
                .text(line)
                .size(9f)
                .font(Builder.INTER.get())
                .color(col)
                .build()
                .render(matrix, x, y);
    }

    private void drawInput(DrawContext ctx, float x, float y, int mx, int my, int text, int main, int iconsColor) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

        boolean isHoveredInput = MouseClick.isClick(mx, my, x - 1f, y, INPUT_W + 1f, INPUT_H);
        if (isHoveredInput) renderTextCursor = true;

        // Background fill
        int inputBg = scaleAlpha(main, inputFocused ? 0.1f : (isHoveredInput ? 0.06f : 0.03f));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(INPUT_W, INPUT_H))
                .color(new QuadColorState(inputBg))
                .radius(new QuadRadiusState(RADIUS))
                .build().render(matrix, x, y);

        int cInput = scaleAlpha(main, inputFocused ? 0.55f : (isHoveredInput ? 0.35f : 0.18f));
        Builder.BORDER_BUILDER
                .size(new SizeState(INPUT_W, INPUT_H))
                .color(new QuadColorState(cInput))
                .radius(new QuadRadiusState(RADIUS))
                .thickness(-0.3f)
                .build().render(matrix, x, y);

        // Focus stripe
        if (inputFocused) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2f, INPUT_H - 10f))
                    .color(new QuadColorState(main))
                    .radius(new QuadRadiusState(1f))
                    .build().render(matrix, x + 4f, y + 5f);
        }

        float addX = x + INPUT_W + ADD_GAP;
        boolean isHoveredAdd = MouseClick.isClick(mx, my, addX - 1f, y, ADD_W + 1f, ADD_H);
        if (isHoveredAdd) renderHandCursor = true;

        // Gradient add button
        int addTop = applyAlpha(Interface.INSTANCE.getMainColor(), renderAlpha * (isHoveredAdd ? 0.95f : 0.8f));
        int addBot = applyAlpha(Interface.INSTANCE.getMainColor(), renderAlpha * (isHoveredAdd ? 0.7f : 0.55f));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(ADD_W, ADD_H))
                .color(new QuadColorState(addTop, addTop, addBot, addBot))
                .radius(new QuadRadiusState(RADIUS))
                .build().render(matrix, addX, y);

        float iSize = 9f;
        float iW = Builder.ICONS.get().getWidth("V", iSize);
        float cx = addX + ADD_W / 2f;
        float cy = y + ADD_H / 2f;
        float ix = (float) Math.floor(cx - iW / 2f);
        float iy = (float) Math.floor(cy - iSize / 2f) + 0.5f;

        Builder.TEXT_BUILDER
                .text("V")
                .size(iSize)
                .font(Builder.ICONS.get())
                .color(applyAlpha(-1, renderAlpha))
                .build()
                .render(matrix, ix, iy);

        float iconPad = inputFocused ? 11f : 8f;
        float textStartX = x + iconPad + Builder.ICONS.get().getWidth("U", 9f) + 5f;

        ScissorUtil.enable(x + 2f, y + 2f, INPUT_W - 4f, INPUT_H - 4f);
        Builder.TEXT_BUILDER
                .text("U")
                .size(9f)
                .font(Builder.ICONS.get())
                .color(iconsColor)
                .build()
                .render(matrix, x + iconPad, Math.round(y + (INPUT_H - 9f) / 2f));

        if (inputFocused && selectAll && !input.isEmpty()) {
            float selW = Builder.INTER.get().getWidth(input, INPUT_TEXT);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(selW + 4f, INPUT_H - 8f))
                    .color(new QuadColorState(applyAlpha(ColorUtils.rgba(50, 100, 200, 100), renderAlpha)))
                    .build()
                    .render(matrix, textStartX - 1f, y + 4f);
        }

        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (inputFocused) {
            if (input.isEmpty()) {
                if (blink) {
                    Builder.TEXT_BUILDER.text("_").size(INPUT_TEXT).font(Builder.INTER.get()).color(text).build()
                            .render(matrix, textStartX, y + (INPUT_H - INPUT_TEXT) / 2f - 0.5f);
                }
            } else {
                if (!selectAll && blink) {
                    Builder.TEXT_BUILDER.text(input + "_").size(INPUT_TEXT).font(Builder.INTER.get()).color(text).build()
                            .render(matrix, textStartX, y + (INPUT_H - INPUT_TEXT) / 2f - 0.5f);
                } else {
                    Builder.TEXT_BUILDER.text(input).size(INPUT_TEXT).font(Builder.INTER.get()).color(text).build().render(matrix, textStartX, y + (INPUT_H - INPUT_TEXT) / 2f - 0.5f);
                }
            }
        } else {
            if (input.isEmpty()) {
                int ph = scaleAlpha(text, 150 / 255f);
                Builder.TEXT_BUILDER.text("Enter name").size(INPUT_TEXT).font(Builder.INTER.get()).color(ph).build().render(matrix, textStartX, y + (INPUT_H - INPUT_TEXT) / 2f - 0.5f);
            } else {
                Builder.TEXT_BUILDER.text(input).size(INPUT_TEXT).font(Builder.INTER.get()).color(text).build().render(matrix, textStartX, y + (INPUT_H - INPUT_TEXT) / 2f - 0.5f);
            }
        }

        ScissorUtil.disable();
    }

    private void drawServerButtons(DrawContext ctx, float x, float startY, float w, int mx, int my, int main, int text) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);
        float y = startY;

        for (ServerShortcut shortcut : SERVER_SHORTCUTS) {
            boolean hovered = MouseClick.isClick(mx, my, x - 1f, y, w + 1f, BTN_H);
            if (hovered) renderHandCursor = true;

            int cardBg = scaleAlpha(main, hovered ? 0.1f : 0.04f);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(w, BTN_H))
                    .color(new QuadColorState(cardBg))
                    .radius(new QuadRadiusState(RADIUS))
                    .build().render(matrix, x, y);

            int bg = scaleAlpha(main, hovered ? 0.45f : 0.22f);
            Builder.BORDER_BUILDER
                    .size(new SizeState(w, BTN_H))
                    .color(new QuadColorState(bg))
                    .radius(new QuadRadiusState(RADIUS))
                    .thickness(-0.3f)
                    .build().render(matrix, x, y);

            // Hover: left accent stripe
            if (hovered) {
                int accent = applyAlpha(Interface.INSTANCE.getMainColor(), renderAlpha);
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(2f, BTN_H - 10f))
                        .color(new QuadColorState(accent))
                        .radius(new QuadRadiusState(1f))
                        .build().render(matrix, x + 4f, y + 5f);
            }

            float iconSize = 12f;
            float iconX = x + (hovered ? 10f : 6f);
            float iconY = Math.round(y + (BTN_H - iconSize) / 2f + 0.5f);

            Builder.TEXTURE_BUILDER
                    .size(new SizeState(iconSize, iconSize))
                    .texture(0, 0, 1, 1, ClientTexture.of(shortcut.texturePath()))
                    .color(new QuadColorState(applyAlpha(-1, renderAlpha)))
                    .build()
                    .render(matrix, iconX, iconY);

            String label = "Connect to " + shortcut.name();
            int labelColor = scaleAlpha(text, (hovered ? 255 : 200) / 255f);
            float textX = iconX + iconSize + 6f;
            float textY = Math.round(y + (BTN_H - 8f) / 2f - 0.5f);

            Builder.TEXT_BUILDER
                    .text(label)
                    .size(8f)
                    .font(Builder.INTER.get())
                    .color(labelColor)
                    .build()
                    .render(matrix, textX, textY);

            serverButtonAreas.add(new ServerButtonArea(x, y, w, BTN_H, shortcut));
            y += BTN_H + BETWEEN;
        }
    }

    private void drawSectionHeader(org.joml.Matrix4f m, String label, float x, float y, int accentColor, int textColor) {
        float dotSize = 3.5f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(dotSize, dotSize))
                .color(new QuadColorState(accentColor))
                .radius(new QuadRadiusState(dotSize / 2f))
                .build()
                .render(m, x, y + 2f);
        Builder.TEXT_BUILDER.text(label).size(7f).font(Builder.INTER.get()).color(textColor).build()
                .render(m, x + dotSize + 4f, y);
    }

    private void drawGradientButton(DrawContext ctx, float x, float y, float w, String icon, String label, int mx, int my, int baseMain, int text, Runnable onClick) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);
        boolean hover = MouseClick.isClick(mx, my, x - 1f, y, w + 1f, BTN_H);
        if (hover) renderHandCursor = true;

        int top = applyAlpha(baseMain, renderAlpha * (hover ? 0.95f : 0.8f));
        int bot = applyAlpha(baseMain, renderAlpha * (hover ? 0.7f : 0.55f));
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(w, BTN_H))
                .color(new QuadColorState(top, top, bot, bot))
                .radius(new QuadRadiusState(RADIUS))
                .build().render(matrix, x, y);

        float iconSize = 9f;
        float iconW = Builder.ICONS.get().getWidth(icon, iconSize);
        float labelW = Builder.INTER.get().getWidth(label, 8f);
        float totalW = iconW + 5f + labelW;
        float iconX = x + (w - totalW) / 2f;
        float iconY = Math.round(y + (BTN_H - iconSize) / 2f);

        int white = applyAlpha(-1, renderAlpha);
        Builder.TEXT_BUILDER.text(icon).size(iconSize).font(Builder.ICONS.get()).color(white).build()
                .render(matrix, iconX, iconY);

        float textX = iconX + iconW + 5f;
        float textY = Math.round(y + (BTN_H - 8f) / 2f - 0.5f);
        Builder.TEXT_BUILDER.text(label).size(8f).font(Builder.INTER.get()).color(white).build()
                .render(matrix, textX, textY);
    }

    private void drawOutlineButton(DrawContext ctx, float x, float y, float w, String icon, String label, int mx, int my, int main, int text, int iconsColor, Runnable onClick) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);
        boolean hover = MouseClick.isClick(mx, my, x - 1f, y, w + 1f, BTN_H);
        if (hover) renderHandCursor = true;

        int bg = scaleAlpha(main, hover ? 0.1f : 0.04f);
        Builder.RECTANGLE_BUILDER.size(new SizeState(w, BTN_H)).color(new QuadColorState(bg))
                .radius(new QuadRadiusState(RADIUS)).build().render(matrix, x, y);
        int border = scaleAlpha(main, hover ? 0.5f : 0.25f);
        Builder.BORDER_BUILDER.size(new SizeState(w, BTN_H)).color(new QuadColorState(border))
                .radius(new QuadRadiusState(RADIUS)).thickness(-0.3f).build().render(matrix, x, y);

        float iconSize = 9f;
        float iconW = Builder.ICONS.get().getWidth(icon, iconSize);
        float labelW = Builder.INTER.get().getWidth(label, 8f);
        float totalW = iconW + 5f + labelW;
        float iconX = x + (w - totalW) / 2f;
        float iconY = Math.round(y + (BTN_H - iconSize) / 2f);

        Builder.TEXT_BUILDER.text(icon).size(iconSize).font(Builder.ICONS.get()).color(iconsColor).build()
                .render(matrix, iconX, iconY);

        int textCol = scaleAlpha(text, hover ? 1f : 0.8f);
        float textX = iconX + iconW + 5f;
        float textY = Math.round(y + (BTN_H - 8f) / 2f - 0.5f);
        Builder.TEXT_BUILDER.text(label).size(8f).font(Builder.INTER.get()).color(textCol).build()
                .render(matrix, textX, textY);
    }

    private void drawButton(DrawContext ctx, float x, float y, float w, String icon, String label, int mx, int my, int main, int text, int iconsColor, Runnable onClick) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);
        boolean isHovered = MouseClick.isClick(mx, my, x - 1f, y, w + 1f, BTN_H);
        if (isHovered) renderHandCursor = true;

        int bg = scaleAlpha(main, 75 / 255f);
        Builder.BORDER_BUILDER
                .size(new SizeState(w, BTN_H))
                .color(new QuadColorState(bg))
                .radius(new QuadRadiusState(4))
                .thickness(-0.3f)
                .build().render(matrix, x, y);

        float iconSize = 9f;
        float iconX = x + 8f;
        float iconY = Math.round(y + (BTN_H - iconSize) / 2f);

        Builder.TEXT_BUILDER
                .text(icon)
                .size(iconSize)
                .font(Builder.ICONS.get())
                .color(iconsColor)
                .build()
                .render(matrix, iconX, iconY);

        int textCol = scaleAlpha(text, (isHovered ? 255 : 180) / 255f);
        float textX = iconX + Builder.ICONS.get().getWidth(icon, iconSize) + 5f;
        float textY = Math.round(y + (BTN_H - 8f) / 2f - 0.5f);

        Builder.TEXT_BUILDER
                .text(label)
                .size(8f)
                .font(Builder.INTER.get())
                .color(textCol)
                .build()
                .render(matrix, textX, textY);
    }

    private void drawAccountList(DrawContext ctx, float x, float y, float w, float h, int mx, int my, int main, int text, int iconsColor, float dt) {
        listScroll += (listTarget - listScroll) * 0.15f;

        ScissorUtil.enableContext(ctx, x, y, w, h);
        hoveredIndex = -1;
        float cy = y - listScroll;

        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            float cardY = cy + i * (ROW_H + BETWEEN);

            if (cardY > y - ROW_H && cardY < y + h) {
                boolean isHoveredCard = MouseClick.isClick(mx, my, x - 1f, cardY, w + 1f, ROW_H);
                if (isHoveredCard) {
                    hoveredIndex = i;
                    renderHandCursor = true;
                }

                float starWidth = Builder.OTHER_ICONS.get().getWidth("J", STAR_ICON_SIZE);
                float starX = x + w - STAR_RIGHT_PADDING - starWidth;
                boolean starHovered = MouseClick.isClick(mx, my, starX - STAR_HITBOX_PADDING, cardY, starWidth + STAR_HITBOX_PADDING * 2f, ROW_H);
                if (starHovered) {
                    renderHandCursor = true;
                }

                boolean isSelected = (i == selectedIndex);

                // Card background fill
                int cardBgColor = isSelected
                        ? scaleAlpha(main, 0.18f)
                        : scaleAlpha(main, isHoveredCard ? 0.08f : 0.04f);
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(w, ROW_H))
                        .color(new QuadColorState(cardBgColor))
                        .radius(new QuadRadiusState(RADIUS))
                        .build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), x, cardY);

                int borderAlpha = isSelected ? 120 : (isHoveredCard ? 85 : 50);
                int c = scaleAlpha(main, borderAlpha / 255f);
                Builder.BORDER_BUILDER
                        .size(new SizeState(w, ROW_H))
                        .color(new QuadColorState(c))
                        .radius(new QuadRadiusState(RADIUS))
                        .thickness(-0.3f)
                        .build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), x, cardY);

                // Selected accent stripe on left
                if (isSelected) {
                    int accent = applyAlpha(Interface.INSTANCE.getMainColor(), renderAlpha);
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(2.5f, ROW_H - 12f))
                            .color(new QuadColorState(accent))
                            .radius(new QuadRadiusState(1.25f))
                            .build().render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), x + 4f, cardY + 6f);
                }

                float headSize = 28f;
                float headRadius = headSize / 6f;
                float headX = x + (isSelected ? 10f : 6f);
                float headY = cardY + 7f;
                Identifier skin = skinFor(a.name());
                Color headColor = new Color(255, 255, 255, Math.round(255 * renderAlpha));

                DrawUtil.renderHeadTexture(
                        ctx,
                        headX,
                        headY,
                        headSize,
                        headSize,
                        headRadius,
                        HEAD_U,
                        HEAD_V,
                        HEAD_TEX_SIZE,
                        HEAD_TEX_SIZE,
                        skin,
                        headColor,
                        HEAD_SMOOTHNESS
                );

                DrawUtil.renderHeadTexture(
                        ctx,
                        headX,
                        headY,
                        headSize,
                        headSize,
                        headRadius,
                        HEAD_OVERLAY_U,
                        HEAD_OVERLAY_V,
                        HEAD_TEX_SIZE,
                        HEAD_TEX_SIZE,
                        skin,
                        headColor,
                        HEAD_SMOOTHNESS
                );

                float nameX = headX + headSize + 6f;
                int dtColor = scaleAlpha(text, 180 / 255f);

                float textClipWidth = Math.max(0f, (starX - 6f) - nameX);
                ScissorUtil.enableContext(ctx, nameX, cardY + 4f, textClipWidth, ROW_H - 8f);
                Builder.TEXT_BUILDER
                        .text(a.name())
                        .size(TEXT_SIZE)
                        .font(Builder.INTER.get())
                        .color(text)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), nameX, cardY + 10f);

                Builder.TEXT_BUILDER
                        .text(a.date())
                        .size(8f)
                        .font(Builder.INTER.get())
                        .color(dtColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), nameX, cardY + 22f);
                ScissorUtil.disableContext(ctx);

                int starColor = a.favorite()
                        ? applyAlpha(ColorUtils.rgba(255, 214, 0, 255), renderAlpha)
                        : iconsColor;
                if (starHovered) {
                    int alpha = Math.min(255, ColorUtils.alpha(starColor) + 40);
                    starColor = ColorUtils.setAlpha(starColor, alpha);
                }

                float starY = cardY + (ROW_H - STAR_ICON_SIZE) / 2f;
                Builder.TEXT_BUILDER
                        .text("J")
                        .size(STAR_ICON_SIZE)
                        .font(Builder.OTHER_ICONS.get())
                        .color(starColor)
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx), starX, starY);
            }
        }

        ScissorUtil.disableContext(ctx);
    }

    private void renderStatus(DrawContext ctx, int textColor) {
        var mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float baseBottom = 22f;
        float spacing = 12f;

        float yCopy = screenH - baseBottom;
        float yPaste = yCopy - spacing - 1f;

        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

        String copyLine = (errorText != null) ? errorText : copyStatusText;

        int copyCol;
        int pasteCol = scaleAlpha(textColor, 100 / 255f);
        if (errorText == null) {
            copyCol = pasteCol;
        } else if ("Успешно!".equals(errorText)) {
            copyCol = applyAlpha(ColorUtils.rgba(120, 255, 120, 100), renderAlpha);
        } else {
            copyCol = applyAlpha(ColorUtils.rgba(255, 100, 100, 100), renderAlpha);
        }
        float copyW = Builder.INTER.get().getWidth(copyLine, 8f);
        float pasteW = Builder.INTER.get().getWidth(pasteStatusText, 8f);

        float xCopy = Math.round((screenW - copyW) / 2f);
        float xPaste = Math.round((screenW - pasteW) / 2f);

        Builder.TEXT_BUILDER
                .text(pasteStatusText)
                .size(8f)
                .font(Builder.INTER.get())
                .color(pasteCol)
                .build()
                .render(matrix, xPaste, yPaste);

        Builder.TEXT_BUILDER
                .text(copyLine)
                .size(8f)
                .font(Builder.INTER.get())
                .color(copyCol)
                .build()
                .render(matrix, xCopy, yCopy);
    }


    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int applyAlpha(int color, float factor) {
        float clamped = clamp01(factor);
        int baseAlpha = ColorUtils.alpha(color);
        int newAlpha = Math.round(baseAlpha * clamped);
        return ColorUtils.setAlpha(color, newAlpha);
    }

    private int scaleAlpha(int color, float scale) {
        float clamped = clamp01(scale);
        int base = ColorUtils.alpha(color);
        int newAlpha = Math.round(base * clamped);
        return ColorUtils.setAlpha(color, newAlpha);
    }


    private Identifier skinFor(String name) {
        var uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        var profile = new GameProfile(uuid, name);
        return MinecraftClient.getInstance().getSkinProvider().supplySkinTextures(profile, false).get().body().texturePath();
    }

    private void addAccount(String name) {
        if (name == null || name.isBlank()) {
            errorText = "Ошибка! Поле ввода пусто";
            return;
        }

        String trimmed = name.trim();
        if (existsInConfig(trimmed)) {
            errorText = "Ошибка! Такой ник уже есть";
            return;
        }

        boolean added = AltConfiguration.add(trimmed);
        reloadAccountsPreservingSelection(AltConfiguration.getSelected());

        errorText = added ? "Успешно!" : "Ошибка! Такой ник уже есть";
    }

    private void reloadAccountsPreservingSelection(String preferredSelection) {
        String target = preferredSelection;
        if (target == null || target.isBlank()) {
            target = AltConfiguration.getSelected();
        }

        accounts.clear();
        for (AltConfiguration.AltAccount a : AltConfiguration.getAccounts()) {
            accounts.add(new Account(a.name, a.date, a.favorite));
        }

        selectedIndex = indexOfAccount(target);
        hoveredIndex = -1;
        clampScroll();
    }

    private int indexOfAccount(String name) {
        if (name == null) return -1;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private void removeAccountAt(int index, boolean successMessage) {
        if (index < 0 || index >= accounts.size()) return;
        String name = accounts.get(index).name();
        AltConfiguration.removeByName(name);
        reloadAccountsPreservingSelection(AltConfiguration.getSelected());
        hoveredIndex = -1;
        if (successMessage) {
            errorText = "Успешно!";
        } else {
            errorText = null;
        }
    }

    private float listViewHeight() {
        return panelH - HEADER_H - 10f - 12f - PAD;
    }

    private void clampScroll() {
        float total = accounts.size() * (ROW_H + BETWEEN) - BETWEEN;
        float view = Math.max(0f, listViewHeight());
        float max = Math.max(0f, total - view);
        listTarget = Math.min(Math.max(listTarget, 0f), max);
        listScroll = Math.min(Math.max(listScroll, 0f), max);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        if (closing) return true;

        double renderMouseX = GLUtils.scaleMouseX(mx, 2);
        double renderMouseY = GLUtils.scaleMouseY(my, 2);

        float leftX = panelX + PAD;
        float baseY = panelY + HEADER_H + 10f;
        float inputY = baseY + 11f;


        float rightX = panelX + PAD + LEFT_W + GAP;
        float listY = baseY + 12f;
        float listH = panelY + panelH - PAD - listY;


        if (button == 0) {
            boolean wasFocused = inputFocused;
            inputFocused = MouseClick.isClick(renderMouseX, renderMouseY, leftX - 1f, inputY, INPUT_W + 1f, INPUT_H);
            if (inputFocused && !wasFocused) {
                selectAll = false;
                errorText = null;
            }


            float addX = leftX + INPUT_W + ADD_GAP;
            if (MouseClick.isClick(renderMouseX, renderMouseY, addX - 1f, inputY, ADD_W + 1f, ADD_H)) {
                String name = input.trim();
                if (!name.isEmpty()) {
                    addAccount(name);
                    input = "";
                    inputFocused = false;
                    selectAll = false;
                } else {
                    errorText = "Ошибка! Поле ввода пусто";
                }
                return true;
            }


            for (ServerButtonArea area : serverButtonAreas) {
                if (MouseClick.isClick(renderMouseX, renderMouseY, area.x() - 1f, area.y(), area.w() + 1f, area.h())) {
                    connectToServer(area.shortcut());
                    return true;
                }
            }


            float buttonsBlockH = BTN_H * 2 + BETWEEN;
            float btnY = panelY + panelH - PAD - buttonsBlockH + 2;


            float addSquareLeft = leftX + INPUT_W + ADD_GAP;
            float leftButtonsW = (addSquareLeft + ADD_W) - leftX;


            if (MouseClick.isClick(renderMouseX, renderMouseY, leftX - 1f, btnY, leftButtonsW + 1f, BTN_H)) {
                String rnd = NameGenerator.generate();
                addAccount(rnd);
                inputFocused = false;
                return true;
            }


            if (MouseClick.isClick(renderMouseX, renderMouseY, leftX - 1f, btnY + BTN_H + BETWEEN, leftButtonsW + 1f, BTN_H)) {
                AltConfiguration.clear();
                reloadAccountsPreservingSelection(null);
                errorText = null;
                return true;
            }


            if (MouseClick.isClick(renderMouseX, renderMouseY, rightX - 1f, listY, RIGHT_W + 1f, listH)) {
                float cy = listY - listScroll;
                for (int i = 0; i < accounts.size(); i++) {
                    float cardY = cy + i * (ROW_H + BETWEEN);
                    if (MouseClick.isClick(renderMouseX, renderMouseY, rightX - 1f, cardY, RIGHT_W + 1f, ROW_H)) {
                        Account account = accounts.get(i);
                        float starWidth = Builder.OTHER_ICONS.get().getWidth("J", STAR_ICON_SIZE);
                        float starX = rightX + RIGHT_W - STAR_RIGHT_PADDING - starWidth;
                        if (MouseClick.isClick(renderMouseX, renderMouseY, starX - STAR_HITBOX_PADDING, cardY, starWidth + STAR_HITBOX_PADDING * 2f, ROW_H)) {
                            AltConfiguration.toggleFavorite(account.name());
                            reloadAccountsPreservingSelection(account.name());
                            return true;
                        }
                        selectedIndex = i;
                        AltConfiguration.setSelected(account.name());
                        setOfflineName(account.name());
                        errorText = null;
                        return true;
                    }
                }
            }
        } else if (button == 1) {
            if (MouseClick.isClick(renderMouseX, renderMouseY, rightX - 1f, listY, RIGHT_W + 1f, listH)) {
                float cy = listY - listScroll;
                for (int i = 0; i < accounts.size(); i++) {
                    float cardY = cy + i * (ROW_H + BETWEEN);
                    if (MouseClick.isClick(renderMouseX, renderMouseY, rightX - 1f, cardY, RIGHT_W + 1f, ROW_H)) {
                        removeAccountAt(i, true);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(new Click(renderMouseX, renderMouseY, new MouseInput(button, click.modifiers())), doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (closing) return true;
        double renderMouseX = GLUtils.scaleMouseX(mx, 2);
        double renderMouseY = GLUtils.scaleMouseY(my, 2);
        float rightX = panelX + PAD + LEFT_W + GAP;
        float listY = panelY + HEADER_H + 10f + 12f;
        float listH = panelY + panelH - PAD - listY;

        if (MouseClick.isClick(renderMouseX, renderMouseY, rightX - 1f, listY, RIGHT_W + 1f, listH)) {
            float total = accounts.size() * (ROW_H + BETWEEN) - BETWEEN;
            float view = Math.max(0f, listH);
            float max = Math.max(0f, total - view);
            listTarget = Math.min(Math.max(listTarget - (float) vert * 20f, 0f), max);
            return true;
        }
        return super.mouseScrolled(renderMouseX, renderMouseY, horiz, vert);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        if (closing) return true;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startCloseToTitle();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hoveredIndex >= 0 && hoveredIndex < accounts.size()) {
                removeAccountAt(hoveredIndex, true);
            } else {
                errorText = "Ошибка! Наведите на аккаунт";
            }
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (selectedIndex >= 0 && selectedIndex < accounts.size()) {
                String nick = accounts.get(selectedIndex).name();
                MinecraftClient.getInstance().keyboard.setClipboard(nick);
                errorText = "Успешно!";
            } else {
                errorText = "Ошибка! Аккаунт не выбран";
            }
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            if (!inputFocused) {
                errorText = "Ошибка! Поле ввода не выбрано";
                return true;
            }
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clip != null && !clip.isBlank()) {
                if (selectAll) {
                    input = "";
                    selectAll = false;
                }
                input += clip.trim();
                errorText = "Успешно!";
            } else {
                errorText = "Ошибка! Буфер обмена пуст";
            }
            return true;
        }

        if (!inputFocused) return super.keyPressed(keyInput);

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAll = true;
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String name = input.trim();
                if (!name.isEmpty()) {
                    addAccount(name);
                } else {
                    errorText = "Ошибка! Поле ввода пусто";
                }
                input = "";
                selectAll = false;
                inputFocused = false;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectAll) {
                    input = "";
                    selectAll = false;
                } else if (!input.isEmpty()) {
                    input = input.substring(0, input.length() - 1);
                }
                errorText = null;
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                inputFocused = false;
                selectAll = false;
                return true;
            }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();
        if (closing) return true;
        if (inputFocused && chr >= 32 && chr != 127) {
            if (selectAll) {
                input = "";
                selectAll = false;
            }
            input += chr;
            errorText = null;
            return true;
        }
        return super.charTyped(charInput);
    }

    private void setOfflineName(String name) {
        if (name == null || name.isBlank()) return;
        final MinecraftClient mc = MinecraftClient.getInstance();
        final String n = name.trim();
        final Session newSession = new Session(
                n,
                Uuids.getOfflinePlayerUuid(n),
                "0",
                Optional.empty(),
                Optional.of("offline")
        );

        Runnable apply = () -> {
            mc.session = newSession;
            AltConfiguration.setSelected(n);
        };

        if (mc.isOnThread()) apply.run(); else mc.execute(apply);
    }

    private boolean existsInConfig(String name) {
        if (name == null) return false;
        String want = name.trim();
        for (AltConfiguration.AltAccount a : AltConfiguration.getAccounts()) {
            if (a != null && a.name != null && a.name.trim().equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private void ensureProxyMenuButtonReady() {
        if (proxyIntegrationErrored) return;

        try {
            if (!proxyMenuFieldSearched || proxyMenuButtonField == null) {
                Class<?> clazz = Class.forName("ru.fiw.proxyserver.ProxyServer");
                proxyMenuButtonField = clazz.getDeclaredField("proxyMenuButton");
                proxyMenuButtonField.setAccessible(true);
                proxyMenuFieldSearched = true;
            }
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            proxyIntegrationErrored = true;
            proxyMenuButtonField = null;
            return;
        }

        if (proxyMenuButtonField == null) {
            proxyIntegrationErrored = true;
            return;
        }

        try {
            Object existing = proxyMenuButtonField.get(null);
            if (!(existing instanceof ButtonWidget)) {
                if (proxyMenuPlaceholder == null) {
                    proxyMenuPlaceholder = ButtonWidget.builder(Text.empty(), b -> {})
                            .dimensions(0, 0, 0, 0)
                            .build();
                }
                proxyMenuButtonField.set(null, proxyMenuPlaceholder);
            }
        } catch (IllegalAccessException e) {
            proxyIntegrationErrored = true;
        }
    }

    private void connectToServer(ServerShortcut shortcut) {
        if (this.client == null || shortcut == null) return;

        String address = shortcut.address();
        if (address == null || address.isBlank()) {
            errorText = "Ошибка! Некорректный адрес сервера";
            return;
        }

        if (!ServerAddress.isValid(address)) {
            errorText = "Ошибка! Некорректный адрес сервера";
            return;
        }

        ServerAddress serverAddress = ServerAddress.parse(address);
        ServerInfo serverInfo = new ServerInfo(shortcut.name(), address, ServerInfo.ServerType.OTHER);
        errorText = null;
        ensureProxyMenuButtonReady();
        ConnectScreen.connect(this, this.client, serverAddress, serverInfo, false, null);
    }

    private void startCloseToTitle() {
        if (closing) return;
        closing = true;
        closeCallback = () -> {
            if (this.client != null) {
                TitleScreen titleScreen = new TitleScreen();
                if (titleScreen instanceof TitleScreenAlphaAccess bridge) {
                    bridge.lumora$showInstantly();
                }
                this.client.setScreen(titleScreen);
            }
        };

        double current = clamp01((float) alphaAnimation.getValue());
        alphaAnimation.setValue(current);
        alphaAnimation.setDestinationValue(0.0);
        alphaAnimation.reset();
    }

    @Override
    public void close() {
        startCloseToTitle();
    }

    private record Account(String name, String date, boolean favorite) {
    }

    private record ServerShortcut(String name, String address, String texturePath) {
    }

    private record ServerButtonArea(float x, float y, float w, float h, ServerShortcut shortcut) {
    }
}
