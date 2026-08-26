package dev.client.api.nullcry.uiClient.title;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.render.ClientTexture;
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
import dev.client.api.nullcry.uiClient.clickGui.ClickGuiScreen;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.modules.core.render.Interface;
import dev.client.api.compat.InputCompat;
import dev.client.api.compat.RenderSystem;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public final class TitleMenuScreen extends Screen implements ClientApi {

    private static final float PANEL_W = 300f;
    private static final float ROW_H = 28f;
    private static final float BTN_H = 26f;
    private static final float GAP = 5f;
    private static final float PAD = 20f;
    private static final float SECTION_GAP = 14f;
    private static final float DIVIDER_H = 1f;
    private static final float RADIUS = 7f;
    private static final float HEADER_H = 48f;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Screen parent;
    private final List<BackgroundOption> backgroundOptions = new ArrayList<>();
    private int selectedBackgroundIndex = 0;
    private float panelX, panelY, panelH;
    private boolean renderHandCursor = false;

    private String customName = "";
    private String customUrl = "";
    private enum FocusedField { NONE, NAME, URL }
    private FocusedField focusedField = FocusedField.NONE;
    private boolean selectAll = false;
    private String errorMessage = null;
    private long errorTime = 0;

    private final CompactAnimation alphaAnimation = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private float renderAlpha = 1f;
    private boolean closing = false;
    private Runnable closeCallback = null;

    public TitleMenuScreen(Screen parent) {
        super(Text.literal(""));
        this.parent = parent;
        backgroundOptions.add(new BackgroundOption("Стандартный", "images/title.png", null));
        backgroundOptions.add(new BackgroundOption("Альтернативный", "images/title2.png", null));
        backgroundOptions.add(new BackgroundOption("Оффицал", "images/title3.png", null));
        backgroundOptions.add(new BackgroundOption("Пастеры", "images/title4.png", null));
        String current = TitleScreenSettings.getBackgroundPath();
        for (int i = 0; i < backgroundOptions.size(); i++) {
            if (backgroundOptions.get(i).path.equals(current)) {
                selectedBackgroundIndex = i;
                break;
            }
        }
    }

    @Override
    protected void init() {
        if (client != null) {
            width = GLUtils.getScaledWidthFor(2);
            height = GLUtils.getScaledHeightFor(2);
        }
        panelH = nc$contentHeight();
        panelX = (width - PANEL_W) / 2f;
        panelY = (height - panelH) / 2f;
        alphaAnimation.setValue(0.0);
        alphaAnimation.setDestinationValue(1.0);
        alphaAnimation.reset();
        renderAlpha = 0f;
        closing = false;
        closeCallback = null;
        CursorsUtil.resetState();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        GLUtils.setupOverlayRendering(2);
        boolean restored = false;
        try {
            CursorsUtil.beginFrame();
            renderHandCursor = false;

            alphaAnimation.run(closing ? 0.0 : 1.0);
            alphaAnimation.update();
            renderAlpha = clamp01((float) alphaAnimation.getValue());

            if (closing && renderAlpha <= 0.01f) {
                if (closeCallback != null) {
                    Runnable cb = closeCallback;
                    closeCallback = null;
                    closing = false;
                    CursorsUtil.applyFrameCursor();
                    CursorsUtil.resetState();
                    restored = true;
                    GLUtils.restoreRendering();
                    cb.run();
                    return;
                }
            }

            if (client != null) {
                width = GLUtils.getScaledWidthFor(2);
                height = GLUtils.getScaledHeightFor(2);
            }
            panelH = nc$contentHeight();
            panelX = (width - PANEL_W) / 2f;
            panelY = (height - panelH) / 2f;

            int renderMouseX = (int) Math.round(GLUtils.scaleMouseX(mouseX, 2));
            int renderMouseY = (int) Math.round(GLUtils.scaleMouseY(mouseY, 2));

            Matrix4f m = dev.client.api.compat.GuiMatrixCompat.toMatrix4(ctx);

            RenderSystem.setShaderTexture(0, 0);
            String backgroundPath = TitleScreenSettings.getBackgroundPath();
            Builder.TEXTURE_BUILDER
                    .size(new SizeState(width, height))
                    .texture(0, 0, 1, 1, ClientTexture.of(backgroundPath))
                    .color(TitleBackgroundRenderer.resolveTextureColor(backgroundPath))
                    .build()
                    .render(m, 0, 0);

            int baseMain = Interface.INSTANCE.getMainColor();
            int baseText = -1;
            int main = applyAlpha(baseMain, renderAlpha);
            int text = applyAlpha(baseText, renderAlpha);
            int dimText = scaleAlpha(text, 0.55f);
            int subtleText = scaleAlpha(text, 0.7f);

            int glowColor = applyAlpha(baseMain, renderAlpha * 0.18f);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(PANEL_W + 16f, panelH + 16f))
                    .color(new QuadColorState(glowColor))
                    .radius(new QuadRadiusState(12f))
                    .build()
                    .render(m, panelX - 8f, panelY - 8f);

            HelperElements.rect(m, panelX, panelY, PANEL_W, panelH, renderAlpha);

            float rowW = PANEL_W - PAD * 2f;

            int headerTint = scaleAlpha(main, 0.06f);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(PANEL_W - 2f, HEADER_H))
                    .color(new QuadColorState(headerTint))
                    .radius(new QuadRadiusState(3.5f))
                    .build().render(m, panelX + 1f, panelY + 1f);

            // Accent bar
            int accentColor = applyAlpha(baseMain, renderAlpha);
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(3f, HEADER_H - 14f))
                    .color(new QuadColorState(accentColor))
                    .radius(new QuadRadiusState(1.5f))
                    .build().render(m, panelX + PAD, panelY + 7f);

            // Title + subtitle
            String title = "Настройки";
            String subtitle = "Главное меню LynxWave";
            float titleX = panelX + PAD + 10f;
            Builder.TEXT_BUILDER.text(title).size(10.5f).font(Builder.INTER.get()).color(text).build()
                    .render(m, titleX, panelY + 11f);
            Builder.TEXT_BUILDER.text(subtitle).size(6.5f).font(Builder.INTER.get()).color(scaleAlpha(text, 0.45f)).build()
                    .render(m, titleX, panelY + 25f);

            // Version pill on the right
            String versionStr = "v1.6";
            float verW = Builder.INTER.get().getWidth(versionStr, 7f);
            float verPillW = verW + 12f;
            float verPillH = 15f;
            float verPillX = panelX + PANEL_W - PAD - verPillW;
            float verPillY = panelY + (HEADER_H - verPillH) / 2f;
            int verPillBg = applyAlpha(baseMain, renderAlpha * 0.18f);
            Builder.RECTANGLE_BUILDER.size(new SizeState(verPillW, verPillH)).color(new QuadColorState(verPillBg))
                    .radius(new QuadRadiusState(verPillH / 2f)).build().render(m, verPillX, verPillY);
            Builder.BORDER_BUILDER.size(new SizeState(verPillW, verPillH)).color(new QuadColorState(scaleAlpha(main, 0.35f)))
                    .radius(new QuadRadiusState(verPillH / 2f)).thickness(-0.3f).build().render(m, verPillX, verPillY);
            Builder.TEXT_BUILDER.text(versionStr).size(7f).font(Builder.INTER.get()).color(accentColor).build()
                    .render(m, verPillX + (verPillW - verW) / 2f, verPillY + (verPillH - 7f) / 2f);

            // Accent line under header
            Builder.RECTANGLE_BUILDER.size(new SizeState(PANEL_W - PAD, DIVIDER_H))
                    .color(new QuadColorState(scaleAlpha(main, 0.2f))).radius(new QuadRadiusState(0.5f)).build()
                    .render(m, panelX + PAD / 2f, panelY + HEADER_H + 1f);

            float y = panelY + HEADER_H + SECTION_GAP;

            // === ClickGui Button (accent gradient) ===
            boolean hoverClickGui = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H);
            if (hoverClickGui) renderHandCursor = true;
            int cgTop = applyAlpha(baseMain, renderAlpha * (hoverClickGui ? 0.95f : 0.8f));
            int cgBot = applyAlpha(baseMain, renderAlpha * (hoverClickGui ? 0.7f : 0.55f));
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(rowW, BTN_H))
                    .color(new QuadColorState(cgTop, cgTop, cgBot, cgBot))
                    .radius(new QuadRadiusState(RADIUS))
                    .build()
                    .render(m, panelX + PAD, y);
            String clickGuiLabel = "Открыть ClickGui";
            float cgW = Builder.INTER.get().getWidth(clickGuiLabel, 8.5f);
            float cgH = Builder.INTER.get().getHeight(clickGuiLabel, 8.5f);
            Builder.TEXT_BUILDER.text(clickGuiLabel).size(8.5f).font(Builder.INTER.get())
                    .color(applyAlpha(-1, renderAlpha)).build()
                    .render(m, panelX + (PANEL_W - cgW) / 2f, y + (BTN_H - cgH) / 2f - 0.3f);
            y += BTN_H + SECTION_GAP;

            // === Background Section ===
            renderSectionHeader(m, "Задний фон", panelX + PAD, y, accentColor, dimText);
            y += 16f;

            for (int i = 0; i < backgroundOptions.size(); i++) {
                BackgroundOption opt = backgroundOptions.get(i);
                boolean selected = (i == selectedBackgroundIndex);
                boolean hover = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H);
                if (hover) renderHandCursor = true;

                // Card background
                int cardBg = selected ? scaleAlpha(main, 0.18f) : scaleAlpha(main, hover ? 0.08f : 0.04f);
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(rowW, ROW_H))
                        .color(new QuadColorState(cardBg))
                        .radius(new QuadRadiusState(RADIUS))
                        .build()
                        .render(m, panelX + PAD, y);

                // Left accent stripe on selected
                if (selected) {
                    Builder.RECTANGLE_BUILDER
                            .size(new SizeState(2.5f, ROW_H - 10f))
                            .color(new QuadColorState(accentColor))
                            .radius(new QuadRadiusState(1.25f))
                            .build()
                            .render(m, panelX + PAD + 4f, y + 5f);
                }

                float textOffset = selected ? 12f : 10f;
                int labelColor = selected ? text : scaleAlpha(text, hover ? 0.95f : 0.75f);

                // Name (and date if custom)
                if (opt.date != null) {
                    Builder.TEXT_BUILDER.text(opt.displayName).size(8f).font(Builder.INTER.get()).color(labelColor).build()
                            .render(m, panelX + PAD + textOffset, y + (ROW_H - 8f) / 2f - 4f);
                    String dateStr = opt.date;
                    int dateColor = scaleAlpha(text, 0.4f);
                    Builder.TEXT_BUILDER.text(dateStr).size(6f).font(Builder.INTER.get()).color(dateColor).build()
                            .render(m, panelX + PAD + textOffset, y + (ROW_H - 6f) / 2f + 5f);
                } else {
                    float labelH = Builder.INTER.get().getHeight(opt.displayName, 8f);
                    Builder.TEXT_BUILDER.text(opt.displayName).size(8f).font(Builder.INTER.get()).color(labelColor).build()
                            .render(m, panelX + PAD + textOffset, y + (ROW_H - labelH) / 2f - 0.3f);
                }

                // Selected check-mark chip on the right
                if (selected && i < 4) {
                    String chip = "активен";
                    float chipW = Builder.INTER.get().getWidth(chip, 6f);
                    float chipPillW = chipW + 10f;
                    float chipPillH = 12f;
                    float chipX = panelX + PAD + rowW - chipPillW - 8f;
                    float chipY = y + (ROW_H - chipPillH) / 2f;
                    int chipBg = applyAlpha(baseMain, renderAlpha * 0.2f);
                    Builder.RECTANGLE_BUILDER.size(new SizeState(chipPillW, chipPillH)).color(new QuadColorState(chipBg))
                            .radius(new QuadRadiusState(chipPillH / 2f)).build().render(m, chipX, chipY);
                    Builder.TEXT_BUILDER.text(chip).size(6f).font(Builder.INTER.get()).color(accentColor).build()
                            .render(m, chipX + (chipPillW - chipW) / 2f, chipY + (chipPillH - 6f) / 2f);
                }

                // Delete button for custom backgrounds (index >= 4)
                if (i >= 4) {
                    float delSize = 16f;
                    float delX = panelX + PAD + rowW - delSize - 6f;
                    float delY = y + (ROW_H - delSize) / 2f;
                    boolean hoverDel = MouseClick.isClick(renderMouseX, renderMouseY, delX, delY, delSize, delSize);
                    if (hoverDel) renderHandCursor = true;

                    int delBg = hoverDel
                            ? ColorUtils.setAlpha(ColorUtils.rgb(255, 90, 90), Math.round(60 * renderAlpha))
                            : scaleAlpha(text, 0.05f);
                    Builder.RECTANGLE_BUILDER.size(new SizeState(delSize, delSize)).color(new QuadColorState(delBg))
                            .radius(new QuadRadiusState(delSize / 2f)).build().render(m, delX, delY);

                    int delColor = hoverDel
                            ? applyAlpha(ColorUtils.rgb(255, 120, 120), renderAlpha)
                            : scaleAlpha(text, 0.5f);
                    String xStr = "x";
                    float xW = Builder.INTER.get().getWidth(xStr, 7f);
                    float xH = Builder.INTER.get().getHeight(xStr, 7f);
                    Builder.TEXT_BUILDER.text(xStr).size(7f).font(Builder.INTER.get()).color(delColor).build()
                            .render(m, delX + (delSize - xW) / 2f, delY + (delSize - xH) / 2f - 0.3f);
                }

                y += ROW_H + GAP;
            }

            // === Custom Background Section ===
            y += SECTION_GAP - GAP;
            renderSectionHeader(m, "Добавить свой фон", panelX + PAD, y, accentColor, dimText);
            y += 16f;

            // Name field
            boolean nameFocused = focusedField == FocusedField.NAME;
            boolean hoverName = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H);
            if (hoverName) renderHandCursor = true;

            renderInputField(ctx, m, panelX + PAD, y, rowW, ROW_H,
                    customName, "Название фона...", nameFocused, hoverName, main, text, subtleText);
            y += ROW_H + GAP;

            // URL field
            boolean urlFieldFocused = focusedField == FocusedField.URL;
            boolean hoverUrl = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H);
            if (hoverUrl) renderHandCursor = true;

            renderInputField(ctx, m, panelX + PAD, y, rowW, ROW_H,
                    customUrl, "Ссылка на картинку...", urlFieldFocused, hoverUrl, main, text, subtleText);
            y += ROW_H + GAP;

            // Error message
            if (errorMessage != null && System.currentTimeMillis() - errorTime < 3000) {
                int errColor = applyAlpha(ColorUtils.rgb(255, 80, 80), renderAlpha);
                Builder.TEXT_BUILDER.text(errorMessage).size(6.5f).font(Builder.INTER.get()).color(errColor).build()
                        .render(m, panelX + PAD, y);
            }
            y += 9f;

            // Apply button - disabled look if name is empty
            boolean canApply = customName != null && !customName.isBlank()
                    && customUrl != null && !customUrl.isBlank();
            boolean hoverApply = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H);
            if (hoverApply && canApply) renderHandCursor = true;

            String applyLabel = "Добавить";
            float aw = Builder.INTER.get().getWidth(applyLabel, 8.5f);
            float apH = Builder.INTER.get().getHeight(applyLabel, 8.5f);

            if (canApply) {
                int apTop = applyAlpha(baseMain, renderAlpha * (hoverApply ? 0.95f : 0.8f));
                int apBot = applyAlpha(baseMain, renderAlpha * (hoverApply ? 0.7f : 0.55f));
                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(rowW, BTN_H))
                        .color(new QuadColorState(apTop, apTop, apBot, apBot))
                        .radius(new QuadRadiusState(RADIUS))
                        .build().render(m, panelX + PAD, y);
                Builder.TEXT_BUILDER.text(applyLabel).size(8.5f).font(Builder.INTER.get())
                        .color(applyAlpha(-1, renderAlpha)).build()
                        .render(m, panelX + (PANEL_W - aw) / 2f, y + (BTN_H - apH) / 2f - 0.3f);
            } else {
                int disBg = scaleAlpha(main, 0.06f);
                Builder.RECTANGLE_BUILDER.size(new SizeState(rowW, BTN_H)).color(new QuadColorState(disBg))
                        .radius(new QuadRadiusState(RADIUS)).build().render(m, panelX + PAD, y);
                int disabledBorder = scaleAlpha(main, 0.15f);
                Builder.BORDER_BUILDER.size(new SizeState(rowW, BTN_H)).color(new QuadColorState(disabledBorder))
                        .radius(new QuadRadiusState(RADIUS)).thickness(-0.3f).build().render(m, panelX + PAD, y);
                Builder.TEXT_BUILDER.text(applyLabel).size(8.5f).font(Builder.INTER.get())
                        .color(scaleAlpha(text, 0.3f)).build()
                        .render(m, panelX + (PANEL_W - aw) / 2f, y + (BTN_H - apH) / 2f - 0.3f);
            }
            y += BTN_H + SECTION_GAP;

            // === Divider ===
            renderDivider(m, panelX + PAD, y, rowW, main);
            y += DIVIDER_H + SECTION_GAP;

            // === Updates button (outlined) ===
            boolean hoverUpdates = MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H);
            if (hoverUpdates) renderHandCursor = true;

            int updBg = scaleAlpha(main, hoverUpdates ? 0.1f : 0.04f);
            Builder.RECTANGLE_BUILDER.size(new SizeState(rowW, BTN_H)).color(new QuadColorState(updBg))
                    .radius(new QuadRadiusState(RADIUS)).build().render(m, panelX + PAD, y);
            int updatesBorder = scaleAlpha(main, hoverUpdates ? 0.5f : 0.25f);
            Builder.BORDER_BUILDER.size(new SizeState(rowW, BTN_H)).color(new QuadColorState(updatesBorder))
                    .radius(new QuadRadiusState(RADIUS)).thickness(-0.3f).build().render(m, panelX + PAD, y);
            String updatesLabel = "Список обновлений →";
            float uw = Builder.INTER.get().getWidth(updatesLabel, 8.5f);
            float upH = Builder.INTER.get().getHeight(updatesLabel, 8.5f);
            Builder.TEXT_BUILDER.text(updatesLabel).size(8.5f).font(Builder.INTER.get())
                    .color(scaleAlpha(text, hoverUpdates ? 1f : 0.8f)).build()
                    .render(m, panelX + (PANEL_W - uw) / 2f, y + (BTN_H - upH) / 2f - 0.3f);

            if (renderHandCursor) CursorsUtil.setCursor(CursorsUtil.HAND);
            else if (focusedField != FocusedField.NONE) CursorsUtil.setCursor(CursorsUtil.IBEAM);
            CursorsUtil.applyFrameCursor();
        } finally {
            if (!restored) GLUtils.restoreRendering();
        }
    }

    private void renderInputField(DrawContext ctx, Matrix4f m, float x, float y, float w, float h,
                                  String value, String placeholder, boolean focused, boolean hover,
                                  int main, int text, int subtleText) {
        // Background fill
        int bgFill = scaleAlpha(main, focused ? 0.1f : (hover ? 0.06f : 0.03f));
        Builder.RECTANGLE_BUILDER.size(new SizeState(w, h)).color(new QuadColorState(bgFill))
                .radius(new QuadRadiusState(RADIUS)).build().render(m, x, y);

        // Border
        int borderColor = scaleAlpha(main, focused ? 0.55f : (hover ? 0.35f : 0.18f));
        Builder.BORDER_BUILDER.size(new SizeState(w, h)).color(new QuadColorState(borderColor))
                .radius(new QuadRadiusState(RADIUS)).thickness(-0.3f).build().render(m, x, y);

        // Left focus indicator stripe
        if (focused) {
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(2f, h - 10f))
                    .color(new QuadColorState(main))
                    .radius(new QuadRadiusState(1f))
                    .build().render(m, x + 4f, y + 5f);
        }

        float textX = x + (focused ? 12f : 10f);
        boolean empty = value == null || value.isBlank();
        String shown = empty ? placeholder : value;
        int color = empty ? scaleAlpha(text, 0.3f) : subtleText;
        float textH = Builder.INTER.get().getHeight(shown.isEmpty() ? "A" : shown, 7.5f);
        float textYBase = y + (h - textH) / 2f - 0.3f;

        ScissorUtil.enableContext(ctx, x + 4f, y + 2f, w - 8f, h - 4f);
        Builder.TEXT_BUILDER.text(shown).size(7.5f).font(Builder.INTER.get()).color(color).build()
                .render(m, textX, textYBase);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cw = empty ? 0f : Builder.INTER.get().getWidth(value, 7.5f);
            Builder.TEXT_BUILDER.text("|").size(7.5f).font(Builder.INTER.get())
                    .color(scaleAlpha(text, 0.8f)).build().render(m, textX + cw, textYBase);
        }
        ScissorUtil.disableContext(ctx);
    }

    private void renderDivider(Matrix4f m, float x, float y, float w, int color) {
        int divColor = scaleAlpha(color, 0.15f);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(w, DIVIDER_H))
                .color(new QuadColorState(divColor))
                .radius(new QuadRadiusState(0.5f))
                .build()
                .render(m, x, y);
    }

    private void renderSectionHeader(Matrix4f m, String label, float x, float y, int accentColor, int textColor) {
        float dotSize = 4f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(dotSize, dotSize))
                .color(new QuadColorState(accentColor))
                .radius(new QuadRadiusState(dotSize / 2f))
                .build()
                .render(m, x, y + 2f);
        Builder.TEXT_BUILDER.text(label).size(7.5f).font(Builder.INTER.get()).color(textColor).build()
                .render(m, x + dotSize + 5f, y);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (closing) return true;
        double renderMouseX = GLUtils.scaleMouseX(mouseX, 2);
        double renderMouseY = GLUtils.scaleMouseY(mouseY, 2);
        float rowW = PANEL_W - PAD * 2f;

        float y = panelY + HEADER_H + SECTION_GAP;

        // ClickGui button
        if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H)) {
            if (client != null) {
                ClickGuiScreen.setOpenedFromTitle(true);
                client.setScreen(LynxWave.getInstance().getClickGuiScreen());
            }
            return true;
        }
        y += BTN_H + SECTION_GAP;

        // Background section header
        y += 16f;

        // Background options
        for (int i = 0; i < backgroundOptions.size(); i++) {
            // Delete button for custom (index >= 4)
            if (i >= 4) {
                float delSize = 16f;
                float delX = panelX + PAD + rowW - delSize - 6f;
                float delY = y + (ROW_H - delSize) / 2f;
                if (MouseClick.isClick(renderMouseX, renderMouseY, delX, delY, delSize, delSize)) {
                    backgroundOptions.remove(i);
                    if (selectedBackgroundIndex >= backgroundOptions.size()) {
                        selectedBackgroundIndex = 0;
                        TitleScreenSettings.setBackgroundPath(backgroundOptions.get(0).path);
                    } else if (selectedBackgroundIndex == i) {
                        selectedBackgroundIndex = 0;
                        TitleScreenSettings.setBackgroundPath(backgroundOptions.get(0).path);
                    }
                    focusedField = FocusedField.NONE;
                    return true;
                }
            }

            if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H)) {
                selectedBackgroundIndex = i;
                TitleScreenSettings.setBackgroundPath(backgroundOptions.get(i).path);
                focusedField = FocusedField.NONE;
                return true;
            }
            y += ROW_H + GAP;
        }

        // Gap before custom section header
        y += SECTION_GAP - GAP;

        // "Добавить свой фон" section header
        y += 16f;

        // Name field
        if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H)) {
            focusedField = FocusedField.NAME;
            selectAll = false;
            return true;
        }
        y += ROW_H + GAP;

        // URL field
        if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, ROW_H)) {
            focusedField = FocusedField.URL;
            selectAll = false;
            return true;
        }
        y += ROW_H + GAP;

        // Error line
        y += 9f;

        // Apply button
        if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H)) {
            applyCustomBackground();
            return true;
        }

        focusedField = FocusedField.NONE;
        selectAll = false;

        y += BTN_H + SECTION_GAP + DIVIDER_H + SECTION_GAP;

        // Updates button
        if (MouseClick.isClick(renderMouseX, renderMouseY, panelX + PAD, y, rowW, BTN_H)) {
            if (client != null) client.setScreen(new UpdatesListScreen(this));
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void applyCustomBackground() {
        String name = (customName != null) ? customName.trim() : "";
        String url = (customUrl != null) ? customUrl.trim() : "";

        if (name.isBlank()) {
            errorMessage = "Введите название фона";
            errorTime = System.currentTimeMillis();
            focusedField = FocusedField.NAME;
            return;
        }
        if (url.isBlank()) {
            errorMessage = "Введите ссылку на картинку";
            errorTime = System.currentTimeMillis();
            focusedField = FocusedField.URL;
            return;
        }

        String date = LocalDateTime.now().format(DATE_FMT);
        backgroundOptions.add(new BackgroundOption(name, url, date));
        selectedBackgroundIndex = backgroundOptions.size() - 1;
        TitleScreenSettings.setBackgroundPath(url);

        customName = "";
        customUrl = "";
        focusedField = FocusedField.NONE;
        errorMessage = null;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (keyCode == 256) {
            if (focusedField != FocusedField.NONE) {
                focusedField = FocusedField.NONE;
                return true;
            }
            startClose();
            return true;
        }

        if (focusedField == FocusedField.NONE) return super.keyPressed(input);

        // Tab to switch fields
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusedField = (focusedField == FocusedField.NAME) ? FocusedField.URL : FocusedField.NAME;
            selectAll = false;
            return true;
        }

        if (InputCompat.isControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_A) { selectAll = true; return true; }
            if (keyCode == GLFW.GLFW_KEY_C) {
                String val = getFocusedValue();
                if (client != null && client.keyboard != null && val != null && !val.isBlank())
                    client.keyboard.setClipboard(val);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                if (client != null && client.keyboard != null) {
                    String clip = client.keyboard.getClipboard();
                    if (clip != null && !clip.isBlank()) {
                        if (selectAll) { setFocusedValue(""); selectAll = false; }
                        setFocusedValue(getFocusedValue() + clip.trim());
                    }
                }
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectAll) { setFocusedValue(""); selectAll = false; return true; }
            String val = getFocusedValue();
            if (val != null && !val.isEmpty()) setFocusedValue(val.substring(0, val.length() - 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (focusedField == FocusedField.NAME) {
                focusedField = FocusedField.URL;
                selectAll = false;
            } else {
                applyCustomBackground();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (closing) return true;
        if (focusedField != FocusedField.NONE && chr >= 32 && chr != 127) {
            if (selectAll) { setFocusedValue(""); selectAll = false; }
            setFocusedValue(getFocusedValue() + chr);
            return true;
        }
        return super.charTyped(input);
    }

    private String getFocusedValue() {
        return switch (focusedField) {
            case NAME -> customName == null ? "" : customName;
            case URL -> customUrl == null ? "" : customUrl;
            default -> "";
        };
    }

    private void setFocusedValue(String val) {
        switch (focusedField) {
            case NAME -> customName = val;
            case URL -> customUrl = val;
        }
    }

    private void startClose() {
        if (closing) return;
        closing = true;
        closeCallback = () -> {
            if (client != null) client.setScreen(parent);
        };
        double current = clamp01((float) alphaAnimation.getValue());
        alphaAnimation.setValue(current);
        alphaAnimation.setDestinationValue(0.0);
        alphaAnimation.reset();
    }

    private float nc$contentHeight() {
        float h = HEADER_H + SECTION_GAP;                  // header strip
        h += BTN_H + SECTION_GAP;                          // ClickGui button
        h += 16f;                                          // "Задний фон" section header
        h += backgroundOptions.size() * (ROW_H + GAP);    // bg options
        h += SECTION_GAP - GAP;                            // pre-custom-section gap
        h += 16f;                                          // "Добавить свой фон" header
        h += ROW_H + GAP;                                  // name field
        h += ROW_H + GAP;                                  // url field
        h += 9f;                                           // error line
        h += BTN_H + SECTION_GAP;                          // apply button
        h += DIVIDER_H + SECTION_GAP;                     // divider
        h += BTN_H;                                        // updates button
        h += PAD;
        return h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int applyAlpha(int color, float factor) {
        int baseAlpha = ColorUtils.alpha(color);
        int newAlpha = Math.round(baseAlpha * clamp01(factor));
        return ColorUtils.setAlpha(color, newAlpha);
    }

    private int scaleAlpha(int color, float scale) {
        int base = ColorUtils.alpha(color);
        int newAlpha = Math.round(base * clamp01(scale));
        return ColorUtils.setAlpha(color, newAlpha);
    }

    private static class BackgroundOption {
        final String displayName;
        final String path;
        final String date;

        BackgroundOption(String displayName, String path, String date) {
            this.displayName = displayName;
            this.path = path;
            this.date = date;
        }
    }

}
